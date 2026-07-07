package com.example.cashbookbd.data.repository

import android.util.Log
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.DueListReport
import com.example.cashbookbd.ui.reports.model.DueRow
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.Locale

/**
 * Backs the Due List report: `GET /reports/duelist?branch_id=&enddate=`.
 *
 * The rows array is deeply nested (observed at
 * `data.data.original.data.data`, where `original` is itself a full response
 * envelope), so the parser searches the whole tree for the best array of ledger
 * rows rather than following a fixed path. Rows carry `coa4_name`/`debit`/
 * `credit`/`ledger_page`/`mobile`, with trailing `Total` and `Balance` summary
 * rows (identified by a null `coa4_id`).
 *
 * `enddate` is sent as `yyyy-MM-dd`; if that yields no rows the request retries
 * with `dd/MM/yyyy`.
 */
class DueListRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val PATH = "reports/duelist"
        private const val LOG_TAG = "DueList"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        // Keys that mark an array element as a due/ledger row (for array scoring).
        private val ROW_MARKER_KEYS =
            setOf("coa4_name", "name", "customer_name", "party_name", "debit", "credit", "mobile", "due")

        private val NAME_KEYS = listOf(
            "coa4_name", "name", "customer_name", "party_name", "ledger_name", "account_name", "contact_name",
        )
        private val MOBILE_KEYS = listOf("mobile", "phone", "contact_no")
        private val REFERENCE_KEYS = listOf("ledger_page", "manual_address", "address", "customer_address")
        private val DEBIT_KEYS = listOf("debit", "debit_bal", "due", "due_amount", "balance")
        private val CREDIT_KEYS = listOf("credit", "credit_bal")

        private val TOTAL_REGEX = Regex("(?i)^\\s*total\\s*$")
        private val BALANCE_REGEX = Regex("(?i)^\\s*balance\\s*$")
    }

    /**
     * @param endApi end date as `yyyy-MM-dd`
     * @param endDisplay end date as `dd/MM/yyyy` (fallback)
     */
    suspend fun fetch(
        branchId: Long,
        endApi: String,
        endDisplay: String,
    ): Resource<DueListReport> = withContext(ioDispatcher) {
        val first = request(branchId, endApi)
        if (first is Resource.Error) return@withContext first
        if (first is Resource.Success && !first.data.isEmpty) return@withContext first

        val second = request(branchId, endDisplay)
        if (second is Resource.Success && !second.data.isEmpty) return@withContext second
        first
    }

    private suspend fun request(branchId: Long, enddate: String): Resource<DueListReport> {
        val params = mapOf(
            "branch_id" to branchId.toString(),
            "enddate" to enddate,
        )
        return try {
            val response = api.get(PATH, params)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return Resource.Error("You do not have permission to view this report.")
                404 -> return Resource.Success(emptyReport())
            }
            if (!response.isSuccessful) {
                return Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            parseBody(response)
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            when (e.code()) {
                HTTP_UNAUTHORIZED -> Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> Resource.Error("You do not have permission to view this report.")
                else -> Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun parseBody(response: Response<JsonElement>): Resource<DueListReport> {
        val root = response.body() ?: return Resource.Error("Invalid response from server.")
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "raw response: $root")

        val array = locateRows(root)
        if (array == null) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "no rows array located")
            return Resource.Success(emptyReport())
        }

        val maps = array.mapNotNull { it.asObjectOrNull()?.fieldMap() }

        // Summary rows are named Total / Balance; everything else is a party row.
        val totalMap = maps.firstOrNull { TOTAL_REGEX.matches(it.string(NAME_KEYS)) }
        val balanceMap = maps.firstOrNull { BALANCE_REGEX.matches(it.string(NAME_KEYS)) }

        val dataRows = maps
            .filter { m ->
                val name = m.string(NAME_KEYS)
                name.isNotBlank() && !TOTAL_REGEX.matches(name) && !BALANCE_REGEX.matches(name)
            }
            .map {
                DueRow(
                    customer = it.string(NAME_KEYS).ifBlank { "—" },
                    mobile = it.stringOrNull(MOBILE_KEYS),
                    reference = it.stringOrNull(REFERENCE_KEYS),
                    debit = it.number(DEBIT_KEYS),
                    credit = it.number(CREDIT_KEYS),
                )
            }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "located array size=${array.size()}, parsed dataRows=${dataRows.size}")
        }

        val totalDebit = totalMap?.number(DEBIT_KEYS) ?: dataRows.sumOf { it.debit }
        val totalCredit = totalMap?.number(CREDIT_KEYS) ?: dataRows.sumOf { it.credit }
        val netBalance = balanceMap?.let { it.number(DEBIT_KEYS) - it.number(CREDIT_KEYS) }
            ?: (totalDebit - totalCredit)

        return Resource.Success(
            DueListReport(
                rows = dataRows,
                totalDebit = totalDebit,
                totalCredit = totalCredit,
                netBalance = netBalance,
            )
        )
    }

    /** Finds the rows array anywhere in the tree, preferring the one with the most ledger-row objects. */
    private fun locateRows(root: JsonElement): JsonArray? {
        val candidates = mutableListOf<JsonArray>()
        collectObjectArrays(root, candidates)
        return candidates.maxByOrNull { arr -> arr.count { el -> el.hasRowMarker() } }
            ?.takeIf { it.size() > 0 }
    }

    private fun collectObjectArrays(element: JsonElement, acc: MutableList<JsonArray>) {
        when {
            element.isJsonArray -> {
                val arr = element.asJsonArray
                if (arr.any { it.isJsonObject }) acc.add(arr)
                arr.forEach { collectObjectArrays(it, acc) }
            }
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { collectObjectArrays(it.value, acc) }
        }
    }

    private fun JsonElement.hasRowMarker(): Boolean {
        val obj = asObjectOrNull() ?: return false
        return obj.entrySet().any { it.key.lowercase(Locale.US) in ROW_MARKER_KEYS }
    }

    private fun emptyReport() = DueListReport(rows = emptyList(), totalDebit = 0.0, totalCredit = 0.0, netBalance = 0.0)

    // ---- helpers ------------------------------------------------------------

    private fun JsonObject.fieldMap(): Map<String, JsonElement> =
        entrySet().associate { it.key.lowercase(Locale.US) to it.value }

    private fun Map<String, JsonElement>.string(candidates: List<String>): String =
        stringOrNull(candidates) ?: ""

    private fun Map<String, JsonElement>.stringOrNull(candidates: List<String>): String? {
        for (key in candidates) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty() && s != "0") return s
            }
        }
        return null
    }

    private fun Map<String, JsonElement>.number(candidates: List<String>): Double {
        for (key in candidates) {
            val el = get(key.lowercase(Locale.US))?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                el.asString.replace(",", "").trim().toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
}
