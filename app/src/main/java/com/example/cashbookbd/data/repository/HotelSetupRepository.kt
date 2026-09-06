package com.example.cashbookbd.data.repository

import android.content.Context
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

// ---------------------------------------------------------------------------
//  What the setup screens read and write
// ---------------------------------------------------------------------------

/** A kind of bookable thing (`resources/types`): room, hall, community centre… */
data class HotelResourceType(
    val id: Long,
    val code: String,
    val name: String,
    /** night for a room, slot for a hall — the fact that decides half the form. */
    val rateUnit: String,
) {
    val isRoom: Boolean get() = code == "room"
    /** Let by the sitting, measured in chairs — never in beds. */
    val isHall: Boolean get() = rateUnit == "slot"
}

/** One entry of a `…/ddl` answer, with the extras the room form reads off it. */
data class HotelDdlOption(
    val value: Long,
    val label: String,
    val label2: String = "",
    /** Facilities only: room / hall / both. */
    val appliesTo: String = "",
    /** Room types only: what picking one fills the form in with. */
    val capacity: Int? = null,
    val defaultSeatCount: Int? = null,
    val defaultSaleMode: String? = null,
    val defaultWholeRent: String? = null,
    val defaultSeatRent: String? = null,
)

/** One row of the rooms list (`hotel-setup/resources`). Seats are not listed — they are reached through their room. */
data class HotelResourceRow(
    val id: Long,
    val code: String,
    val name: String,
    val displayName: String,
    /** The type's code: room / hall / community_centre / … */
    val kind: String,
    val typeName: String,
    val buildingName: String,
    val floorName: String,
    val roomTypeName: String,
    val saleMode: String,
    val capacity: Int,
    val rent: Double?,
    val seatsCount: Int,
    val activeSeatsCount: Int,
    val status: Int,
    val facilities: List<String>,
    val description: String,
)

data class HotelResourcePage(
    val rows: List<HotelResourceRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** One bed of a room, as the seat editor reads and writes it. */
data class HotelSeatRow(
    val id: Long,
    val code: String,
    val name: String,
    val rent: Double?,
    val status: Int,
) {
    val label: String get() = if (name.isNotBlank()) "$code · $name" else "Seat $code"
}

/** One room with its beds and its ticks (`resources/edit/{id}`) — what the form loads. */
data class HotelResourceDetail(
    val id: Long,
    val resourceTypeId: Long?,
    val buildingId: Long?,
    val floorId: Long?,
    val roomTypeId: Long?,
    val code: String,
    val name: String,
    val displayName: String,
    val saleMode: String,
    val capacity: Int,
    val rent: String,
    val status: Int,
    val sortOrder: Int,
    val description: String,
    val facilityIds: List<Long>,
    val seats: List<HotelSeatRow>,
) {
    val activeSeatCount: Int get() = seats.count { it.status == 1 }
}

/** What the room form sends. Text where the box is text: the server parses. */
data class HotelRoomDraft(
    val resourceTypeId: Long,
    val buildingId: Long,
    val floorId: Long?,
    val roomTypeId: Long?,
    val code: String,
    val name: String,
    val saleMode: String,
    val capacity: Int,
    val rent: String,
    val seatCount: Int?,
    val seatRent: String?,
    val description: String,
    /** Sent even when empty — an empty list is the answer "none of them". */
    val facilityIds: List<Long>,
    val status: Int,
    val sortOrder: Int = 0,
)

/** One room on the property drawing. */
data class LayoutRoom(
    val id: Long,
    val code: String,
    val name: String,
    val displayName: String,
    val kind: String,
    val roomTypeId: Long?,
    val roomType: String,
    val roomTypeCode: String,
    val saleMode: String,
    val capacity: Int,
    val rent: Double?,
    val status: Int,
    val description: String,
    val facilities: List<String>,
    /** Null on a room; a hall carries the property's sittings (maybe none). */
    val sittings: List<String>?,
    val beds: Int,
    val activeBeds: Int,
    val seatRentMin: Double?,
    val seatRentMax: Double?,
) {
    val isHall: Boolean get() = kind == "hall" || kind == "community_centre"
    val isActive: Boolean get() = status == 1
}

data class HotelLayoutFloor(
    val id: Long,
    val name: String,
    val floorNo: Int?,
    val status: Int,
    val rooms: List<LayoutRoom>,
)

data class LayoutBuilding(
    val id: Long,
    val name: String,
    val code: String,
    val status: Int,
    /** Ground-first, as the server sends them; the drawing reverses. */
    val floors: List<HotelLayoutFloor>,
    val unfloored: List<LayoutRoom>,
    val roomsCount: Int,
    val bedsCount: Int,
    val hallsCount: Int,
    val seatsCount: Int,
    val rentMin: Double?,
    val rentMax: Double?,
    val seatRentMin: Double?,
    val seatRentMax: Double?,
)

/** When the property's day turns over — branch metas, surfaced beside the rooms. */
data class HotelTimes(
    val checkIn: String,
    val checkOut: String,
    val holdHours: Int,
    val holdMaxHours: Int,
)

data class HotelLayout(
    val buildings: List<LayoutBuilding>,
    val times: HotelTimes?,
)

/** One row of the service-charge history. */
data class HotelTaxRateRow(
    val id: Long,
    val taxType: String,
    val rate: Double,
    val effectiveFrom: String,
    val effectiveTo: String,
    val notes: String,
    val isShipped: Boolean,
    val inForce: Boolean,
) {
    /** Only a rate of this property's own that has not started yet may go. */
    val removable: Boolean get() = !isShipped && !inForce && effectiveTo.isBlank()
}

data class HotelTaxRates(
    val rows: List<HotelTaxRateRow>,
    /** What a bill made today would carry — worked out by the bill's own lookup. */
    val currentServiceCharge: Double,
    val on: String,
    val note: String,
)

/** A sitting the property sells (`bookings/halls` → slots). */
data class HallSlot(val id: Long, val code: String, val name: String, val label: String)

/** One cell of the hall grid: a sitting and what it is doing on that date. */
data class HallSitting(
    val slotId: Long,
    val slot: String,
    val label: String,
    /** free / closed / held / booked / checked_in. */
    val state: String,
    val blockedReason: String,
    val takenBy: String,
) {
    val isFree: Boolean get() = state == "free"
}

data class HallRow(
    val id: Long,
    val code: String,
    val name: String,
    val capacity: Int,
    /** The price of ONE sitting, never of a night. Null = no rate, not for sale. */
    val rent: Double?,
    val building: String,
    val date: String,
    val sittings: List<HallSitting>,
) {
    val displayName: String get() = name.ifBlank { code }
}

data class HallAvailability(
    val date: String,
    val slots: List<HallSlot>,
    val halls: List<HallRow>,
    val freeCount: Int,
)

/** One sitting being taken: a hall, a date and a part of that day. */
data class HallSittingPick(val resourceId: Long, val date: String, val slotId: Long)

// ---------------------------------------------------------------------------

/**
 * The hotel setup's own reads and writes: rooms and their beds, the property
 * drawing, the service charge, and the hall grid.
 *
 * Separate from [HotelRepository] because that one is the desk's — bookings,
 * arrivals — and this one is the back office's. The buildings, floors, room
 * types, facilities and sittings still ride the shared AppList/CrudForms
 * engines; what lands here is what those engines cannot draw: a form whose
 * fields depend on each other, a drawing, a grid.
 *
 * Every refusal is a SENTENCE from the server and is handed on verbatim —
 * "this building already has a room numbered 301" is the answer, not a fault.
 */
class HotelSetupRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---- Dropdowns --------------------------------------------------------

    /** The kinds a resource may be. The seat is not offered: a bed is made by splitting a room. */
    suspend fun fetchResourceTypes(): Resource<List<HotelResourceType>> = guard {
        when (val read = read(api.get("hotel-setup/resources/types", emptyMap()), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> Resource.Success(
                read.payload.rows().mapNotNull { o ->
                    HotelResourceType(
                        id = o.long("id") ?: return@mapNotNull null,
                        code = o.text("code"),
                        name = o.text("name"),
                        rateUnit = o.text("rate_unit"),
                    )
                }
            )
        }
    }

    suspend fun fetchBuildingDdl(branchId: Long? = null): Resource<List<HotelDdlOption>> =
        fetchDdl("hotel-setup/buildings/ddl", buildMap { branchId?.let { put("branch_id", it.toString()) } })

    /** The floors of ONE building — a floor from another block must never be offered. */
    suspend fun fetchFloorDdl(buildingId: Long): Resource<List<HotelDdlOption>> =
        fetchDdl("hotel-setup/floors/ddl", mapOf("building_id" to buildingId.toString()))

    /** Room types, with the defaults picking one fills the form in with. */
    suspend fun fetchRoomTypeDdl(branchId: Long? = null): Resource<List<HotelDdlOption>> =
        fetchDdl("hotel-setup/room-types/ddl", buildMap { branchId?.let { put("branch_id", it.toString()) } })

    /**
     * The whole tick list, with each row's applies_to, so the form can switch
     * between a bedroom's list and a hall's the moment the Kind changes — no
     * second round trip, no blank list while it answers.
     */
    suspend fun fetchFacilityDdl(): Resource<List<HotelDdlOption>> =
        fetchDdl("hotel-setup/facilities/ddl", emptyMap())

    private suspend fun fetchDdl(path: String, params: Map<String, String>): Resource<List<HotelDdlOption>> = guard {
        when (val read = read(api.get(path, params), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> Resource.Success(
                read.payload.rows().mapNotNull { o ->
                    HotelDdlOption(
                        value = o.long("value") ?: o.long("id") ?: return@mapNotNull null,
                        label = o.text("label").ifBlank { o.text("name") },
                        label2 = o.text("label_2"),
                        appliesTo = o.text("applies_to"),
                        capacity = o.int("capacity"),
                        defaultSeatCount = o.int("default_seat_count"),
                        defaultSaleMode = o.text("default_sale_mode").ifBlank { null },
                        defaultWholeRent = o.text("default_whole_rent").ifBlank { null },
                        defaultSeatRent = o.text("default_seat_rent").ifBlank { null },
                    )
                }
            )
        }
    }

    /** Copies the standard twenty-two into this company. Adds what is missing, touches nothing that is there. */
    suspend fun seedStandardFacilities(): Resource<String> = guard {
        postForMessage("hotel-setup/facilities/standard", JsonObject(), "Facilities added.")
    }

    // ---- Rooms ------------------------------------------------------------

    suspend fun fetchResources(
        search: String,
        buildingId: Long?,
        page: Int,
        perPage: Int = 20,
    ): Resource<HotelResourcePage> = guard {
        val params = buildMap {
            put("page", page.toString())
            put("per_page", perPage.toString())
            search.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
            buildingId?.let { put("building_id", it.toString()) }
        }
        when (val read = read(api.get("hotel-setup/resources", params), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> {
                val paginator = read.payload?.takeIf { it.isJsonObject }?.asJsonObject
                val rows = paginator?.get("data").rows().mapNotNull { it.toResourceRow() }
                Resource.Success(
                    HotelResourcePage(
                        rows = rows,
                        currentPage = paginator?.int("current_page") ?: page,
                        lastPage = paginator?.int("last_page") ?: 1,
                        total = paginator?.int("total") ?: rows.size,
                    )
                )
            }
        }
    }

    suspend fun fetchResource(id: Long): Resource<HotelResourceDetail> = guard {
        when (val read = read(api.get("hotel-setup/resources/edit/$id", emptyMap()), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> {
                val o = read.payload?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@guard Resource.Error("The room could not be read.")
                Resource.Success(
                    HotelResourceDetail(
                        id = o.long("id") ?: id,
                        resourceTypeId = o.long("resource_type_id"),
                        buildingId = o.long("building_id"),
                        floorId = o.long("floor_id"),
                        roomTypeId = o.long("room_type_id"),
                        code = o.text("code"),
                        name = o.text("name"),
                        displayName = o.text("display_name").ifBlank { o.text("code") },
                        saleMode = o.text("sale_mode").ifBlank { "whole" },
                        capacity = o.int("capacity") ?: 1,
                        rent = o.text("rent"),
                        status = o.int("status") ?: 1,
                        sortOrder = o.int("sort_order") ?: 0,
                        description = o.text("description"),
                        facilityIds = o.get("facility_ids").rows().mapNotNull { it.long("id") }
                            .ifEmpty {
                                o.get("facility_ids")?.takeIf { it.isJsonArray }?.asJsonArray
                                    ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong() }
                                    ?: o.get("facilities").rows().mapNotNull { it.long("id") }
                            },
                        seats = o.get("seats").rows().mapNotNull { it.toSeat() },
                    )
                )
            }
        }
    }

    /** Store or update one room; its beds are written with it, in one transaction. */
    suspend fun saveResource(id: Long?, draft: HotelRoomDraft): Resource<String> = guard {
        val path = if (id == null) "hotel-setup/resources/store" else "hotel-setup/resources/update/$id"
        postForMessage(path, draft.toJson(), "Saved successfully")
    }

    /**
     * A run of rooms — a whole floor at once. Same form, with the number swapped
     * for where the run starts and how long it is. A clash refuses the WHOLE run
     * and creates nothing, and the sentence names the numbers.
     */
    suspend fun bulkStoreResources(draft: HotelRoomDraft, startCode: String, count: Int): Resource<String> = guard {
        val body = draft.toJson().apply {
            remove("code")
            remove("name")
            addProperty("start_code", startCode.trim())
            addProperty("count", count)
        }
        postForMessage("hotel-setup/resources/bulk-store", body, "Rooms created.")
    }

    suspend fun deleteResource(id: Long): Resource<String> = guard {
        postForMessage("hotel-setup/resources/delete/$id", JsonObject(), "Deleted successfully")
    }

    suspend fun fetchSeats(roomId: Long): Resource<List<HotelSeatRow>> = guard {
        when (val read = read(api.get("hotel-setup/resources/$roomId/seats", emptyMap()), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> Resource.Success(read.payload.rows().mapNotNull { it.toSeat() })
        }
    }

    /**
     * One bed, on its own. This is where a particular bed's rent is set — the
     * room form's seat rent is only what a NEW bed starts at, and never
     * overwrites what is set here.
     */
    suspend fun updateSeat(seatId: Long, name: String, rent: String, status: Int): Resource<String> = guard {
        val body = JsonObject().apply {
            addProperty("name", name.trim().ifBlank { null })
            rent.trim().toDoubleOrNull()?.let { addProperty("rent", it) } ?: addProperty("rent", null as String?)
            addProperty("status", status)
        }
        postForMessage("hotel-setup/resources/seats/update/$seatId", body, "Seat updated successfully")
    }

    // ---- The drawing ------------------------------------------------------

    /** The whole property in one answer. An empty property is a success with no buildings. */
    suspend fun fetchLayout(branchId: Long?): Resource<HotelLayout> = guard {
        val params = buildMap { branchId?.let { put("branch_id", it.toString()) } }
        when (val read = read(api.get("hotel-setup/layout", params), SETUP_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> {
                val o = read.payload?.takeIf { it.isJsonObject }?.asJsonObject
                val times = o?.obj("times")?.let {
                    HotelTimes(
                        checkIn = it.text("check_in"),
                        checkOut = it.text("check_out"),
                        holdHours = it.int("hold_hours") ?: 0,
                        holdMaxHours = it.int("hold_max_hours") ?: 0,
                    )
                }
                Resource.Success(
                    HotelLayout(
                        buildings = o?.get("buildings").rows().mapNotNull { it.toBuilding() },
                        times = times,
                    )
                )
            }
        }
    }

    // ---- Service charge ---------------------------------------------------

    suspend fun fetchTaxRates(): Resource<HotelTaxRates> = guard {
        when (val read = read(api.get("hotel-setup/tax-rates", emptyMap()), CHARGE_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> {
                val o = read.payload?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@guard Resource.Error("The rates could not be read.")
                val current = o.obj("current")
                Resource.Success(
                    HotelTaxRates(
                        rows = o.get("rows").rows().mapNotNull { r ->
                            HotelTaxRateRow(
                                id = r.long("id") ?: return@mapNotNull null,
                                taxType = r.text("tax_type"),
                                rate = r.text("rate").toDoubleOrNull() ?: 0.0,
                                effectiveFrom = r.text("effective_from").take(10),
                                effectiveTo = r.text("effective_to").take(10),
                                notes = r.text("notes"),
                                isShipped = r.flag("is_shipped"),
                                inForce = r.flag("in_force"),
                            )
                        },
                        currentServiceCharge = current?.text("service_charge")?.toDoubleOrNull() ?: 0.0,
                        on = current?.text("on").orEmpty(),
                        note = o.text("note"),
                    )
                )
            }
        }
    }

    /** Writes a NEW row from the day given and closes the old one — never edits a figure in place. */
    suspend fun storeTaxRate(serviceChargeRate: String, effectiveFrom: String, notes: String): Resource<String> = guard {
        val body = JsonObject().apply {
            addProperty("service_charge_rate", serviceChargeRate.trim())
            addProperty("effective_from", effectiveFrom)
            notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
        }
        postForMessage("hotel-setup/tax-rates/store", body, "Saved")
    }

    suspend fun deleteTaxRate(id: Long): Resource<String> = guard {
        postForMessage("hotel-setup/tax-rates/delete/$id", JsonObject(), "Removed")
    }

    // ---- Halls ------------------------------------------------------------

    /**
     * What is free in the halls on ONE date, sitting by sitting. Advisory, like
     * every availability read: two clerks may both be told the evening is free.
     */
    suspend fun fetchHalls(branchId: Long?, date: String): Resource<HallAvailability> = guard {
        val params = buildMap {
            put("date", date)
            branchId?.let { put("branch_id", it.toString()) }
        }
        when (val read = read(api.get("hotel-setup/bookings/halls", params), BOOKING_DENIED)) {
            is Read.Failed -> read.error
            is Read.Ok -> {
                val o = read.payload?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@guard Resource.Error("The halls could not be read.")
                Resource.Success(
                    HallAvailability(
                        date = o.text("date").take(10),
                        slots = o.get("slots").rows().mapNotNull { s ->
                            HallSlot(
                                id = s.long("id") ?: return@mapNotNull null,
                                code = s.text("code"),
                                name = s.text("name"),
                                label = s.text("label").ifBlank { s.text("name") },
                            )
                        },
                        halls = o.get("halls").rows().mapNotNull { h ->
                            HallRow(
                                id = h.long("id") ?: return@mapNotNull null,
                                code = h.text("code"),
                                name = h.text("name"),
                                capacity = h.int("capacity") ?: 0,
                                rent = h.text("rent").toDoubleOrNull(),
                                building = h.text("building"),
                                date = h.text("date").take(10),
                                sittings = h.get("sittings").rows().mapNotNull { c ->
                                    HallSitting(
                                        slotId = c.long("slot_id") ?: return@mapNotNull null,
                                        slot = c.text("slot"),
                                        label = c.text("label"),
                                        state = c.text("state"),
                                        blockedReason = c.text("blocked_reason"),
                                        takenBy = c.text("taken_by"),
                                    )
                                },
                            )
                        },
                        freeCount = o.int("free_count") ?: 0,
                    )
                )
            }
        }
    }

    /**
     * Takes the sittings (`bookings/store`). Its own call rather than
     * [HotelRepository.storeBooking]: `sittings` travels as an array of
     * objects, and there are NO check-in/check-out dates — a hall-only booking
     * has no stay of nights, and the server reads its dates off the sittings.
     */
    suspend fun storeHallBooking(
        branchId: Long?,
        sittings: List<HallSittingPick>,
        bookerName: String,
        bookerMobile: String,
        notes: String,
    ): Resource<String> = guard {
        val body = JsonObject().apply {
            branchId?.let { addProperty("branch_id", it) }
            add(
                "sittings",
                JsonArray().apply {
                    sittings.forEach { s ->
                        add(
                            JsonObject().apply {
                                addProperty("resource_id", s.resourceId)
                                addProperty("date", s.date)
                                addProperty("slot_id", s.slotId)
                            }
                        )
                    }
                },
            )
            addProperty("booker_name", bookerName.trim())
            bookerMobile.trim().takeIf { it.isNotEmpty() }?.let { addProperty("booker_mobile", it) }
            notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
        }
        postForMessage("hotel-setup/bookings/store", body, "Booking saved.")
    }

    // ---- The envelope -----------------------------------------------------

    private sealed interface Read {
        data class Ok(val payload: JsonElement?, val message: String) : Read
        data class Failed(val error: Resource.Error) : Read
    }

    /**
     * foundData / notFound, unpicked. The payload sits at data.data (data when
     * single-wrapped); a refusal is HTTP 200 with success:false and a sentence
     * in `message`, which is what is shown.
     */
    private fun read(response: Response<JsonElement>, denied: String): Read {
        if (response.code() == 401) {
            return Read.Failed(Resource.Error(SESSION_EXPIRED, isUnauthorized = true))
        }
        if (response.code() == 403) return Read.Failed(Resource.Error(denied))
        val body = (
            response.body() ?: response.errorBody()?.let {
                runCatching { JsonParser.parseString(it.string()) }.getOrNull()
            }
            )?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Read.Failed(Resource.Error("Server error (${response.code()}). Please try again later."))
        val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = body.text("message")
        if (success == false) {
            return Read.Failed(Resource.Error(message.ifBlank { "The request was refused." }))
        }
        if (!response.isSuccessful && response.code() != 201) {
            return Read.Failed(
                Resource.Error(message.ifBlank { "Server error (${response.code()}). Please try again later." })
            )
        }
        val data = body.get("data")
        val payload = data?.takeIf { it.isJsonObject }?.asJsonObject
            ?.let { d -> if (d.has("data")) d.get("data") else d }
            ?: data
        return Read.Ok(payload, message)
    }

    private suspend fun postForMessage(path: String, body: JsonObject, fallback: String): Resource<String> =
        when (val read = read(api.postObjectRaw(path, body), "You do not have permission for that.")) {
            is Read.Failed -> read.error
            is Read.Ok -> Resource.Success(read.message.ifBlank { fallback })
        }

    private suspend fun <T> guard(block: suspend () -> Resource<T>): Resource<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    // ---- Parsing ----------------------------------------------------------

    private fun HotelRoomDraft.toJson(): JsonObject = JsonObject().apply {
        addProperty("resource_type_id", resourceTypeId)
        addProperty("building_id", buildingId)
        floorId?.let { addProperty("floor_id", it) }
        roomTypeId?.let { addProperty("room_type_id", it) }
        addProperty("code", code.trim())
        name.trim().takeIf { it.isNotEmpty() }?.let { addProperty("name", it) }
        addProperty("sale_mode", saleMode)
        addProperty("capacity", capacity)
        rent.trim().toDoubleOrNull()?.let { addProperty("rent", it) }
        seatCount?.let { addProperty("seat_count", it) }
        seatRent?.trim()?.toDoubleOrNull()?.let { addProperty("seat_rent", it) }
        description.trim().takeIf { it.isNotEmpty() }?.let { addProperty("description", it) }
        // Always sent: absent means "not talking about facilities" and keeps
        // whatever is stored; empty means "none of them" and clears the list.
        add("facility_ids", JsonArray().apply { facilityIds.forEach { add(it) } })
        addProperty("sort_order", sortOrder)
        addProperty("status", status)
    }

    private fun JsonObject.toResourceRow(): HotelResourceRow? {
        val id = long("id") ?: return null
        return HotelResourceRow(
            id = id,
            code = text("code"),
            name = text("name"),
            displayName = text("display_name").ifBlank { text("code") },
            kind = obj("type")?.text("code").orEmpty(),
            typeName = obj("type")?.text("name").orEmpty(),
            buildingName = obj("building")?.text("name").orEmpty(),
            floorName = obj("floor")?.text("name").orEmpty(),
            roomTypeName = obj("room_type")?.text("name").orEmpty(),
            saleMode = text("sale_mode"),
            capacity = int("capacity") ?: 0,
            rent = text("rent").toDoubleOrNull(),
            seatsCount = int("seats_count") ?: 0,
            activeSeatsCount = int("active_seats_count") ?: 0,
            status = int("status") ?: 1,
            facilities = get("facilities").rows().map { it.text("name") }.filter { it.isNotBlank() },
            description = text("description"),
        )
    }

    private fun JsonObject.toSeat(): HotelSeatRow? {
        val id = long("id") ?: return null
        return HotelSeatRow(
            id = id,
            code = text("code"),
            name = text("name"),
            rent = text("rent").toDoubleOrNull(),
            status = int("status") ?: 1,
        )
    }

    private fun JsonObject.toBuilding(): LayoutBuilding? {
        val id = long("id") ?: return null
        return LayoutBuilding(
            id = id,
            name = text("name"),
            code = text("code"),
            status = int("status") ?: 1,
            floors = get("floors").rows().mapNotNull { f ->
                HotelLayoutFloor(
                    id = f.long("id") ?: return@mapNotNull null,
                    name = f.text("name"),
                    floorNo = f.int("floor_no"),
                    status = f.int("status") ?: 1,
                    rooms = f.get("rooms").rows().mapNotNull { it.toLayoutRoom() },
                )
            },
            unfloored = get("unfloored").rows().mapNotNull { it.toLayoutRoom() },
            roomsCount = int("rooms_count") ?: 0,
            bedsCount = int("beds_count") ?: 0,
            hallsCount = int("halls_count") ?: 0,
            seatsCount = int("seats_count") ?: 0,
            rentMin = text("rent_min").toDoubleOrNull(),
            rentMax = text("rent_max").toDoubleOrNull(),
            seatRentMin = text("seat_rent_min").toDoubleOrNull(),
            seatRentMax = text("seat_rent_max").toDoubleOrNull(),
        )
    }

    private fun JsonObject.toLayoutRoom(): LayoutRoom? {
        val id = long("id") ?: return null
        return LayoutRoom(
            id = id,
            code = text("code"),
            name = text("name"),
            displayName = text("display_name").ifBlank { text("code") },
            kind = text("kind"),
            roomTypeId = long("room_type_id"),
            roomType = text("room_type"),
            roomTypeCode = text("room_type_code"),
            saleMode = text("sale_mode"),
            capacity = int("capacity") ?: 0,
            rent = text("rent").toDoubleOrNull(),
            status = int("status") ?: 1,
            description = text("description"),
            facilities = get("facilities")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty(),
            sittings = get("sittings")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.text("name") },
            beds = int("beds") ?: 0,
            activeBeds = int("active_beds") ?: 0,
            seatRentMin = text("seat_rent_min").toDoubleOrNull(),
            seatRentMax = text("seat_rent_max").toDoubleOrNull(),
        )
    }

    /** The objects of a JSON array — or nothing, for anything that is not one. */
    private fun JsonElement?.rows(): List<JsonObject> =
        this?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    /** A JSON boolean that may arrive as true/false, 1/0 or "1"/"0". */
    private fun JsonObject.flag(key: String): Boolean =
        text(key).let { it == "1" || it.equals("true", ignoreCase = true) }

    companion object {
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val SETUP_DENIED = "You do not have permission to set up rooms."
        private const val CHARGE_DENIED = "You do not have permission to set the service charge."
        private const val BOOKING_DENIED = "You do not have permission to see bookings."

        @Volatile
        private var instance: HotelSetupRepository? = null

        fun get(context: Context): HotelSetupRepository =
            instance ?: synchronized(this) {
                instance ?: HotelSetupRepository(
                    ServiceLocator.provideReportApiService(context.applicationContext),
                ).also { instance = it }
            }
    }
}
