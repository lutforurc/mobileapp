package com.example.cashbookbd.ui.reports.model

/** One account line of the Level-4 trial balance. */
data class TrialBalanceRow(
    val description: String,
    val openingDebit: Double,
    val openingCredit: Double,
    val movementDebit: Double,
    val movementCredit: Double,
    val closingDebit: Double,
    val closingCredit: Double,
)

/**
 * The Level-4 trial balance report: the account rows plus the closing totals
 * shown in the summary boxes. [closingDebit]/[closingCredit] come from the
 * backend's total row when present, otherwise the sum of the rows.
 */
data class TrialBalanceReport(
    val rows: List<TrialBalanceRow>,
    val closingDebit: Double,
    val closingCredit: Double,
) {
    /** Closing Debit − Closing Credit (≈ 0 for a balanced trial balance). */
    val difference: Double get() = closingDebit - closingCredit

    val isEmpty: Boolean get() = rows.isEmpty()
}
