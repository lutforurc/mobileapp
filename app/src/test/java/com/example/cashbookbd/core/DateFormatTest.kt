package com.example.cashbookbd.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The web's formatDayMonthYear (react utils-functions/formatDate.tsx), case for
 * case — a subscription's end date must read the same on the phone as in the
 * browser, because the two say the same sentence about the same bill.
 */
class DateFormatTest {

    @Test
    fun `ISO goes in and day-first comes out`() {
        assertEquals("10/09/2026", DateFormat.dayMonthYear("2026-09-10"))
        assertEquals("05/01/2026", DateFormat.dayMonthYear("2026-1-5"))
    }

    @Test
    fun `a timestamp keeps its date and loses its time`() {
        // grace_period_end_at is a DATETIME, and "24/09/2026 23:59:59" on a
        // card headed "Grace period ended" is three numbers nobody asked for.
        assertEquals("24/09/2026", DateFormat.dayMonthYear("2026-09-24 23:59:59"))
        assertEquals("24/09/2026", DateFormat.dayMonthYear("2026-09-24T23:59:59Z"))
    }

    @Test
    fun `already day-first is normalised, not mangled`() {
        assertEquals("10/09/2026", DateFormat.dayMonthYear("10/09/2026"))
        assertEquals("05/01/2026", DateFormat.dayMonthYear("5/1/2026"))
        // Four digits on purpose: "10/09/26" invites the reader to wonder which
        // century, and these dates decide whether somebody pays a bill.
        assertEquals("10/09/2026", DateFormat.dayMonthYear("10/09/26"))
    }

    @Test
    fun `nothing to show answers the blank the caller asked for`() {
        assertEquals("-", DateFormat.dayMonthYear(null))
        assertEquals("-", DateFormat.dayMonthYear(""))
        assertEquals("-", DateFormat.dayMonthYear("   "))
        assertEquals("", DateFormat.dayMonthYear(null, blank = ""))
    }

    @Test
    fun `what it cannot read is handed back, not swallowed`() {
        // A date in a shape nobody anticipated is still worth more on screen
        // than a dash.
        assertEquals("Ongoing", DateFormat.dayMonthYear("Ongoing"))
        assertEquals("10/09/2026", DateFormat.dayMonthYear("  2026-09-10  "))
    }
}
