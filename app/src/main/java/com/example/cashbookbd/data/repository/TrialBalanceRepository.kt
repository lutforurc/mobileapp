package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.TrialBalanceReport
import com.example.cashbookbd.ui.reports.model.TrialBalanceRow
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/**
 * Backs the Trial Balance Details (Level 4) report:
 * `GET /reports/trialbalance-level4?branch_id=&start_date=&end_date=`.
 *
 * The exact JSON keys aren't fixed across the backend's report endpoints, so each
 * column is read from a small list of candidate keys. A 401 sets
 * [Resource.Error.isUnauthorized]; a 403 yields a clear permission message.
 */
class TrialBalanceRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val PATH = "reports/trialbalance-level4"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        private val ROW_ARRAY_KEYS = listOf("rows", "items", "details", "data", "transactions", "list")
        private val TOTAL_REGEX = Regex("(?i)^\\s*(grand\\s+)?total")

        // Candidate keys per column, matched case-insensitively. The Level-4
        // endpoint uses the `*_bal` names (first in each list); the others are
        // kept as fallbacks for sibling report endpoints.
        private val DESCRIPTION_KEYS = listOf(
            "name", "description", "head", "account_name", "account", "particulars",
            "ledger_name", "title", "coa_name",
        )
        private val OPENING_DR_KEYS = listOf("opening_debit_bal", "opening_debit", "opening_dr", "op_debit")
        private val OPENING_CR_KEYS = listOf("opening_credit_bal", "opening_credit", "opening_cr", "op_credit")
        private val MOVEMENT_DR_KEYS =
            listOf("movement_debit_bal", "movement_debit", "movement_dr", "trans_debit", "transaction_debit", "debit")
        private val MOVEMENT_CR_KEYS =
            listOf("movement_credit_bal", "movement_credit", "movement_cr", "trans_credit", "transaction_credit", "credit")
        private val CLOSING_DR_KEYS = listOf("debit_bal", "closing_debit", "closing_dr", "cl_debit")
        private val CLOSING_CR_KEYS = listOf("credit_bal", "closing_credit", "closing_cr", "cl_credit")
    }

    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<TrialBalanceReport> = withContext(ioDispatcher) {
        val params = mapOf(
            "branch_id" to branchId.toString(),
            "start_date" to startDate,
            "end_date" to endDate,
        )
        try {
            val response = api.get(PATH, params)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission to view this report."
                )
                404 -> return@withContext Resource.Success(
                    TrialBalanceReport(rows = emptyList(), closingDebit = 0.0, closingCredit = 0.0)
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }
            parseBody(response.body())
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

    private fun parseBody(root: JsonElement?): Resource<TrialBalanceReport> {
        if (root == null) return Resource.Error("Invalid response from server.")

        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                val message = root.asJsonObject.getAsJsonObject("error")
                    ?.get("message")?.takeUnless { it.isJsonNull }?.asString
                    ?: root.asJsonObject.get("message")?.takeUnless { it.isJsonNull }?.asString
                return Resource.Error(message?.ifBlank { null } ?: "Couldn't load the trial balance.")
            }
        }

        val payload = unwrap(root)
        val all = extractRows(payload).mapNotNull { it.asJsonObjectOrNull()?.toRow() }

        // A backend "Total" row (if any) supplies the closing totals; exclude it
        // from the table body. Otherwise the totals are summed from the rows.
        val totalRow = all.lastOrNull { TOTAL_REGEX.containsMatchIn(it.description) }
        val dataRows = all.filter { it !== totalRow }
        val closingDebit = totalRow?.closingDebit ?: dataRows.sumOf { it.closingDebit }
        val closingCredit = totalRow?.closingCredit ?: dataRows.sumOf { it.closingCredit }

        return Resource.Success(
            TrialBalanceReport(
                rows = dataRows,
                closingDebit = closingDebit,
                closingCredit = closingCredit,
            )
        )
    }

    /** Peels the `data` / `data.data` envelope produced by the backend helpers. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    private fun extractRows(payload: JsonElement): List<JsonElement> {
        if (payload.isJsonArray) return payload.asJsonArray.toList()
        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            for (key in ROW_ARRAY_KEYS) {
                val value = obj.get(key)?.takeUnless { it.isJsonNull }
                if (value != null && value.isJsonArray) return value.asJsonArray.toList()
            }
        }
        return emptyList()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private fun JsonObject.toRow(): TrialBalanceRow {
        // Index by lowercased key so "NAME" matches "name", etc.
        val fields = entrySet().associate { it.key.lowercase(Locale.US) to it.value }
        return TrialBalanceRow(
            description = fields.readString(DESCRIPTION_KEYS),
            openingDebit = fields.readNumber(OPENING_DR_KEYS),
            openingCredit = fields.readNumber(OPENING_CR_KEYS),
            movementDebit = fields.readNumber(MOVEMENT_DR_KEYS),
            movementCredit = fields.readNumber(MOVEMENT_CR_KEYS),
            closingDebit = fields.readNumber(CLOSING_DR_KEYS),
            closingCredit = fields.readNumber(CLOSING_CR_KEYS),
        )
    }

    private fun Map<String, JsonElement>.readString(candidates: List<String>): String {
        for (key in candidates) {
            val el = get(key)?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) return el.asString.trim()
        }
        return ""
    }

    private fun Map<String, JsonElement>.readNumber(candidates: List<String>): Double {
        for (key in candidates) {
            val el = get(key)?.takeUnless { it.isJsonNull } ?: continue
            if (el.isJsonPrimitive) {
                val value = el.asString.replace(",", "").trim().toDoubleOrNull()
                if (value != null) return value
            }
        }
        return 0.0
    }
}
