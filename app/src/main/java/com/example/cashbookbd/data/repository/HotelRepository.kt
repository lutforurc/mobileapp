package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** One row of the hotel's booking list (`hotel-setup/bookings`). */
data class HotelBookingRow(
    val id: Long,
    val bookingNo: String,
    /** hold / confirmed / checked_in / checked_out / cancelled / expired. */
    val status: String,
    val bookingType: String,
    val bookerName: String,
    val bookerMobile: String,
    val checkInDate: String,
    val checkOutDate: String,
    val nights: Int,
    /** Tentative holds only: when the beds go back if nobody rings. */
    val holdUntil: String,
    /** What was stated on the telephone. */
    val statedRooms: Int,
    val statedAdults: Int,
    val statedChildren: Int,
    /** How many of the party are actually written down (checked in). */
    val guestsCount: Int,
)

/** One bed of a room that is sold by the seat. */
data class HotelSeat(
    val id: Long,
    val code: String,
    val name: String,
    val rent: Double?,
    /** free / booked / in_house — a bed has only the two answers. */
    val state: String,
    /** "BK-2026-00042 · Mr Rahman" when something holds it. */
    val takenBy: String,
) {
    val isFree: Boolean get() = state == "free"
    val label: String get() = name.ifBlank { code }
}

/** One room on the availability grid, on the dates asked for. */
data class HotelRoom(
    val id: Long,
    val code: String,
    val displayName: String,
    val roomType: String,
    /** whole / seat / both — what this room may be sold as. */
    val saleMode: String,
    val capacity: Int,
    val rent: Double?,
    val beds: Int,
    val freeBeds: Int,
    /** free / booked / in_house / part / closed. */
    val state: String,
    /** Why it cannot be taken, as a sentence — never a bare flag. */
    val blockedReason: String,
    val takenBy: String,
    /** Empty unless the room is sold bed by bed. */
    val seats: List<HotelSeat>,
    val buildingName: String,
    val floorName: String,
) {
    val isFree: Boolean get() = state == "free"
    val sellsSeats: Boolean get() = saleMode == "seat" || saleMode == "both"
}

/** What the availability read answers with. */
data class HotelAvailability(
    val checkInDate: String,
    val checkOutDate: String,
    val nights: Int,
    val freeCount: Int,
    val rooms: List<HotelRoom>,
    /** When the property's day turns over — shown where the rooms are. */
    val checkInTime: String,
    val checkOutTime: String,
)

/** One guest written down against a room at check-in. */
data class HotelGuestEntry(
    val name: String,
    val mobile: String = "",
    val nationalId: String = "",
    val address: String = "",
    val gender: String = "",
    val age: String = "",
    val isChild: Boolean = false,
)

/** One room of a booking, as the allotment screen reads it. */
data class HotelAllotmentRoom(
    val roomId: Long,
    val displayName: String,
    val capacity: Int,
    /** whole / seat — how this room was let on this booking. */
    val letAs: String,
    val guests: List<HotelGuestEntry>,
    /** No identified guest yet: the police register is built from an NID. */
    val needsIdentified: Boolean,
    /** No mobile in the room yet — one for the room is enough. */
    val needsMobile: Boolean,
)

/** The allotment read: the booking, its rooms, and who has actually arrived. */
data class HotelAllotment(
    val bookingId: Long,
    val bookingNo: String,
    val status: String,
    val bookerName: String,
    val checkInDate: String,
    val checkOutDate: String,
    /** Both numbers, never one: booked for twelve and ten arrived is a fact. */
    val stated: Int,
    val arrived: Int,
    val roomsOutstanding: Int,
    val rooms: List<HotelAllotmentRoom>,
)

/** Somebody this company already has on its customer list, for a corporate bill. */
data class HotelParty(
    val id: Long,
    val name: String,
    val mobile: String,
    val code: String,
)

/** What the property already knows about a telephone number. */
data class HotelReturningGuest(
    val found: Boolean,
    val name: String,
    val mobile: String,
    /** How many stays this number has behind it, cancellations excluded. */
    val stays: Int,
    val lastStay: String,
    /** Offered so the form can follow, never applied by the server. */
    val bookingType: String,
    val billedToPartyId: Long?,
)

/** A page of [HotelBookingRow]s with the paginator meta the footer needs. */
data class HotelBookingPage(
    val rows: List<HotelBookingRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/**
 * The hotel module's reads.
 *
 * Only the booking list for now — the setup lists and their forms ride the
 * shared AppList/CrudForms engines, which speak to the same endpoints without
 * a repository of their own.
 */
class HotelRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * The booking list, newest arrival first — except when filtered to holds,
     * where the server reorders to soonest-to-lapse: what the desk wants from
     * that list is "who do I ring before their beds go back".
     */
    suspend fun fetchBookings(
        status: String?,
        search: String,
        page: Int,
        perPage: Int = 20,
        dateFrom: String? = null,
        dateTo: String? = null,
        kind: String = "stay",
    ): Resource<HotelBookingPage> = withContext(ioDispatcher) {
        try {
            val params = buildMap {
                put("page", page.toString())
                put("per_page", perPage.toString())
                status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
                search.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
                // The dates cut by ARRIVAL, not by overlap: the question this
                // list is asked is who is coming between two days, not which
                // stays happen to touch them. Either end alone is a real
                // filter, so each is sent only when it is set.
                dateFrom?.takeIf { it.isNotBlank() }?.let { put("date_from", it) }
                dateTo?.takeIf { it.isNotBlank() }?.let { put("date_to", it) }
                // stay (the default) keeps walk-in meals off the list the front
                // desk runs on: a restaurant serves more people in a fortnight
                // than the rooms take in a year, and the desk would be paging
                // past lunches to find who is arriving tonight.
                put("kind", kind)
            }
            val response = api.get("hotel-setup/bookings", params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see bookings.")
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            // foundData's double wrap, then this endpoint's own envelope:
            // data.data.bookings is the paginator, beside the property's times.
            val payload = body.obj("data")?.obj("data") ?: body.obj("data")
            val paginator = payload?.obj("bookings")
            val rows = paginator?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.toBookingRow() }
                .orEmpty()
            Resource.Success(
                HotelBookingPage(
                    rows = rows,
                    currentPage = paginator?.int("current_page") ?: page,
                    lastPage = paginator?.int("last_page") ?: 1,
                    total = paginator?.int("total") ?: rows.size,
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

/**
     * What is free on the dates asked for (`bookings/availability`).
     *
     * The seat is the inventory and the room is only how a clerk refers to
     * them, so a room sold by the bed comes back with its beds listed and
     * priced one each; a room sold whole comes back without them.
     */
    suspend fun fetchAvailability(
        checkIn: String,
        checkOut: String,
    ): Resource<HotelAvailability> = withContext(ioDispatcher) {
        try {
            val response = api.get(
                "hotel-setup/bookings/availability",
                mapOf("check_in_date" to checkIn, "check_out_date" to checkOut),
            )
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see bookings.")
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            // A property with nothing set up answers with the sentence that
            // says so — which is the answer, not a failure.
            if (body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(
                    body.get("message")?.takeUnless { it.isJsonNull }?.asString
                        ?: "No rooms on this property yet.",
                )
            }
            val payload = body.obj("data")?.obj("data") ?: body.obj("data")
                ?: return@withContext Resource.Error("The property could not be read.")

            val rooms = mutableListOf<HotelRoom>()
            payload.get("buildings")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { bEl ->
                val building = bEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val buildingName = building.text("name")
                building.get("floors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { fEl ->
                    val floor = fEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    val floorName = floor.text("name")
                    floor.get("rooms")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { rEl ->
                        rEl.takeIf { it.isJsonObject }?.asJsonObject
                            ?.toRoom(buildingName, floorName)?.let { rooms.add(it) }
                    }
                }
                // A resort's cottages, and rooms whose floor nobody has said —
                // their own group rather than an invented floor to hold them.
                building.get("unfloored")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { rEl ->
                    rEl.takeIf { it.isJsonObject }?.asJsonObject
                        ?.toRoom(buildingName, "")?.let { rooms.add(it) }
                }
            }

            val times = payload.obj("times")
            Resource.Success(
                HotelAvailability(
                    checkInDate = payload.text("check_in_date").take(10),
                    checkOutDate = payload.text("check_out_date").take(10),
                    nights = payload.int("nights") ?: 0,
                    freeCount = payload.int("free_count") ?: 0,
                    rooms = rooms,
                    checkInTime = times?.text("check_in").orEmpty(),
                    checkOutTime = times?.text("check_out").orEmpty(),
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Takes the booking (`bookings/store`) — the telephone call.
     *
     * Rooms and beds travel as two lists because they are two different things
     * being bought: a room id arriving in the bed list would otherwise be
     * treated as a bed. At least one of the two must be there.
     */
    suspend fun storeBooking(
        roomIds: List<Long>,
        seatIds: List<Long>,
        checkIn: String,
        checkOut: String,
        bookingType: String,
        status: String,
        bookerName: String,
        bookerMobile: String,
        statedAdults: String,
        statedChildren: String,
        notes: String,
        billedToPartyId: Long? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val body = JsonObject().apply {
                add("room_ids", JsonArray().apply { roomIds.forEach { add(it) } })
                add("seat_ids", JsonArray().apply { seatIds.forEach { add(it) } })
                addProperty("check_in_date", checkIn)
                addProperty("check_out_date", checkOut)
                addProperty("booking_type", bookingType)
                addProperty("status", status)
                addProperty("booker_name", bookerName.trim())
                bookerMobile.trim().takeIf { it.isNotEmpty() }?.let { addProperty("booker_mobile", it) }
                statedAdults.trim().toIntOrNull()?.let { addProperty("stated_adults", it) }
                statedChildren.trim().toIntOrNull()?.let { addProperty("stated_children", it) }
                notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
                // A corporate booking is refused without one: the bill goes to
                // a company and the money comes later, so with no party there
                // is nobody for the ageing report to chase.
                billedToPartyId?.let { addProperty("billed_to_party_id", it) }
            }
            postForMessage("hotel-setup/bookings/store", body, "Booking saved.")
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

/**
     * Has this telephone number been here before (`bookings/guest`)?
     *
     * Asked as the mobile is typed on the booking form, so a returning guest
     * is not made to give their name again. What comes back is OFFERED, never
     * applied: the server hands over the last booking's type and company too,
     * because a guest who came on a company account usually is again — but it
     * is the clerk who says so.
     */
    suspend fun findReturningGuest(mobile: String): Resource<HotelReturningGuest> =
        withContext(ioDispatcher) {
            try {
                val response = api.get("hotel-setup/bookings/guest", mapOf("mobile" to mobile.trim()))
                if (response.code() == 401) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                }
                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@withContext Resource.Error("Could not look that number up.")
                val payload = body.obj("data")?.obj("data") ?: body.obj("data")
                val found = payload?.flag("found") == true
                Resource.Success(
                    HotelReturningGuest(
                        found = found,
                        name = payload?.text("name").orEmpty(),
                        mobile = payload?.text("mobile").orEmpty(),
                        stays = payload?.int("stays") ?: 0,
                        lastStay = payload?.text("last_stay").orEmpty().take(10),
                        bookingType = payload?.text("booking_type").orEmpty(),
                        billedToPartyId = payload?.long("billed_to_party_id"),
                    )
                )
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /**
     * The companies a corporate booking may be billed to (`bookings/parties`).
     *
     * These are `cust_party_infos` ids — what `billed_to_party_id` points at,
     * NOT the coa4 ids the older account dropdowns answer with.
     */
    suspend fun searchParties(query: String): Resource<List<HotelParty>> = withContext(ioDispatcher) {
        try {
            val response = api.get(
                "hotel-setup/bookings/parties",
                buildMap { query.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) } },
            )
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Could not read the customer list.")
            val rows = (body.obj("data")?.get("data") ?: body.get("data"))
                ?.takeIf { it.isJsonArray }?.asJsonArray
            Resource.Success(
                rows?.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    HotelParty(
                        id = o.long("id") ?: return@mapNotNull null,
                        name = o.text("name"),
                        mobile = o.text("mobile"),
                        code = o.text("idfr_code"),
                    )
                }.orEmpty()
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** The booking reopened on the day the guests arrive (`bookings/allotment/{id}`). */
    suspend fun fetchAllotment(bookingId: Long): Resource<HotelAllotment> = withContext(ioDispatcher) {
        try {
            val response = api.get("hotel-setup/bookings/allotment/$bookingId", emptyMap())
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to check guests in.")
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            if (body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(
                    body.get("message")?.takeUnless { it.isJsonNull }?.asString ?: "Booking not found.",
                )
            }
            val payload = body.obj("data")?.obj("data") ?: body.obj("data")
                ?: return@withContext Resource.Error("The booking could not be read.")
            val booking = payload.obj("booking")
            Resource.Success(
                HotelAllotment(
                    bookingId = booking?.long("id") ?: bookingId,
                    bookingNo = booking?.text("booking_no").orEmpty(),
                    status = booking?.text("status").orEmpty(),
                    bookerName = booking?.text("booker_name").orEmpty(),
                    checkInDate = booking?.text("check_in_date").orEmpty().take(10),
                    checkOutDate = booking?.text("check_out_date").orEmpty().take(10),
                    stated = payload.int("stated") ?: 0,
                    arrived = payload.int("arrived") ?: 0,
                    roomsOutstanding = payload.int("rooms_outstanding") ?: 0,
                    rooms = payload.get("rooms")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.toAllotmentRoom() }
                        .orEmpty(),
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Writes down the people in ONE room (`bookings/allot/{id}`).
     *
     * One room at a time because that is how a desk works — a family checks in
     * while the coach party is still unloading. The list REPLACES what was
     * there for that room. The server asks for one NID and one mobile AMONG
     * them rather than from everybody: a required field that cannot be filled
     * gets filled with rubbish, which is worse than empty.
     */
    suspend fun allotRoom(
        bookingId: Long,
        roomId: Long,
        guests: List<HotelGuestEntry>,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val body = JsonObject().apply {
                addProperty("room_id", roomId)
                add(
                    "guests",
                    JsonArray().apply {
                        guests.forEach { g ->
                            add(
                                JsonObject().apply {
                                    addProperty("name", g.name.trim())
                                    g.mobile.trim().takeIf { it.isNotEmpty() }?.let { addProperty("mobile", it) }
                                    g.nationalId.trim().takeIf { it.isNotEmpty() }
                                        ?.let { addProperty("national_id", it) }
                                    g.address.trim().takeIf { it.isNotEmpty() }?.let { addProperty("address", it) }
                                    g.gender.takeIf { it.isNotBlank() }?.let { addProperty("gender", it) }
                                    g.age.trim().toIntOrNull()?.let { addProperty("age", it) }
                                    addProperty("is_child", g.isChild)
                                }
                            )
                        }
                    },
                )
            }
            postForMessage("hotel-setup/bookings/allot/$bookingId", body, "Guests checked in.")
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Shared POST to message. The hotel endpoints refuse with a SENTENCE and a
     * 422, never a bare flag — "sold by the bed" and "taken until Friday" are
     * different problems with different answers — so the refusal is shown
     * verbatim rather than replaced with one of our own.
     */
    private suspend fun postForMessage(
        path: String,
        body: JsonObject,
        fallback: String,
    ): Resource<String> {
        val response = api.postObjectRaw(path, body)
        if (response.code() == 401) {
            return Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        }
        if (response.code() == 403) {
            return Resource.Error("You do not have permission for that.")
        }
        val respBody = (
            response.body() ?: response.errorBody()?.let {
                runCatching { JsonParser.parseString(it.string()) }.getOrNull()
            }
            )?.takeIf { it.isJsonObject }?.asJsonObject
        val success = respBody?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = respBody?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        return when {
            success == false -> Resource.Error(message ?: "The request was refused.")
            !response.isSuccessful && response.code() != 201 ->
                Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")
            else -> Resource.Success(message ?: fallback)
        }
    }

    private fun JsonObject.toRoom(buildingName: String, floorName: String): HotelRoom? {
        val id = long("id") ?: return null
        return HotelRoom(
            id = id,
            code = text("code"),
            displayName = text("display_name").ifBlank { text("code") },
            roomType = text("room_type"),
            saleMode = text("sale_mode"),
            capacity = int("capacity") ?: 0,
            rent = text("rent").toDoubleOrNull(),
            beds = int("beds") ?: 0,
            freeBeds = int("free_beds") ?: 0,
            state = text("state"),
            blockedReason = text("blocked_reason"),
            takenBy = text("taken_by"),
            seats = get("seats")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    HotelSeat(
                        id = o.long("id") ?: return@mapNotNull null,
                        code = o.text("code"),
                        name = o.text("name"),
                        rent = o.text("rent").toDoubleOrNull(),
                        state = o.text("state"),
                        takenBy = o.text("taken_by"),
                    )
                }
                .orEmpty(),
            buildingName = buildingName,
            floorName = floorName,
        )
    }

    private fun JsonObject.toAllotmentRoom(): HotelAllotmentRoom? {
        val roomId = long("room_id") ?: return null
        return HotelAllotmentRoom(
            roomId = roomId,
            displayName = text("display_name").ifBlank { "Room $roomId" },
            capacity = int("capacity") ?: 0,
            letAs = text("let_as"),
            guests = get("guests")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    HotelGuestEntry(
                        name = o.text("name"),
                        mobile = o.text("mobile"),
                        nationalId = o.text("national_id"),
                        address = o.text("address"),
                        gender = o.text("gender"),
                        age = o.text("age"),
                        isChild = o.flag("is_child"),
                    )
                }
                .orEmpty(),
            needsIdentified = flag("needs_identified"),
            needsMobile = flag("needs_mobile"),
        )
    }

    /** A JSON boolean that may arrive as true/false, 1/0 or "1"/"0". */
    private fun JsonObject.flag(key: String): Boolean =
        text(key).let { it == "1" || it.equals("true", ignoreCase = true) }

    private fun JsonObject.toBookingRow(): HotelBookingRow? {
        val id = long("id") ?: return null
        return HotelBookingRow(
            id = id,
            bookingNo = text("booking_no"),
            status = text("status"),
            bookingType = text("booking_type"),
            bookerName = text("booker_name"),
            bookerMobile = text("booker_mobile"),
            checkInDate = text("check_in_date").take(10),
            checkOutDate = text("check_out_date").take(10),
            nights = int("nights") ?: 0,
            holdUntil = text("hold_until").replace('T', ' ').take(16),
            statedRooms = int("stated_rooms") ?: 0,
            statedAdults = int("stated_adults") ?: 0,
            statedChildren = int("stated_children") ?: 0,
            guestsCount = int("guests_count") ?: 0,
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
}
