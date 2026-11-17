package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** One employee as "Advance for Purchase" lists them. */
data class PurchaseAdvanceStaff(
    val id: Long,
    val name: String,
    val designation: String,
    /** Positive: he holds the company's money. Negative: the company owes him. */
    val balance: Double,
    val lastAdvance: String,
    val lastMovement: String,
)

data class PurchaseAdvanceSummary(
    val held: Double = 0.0,
    val owed: Double = 0.0,
    /** The branch's transaction date the entries are booked on. */
    val date: String = "",
)

/** The head-vs-rows agreement check; `agrees` false means an orphan posting. */
data class PurchaseAdvanceCheck(
    val head: Double = 0.0,
    val rows: Double = 0.0,
    val agrees: Boolean = true,
)

data class PurchaseAdvanceBank(val id: Long, val name: String)

data class PurchaseAdvanceData(
    val staff: List<PurchaseAdvanceStaff> = emptyList(),
    val banks: List<PurchaseAdvanceBank> = emptyList(),
    val summary: PurchaseAdvanceSummary = PurchaseAdvanceSummary(),
    val check: PurchaseAdvanceCheck = PurchaseAdvanceCheck(),
)

/** One goods line under a bill row. */
data class PurchaseAdvanceItem(
    val name: String,
    val kind: String,
    val quantity: Double,
    val unit: String,
    val rate: Double,
    val amount: Double,
)

/** One movement in an employee's ledger. */
data class PurchaseAdvanceLedgerRow(
    val id: Long,
    val kind: String,
    val vrDate: String,
    val vrNo: String,
    val mainTrxId: Long?,
    val given: Double,
    val used: Double,
    val balance: Double,
    val boughtFrom: String,
    val note: String,
    val items: List<PurchaseAdvanceItem> = emptyList(),
)

data class PurchaseAdvanceLedger(
    val opening: Double = 0.0,
    val rows: List<PurchaseAdvanceLedgerRow> = emptyList(),
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val balance: Double = 0.0,
)

/**
 * Advance for Purchase (web `PurchaseAdvance.tsx` and its purchase-advance endpoints).
 *
 * ```
 * GET  purchase-advance/list                      purchase.advance.view
 * GET  purchase-advance/statement?employee_id&from_date&to_date
 * POST purchase-advance/give   { employee_id, amount, mode, bank_account, note }
 * POST purchase-advance/settle same
 * ```
 *
 * ⚠️ Not a loan: nothing here reaches the salary run. The balance decides which
 * way a settlement goes — above zero he hands money back, below zero the
 * company pays him.
 */
class PurchaseAdvanceRepository(private val api: WebApi) {

    suspend fun fetchList(): Resource<PurchaseAdvanceData> =
        when (val result = api.get("$BASE/list")) {
            is Resource.Success -> {
                val data = payloadOf(result.data)
                Resource.Success(
                    PurchaseAdvanceData(
                        staff = data.jObjs("employees").map { it.toStaff() },
                        banks = data.jObjs("banks").map {
                            PurchaseAdvanceBank(it.jLong("id") ?: 0L, it.jStr("name"))
                        },
                        summary = data.jObj("summary")?.let {
                            PurchaseAdvanceSummary(
                                held = it.jNum("held"),
                                owed = it.jNum("owed"),
                                date = it.jStr("date"),
                            )
                        } ?: data.let {
                            PurchaseAdvanceSummary(
                                held = it.jNum("held"),
                                owed = it.jNum("owed"),
                                date = it.jStr("date"),
                            )
                        },
                        check = data.jObj("check")?.let {
                            PurchaseAdvanceCheck(
                                head = it.jNum("head"),
                                rows = it.jNum("rows"),
                                agrees = it.jBool("agrees"),
                            )
                        } ?: PurchaseAdvanceCheck(),
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    suspend fun fetchStatement(
        employeeId: Long,
        fromDate: String,
        toDate: String,
    ): Resource<PurchaseAdvanceLedger> {
        val params = mapOf(
            "employee_id" to employeeId.toString(),
            "from_date" to fromDate,
            "to_date" to toDate,
        )
        return when (val result = api.get("$BASE/statement", params)) {
            is Resource.Success -> {
                val data = payloadOf(result.data)
                Resource.Success(
                    PurchaseAdvanceLedger(
                        opening = data.jNum("opening"),
                        rows = data.jObjs("rows").map { it.toLedgerRow() },
                        debit = data.jNum("debit"),
                        credit = data.jNum("credit"),
                        balance = data.jNum("balance"),
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /**
     * Gives an advance, or settles one. [bankAccount] travels only in bank mode;
     * a refusal rides HTTP 200 with success:false and WebApi turns it into the
     * error message.
     */
    suspend fun submit(
        give: Boolean,
        employeeId: Long,
        amount: Double,
        bank: Boolean,
        bankAccount: Long?,
        note: String,
    ): Resource<String> {
        val body = JsonObject().apply {
            addProperty("employee_id", employeeId)
            addProperty("amount", amount)
            addProperty("mode", if (bank) "bank" else "cash")
            add("bank_account", if (bank && bankAccount != null) JsonPrimitive(bankAccount) else JsonNull.INSTANCE)
            add("note", note.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        }
        val path = if (give) "$BASE/give" else "$BASE/settle"
        return when (val result = api.post(path, body)) {
            is Resource.Success -> {
                val vrNo = payloadOf(result.data).jStr("vr_no")
                    .ifBlank { result.data.jObj("data")?.jObj("data")?.jStr("vr_no").orEmpty() }
                Resource.Success(
                    result.data.jStr("message").ifBlank {
                        if (vrNo.isBlank()) "Saved" else "Voucher $vrNo saved"
                    }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /** `data.data`, else `data`, else the body — the two envelopes the API uses. */
    private fun payloadOf(body: JsonObject): JsonObject {
        val data = body.jObj("data") ?: return body
        return data.jObj("data") ?: data
    }

    private fun JsonObject.toStaff() = PurchaseAdvanceStaff(
        id = jLong("id") ?: 0L,
        name = jStr("name"),
        designation = jStr("designation"),
        balance = jNum("balance"),
        lastAdvance = jStr("last_advance").take(10),
        lastMovement = jStr("last_movement").take(10),
    )

    private fun JsonObject.toLedgerRow() = PurchaseAdvanceLedgerRow(
        id = jLong("id") ?: 0L,
        kind = jStr("kind"),
        vrDate = jStr("vr_date"),
        vrNo = jStr("vr_no"),
        mainTrxId = jLong("main_trx_id"),
        given = jNum("given"),
        used = jNum("used"),
        balance = jNum("balance"),
        boughtFrom = jStr("bought_from"),
        note = jStr("note"),
        items = jObjs("items").map {
            PurchaseAdvanceItem(
                name = it.jStr("name"),
                kind = it.jStr("kind"),
                quantity = it.jNum("quantity"),
                unit = it.jStr("unit"),
                rate = it.jNum("rate"),
                amount = it.jNum("amount"),
            )
        },
    )

    companion object {
        private const val BASE = "purchase-advance"
    }
}
