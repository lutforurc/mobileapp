package com.example.cashbookbd.ui.reports.model

/**
 * One line of the four-column Profit & Loss statement — the web's layout:
 * Particulars | (working) | Debit (Tk.) | Credit (Tk.). Component rows
 * (Purchase, "(-) Purchase Return", each expense/income head) carry their
 * amount in [working] only; netted heads land in [debit] or [credit].
 * A null amount renders as a blank cell.
 */
data class ProfitLossAccountLine(
    val label: String,
    val working: Double? = null,
    val debit: Double? = null,
    val credit: Double? = null,
    /** Bold styling for subtotals / gross / net / total lines. */
    val emphasis: Boolean = false,
    /** Component rows are indented under their head (the web's pl-6). */
    val indent: Boolean = false,
    /** An income head listed inside its group — a step further in, muted (pl-12). */
    val child: Boolean = false,
    /** The note this row points at, printed after the label as "[n]". */
    val note: Int? = null,
)

/**
 * A parsed Profit & Loss statement following the web app's logic: a
 * "PROFIT OR LOSS A/C (TRADING A/C)" section (from `trading[]`) and a
 * "NET PROFIT OR LOSS A/C" section (from `netprofit[]`), each ending in its
 * balancing Total row.
 */
data class ProfitLossReport(
    val trading: List<ProfitLossAccountLine>,
    val profitLoss: List<ProfitLossAccountLine>,
    /** The Cost of Goods Sold written out — a reading, added to no total. */
    val cogs: List<ProfitLossAccountLine> = emptyList(),
    /** The note the Trading Account heading points at (Basis of preparation). */
    val basisNote: Int? = null,
    val notes: List<com.example.cashbookbd.ui.reports.ReportNoteItem> = emptyList(),
) {
    val isEmpty: Boolean get() = trading.isEmpty() && profitLoss.isEmpty()
}
