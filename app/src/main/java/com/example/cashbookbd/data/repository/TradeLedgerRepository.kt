package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.floor

/** Which trade ledger — the two screens share everything but the arithmetic. */
enum class TradeLedgerKind { PURCHASE, SALES }

/** One product line of a voucher (index-aligned across the stacked cells). */
data class TradeLedgerLine(
    /** "Category Product" when the branch groups stock; else just the name. */
    val productLabel: String,
    val unitName: String,
    val quantity: Double,
    val rate: Double,
    /** rate×qty; Sales floors it to whole taka, as the web does. */
    val total: Double,
)

/** One voucher row of the Purchase/Sales Ledger. */
data class TradeLedgerRow(
    val slNumber: Int,
    val challanNo: String,
    /** Arrives pre-formatted dd/mm/yyyy — shown verbatim, never re-parsed. */
    val challanDate: String,
    /** The party (supplier/customer) resolved from the voucher's accounts. */
    val partyName: String,
    val note: String,
    val vehicleNo: String,
    val orderNumber: String,
    val deliveryLocation: String,
    val lines: List<TradeLedgerLine>,
    /** Purchase: first coa4 40 credit (absent → null, shown blank).
     *  Sales: Σ coa4 23 debit (0 → dash). */
    val discount: Double?,
    /** Purchase: first coa4 17 credit. Sales: Σ coa4 17 debit. */
    val payment: Double,
    val balance: Double,
    val branchId: String,
    val voucherImage: String,
    val remarks: String,
    /** The main_trx id the approve endpoints key on (0 = row not actionable). */
    val voucherId: Long,
    val isApproved: Boolean,
)

/** The bold summary strip under the table (the web calculators' figures). */
data class TradeLedgerTotals(
    val quantity: Double,
    val total: Double,
    val discount: Double,
    val payment: Double,
    val balance: Double,
)

data class TradeLedgerReport(
    val rows: List<TradeLedgerRow>,
    val totals: TradeLedgerTotals,
)

// The fixed chart-of-accounts ids the web's cell rules key on.
private const val COA_STOCK = 15L
private const val COA_CASH = 17L
private const val COA_SALES_DISCOUNT = 23L
private const val COA_PURCHASE_DISCOUNT = 40L

/**
 * The Purchase/Sales Ledger reports, parsed to the web's own cell rules: one
 * row per voucher, the product lines stacked inside it, discount/payment read
 * off the fixed CoA ids, and the balance formulas kept deliberately different
 * (Sales trusts `sales_master.total`; Purchase recomputes from lines).
 */
class TradeLedgerRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetch(
        kind: TradeLedgerKind,
        branchId: String,
        ledgerId: String?,
        productId: String?,
        startDate: String,
        endDate: String,
        search: String,
        groupByCategory: Boolean,
    ): Resource<TradeLedgerReport> = withContext(ioDispatcher) {
        try {
            val endpoint = when (kind) {
                TradeLedgerKind.PURCHASE -> "reports/purchase/ledger"
                TradeLedgerKind.SALES -> "reports/sales/ledger"
            }
            val params = buildMap {
                put("branch_id", branchId)
                // Absent params read as unset server-side (the web sends the
                // literal "null", which the server also treats as absent).
                ledgerId?.takeIf { it.isNotBlank() }?.let { put("ledger_id", it) }
                productId?.takeIf { it.isNotBlank() }?.let { put("item_id", it) }
                put("startdate", startDate)
                put("enddate", endDate)
                search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }
                put("delay", "1")
            }
            val response = api.get(endpoint, params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            // An empty period answers success:false with error 404 — that is a
            // result, not a failure.
            val rows = body.obj("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: JsonArray()
            val parsed = rows.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.toRow(kind, groupByCategory)
            }
            Resource.Success(TradeLedgerReport(rows = parsed, totals = parsed.totals(kind)))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    // ---- Row actions -------------------------------------------------------

    /**
     * Approves one voucher (`POST accounts/voucher/approved/{id}`) — the web
     * row icon's own endpoint. The 2026-08-19 security pass turned the route
     * POST (a GET that changes a voucher was the audit's 1.1), put it behind
     * `cashbook.approved`, scoped it to the caller's branches, and made it
     * answer the ordinary foundData envelope instead of a bare "1"/"2".
     * The old "1"/"2" strings are still read, for a server the API deploy has
     * not reached yet — though on such a server this POST 405s anyway.
     *
     * ⚠️ Approval locks the voucher against editing — only confirmed taps.
     */
    suspend fun approveVoucher(voucherId: Long): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.post("accounts/voucher/approved/$voucherId", emptyMap())
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to approve vouchers.")
            }
            if (response.code() == 405) {
                // Old client assumption meeting an old server, or vice versa.
                return@withContext Resource.Error("The server refused the request. Please update the app and try again.")
            }
            val body = response.body()
            val primitive = body?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val obj = body?.takeIf { it.isJsonObject }?.asJsonObject
            val success = obj?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = obj?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
            when {
                primitive == "1" || success == true -> Resource.Success("Voucher approved.")
                primitive == "2" -> Resource.Error("Voucher not found.")
                // notFound() carries the reason ("Transaction Not Found" — also
                // what an out-of-reach branch answers, deliberately).
                success == false -> Resource.Error(message ?: "The voucher could not be approved.")
                else -> Resource.Error("The voucher could not be approved.")
            }
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Withdraws one voucher's approval (`POST admin/voucher/remove/approval`,
     * body `{id, mtm_id, remove_for_approval}`) — again the row icon's own
     * endpoint, not the bulk Approval Remove form's.
     */
    suspend fun removeApproval(voucherId: Long, vrNo: String): Resource<String> =
        withContext(ioDispatcher) {
            try {
                val response = api.postAny(
                    "admin/voucher/remove/approval",
                    mapOf(
                        "id" to voucherId,
                        "mtm_id" to voucherId,
                        "remove_for_approval" to vrNo,
                    ),
                )
                if (response.code() == 401) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                }
                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                val success = body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                val message = body?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
                if (success == false) {
                    Resource.Error(message ?: "The approval could not be removed.")
                } else {
                    Resource.Success(message ?: "Approval removed.")
                }
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    // ---- Row parsing -------------------------------------------------------

    private fun JsonObject.toRow(kind: TradeLedgerKind, groupByCategory: Boolean): TradeLedgerRow? {
        val master = obj(if (kind == TradeLedgerKind.PURCHASE) "purchase_master" else "sales_master")
        val order = master?.obj(if (kind == TradeLedgerKind.PURCHASE) "purchase_order" else "sales_order")
        val rateKey = if (kind == TradeLedgerKind.PURCHASE) "purchase_price" else "sales_price"

        val lines = master?.get("details")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                val d = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val product = d.obj("product")
                val qty = d.dbl("quantity")
                val rate = d.dbl(rateKey)
                TradeLedgerLine(
                    productLabel = buildString {
                        if (groupByCategory) {
                            product?.obj("category")?.text("name")?.takeIf { it.isNotBlank() }
                                ?.let { append(it).append(' ') }
                        }
                        append(product?.text("name").orEmpty())
                    }.trim(),
                    unitName = product?.obj("unit")?.text("name").orEmpty(),
                    quantity = qty,
                    rate = rate,
                    // Sales floors each line to whole taka; Purchase does not.
                    total = if (kind == TradeLedgerKind.SALES) floor(qty * rate) else qty * rate,
                )
            }
            .orEmpty()

        // Every account movement of the voucher, flattened across its masters.
        val accDetails: List<JsonObject> =
            get("acc_transaction_master")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                ?.flatMap { m ->
                    m.get("acc_transaction_details")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                        .orEmpty()
                }
                .orEmpty()

        // The party: the first account that is neither stock nor cash; the
        // cash account's own name failing that.
        val partyName = accDetails.firstOrNull {
            it.long("coa4_id") !in setOf(COA_STOCK, COA_CASH)
        }?.obj("coa_l4")?.text("name")
            ?: accDetails.firstOrNull { it.long("coa4_id") == COA_CASH }
                ?.obj("coa_l4")?.text("name")
            ?: ""

        val discount: Double?
        val payment: Double
        val balance: Double
        if (kind == TradeLedgerKind.PURCHASE) {
            discount = accDetails.firstOrNull { it.long("coa4_id") == COA_PURCHASE_DISCOUNT }?.dbl("credit")
            payment = accDetails.firstOrNull { it.long("coa4_id") == COA_CASH }?.dbl("credit") ?: 0.0
            balance = lines.sumOf { it.total } -
                accDetails.filter { it.long("coa4_id") == COA_CASH }.sumOf { it.dbl("credit") } -
                accDetails.filter { it.long("coa4_id") == COA_PURCHASE_DISCOUNT }.sumOf { it.dbl("credit") }
        } else {
            discount = accDetails.filter { it.long("coa4_id") == COA_SALES_DISCOUNT }.sumOf { it.dbl("debit") }
            payment = accDetails.filter { it.long("coa4_id") == COA_CASH }.sumOf { it.dbl("debit") }
            // Sales trusts the server total (it carries header-level discount).
            balance = (master?.dbl("total") ?: 0.0) - payment - discount
        }

        val note = if (kind == TradeLedgerKind.PURCHASE) {
            master?.text("notes").orEmpty()
        } else {
            master?.text("notes")?.takeIf { it.isNotBlank() }
                ?: text("notes").takeIf { it.isNotBlank() }
                ?: accDetails.firstOrNull()?.text("remarks").orEmpty()
        }

        return TradeLedgerRow(
            slNumber = long("sl_number")?.toInt() ?: 0,
            challanNo = text("challan_no"),
            challanDate = text("challan_date"),
            partyName = partyName,
            note = note,
            vehicleNo = formatVehicleNo(master?.text("vehicle_no").orEmpty()),
            orderNumber = order?.text("order_number").orEmpty(),
            deliveryLocation = order?.text("delivery_location").orEmpty(),
            lines = lines,
            discount = discount,
            payment = payment,
            balance = balance,
            branchId = text("branch_id"),
            voucherImage = text("voucher_image"),
            remarks = text("remarks"),
            // The web's fallback chain for the voucher id, in its order.
            voucherId = long("mtm_id") ?: long("smtm_id") ?: long("mtmid") ?: long("id") ?: 0L,
            isApproved = long("is_approved") == 1L,
        )
    }

    /** The web calculators: first-matching-account sums, per row. */
    private fun List<TradeLedgerRow>.totals(kind: TradeLedgerKind): TradeLedgerTotals {
        val quantity = sumOf { row -> row.lines.sumOf { it.quantity } }
        val total = sumOf { row -> row.lines.sumOf { it.total } }
        val discount = sumOf { it.discount ?: 0.0 }
        val payment = sumOf { it.payment }
        val balance = if (kind == TradeLedgerKind.PURCHASE) {
            total - payment - discount
        } else {
            sumOf { it.balance }
        }
        return TradeLedgerTotals(quantity, total, discount, payment, balance)
    }

    /** The web's formatTransportationNumber: upper-cased prefix, one space. */
    private fun formatVehicleNo(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val splitAt = trimmed.indexOfFirst { it.isDigit() }
        if (splitAt <= 0) return trimmed.uppercase()
        return trimmed.substring(0, splitAt).trim().uppercase() + " " +
            trimmed.substring(splitAt).trim()
    }

    // ---- JSON helpers ------------------------------------------------------

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}
