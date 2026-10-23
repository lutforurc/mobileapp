package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetReport

/*
 * Notes to the Balance Sheet — the web's balanceSheetNotes.ts. A note is
 * written only where the sheet carries the row that points at it, numbered
 * 1..n from the sheet in hand; the rows read the same index.
 */

/** The chart's section names that read wrong on the side they now sit on. */
private val SECTION_LABELS = mapOf("Current Liability" to "Current Liabilities")

fun reportSectionLabel(name: String): String = SECTION_LABELS[name] ?: name

/** Which groups carry a note, in the order the notes are numbered. */
private val GROUP_NOTE_ORDER = listOf(
    "Supplier Advances",
    "Account Payable",
    "Customer Advances",
    "Bank & Finance Borrowings",
    "Loans Received",
)

/** Either name the server gives the period's result, whichever side it landed. */
private val RESULT_GROUPS = listOf("Current Period Profit", "Current Period Loss")

private fun BalanceSheetReport.allGroups(): List<BalanceSheetGroup> =
    if (hasSections) sectioned.values.flatten().flatMap { it.groups } else sections.flatMap { it.groups }

private fun BalanceSheetReport.findGroup(name: String): BalanceSheetGroup? = allGroups().firstOrNull { it.title == name }

/**
 * The note number each group on THIS sheet points at, [1] being the basis.
 * The stock-movement note has no row of its own: Closing Stock carries it,
 * and only when the note exists.
 */
fun balanceSheetNoteIndex(report: BalanceSheetReport, hasStockNote: Boolean): Map<String, Int> {
    val index = mutableMapOf<String, Int>()
    var n = 1
    for (name in GROUP_NOTE_ORDER) if (report.findGroup(name) != null) index[name] = ++n
    if (RESULT_GROUPS.any { report.findGroup(it) != null }) {
        val result = ++n
        RESULT_GROUPS.forEach { index[it] = result }
    }
    if (hasStockNote && report.findGroup("Closing Stock") != null) index["Closing Stock"] = ++n
    return index
}

/** The group's own name with its note number after it, where this sheet has one. */
fun reportGroupLabel(name: String, index: Map<String, Int>): String = name + noteRef(index[name])

private fun money(v: Double) = AmountFormat.format(v)

/**
 * [productOut]/[productIn] are null when they could not be read — that writes
 * no note, it is never taken for a zero.
 */
fun buildBalanceSheetNotes(
    report: BalanceSheetReport,
    asOn: String?,
    productOut: Double?,
    productIn: Double?,
): List<ReportNoteItem> {
    fun amount(name: String) = report.findGroup(name)?.closing ?: 0.0

    val supplierAdvances = amount("Supplier Advances")
    val payables = amount("Account Payable")
    val customerCredits = amount("Customer Advances")
    val bankCredits = amount("Bank & Finance Borrowings")
    val loans = amount("Loans Received")
    // One figure, not two: only one of the pair exists, and a loss arrives negative.
    val result = amount("Current Period Profit") + amount("Current Period Loss")
    val stockMovement = report.findGroup("Closing Stock")?.movement ?: 0.0
    val ledgerResult = result - stockMovement
    val asOnText = if (!asOn.isNullOrBlank()) " as at $asOn" else ""

    val index = balanceSheetNoteIndex(report, (productOut ?: 0.0) > 0 || (productIn ?: 0.0) > 0)
    val resultNote = index["Current Period Profit"] ?: index["Current Period Loss"]

    val stockNote = stockMovementNote(
        productOut ?: 0.0,
        productIn ?: 0.0,
        "The written-off quantities are excluded from closing inventory within assets and the " +
            "received quantities are inside it, and their financial effect is reflected through " +
            "inventory valuation and the current period's profit or loss within equity" +
            (if (resultNote != null) " (note $resultNote)" else "") +
            ". Accordingly, neither disclosed amount is adjusted again against assets or equity. " +
            "Where purchase costs vary, the inventory valuation method may cause the effect on the " +
            "current period's result to differ from the recorded write-off amount; and a receipt " +
            "taken in at no rate, which happens where the books held none, adds nothing to closing " +
            "inventory even though the goods are on the shelf.",
    )

    return buildList {
        add(
            ReportNoteItem(
                1, "Basis of preparation",
                "The sheet$asOnText is drawn from posted vouchers, including a year-end " +
                    "closing voucher where a year has been closed, so the capital accounts carry " +
                    "the years already closed and the current period carries only what is left " +
                    "standing. Every account is placed by its own closing balance and by what " +
                    "the account is: a head carrying a debit is shown among the assets, a head " +
                    "carrying a credit among what is owed, wherever in the chart the head is " +
                    "filed. Where that puts an account on the opposite side to the one the chart " +
                    "files it in, it is shown once, under a group named for what it is on that " +
                    "side — a customer ledger and a party loan account both sit in the chart's " +
                    "asset branch, and both are money owed. Assets, liabilities and the owners' " +
                    "stake are shown gross, with no account netted against another; only the " +
                    "sign of a balance decides its side, and no balance is moved for any other " +
                    "reason.",
            ),
        )
        index["Supplier Advances"]?.let { n ->
            add(
                ReportNoteItem(
                    n, "Supplier advances",
                    "${money(supplierAdvances)} in the business's favour with suppliers — " +
                        "supplier ledgers carrying a debit balance, shown inside Current Assets and " +
                        "kept apart from the amounts owed to suppliers on the other side. These are " +
                        "recoverable amounts, and they are not necessarily money paid ahead of a " +
                        "bill: the direction of the balance is all the books record, and it is the " +
                        "same for a prepayment, an overpayment and an opening balance carried in when " +
                        "the books were brought across. Whether a particular balance will come back as " +
                        "goods still to come, as a refund, or against a bill already raised is not " +
                        "recorded anywhere, so the report does not say — it reports the balance as it " +
                        "stands and leaves the reason to the ledger behind it.",
                ),
            )
        }
        index["Account Payable"]?.let { n ->
            add(
                ReportNoteItem(
                    n, "Accounts payable",
                    "${money(payables)} owed to suppliers — the credit balances of the supplier " +
                        "ledgers, in full. They are not reduced by a debit balance standing on " +
                        "another supplier's ledger: the two are different parties' balances, and one " +
                        "party's debit does not settle another's credit.",
                ),
            )
        }
        index["Customer Advances"]?.let { n ->
            add(
                ReportNoteItem(
                    n, "Customer credit balances",
                    "${money(customerCredits)} held for customers — customer ledgers standing in " +
                        "credit, which is money the business owes back rather than a negative " +
                        "receivable. It is shown in a section of its own, apart from the borrowings " +
                        "below it, because it is not a borrowing: a balance owed to a customer is " +
                        "settled by supplying goods or by refunding the money, not by repaying a loan " +
                        "with interest, and the two have nothing in common but the side of the sheet " +
                        "they land on. No repayment terms are recorded for it and the books do not say " +
                        "which of the two is expected, so the amount is reported as it stands.",
                ),
            )
        }
        index["Bank & Finance Borrowings"]?.let { n ->
            add(
                ReportNoteItem(
                    n, "Bank and finance borrowings",
                    "${money(bankCredits)} of bank and finance ledgers standing in credit, on " +
                        "which the business is the one that owes. ⚠️ A credit balance on a bank ledger " +
                        "does not by itself make the account an overdraft, and this group is not named " +
                        "as one: what stands behind the balance may be a current account the bank has " +
                        "been paid into, a cash-credit or overdraft facility, or a finance company's " +
                        "ledger, and only the ledgers themselves say which. The report does not pick " +
                        "one, and no head in the chart is named as an overdraft either. No facility " +
                        "terms, limits or maturity dates are recorded for these accounts — no voucher " +
                        "in the books carries a due date — so the report says only what the balances " +
                        "establish and leaves the nature of each account to the reader. Interest " +
                        "charged on such an account is posted to Bank Interest as an expense of the " +
                        "period; it does not change the classification of the balance.",
                ),
            )
        }
        index["Loans Received"]?.let { n ->
            add(
                ReportNoteItem(
                    n, "Loans received",
                    "${money(loans)} borrowed from parties, filed in the chart under the heading " +
                        "\"Advance\" inside the asset branch and shown here for what it is — money the " +
                        "business has taken, not given. ⚠️ That same chart heading also carries loans " +
                        "the business has GIVEN, which stand on the asset side; the two are told apart " +
                        "by the direction of the balance and by nothing else, so the same heading can " +
                        "appear on both sides of the sheet. A balance here may be an opening balance " +
                        "carried in when the books were brought across or money actually drawn during " +
                        "the period — the report shows the balance and does not sort it. No repayment " +
                        "date or term is recorded for any of them, so the sheet does not divide them " +
                        "between current and non-current.",
                ),
            )
        }
        resultNote?.let { n ->
            add(
                ReportNoteItem(
                    n, "Current period result",
                    "${money(result)} — the result of the period, carried to the owners' stake " +
                        "and not yet distributed. It is not a balancing figure: nothing is ever " +
                        "inserted to make the two sides agree, and a sheet whose sides differ prints " +
                        "its Difference rather than closing the gap with profit. The figure is the " +
                        "ledger's own income and expense heads, which have no other home on this " +
                        "sheet, together with the change in the value of the stock — stock is valued " +
                        "from the stock records rather than posted to a head, so it has nowhere in " +
                        "the ledger to sit. For the period shown the ledger's income and expense heads " +
                        "leave ${money(ledgerResult)}, which is normally negative while goods are " +
                        "still on hand, because the purchases of the period sit in there as expense " +
                        "in full; adding the ${money(stockMovement)} by which the stock held rose over " +
                        "the period gives ${money(result)}. That is the same amount the Profit & Loss " +
                        "Account reports as its net result for the same dates, worked out " +
                        "independently of this sheet; if the two ever disagree it is the stock " +
                        "valuation or a voucher that does not balance, and both are reported " +
                        "elsewhere rather than absorbed here. Where a year has been closed, the " +
                        "closing voucher has already emptied that year's income and expense into " +
                        "capital, so this line then carries only what the period itself has left " +
                        "standing.",
                ),
            )
        }
        val stockN = index["Closing Stock"]
        if (stockNote != null && stockN != null) add(ReportNoteItem(stockN, stockNote.first, stockNote.second))
    }
}
