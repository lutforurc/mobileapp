package com.example.cashbookbd.ui.reports.model

/** A single line within a Balance Sheet group, with the web's three columns. */
data class BalanceSheetItem(
    val description: String,
    val opening: Double,
    val movement: Double,
    val closing: Double,
)

/**
 * One Balance Sheet table row — a group of items. The web renders a single row
 * per group (name + "N items" badge + Opening/Movement/Closing) that opens the
 * group's item breakdown on tap.
 */
data class BalanceSheetGroup(
    val title: String,
    val items: List<BalanceSheetItem>,
    val opening: Double,
    val movement: Double,
    val closing: Double,
)

/** A top-level section: Assets, Liabilities, or Equity. */
data class BalanceSheetSection(
    val title: String,
    val groups: List<BalanceSheetGroup>,
    val opening: Double,
    val movement: Double,
    val closing: Double,
)

/** A summary-box figure (Total Assets, Liabilities + Equity, Difference). */
data class BalanceSheetSummaryItem(
    val label: String,
    val value: Double,
)

/**
 * A parsed Balance Sheet: the grouped [sections] (after the web's equity
 * opening-difference adjustment) and the [summary] closing-column totals. Built
 * from the structured `{ assets, liabilities, equity, totals }` response.
 */
data class BalanceSheetReport(
    val sections: List<BalanceSheetSection>,
    val summary: List<BalanceSheetSummaryItem>,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && summary.isEmpty()
}
