package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** What the two columns stood at before the first day shown, cash and bank apart. */
data class CashBankOpening(
    val cash: Double,
    val bank: Double,
    val cashDebit: Double,
    val cashCredit: Double,
    val bankDebit: Double,
    val bankCredit: Double,
)

/** One voucher that touched the till or the bank — a contra fills two cells on one row. */
data class CashBankRow(
    val mtmId: Long,
    val vrNo: String,
    val vrDate: String,
    val debitCash: Double,
    val debitBank: Double,
    val creditCash: Double,
    val creditBank: Double,
    val description: String,
    val note: String,
    val bankName: String,
    val isContra: Boolean,
)

data class CashBankMovement(
    val debitCash: Double,
    val debitBank: Double,
    val creditCash: Double,
    val creditBank: Double,
)

/** Balance c/d — carried on the side OPPOSITE where it opened. */
data class CashBankClosing(
    val cash: Double,
    val bank: Double,
    val cashDebit: Double,
    val cashCredit: Double,
    val bankDebit: Double,
    val bankCredit: Double,
)

data class CashBankTotals(
    val debitCash: Double,
    val creditCash: Double,
    val debitBank: Double,
    val creditBank: Double,
)

data class CashBankBookView(
    val branchName: String,
    val from: String,
    val to: String,
    val opening: CashBankOpening,
    val rows: List<CashBankRow>,
    val movement: CashBankMovement,
    val closing: CashBankClosing,
    val totals: CashBankTotals,
    val cashBalanced: Boolean,
    val bankBalanced: Boolean,
    val banks: List<SelectorOption>,
) {
    /** False the moment either side of either account fails to foot — the report says so rather than printing a lie. */
    val isBalanced: Boolean get() = cashBalanced && bankBalanced
}

/**
 * The cash book with a bank column beside the cash one — the paper double-
 * column book every accountant here learned on. The two columns are never
 * summed together: a contra (till → bank) fills a cell in each on the same
 * row, marked, so nobody reading it counts the same money twice.
 */
class CashBankBookRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetch(
        branchId: String,
        from: String,
        to: String,
        bankAccountId: Long?,
    ): Resource<CashBankBookView> = withContext(ioDispatcher) {
        try {
            val params = buildMap {
                put("branch_id", branchId)
                put("start_date", from)
                put("end_date", to)
                bankAccountId?.let { put("bank_account_id", it.toString()) }
            }
            val response = api.get("reports/cash-book-two-column", params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see the cash book.")
            }
            val payload = payloadOf(response.body())
                ?: return@withContext Resource.Error("The cash & bank book could not be read.")
            val opening = payload.obj("opening")
            val movement = payload.obj("movement")
            val closing = payload.obj("closing")
            val totals = payload.obj("totals")
            val balanced = payload.obj("balanced")
            Resource.Success(
                CashBankBookView(
                    branchName = payload.obj("branch")?.text("name").orEmpty(),
                    from = payload.text("from"),
                    to = payload.text("to"),
                    opening = CashBankOpening(
                        cash = opening?.dbl("cash") ?: 0.0,
                        bank = opening?.dbl("bank") ?: 0.0,
                        cashDebit = opening?.dbl("cash_debit") ?: 0.0,
                        cashCredit = opening?.dbl("cash_credit") ?: 0.0,
                        bankDebit = opening?.dbl("bank_debit") ?: 0.0,
                        bankCredit = opening?.dbl("bank_credit") ?: 0.0,
                    ),
                    rows = payload.arr("rows").mapNotNull { it.asObject()?.toRow() },
                    movement = CashBankMovement(
                        debitCash = movement?.dbl("debit_cash") ?: 0.0,
                        debitBank = movement?.dbl("debit_bank") ?: 0.0,
                        creditCash = movement?.dbl("credit_cash") ?: 0.0,
                        creditBank = movement?.dbl("credit_bank") ?: 0.0,
                    ),
                    closing = CashBankClosing(
                        cash = closing?.dbl("cash") ?: 0.0,
                        bank = closing?.dbl("bank") ?: 0.0,
                        cashDebit = closing?.dbl("cash_debit") ?: 0.0,
                        cashCredit = closing?.dbl("cash_credit") ?: 0.0,
                        bankDebit = closing?.dbl("bank_debit") ?: 0.0,
                        bankCredit = closing?.dbl("bank_credit") ?: 0.0,
                    ),
                    totals = CashBankTotals(
                        debitCash = totals?.dbl("debit_cash") ?: 0.0,
                        creditCash = totals?.dbl("credit_cash") ?: 0.0,
                        debitBank = totals?.dbl("debit_bank") ?: 0.0,
                        creditBank = totals?.dbl("credit_bank") ?: 0.0,
                    ),
                    cashBalanced = balanced?.get("cash")?.takeUnless { it.isJsonNull }?.asBoolean ?: true,
                    bankBalanced = balanced?.get("bank")?.takeUnless { it.isJsonNull }?.asBoolean ?: true,
                    banks = payload.arr("banks").mapNotNull { el ->
                        val o = el.asObject() ?: return@mapNotNull null
                        val id = o.long("id") ?: return@mapNotNull null
                        SelectorOption(id.toString(), o.text("name"))
                    },
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonObject.toRow(): CashBankRow? {
        val id = long("mtm_id") ?: return null
        return CashBankRow(
            mtmId = id,
            vrNo = text("vr_no"),
            vrDate = text("vr_date").take(10),
            debitCash = dbl("debit_cash") ?: 0.0,
            debitBank = dbl("debit_bank") ?: 0.0,
            creditCash = dbl("credit_cash") ?: 0.0,
            creditBank = dbl("credit_bank") ?: 0.0,
            description = text("description"),
            note = text("note"),
            bankName = text("bank_name"),
            isContra = get("is_contra")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
        )
    }

    /** Peels the `data` / `data.data` envelope. */
    private fun payloadOf(body: JsonElement?): JsonObject? {
        var payload: JsonElement? = body?.takeIf { it.isJsonObject }
        repeat(2) {
            val inner = payload?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.dbl(key: String): Double? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.replace(",", "")?.toDoubleOrNull()
}
