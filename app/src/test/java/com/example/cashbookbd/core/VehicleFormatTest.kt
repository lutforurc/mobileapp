package com.example.cashbookbd.core

import com.example.cashbookbd.ui.reports.model.AgeParts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The web's formatTransportationNumber (react 515b1071) and formatAge
 * (f759a70f), line for line — a plate and an age must read the same on the
 * phone as on the paper.
 */
class VehicleFormatTest {

    @Test
    fun `letters then figures come out in capitals with one space`() {
        assertEquals("DHAKA METRO-TA 11-2233", VehicleFormat.format("dhaka metro-ta 11 - 2233"))
        assertEquals("DM 1234", VehicleFormat.format("dm-1234"))
    }

    @Test
    fun `a prefix with digits, or Bengali digits, still goes up`() {
        assertEquals("B31 X", VehicleFormat.format("b31 x"))
        assertEquals("ঢাকা মেট্রো ১২", VehicleFormat.format(" ঢাকা   মেট্রো  ১২ "))
    }

    @Test
    fun `blank stays blank and letters alone go up`() {
        assertEquals("", VehicleFormat.format("   "))
        assertEquals("", VehicleFormat.format(null))
        assertEquals("TRUCK", VehicleFormat.format("truck"))
    }

    @Test
    fun `ages drop their zero parts and say today when all are zero`() {
        assertEquals("1y 1m 10d", AgeParts(1, 1, 10).format())
        assertEquals("7m 29d", AgeParts(0, 7, 29).format())
        assertEquals("2y", AgeParts(2, 0, 0).format())
        assertEquals("today", AgeParts(0, 0, 0).format())
    }
}
