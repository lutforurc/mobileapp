package com.example.cashbookbd.ui.reports.model

/** One line of the Trading or Profit & Loss account (a computed head or a raw row). */
data class ProfitLossAccountLine(
    val label: String,
    val amount: Double,
    /** Bold styling for subtotals / gross / net lines. */
    val emphasis: Boolean = false,
)

/** A single summary-box figure (Opening Stock, Net Sales, Gross Profit, …). */
data class ProfitLossSummaryItem(
    val label: String,
    val value: Double,
)

/**
 * A parsed Profit & Loss statement following the web app's logic: a Trading
 * Account (from `trading[]`) and a Profit & Loss Account (from `netprofit[]`),
 * with the summary figures and the bottom-line Net Profit/Loss.
 */
data class ProfitLossReport(
    val trading: List<ProfitLossAccountLine>,
    val profitLoss: List<ProfitLossAccountLine>,
    val summary: List<ProfitLossSummaryItem>,
    val netLabel: String,
    val netAmount: Double,
    val isNetProfit: Boolean,
) {
    val isEmpty: Boolean get() = trading.isEmpty() && profitLoss.isEmpty()
}
