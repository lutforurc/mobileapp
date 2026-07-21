package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.ui.reports.model.BankBookReport
import com.example.cashbookbd.ui.reports.model.BranchList
import com.example.cashbookbd.ui.reports.model.CashBookReport
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.reports.model.toBankBookRow
import com.example.cashbookbd.ui.reports.model.toBranchOption
import com.example.cashbookbd.ui.reports.model.toCashBookRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Backs the report screens. Maps every outcome to a [Resource] and flags a 401
 * via [Resource.Error.isUnauthorized] so the UI can force re-login.
 */
class ReportRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getBranches(): Resource<BranchList> = withContext(ioDispatcher) {
        safeCall {
            val response = api.getBranches()
            response.unauthorizedOrNull()?.let { return@safeCall it }

            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@safeCall Resource.Error(body.message?.ifBlank { null } ?: "Couldn't load branches.")
            }

            val payload = body.data?.payload
            val branches = payload?.items.orEmpty().mapNotNull { it.toBranchOption() }
            // Backend's current business date (dd/MM/yyyy) — used as the default range.
            val transactionDate = SimpleDate.fromDisplay(payload?.transactionDate)
            Resource.Success(BranchList(branches = branches, transactionDate = transactionDate))
        }
    }

    suspend fun getCashBook(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<CashBookReport> = withContext(ioDispatcher) {
        safeCall {
            val response = api.getCashBook(branchId, startDate, endDate)
            response.unauthorizedOrNull()?.let { return@safeCall it }

            // The backend returns notFound() (success=false) when there are no
            // rows — surface that as an empty report rather than an error.
            if (response.code() == 404) {
                return@safeCall Resource.Success(CashBookReport(emptyList()))
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@safeCall Resource.Success(CashBookReport(emptyList()))
            }

            val rows = body.data?.rows.orEmpty().map { it.toCashBookRow() }
            Resource.Success(CashBookReport(rows))
        }
    }

    /** The bank-side twin of [getCashBook]; [startDate]/[endDate] are yyyy-MM-dd. */
    suspend fun getBankBook(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<BankBookReport> = withContext(ioDispatcher) {
        safeCall {
            val response = api.getBankBook(branchId, startDate, endDate)
            response.unauthorizedOrNull()?.let { return@safeCall it }

            // notFound() answers 200 with success=false when the period is
            // empty, so both it and a real 404 mean "no rows", not an error.
            if (response.code() == 404) {
                return@safeCall Resource.Success(BankBookReport(emptyList()))
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@safeCall Resource.Success(BankBookReport(emptyList()))
            }

            val rows = body.data?.rows.orEmpty().map { it.toBankBookRow() }
            Resource.Success(BankBookReport(rows))
        }
    }

    /** Shared 401 check for a Retrofit [retrofit2.Response]. */
    private fun retrofit2.Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
        block()
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
