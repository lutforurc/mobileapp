package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

/**
 * One line of the Order With Transaction table — already carrying the web
 * report's computed figures, including the running [balance].
 */
data class OrderTransactionRow(
    val challanNo: String,
    /** vr_date (falling back to the sales/purchase transact_date), raw from the API. */
    val challanDate: String,
    /** First line of the Product & Details cell: product name or the ledger name. */
    val productLine: String,
    /** Second, muted line: the voucher's remarks (blank when none). */
    val remarks: String,
    /** True when the voucher carries a sales/purchase detail line (qty/rate/total show). */
    val hasLineDetail: Boolean,
    val quantity: Double,
    val rate: Double,
    val total: Double,
    val discount: Double,
    /** Signed: a receipt against a sales order is negative, like the web. */
    val payment: Double,
    val balance: Double,
)

/**
 * The `admin/order/transaction` report: the order header (for the summary
 * tiles) plus the de-duplicated, balance-accumulated transaction rows and
 * their grand totals — the web page's row math, ported verbatim.
 */
data class OrderTransactionReport(
    val orderNumber: String,
    val orderRate: Double,
    val totalOrder: Double,
    /** "1" Purchase / "2" Sales / "3" Stock (raw, as the API sends it). */
    val orderType: String,
    val productName: String,
    val unitName: String,
    val customerName: String,
    val notes: String,
    val rows: List<OrderTransactionRow>,
    val totalQuantity: Double,
    val totalAmount: Double,
    val totalDiscount: Double,
    /** Signed sum; the screen shows its absolute value, like the web footer. */
    val totalPayment: Double,
    /** The last row's running balance (0 when there are no rows). */
    val finalBalance: Double,
) {
    val orderTypeLabel: String
        get() = when (orderType) {
            "1" -> "Purchase"
            "2" -> "Sales"
            "3" -> "Stock"
            else -> "Order"
        }

    /** The web renames the Payment column for a sales order. */
    val paymentHeader: String get() = if (orderType == "2") "RECEIVED" else "PAYMENT"
}

/** One `others_expense` line of the Average Price report. */
data class OrderOtherExpense(
    val name: String,
    val debit: Double,
)

/** The `invoice/order/avg-price` figures, one-to-one with the API payload. */
data class AveragePriceReport(
    /** The product's name — blank means the backend found no purchase details. */
    val productName: String,
    val unitName: String,
    val totalQty: Double,
    val increaseQty: Double,
    val decreaseQty: Double,
    val totalCost: Double,
    val grandTotalExpense: Double,
    val otherExpenses: List<OrderOtherExpense>,
) {
    /** Mirrors the web's "No Purchase Details Found" branch. */
    val isEmpty: Boolean get() = productName.isBlank()

    /** total cost ÷ total qty, guarded against ÷0. */
    val averagePerUnit: Double get() = if (totalQty == 0.0) 0.0 else totalCost / totalQty

    /** grand total expense ÷ total qty, guarded against ÷0. */
    val netPerUnit: Double get() = if (totalQty == 0.0) 0.0 else grandTotalExpense / totalQty
}

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

/**
 * Backs the two admin order reports — Order With Transaction
 * (`POST admin/order/transaction`) and Average Price
 * (`POST invoice/order/avg-price`) — plus the shared order type-ahead
 * (`GET invoice/order/search`).
 *
 * The backend answers errors as a `success:false` envelope, sometimes on a
 * non-2xx status (its `notFound()` helper may emit 404 or 201), so both the
 * body and the error body are read — the JSON `success` field is the verdict,
 * never the HTTP status. "Not found" messages ("Order not found." etc.) are
 * surfaced verbatim as errors. A 401 sets [Resource.Error.isUnauthorized].
 */
class OrderReportsRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        /** The ledger the web treats as the cash/payment line of a voucher. */
        private const val CASH_COA4_ID = 17
    }

    // ------------------------------------------------------------------
    // Order search (shared by both filters)
    // ------------------------------------------------------------------

    /**
     * Order type-ahead (`invoice/order/search?q=`). Each option: value = order
     * id, label = order_number, sublabel = order_for • product (label_2/label_3).
     * A `notFound()` (404/201) answer means "no match", not an error.
     */
    suspend fun searchOrders(query: String): Resource<List<SelectorOption>> =
        withContext(ioDispatcher) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Resource.Success(emptyList())
            safeCall {
                val response = reportApi.get("invoice/order/search", mapOf("q" to q))
                response.unauthorizedOrNull()?.let { return@safeCall it }
                if (response.code() == 404 || response.code() == 201) {
                    return@safeCall Resource.Success(emptyList())
                }
                if (!response.isSuccessful) {
                    return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val array = unwrap(response.body())?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return@safeCall Resource.Success(emptyList())
                Resource.Success(
                    array.mapNotNull { el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        val id = o.str("value") ?: return@mapNotNull null
                        SelectorOption(
                            id = id,
                            label = o.str("label") ?: id,
                            // label_2 = order_for (party), label_3 = product name.
                            sublabel = listOfNotNull(o.str("label_2"), o.str("label_3"))
                                .joinToString("  •  ")
                                .ifBlank { null },
                        )
                    }
                )
            }
        }

    // ------------------------------------------------------------------
    // Order With Transaction
    // ------------------------------------------------------------------

    /**
     * `POST admin/order/transaction`. The id and number travel under every
     * alias the backend reads (`order_id`/`id`, `order_number`/`order_no`/
     * `orderNumber`), exactly like the web page.
     */
    suspend fun fetchOrderTransactions(
        orderId: Int,
        orderNumber: String,
        branchId: Long,
    ): Resource<OrderTransactionReport> = withContext(ioDispatcher) {
        safeCall {
            val body = JsonObject().apply {
                addProperty("order_id", orderId)
                addProperty("id", orderId)
                addProperty("order_number", orderNumber)
                addProperty("order_no", orderNumber)
                addProperty("orderNumber", orderNumber)
                addProperty("branch_id", branchId)
            }
            val response = transactionApi.postObject("admin/order/transaction", body)
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == HTTP_FORBIDDEN) {
                return@safeCall Resource.Error("You do not have permission for this action.")
            }

            val envelope = readEnvelope(response)
                ?: return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            if (envelope.success != true) {
                // e.g. "Order not found." — the server's wording is the truth.
                return@safeCall Resource.Error(envelope.message ?: "Couldn't load the order transactions.")
            }
            val order = envelope.data?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall Resource.Error("Invalid response from server.")
            Resource.Success(parseOrderTransactions(order))
        }
    }

    /** The web page's row math, verbatim (see OrderWithProduct.tsx). */
    private fun parseOrderTransactions(order: JsonObject): OrderTransactionReport {
        val product = order.obj("product")
        val productName = product?.str("name").orEmpty()
        val unitName = product?.obj("unit")?.str("name").orEmpty()
        val orderRate = order.num("order_rate")

        // De-duplicate by main_transaction_master.id (keep the first),
        // falling back to vr_no / the row's own id like the web.
        val seen = HashSet<String>()
        val unique = order.arr("transaction_with_order").withIndex().mapNotNull { (index, el) ->
            val trx = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val mtm = trx.obj("main_transaction_master")
            val key = mtm?.str("id") ?: mtm?.str("vr_no") ?: trx.str("id") ?: index.toString()
            if (!seen.add(key)) null else trx
        }

        // First pass: everything except the running balance.
        val partials = unique.map { trx ->
            val mtm = trx.obj("main_transaction_master")
            val salesMaster = mtm?.obj("sales_master")
            val purchaseMaster = mtm?.obj("purchase_master")
            val salesDetails = salesMaster?.arr("details") ?: JsonArray()
            val purchaseDetails = purchaseMaster?.arr("details") ?: JsonArray()
            /** The active master's lines: sales when it has any, else purchase. */
            val details = if (salesDetails.size() > 0) salesDetails else purchaseDetails
            val firstSales = salesDetails.firstObj()
            val firstPurchase = purchaseDetails.firstObj()
            val hasLineDetail = firstSales != null || firstPurchase != null

            val accDetails = trx.arr("acc_transaction_details").mapNotNull {
                it.takeIf { e -> e.isJsonObject }?.asJsonObject
            }
            val debitTotal = accDetails.sumOf { it.num("debit") }
            val creditTotal = accDetails.sumOf { it.num("credit") }

            // Product cell: the product for a sales/purchase voucher, else the
            // voucher's primary ledger (the coa4==17 line first, then the first
            // debit/credit line, then any named line).
            val cash17Name = accDetails
                .firstOrNull { it.num("coa4_id").toInt() == CASH_COA4_ID }
                ?.obj("coa_l4")?.str("name")
            val primaryLedgerName = cash17Name
                ?: accDetails.firstOrNull { it.num("debit") > 0.0 }?.obj("coa_l4")?.str("name")
                ?: accDetails.firstOrNull { it.num("credit") > 0.0 }?.obj("coa_l4")?.str("name")
                ?: accDetails.firstNotNullOfOrNull { it.obj("coa_l4")?.str("name") }
                ?: "-"
            val hasMaster = salesMaster != null || purchaseMaster != null
            val productLine = if (hasMaster) productName.ifBlank { "-" } else primaryLedgerName
            val remarks = accDetails.firstOrNull()?.str("remarks").orEmpty()

            // total = sales.total ?: purchase.total ?: max(Σdebit, Σcredit)
            // (`||` on the web, so a zero falls through to the next source).
            val total = salesMaster?.num("total").orZero().takeIf { it != 0.0 }
                ?: purchaseMaster?.num("total").orZero().takeIf { it != 0.0 }
                ?: max(debitTotal, creditTotal)

            // payment = the coa4==17 line's debit (positive) or credit (negative)
            // when the voucher has a line detail; else the whole voucher's
            // -Σcredit (negative) or Σdebit.
            val cash17Debit = accDetails
                .filter { it.num("coa4_id").toInt() == CASH_COA4_ID }
                .sumOf { it.num("debit") }
            val cash17Credit = accDetails
                .filter { it.num("coa4_id").toInt() == CASH_COA4_ID }
                .sumOf { it.num("credit") }
            val payment = if (hasLineDetail) {
                when {
                    cash17Debit > 0.0 -> cash17Debit
                    cash17Credit > 0.0 -> -cash17Credit
                    else -> 0.0
                }
            } else {
                if (creditTotal > 0.0) -creditTotal else debitTotal
            }

            // qty = Σ details.quantity of the active master; rate = the first
            // detail's price, falling back to the order's own rate.
            val quantity = details.sumOfObj { it.num("quantity") }
            val rate = firstSales?.num("sales_price").orZero().takeIf { it != 0.0 }
                ?: firstPurchase?.num("purchase_price").orZero().takeIf { it != 0.0 }
                ?: orderRate

            val discount = salesMaster?.num("discount").orZero().takeIf { it != 0.0 }
                ?: purchaseMaster?.num("discount").orZero()

            OrderTransactionRow(
                challanNo = mtm?.str("vr_no") ?: "-",
                challanDate = mtm?.str("vr_date")
                    ?: salesMaster?.str("transact_date")
                    ?: purchaseMaster?.str("transact_date")
                    ?: "-",
                productLine = productLine,
                remarks = remarks,
                hasLineDetail = hasLineDetail,
                quantity = if (hasLineDetail) quantity else 0.0,
                rate = if (hasLineDetail) rate else 0.0,
                total = if (hasLineDetail) total else 0.0,
                discount = discount,
                payment = payment,
                balance = 0.0,
            )
        }

        // Second pass: the running balance. Normally balance += total −
        // discount − |payment|; when every row's total is 0 (payment-only
        // orders) the balance accumulates +|payment| instead, like the web.
        val rowTotalAmount = partials.sumOf { it.total }
        val rowPaymentAmount = partials.sumOf { abs(it.payment) }
        val useReceivedBalance = rowTotalAmount == 0.0 && rowPaymentAmount > 0.0
        var cumulative = 0.0
        val rows = partials.map { r ->
            cumulative += if (useReceivedBalance) {
                abs(r.payment)
            } else {
                r.total - r.discount - abs(r.payment)
            }
            r.copy(balance = cumulative)
        }

        return OrderTransactionReport(
            orderNumber = order.str("order_number").orEmpty(),
            orderRate = orderRate,
            totalOrder = order.num("total_order"),
            orderType = order.str("order_type").orEmpty(),
            productName = productName,
            unitName = unitName,
            customerName = order.obj("customer")?.str("name").orEmpty(),
            notes = order.str("notes").orEmpty(),
            rows = rows,
            totalQuantity = rows.sumOf { it.quantity },
            totalAmount = rows.sumOf { it.total },
            totalDiscount = rows.sumOf { it.discount },
            totalPayment = rows.sumOf { it.payment },
            finalBalance = rows.lastOrNull()?.balance ?: 0.0,
        )
    }

    // ------------------------------------------------------------------
    // Average Price
    // ------------------------------------------------------------------

    /**
     * `POST invoice/order/avg-price` with the web's exact body:
     * `{branchId, orderNumber: <order id>, reportType}` — all numeric.
     * Report type 2 (Sales) currently 500s server-side; that error is
     * surfaced to the caller rather than masked.
     */
    suspend fun fetchAveragePrice(
        branchId: Long,
        orderId: Int,
        reportType: Int,
    ): Resource<AveragePriceReport> = withContext(ioDispatcher) {
        safeCall {
            val body = JsonObject().apply {
                addProperty("branchId", branchId)
                addProperty("orderNumber", orderId)
                addProperty("reportType", reportType)
            }
            val response = transactionApi.postObject("invoice/order/avg-price", body)
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == HTTP_FORBIDDEN) {
                return@safeCall Resource.Error("You do not have permission for this action.")
            }

            val envelope = readEnvelope(response)
                ?: return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            if (envelope.success != true) {
                return@safeCall Resource.Error(envelope.message ?: "Couldn't load the average price report.")
            }
            val data = envelope.data?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall Resource.Error("Invalid response from server.")

            Resource.Success(
                AveragePriceReport(
                    productName = data.str("total_purchase_product").orEmpty(),
                    unitName = data.str("product_unit").orEmpty(),
                    totalQty = data.num("total_purchase_qty"),
                    increaseQty = data.num("total_increase_qty"),
                    decreaseQty = data.num("total_decrease_qty"),
                    totalCost = data.num("total_purchase_cost"),
                    grandTotalExpense = data.num("grand_total_expense"),
                    otherExpenses = data.arr("others_expense").mapNotNull { el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        OrderOtherExpense(
                            name = o.str("name") ?: return@mapNotNull null,
                            debit = o.num("debit"),
                        )
                    },
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // Envelope & JSON helpers
    // ------------------------------------------------------------------

    private data class Envelope(
        val success: Boolean?,
        val message: String?,
        /** The `data.data` payload (or `data` when not double-wrapped). */
        val data: JsonElement?,
    )

    /**
     * Reads the `success`/`message`/`data` envelope from whichever side of the
     * response carries the JSON — the body on 2xx, the error body when the
     * backend's `notFound()` rode a 404/4xx. Null when neither side parses.
     */
    private fun readEnvelope(response: Response<JsonElement>): Envelope? {
        val root: JsonElement? = if (response.isSuccessful) {
            response.body()
        } else {
            runCatching {
                response.errorBody()?.string()?.let(JsonParser::parseString)
            }.getOrNull()
        }
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val success = obj.get("success")?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }?.asBoolean
        val message = obj.str("message")
        // foundData() double-wraps: {data: {data: <payload>, transaction_date}}.
        val data = obj.get("data")?.takeUnless { it.isJsonNull }
        val payload = if (data != null && data.isJsonObject) {
            data.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: data
        } else {
            data
        }
        return Envelope(success = success, message = message, data = payload)
    }

    /** Peels the `data` / `data.data` envelope produced by `foundData()`. */
    private fun unwrap(root: JsonElement?): JsonElement? {
        if (root == null) return null
        if (!root.isJsonObject) return root
        val obj = root.asJsonObject
        val success = obj.get("success")?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }?.asBoolean
        if (success == false) return null
        val data = obj.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** The primitive at [key] as trimmed text; null when absent/null/non-primitive. */
    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim()?.ifBlank { null }

    /** The primitive at [key] as a number; 0.0 when absent or non-numeric text. */
    private fun JsonObject.num(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim()?.toDoubleOrNull() ?: 0.0

    /** The object at [key], or null. */
    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonObject }?.asJsonObject

    /** The array at [key], or an empty one. */
    private fun JsonObject.arr(key: String): JsonArray =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonArray.firstObj(): JsonObject? =
        firstOrNull { it.isJsonObject }?.asJsonObject

    private inline fun JsonArray.sumOfObj(selector: (JsonObject) -> Double): Double {
        var sum = 0.0
        forEach { el -> if (el.isJsonObject) sum += selector(el.asJsonObject) }
        return sum
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    /** Shared 401 check for a Retrofit [Response]. */
    private fun Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }
}
