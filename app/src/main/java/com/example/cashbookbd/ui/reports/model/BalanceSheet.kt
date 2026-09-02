package com.example.cashbookbd.ui.reports.model

/** A single line within a Balance Sheet group, with the web's three columns. */
data class BalanceSheetItem(
    val description: String,
    val opening: Double,
    val movement: Double,
    val closing: Double,
)

/**
 * One Balance Sheet table row — a group of items (a level-3 head). The web
 * renders one numbered line per group that opens the group's accounts on tap.
 */
data class BalanceSheetGroup(
    val title: String,
    val items: List<BalanceSheetItem>,
    val opening: Double,
    val movement: Double,
    val closing: Double,
    /**
     * Every account in this group is an accumulated-depreciation head (api
     * a30c16aa reads them from `asset_categories.accum_dep_coa4_id`, never by
     * name). A contra group is not listed among its section's items; it
     * becomes the one "Less: Accumulated Depreciation" line under them.
     */
    val isContra: Boolean = false,
)

/** The three money columns of any sheet line. */
data class BalanceSheetColumns(
    val opening: Double,
    val movement: Double,
    val closing: Double,
) {
    operator fun plus(other: BalanceSheetColumns) =
        BalanceSheetColumns(opening + other.opening, movement + other.movement, closing + other.closing)

    fun negated() = BalanceSheetColumns(-opening, -movement, -closing)

    companion object {
        val ZERO = BalanceSheetColumns(0.0, 0.0, 0.0)
    }
}

/**
 * A level-2 head of the chart ("Current Assets", "Fixed Asset", "Capital
 * Account", the synthetic "Retained Earnings") with its groups — the web's
 * `sections` shape (api a30c16aa / react 4c718343). [columns] is the section's
 * total AFTER any contra deduction; when [hasContra], [depreciation] carries
 * the accumulated depreciation as the server reports it (a positive figure
 * with negative columns), drawn as "Less: …" then "Net <name>".
 */
data class BalanceSheetSubsection(
    val name: String,
    val groups: List<BalanceSheetGroup>,
    val columns: BalanceSheetColumns,
    val hasContra: Boolean = false,
    val depreciation: BalanceSheetColumns? = null,
) {
    /** The groups listed as numbered items — the contra one is drawn as the Less line. */
    val listedGroups: List<BalanceSheetGroup> get() = groups.filterNot { it.isContra }
}

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
 * A parsed Balance Sheet. [sections] is the flat `{assets, liabilities,
 * equity}` shape every API version sends (after the web's equity
 * opening-difference adjustment); [sectioned] is the newer `sections` key —
 * the same groups hung under their level-2 heads, in the same order — and is
 * empty on an older server, where the screen falls back to the flat lists.
 */
data class BalanceSheetReport(
    val sections: List<BalanceSheetSection>,
    val summary: List<BalanceSheetSummaryItem>,
    val sectioned: Map<String, List<BalanceSheetSubsection>> = emptyMap(),
) {
    val isEmpty: Boolean get() = sections.isEmpty() && summary.isEmpty()
    val hasSections: Boolean get() = sectioned.values.any { it.isNotEmpty() }
}
