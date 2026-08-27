package com.example.cashbookbd.hotel

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Hotel menu (the web sidebar's Hotel group). */
data class HotelItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
    /** false renders the entry greyed with "not available yet", as elsewhere. */
    val supported: Boolean = true,
)

/**
 * The web's Hotel group, in its order: the screens opened daily first, the
 * sitting-done-once setup last.
 *
 * Gated on permission alone, never on a business type. The web makes the same
 * choice for the same reason: the Real Estate check reads `business_type_id == 9`
 * and that id is auto-increment, so "Hotel / Motel" is 9 in one tenant's
 * database and 11 in another's. The hotel permissions are granted to nobody
 * when they are created, which draws the same line and cannot drift.
 */
object HotelMenu {

    const val BOOKINGS_KEY = "hotelBookings"
    const val CALENDAR_KEY = "hotelCalendar"
    const val HOUSEKEEPING_KEY = "hotelHousekeeping"
    const val REPORTS_KEY = "hotelReports"
    const val SETUP_KEY = "hotelSetup"

    /** The setup screen's tabs, each on its own permission. */
    private val SETUP_ANY = listOf(
        "hotel.building.view",
        "hotel.floor.view",
        "hotel.room.type.view",
        "hotel.charge.type.view",
        "hotel.resource.view",
    )

    val all: List<HotelItem> = listOf(
        // Bookings first: setup is a sitting done once, this is the screen
        // somebody opens every day.
        HotelItem(BOOKINGS_KEY, "Bookings", listOf("hotel.booking.view")),
        // What next week looks like — the question the bookings list cannot
        // answer.
        HotelItem(CALENDAR_KEY, "Calendar", listOf("hotel.booking.view")),
        // Opened every morning by somebody who does nothing else in here.
        HotelItem(HOUSEKEEPING_KEY, "Housekeeping", listOf("hotel.housekeeping.view")),
        HotelItem(REPORTS_KEY, "Reports", listOf("hotel.report.view")),
        HotelItem(SETUP_KEY, "Rooms & Seats Setup", SETUP_ANY),
    )

    /**
     * Any one of the five opens the menu — the setup screen's tabs are one
     * screen, and a role given only the room types still has to reach it.
     * The web's `hotel` menuPermissions key, verbatim, widened by the entries'
     * own permissions so a housekeeper reaches their board.
     */
    val PARENT_PERMISSIONS: List<String> = listOf(
        "hotel.building.view",
        "hotel.floor.view",
        "hotel.room.type.view",
        "hotel.resource.view",
        "hotel.booking.view",
    )

    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS + all.flatMap { it.anyOf })

    fun visible(permissions: List<Permission>?): List<HotelItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
