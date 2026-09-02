package com.example.cashbookbd.core

import java.util.Locale

/**
 * The web's `formatTransportationNumber` (react 515b1071): a vehicle number
 * printed in capitals on every path out, one space between the letters and
 * the figures. Done in the formatter rather than per screen so a plate typed
 * as "dhaka metro-ta 11-2233" reads the same on the ledger, the order sheet and
 * the challan. Display only — what was typed is what is stored.
 */
object VehicleFormat {

    private val DASH_SPACING = Regex("""\s*-\s*""")
    private val WHITESPACE = Regex("""\s+""")

    /** Letters, then optionally a separator and the rest — Latin letters only. */
    private val PREFIXED = Regex("""^([A-Za-z]+)(?:[-\s]+(.+))?$""")

    fun format(raw: String?): String {
        val normalized = raw.orEmpty().trim()
            .replace(DASH_SPACING, "-")
            .replace(WHITESPACE, " ")
        if (normalized.isEmpty()) return ""
        // Bengali digits, or a prefix with digits in it ("B31"): the pattern
        // decides spacing only, never case, so these still go up.
        val match = PREFIXED.find(normalized) ?: return normalized.uppercase(Locale.US)
        val prefix = match.groupValues[1]
        val number = match.groupValues.getOrNull(2).orEmpty()
        if (number.isBlank()) return prefix.uppercase(Locale.US)
        return "$prefix $number".uppercase(Locale.US)
    }
}
