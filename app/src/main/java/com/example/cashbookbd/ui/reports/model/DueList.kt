package com.example.cashbookbd.ui.reports.model

/**
 * A span said in years, months and days — the web's formatAge (react
 * f759a70f): zero parts drop, "7m 28d" rather than "240 days". Never derived
 * from a day count on the client; the server works it out from the dates so
 * the screen and the paper agree.
 */
data class AgeParts(val years: Int, val months: Int, val days: Int) {
    /** "1y 1m 10d", "7m 29d", "2y"; all-zero is the literal "today". */
    fun format(): String {
        val parts = buildList {
            if (years > 0) add("${years}y")
            if (months > 0) add("${months}m")
            if (days > 0) add("${days}d")
        }
        return if (parts.isEmpty()) "today" else parts.joinToString(" ")
    }
}

/** One party's due line (a ledger balance: debit − credit). */
data class DueRow(
    val customer: String,
    val mobile: String?,
    /** Ledger page reference, e.g. "L#01, P#407". */
    val page: String?,
    /** Customer/supplier address line. */
    val address: String?,
    /** Area / zone code, when the backend provides one. */
    val areaCode: String?,
    val debit: Double,
    val credit: Double,
    /**
     * The debit split by how long each unpaid item has stood, oldest cleared
     * first (api a20af1ed): 0-30, 31-60, 61-90, 90+ days. Always four figures,
     * zeros where nothing is owed; they sum to [debit].
     */
    val ageing: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    /** Days of the oldest unpaid item, 0 when nothing is owed. */
    val oldestDays: Int = 0,
    val oldestAge: AgeParts? = null,
    /** The last RECEIPT against this party (yyyy-MM-dd); null = never on these books. */
    val lastPaid: String? = null,
    val lastPaidAge: AgeParts? = null,
    val lastPaidDays: Int? = null,
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
