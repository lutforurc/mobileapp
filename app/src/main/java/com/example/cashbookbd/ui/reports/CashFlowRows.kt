package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.CashFlowLine
import com.example.cashbookbd.ui.reports.model.CashFlowStatementReport
import kotlin.math.abs

/** How a statement row is drawn: a heading, a figure, a bold total, or a note. */
enum class CashFlowRowKind { SECTION, LINE, SUBTOTAL, TOTAL, NOTE }

/**
 * One row of the statement. [amount] is null for a heading, so the Amount column
 * stays blank on a section title.
 */
data class CashFlowRow(
    val key: String,
    val label: String,
    val amount: Double?,
    val kind: CashFlowRowKind,
)

/** "Add" when the amount grows the figure, "Deduct" when it reduces it. */
private fun addDeduct(amount: Double, label: String) =
    "${if (amount < 0) "Deduct" else "Add"}: $label"

/** "Less" when the amount reduces the figure, "Add" when it is a reversal. */
private fun lessAdd(amount: Double, label: String) =
    "${if (amount < 0) "Less" else "Add"}: $label"

/**
 * The one direction word that applies to a working-capital line — the web's
 * `workingCapitalLabel`. The amount is the CASH effect: positive means cash
 * came in, so an asset line's positive effect is a decrease in that asset and a
 * liability line's positive effect is an increase.
 */
private fun workingCapitalLabel(line: CashFlowLine): String {
    val amount = line.amount
    val group = line.group

    if (abs(amount) < 0.005) return "Increase / (Decrease) in $group"

    return if (line.type == "liability") {
        "${if (amount > 0) "Increase" else "Decrease"} in $group"
    } else {
        "${if (amount > 0) "Decrease" else "Increase"} in $group"
    }
}

/**
 * The statement, section by section — the web's `cashFlowRows` ported rule for
 * rule (react `cash-flow-statement/cashFlowStatementRows.ts`). Note that a
 * section heading is always printed for operating, investing and financing;
 * "Other movements not classified" only appears when the server classified
 * nothing into it.
 */
fun cashFlowRows(report: CashFlowStatementReport): List<CashFlowRow> {
    val rows = mutableListOf<CashFlowRow>()

    rows += CashFlowRow("op-head", "Operating Activities", null, CashFlowRowKind.SECTION)

    val netProfit = report.operating.netProfit
    rows += CashFlowRow(
        key = "net-profit",
        label = if (netProfit < 0) "Net loss for the period" else "Net profit for the period",
        amount = netProfit,
        kind = CashFlowRowKind.LINE,
    )

    val depreciation = report.operating.nonCash.depreciation
    val otherExpense = report.operating.nonCash.otherExpense
    val nonCashIncome = report.operating.nonCash.income

    if (report.depreciationSupported || otherExpense != 0.0 || nonCashIncome != 0.0) {
        rows += CashFlowRow("noncash-head", "Non-cash adjustments", null, CashFlowRowKind.NOTE)

        if (report.depreciationSupported) {
            rows += CashFlowRow(
                key = "depreciation",
                label = addDeduct(depreciation, "Depreciation (non-cash)"),
                amount = depreciation,
                kind = CashFlowRowKind.LINE,
            )
        }

        if (otherExpense != 0.0) {
            rows += CashFlowRow(
                key = "other-noncash-expense",
                label = addDeduct(
                    otherExpense,
                    "Non-cash purchases & expenses (settled outside cash)",
                ),
                amount = otherExpense,
                kind = CashFlowRowKind.LINE,
            )
        }

        if (nonCashIncome != 0.0) {
            val incomeAmount = -nonCashIncome
            rows += CashFlowRow(
                key = "noncash-income",
                label = lessAdd(incomeAmount, "Non-cash sales & income (settled outside cash)"),
                amount = incomeAmount,
                kind = CashFlowRowKind.LINE,
            )
        }
    }

    val workingCapital = report.operating.workingCapital
    if (workingCapital.isNotEmpty()) {
        rows += CashFlowRow("wc-head", "Working capital adjustments", null, CashFlowRowKind.NOTE)
        workingCapital.forEachIndexed { index, line ->
            rows += CashFlowRow(
                key = "wc-${line.key.ifBlank { index.toString() }}",
                label = workingCapitalLabel(line),
                amount = line.amount,
                kind = CashFlowRowKind.LINE,
            )
        }
    }

    rows += CashFlowRow(
        key = "op-total",
        label = "Net cash from operating activities",
        amount = report.operating.total,
        kind = CashFlowRowKind.SUBTOTAL,
    )

    rows += CashFlowRow("inv-head", "Investing Activities", null, CashFlowRowKind.SECTION)
    report.investing.lines.forEachIndexed { index, line ->
        rows += CashFlowRow(
            key = "inv-${line.key.ifBlank { index.toString() }}",
            label = line.group,
            amount = line.amount,
            kind = CashFlowRowKind.LINE,
        )
    }
    rows += CashFlowRow(
        key = "inv-total",
        label = "Net cash from investing activities",
        amount = report.investing.total,
        kind = CashFlowRowKind.SUBTOTAL,
    )

    rows += CashFlowRow("fin-head", "Financing Activities", null, CashFlowRowKind.SECTION)
    report.financing.lines.forEachIndexed { index, line ->
        rows += CashFlowRow(
            key = "fin-${line.key.ifBlank { index.toString() }}",
            label = line.group,
            amount = line.amount,
            kind = CashFlowRowKind.LINE,
        )
    }
    rows += CashFlowRow(
        key = "fin-total",
        label = "Net cash from financing activities",
        amount = report.financing.total,
        kind = CashFlowRowKind.SUBTOTAL,
    )

    val unclassified = report.unclassified.lines
    if (unclassified.isNotEmpty()) {
        rows += CashFlowRow(
            "unc-head", "Other movements not classified", null, CashFlowRowKind.SECTION,
        )
        unclassified.forEachIndexed { index, line ->
            rows += CashFlowRow(
                key = "unc-${line.key.ifBlank { index.toString() }}",
                label = "${line.group} (${line.type})",
                amount = line.amount,
                kind = CashFlowRowKind.LINE,
            )
        }
        rows += CashFlowRow(
            key = "unc-total",
            label = "Net other movements",
            amount = report.unclassified.total,
            kind = CashFlowRowKind.SUBTOTAL,
        )
    }

    val netIncrease = report.reconciliation.netIncrease
    rows += CashFlowRow(
        key = "net-increase",
        label = when {
            netIncrease < 0 -> "Net decrease in cash and cash equivalents"
            netIncrease > 0 -> "Net increase in cash and cash equivalents"
            else -> "Net increase / (decrease) in cash and cash equivalents"
        },
        amount = netIncrease,
        kind = CashFlowRowKind.TOTAL,
    )
    rows += CashFlowRow(
        key = "opening-cash",
        label = "Cash and cash equivalents at the beginning of the period",
        amount = report.reconciliation.openingCash,
        kind = CashFlowRowKind.LINE,
    )
    rows += CashFlowRow(
        key = "closing-cash",
        label = "Cash and cash equivalents at the end of the period",
        amount = report.reconciliation.closingCash,
        kind = CashFlowRowKind.TOTAL,
    )

    if (!report.reconciliation.reconciles) {
        rows += CashFlowRow(
            key = "difference",
            label = "Unreconciled difference against the ledger",
            amount = report.reconciliation.difference,
            kind = CashFlowRowKind.NOTE,
        )
        rows += CashFlowRow(
            key = "ledger-closing",
            label = "Cash and cash equivalents per the ledger",
            amount = report.reconciliation.ledgerClosingCash,
            kind = CashFlowRowKind.NOTE,
        )
    }

    return rows
}

/**
 * The web's `amountText`: a blank cell for a heading, a parenthesised figure for
 * a negative, thousands otherwise.
 */
fun cashFlowAmountText(value: Double?): String {
    if (value == null) return ""
    val text = com.example.cashbookbd.core.AmountFormat.format(abs(value))
    return if (value < 0) "($text)" else text
}
