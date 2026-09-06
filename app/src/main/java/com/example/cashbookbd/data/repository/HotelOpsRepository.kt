package com.example.cashbookbd.data.repository

import android.content.Context
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// ---------------------------------------------------------------------------
//  Housekeeping
// ---------------------------------------------------------------------------

/** One room on the housekeeping board (`hotel-setup/housekeeping`). */
data class HkRoom(
    val id: Long,
    val name: String,
    val buildingId: Long?,
    /** The block's code — "MB" — what a room is named by. */
    val building: String,
    val buildingName: String,
    val floorId: Long?,
    /** The number, not the name: the gutter has room for "5F", not "Fifth Floor". */
    val floorNo: Int?,
    val floorName: String,
    /** clean / dirty / cleaning / out_of_order. A room with no row reads clean. */
    val status: String,
    val notes: String,
    val changedAt: String,
    /** Somebody is asleep in it TONIGHT — a different job from an empty dirty room. */
    val occupied: Boolean,
    /** "BK-… · Mr Rahman" when occupied. */
    val guest: String,
)

/** The shape of the morning, over the whole property whatever filter is on. */
data class HkCounts(
    val clean: Int,
    val dirty: Int,
    val cleaning: Int,
    val outOfOrder: Int,
) {
    fun of(status: String): Int = when (status) {
        "clean" -> clean
        "dirty" -> dirty
        "cleaning" -> cleaning
        "out_of_order" -> outOfOrder
        else -> 0
    }
}

data class HkBoard(
    val branchId: Long?,
    /** In SERVER order — block, floor number, room number. Never re-sorted here. */
    val rooms: List<HkRoom>,
    val counts: HkCounts,
)

/** One move in a room's history; [by] is "the system" when a check-out did it. */
data class HkHistoryRow(
    val at: String,
    val from: String,
    val to: String,
    val notes: String,
    val by: String,
)

data class HkHistory(val room: String, val rows: List<HkHistoryRow>)

// ---------------------------------------------------------------------------
//  Calendar
// ---------------------------------------------------------------------------

/** One night of the month grid (`calendar/month`). */
data class HotelCalendarDay(
    val date: String,
    val weekday: String,
    val sold: Int,
    val held: Int,
    val free: Int,
    val capacity: Int,
    /** Percent, one decimal, sold AND held against every bed. */
    val occupancy: Double,
    val revenue: Double,
    val adr: Double,
    val arrivals: Int,
    val departures: Int,
    val isPast: Boolean,
)

data class HotelCalendarTotals(
    val sold: Int,
    val held: Int,
    val revenue: Double,
    val occupancy: Double,
    val adr: Double,
    val revpar: Double,
)

data class HotelCalendarMonth(
    /** "YYYY-MM". */
    val month: String,
    val from: String,
    val to: String,
    val capacity: Int,
    val days: List<HotelCalendarDay>,
    val totals: HotelCalendarTotals,
    val capacityNote: String,
)

data class HotelTapeDate(val date: String, val weekday: String, val isPast: Boolean)

/** One room on one night of the tape chart. */
data class HotelTapeCell(
    val date: String,
    val taken: Int,
    val capacity: Int,
    /** free / part / full — worked out on the server so no screen disagrees. */
    val state: String,
    val bookingNo: String,
    val guest: String,
    val status: String,
    val shared: Boolean,
)

data class HotelTapeRoom(
    val id: Long,
    val name: String,
    val building: String,
    val capacity: Int,
    val cells: List<HotelTapeCell>,
)

data class HotelTape(
    val from: String,
    val to: String,
    val dates: List<HotelTapeDate>,
    val rooms: List<HotelTapeRoom>,
)

// ---------------------------------------------------------------------------
//  Reports
// ---------------------------------------------------------------------------

/** One GUEST on the register — a booking of four is four rows. */
data class HotelRegisterRow(
    val serialNo: Int,
    val bookingNo: String,
    val bookingId: Long?,
    val status: String,
    val name: String,
    /** False when nobody was named: the booker stands in, and is marked as such. */
    val named: Boolean,
    val isPrimary: Boolean,
    val mobile: String,
    val nationalId: String,
    val address: String,
    val gender: String,
    val age: String,
    val isChild: Boolean,
    val room: String,
    val checkInDate: String,
    val checkOutDate: String,
    val bookerName: String,
)

data class HotelRegisterCounts(val inHouse: Int, val arrivals: Int, val departures: Int)

data class HotelRegister(
    val date: String,
    val mode: String,
    val branchName: String,
    val rows: List<HotelRegisterRow>,
    val counts: HotelRegisterCounts,
)

/** One receipt on the collection report; [signed] is negative for a refund. */
data class HotelCollectionRow(
    val id: Long,
    val paymentNo: String,
    val paymentDate: String,
    val purpose: String,
    val method: String,
    val amount: Double,
    val reference: String,
    val bookingNo: String,
    val bookerName: String,
    val account: String,
    val vrNo: String,
    val serialNo: Int,
    val signed: Double,
)

data class HotelNamedAmount(val name: String, val amount: Double)

data class HotelCollectionTotals(
    val received: Double,
    val refunded: Double,
    /** Netted by the server — never the column added up. */
    val net: Double,
    val count: Int,
    val byMethod: List<HotelNamedAmount>,
    val byAccount: List<HotelNamedAmount>,
)

data class HotelCollection(
    val from: String,
    val to: String,
    val rows: List<HotelCollectionRow>,
    val totals: HotelCollectionTotals,
    /** Receipts with no voucher behind them — a number somebody has to account for. */
    val unposted: Int,
)

data class HotelPerformanceTotals(
    val roomNightsAvailable: Int,
    val occupancy: Double,
    val adr: Double,
    val revpar: Double,
    val roomNightsSold: Int,
    val bedNightsSold: Int,
    val revenue: Double,
    val bedNightsAvailable: Int,
    val bedOccupancy: Double,
    val heldRoomNights: Int,
)

data class HotelPerformanceDay(
    val date: String,
    val sold: Int,
    val held: Int,
    val revenue: Double,
    val free: Int,
    val roomNightsAvailable: Int,
    val occupancy: Double,
    val adr: Double,
    val revpar: Double,
)

data class HotelPerformanceRoomType(
    val name: String,
    val rooms: Int,
    val sold: Int,
    val revenue: Double,
    val roomNightsAvailable: Int,
    val occupancy: Double,
    val adr: Double,
    val revpar: Double,
)

data class HotelPerformance(
    val from: String,
    val to: String,
    val days: Int,
    val rooms: Int,
    val beds: Int,
    val totals: HotelPerformanceTotals,
    val daily: List<HotelPerformanceDay>,
    val byRoomType: List<HotelPerformanceRoomType>,
)

/**
 * The hotel's OPERATIONS reads — the board, the calendar, the three reports.
 *
 * Kept apart from [HotelRepository], which takes and allots bookings: nothing
 * in here writes a night or moves money. The one write is the housekeeping
 * move, which changes whether a room may be SOLD, never whether it is.
 *
 * Every endpoint answers the same envelope — `data.data` on success, and on a
 * refusal `success:false` with a SENTENCE in `message` that is shown verbatim,
 * because "not a lodging property" and "more than a year of days" are
 * different problems with different answers. The HTTP status is not what is
 * branched on: a refusal may arrive as 200, 201 or 422.
 */
class HotelOpsRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ------------------------------------------------------------------
    //  Housekeeping
    // ------------------------------------------------------------------

    /**
     * Every room of the property with its state and who is in it tonight.
     *
     * The status filter is applied on the SCREEN, not here: the counts are
     * over the whole property whatever is being looked at, and one read
     * serves both.
     */
    suspend fun fetchBoard(branchId: Long?): Resource<HkBoard> = withContext(ioDispatcher) {
        guarded {
            val payload = read(
                path = "hotel-setup/housekeeping",
                params = branchParams(branchId),
                denied = "You do not have permission to see the housekeeping board.",
                fallback = "The board could not be read.",
            ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
            val counts = payload.obj("counts")
            Resource.Success(
                HkBoard(
                    branchId = payload.long("branch_id"),
                    rooms = payload.arr("rooms").mapNotNull { it.toHkRoom() },
                    counts = HkCounts(
                        clean = counts?.int("clean") ?: 0,
                        dirty = counts?.int("dirty") ?: 0,
                        cleaning = counts?.int("cleaning") ?: 0,
                        outOfOrder = counts?.int("out_of_order") ?: 0,
                    ),
                )
            )
        }
    }

    /**
     * Moves one room into a state (`housekeeping/move/{id}`).
     *
     * Out of order is refused without a note — it takes the room off the
     * market until a person clears it, and a room nobody can sell for a
     * reason nobody wrote down stays out of order until it is noticed.
     */
    suspend fun moveRoom(roomId: Long, status: String, notes: String?): Resource<String> =
        withContext(ioDispatcher) {
            guarded {
                val body = buildMap {
                    put("status", status)
                    notes?.trim()?.takeIf { it.isNotEmpty() }?.let { put("notes", it) }
                }
                val response = api.post("hotel-setup/housekeeping/move/$roomId", body)
                if (response.code() == 401) return@guarded sessionExpired()
                if (response.code() == 403) {
                    return@guarded Resource.Error("You do not have permission to move rooms.")
                }
                val respBody = response.bodyOrErrorBody()
                val success = respBody?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                val message = respBody?.text("message")?.ifBlank { null }
                when {
                    success == false -> Resource.Error(message ?: "The move was refused.")
                    !response.isSuccessful && response.code() != 201 ->
                        Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")
                    else -> Resource.Success(message ?: "Moved.")
                }
            }
        }

    /** One room's history, newest first (`housekeeping/history/{id}`). */
    suspend fun fetchHistory(roomId: Long): Resource<HkHistory> = withContext(ioDispatcher) {
        guarded {
            val payload = read(
                path = "hotel-setup/housekeeping/history/$roomId",
                params = emptyMap(),
                denied = "You do not have permission to see the housekeeping board.",
                fallback = "Could not read its history.",
            ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
            Resource.Success(
                HkHistory(
                    room = payload.text("room"),
                    rows = payload.arr("rows").map { o ->
                        HkHistoryRow(
                            at = o.text("at"),
                            from = o.text("from"),
                            to = o.text("to"),
                            notes = o.text("notes"),
                            by = o.text("by").ifBlank { "the system" },
                        )
                    },
                )
            )
        }
    }

    // ------------------------------------------------------------------
    //  Calendar
    // ------------------------------------------------------------------

    /** The month, one row per night (`calendar/month`). [month] is any date in it. */
    suspend fun fetchMonth(month: String, branchId: Long?): Resource<HotelCalendarMonth> =
        withContext(ioDispatcher) {
            guarded {
                val payload = read(
                    path = "hotel-setup/calendar/month",
                    params = branchParams(branchId) + ("month" to month),
                    denied = "You do not have permission to see the calendar.",
                    fallback = "The month could not be read.",
                ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
                val totals = payload.obj("totals")
                Resource.Success(
                    HotelCalendarMonth(
                        month = payload.text("month"),
                        from = payload.text("from"),
                        to = payload.text("to"),
                        capacity = payload.int("capacity") ?: 0,
                        days = payload.arr("days").map { o ->
                            HotelCalendarDay(
                                date = o.text("date").take(10),
                                weekday = o.text("weekday"),
                                sold = o.int("sold") ?: 0,
                                held = o.int("held") ?: 0,
                                free = o.int("free") ?: 0,
                                capacity = o.int("capacity") ?: 0,
                                occupancy = o.num("occupancy") ?: 0.0,
                                revenue = o.num("revenue") ?: 0.0,
                                adr = o.num("adr") ?: 0.0,
                                arrivals = o.int("arrivals") ?: 0,
                                departures = o.int("departures") ?: 0,
                                isPast = o.flag("is_past"),
                            )
                        },
                        totals = HotelCalendarTotals(
                            sold = totals?.int("sold") ?: 0,
                            held = totals?.int("held") ?: 0,
                            revenue = totals?.num("revenue") ?: 0.0,
                            occupancy = totals?.num("occupancy") ?: 0.0,
                            adr = totals?.num("adr") ?: 0.0,
                            revpar = totals?.num("revpar") ?: 0.0,
                        ),
                        capacityNote = payload.text("capacity_note"),
                    )
                )
            }
        }

    /**
     * Rooms down the side, nights across the top (`calendar/timeline`).
     * A property with no rooms answers with the sentence saying so.
     */
    suspend fun fetchTimeline(from: String, days: Int, branchId: Long?): Resource<HotelTape> =
        withContext(ioDispatcher) {
            guarded {
                val payload = read(
                    path = "hotel-setup/calendar/timeline",
                    params = branchParams(branchId) + mapOf(
                        "from" to from,
                        "days" to days.coerceIn(1, 31).toString(),
                    ),
                    denied = "You do not have permission to see the calendar.",
                    fallback = "The tape chart could not be read.",
                ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
                Resource.Success(
                    HotelTape(
                        from = payload.text("from"),
                        to = payload.text("to"),
                        dates = payload.arr("dates").map { o ->
                            HotelTapeDate(
                                date = o.text("date").take(10),
                                weekday = o.text("weekday"),
                                isPast = o.flag("is_past"),
                            )
                        },
                        rooms = payload.arr("rooms").mapNotNull { o ->
                            HotelTapeRoom(
                                id = o.long("id") ?: return@mapNotNull null,
                                name = o.text("name"),
                                building = o.text("building"),
                                capacity = o.int("capacity") ?: 0,
                                cells = o.arr("cells").map { c ->
                                    HotelTapeCell(
                                        date = c.text("date").take(10),
                                        taken = c.int("taken") ?: 0,
                                        capacity = c.int("capacity") ?: 0,
                                        state = c.text("state").ifBlank { "free" },
                                        bookingNo = c.text("booking_no"),
                                        guest = c.text("guest"),
                                        status = c.text("status"),
                                        shared = c.flag("shared"),
                                    )
                                },
                            )
                        },
                    )
                )
            }
        }

    // ------------------------------------------------------------------
    //  Reports
    // ------------------------------------------------------------------

    /**
     * Who slept here / arrives / leaves on one night (`reports/register`).
     *
     * [countsOnly] asks for the three numbers WITHOUT the names: every row
     * carries a guest's NID, and a dashboard tile that only wants "how many"
     * must not pull the police register across the wire to count it.
     */
    suspend fun fetchRegister(
        date: String,
        mode: String,
        branchId: Long?,
        query: String = "",
        countsOnly: Boolean = false,
    ): Resource<HotelRegister> = withContext(ioDispatcher) {
        guarded {
            val payload = read(
                path = "hotel-setup/reports/register",
                params = branchParams(branchId) + buildMap {
                    put("date", date)
                    put("mode", mode)
                    query.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
                    if (countsOnly) put("counts_only", "1")
                },
                denied = "You do not have permission to see hotel reports.",
                fallback = "The register could not be read.",
            ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
            val counts = payload.obj("counts")
            Resource.Success(
                HotelRegister(
                    date = payload.text("date").take(10),
                    mode = payload.text("mode"),
                    branchName = payload.obj("branch")?.text("name").orEmpty(),
                    rows = payload.arr("rows").map { o ->
                        HotelRegisterRow(
                            serialNo = o.int("serial_no") ?: 0,
                            bookingNo = o.text("booking_no"),
                            bookingId = o.long("booking_id"),
                            status = o.text("status"),
                            name = o.text("name"),
                            named = o.flag("named"),
                            isPrimary = o.flag("is_primary"),
                            mobile = o.text("mobile"),
                            nationalId = o.text("national_id"),
                            address = o.text("address"),
                            gender = o.text("gender"),
                            age = o.text("age"),
                            isChild = o.flag("is_child"),
                            room = o.text("room"),
                            checkInDate = o.text("check_in_date").take(10),
                            checkOutDate = o.text("check_out_date").take(10),
                            bookerName = o.text("booker_name"),
                        )
                    },
                    counts = HotelRegisterCounts(
                        inHouse = counts?.int("in_house") ?: 0,
                        arrivals = counts?.int("arrivals") ?: 0,
                        departures = counts?.int("departures") ?: 0,
                    ),
                )
            )
        }
    }

    /** Money taken between two dates, netted and broken down (`reports/collection`). */
    suspend fun fetchCollection(
        from: String,
        to: String,
        branchId: Long?,
        method: String = "",
    ): Resource<HotelCollection> = withContext(ioDispatcher) {
        guarded {
            val payload = read(
                path = "hotel-setup/reports/collection",
                params = branchParams(branchId) + buildMap {
                    put("from", from)
                    put("to", to)
                    method.takeIf { it.isNotBlank() }?.let { put("method", it) }
                },
                denied = "You do not have permission to see hotel reports.",
                fallback = "The collection report could not be read.",
            ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
            val totals = payload.obj("totals")
            Resource.Success(
                HotelCollection(
                    from = payload.text("from").take(10),
                    to = payload.text("to").take(10),
                    rows = payload.arr("rows").mapNotNull { o ->
                        val amount = o.num("amount") ?: 0.0
                        HotelCollectionRow(
                            id = o.long("id") ?: return@mapNotNull null,
                            paymentNo = o.text("payment_no"),
                            paymentDate = o.text("payment_date").take(10),
                            purpose = o.text("purpose"),
                            method = o.text("method"),
                            amount = amount,
                            reference = o.text("reference"),
                            bookingNo = o.text("booking_no"),
                            bookerName = o.text("booker_name"),
                            account = o.text("account"),
                            vrNo = o.text("vr_no"),
                            serialNo = o.int("serial_no") ?: 0,
                            signed = o.num("signed") ?: amount,
                        )
                    },
                    totals = HotelCollectionTotals(
                        received = totals?.num("received") ?: 0.0,
                        refunded = totals?.num("refunded") ?: 0.0,
                        net = totals?.num("net") ?: 0.0,
                        count = totals?.int("count") ?: 0,
                        byMethod = totals?.arr("by_method").orEmpty().map { it.toNamedAmount() },
                        byAccount = totals?.arr("by_account").orEmpty().map { it.toNamedAmount() },
                    ),
                    unposted = payload.int("unposted") ?: 0,
                )
            )
        }
    }

    /**
     * Occupancy, ADR and RevPAR over a range (`reports/performance`).
     *
     * A branch that does not let rooms by the night is refused with the
     * sentence [isNotLodgingRefusal] recognises — the screen hides the tab on
     * it rather than showing a month of noughts that reads as a bad month.
     */
    suspend fun fetchPerformance(
        from: String,
        to: String,
        branchId: Long?,
    ): Resource<HotelPerformance> = withContext(ioDispatcher) {
        guarded {
            val payload = read(
                path = "hotel-setup/reports/performance",
                params = branchParams(branchId) + mapOf("from" to from, "to" to to),
                denied = "You do not have permission to see hotel reports.",
                fallback = "The performance report could not be read.",
            ).let { if (it is Resource.Error) return@guarded it else (it as Resource.Success).data }
            val t = payload.obj("totals")
            Resource.Success(
                HotelPerformance(
                    from = payload.text("from").take(10),
                    to = payload.text("to").take(10),
                    days = payload.int("days") ?: 0,
                    rooms = payload.int("rooms") ?: 0,
                    beds = payload.int("beds") ?: 0,
                    totals = HotelPerformanceTotals(
                        roomNightsAvailable = t?.int("room_nights_available") ?: 0,
                        occupancy = t?.num("occupancy") ?: 0.0,
                        adr = t?.num("adr") ?: 0.0,
                        revpar = t?.num("revpar") ?: 0.0,
                        roomNightsSold = t?.int("room_nights_sold") ?: 0,
                        bedNightsSold = t?.int("bed_nights_sold") ?: 0,
                        revenue = t?.num("revenue") ?: 0.0,
                        bedNightsAvailable = t?.int("bed_nights_available") ?: 0,
                        bedOccupancy = t?.num("bed_occupancy") ?: 0.0,
                        heldRoomNights = t?.int("held_room_nights") ?: 0,
                    ),
                    daily = payload.arr("daily").map { o ->
                        HotelPerformanceDay(
                            date = o.text("date").take(10),
                            sold = o.int("sold") ?: 0,
                            held = o.int("held") ?: 0,
                            revenue = o.num("revenue") ?: 0.0,
                            free = o.int("free") ?: 0,
                            roomNightsAvailable = o.int("room_nights_available") ?: 0,
                            occupancy = o.num("occupancy") ?: 0.0,
                            adr = o.num("adr") ?: 0.0,
                            revpar = o.num("revpar") ?: 0.0,
                        )
                    },
                    byRoomType = payload.arr("by_room_type").map { o ->
                        HotelPerformanceRoomType(
                            name = o.text("name"),
                            rooms = o.int("rooms") ?: 0,
                            sold = o.int("sold") ?: 0,
                            revenue = o.num("revenue") ?: 0.0,
                            roomNightsAvailable = o.int("room_nights_available") ?: 0,
                            occupancy = o.num("occupancy") ?: 0.0,
                            adr = o.num("adr") ?: 0.0,
                            revpar = o.num("revpar") ?: 0.0,
                        )
                    },
                )
            )
        }
    }

    // ------------------------------------------------------------------
    //  Envelope
    // ------------------------------------------------------------------

    /**
     * One GET through the hotel envelope: 401 → session expired, 403 → the
     * caller's own wording, `success:false` → the server's sentence verbatim,
     * else the payload at `data.data` (falling back to `data`).
     */
    private suspend fun read(
        path: String,
        params: Map<String, String>,
        denied: String,
        fallback: String,
    ): Resource<JsonObject> {
        val response = api.get(path, params)
        if (response.code() == 401) return sessionExpired()
        if (response.code() == 403) return Resource.Error(denied)
        val body = response.bodyOrErrorBody()
            ?: return Resource.Error("Server error (${response.code()}). Please try again later.")
        if (body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
            return Resource.Error(body.text("message").ifBlank { fallback })
        }
        if (!response.isSuccessful && response.code() != 201) {
            // Laravel's own validation answer has no `success` key at all.
            return Resource.Error(body.text("message").ifBlank { "Server error (${response.code()}). Please try again later." })
        }
        val payload = body.obj("data")?.obj("data") ?: body.obj("data")
            ?: return Resource.Error(fallback)
        return Resource.Success(payload)
    }

    private fun sessionExpired(): Resource.Error =
        Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)

    /** The network and parse failures every call shares, worded once. */
    private inline fun <T> guarded(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    /**
     * A refusal's JSON lives in the error body when the HTTP status is 422 —
     * the same sentence the 200-with-success:false case carries.
     */
    private fun retrofit2.Response<com.google.gson.JsonElement>.bodyOrErrorBody(): JsonObject? = (
        body() ?: errorBody()?.let { runCatching { JsonParser.parseString(it.string()) }.getOrNull() }
        )?.takeIf { it.isJsonObject }?.asJsonObject

    /** `branch_id` only when a branch is chosen — absent, the server uses the user's own. */
    private fun branchParams(branchId: Long?): Map<String, String> =
        if (branchId == null) emptyMap() else mapOf("branch_id" to branchId.toString())

    private fun JsonObject.toHkRoom(): HkRoom? {
        val id = long("id") ?: return null
        return HkRoom(
            id = id,
            name = text("name").ifBlank { "Room $id" },
            buildingId = long("building_id"),
            building = text("building"),
            buildingName = text("building_name"),
            floorId = long("floor_id"),
            floorNo = int("floor_no"),
            floorName = text("floor_name"),
            status = text("status").ifBlank { "clean" },
            notes = text("notes"),
            changedAt = text("changed_at"),
            occupied = flag("occupied"),
            guest = text("guest"),
        )
    }

    private fun JsonObject.toNamedAmount(): HotelNamedAmount =
        HotelNamedAmount(name = text("name"), amount = num("amount") ?: 0.0)

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): List<JsonObject> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject }
            .orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.num(key: String): Double? = text(key).toDoubleOrNull()

    private fun JsonObject.long(key: String): Long? = num(key)?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    /** A JSON boolean that may arrive as true/false, 1/0 or "1"/"0". */
    private fun JsonObject.flag(key: String): Boolean =
        text(key).let { it == "1" || it.equals("true", ignoreCase = true) }

    companion object {
        /** How the performance report begins its refusal on a branch that lets no rooms. */
        private const val NOT_LODGING_PREFIX = "These figures are for a hotel"

        fun isNotLodgingRefusal(message: String?): Boolean =
            message?.startsWith(NOT_LODGING_PREFIX) == true

        @Volatile
        private var instance: HotelOpsRepository? = null

        /** The shared instance, built on the app-wide report API service. */
        fun get(context: Context): HotelOpsRepository = instance ?: synchronized(this) {
            instance ?: HotelOpsRepository(
                api = ServiceLocator.provideReportApiService(context.applicationContext),
            ).also { instance = it }
        }
    }
}
