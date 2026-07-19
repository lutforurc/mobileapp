package com.example.cashbookbd.core

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The single place every transaction amount is formatted, so the current
 * branch's `decimal_places` setting is honoured everywhere at once — the same
 * "one shared component" rule the buttons and fields follow.
 *
 * Before this, roughly a dozen screens each declared their own DecimalFormat,
 * mostly `#,##0.##` but with a couple on `#,##0.00` and `#,##0`, so amounts did
 * not even agree with each other. They now all route through [format].
 *
 * The decimal-place count is a single app-wide value — the current branch — that
 * changes only on login, settings refresh, or branch switch. [SessionManager]
 * pushes it here via [setDecimalPlaces] whenever settings load, so callers
 * (Composables and repositories alike) can format without threading it through.
 * Reads are a plain field, not Compose state: the screens that show amounts are
 * re-fetched / re-navigated on a branch switch, so they pick up the new value
 * when they rebuild.
 */
object AmountFormat {

    /** Used until settings load, and when the branch has no value stored. */
    const val DEFAULT_DECIMAL_PLACES = 2

    /** A sane upper bound; guards against a stray large meta value. */
    private const val MAX_DECIMAL_PLACES = 6

    @Volatile
    private var decimalPlaces: Int = DEFAULT_DECIMAL_PLACES

    /** Called by the session layer when settings load. Null resets to the default. */
    fun setDecimalPlaces(places: Int?) {
        decimalPlaces = places?.coerceIn(0, MAX_DECIMAL_PLACES) ?: DEFAULT_DECIMAL_PLACES
    }

    val current: Int get() = decimalPlaces

    /**
     * Formats [value] with grouping and exactly [places] fraction digits, cutting
     * off extra digits rather than rounding (per the branch setting's intent:
     * decimal_places=0 turns 2,000.99 into 2,000, not 2,001).
     */
    fun format(value: Double, places: Int = decimalPlaces): String =
        formatterFor(places).format(value)

    /**
     * As [format], but a zero amount renders as "-". Report and list tables read
     * cleaner when empty cells are a dash instead of a column of zeros.
     */
    fun formatOrDash(value: Double, places: Int = decimalPlaces): String =
        if (value == 0.0) "-" else format(value, places)

    /**
     * DecimalFormat is not thread-safe and amounts are formatted from many
     * screens at once, so a fresh instance is built per call. The work is
     * trivial next to laying out the row it sits in.
     */
    private fun formatterFor(places: Int): DecimalFormat {
        val pattern = if (places > 0) "#,##0." + "0".repeat(places) else "#,##0"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.DOWN
        }
    }
}
