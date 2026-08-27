package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonObject
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
    ): Resource<HotelBookingPage> = withContext(ioDispatcher) {
        try {
            val params = buildMap {
                put("page", page.toString())
                put("per_page", perPage.toString())
                status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
                search.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
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
