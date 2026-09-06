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
//  What the money side of a booking reads as
// ---------------------------------------------------------------------------

/** The booking row as the folio, check-out and cancellation reads carry it. */
data class HotelFolioBooking(
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
    val billedToPartyId: Long?,
    val discountRate: Double?,
    val discountAmount: Double?,
    val discountReason: String,
    val notes: String,
) {
    val isWalkIn: Boolean get() = bookingType == "walk_in"
}

/** One line of the bill — a night, a meal, a ticket. */
data class HotelFolioLine(
    val id: Long,
    val lineNo: Int,
    val chargeType: String,
    val description: String,
    val resourceId: Long?,
    /** May arrive as a UTC instant; read as a calendar day where it is shown. */
    val stayDate: String,
    val quantity: Double,
    val unitRate: Double,
    val baseAmount: Double,
    val serviceChargeRate: Double,
    val serviceChargeAmount: Double,
    val vatRate: Double,
    val vatAmount: Double,
    val lineTotal: Double,
    /** The voucher NUMBER; blank means the line is not in the books yet. */
    val vrNo: String,
)

/** Money taken against (or given back on) the bill. */
data class HotelFolioPayment(
    val id: Long,
    val paymentNo: String,
    val paymentDate: String,
    /** advance / settlement / refund. */
    val purpose: String,
    /** cash / bank / card / mobile / adjustment. */
    val method: String,
    val amount: Double,
    val coa4Id: Long?,
    val reference: String,
    val notes: String,
    val vrNo: String,
) {
    val isRefund: Boolean get() = purpose == "refund"
}

/** One VAT band of the bill — several when the lines are taxed at different rates. */
data class HotelVatBand(val rate: Double, val base: Double, val vat: Double)

/** The bill's arithmetic, worked out by the server and never here. */
data class HotelFolioTotals(
    val base: Double,
    val discount: Double,
    val net: Double,
    val serviceCharge: Double,
    val vat: Double,
    val gross: Double,
    val rounded: Double,
    val rounding: Double,
    val discountRate: Double,
    val serviceChargeRate: Double,
    val vatBands: List<HotelVatBand>,
)

/** What may be put on the bill by hand — the COMPANY's list, not a constant. */
data class HotelChargeType(val code: String, val name: String, val defaultRate: Double?)

/** The whole folio, as `bookings/folio/{id}` answers and every write answers again. */
data class HotelFolio(
    val booking: HotelFolioBooking,
    val lines: List<HotelFolioLine>,
    val payments: List<HotelFolioPayment>,
    val totals: HotelFolioTotals,
    val paid: Double,
    /** rounded − paid; negative means the guest is in credit ("in hand"). */
    val balance: Double,
    val unbilledNights: Int,
    val postedToLedger: Boolean,
    val unpostedRows: Int,
    /** Ledger heads the chart lacks; non-empty means no money may move. */
    val chartMissing: List<String>,
    val canDiscount: Boolean,
    val chargeTypes: List<HotelChargeType>,
)

/** A write that answers with the folio again, and the sentence to show. */
data class HotelFolioWrite(val message: String, val folio: HotelFolio?)

/** One of the company's cash or bank heads, where money may be taken into. */
data class HotelTill(val id: Long, val name: String, val groupName: String, val side: String) {
    val label: String get() = if (groupName.isBlank()) name else "$name · $groupName"
}

/** One move of the bill from one pocket to another. */
data class HotelBillTransfer(
    val id: Long,
    val date: String,
    val from: String,
    val to: String,
    val amount: Double,
    val reason: String,
    val voucherNo: String,
)

/** Whose bill it is (`bookings/bill/{id}`). */
data class HotelBillOwnership(
    val owedById: Long?,
    /** Falls back to "the guest" on the server. */
    val owedByName: String,
    val carried: Boolean,
    val charged: Double,
    val paid: Double,
    val outstanding: Double,
    val note: String,
    val history: List<HotelBillTransfer>,
    val chartMissing: List<String>,
)

/**
 * A paper — the bill or a receipt — as JSON facts. Kept as loose maps: the
 * server names some sixty keys and the page draws whichever it has, so a typed
 * row here would be sixty nullable fields that change whenever the paper does.
 */
data class HotelPaper(
    val basic: Map<String, String>,
    val products: List<Map<String, String>>,
    val branchName: String,
    val branchAddress: String,
    val branchPhone: String,
) {
    fun fact(key: String): String = basic[key].orEmpty()
}

/** One room on the check-out plan. */
data class HotelCheckOutRoom(
    val roomResourceId: Long,
    val room: String,
    val firstNight: String,
    val lastNight: String,
    val nightsHeld: Int,
    val alreadyLeft: Boolean,
    val nightsToRelease: Int,
    val nightsToBill: Int,
    val chosen: Boolean,
    val pending: Boolean,
)

/** What check-out would do — read before, and answered again after. */
data class HotelCheckOutPlan(
    val booking: HotelFolioBooking,
    val departureDate: String,
    val bookedOutOn: String,
    val leavingEarly: Boolean,
    val nightsReleased: Int,
    val nightsToBill: Int,
    val billedAhead: Int,
    val rooms: List<HotelCheckOutRoom>,
    val closesBooking: Boolean,
    val roomsLeaving: Int,
    val roomsStaying: Int,
    /** PROJECTED — with the unbilled nights counted in. */
    val totals: HotelFolioTotals,
    val paid: Double,
    val balance: Double,
    val chartMissing: List<String>,
)

/** The check-out write: the sentence, and the plan as it stands afterwards. */
data class HotelCheckOutOutcome(val message: String, val plan: HotelCheckOutPlan?)

/** What cancelling would release and what money is on the booking. */
data class HotelCancellation(
    val booking: HotelFolioBooking,
    val nightsHeld: Int,
    val amountHeld: Double,
    val billedLines: Int,
    val chartMissing: List<String>,
    val tills: List<HotelTill>,
)

/** A hall sitting the booking holds — echoed back unchanged on an edit. */
data class HotelSitting(val resourceId: Long, val slotId: Long, val date: String, val hall: String, val sitting: String)

/** A room the booking holds, as the edit read folds the nights back. */
data class HotelHeldRoom(val roomId: Long, val displayName: String, val letAs: String, val beds: Int, val nights: Int)

/** The booking reopened for changing (`bookings/edit/{id}`). */
data class HotelBookingDetail(
    val booking: HotelFolioBooking,
    val statedAdults: Int,
    val statedChildren: Int,
    /** The three lists the form posts back — the server derives them, not us. */
    val roomIds: List<Long>,
    val seatIds: List<Long>,
    val sittings: List<HotelSitting>,
    val rooms: List<HotelHeldRoom>,
    val billedToPartyName: String,
)

/** What the form is asking for on an edit, in the server's own words. */
data class HotelBookingEdit(
    val roomIds: List<Long>,
    val seatIds: List<Long>,
    val sittings: List<HotelSitting>,
    val checkIn: String,
    val checkOut: String,
    val bookerName: String,
    val bookerMobile: String,
    val statedAdults: Int,
    val statedChildren: Int,
    val notes: String,
    val reason: String,
    /** hold / confirmed, or blank to leave the status alone. */
    val status: String,
)

/** The dry run's answer — what would change, and whether it is refused outright. */
data class HotelEditSummary(
    val message: String,
    val nightsAdding: Int,
    val nightsDropping: Int,
    val billedDropping: Int,
    val refused: Boolean,
    val roomsLeaving: List<String>,
    val checkInDate: String,
    val checkOutDate: String,
    val nights: Int,
)

/** The new booking's id, so the walk-in screen can open its bill. */
data class HotelWalkInResult(val message: String, val bookingId: Long?)

/**
 * The money side of a booking: the folio and its writes, the papers, check-out,
 * cancellation, the bill transfer and the edit after the fact.
 *
 * Apart from [HotelRepository] because that one is the front desk's reads —
 * who is coming and who is in — and this is the cashier's. Every endpoint here
 * either moves money or changes what is owed, and every refusal is a SENTENCE
 * the server wrote ("This bill still has 4,500.00 outstanding…"), so the one
 * rule throughout is: branch on the body's `success`, never on the HTTP
 * status, and show the sentence verbatim. A bare "Booking not found" answers
 * HTTP 201; a business refusal 422; a two-clerks collision 409 — all with
 * `success:false` and the words that matter.
 */
class HotelFolioRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ------------------------------------------------------------------
    //  The folio
    // ------------------------------------------------------------------

    /** The bill as it stands (`bookings/folio/{id}`, perm hotel.folio.view). */
    suspend fun fetchFolio(bookingId: Long): Resource<HotelFolio> = guarded {
        when (val a = answer(api.get("hotel-setup/bookings/folio/$bookingId", emptyMap()), SEE_BILL)) {
            is Answer.Refused -> a.error
            is Answer.Ok -> a.payload.asObject()?.toFolio()?.let { Resource.Success(it) }
                ?: Resource.Error("The bill could not be read.")
        }
    }

    /**
     * Put the nights on the bill (`folio/{id}/bill`). No body: every night the
     * booking holds that is not yet billed goes on, at that night's rates.
     * Safe to press twice — the server skips what is already there.
     */
    suspend fun billNights(bookingId: Long): Resource<HotelFolioWrite> = guarded {
        folioWrite(
            api.postObjectRaw("hotel-setup/bookings/folio/$bookingId/bill", JsonObject()),
            "Nights added to the bill",
        )
    }

    /** A charge by hand — a meal, laundry, a ticket. Room rent never goes on this way. */
    suspend fun addCharge(
        bookingId: Long,
        chargeType: String,
        description: String,
        quantity: Double,
        unitRate: Double,
        chargeDate: String?,
    ): Resource<HotelFolioWrite> = guarded {
        val body = JsonObject().apply {
            addProperty("charge_type", chargeType)
            addProperty("description", description.trim())
            addProperty("quantity", quantity)
            addProperty("unit_rate", unitRate)
            chargeDate?.takeIf { it.isNotBlank() }?.let { addProperty("charge_date", it) }
        }
        folioWrite(api.postObjectRaw("hotel-setup/bookings/folio/$bookingId/charge", body), "Added")
    }

    /**
     * An end-of-bill discount, as a percentage OR an amount. Both zero clears
     * it. The reason is required by the server — a discount with no reason is
     * a hole in the takings.
     */
    suspend fun giveDiscount(
        bookingId: Long,
        rate: Double?,
        amount: Double?,
        reason: String,
    ): Resource<HotelFolioWrite> = guarded {
        val body = JsonObject().apply {
            addProperty("discount_rate", rate ?: 0.0)
            addProperty("discount_amount", amount ?: 0.0)
            addProperty("reason", reason.trim())
        }
        folioWrite(api.postObjectRaw("hotel-setup/bookings/folio/$bookingId/discount", body), "Discount recorded")
    }

    /** Money in or out against the bill (`folio/{id}/receive`). */
    suspend fun receiveMoney(
        bookingId: Long,
        purpose: String,
        amount: Double,
        method: String,
        paymentDate: String?,
        coa4Id: Long,
        reference: String,
        notes: String,
    ): Resource<HotelFolioWrite> = guarded {
        val body = JsonObject().apply {
            addProperty("purpose", purpose)
            addProperty("amount", amount)
            addProperty("method", method)
            paymentDate?.takeIf { it.isNotBlank() }?.let { addProperty("payment_date", it) }
            addProperty("coa4_id", coa4Id)
            reference.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reference", it) }
            notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
        }
        folioWrite(api.postObjectRaw("hotel-setup/bookings/folio/$bookingId/receive", body), "Received")
    }

    /** The company's cash and bank heads (`folio/tills`, perm hotel.folio.bill). */
    suspend fun fetchTills(): Resource<List<HotelTill>> = guarded {
        val response = api.get("hotel-setup/bookings/folio/tills", emptyMap())
        when (val a = answer(response, "You do not have permission to take money.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(a.payload.asArray().toTills())
        }
    }

    // ------------------------------------------------------------------
    //  Whose bill it is
    // ------------------------------------------------------------------

    /** Who owes the outstanding balance, and how it got there (`bookings/bill/{id}`). */
    suspend fun fetchBillOwnership(bookingId: Long): Resource<HotelBillOwnership> = guarded {
        when (val a = answer(api.get("hotel-setup/bookings/bill/$bookingId", emptyMap()), SEE_BILL)) {
            is Answer.Refused -> a.error
            is Answer.Ok -> {
                val p = a.payload.asObject() ?: return@guarded Resource.Error("The bill could not be read.")
                val owedBy = p.obj("owed_by")
                Resource.Success(
                    HotelBillOwnership(
                        owedById = owedBy?.long("id"),
                        owedByName = p.text("owed_by_name")
                            .ifBlank { owedBy?.text("name").orEmpty() }
                            .ifBlank { "the guest" },
                        carried = p.flag("carried"),
                        charged = p.dbl("charged") ?: 0.0,
                        paid = p.dbl("paid") ?: 0.0,
                        outstanding = p.dbl("outstanding") ?: 0.0,
                        note = p.text("note"),
                        history = p.arr("history").mapNotNull { el ->
                            val o = el.asObject() ?: return@mapNotNull null
                            HotelBillTransfer(
                                id = o.long("id") ?: 0L,
                                date = o.text("date").take(10),
                                from = o.text("from"),
                                to = o.text("to"),
                                amount = o.dbl("amount") ?: 0.0,
                                reason = o.text("reason"),
                                voucherNo = o.text("voucher_no"),
                            )
                        },
                        chartMissing = p.strings("chart_missing"),
                    )
                )
            }
        }
    }

    /**
     * Move the outstanding balance to a party's account — or, with no party,
     * back to the guest (`bookings/bill/{id}/transfer`, perm hotel.booking.transfer).
     * Only what is still owed moves; money already taken stays where it was paid.
     */
    suspend fun transferBill(
        bookingId: Long,
        toPartyId: Long?,
        reason: String,
        transferDate: String?,
    ): Resource<String> = guarded {
        val body = JsonObject().apply {
            // Absent, not null-as-string: absent is how "back to the guest" is said.
            toPartyId?.let { addProperty("to_party_id", it) }
            reason.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
            transferDate?.takeIf { it.isNotBlank() }?.let { addProperty("transfer_date", it) }
        }
        val response = api.postObjectRaw("hotel-setup/bookings/bill/$bookingId/transfer", body)
        when (val a = answer(response, "You do not have permission to move a bill.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(a.message ?: "Bill moved")
        }
    }

    // ------------------------------------------------------------------
    //  Papers
    // ------------------------------------------------------------------

    /** The bill's facts for printing (`folio/{id}/bill-paper`). */
    suspend fun fetchBillPaper(bookingId: Long): Resource<HotelPaper> = guarded {
        paper(api.get("hotel-setup/bookings/folio/$bookingId/bill-paper", emptyMap()))
    }

    /** One receipt's facts (`folio/{id}/receipt/{paymentId}`). Carries no VAT, by design. */
    suspend fun fetchReceiptPaper(bookingId: Long, paymentId: Long): Resource<HotelPaper> = guarded {
        paper(api.get("hotel-setup/bookings/folio/$bookingId/receipt/$paymentId", emptyMap()))
    }

    private fun paper(response: Response<JsonElement>): Resource<HotelPaper> =
        when (val a = answer(response, SEE_BILL)) {
            is Answer.Refused -> a.error
            is Answer.Ok -> {
                val p = a.payload.asObject()
                if (p == null) {
                    Resource.Error("The paper could not be read.")
                } else {
                    val branch = p.obj("branch")
                    Resource.Success(
                        HotelPaper(
                            basic = p.obj("basic")?.toStringMap().orEmpty(),
                            products = p.arr("products").mapNotNull { it.asObject()?.toStringMap() },
                            branchName = branch?.text("name").orEmpty(),
                            branchAddress = branch?.text("address").orEmpty(),
                            branchPhone = branch?.text("phone").orEmpty(),
                        )
                    )
                }
            }
        }

    // ------------------------------------------------------------------
    //  Check-out
    // ------------------------------------------------------------------

    /**
     * What checking out would do (`bookings/checkout/{id}`). [resourceIds]
     * absent means every room; named, only those rooms leave and the rest
     * sleep on. Sent as `resource_ids[0]=…` because a query map cannot repeat
     * a key and PHP reads the indexed form as the same array.
     */
    suspend fun fetchCheckOutPlan(
        bookingId: Long,
        departureDate: String?,
        resourceIds: List<Long>?,
    ): Resource<HotelCheckOutPlan> = guarded {
        val params = buildMap {
            departureDate?.takeIf { it.isNotBlank() }?.let { put("departure_date", it) }
            resourceIds?.forEachIndexed { i, id -> put("resource_ids[$i]", id.toString()) }
        }
        val response = api.get("hotel-setup/bookings/checkout/$bookingId", params)
        when (val a = answer(response, "You do not have permission to check guests out.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> a.payload.asObject()?.toCheckOutPlan()?.let { Resource.Success(it) }
                ?: Resource.Error("The check-out could not be read.")
        }
    }

    /** Check the guests out (`POST bookings/checkout/{id}`, perm hotel.booking.checkout). */
    suspend fun checkOut(
        bookingId: Long,
        departureDate: String?,
        billedToPartyId: Long?,
        reason: String,
        resourceIds: List<Long>?,
    ): Resource<HotelCheckOutOutcome> = guarded {
        val body = JsonObject().apply {
            departureDate?.takeIf { it.isNotBlank() }?.let { addProperty("departure_date", it) }
            billedToPartyId?.let { addProperty("billed_to_party_id", it) }
            reason.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
            resourceIds?.let { ids -> add("resource_ids", JsonArray().apply { ids.forEach { add(it) } }) }
        }
        val response = api.postObjectRaw("hotel-setup/bookings/checkout/$bookingId", body)
        when (val a = answer(response, "You do not have permission to check guests out.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(
                HotelCheckOutOutcome(a.message ?: "Checked out", a.payload.asObject()?.toCheckOutPlan())
            )
        }
    }

    // ------------------------------------------------------------------
    //  Cancellation
    // ------------------------------------------------------------------

    /** What cancelling would release, and the money on the booking (`bookings/cancellation/{id}`). */
    suspend fun fetchCancellation(bookingId: Long): Resource<HotelCancellation> = guarded {
        val response = api.get("hotel-setup/bookings/cancellation/$bookingId", emptyMap())
        when (val a = answer(response, "You do not have permission to cancel a booking.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> {
                val p = a.payload.asObject() ?: return@guarded Resource.Error("The booking could not be read.")
                val booking = p.obj("booking")?.toFolioBooking()
                    ?: return@guarded Resource.Error("The booking could not be read.")
                Resource.Success(
                    HotelCancellation(
                        booking = booking,
                        nightsHeld = p.int("nights_held") ?: 0,
                        amountHeld = p.dbl("amount_held") ?: 0.0,
                        billedLines = p.int("billed_lines") ?: 0,
                        chartMissing = p.strings("chart_missing"),
                        tills = p.arr("tills").toTills(),
                    )
                )
            }
        }
    }

    /**
     * Cancel (`bookings/cancel/{id}`, perm hotel.booking.cancel). Whatever is
     * held is split: [refundAmount] goes back from [coa4Id], and the rest is
     * retained as cancellation income — neither half is optional.
     */
    suspend fun cancelBooking(
        bookingId: Long,
        reason: String,
        refundAmount: Double,
        coa4Id: Long?,
        cancelledOn: String?,
    ): Resource<String> = guarded {
        val body = JsonObject().apply {
            reason.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
            addProperty("refund_amount", refundAmount)
            if (refundAmount > 0) coa4Id?.let { addProperty("coa4_id", it) }
            cancelledOn?.takeIf { it.isNotBlank() }?.let { addProperty("cancelled_on", it) }
        }
        val response = api.postObjectRaw("hotel-setup/bookings/cancel/$bookingId", body)
        when (val a = answer(response, "You do not have permission to cancel a booking.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(a.message ?: "Booking cancelled")
        }
    }

    // ------------------------------------------------------------------
    //  Changing a booking after it is taken
    // ------------------------------------------------------------------

    /** The booking with its three pick lists (`bookings/edit/{id}`, perm hotel.booking.view). */
    suspend fun fetchBookingForEdit(bookingId: Long): Resource<HotelBookingDetail> = guarded {
        val response = api.get("hotel-setup/bookings/edit/$bookingId", emptyMap())
        when (val a = answer(response, "You do not have permission to see bookings.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> {
                val p = a.payload.asObject() ?: return@guarded Resource.Error("The booking could not be read.")
                val booking = p.toFolioBooking() ?: return@guarded Resource.Error("The booking could not be read.")
                Resource.Success(
                    HotelBookingDetail(
                        booking = booking,
                        statedAdults = p.int("stated_adults") ?: 0,
                        statedChildren = p.int("stated_children") ?: 0,
                        roomIds = p.arr("room_ids").mapNotNull { it.asLongOrNull() },
                        seatIds = p.arr("seat_ids").mapNotNull { it.asLongOrNull() },
                        sittings = p.arr("sittings").mapNotNull { el ->
                            val o = el.asObject() ?: return@mapNotNull null
                            HotelSitting(
                                resourceId = o.long("resource_id") ?: return@mapNotNull null,
                                slotId = o.long("slot_id") ?: return@mapNotNull null,
                                date = o.text("date").take(10),
                                hall = o.text("hall"),
                                sitting = o.text("sitting"),
                            )
                        },
                        rooms = p.arr("rooms").mapNotNull { el ->
                            val o = el.asObject() ?: return@mapNotNull null
                            HotelHeldRoom(
                                roomId = o.long("room_id") ?: return@mapNotNull null,
                                displayName = o.text("display_name"),
                                letAs = o.text("let_as"),
                                beds = o.int("beds") ?: 0,
                                nights = o.arr("nights").size(),
                            )
                        },
                        billedToPartyName = p.text("billed_to_party_name"),
                    )
                )
            }
        }
    }

    /**
     * The edit as a diff (`bookings/update/{id}`). With [dryRun] the server
     * answers what WOULD change and writes nothing — the figures the clerk is
     * shown before confirming. The three lists are the whole wanted shape:
     * what is not in them is dropped, which is why the sittings are echoed
     * back untouched.
     */
    suspend fun updateBooking(
        bookingId: Long,
        edit: HotelBookingEdit,
        dryRun: Boolean,
    ): Resource<HotelEditSummary> = guarded {
        val body = JsonObject().apply {
            add("room_ids", JsonArray().apply { edit.roomIds.forEach { add(it) } })
            add("seat_ids", JsonArray().apply { edit.seatIds.forEach { add(it) } })
            add(
                "sittings",
                JsonArray().apply {
                    edit.sittings.forEach { s ->
                        add(
                            JsonObject().apply {
                                addProperty("resource_id", s.resourceId)
                                addProperty("slot_id", s.slotId)
                                addProperty("date", s.date)
                            }
                        )
                    }
                },
            )
            edit.checkIn.takeIf { it.isNotBlank() }?.let { addProperty("check_in_date", it) }
            edit.checkOut.takeIf { it.isNotBlank() }?.let { addProperty("check_out_date", it) }
            edit.bookerName.trim().takeIf { it.isNotEmpty() }?.let { addProperty("booker_name", it) }
            edit.bookerMobile.trim().takeIf { it.isNotEmpty() }?.let { addProperty("booker_mobile", it) }
            addProperty("stated_adults", edit.statedAdults)
            addProperty("stated_children", edit.statedChildren)
            edit.notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
            edit.reason.trim().takeIf { it.isNotEmpty() }?.let { addProperty("reason", it) }
            edit.status.takeIf { it.isNotBlank() }?.let { addProperty("status", it) }
            addProperty("dry_run", dryRun)
        }
        val response = api.postObjectRaw("hotel-setup/bookings/update/$bookingId", body)
        when (val a = answer(response, "You do not have permission to change a booking.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> {
                val p = a.payload.asObject()
                Resource.Success(
                    HotelEditSummary(
                        message = a.message ?: if (dryRun) "This is what would change" else "Booking updated",
                        nightsAdding = p?.int("nights_adding") ?: 0,
                        nightsDropping = p?.int("nights_dropping") ?: 0,
                        billedDropping = p?.int("billed_dropping") ?: 0,
                        refused = p?.flag("refused") ?: false,
                        roomsLeaving = p?.strings("rooms_leaving").orEmpty(),
                        checkInDate = p?.text("check_in_date").orEmpty().take(10),
                        checkOutDate = p?.text("check_out_date").orEmpty().take(10),
                        nights = p?.int("nights") ?: 0,
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    //  Walk-in sale
    // ------------------------------------------------------------------

    /**
     * A sale to somebody who is not staying (`bookings/store` with
     * booking_type walk_in). No room, bed or hall travels — the server refuses
     * one — and the status is forced confirmed; what was sold goes on as
     * charges on the folio this answers the id of.
     */
    suspend fun storeWalkIn(
        bookerName: String,
        bookerMobile: String,
        statedAdults: Int?,
        statedChildren: Int?,
        notes: String,
    ): Resource<HotelWalkInResult> = guarded {
        val body = JsonObject().apply {
            addProperty("booking_type", "walk_in")
            addProperty("booker_name", bookerName.trim())
            bookerMobile.trim().takeIf { it.isNotEmpty() }?.let { addProperty("booker_mobile", it) }
            statedAdults?.let { addProperty("stated_adults", it) }
            statedChildren?.let { addProperty("stated_children", it) }
            notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) }
        }
        val response = api.postObjectRaw("hotel-setup/bookings/store", body)
        when (val a = answer(response, "You do not have permission to take a booking.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(
                HotelWalkInResult(a.message ?: "Walk-in recorded", a.payload.asObject()?.long("id"))
            )
        }
    }

    // ------------------------------------------------------------------
    //  Envelope
    // ------------------------------------------------------------------

    private sealed interface Answer {
        data class Ok(val message: String?, val payload: JsonElement?) : Answer
        data class Refused(val error: Resource.Error) : Answer
    }

    /**
     * One reading of every hotel answer. foundData wraps twice — the payload is
     * at data.data — and a refusal is `success:false` with the sentence in
     * `message`, whatever the HTTP status says. Laravel's own validator answers
     * without a `success` key at all, so its first complaint is lifted out of
     * `errors` rather than shown as "The given data was invalid".
     */
    private fun answer(response: Response<JsonElement>, permissionMessage: String): Answer {
        if (response.code() == 401) {
            return Answer.Refused(Resource.Error(SESSION_EXPIRED, isUnauthorized = true))
        }
        if (response.code() == 403) {
            return Answer.Refused(Resource.Error(permissionMessage))
        }
        val body = (
            response.body() ?: response.errorBody()?.let {
                runCatching { JsonParser.parseString(it.string()) }.getOrNull()
            }
            )?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Answer.Refused(Resource.Error("Server error (${response.code()}). Please try again later."))
        val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = body.text("message").ifBlank { null }
        val firstValidationError = body.obj("errors")?.entrySet()?.firstOrNull()?.value
            ?.let { v -> if (v.isJsonArray) v.asJsonArray.firstOrNull()?.asStringOrNull() else v.asStringOrNull() }
        return when {
            success == false -> Answer.Refused(Resource.Error(message ?: "The request was refused."))
            success == null && firstValidationError != null -> Answer.Refused(Resource.Error(firstValidationError))
            success == null && !response.isSuccessful ->
                Answer.Refused(Resource.Error(message ?: "Server error (${response.code()}). Please try again later."))
            else -> {
                val inner = body.obj("data")
                Answer.Ok(message, inner?.get("data") ?: inner ?: body.get("data"))
            }
        }
    }

    private fun folioWrite(response: Response<JsonElement>, fallback: String): Resource<HotelFolioWrite> =
        when (val a = answer(response, "You do not have permission to bill.")) {
            is Answer.Refused -> a.error
            is Answer.Ok -> Resource.Success(HotelFolioWrite(a.message ?: fallback, a.payload.asObject()?.toFolio()))
        }

    private suspend fun <T> guarded(block: suspend () -> Resource<T>): Resource<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    // ------------------------------------------------------------------
    //  Parsing
    // ------------------------------------------------------------------

    private fun JsonObject.toFolio(): HotelFolio? {
        val booking = obj("booking")?.toFolioBooking() ?: return null
        return HotelFolio(
            booking = booking,
            lines = arr("lines").mapNotNull { it.asObject()?.toLine() },
            payments = arr("payments").mapNotNull { it.asObject()?.toPayment() },
            totals = obj("totals").toTotals(),
            paid = dbl("paid") ?: 0.0,
            balance = dbl("balance") ?: 0.0,
            unbilledNights = int("unbilled_nights") ?: 0,
            postedToLedger = flag("posted_to_ledger"),
            unpostedRows = int("unposted_rows") ?: 0,
            chartMissing = strings("chart_missing"),
            // Absent reads as allowed: only an explicit false hides the button.
            canDiscount = get("can_discount")?.takeUnless { it.isJsonNull }?.let { flag("can_discount") } ?: true,
            chargeTypes = arr("charge_types").mapNotNull { el ->
                val o = el.asObject() ?: return@mapNotNull null
                HotelChargeType(
                    code = o.text("id").ifBlank { o.text("code") },
                    name = o.text("name"),
                    defaultRate = o.dbl("default_rate"),
                )
            },
        )
    }

    private fun JsonObject.toFolioBooking(): HotelFolioBooking? {
        val id = long("id") ?: return null
        return HotelFolioBooking(
            id = id,
            bookingNo = text("booking_no"),
            status = text("status"),
            bookingType = text("booking_type"),
            bookerName = text("booker_name"),
            bookerMobile = text("booker_mobile"),
            checkInDate = text("check_in_date").take(10),
            checkOutDate = text("check_out_date").take(10),
            nights = int("nights_count") ?: int("nights") ?: 0,
            billedToPartyId = long("billed_to_party_id"),
            discountRate = dbl("discount_rate"),
            discountAmount = dbl("discount_amount"),
            discountReason = text("discount_reason"),
            notes = text("notes"),
        )
    }

    private fun JsonObject.toLine(): HotelFolioLine? {
        val id = long("id") ?: return null
        return HotelFolioLine(
            id = id,
            lineNo = int("line_no") ?: 0,
            chargeType = text("charge_type"),
            description = text("description"),
            resourceId = long("resource_id"),
            stayDate = text("stay_date"),
            quantity = dbl("quantity") ?: 0.0,
            unitRate = dbl("unit_rate") ?: 0.0,
            baseAmount = dbl("base_amount") ?: 0.0,
            serviceChargeRate = dbl("service_charge_rate") ?: 0.0,
            serviceChargeAmount = dbl("service_charge_amount") ?: 0.0,
            vatRate = dbl("vat_rate") ?: 0.0,
            vatAmount = dbl("vat_amount") ?: 0.0,
            lineTotal = dbl("line_total") ?: 0.0,
            vrNo = text("vr_no"),
        )
    }

    private fun JsonObject.toPayment(): HotelFolioPayment? {
        val id = long("id") ?: return null
        return HotelFolioPayment(
            id = id,
            paymentNo = text("payment_no"),
            paymentDate = text("payment_date").take(10),
            purpose = text("purpose"),
            method = text("method"),
            amount = dbl("amount") ?: 0.0,
            coa4Id = long("coa4_id"),
            reference = text("reference"),
            notes = text("notes"),
            vrNo = text("vr_no"),
        )
    }

    private fun JsonObject?.toTotals(): HotelFolioTotals = HotelFolioTotals(
        base = this?.dbl("base") ?: 0.0,
        discount = this?.dbl("discount") ?: 0.0,
        net = this?.dbl("net") ?: 0.0,
        serviceCharge = this?.dbl("service_charge") ?: 0.0,
        vat = this?.dbl("vat") ?: 0.0,
        gross = this?.dbl("gross") ?: 0.0,
        rounded = this?.dbl("rounded") ?: 0.0,
        rounding = this?.dbl("rounding") ?: 0.0,
        discountRate = this?.dbl("discount_rate") ?: 0.0,
        serviceChargeRate = this?.dbl("service_charge_rate") ?: 0.0,
        vatBands = this?.arr("vat_bands")?.mapNotNull { el ->
            val o = el.asObject() ?: return@mapNotNull null
            HotelVatBand(o.dbl("rate") ?: 0.0, o.dbl("base") ?: 0.0, o.dbl("vat") ?: 0.0)
        }.orEmpty(),
    )

    private fun JsonObject.toCheckOutPlan(): HotelCheckOutPlan? {
        val booking = obj("booking")?.toFolioBooking() ?: return null
        return HotelCheckOutPlan(
            booking = booking,
            departureDate = text("departure_date").take(10),
            bookedOutOn = text("booked_out_on").take(10),
            leavingEarly = flag("leaving_early"),
            nightsReleased = int("nights_released") ?: 0,
            nightsToBill = int("nights_to_bill") ?: 0,
            billedAhead = int("billed_ahead") ?: 0,
            rooms = arr("rooms").mapNotNull { el ->
                val o = el.asObject() ?: return@mapNotNull null
                HotelCheckOutRoom(
                    roomResourceId = o.long("room_resource_id") ?: return@mapNotNull null,
                    room = o.text("room"),
                    firstNight = o.text("first_night").take(10),
                    lastNight = o.text("last_night").take(10),
                    nightsHeld = o.int("nights_held") ?: 0,
                    alreadyLeft = o.flag("already_left"),
                    nightsToRelease = o.int("nights_to_release") ?: 0,
                    nightsToBill = o.int("nights_to_bill") ?: 0,
                    chosen = o.flag("chosen"),
                    pending = o.flag("pending"),
                )
            },
            closesBooking = flag("closes_booking"),
            roomsLeaving = int("rooms_leaving") ?: 0,
            roomsStaying = int("rooms_staying") ?: 0,
            totals = obj("totals").toTotals(),
            paid = dbl("paid") ?: 0.0,
            balance = dbl("balance") ?: 0.0,
            chartMissing = strings("chart_missing"),
        )
    }

    private fun JsonArray.toTills(): List<HotelTill> = mapNotNull { el ->
        val o = el.asObject() ?: return@mapNotNull null
        HotelTill(
            id = o.long("id") ?: return@mapNotNull null,
            name = o.text("name"),
            groupName = o.text("group_name"),
            side = o.text("side"),
        )
    }

    /** Every scalar of an object as text, nulls as blank — what a paper is drawn from. */
    private fun JsonObject.toStringMap(): Map<String, String> = buildMap {
        this@toStringMap.entrySet().forEach { (k, v) ->
            when {
                v == null || v.isJsonNull -> put(k, "")
                v.isJsonPrimitive -> put(k, v.asString)
                v.isJsonArray -> put(k, v.asJsonArray.mapNotNull { it.asStringOrNull() }.joinToString(", "))
                else -> Unit
            }
        }
    }

    private fun JsonElement?.asObject(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonElement?.asArray(): JsonArray = this?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonElement?.asStringOrNull(): String? =
        this?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonElement?.asLongOrNull(): Long? = asStringOrNull()?.toDoubleOrNull()?.toLong()

    private fun JsonObject.obj(key: String): JsonObject? = get(key).asObject()
    private fun JsonObject.arr(key: String): JsonArray = get(key).asArray()
    private fun JsonObject.text(key: String): String = get(key).asStringOrNull().orEmpty()
    private fun JsonObject.dbl(key: String): Double? = get(key).asStringOrNull()?.toDoubleOrNull()
    private fun JsonObject.long(key: String): Long? = dbl(key)?.toLong()
    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
    private fun JsonObject.flag(key: String): Boolean =
        text(key).let { it == "1" || it.equals("true", ignoreCase = true) }
    private fun JsonObject.strings(key: String): List<String> = arr(key).mapNotNull { it.asStringOrNull() }

    companion object {
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val SEE_BILL = "You do not have permission to see the bill."

        @Volatile
        private var instance: HotelFolioRepository? = null

        /**
         * One per process, like the ServiceLocator's own singletons — built here
         * rather than there so the locator (another engineer's file this week)
         * is not edited for it.
         */
        fun get(context: Context): HotelFolioRepository =
            instance ?: synchronized(this) {
                instance ?: HotelFolioRepository(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                ).also { instance = it }
            }
    }
}
