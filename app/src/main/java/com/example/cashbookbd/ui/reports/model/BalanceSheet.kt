package com.example.cashbookbd.ui.reports.model

/** A single line within a Balance Sheet group. */
data class BalanceSheetItem(
    val description: String,
    val amount: Double,
)

/**
 * A group of items inside a section. [title] is null for a flat section that has
 * no sub-groups (its items render directly under the section).
 */
data class BalanceSheetGroup(
    val title: String?,
    val items: List<BalanceSheetItem>,
    val total: Double,
)

/** A top-level section: Assets, Liabilities, or Equity. */
data class BalanceSheetSection(
    val title: String,
    val groups: List<BalanceSheetGroup>,
    val total: Double,
)

/** A summary-box figure (Assets, Liabilities + Equity, Difference, …). */
data class BalanceSheetSummaryItem(
    val label: String,
    val value: Double,
)

/**
 * A parsed Balance Sheet: the grouped [sections] and the [summary] totals. Built
 * from the structured `{ assets, liabilities, equity, totals }` response.
 */
data class BalanceSheetReport(
    val sections: List<BalanceSheetSection>,
    val summary: List<BalanceSheetSummaryItem>,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && summary.isEmpty()
}
