package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.ReceiveRequest
import com.example.cashbookbd.data.remote.dto.TopProductDto
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.dashboard.model.TopProduct
import com.example.cashbookbd.ui.dashboard.model.toDashboard
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** The month's top sold/purchased products plus the period they cover. */
data class MonthlyTopProducts(
    val days: Int,
    val sales: List<TopProduct>,
    val purchases: List<TopProduct>,
)

/** One KPI figure: today's value, yesterday's, and its last-14-days series. */
data class DashboardKpi(
    val value: Double,
    val previous: Double,
    val spark: List<Double>,
)

/** One receivable-ageing bucket ("0-30", "31-60", "61-90", "90+"). */
data class AgingBucket(val label: String, val amount: Double, val parties: Int)

data class DueAging(
    val total: Double,
    val parties: Int,
    /** Credit-balance parties' sum — shown only when positive, like the web. */
    val advance: Double,
    val buckets: List<AgingBucket>,
)

data class LowStockItem(val name: String, val stock: Double, val orderLevel: Double)

data class LowStock(
    /** "order_level" (items at/under their reorder level) or "lowest" (no levels set). */
    val mode: String,
    val items: List<LowStockItem>,
)

/** The `dashboard/summary` payload: KPI tiles, ageing, low stock, sparklines. */
data class DashboardSummary(
    /** ISO yyyy-MM-dd branch transaction date. */
    val trxDate: String,
    val kpis: Map<String, DashboardKpi>,
    val dueAging: DueAging?,
    val lowStock: LowStock?,
)

/**
 * Fetches the dashboard payload and maps every outcome to a [Resource] so the
 * ViewModel never touches Retrofit types. A 401 is flagged via
 * [Resource.Error.isUnauthorized] so the UI can force re-login.
 */
class DashboardRepository(
    private val api: ApiService,
    private val cache: DashboardCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    /**
     * The last cached dashboard, or null if nothing has been stored yet. Lets the
     * UI show content instantly on open while [getDashboard] refreshes it.
     */
    suspend fun getCachedDashboard(): Dashboard? = withContext(ioDispatcher) {
        cache.load()?.toDashboard()
    }

    suspend fun getDashboard(): Resource<Dashboard> = withContext(ioDispatcher) {
        try {
            val response = api.getDashboard()

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }

            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }

            val body = response.body()
                ?: return@withContext Resource.Error("Invalid response from server.")

            when {
                !body.success -> Resource.Error(body.message?.ifBlank { null } ?: "Couldn't load dashboard.")

                body.data?.payload == null -> Resource.Error("No dashboard data available.")

                else -> {
                    // Store the full payload so the next open can render offline.
                    cache.save(body.data.payload)
                    Resource.Success(body.data.payload.toDashboard())
                }
            }
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * The month's top sold/purchased products, for the non-construction
     * dashboards. Returns null on any failure — these two lists are secondary,
     * so a miss leaves the rest of the dashboard usable rather than erroring.
     */
    suspend fun getMonthlyTopProducts(): MonthlyTopProducts? = withContext(ioDispatcher) {
        try {
            val body = api.getMonthlyTopProducts().takeIf { it.isSuccessful }?.body()
            val payload = body?.takeIf { it.success }?.data?.payload ?: return@withContext null
            MonthlyTopProducts(
                days = payload.topProductDays ?: 0,
                sales = payload.topProductsSales.toTopProducts(),
                purchases = payload.topProductsPurchase.toTopProducts(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun List<TopProductDto>?.toTopProducts(): List<TopProduct> =
        orEmpty().map {
            TopProduct(
                name = it.name?.trim().orEmpty().ifBlank { "Unnamed product" },
                quantity = it.qty?.trim()?.toDoubleOrNull() ?: 0.0,
            )
        }

    /**
     * The KPI/ageing/low-stock payload (`dashboard/summary`). Null on any
     * failure — like the top-products lists, a summary miss must never blank
     * the cards that already work.
     */
    suspend fun getDashboardSummary(): DashboardSummary? = withContext(ioDispatcher) {
        try {
            val body = api.getDashboardSummary().takeIf { it.isSuccessful }?.body()
                ?.takeIf { it.isJsonObject }?.asJsonObject
            if (body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext null
            }
            val payload = body?.getAsJsonObject("data")?.get("data")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext null
            parseSummary(payload)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSummary(p: JsonObject): DashboardSummary {
        val kpis = p.get("kpis")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet().orEmpty()
            .mapNotNull { (key, value) ->
                val kpi = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                key to DashboardKpi(
                    value = kpi.num("value"),
                    previous = kpi.num("previous"),
                    spark = kpi.get("spark")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() }
                        .orEmpty(),
                )
            }
            .toMap()

        val aging = p.get("dueAging")?.takeIf { it.isJsonObject }?.asJsonObject?.let { a ->
            DueAging(
                total = a.num("total"),
                parties = a.num("parties").toInt(),
                advance = a.num("advance"),
                buckets = a.get("buckets")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { el ->
                        val b = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        AgingBucket(
                            label = b.get("label")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                            amount = b.num("amount"),
                            parties = b.num("parties").toInt(),
                        )
                    }
                    .orEmpty(),
            )
        }

        val lowStock = p.get("lowStock")?.takeIf { it.isJsonObject }?.asJsonObject?.let { l ->
            LowStock(
                mode = l.get("mode")?.takeUnless { it.isJsonNull }?.asString ?: "order_level",
                items = l.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { el ->
                        val item = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        LowStockItem(
                            name = item.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                            stock = item.num("stock"),
                            // camelCase from the PHP service, unlike the rest of the API.
                            orderLevel = item.num("orderLevel"),
                        )
                    }
                    .orEmpty(),
            )
        }

        return DashboardSummary(
            trxDate = p.get("trxDate")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            kpis = kpis,
            dueAging = aging,
            lowStock = lowStock,
        )
    }

    /** A JSON number (or numeric string) as a double; anything else is 0. */
    private fun JsonObject.num(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /**
     * Confirms ("receives") one head-office remittance. Returns the success
     * message (the new voucher no) on success.
     *
     * ⚠️ NON-IDEMPOTENT: there is deliberately NO retry here and callers must not
     * add one. Success is decided by [ReceiveResponse.success], NOT the HTTP
     * status (a business failure returns HTTP 201). On an ambiguous failure
     * (timeout / lost connection) the error is flagged
     * [Resource.Error.isAmbiguous] so the caller re-fetches the dashboard to
     * learn the true state instead of re-posting.
     */
    suspend fun receiveSpecificItem(request: ReceiveRequest): Resource<String> =
        withContext(ioDispatcher) {
            try {
                val response = api.receiveSpecificItem(request)

                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                }

                // Do NOT branch on response.isSuccessful — a failure is HTTP 201 (2xx).
                val body = response.body()
                    ?: return@withContext Resource.Error(
                        "No response from server — refreshing to check.",
                        isAmbiguous = true,
                    )

                if (body.success) {
                    Resource.Success(body.message?.ifBlank { null } ?: "Received successfully.")
                } else {
                    Resource.Error(body.message?.ifBlank { null } ?: "Couldn't confirm receipt.")
                }
            } catch (e: IOException) {
                // Timeout / connection lost: the POST MAY have reached the server.
                // Ambiguous — must not be retried; caller re-fetches to reconcile.
                Resource.Error(
                    "Connection lost before we got a reply — refreshing to check.",
                    isAmbiguous = true,
                )
            } catch (e: HttpException) {
                if (e.code() == HTTP_UNAUTHORIZED) {
                    Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
                } else {
                    // A thrown HTTP error is also ambiguous for a non-idempotent POST.
                    Resource.Error("Couldn't confirm receipt (${e.code()}) — refreshing to check.", isAmbiguous = true)
                }
            } catch (e: Exception) {
                Resource.Error("Couldn't confirm receipt. Please try again.")
            }
        }
}
