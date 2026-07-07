package com.example.cashbookbd.ui.reports.model

/** One party's due line (a ledger balance: debit − credit). */
data class DueRow(
    val customer: String,
    val mobile: String?,
    /** Ledger page or address, shown as a reference line. */
    val reference: String?,
    val debit: Double,
    val credit: Double,
) {
    /** Net outstanding: positive = receivable, negative = advance/payable. */
    val balance: Double get() = debit - credit
}

/**
 * The Due List report: party rows plus the totals from the backend's Total /
 * Balance summary rows (or computed from the rows when absent).
 */
data class DueListReport(
    val rows: List<DueRow>,
    val totalDebit: Double,
    val totalCredit: Double,
    val netBalance: Double,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}
