package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.LedgerApiService
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.LedgerStatement
import com.example.cashbookbd.ui.reports.model.toLedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.toLedgerStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Backs the Ledger report screen: the searchable ledger dropdown and the
 * `/reports/api-ledger` detail lookup. Maps every outcome to a [Resource] and
 * flags a 401 via [Resource.Error.isUnauthorized] so the UI can force re-login.
 */
class LedgerRepository(
    private val api: LedgerApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    /**
     * Searches the ledger/account dropdown source. A blank query short-circuits
     * to an empty list (the backend returns a bare `[]` for that, which wouldn't
     * parse, and there's nothing to search anyway).
     */
    suspend fun searchLedgers(
        query: String,
        acType: String = "",
    ): Resource<List<LedgerDropdownItem>> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Resource.Success(emptyList())

        safeCall {
            val response = api.searchLedgers(searchName = trimmed, acType = acType)
            response.unauthorizedOrNull()?.let { return@safeCall it }

            if (response.code() == 404) {
                return@safeCall Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@safeCall Resource.Success(emptyList())
            }

            val ledgers = body.data?.items.orEmpty().mapNotNull { it.toLedgerDropdownItem() }
            Resource.Success(ledgers)
        }
    }

    /** Fetches the selected ledger's statement from `/reports/api-ledger`. */
    suspend fun getLedgerReport(
        branchId: Long,
        ledgerId: Long,
        startDate: String,
        endDate: String,
    ): Resource<LedgerStatement> = withContext(ioDispatcher) {
        safeCall {
            val response = api.getLedgerReport(
                branchId = branchId,
                ledgerId = ledgerId,
                startDate = startDate,
                endDate = endDate,
            )
            response.unauthorizedOrNull()?.let { return@safeCall it }

            if (response.code() == 404) {
                return@safeCall Resource.Error("No data found for this ledger.")
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")

            val statement = body.data?.statement?.toLedgerStatement()
            when {
                !body.success -> Resource.Error(body.message?.ifBlank { null } ?: "Couldn't load ledger.")
                statement == null -> Resource.Error("No data found for this ledger.")
                else -> Resource.Success(statement)
            }
        }
    }

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
