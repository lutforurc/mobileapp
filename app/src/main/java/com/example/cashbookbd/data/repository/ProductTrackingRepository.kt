package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

/** One configured tracking row (`product-tracking/settings`). */
data class TrackingSetting(
    val id: Long,
    val productId: Long,
    val productName: String,
    /** 0 = all branches — a real domain value, not "unset". */
    val branchId: Long,
    val branchName: String,
    /** 0 = all parties. */
    val coa4Id: Long,
    val partyName: String,
    val trackSalesBill: Boolean,
    val trackPurchaseBill: Boolean,
    val trackCashReceived: Boolean,
    val trackCashPayment: Boolean,
    val isActive: Boolean,
)

/** What the settings form posts (create and update share the shape). */
data class TrackingSettingDraft(
    val productId: Long,
    val branchId: Long,
    val coa4Id: Long,
    val trackSalesBill: Boolean,
    val trackPurchaseBill: Boolean,
    val trackCashReceived: Boolean,
    val trackCashPayment: Boolean,
    val isActive: Boolean,
)

/** A product offered by `available-products` / `products`. */
data class TrackingProductOption(
    val id: Long,
    val name: String,
    val isActive: Boolean = true,
)

/** The 10 running figures both reports share. */
data class TrackingFigures(
    val openingReceivable: Double,
    val salesBill: Double,
    val salesReturn: Double,
    val cashReceived: Double,
    val closingReceivable: Double,
    val openingPayable: Double,
    val purchaseBill: Double,
    val purchaseReturn: Double,
    val cashPayment: Double,
    val closingPayable: Double,
)

/** Vouchers in range that carry no product mapping — shown, never hidden. */
data class TrackingUnmapped(
    val received: Double,
    val payment: Double,
    val rowsCount: Int,
)

/** One line of the Product Financial Statement. */
data class StatementRow(
    val vrDate: String,
    val vrNo: String,
    val lineType: String,
    val partyName: String,
    val quantity: Double,
    val rate: Double,
    val salesBill: Double,
    val salesReturn: Double,
    val purchaseBill: Double,
    val purchaseReturn: Double,
    val cashReceived: Double,
    val cashPayment: Double,
    val receivableBalance: Double,
    val payableBalance: Double,
)

/** The full statement payload. */
data class ProductStatement(
    val productName: String,
    val branchName: String,
    val partyName: String,
    val startDate: String,
    val endDate: String,
    val notice: String,
    val summary: TrackingFigures,
    val unmapped: TrackingUnmapped,
    val rows: List<StatementRow>,
)

/** One product's row of the receivable/payable summary. */
data class TrackingSummaryRow(
    val productId: Long,
    val productName: String,
    val figures: TrackingFigures,
)

/** The full summary payload. */
data class TrackingSummary(
    val branchName: String,
    val partyName: String,
    val startDate: String,
    val endDate: String,
    val notice: String,
    val rows: List<TrackingSummaryRow>,
    val totals: TrackingFigures?,
    val unmapped: TrackingUnmapped,
)

/**
 * The product-tracking module: settings CRUD (no delete by design — deactivation
 * is the only removal path, and it never touches history) and the two memo
 * reports. Business rejections arrive as HTTP 201 with `success:false`
 * (duplicate setting etc.) — the JSON `success` flag is the only verdict.
 */
class ProductTrackingRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
    }

    // ---- Settings ----------------------------------------------------------

    /** The configured rows. Paginated server-side; like the web, page 1 only. */
    suspend fun fetchSettings(search: String): Resource<List<TrackingSetting>> =
        withContext(ioDispatcher) {
            guarded {
                val params = buildMap {
                    put("per_page", "100")
                    search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                }
                val response = api.get("product-tracking/settings", params)
                envelope(response) { body ->
                    // Paginator: rows one level deeper than the plain payloads.
                    val rows = body.obj("data")?.obj("data")?.get("data")
                        ?.takeIf { it.isJsonArray }?.asJsonArray
                    Resource.Success(
                        rows?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            TrackingSetting(
                                id = o.long("id") ?: return@mapNotNull null,
                                productId = o.long("product_id") ?: 0L,
                                productName = o.text("product_name"),
                                branchId = o.long("branch_id") ?: 0L,
                                branchName = o.text("branch_name"),
                                coa4Id = o.long("coa4_id") ?: 0L,
                                partyName = o.text("party_name"),
                                trackSalesBill = o.flag("track_sales_bill"),
                                trackPurchaseBill = o.flag("track_purchase_bill"),
                                trackCashReceived = o.flag("track_cash_received"),
                                trackCashPayment = o.flag("track_cash_payment"),
                                isActive = o.flag("is_active"),
                            )
                        }.orEmpty()
                    )
                }
            }
        }

    /**
     * Products not yet configured for exactly this (branch, party) pair — the
     * form's Product dropdown. The list changes when either changes.
     */
    suspend fun fetchAvailableProducts(branchId: Long, coa4Id: Long): Resource<List<TrackingProductOption>> =
        withContext(ioDispatcher) {
            guarded {
                val response = api.get(
                    "product-tracking/available-products",
                    mapOf("branch_id" to branchId.toString(), "coa4_id" to coa4Id.toString()),
                )
                envelope(response) { body ->
                    Resource.Success(body.productArray())
                }
            }
        }

    /** The tracked products a report may filter on (ledger context). */
    suspend fun fetchTrackedProducts(branchId: Long, coa4Id: Long): Resource<List<TrackingProductOption>> =
        withContext(ioDispatcher) {
            guarded {
                val params = buildMap {
                    put("context", "ledger")
                    put("include_inactive", "1")
                    if (branchId > 0) put("branch_id", branchId.toString())
                    if (coa4Id > 0) put("coa4_id", coa4Id.toString())
                }
                val response = api.get("product-tracking/products", params)
                envelope(response) { body ->
                    Resource.Success(body.productArray())
                }
            }
        }

    /** Creates ([id] null) or updates a setting. Duplicate = 2xx success:false. */
    suspend fun saveSetting(id: Long?, draft: TrackingSettingDraft): Resource<String> =
        withContext(ioDispatcher) {
            val body = mapOf<String, Any>(
                "product_id" to draft.productId,
                "branch_id" to draft.branchId,
                "coa4_id" to draft.coa4Id,
                "track_sales_bill" to draft.trackSalesBill,
                "track_purchase_bill" to draft.trackPurchaseBill,
                "track_cash_received" to draft.trackCashReceived,
                "track_cash_payment" to draft.trackCashPayment,
                "is_active" to draft.isActive,
            )
            guarded {
                val response = if (id == null) {
                    api.postAny("product-tracking/settings", body)
                } else {
                    api.putAny("product-tracking/settings/$id", body)
                }
                envelope(response) { json ->
                    Resource.Success(json.message() ?: "Saved.")
                }
            }
        }

    /** Flips one setting on/off. Never deletes — history stays intact. */
    suspend fun toggleSetting(id: Long, isActive: Boolean): Resource<String> =
        withContext(ioDispatcher) {
            guarded {
                val response = api.patchAny(
                    "product-tracking/settings/$id/toggle",
                    mapOf("is_active" to if (isActive) 1 else 0),
                )
                envelope(response) { json ->
                    Resource.Success(json.message() ?: if (isActive) "Tracking activated." else "Tracking deactivated.")
                }
            }
        }

    // ---- Reports -----------------------------------------------------------

    /** `GET reports/product-financial-statement` — one product's memo ledger. */
    suspend fun fetchStatement(
        productId: Long,
        branchId: Long,
        coa4Id: Long,
        startDate: String,
        endDate: String,
    ): Resource<ProductStatement> = withContext(ioDispatcher) {
        guarded {
            val response = api.get(
                "reports/product-financial-statement",
                mapOf(
                    "product_id" to productId.toString(),
                    "branch_id" to branchId.toString(),
                    "coa4_id" to coa4Id.toString(),
                    "start_date" to startDate,
                    "end_date" to endDate,
                ),
            )
            envelope(response) { body ->
                val p = body.obj("data")?.obj("data")
                    ?: return@envelope Resource.Error("Invalid response from server.")
                Resource.Success(
                    ProductStatement(
                        productName = p.obj("product")?.text("name").orEmpty(),
                        branchName = p.obj("branch")?.text("name").orEmpty(),
                        partyName = p.obj("party")?.text("name").orEmpty(),
                        startDate = p.text("start_date"),
                        endDate = p.text("end_date"),
                        notice = p.text("notice"),
                        summary = p.obj("summary").toFigures(),
                        unmapped = p.obj("unmapped").toUnmapped(),
                        rows = p.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { el ->
                                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                                StatementRow(
                                    vrDate = o.text("vr_date"),
                                    vrNo = o.text("vr_no"),
                                    lineType = o.text("line_type"),
                                    partyName = o.text("party_name"),
                                    quantity = o.dbl("quantity"),
                                    rate = o.dbl("rate"),
                                    salesBill = o.dbl("sales_bill"),
                                    salesReturn = o.dbl("sales_return"),
                                    purchaseBill = o.dbl("purchase_bill"),
                                    purchaseReturn = o.dbl("purchase_return"),
                                    cashReceived = o.dbl("cash_received"),
                                    cashPayment = o.dbl("cash_payment"),
                                    receivableBalance = o.dbl("receivable_balance"),
                                    payableBalance = o.dbl("payable_balance"),
                                )
                            }
                            .orEmpty(),
                    )
                )
            }
        }
    }

    /** `GET reports/product-tracking-summary` — every product's figures at once. */
    suspend fun fetchSummary(
        branchId: Long,
        coa4Id: Long,
        startDate: String,
        endDate: String,
        includeInactive: Boolean,
    ): Resource<TrackingSummary> = withContext(ioDispatcher) {
        guarded {
            val params = buildMap {
                put("branch_id", branchId.toString())
                put("coa4_id", coa4Id.toString())
                put("start_date", startDate)
                put("end_date", endDate)
                // Sent as 1 or omitted entirely, like the web.
                if (includeInactive) put("include_inactive", "1")
            }
            val response = api.get("reports/product-tracking-summary", params)
            envelope(response) { body ->
                val p = body.obj("data")?.obj("data")
                    ?: return@envelope Resource.Error("Invalid response from server.")
                Resource.Success(
                    TrackingSummary(
                        branchName = p.obj("branch")?.text("name").orEmpty(),
                        partyName = p.obj("party")?.text("name").orEmpty(),
                        startDate = p.text("start_date"),
                        endDate = p.text("end_date"),
                        notice = p.text("notice"),
                        rows = p.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { el ->
                                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                                TrackingSummaryRow(
                                    productId = o.long("product_id") ?: return@mapNotNull null,
                                    productName = o.text("product_name"),
                                    figures = o.toFigures(),
                                )
                            }
                            .orEmpty(),
                        totals = p.get("totals")?.takeIf { it.isJsonObject }?.asJsonObject?.toFigures(),
                        unmapped = p.obj("unmapped").toUnmapped(),
                    )
                )
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------

    private fun JsonObject?.toFigures(): TrackingFigures = TrackingFigures(
        openingReceivable = this?.dbl("opening_receivable") ?: 0.0,
        salesBill = this?.dbl("sales_bill") ?: 0.0,
        salesReturn = this?.dbl("sales_return") ?: 0.0,
        cashReceived = this?.dbl("cash_received") ?: 0.0,
        closingReceivable = this?.dbl("closing_receivable") ?: 0.0,
        openingPayable = this?.dbl("opening_payable") ?: 0.0,
        purchaseBill = this?.dbl("purchase_bill") ?: 0.0,
        purchaseReturn = this?.dbl("purchase_return") ?: 0.0,
        cashPayment = this?.dbl("cash_payment") ?: 0.0,
        closingPayable = this?.dbl("closing_payable") ?: 0.0,
    )

    private fun JsonObject?.toUnmapped(): TrackingUnmapped = TrackingUnmapped(
        received = this?.dbl("received") ?: 0.0,
        payment = this?.dbl("payment") ?: 0.0,
        rowsCount = this?.long("rows_count")?.toInt() ?: 0,
    )

    private fun JsonObject.productArray(): List<TrackingProductOption> =
        obj("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                TrackingProductOption(
                    id = o.long("id") ?: return@mapNotNull null,
                    name = o.text("name").ifBlank { "Product ${o.long("id")}" },
                    isActive = if (o.has("is_active")) o.flag("is_active") else true,
                )
            }
            .orEmpty()

    /**
     * 401 → session expired; otherwise the JSON `success` flag is the verdict —
     * business rejections come back as HTTP 201 with success:false.
     */
    private inline fun <T> envelope(
        response: Response<JsonElement>,
        read: (JsonObject) -> Resource<T>,
    ): Resource<T> {
        if (response.code() == HTTP_UNAUTHORIZED) {
            return Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        }
        val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: response.errorBody()?.string()
                ?.let { runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull() }
                ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Resource.Error("Server error (${response.code()}). Please try again later.")
        val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        if (success == false) {
            return Resource.Error(body.message() ?: "The request was refused.")
        }
        return read(body)
    }

    private inline fun <T> guarded(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error(NO_NETWORK)
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.flag(key: String): Boolean =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.let { it == "true" || it.toDoubleOrNull()?.let { n -> n != 0.0 } == true } == true

    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null }
}
