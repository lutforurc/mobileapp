package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.math.max

// ---------------------------------------------------------------------------
// Models — Sold Units report
// ---------------------------------------------------------------------------

/** One priced line inside a sold unit (the unit itself, a parking slot, …). */
data class SoldUnitLine(
    val type: String,
    val caption: String,
    val place: String,
    val amount: Double,
)

/** One unit sale under a customer (`units[]` of a sold-units customer row). */
data class SoldUnitSale(
    val saleId: String,
    val saleDate: String,
    /** How many allotment-letter snapshots this sale has (the web's L-1…L-n). */
    val letterCount: Int,
    /**
     * The letter numbers that actually exist. After a withdrawal a sale can
     * carry L-1 and L-3 — counting 1..n would offer a letter that is gone and
     * hide one that is not. Falls back to 1..[letterCount] for a server that
     * does not send the list yet.
     */
    val letterVersions: List<Int>,
    /**
     * The booking-form numbers that exist (the web's B-1…B-n) — its own series,
     * because a sale can be on its third form and its first letter. Same
     * hole-tolerant rule as [letterVersions].
     */
    val bookingFormVersions: List<Int>,
    /** How many people this particular property is left to. */
    val nomineeCount: Int,
    val unitPriceAmount: Double,
    val parkingAmount: Double,
    val buildingName: String,
    val floorName: String,
    val receiptNo: String,
    val totalAmount: Double,
    val bookingAmount: Double,
    val receivedAmount: Double,
    val dueAmount: Double,
    val lines: List<SoldUnitLine>,
)

/** One customer group of the Sold Units report. */
data class SoldUnitCustomer(
    val customerId: String,
    val customerName: String,
    val customerMobile: String,
    val customerAddress: String,
    val unitCount: Int,
    val parkingCount: Int,
    val unitPriceAmount: Double,
    val parkingAmount: Double,
    val totalAmount: Double,
    val receivedAmount: Double,
    val dueAmount: Double,
    val units: List<SoldUnitSale>,
)

/** The report's `totals` block. */
data class SoldUnitsTotals(
    val customerCount: Int,
    val unitCount: Int,
    val parkingCount: Int,
    val unitPriceAmount: Double,
    val parkingAmount: Double,
    val totalAmount: Double,
    val receivedAmount: Double,
    val dueAmount: Double,
)

/** The full Sold Units payload: grouped customer rows plus the grand totals. */
data class SoldUnitsReport(
    val customers: List<SoldUnitCustomer>,
    val totals: SoldUnitsTotals,
)

/** One person on the buyer's nominee list, as the customer screen saved them. */
data class SaleNomineeOption(
    val id: Long,
    val name: String,
    val relation: String,
    val nationalId: String,
    /** The buyer's own default share ("" when none) — a text-field value. */
    val defaultShare: String,
)

/** What one sale says about one nominee: picked, with what share and order. */
data class SaleNomineePick(
    val nomineeId: Long,
    /** Share % as typed ("" = division left to law). */
    val share: String,
    /** Priority order as typed ("" = the order they appear in). */
    val priority: String,
)

/** The nominee dialog's load: everyone available, and who is already named. */
data class SaleNominees(
    val available: List<SaleNomineeOption>,
    val attached: List<SaleNomineePick>,
)

// ---------------------------------------------------------------------------
// Models — Sales Summary report
// ---------------------------------------------------------------------------

/** One sale under a Sales Summary buyer: where the flat is, and which one. */
data class SalesSummaryUnit(
    val saleId: String,
    val areaName: String,
    val projectName: String,
    val buildingName: String,
    val floorName: String,
    /** Printed as stored — it already reads "Unit# 4/A". */
    val unitNo: String,
    val parkingNo: String,
)

/** One line of the report: a buyer, what they bought, and what they owe. */
data class SalesSummaryCustomer(
    val customerId: String,
    val customerName: String,
    val customerMobile: String,
    val units: List<SalesSummaryUnit>,
    val totalAmount: Double,
    val receivedAmount: Double,
    val dueAmount: Double,
)

/** The report's `totals` block. */
data class SalesSummaryTotals(
    val customerCount: Int,
    val totalAmount: Double,
    val receivedAmount: Double,
    val dueAmount: Double,
)

/** The full Sales Summary payload: one row per buyer plus the grand totals. */
data class SalesSummaryReport(
    val customers: List<SalesSummaryCustomer>,
    val totals: SalesSummaryTotals,
)

/** One receipt of the buyer-wise payment ledger. */
data class CustomerPaymentRow(
    val paymentDate: String,
    val receiptNo: String,
    val paymentMode: String,
    val referenceNo: String,
    val paymentType: String,
    val status: String,
    val amount: Double,
)

// ---------------------------------------------------------------------------
// Models — Installment Create
// ---------------------------------------------------------------------------

/** One entry of the unit-sale picker (`real-estate/unit-sale/ddl`). */
data class SaleOption(
    val id: Long,
    val label: String,
    val customerId: Long,
    val customerName: String,
    val customerMobile: String,
    val saleDate: String,
    val totalAmount: Double,
    val bookingAmount: Double,
    val downpaymentAmount: Double,
    val dueAmount: Double,
)

/** The picked sale's summary (`real-estate/unit-sale/summary/{id}`). */
data class SaleSummary(
    val id: Long,
    val receiptNo: String,
    val unitLabel: String,
    val parkingLabel: String,
    val customerId: Long,
    val customerName: String,
    val customerMobile: String,
    val totalAmount: Double,
    val bookingAmount: Double,
    val downpaymentAmount: Double,
    val confirmedBookingAmount: Double,
    val confirmedDownpaymentAmount: Double,
    val confirmedReceived: Double,
    val dueAmount: Double,
    val saleDate: String,
    val status: String,
) {
    /** Booking money: the larger of the entered and the confirmed figure. */
    val effectiveBookingAmount: Double get() = max(bookingAmount, confirmedBookingAmount)

    /** Down payment: the larger of the entered and the confirmed figure. */
    val effectiveDownPayment: Double get() = max(downpaymentAmount, confirmedDownpaymentAmount)

    /** What the schedule must cover: total − booking − down payment, floored at 0. */
    val scheduleBase: Double get() =
        max(totalAmount - effectiveBookingAmount - effectiveDownPayment, 0.0)
}

/** Result of a successful `installment-create`. */
data class ScheduleCreateResult(
    val scheduleTotal: Double,
    val installmentCount: Int,
    val message: String,
)

/** One saved installment row (`accounts/installment/details/{customerId}?report=true`). */
data class SavedInstallmentRow(
    /** Hashed string id — sent back verbatim on update. */
    val installmentId: String,
    val installmentNo: Int,
    /** As delivered: dd/MM/yyyy. */
    val dueDate: String,
    val amount: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val status: String,
    val earlyPaymentDiscount: Double,
    val earlyPaymentDate: String?,
    val invoiceNo: String,
)

/** The extra fields an installment-update carries for installment no. 1 only. */
data class FirstRowEarlyPayload(
    /** yyyy-MM-dd, or null to clear. */
    val earlyPaymentDate: String?,
    val earlyDiscount: Double,
)

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

/**
 * Backs the Real Estate sales screens: the Sold Units report and the
 * Installment Create flow. Read endpoints go through the generic
 * [ReportApiService]; the two writes post a typed [JsonObject] via
 * [TransactionApiService] so numbers/booleans/nulls keep their JSON types.
 *
 * Every endpoint answers with the Laravel envelope — the outcome is the JSON
 * `success` flag, not the HTTP status (`notFound()` arrives as `success:false`
 * at HTTP 201).
 */
class RealEstateSalesRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
        private const val GENERIC_ERROR = "Something went wrong. Please try again."
    }

    // ---- Dropdowns --------------------------------------------------------

    /** Project options for the Sold Units filter (`real-estate/projects/ddl`). */
    suspend fun fetchProjects(): Resource<List<SelectorOption>> =
        fetchDdl("real-estate/projects/ddl")

    /** Building options for the Sold Units filter (`real-estate/buildings/ddl`). */
    suspend fun fetchBuildings(): Resource<List<SelectorOption>> =
        fetchDdl("real-estate/buildings/ddl")

    /** Location options — the project areas (`real-estate/project-areas/ddl`). */
    suspend fun fetchAreas(): Resource<List<SelectorOption>> =
        fetchDdl("real-estate/project-areas/ddl")

    /** `{value,label}` options at `data.data`. */
    private suspend fun fetchDdl(path: String): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        guarded {
            val response = reportApi.get(path, emptyMap())
            envelope(response, onNotFound = { Resource.Success(emptyList()) }) { json ->
                val options = json.obj("data")?.arr("data")
                    ?.mapNotNull { it.objOrNull() }
                    ?.mapNotNull { o ->
                        val value = o.str("value") ?: return@mapNotNull null
                        SelectorOption(id = value, label = o.str("label") ?: value)
                    }
                    .orEmpty()
                Resource.Success(options)
            }
        }
    }

    // ---- Sold Units -------------------------------------------------------

    /**
     * `GET real-estate/unit-sale/sold-units` — blank filters are omitted;
     * `due_only=true` is sent only when the checkbox is on.
     */
    suspend fun fetchSoldUnits(
        projectId: String,
        buildingId: String,
        dateFrom: String?,
        dateTo: String?,
        query: String,
        dueOnly: Boolean,
    ): Resource<SoldUnitsReport> = withContext(ioDispatcher) {
        val params = buildMap {
            if (projectId.isNotBlank()) put("project_id", projectId)
            if (buildingId.isNotBlank()) put("building_id", buildingId)
            if (!dateFrom.isNullOrBlank()) put("date_from", dateFrom)
            if (!dateTo.isNullOrBlank()) put("date_to", dateTo)
            if (query.isNotBlank()) put("q", query.trim())
            if (dueOnly) put("due_only", "true")
        }
        guarded {
            val response = reportApi.get("real-estate/unit-sale/sold-units", params)
            envelope(response, onNotFound = { Resource.Success(emptySoldUnitsReport()) }) { json ->
                val payload = json.obj("data")?.obj("data")
                val customers = payload?.arr("data")
                    ?.mapNotNull { it.objOrNull()?.toSoldUnitCustomer() }
                    .orEmpty()
                val totals = payload?.obj("totals").toSoldUnitsTotals()
                Resource.Success(SoldUnitsReport(customers = customers, totals = totals))
            }
        }
    }

    private fun emptySoldUnitsReport() =
        SoldUnitsReport(customers = emptyList(), totals = JsonObject().toSoldUnitsTotals())

    // ---- Sales Summary ----------------------------------------------------

    /**
     * `GET real-estate/reports/sales-summary` — the report's own endpoint, not
     * the sold-units list's, because the permission is separate: it answers
     * only to `real.estate.sales.summary`, so a role that may enter a sale but
     * not read the whole sales book gets a 403 here while its own screens keep
     * working. Amounts follow the module's one rule (CONFIRMED payments only,
     * a CONFIRMED REFUND taken off), so a total here and a total on the
     * sold-units list still agree.
     */
    suspend fun fetchSalesSummary(
        areaId: String,
        projectId: String,
        buildingId: String,
    ): Resource<SalesSummaryReport> = withContext(ioDispatcher) {
        val params = buildMap {
            if (areaId.isNotBlank()) put("area_id", areaId)
            if (projectId.isNotBlank()) put("project_id", projectId)
            if (buildingId.isNotBlank()) put("building_id", buildingId)
        }
        guarded {
            val response = reportApi.get("real-estate/reports/sales-summary", params)
            envelope(response, onNotFound = { Resource.Success(emptySalesSummaryReport()) }) { json ->
                val payload = json.obj("data")?.obj("data")
                val customers = payload?.arr("data")
                    ?.mapNotNull { it.objOrNull()?.toSalesSummaryCustomer() }
                    .orEmpty()
                val totals = payload?.obj("totals")
                Resource.Success(
                    SalesSummaryReport(
                        customers = customers,
                        totals = SalesSummaryTotals(
                            customerCount = totals?.int("customer_count") ?: 0,
                            totalAmount = totals?.dbl("total_amount") ?: 0.0,
                            receivedAmount = totals?.dbl("received_amount") ?: 0.0,
                            dueAmount = totals?.dbl("due_amount") ?: 0.0,
                        ),
                    )
                )
            }
        }
    }

    private fun emptySalesSummaryReport() = SalesSummaryReport(
        customers = emptyList(),
        totals = SalesSummaryTotals(0, 0.0, 0.0, 0.0),
    )

    private fun JsonObject.toSalesSummaryCustomer(): SalesSummaryCustomer? {
        val name = str("customer_name") ?: return null
        return SalesSummaryCustomer(
            customerId = str("customer_id").orEmpty(),
            customerName = name,
            customerMobile = str("customer_mobile").orEmpty(),
            units = arr("units")?.mapNotNull { el ->
                el.objOrNull()?.let { o ->
                    SalesSummaryUnit(
                        saleId = o.str("sale_id").orEmpty(),
                        areaName = o.str("area_name").orEmpty(),
                        projectName = o.str("project_name").orEmpty(),
                        buildingName = o.str("building_name").orEmpty(),
                        floorName = o.str("floor_name").orEmpty(),
                        unitNo = o.str("unit_no").orEmpty(),
                        parkingNo = o.str("parking_no").orEmpty(),
                    )
                }
            }.orEmpty(),
            totalAmount = dbl("total_amount"),
            receivedAmount = dbl("received_amount"),
            dueAmount = dbl("due_amount"),
        )
    }

    /**
     * The buyer's money, receipt by receipt — the web's clipboard action, which
     * hands the payments register the buyer's account id rather than a name to
     * search: it gathers every payment against them even where two flats put
     * them on two bookings, and it cannot drag in a second customer whose name
     * reads the same. `GET real-estate/unit-sale/payments-list?customer_id=` —
     * SINGLE-wrapped (`data` is the paginator itself), unlike the rest of the
     * module; the camelCase `perPage` is what this endpoint reads.
     */
    suspend fun fetchCustomerPayments(customerId: String): Resource<List<CustomerPaymentRow>> =
        withContext(ioDispatcher) {
            val params = mapOf("customer_id" to customerId, "perPage" to "200")
            guarded {
                val response = reportApi.get("real-estate/unit-sale/payments-list", params)
                envelope(response, onNotFound = { Resource.Success(emptyList()) }) { json ->
                    val rows = json.obj("data")?.arr("data")
                        ?.mapNotNull { el ->
                            el.objOrNull()?.let { o ->
                                CustomerPaymentRow(
                                    paymentDate = o.str("payment_date").orEmpty(),
                                    receiptNo = o.str("receipt_no").orEmpty(),
                                    paymentMode = o.str("payment_mode").orEmpty(),
                                    referenceNo = o.str("reference_no").orEmpty(),
                                    paymentType = o.str("payment_type").orEmpty(),
                                    status = o.str("status").orEmpty(),
                                    amount = o.dbl("amount"),
                                )
                            }
                        }
                        .orEmpty()
                    Resource.Success(rows)
                }
            }
        }

    private fun JsonObject.toSoldUnitCustomer(): SoldUnitCustomer? {
        val name = str("customer_name") ?: return null
        return SoldUnitCustomer(
            customerId = str("customer_id").orEmpty(),
            customerName = name,
            customerMobile = str("customer_mobile").orEmpty(),
            customerAddress = str("customer_address").orEmpty(),
            unitCount = int("unit_count"),
            parkingCount = int("parking_count"),
            unitPriceAmount = dbl("unit_price_amount"),
            parkingAmount = dbl("parking_amount"),
            totalAmount = dbl("total_amount"),
            receivedAmount = dbl("received_amount"),
            dueAmount = dbl("due_amount"),
            units = arr("units")?.mapNotNull { it.objOrNull()?.toSoldUnitSale() }.orEmpty(),
        )
    }

    /**
     * The issued numbers of a paper, e.g. [1, 3] after the second copy was
     * withdrawn. Falls back to 1..count for a server that only sends the count,
     * where 1..n is still true.
     */
    private fun JsonObject.versionList(versionsKey: String, countKey: String): List<Int> =
        arr(versionsKey)
            ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: (1..int(countKey)).toList()

    private fun JsonObject.toSoldUnitSale(): SoldUnitSale = SoldUnitSale(
        saleId = str("sale_id").orEmpty(),
        saleDate = str("sale_date").orEmpty(),
        letterCount = int("letter_count"),
        letterVersions = versionList("letter_versions", "letter_count"),
        bookingFormVersions = versionList("booking_form_versions", "booking_form_count"),
        nomineeCount = int("nominee_count"),
        unitPriceAmount = dbl("unit_price_amount"),
        parkingAmount = dbl("parking_amount"),
        buildingName = str("building_name").orEmpty(),
        floorName = str("floor_name").orEmpty(),
        receiptNo = str("receipt_no").orEmpty(),
        totalAmount = dbl("total_amount"),
        bookingAmount = dbl("booking_amount"),
        receivedAmount = dbl("received_amount"),
        dueAmount = dbl("due_amount"),
        lines = arr("lines")?.mapNotNull { el ->
            el.objOrNull()?.let { o ->
                SoldUnitLine(
                    type = o.str("type").orEmpty(),
                    caption = o.str("caption").orEmpty(),
                    place = o.str("place").orEmpty(),
                    amount = o.dbl("amount"),
                )
            }
        }.orEmpty(),
    )

    private fun JsonObject?.toSoldUnitsTotals(): SoldUnitsTotals = SoldUnitsTotals(
        customerCount = this?.int("customer_count") ?: 0,
        unitCount = this?.int("unit_count") ?: 0,
        parkingCount = this?.int("parking_count") ?: 0,
        unitPriceAmount = this?.dbl("unit_price_amount") ?: 0.0,
        parkingAmount = this?.dbl("parking_amount") ?: 0.0,
        totalAmount = this?.dbl("total_amount") ?: 0.0,
        receivedAmount = this?.dbl("received_amount") ?: 0.0,
        dueAmount = this?.dbl("due_amount") ?: 0.0,
    )

    // ---- Sale picker + summary -------------------------------------------

    /** `GET real-estate/unit-sale/ddl?q=&page=1&perPage=50` — items at `data.data.data`. */
    suspend fun searchSales(query: String): Resource<List<SaleOption>> = withContext(ioDispatcher) {
        val params = mapOf("q" to query.trim(), "page" to "1", "perPage" to "50")
        guarded {
            val response = reportApi.get("real-estate/unit-sale/ddl", params)
            envelope(response, onNotFound = { Resource.Success(emptyList()) }) { json ->
                val items = json.obj("data")?.obj("data")?.arr("data")
                    ?.mapNotNull { it.objOrNull()?.toSaleOption() }
                    .orEmpty()
                Resource.Success(items)
            }
        }
    }

    private fun JsonObject.toSaleOption(): SaleOption? {
        val id = long("id") ?: return null
        return SaleOption(
            id = id,
            label = str("label") ?: "Sale #$id",
            customerId = long("customer_id") ?: 0L,
            customerName = str("customer_name").orEmpty(),
            customerMobile = str("customer_mobile").orEmpty(),
            saleDate = str("sale_date").orEmpty(),
            totalAmount = dbl("total_amount"),
            bookingAmount = dbl("booking_amount"),
            downpaymentAmount = dbl("downpayment_amount"),
            dueAmount = dbl("due_amount"),
        )
    }

    /**
     * `GET real-estate/unit-sale/summary/{id}` — unlike the rest of the module
     * this response is singly nested: the payload sits directly at `data`.
     */
    suspend fun fetchSaleSummary(saleId: Long): Resource<SaleSummary> = withContext(ioDispatcher) {
        guarded {
            val response = reportApi.get("real-estate/unit-sale/summary/$saleId", emptyMap())
            envelope(response) { json ->
                val d = json.obj("data")
                    ?: return@envelope Resource.Error("Invalid response from server.")
                val booking = d.obj("booking")
                val customer = d.obj("customer")
                val amounts = d.obj("amounts")
                val meta = d.obj("meta")
                Resource.Success(
                    SaleSummary(
                        id = d.long("id") ?: saleId,
                        receiptNo = d.str("receipt_no").orEmpty(),
                        unitLabel = booking?.str("unit_label").orEmpty(),
                        parkingLabel = booking?.str("parking_label").orEmpty(),
                        customerId = customer?.long("id") ?: 0L,
                        customerName = customer?.str("name").orEmpty(),
                        customerMobile = customer?.str("mobile").orEmpty(),
                        totalAmount = amounts?.dbl("total_amount") ?: 0.0,
                        bookingAmount = amounts?.dbl("booking_amount") ?: 0.0,
                        downpaymentAmount = amounts?.dbl("downpayment_amount") ?: 0.0,
                        confirmedBookingAmount = amounts?.dbl("confirmed_booking_amount") ?: 0.0,
                        confirmedDownpaymentAmount = amounts?.dbl("confirmed_downpayment_amount") ?: 0.0,
                        confirmedReceived = amounts?.dbl("confirmed_received") ?: 0.0,
                        dueAmount = amounts?.dbl("due_amount") ?: 0.0,
                        saleDate = meta?.str("sale_date").orEmpty(),
                        status = meta?.str("status").orEmpty(),
                    )
                )
            }
        }
    }

    // ---- Schedule create / saved schedule / update ------------------------

    /** `POST real-estate/unit-sale/installment-create` — writes installment rows only. */
    suspend fun createSchedule(
        bookingId: Long,
        amount: Double,
        startDate: String,
        numberOfInstallments: Int,
        earlyPayment: Boolean,
        earlyDiscount: Double,
        earlyPaymentDate: String?,
    ): Resource<ScheduleCreateResult> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("booking_id", bookingId)
            addProperty("amount", amount)
            addProperty("start_date", startDate)
            addProperty("number_of_installments", numberOfInstallments)
            addProperty("early_payment", earlyPayment)
            addProperty("early_discount", earlyDiscount)
            if (earlyPaymentDate != null) {
                addProperty("early_payment_date", earlyPaymentDate)
            } else {
                add("early_payment_date", JsonNull.INSTANCE)
            }
        }
        guarded {
            val response = transactionApi.postObject("real-estate/unit-sale/installment-create", body)
            envelope(response) { json ->
                val payload = json.obj("data")?.obj("data")
                Resource.Success(
                    ScheduleCreateResult(
                        scheduleTotal = payload?.dbl("schedule_total") ?: 0.0,
                        installmentCount = payload?.int("installment_count") ?: 0,
                        message = json.message()
                            ?: "Real estate installment schedule created successfully.",
                    )
                )
            }
        }
    }

    /**
     * The customer's saved installment rows
     * (`GET accounts/installment/details/{customerId}?report=true`), narrowed to
     * this sale: rows whose `invoice_no` equals [receiptNo] (case-insensitive,
     * trimmed) — or all rows when none match — sorted by installment number.
     * An empty schedule arrives as `success:false` (HTTP 201) → empty list.
     */
    suspend fun fetchSavedSchedule(
        customerId: Long,
        receiptNo: String,
    ): Resource<List<SavedInstallmentRow>> = withContext(ioDispatcher) {
        guarded {
            val response = reportApi.get(
                "accounts/installment/details/$customerId",
                mapOf("report" to "true"),
            )
            envelope(response, onNotFound = { Resource.Success(emptyList()) }) { json ->
                val rows = json.obj("data")?.arr("data")
                    ?.mapNotNull { it.objOrNull()?.toSavedInstallmentRow() }
                    .orEmpty()
                val wanted = receiptNo.trim()
                val matching = rows.filter { it.invoiceNo.trim().equals(wanted, ignoreCase = true) }
                val chosen = if (matching.isNotEmpty()) matching else rows
                Resource.Success(chosen.sortedBy { it.installmentNo })
            }
        }
    }

    private fun JsonObject.toSavedInstallmentRow(): SavedInstallmentRow? {
        val id = str("installment_id") ?: return null
        return SavedInstallmentRow(
            installmentId = id,
            installmentNo = int("installment_no"),
            dueDate = str("due_date").orEmpty(),
            amount = dbl("amount"),
            paidAmount = dbl("paid_amount"),
            dueAmount = dbl("due_amount"),
            status = str("status").orEmpty(),
            earlyPaymentDiscount = dbl("early_payment_discount"),
            earlyPaymentDate = str("early_payment_date"),
            invoiceNo = str("invoice_no").orEmpty(),
        )
    }

    /**
     * `POST real-estate/unit-sale/installment-update`. [firstRowEarly] must be
     * non-null only for installment no. 1 — it adds `early_payment_date`,
     * `early_discount` and `early_payment_discount` (the same number twice, as
     * the web sends it).
     */
    suspend fun updateInstallment(
        installmentId: String,
        dueDate: String,
        amount: Double,
        firstRowEarly: FirstRowEarlyPayload? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("installment_id", installmentId)
            addProperty("due_date", dueDate)
            addProperty("amount", amount)
            if (firstRowEarly != null) {
                if (firstRowEarly.earlyPaymentDate != null) {
                    addProperty("early_payment_date", firstRowEarly.earlyPaymentDate)
                } else {
                    add("early_payment_date", JsonNull.INSTANCE)
                }
                addProperty("early_discount", firstRowEarly.earlyDiscount)
                addProperty("early_payment_discount", firstRowEarly.earlyDiscount)
            }
        }
        guarded {
            val response = transactionApi.postObject("real-estate/unit-sale/installment-update", body)
            envelope(response) { json ->
                Resource.Success(json.message() ?: "Installment updated successfully.")
            }
        }
    }

    /**
     * `POST real-estate/unit-sale/allotment-letter/generate/{saleId}` — renders
     * and APPENDS one immutable allotment-letter snapshot (L-{n}) to the sale.
     * [refNo]/[refDate] head the letter (the office's register number and the
     * day it goes out); blank ones are omitted so the server keeps deriving
     * what it always did. Printing the saved versions stays web-only.
     */
    suspend fun generateAllotmentLetter(
        saleId: String,
        refNo: String? = null,
        refDate: String? = null,
    ): Resource<String> =
        withContext(ioDispatcher) {
            val body = JsonObject().apply {
                refNo?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("ref_no", it) }
                refDate?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("ref_date", it) }
            }
            guarded {
                val response = transactionApi.postObject(
                    "real-estate/unit-sale/allotment-letter/generate/$saleId",
                    body,
                )
                envelope(response) { json ->
                    Resource.Success(json.message() ?: "Allotment letter generated.")
                }
            }
        }

    /**
     * `DELETE real-estate/unit-sale/allotment-letter/{saleId}/{version}` —
     * withdraws one issued copy. The number is not reused afterwards: the
     * server issues past the highest ever used, so L-2 gone never means a new
     * paper called L-2. A missing version answers success:false on a 2xx.
     */
    suspend fun withdrawAllotmentLetter(saleId: String, version: Int): Resource<String> =
        withContext(ioDispatcher) {
            guarded {
                val response = reportApi.delete(
                    "real-estate/unit-sale/allotment-letter/$saleId/$version",
                )
                envelope(response) { json ->
                    Resource.Success(json.message() ?: "Letter withdrawn.")
                }
            }
        }

    /**
     * `POST real-estate/unit-sale/booking-form/generate/{saleId}` — issues one
     * immutable booking-form snapshot (B-{n}) on its own series, separate from
     * the letters. The letter's ref prefix is NOT offered here: the two are
     * numbered apart, and BST/ALLOT on a booking form would be wrong in a way
     * nobody notices until the register is reconciled.
     */
    suspend fun generateBookingForm(
        saleId: String,
        refNo: String? = null,
        refDate: String? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            refNo?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("ref_no", it) }
            refDate?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("ref_date", it) }
        }
        guarded {
            val response = transactionApi.postObject(
                "real-estate/unit-sale/booking-form/generate/$saleId",
                body,
            )
            envelope(response) { json ->
                Resource.Success(json.message() ?: "Booking form generated.")
            }
        }
    }

    /** `DELETE real-estate/unit-sale/booking-form/{saleId}/{version}` — see [withdrawAllotmentLetter]. */
    suspend fun withdrawBookingForm(saleId: String, version: Int): Resource<String> =
        withContext(ioDispatcher) {
            guarded {
                val response = reportApi.delete(
                    "real-estate/unit-sale/booking-form/$saleId/$version",
                )
                envelope(response) { json ->
                    Resource.Success(json.message() ?: "Booking form withdrawn.")
                }
            }
        }

    // ---- Sale nominees -----------------------------------------------------

    /**
     * `GET real-estate/unit-sale/{saleId}/nominees` — the buyer's own nominee
     * list (written on the customer screen) plus what THIS sale says about
     * them. A buyer holding three flats can leave each of them differently, so
     * this is asked per sale and never inherited from the customer.
     */
    suspend fun fetchSaleNominees(saleId: String): Resource<SaleNominees> =
        withContext(ioDispatcher) {
            guarded {
                val response = reportApi.get("real-estate/unit-sale/$saleId/nominees", emptyMap())
                envelope(response) { json ->
                    val payload = json.obj("data")?.obj("data")
                    val available = payload?.arr("available")?.mapNotNull { el ->
                        el.objOrNull()?.let { o ->
                            SaleNomineeOption(
                                id = o.str("id")?.toLongOrNull() ?: return@let null,
                                name = o.str("name").orEmpty(),
                                relation = o.str("relation").orEmpty(),
                                nationalId = o.str("national_id").orEmpty(),
                                // The buyer's own default share — the starting
                                // point when this sale first picks them.
                                defaultShare = o.str("share_percentage")
                                    ?.toDoubleOrNull()?.toPlainShare().orEmpty(),
                            )
                        }
                    }.orEmpty()
                    val attached = payload?.arr("nominees")?.mapNotNull { el ->
                        el.objOrNull()?.let { o ->
                            val id = o.str("nominee_id")?.toLongOrNull() ?: return@let null
                            SaleNomineePick(
                                nomineeId = id,
                                share = o.str("share_percentage")?.toDoubleOrNull()?.toPlainShare().orEmpty(),
                                priority = o.str("priority_order")?.toDoubleOrNull()?.toInt()?.toString().orEmpty(),
                            )
                        }
                    }.orEmpty()
                    Resource.Success(SaleNominees(available = available, attached = attached))
                }
            }
        }

    /**
     * `POST real-estate/unit-sale/{saleId}/nominees` — replaces the sale's
     * nominations with [picks]. Always sent, even empty: that is how the last
     * nominee is removed. Shares travel as numbers or null (division left to
     * law); priorities as numbers.
     */
    suspend fun saveSaleNominees(
        saleId: String,
        picks: List<SaleNomineePick>,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            add("nominees", JsonArray().apply {
                picks.forEachIndexed { index, pick ->
                    add(JsonObject().apply {
                        addProperty("nominee_id", pick.nomineeId)
                        pick.share.trim().toDoubleOrNull()
                            ?.let { addProperty("share_percentage", it) }
                            ?: add("share_percentage", JsonNull.INSTANCE)
                        // Nothing typed means the order they appear in, which
                        // is the order the booking form prints them.
                        addProperty(
                            "priority_order",
                            pick.priority.trim().toIntOrNull() ?: (index + 1),
                        )
                    })
                }
            })
        }
        guarded {
            val response = transactionApi.postObject("real-estate/unit-sale/$saleId/nominees", body)
            envelope(response) { json ->
                Resource.Success(json.message() ?: "Nominees saved.")
            }
        }
    }

    // ---- Cancelling a sale -------------------------------------------------

    /**
     * `POST real-estate/unit-sale/cancel/{saleId}` — withdraws the sale: the
     * voucher goes to the recycle bin, the flat goes back on the market, and
     * the receipts are marked REVERSED. Not final — restoring the voucher puts
     * all of it back. The reason is required (min 3 chars): a flat suddenly
     * for sale again gets asked about, and the answer belongs on the record.
     *
     * The refusals matter more here than anywhere else — an approved voucher,
     * papers already issued, a live installment schedule, receipts taken after
     * the booking — so the server's own sentence is what gets surfaced.
     */
    suspend fun cancelSale(saleId: String, reason: String): Resource<String> =
        withContext(ioDispatcher) {
            val body = JsonObject().apply { addProperty("reason", reason) }
            guarded {
                val response = transactionApi.postObject("real-estate/unit-sale/cancel/$saleId", body)
                envelope(response) { json ->
                    Resource.Success(json.message() ?: "Sale cancelled.")
                }
            }
        }

    /** 50.0 → "50", 33.33 → "33.33" — what belongs in a text field. */
    private fun Double.toPlainShare(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()

    // ---- Envelope / transport helpers -------------------------------------

    /** The shared try/catch every call runs inside. */
    private inline fun <T> guarded(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error(NO_NETWORK)
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error(GENERIC_ERROR)
    }

    /**
     * Applies the Laravel envelope rules: 401 → session expired; then branch on
     * the JSON `success` flag (never the HTTP status — `notFound()` is
     * `success:false` at HTTP 201). A false success routes to [onNotFound] when
     * given, otherwise becomes an error with the server's message (e.g. the 422
     * "already exists" validation replies).
     */
    private fun <T> envelope(
        response: Response<JsonElement>,
        onNotFound: (() -> Resource<T>)? = null,
        parse: (JsonObject) -> Resource<T>,
    ): Resource<T> {
        if (response.code() == HTTP_UNAUTHORIZED) {
            return Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        }
        val json = response.jsonBody()
            ?: return Resource.Error("Server error (${response.code()}). Please try again later.")
        val success = json.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        if (success == false) {
            // List reads treat a false success ("No data found!") as an empty
            // result; writes surface the server's message (422 validation etc.).
            return onNotFound?.invoke() ?: Resource.Error(json.message() ?: GENERIC_ERROR)
        }
        if (!response.isSuccessful && response.code() != 201) {
            return Resource.Error(
                json.message() ?: "Server error (${response.code()}). Please try again later."
            )
        }
        return parse(json)
    }

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a write was rejected: `message`, `error.message`, or a field error. */
    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() }
            ?: get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: firstFieldError()

    /** Reads `errors: {field: ["reason", …]}` — Laravel's per-field validation. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = get("errors")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }

    // ---- JSON field helpers -----------------------------------------------

    private fun JsonElement.objOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.int(key: String): Int = dbl(key).toInt()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim()?.toDoubleOrNull()?.toLong()
}
