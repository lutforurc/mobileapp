package com.example.cashbookbd.data.repository

import android.content.Context
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// ---------------------------------------------------------------------------
// Shared shapes
// ---------------------------------------------------------------------------

/** A level-4 chart-of-accounts head, as every one of these pickers hands it over. */
data class AccountHead(
    val id: Long,
    val name: String,
    /** The level-3 group it hangs under — shown beneath the name, never instead of it. */
    val groupName: String = "",
)

// ---------------------------------------------------------------------------
// Bank reconciliation
// ---------------------------------------------------------------------------

/** A month that was closed and signed, as the account read lists them. */
data class BankRecClosedMonth(
    val id: Long,
    val coa4Id: Long,
    val statementDate: String,
    val statementBalance: Double,
    val note: String,
    val accountName: String,
)

/** What `bank-reconciliation/accounts` answers with: the banks, and their signed months. */
data class BankRecAccounts(
    val accounts: List<AccountHead>,
    val history: List<BankRecClosedMonth>,
    val note: String,
)

/**
 * The five figures, in the order a reconciliation is read out loud.
 *
 * [difference] is SHOWN and never posted — that is the whole point of the
 * exercise. A reconciliation that wrote its own entries would hide the very
 * thing it exists to reveal.
 */
data class BankRecTotals(
    val bookBalance: Double,
    val unclearedOut: Double,
    val unclearedIn: Double,
    val expectedBank: Double,
    val statementBalance: Double,
    val difference: Double,
    val balanced: Boolean,
)

/** One leg of the bank account, and whether the statement has shown it yet. */
data class BankRecRow(
    /** The transaction-detail id — what `tick` sends. */
    val id: Long,
    val debit: Double,
    val credit: Double,
    val remarks: String,
    val reconciledOn: String,
    val reconId: Long?,
    val mainTrxId: Long?,
    val vrNo: String,
    val vrDate: String,
    val voucherType: String,
    val note: String,
    val reference: String,
    val cleared: Boolean,
) {
    /** What it was: the leg's own remark, else the voucher's note. */
    val what: String get() = remarks.ifBlank { note }
}

/** One reconciliation as read for a bank and a statement date. */
data class BankRecView(
    val asAt: String,
    /** Non-null when this month has already been closed and signed. */
    val savedId: Long?,
    val savedNote: String,
    val savedStatementBalance: Double?,
    val totals: BankRecTotals,
    val rows: List<BankRecRow>,
)

// ---------------------------------------------------------------------------
// Cheque register
// ---------------------------------------------------------------------------

/** One piece of paper: which cheque, on which bank, dated when, and what became of it. */
data class ChequeRow(
    val id: Long,
    /** received / issued. */
    val direction: String,
    val chequeNo: String,
    val bankName: String,
    val branchName: String,
    /** The date written on the cheque. */
    val chequeDate: String,
    /** The date the current status happened on. */
    val onDate: String,
    val partyCoa4Id: Long?,
    val partyName: String,
    val partyHead: String,
    val accountCoa4Id: Long?,
    val accountName: String,
    val amount: Double,
    /** in_hand / deposited / cleared / dishonoured / cancelled. */
    val status: String,
    val mainTrxId: Long?,
    val vrNo: String,
    val vrDate: String,
    val note: String,
    val returnReason: String,
    val depositedOn: String,
    val clearedOn: String,
) {
    /** Whose cheque it is — the typed name, else the head it was booked against. */
    val whose: String get() = partyName.ifBlank { partyHead }

    /** Still live: neither cleared, bounced nor cancelled. */
    val isOpen: Boolean get() = status == "in_hand" || status == "deposited"
}

/** "3 received due · 2 issued due" — the line pinned above the register. */
data class ChequeDue(
    val direction: String,
    val count: Int,
    val total: Double,
)

/** A page of the register, with everything its filters and its add form need. */
data class ChequeRegisterPage(
    val rows: List<ChequeRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
    val due: List<ChequeDue>,
    val partyHeads: List<AccountHead>,
    val bankHeads: List<AccountHead>,
    val expenseHeads: List<AccountHead>,
    val note: String,
)

/** One leg of the voucher a bounce would pass. */
data class VoucherLeg(
    val coa4Id: Long,
    val debit: Double,
    val credit: Double,
    val note: String,
    val head: String,
)

/**
 * What a bounce would do, shown BEFORE it is done.
 *
 * Banking and clearing post nothing — they are facts about a piece of paper.
 * The bounce is the exception: it turns the receipt voucher around leg for leg,
 * so the legs are laid out first and confirmed by hand.
 */
data class ChequeDishonourPlan(
    val chequeNo: String,
    val partyName: String,
    val amount: Double,
    val legs: List<VoucherLeg>,
)

// ---------------------------------------------------------------------------
// Year closing
// ---------------------------------------------------------------------------

/** A year that has been closed — or the one just closed, as `run` reports it. */
data class YearClosingRow(
    val id: Long,
    val yearEnd: String,
    val profit: Double,
    val mainTrxId: Long?,
    val vrNo: String,
    val note: String,
)

/** Every profit-and-loss head emptied into capital, leg by leg, before it happens. */
data class YearClosingPlan(
    val yearStart: String,
    val yearEnd: String,
    val incomeTotal: Double,
    val expenseTotal: Double,
    /** Positive is a profit, negative a loss. */
    val profit: Double,
    val headsClosed: Int,
    val legs: List<VoucherLeg>,
)

/** The whole year-closing screen in one read. */
data class YearClosingView(
    val plan: YearClosingPlan,
    /** Non-null when this year end has already been closed. */
    val already: YearClosingRow?,
    val history: List<YearClosingRow>,
    val capitalHeads: List<AccountHead>,
    val note: String,
)

// ---------------------------------------------------------------------------
// Budget
// ---------------------------------------------------------------------------

/** What was meant to be spent on one head, against what was. */
data class BudgetRow(
    val coa4Id: Long,
    val name: String,
    val groupName: String,
    val budget: Double,
    /** The share of the budget the months elapsed have earned. */
    val expected: Double,
    val actual: Double,
    val left: Double,
    /** actual ÷ expected as a percentage; 0 when nothing was expected yet. */
    val againstExpected: Double,
)

data class BudgetTotals(
    val budget: Double,
    val expected: Double,
    val actual: Double,
    val left: Double,
)

/** A project the budget may be cut by (optional — most budgets are company-wide). */
data class ProjectOption(val id: Long, val name: String)

data class BudgetView(
    val yearStart: String,
    val yearEnd: String,
    /** The ledger is read up to here; the actuals are never stored. */
    val actualsUpTo: String,
    val monthsElapsed: Int,
    val rows: List<BudgetRow>,
    val totals: BudgetTotals,
    val projects: List<ProjectOption>,
    val note: String,
)

// ---------------------------------------------------------------------------
// Ageing
// ---------------------------------------------------------------------------

/**
 * One party's debt, split by how long it has stood.
 *
 * The bucket keys carry an EN DASH ("0–30", not "0-30"): they are the server's
 * own keys and are echoed back from [AgeingView.buckets], never re-typed.
 */
data class AgeingRow(
    val coa4Id: Long,
    val name: String,
    val partyName: String,
    val mobile: String,
    /** Null when no terms are set — that party pays cash. */
    val creditDays: Int?,
    val buckets: Map<String, Double>,
    val outstanding: Double,
    val notDue: Double,
    val advance: Double,
    val oldestDue: String,
    val oldestDays: Int,
)

data class AgeingTotals(
    val outstanding: Double,
    val advance: Double,
    val notDue: Double,
    val buckets: Map<String, Double>,
)

data class AgeingView(
    val asOn: String,
    /** receivable / payable. */
    val side: String,
    val buckets: List<String>,
    val rows: List<AgeingRow>,
    val totals: AgeingTotals,
    val note: String,
)

// ---------------------------------------------------------------------------
// Audit trail
// ---------------------------------------------------------------------------

/** One field that moved: what it was, and what it became. */
data class AuditChange(
    val field: String,
    val old: String,
    val new: String,
)

/** One thing somebody did to a voucher. */
data class AuditEvent(
    val id: Long,
    /**
     * trail — a recorded change, with its fields; voucher — a row found on the
     * voucher itself, which knows only who and when.
     */
    val source: String,
    val at: String,
    val user: String,
    val action: String,
    val vrNo: String,
    val vrDate: String,
    val mainTrxId: Long?,
    val changes: List<AuditChange>,
) {
    val isFromVoucher: Boolean get() = source == "voucher"
}

data class AuditUser(val id: Long, val name: String)

data class AuditTrailView(
    val from: String,
    val to: String,
    val events: List<AuditEvent>,
    val users: List<AuditUser>,
    val note: String,
)

// ---------------------------------------------------------------------------
// The repository
// ---------------------------------------------------------------------------

/**
 * The six accounts screens' reads and writes, in one place.
 *
 * They share an envelope (`data.data`), a refusal style (a `success:false` with
 * a SENTENCE, at 422 or at 200 — the flag is the verdict, not the status code)
 * and a set of chart-of-accounts pickers, so one repository keeps the parsing
 * honest across all of them.
 *
 * A sentence each screen needs to be read right:
 *  - **Bank reconciliation** — the difference is shown, never posted.
 *  - **Cheque register** — the voucher records the money; this records the
 *    paper. Only the bounce posts.
 *  - **Year closing** — every profit-and-loss head emptied into capital.
 *  - **Budget** — what was meant to be spent, against what was; the actuals are
 *    never stored, they are read from the ledger on every request.
 *  - **Ageing** — age counts from the day a bill fell due, and a payment clears
 *    the oldest bill still standing.
 *  - **Audit trail** — who changed which voucher, and when.
 */
class AccountsRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ——— Bank reconciliation ————————————————————————————————————————————

    /** The banks that can be reconciled, and the months already signed. */
    suspend fun fetchBankAccounts(): Resource<BankRecAccounts> = read("bank-reconciliation/accounts") { p ->
        BankRecAccounts(
            accounts = p.array("accounts").mapObjects { it.toHead() },
            history = p.array("history").mapObjects { o ->
                BankRecClosedMonth(
                    id = o.long("id") ?: return@mapObjects null,
                    coa4Id = o.long("coa4_id") ?: 0L,
                    statementDate = o.date("statement_date"),
                    statementBalance = o.num("statement_balance"),
                    note = o.text("note"),
                    accountName = o.text("account_name").ifBlank { o.text("name") },
                )
            },
            note = p.text("note"),
        )
    }

    /**
     * One account against one statement date.
     *
     * [statementBalance] is what the bank says; sent blank it simply is not
     * compared, and the screen shows the book side alone.
     */
    suspend fun fetchBankReconciliation(
        coa4Id: Long,
        statementDate: String,
        statementBalance: String,
        branchId: Long?,
    ): Resource<BankRecView> {
        val params = buildMap {
            put("coa4_id", coa4Id.toString())
            put("statement_date", statementDate)
            statementBalance.trim().takeIf { it.isNotEmpty() }?.let { put("statement_balance", it) }
            branchId?.let { put("branch_id", it.toString()) }
        }
        return read("bank-reconciliation", params) { p ->
            val t = p.obj("totals")
            val saved = p.obj("saved")
            BankRecView(
                asAt = p.date("as_at"),
                savedId = saved?.long("id"),
                savedNote = saved?.text("note").orEmpty(),
                savedStatementBalance = saved?.numOrNull("statement_balance"),
                totals = BankRecTotals(
                    bookBalance = t.num("book_balance"),
                    unclearedOut = t.num("uncleared_out"),
                    unclearedIn = t.num("uncleared_in"),
                    expectedBank = t.num("expected_bank"),
                    statementBalance = t.num("statement_balance"),
                    difference = t.num("difference"),
                    balanced = t?.flag("balanced") == true,
                ),
                rows = p.array("rows").mapObjects { o ->
                    BankRecRow(
                        id = o.long("id") ?: return@mapObjects null,
                        debit = o.num("debit"),
                        credit = o.num("credit"),
                        remarks = o.text("remarks"),
                        reconciledOn = o.date("reconciled_on"),
                        reconId = o.long("recon_id"),
                        mainTrxId = o.long("main_trx_id"),
                        vrNo = o.text("vr_no"),
                        vrDate = o.date("vr_date"),
                        voucherType = o.text("voucher_type"),
                        note = o.text("note"),
                        reference = o.text("reference"),
                        cleared = o.flag("cleared"),
                    )
                },
            )
        }
    }

    /**
     * Ticks legs off the statement, or lets them go again.
     *
     * A null [reconciledOn] unticks. It travels by being ABSENT rather than as
     * an explicit JSON null: the app's Gson drops nulls, and the endpoint reads
     * `$data['reconciled_on'] ?? null`, so an absent key and a null key mean the
     * same thing to it. The ids must be numbers, hence the raw JSON body.
     */
    suspend fun tickBankRows(
        ids: List<Long>,
        coa4Id: Long,
        reconciledOn: String?,
    ): Resource<String> {
        val body = JsonObject().apply {
            add("ids", JsonArray().apply { ids.forEach { add(it) } })
            addProperty("coa4_id", coa4Id)
            reconciledOn?.takeIf { it.isNotBlank() }?.let { addProperty("reconciled_on", it) }
        }
        return post("bank-reconciliation/tick", body, "Ticked off.")
    }

    /** Signs the month off. Refused, with the sentence why, while it does not agree. */
    suspend fun closeBankMonth(
        coa4Id: Long,
        statementDate: String,
        statementBalance: String,
        branchId: Long?,
        note: String,
    ): Resource<String> {
        val body = JsonObject().apply {
            addProperty("coa4_id", coa4Id)
            addProperty("statement_date", statementDate)
            addProperty("statement_balance", statementBalance.trim().toDoubleOrNull() ?: 0.0)
            branchId?.let { addProperty("branch_id", it) }
            note.trim().takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
        }
        return post("bank-reconciliation/close", body, "The month is closed.")
    }

    /** Lets go of exactly the ticks made in that month. */
    suspend fun reopenBankMonth(id: Long): Resource<String> =
        post("bank-reconciliation/reopen/$id", JsonObject(), "The month is open again.")

    // ——— Cheque register ————————————————————————————————————————————————

    suspend fun fetchCheques(
        branchId: Long?,
        direction: String,
        status: String,
        query: String,
        page: Int,
        perPage: Int = 20,
    ): Resource<ChequeRegisterPage> {
        val params = buildMap {
            branchId?.let { put("branch_id", it.toString()) }
            direction.takeIf { it.isNotBlank() }?.let { put("direction", it) }
            status.takeIf { it.isNotBlank() }?.let { put("status", it) }
            query.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
            put("per_page", perPage.toString())
            put("page", page.toString())
        }
        return read("cheque-register", params) { p ->
            val paginator = p.obj("rows")
            ChequeRegisterPage(
                rows = (paginator?.array("data") ?: p.array("rows")).mapObjects { it.toCheque() },
                currentPage = paginator?.int("current_page") ?: page,
                lastPage = paginator?.int("last_page") ?: 1,
                total = paginator?.int("total") ?: 0,
                due = p.array("due").mapObjects { o ->
                    ChequeDue(
                        direction = o.text("direction"),
                        count = o.int("count") ?: 0,
                        total = o.num("total"),
                    )
                },
                partyHeads = p.array("party_heads").mapObjects { it.toHead() },
                bankHeads = p.array("bank_heads").mapObjects { it.toHead() },
                expenseHeads = p.array("expense_heads").mapObjects { it.toHead() },
                note = p.text("note"),
            )
        }
    }

    /**
     * Writes the paper down (or corrects it). Nothing is posted here.
     *
     * Either a [partyCoa4Id] or a typed [partyName] is required: a cheque with
     * nobody's name on it is not a record of anything.
     */
    suspend fun saveCheque(
        id: Long?,
        branchId: Long?,
        direction: String,
        chequeNo: String,
        bankName: String,
        branchName: String,
        chequeDate: String,
        onDate: String,
        partyCoa4Id: Long?,
        partyName: String,
        accountCoa4Id: Long?,
        amount: String,
        note: String,
    ): Resource<String> {
        val body = JsonObject().apply {
            id?.let { addProperty("id", it) }
            branchId?.let { addProperty("branch_id", it) }
            addProperty("direction", direction)
            addProperty("cheque_no", chequeNo.trim())
            bankName.trim().takeIf { it.isNotEmpty() }?.let { addProperty("bank_name", it) }
            branchName.trim().takeIf { it.isNotEmpty() }?.let { addProperty("branch_name", it) }
            addProperty("cheque_date", chequeDate)
            onDate.takeIf { it.isNotBlank() }?.let { addProperty("on_date", it) }
            partyCoa4Id?.let { addProperty("party_coa4_id", it) }
            partyName.trim().takeIf { it.isNotEmpty() }?.let { addProperty("party_name", it) }
            accountCoa4Id?.let { addProperty("account_coa4_id", it) }
            addProperty("amount", amount.trim().toDoubleOrNull() ?: 0.0)
            note.trim().takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
        }
        return post("cheque-register/store", body, "Cheque saved.")
    }

    /** Banking, clearing, cancelling — facts about the paper, and they post nothing. */
    suspend fun setChequeStatus(id: Long, status: String, onDate: String?): Resource<String> {
        val body = JsonObject().apply {
            addProperty("status", status)
            onDate?.takeIf { it.isNotBlank() }?.let { addProperty("on_date", it) }
        }
        return post("cheque-register/status/$id", body, "Status changed.")
    }

    /** What a bounce would post — read and shown before anything is written. */
    suspend fun fetchDishonourPlan(
        id: Long,
        charge: String,
        chargeCoa4Id: Long?,
    ): Resource<ChequeDishonourPlan> {
        val params = buildMap {
            charge.trim().takeIf { it.isNotEmpty() }?.let { put("charge", it) }
            chargeCoa4Id?.let { put("charge_coa4_id", it.toString()) }
        }
        return read("cheque-register/dishonour/plan/$id", params) { p ->
            val cheque = p.obj("cheque")
            ChequeDishonourPlan(
                chequeNo = cheque?.text("cheque_no").orEmpty(),
                partyName = cheque?.text("party_name").orEmpty()
                    .ifBlank { cheque?.text("party_head").orEmpty() },
                amount = cheque?.num("amount") ?: 0.0,
                legs = p.array("legs").mapObjects { it.toLeg() },
            )
        }
    }

    /** The one act on this screen that posts a real voucher. */
    suspend fun dishonourCheque(
        id: Long,
        onDate: String,
        reason: String,
        charge: String,
        chargeCoa4Id: Long?,
    ): Resource<String> {
        val body = JsonObject().apply {
            addProperty("on_date", onDate)
            reason.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
            charge.trim().toDoubleOrNull()?.takeIf { it > 0.0 }?.let { addProperty("charge", it) }
            chargeCoa4Id?.let { addProperty("charge_coa4_id", it) }
        }
        return post("cheque-register/dishonour/$id", body, "The cheque was bounced.")
    }

    suspend fun deleteCheque(id: Long): Resource<String> =
        post("cheque-register/delete/$id", JsonObject(), "Cheque deleted.")

    // ——— Year closing ————————————————————————————————————————————————————

    suspend fun fetchYearClosingPlan(
        yearEnd: String,
        branchId: Long?,
        capitalCoa4Id: Long?,
    ): Resource<YearClosingView> {
        val params = buildMap {
            put("year_end", yearEnd)
            branchId?.let { put("branch_id", it.toString()) }
            capitalCoa4Id?.let { put("capital_coa4_id", it.toString()) }
        }
        return read("year-closing/plan", params) { p ->
            val plan = p.obj("plan")
            YearClosingView(
                plan = YearClosingPlan(
                    yearStart = plan.date("year_start"),
                    yearEnd = plan.date("year_end"),
                    incomeTotal = plan.num("income_total"),
                    expenseTotal = plan.num("expense_total"),
                    profit = plan.num("profit"),
                    headsClosed = plan?.int("heads_closed") ?: 0,
                    legs = plan?.array("legs").orEmptyArray().mapObjects { it.toLeg() },
                ),
                already = p.obj("already")?.toClosingRow(),
                history = p.array("history").mapObjects { it.toClosingRow() },
                capitalHeads = p.array("capital_heads").mapObjects { it.toHead() },
                note = p.text("note"),
            )
        }
    }

    /** The heaviest act in the accounts — always behind a confirm. */
    suspend fun runYearClosing(
        yearEnd: String,
        capitalCoa4Id: Long,
        branchId: Long?,
        note: String,
    ): Resource<String> {
        val body = JsonObject().apply {
            addProperty("year_end", yearEnd)
            addProperty("capital_coa4_id", capitalCoa4Id)
            branchId?.let { addProperty("branch_id", it) }
            note.trim().takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
        }
        return post("year-closing/run", body, "The year is closed.")
    }

    /** Undone with a contra voucher, never a deletion — and newest first. */
    suspend fun reverseYearClosing(id: Long): Resource<String> =
        post("year-closing/reverse/$id", JsonObject(), "The closing was reversed.")

    // ——— Budget ——————————————————————————————————————————————————————————

    suspend fun fetchBudget(
        branchId: Long?,
        yearEnd: String,
        projectId: Long?,
    ): Resource<BudgetView> {
        val params = buildMap {
            branchId?.let { put("branch_id", it.toString()) }
            put("year_end", yearEnd)
            projectId?.let { put("project_id", it.toString()) }
        }
        return read("budget", params) { p ->
            val t = p.obj("totals")
            BudgetView(
                yearStart = p.date("year_start"),
                yearEnd = p.date("year_end"),
                actualsUpTo = p.date("actuals_up_to"),
                monthsElapsed = p.int("months_elapsed") ?: 0,
                rows = p.array("rows").mapObjects { o ->
                    BudgetRow(
                        coa4Id = o.long("coa4_id") ?: return@mapObjects null,
                        name = o.text("name"),
                        groupName = o.text("group_name"),
                        budget = o.num("budget"),
                        expected = o.num("expected"),
                        actual = o.num("actual"),
                        left = o.num("left"),
                        againstExpected = o.num("against_expected"),
                    )
                },
                totals = BudgetTotals(
                    budget = t.num("budget"),
                    expected = t.num("expected"),
                    actual = t.num("actual"),
                    left = t.num("left"),
                ),
                projects = p.array("projects").mapObjects { o ->
                    ProjectOption(
                        id = o.long("id") ?: return@mapObjects null,
                        name = o.text("name"),
                    )
                },
                note = p.text("note"),
            )
        }
    }

    /**
     * Sets one head's budget for the year.
     *
     * A blank or zero [amount] DELETES the row — "no budget" and "a budget of
     * nothing" are the same statement, and the second would otherwise show as a
     * head that is 100% overspent from the first taka.
     */
    suspend fun saveBudget(
        yearEnd: String,
        coa4Id: Long,
        amount: String,
        projectId: Long?,
        branchId: Long?,
        note: String,
    ): Resource<String> {
        val body = JsonObject().apply {
            addProperty("year_end", yearEnd)
            addProperty("coa4_id", coa4Id)
            addProperty("amount", amount.trim().toDoubleOrNull() ?: 0.0)
            projectId?.let { addProperty("project_id", it) }
            branchId?.let { addProperty("branch_id", it) }
            note.trim().takeIf { it.isNotEmpty() }?.let { addProperty("note", it) }
        }
        return post("budget/store", body, "Budget saved.")
    }

    // ——— Ageing ——————————————————————————————————————————————————————————

    suspend fun fetchAgeing(
        asOn: String,
        side: String,
        branchId: Long?,
    ): Resource<AgeingView> {
        val params = buildMap {
            put("as_on", asOn)
            put("side", side)
            branchId?.let { put("branch_id", it.toString()) }
        }
        return read("ageing", params) { p ->
            // The bucket keys come back from the server (they carry an en dash)
            // and are used verbatim to read every row and the totals — typing
            // them again here is exactly how the columns would silently blank.
            val keys = p.array("buckets").mapNotNull { el ->
                el.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            }
            val totals = p.obj("totals")
            AgeingView(
                asOn = p.date("as_on"),
                side = p.text("side").ifBlank { side },
                buckets = keys,
                rows = p.array("rows").mapObjects { o ->
                    val rowBuckets = o.obj("buckets")
                    AgeingRow(
                        coa4Id = o.long("coa4_id") ?: return@mapObjects null,
                        name = o.text("name"),
                        partyName = o.text("party_name"),
                        mobile = o.text("mobile"),
                        creditDays = o.intOrNull("credit_days"),
                        buckets = keys.associateWith { k -> rowBuckets.num(k) },
                        outstanding = o.num("outstanding"),
                        notDue = o.num("not_due"),
                        advance = o.num("advance"),
                        oldestDue = o.date("oldest_due"),
                        oldestDays = o.int("oldest_days") ?: 0,
                    )
                },
                totals = AgeingTotals(
                    outstanding = totals.num("outstanding"),
                    advance = totals.num("advance"),
                    notDue = totals.num("not_due"),
                    buckets = keys.associateWith { k -> totals.num(k) },
                ),
                note = p.text("note"),
            )
        }
    }

    /**
     * The one thing this report writes: a party's credit terms.
     *
     * It lives here because this is the screen on which somebody discovers the
     * terms are missing. A null [creditDays] clears them back to cash.
     */
    suspend fun saveAgeingTerms(coa4Id: Long, creditDays: Int?): Resource<String> {
        val body = JsonObject().apply {
            addProperty("coa4_id", coa4Id)
            // Absent means null to the endpoint; Gson drops an explicit null.
            creditDays?.let { addProperty("credit_days", it) }
        }
        return post("ageing/terms", body, "Terms saved.")
    }

    // ——— Audit trail —————————————————————————————————————————————————————

    suspend fun fetchAuditTrail(
        from: String,
        to: String,
        userId: Long?,
        action: String,
        voucherNo: String,
        branchId: Long?,
    ): Resource<AuditTrailView> {
        val params = buildMap {
            put("from", from)
            put("to", to)
            userId?.let { put("user_id", it.toString()) }
            action.trim().takeIf { it.isNotEmpty() }?.let { put("action", it) }
            voucherNo.trim().takeIf { it.isNotEmpty() }?.let { put("voucher_no", it) }
            branchId?.let { put("branch_id", it.toString()) }
        }
        return read("audit-trail", params) { p -> p.toAuditView(from, to) }
    }

    /** Everything recorded against one voucher. */
    suspend fun fetchVoucherTrail(mainTrxId: Long): Resource<AuditTrailView> =
        read("audit-trail/voucher/$mainTrxId") { p -> p.toAuditView("", "") }

    // ——— Plumbing ————————————————————————————————————————————————————————

    /**
     * One GET, one envelope, one refusal style.
     *
     * `notFound()` answers with `success:false` and a sentence — sometimes at
     * 422, sometimes at 200 — so the FLAG is the verdict and the message is
     * shown as it was written, never replaced with one of ours.
     */
    private suspend fun <T> read(
        path: String,
        params: Map<String, String> = emptyMap(),
        parse: (JsonObject) -> T,
    ): Resource<T> = withContext(ioDispatcher) {
        try {
            val response = api.get(path, params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see this.")
            }
            val body = response.bodyOrError()
                ?: return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later.",
                )
            val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = body.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
            if (success == false) {
                return@withContext Resource.Error(message ?: "That could not be read.")
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    message ?: "Server error (${response.code()}). Please try again later.",
                )
            }
            val payload = body.obj("data")?.obj("data") ?: body.obj("data") ?: JsonObject()
            Resource.Success(parse(payload))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** One POST, answered with the server's own sentence either way. */
    private suspend fun post(
        path: String,
        body: JsonObject,
        fallback: String,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.postObjectRaw(path, body)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to do that.")
            }
            val respBody = response.bodyOrError()
            val success = respBody?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = respBody?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
            when {
                success == false -> Resource.Error(message ?: "The request was refused.")
                !response.isSuccessful && response.code() != 201 ->
                    Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")

                else -> Resource.Success(message ?: fallback)
            }
        } catch (e: IOException) {
            Resource.Error(
                "No internet connection. Please check your network and try again.",
                isAmbiguous = true,
            )
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    companion object {
        @Volatile
        private var instance: AccountsRepository? = null

        /** The process-wide instance, built on the shared report transport. */
        fun get(context: Context): AccountsRepository =
            instance ?: synchronized(this) {
                instance ?: AccountsRepository(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                ).also { instance = it }
            }
    }
}

// ---------------------------------------------------------------------------
// JSON helpers — every one null-tolerant, because a refusal is a valid answer
// and half a payload is a normal shape here (no rows, no heads, no history).
// ---------------------------------------------------------------------------

private fun retrofit2.Response<JsonElement>.bodyOrError(): JsonObject? =
    (body() ?: errorBody()?.let { runCatching { JsonParser.parseString(it.string()) }.getOrNull() })
        ?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject?.obj(key: String): JsonObject? =
    this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject?.array(key: String): JsonArray =
    this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray()

private fun JsonObject?.text(key: String): String =
    this?.get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

/** A wire date, trimmed of any time part — "2026-04-30T00:00:00" reads as a date. */
private fun JsonObject?.date(key: String): String = text(key).replace('T', ' ').trim().take(10)

private fun JsonObject?.long(key: String): Long? =
    text(key).toDoubleOrNull()?.toLong()

private fun JsonObject?.int(key: String): Int? = long(key)?.toInt()

private fun JsonObject?.intOrNull(key: String): Int? = long(key)?.toInt()

private fun JsonObject?.num(key: String): Double = text(key).toDoubleOrNull() ?: 0.0

private fun JsonObject?.numOrNull(key: String): Double? = text(key).toDoubleOrNull()

/** true / false, 1 / 0 or "1" / "0" — all three arrive from this API. */
private fun JsonObject?.flag(key: String): Boolean =
    text(key).let { it == "1" || it.equals("true", ignoreCase = true) }

private fun <T> JsonArray.mapObjects(transform: (JsonObject) -> T?): List<T> =
    mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let(transform) }

private fun JsonObject.toHead(): AccountHead? {
    val id = long("id") ?: return null
    return AccountHead(
        id = id,
        name = text("name"),
        groupName = text("group_name"),
    )
}

private fun JsonObject.toLeg(): VoucherLeg? {
    val coa4Id = long("coa4_id") ?: return null
    return VoucherLeg(
        coa4Id = coa4Id,
        debit = num("debit"),
        credit = num("credit"),
        note = text("note"),
        head = text("head"),
    )
}

private fun JsonObject.toCheque(): ChequeRow? {
    val id = long("id") ?: return null
    return ChequeRow(
        id = id,
    direction = text("direction"),
    chequeNo = text("cheque_no"),
    bankName = text("bank_name"),
    branchName = text("branch_name"),
    chequeDate = date("cheque_date"),
    onDate = date("on_date"),
    partyCoa4Id = long("party_coa4_id"),
    partyName = text("party_name"),
    partyHead = text("party_head"),
    accountCoa4Id = long("account_coa4_id"),
    accountName = text("account_name"),
    amount = num("amount"),
    status = text("status"),
    mainTrxId = long("main_trx_id"),
    vrNo = text("vr_no"),
    vrDate = date("vr_date"),
    note = text("note"),
    returnReason = text("return_reason"),
    depositedOn = date("deposited_on"),
        clearedOn = date("cleared_on"),
    )
}

private fun JsonObject.toClosingRow(): YearClosingRow? {
    val id = long("id") ?: return null
    return YearClosingRow(
        id = id,
        yearEnd = date("year_end"),
        profit = num("profit"),
        mainTrxId = long("main_trx_id"),
        vrNo = text("vr_no"),
        note = text("note"),
    )
}

private fun JsonObject.toAuditView(from: String, to: String): AuditTrailView = auditViewOf(from, to)

private fun JsonObject.auditViewOf(from: String, to: String): AuditTrailView {
    return AuditTrailView(
        from = date("from").ifBlank { from },
    to = date("to").ifBlank { to },
    events = array("events").mapObjects { o ->
        AuditEvent(
            id = o.long("id") ?: 0L,
            source = o.text("source"),
            at = o.text("at").replace('T', ' ').take(19),
            user = o.text("user"),
            action = o.text("action"),
            vrNo = o.text("vr_no"),
            vrDate = o.date("vr_date"),
            mainTrxId = o.long("main_trx_id"),
            changes = o.array("changes").mapObjects { c ->
                AuditChange(
                    field = c.text("field"),
                    old = c.text("old"),
                    new = c.text("new"),
                )
            },
        )
    },
        users = array("users").mapObjects { o ->
            val id = o.long("id")
            if (id == null) null else AuditUser(id = id, name = o.text("name"))
        },
        note = text("note"),
    )
}
