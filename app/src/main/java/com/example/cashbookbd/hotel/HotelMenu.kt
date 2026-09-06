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

    const val DASHBOARD_KEY = "hotelDashboard"
    const val BOOKINGS_KEY = "hotelBookings"
    const val HALL_BOOKINGS_KEY = "hotelHallBookings"
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
        // The property at a glance — the web shows this on its main dashboard
        // for a lodging branch; here it heads the section (a mobile-only
        // entry, so it has no web id to be arranged or hidden by).
        HotelItem(
            DASHBOARD_KEY, "Dashboard",
            listOf("hotel.booking.view", "hotel.report.view", "hotel.housekeeping.view"),
        ),
        // Bookings first: setup is a sitting done once, this is the screen
        // somebody opens every day.
        HotelItem(BOOKINGS_KEY, "Bookings", listOf("hotel.booking.view")),
        // A hall is let by the sitting, not the night — its own screen.
        HotelItem(HALL_BOOKINGS_KEY, "Hall Booking", listOf("hotel.booking.view")),
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

    // ---- Routes of the setup screens that are drawn by hand -----------------
    // The buildings, floors, room types, facilities, sittings and charges ride
    // the shared list engine and are reached through Routes.appListView; these
    // are the screens that engine cannot draw — a form whose fields depend on
    // each other, a drawing, a grid — and the navigation graph wires them by
    // these names.
    const val ROUTE_ROOMS = "hotel/setup/rooms"
    const val ROUTE_ROOM_ID_ARG = "id"
    /** Optional id: absent means "a new room". */
    const val ROUTE_ROOM_FORM = "hotel/setup/rooms/form?id={id}"
    fun roomForm(id: Long?): String = "hotel/setup/rooms/form" + (id?.let { "?id=$it" } ?: "")
    const val ROUTE_LAYOUT = "hotel/setup/layout"
    const val ROUTE_TAX_RATES = "hotel/setup/tax-rates"
    const val ROUTE_HALL_BOOKING = "hotel/hall-bookings"

    // ------------------------------------------------------------------
    //  Routes of the money-side screens — the folio, its papers, check-out,
    //  cancellation, the edit after the fact, the walk-in sale.
    //
    //  Kept here rather than beside the other hotel routes in Routes because
    //  AppNavigation is wired by another hand this week; the composable()
    //  entries read these constants and builders. Booking ids travel as Long
    //  nav args; the receipt's paymentId is optional (LongType, default 0 =
    //  none) and the paper screen takes it as null.
    // ------------------------------------------------------------------

    const val BOOKING_ID_ARG = "bookingId"
    const val PAYMENT_ID_ARG = "paymentId"

    const val ROUTE_FOLIO = "hotel/bookings/{bookingId}/folio"
    const val ROUTE_BILL_PAPER = "hotel/bookings/{bookingId}/paper?paymentId={paymentId}"
    const val ROUTE_CHECKOUT = "hotel/bookings/{bookingId}/checkout"
    const val ROUTE_CANCEL = "hotel/bookings/{bookingId}/cancel"
    const val ROUTE_EDIT = "hotel/bookings/{bookingId}/edit"
    const val ROUTE_WALK_IN = "hotel/bookings/walk-in"

    fun folio(bookingId: Long): String = "hotel/bookings/$bookingId/folio"

    /** The bill, or — with a [paymentId] — one money receipt. */
    fun billPaper(bookingId: Long, paymentId: Long? = null): String =
        "hotel/bookings/$bookingId/paper" + (paymentId?.let { "?paymentId=$it" } ?: "")

    fun checkOut(bookingId: Long): String = "hotel/bookings/$bookingId/checkout"
    fun cancel(bookingId: Long): String = "hotel/bookings/$bookingId/cancel"
    fun edit(bookingId: Long): String = "hotel/bookings/$bookingId/edit"
}
