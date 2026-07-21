package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.BankDetailRow
import com.example.cashbookbd.ui.reports.model.CashBankReport
import com.example.cashbookbd.ui.reports.model.CashBankSummaryRow
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Backs the Cash & Bank (Received & Payment) report:
 * `GET /reports/cash-bank-received-payment?branch_id=&start_date=&end_date=`
 * with dates as `dd/MM/yyyy`.
 *
 * This endpoint's response is a shape of its own — neither the `{success, data}`
 * envelope nor a bare payload, but two sibling arrays with no `success` field at
 * all:
 *
 * ```
 * { "data": [ summary rows ], "bank_details": [ bank rows ] }
 * ```
 *
 * So the generic report parser cannot serve it: that one peels `data` and keeps
 * a single array, which would silently drop `bank_details`. Both arrays are read
 * explicitly here, and the envelope form is still tolerated in case the backend
 * ever wraps it.
 */
class CashBankRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        const val PATH = "reports/cash-bank-received-payment"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNPROCESSABLE = 422
    }

    /** [startDate] and [endDate] must already be `dd/MM/yyyy`, as the web sends. */
    suspend fun fetch(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<CashBankReport> = withContext(ioDispatcher) {
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

                HTTP_NOT_FOUND -> return@withContext Resource.Success(
                    CashBankReport(rows = emptyList(), bankDetails = emptyList())
                )

                // The endpoint validates branch_id/start_date/end_date and
                // answers 422 with the field message, which is worth showing.
                HTTP_UNPROCESSABLE -> return@withContext Resource.Error(
                    validationMessage(response.errorBody()?.string())
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

    private fun parseBody(root: JsonElement?): Resource<CashBankReport> {
        if (root == null || !root.isJsonObject) return Resource.Error("Invalid response from server.")
        val obj = root.asJsonObject

        // No `success` field is expected here, but a false one would mean a real
        // failure rather than an empty report — this endpoint always answers
        // with at least the Opening Balance row.
        val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        if (success == false) {
            val message = obj.getAsJsonObject("error")?.get("message")
                ?.takeUnless { it.isJsonNull }?.asString
                ?: obj.get("message")?.takeUnless { it.isJsonNull }?.asString
            return Resource.Error(message?.ifBlank { null } ?: "Couldn't load the report.")
        }

        val payload = if (success != null) unwrap(obj) else obj

        return Resource.Success(
            CashBankReport(
                rows = payload.array("data").mapNotNull { it.obj()?.toSummaryRow() },
                bankDetails = payload.array("bank_details").mapNotNull { it.obj()?.toBankRow() },
            )
        )
    }

    /** Peels a `data` / `data.data` envelope, should the backend ever add one. */
    private fun unwrap(root: JsonObject): JsonObject {
        val data = root.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (!data.isJsonObject) return root
        val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
        return if (inner != null && inner.isJsonObject) inner.asJsonObject else data.asJsonObject
    }

    private fun JsonObject.array(key: String): List<JsonElement> =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonArray }?.asJsonArray?.toList()
            ?: emptyList()

    private fun JsonElement.obj(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.toSummaryRow() = CashBankSummaryRow(
        name = text("name"),
        cashReceived = number("cash_debit"),
        cashPayment = number("cash_credit"),
        bankReceived = number("bank_debit"),
        bankPayment = number("bank_credit"),
    )

    private fun JsonObject.toBankRow() = BankDetailRow(
        bankName = text("bank_name"),
        received = number("received"),
        payment = number("payment"),
    )

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?: ""

    /** The aggregates arrive as strings from MySQL's SUM(), sometimes grouped. */
    private fun JsonObject.number(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.replace(",", "")?.trim()?.toDoubleOrNull()
            ?: 0.0

    private fun validationMessage(body: String?): String {
        val fallback = "Please choose a branch and a valid date range."
        if (body.isNullOrBlank()) return fallback
        return try {
            val obj = com.google.gson.JsonParser.parseString(body).asJsonObject
            obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}
