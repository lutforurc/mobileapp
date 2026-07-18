package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.ReceiveRequest
import com.example.cashbookbd.data.remote.dto.TopProductDto
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.dashboard.model.TopProduct
import com.example.cashbookbd.ui.dashboard.model.toDashboard
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
