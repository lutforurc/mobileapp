package com.example.cashbookbd.ui.reports.model

import java.util.Calendar
import java.util.Locale

/**
 * A month + year value for reports that filter by month rather than a date range
 * (Collection Sheet). [month] is 1-based. Serializes to `MM/yyyy` — the backend
 * prepends `"01/"` to build a full `dd/MM/yyyy` date.
 */
data class MonthYear(
    val year: Int,
    val month: Int,
) {
    /** Wire format the API expects (`MM/yyyy`). */
    fun toParam(): String = String.format(Locale.US, "%02d/%04d", month, year)

    /** Human label, e.g. "July 2026". */
    fun toDisplay(): String = "${MONTH_NAMES[(month - 1).coerceIn(0, 11)]} $year"

    companion object {
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        fun current(): MonthYear {
            val c = Calendar.getInstance()
            return MonthYear(year = c.get(Calendar.YEAR), month = c.get(Calendar.MONTH) + 1)
        }
    }
}
