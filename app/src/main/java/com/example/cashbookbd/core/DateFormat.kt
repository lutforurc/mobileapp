package com.example.cashbookbd.core

/**
 * The one shape a date is shown in: dd/MM/yyyy (the web's formatDayMonthYear).
 *
 * The API speaks ISO — `2026-09-10`, and a timestamp where a column is DATETIME
 * — and every screen in both clients puts day first. Until now each screen that
 * needed it kept its own private copy of the conversion, which is how the
 * subscription screens came to print the raw ISO string in the middle of a
 * sentence.
 *
 * The year stays four digits on purpose: "10/09/26" invites the reader to
 * wonder which century, and these dates are read by people deciding whether to
 * pay a bill.
 */
object DateFormat {

    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val DAY_FIRST = Regex("""^(\d{1,2})/(\d{1,2})/(\d{2,4})$""")

    /**
     * [value] as dd/MM/yyyy, whichever way round it arrives.
     *
     * A blank answers [blank]. Anything this cannot read is handed back
     * untouched rather than blanked — a date nobody anticipated is still worth
     * more on screen than a dash.
     */
    fun dayMonthYear(value: String?, blank: String = "-"): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return blank

        // Trims a timestamp down to its date before matching.
        val date = text.take(10)

        ISO.find(date)?.let { m ->
            val (year, month, day) = m.destructured
            return "${day.padStart(2, '0')}/${month.padStart(2, '0')}/$year"
        }

        DAY_FIRST.find(date)?.let { m ->
            val (day, month, rawYear) = m.destructured
            val year = if (rawYear.length == 2) "20$rawYear" else rawYear
            return "${day.padStart(2, '0')}/${month.padStart(2, '0')}/$year"
        }

        return text
    }

    /**
     * [value] as dd/MM/yy, keeping any time that follows the date.
     *
     * The compact form is for the My Devices cards, where the timestamp shares
     * a narrow row with its label. It is deliberately NOT the default: a
     * two-digit year is for a log line, not for a bill.
     */
    fun dayMonthYearShort(value: String?, blank: String = "-"): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return blank

        val date = text.take(10)
        // A DATETIME with a `T` and a trailing `Z` reads like a log line; the
        // compact display keeps the clock but in the same shape as the rest.
        val time = text.drop(10)
            .replaceFirst('T', ' ')
            .removeSuffix("Z")

        ISO.find(date)?.let { m ->
            val (year, month, day) = m.destructured
            return "${day.padStart(2, '0')}/${month.padStart(2, '0')}/${year.takeLast(2)}$time"
        }

        DAY_FIRST.find(date)?.let { m ->
            val (day, month, rawYear) = m.destructured
            val year = if (rawYear.length == 2) rawYear else rawYear.takeLast(2)
            return "${day.padStart(2, '0')}/${month.padStart(2, '0')}/$year$time"
        }

        return text
    }
}
