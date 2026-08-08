package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One line of the Expense Report — a head (group) or a level-4 account. */
data class ExpenseReportRow(
    /** coa3 id on a group row; coa4 id on a detail row. */
    val id: Long,
    /** The expense head's name (group rows and the detail rows' Group column). */
    val groupName: String,
    /** Detail rows only: the level-4 account's own name. */
    val accountName: String = "",
    /** Detail rows only: which head this account belongs to. */
    val coa3Id: Long = 0,
    val openingDebit: Double,
    val openingCredit: Double,
    val movementDebit: Double,
    val movementCredit: Double,
    val closingDebit: Double,
    val closingCredit: Double,
)

data class ExpenseReport(
    val groups: List<ExpenseReportRow>,
    val details: List<ExpenseReportRow>,
)

/**
 * The web's Expense Report — the Trial Balance Group figures narrowed to
 * expense heads server-side. One Apply makes both requests (groups + every
 * group's level-4 details), exactly as the web fetches the detail set up
 * front so opening a row costs nothing.
 */
class ExpenseReportRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetch(
        branchId: String,
        startDate: String,
        endDate: String,
    ): Resource<ExpenseReport> = withContext(ioDispatcher) {
        try {
            val params = mapOf(
                "branch_id" to branchId,
                "start_date" to startDate,
                "end_date" to endDate,
            )
            val groupsResponse = api.get("reports/expense-report", params)
            if (groupsResponse.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val groups = rows(groupsResponse.body()).map { it.toGroupRow() }

            // The details are one flat array for every group at once.
            val details = api.get("reports/expense-report-details", params)
                .takeIf { it.isSuccessful }
                ?.let { rows(it.body()).map { row -> row.toDetailRow() } }
                .orEmpty()

            Resource.Success(ExpenseReport(groups = groups, details = details))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Both endpoints answer a bare JSON array; tolerate an envelope anyway. */
    private fun rows(body: JsonElement?): List<JsonObject> {
        val array: JsonArray? = when {
            body == null -> null
            body.isJsonArray -> body.asJsonArray
            body.isJsonObject -> {
                var payload: JsonElement = body
                repeat(2) {
                    val inner = payload.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("data")?.takeUnless { it.isJsonNull }
                    if (inner != null) payload = inner
                }
                payload.takeIf { it.isJsonArray }?.asJsonArray
            }
            else -> null
        }
        return array?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
    }

    private fun JsonObject.toGroupRow(): ExpenseReportRow = ExpenseReportRow(
        id = long("id"),
        groupName = text("coal3_name").ifBlank { "Unnamed Head" },
        openingDebit = dbl("opening_debit_bal"),
        openingCredit = dbl("opening_credit_bal"),
        movementDebit = dbl("movement_debit_bal"),
        movementCredit = dbl("movement_credit_bal"),
        closingDebit = dbl("debit_bal"),
        closingCredit = dbl("credit_bal"),
    )

    private fun JsonObject.toDetailRow(): ExpenseReportRow = ExpenseReportRow(
        id = long("coa4_id"),
        groupName = text("coal3_name").ifBlank { "Unnamed Head" },
        accountName = text("NAME").ifBlank { "Unnamed Head" },
        coa3Id = long("coa3_id"),
        openingDebit = dbl("opening_debit_bal"),
        openingCredit = dbl("opening_credit_bal"),
        movementDebit = dbl("movement_debit_bal"),
        movementCredit = dbl("movement_credit_bal"),
        closingDebit = dbl("debit_bal"),
        closingCredit = dbl("credit_bal"),
    )

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.toDoubleOrNull()?.toLong() ?: 0L

    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}
