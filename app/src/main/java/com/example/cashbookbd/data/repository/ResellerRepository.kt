package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.ResellerOverviewDto
import com.example.cashbookbd.ui.reseller.model.ResellerClient
import com.example.cashbookbd.ui.reseller.model.ResellerCommissionRow
import com.example.cashbookbd.ui.reseller.model.ResellerDashboard
import com.example.cashbookbd.ui.reseller.model.ResellerOverview
import com.example.cashbookbd.ui.reseller.model.ResellerPaymentRow
import com.example.cashbookbd.ui.reseller.model.toResellerClient
import com.example.cashbookbd.ui.reseller.model.toResellerCommissionRow
import com.example.cashbookbd.ui.reseller.model.toResellerOverview
import com.example.cashbookbd.ui.reseller.model.toResellerPaymentRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Loads the reseller self-service dashboard: the KPI [ResellerOverview] plus three
 * lists (assigned clients, commission history, payout details). Every outcome is
 * mapped to a [Resource] so Retrofit types never reach the ViewModel; a 401 is
 * flagged [Resource.Error.isUnauthorized] so the UI can force re-login.
 *
 * The overview call GATES the screen — its failure (or 401) errors the whole
 * dashboard. The three list calls are secondary: a single list failing is
 * swallowed to an empty section rather than failing the screen, so a partial
 * outage still shows the KPIs and whatever loaded.
 */
class ResellerRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getDashboard(): Resource<ResellerDashboard> = withContext(ioDispatcher) {
        try {
            coroutineScope {
                // Fire all four in parallel; the overview decides success/failure.
                val overviewDeferred = async { api.getResellerOverview() }
                val companiesDeferred = async { runCatching { api.getResellerCompanies() }.getOrNull() }
                val paymentsDeferred = async { runCatching { api.getResellerPayments() }.getOrNull() }
                val ledgersDeferred = async { runCatching { api.getResellerCommissionLedgers() }.getOrNull() }

                val overviewResponse = overviewDeferred.await()

                if (overviewResponse.code() == HTTP_UNAUTHORIZED) {
                    return@coroutineScope Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                }
                if (!overviewResponse.isSuccessful) {
                    return@coroutineScope Resource.Error(
                        "Server error (${overviewResponse.code()}). Please try again later."
                    )
                }
                val overviewBody = overviewResponse.body()
                    ?: return@coroutineScope Resource.Error("Invalid response from server.")
                if (!overviewBody.success) {
                    return@coroutineScope Resource.Error(
                        overviewBody.message?.ifBlank { null } ?: "Couldn't load the reseller dashboard."
                    )
                }

                val overview: ResellerOverview =
                    (overviewBody.data ?: ResellerOverviewDto()).toResellerOverview()

                val clients: List<ResellerClient> = companiesDeferred.await()
                    ?.takeIf { it.isSuccessful }?.body()?.takeIf { it.success }?.data
                    .orEmpty().map { it.toResellerClient() }

                val commissionHistory: List<ResellerCommissionRow> = paymentsDeferred.await()
                    ?.takeIf { it.isSuccessful }?.body()?.takeIf { it.success }?.data
                    .orEmpty().map { it.toResellerCommissionRow() }

                // Payment Details = only the "paid" ledger rows, as the web filters.
                val paymentDetails: List<ResellerPaymentRow> = ledgersDeferred.await()
                    ?.takeIf { it.isSuccessful }?.body()?.takeIf { it.success }?.data
                    .orEmpty()
                    .filter { it.ledgerType?.trim().equals("paid", ignoreCase = true) }
                    .map { it.toResellerPaymentRow() }

                Resource.Success(
                    ResellerDashboard(
                        overview = overview,
                        clients = clients,
                        commissionHistory = commissionHistory,
                        paymentDetails = paymentDetails,
                    )
                )
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
}
