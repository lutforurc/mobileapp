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

/**
 * One pending product line of a multi-product order. Values stay the strings
 * the user typed — the web form sends them over the wire untouched, and the
 * backend does the casting.
 */
data class OrderProductLine(
    val productId: String,
    val productName: String,
    /** Order quantity (`total_order`). Blank means 0 — zero is a valid order. */
    val qty: String,
    /** Order rate. Blank goes over the wire as a literal 0, like the web. */
    val rate: String,
    /** Contract quantity. Blank goes over the wire as null. */
    val contractQty: String,
) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val rateValue: Double get() = rate.toDoubleOrNull() ?: 0.0

    /** Display amount (rate × qty), like the web grid's Amount column. */
    val amount: Double get() = qtyValue * rateValue
}

/**
 * The flat header `invoice/order/store` takes. Every value is the string the
 * form holds (dates already yyyy-MM-dd); [refOrderId] and [contractOrderQty]
 * are null when unset, exactly as the web form posts them. In multi-product
 * mode the caller passes the product fields ([productId], [orderRate],
 * [totalOrder]) as empty strings and [contractOrderQty] as null — the real
 * lines travel in `items[]`.
 */
data class OrderHeader(
    val branchId: String,
    /** Ledger id, or "" when the order type is Stock ("3"). */
    val orderFor: String,
    val productId: String,
    val orderNumber: String,
    val refOrderId: String?,
    val deliveryLocation: String,
    /** yyyy-MM-dd. */
    val orderDate: String,
    /** yyyy-MM-dd. */
    val lastDeliveryDate: String,
    val orderRate: String,
    val contractOrderQty: String?,
    val totalOrder: String,
    /** "1" Purchase / "2" Sales / "3" Stock. */
    val orderType: String,
    /** "1" Active / "2" Inactive. */
    val status: String,
    val notes: String,
)

/**
 * Backs the Create Order form: the reference-order search and the store POST.
 * The backend answers errors as a `success:false` envelope (sometimes on a
 * non-2xx status, depending on which `notFound()` helper is loaded), so both
 * the body and the error body are read — the JSON `success` field is the
 * verdict, never the HTTP status. A 401 sets [Resource.Error.isUnauthorized].
 */
class OrderRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /**
     * Reference-order search (`invoice/order/search?q=&order_type=`). The
     * caller passes the OPPOSITE order type (a purchase references a sales
     * order and vice versa). Each option: value=id, label=order_number,
     * sublabel = customer • product. Blank queries short-circuit to empty.
     */
    suspend fun searchReferenceOrders(
        query: String,
        orderType: String,
    ): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Resource.Success(emptyList())
        safeCall {
            val response = reportApi.get(
                "invoice/order/search",
                mapOf("q" to q, "order_type" to orderType),
            )
            response.unauthorizedOrNull()?.let { return@safeCall it }
            // notFound() maps "no match" to a 404/201 — that's an empty result.
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
                        // label_2 = order_for (customer), label_3 = product name.
                        sublabel = listOfNotNull(o.str("label_2"), o.str("label_3"))
                            .joinToString("  •  ")
                            .ifBlank { null },
                    )
                }
            )
        }
    }

    /**
     * Order store (`invoice/order/store`). Header values go as strings exactly
     * as typed, like the web form. [items] is non-null only in multi-product
     * mode: a flat array where a blank rate becomes a literal 0 and a blank
     * contract quantity becomes null; the caller sends only lines that carry a
     * product. Returns the backend's confirmation message.
     *
     * ⚠️ Posts a REAL order server-side — never auto-retry.
     */
    suspend fun submitOrder(
        header: OrderHeader,
        items: List<OrderProductLine>?,
    ): Resource<String> = withContext(ioDispatcher) {
        safeCall {
            val body = JsonObject().apply {
                addProperty("branch_id", header.branchId)
                addProperty("order_for", header.orderFor)
                addProperty("product_id", header.productId)
                addProperty("order_number", header.orderNumber)
                header.refOrderId?.let { addProperty("ref_order_id", it) }
                    ?: add("ref_order_id", JsonNull.INSTANCE)
                addProperty("delivery_location", header.deliveryLocation)
                addProperty("order_date", header.orderDate)
                addProperty("last_delivery_date", header.lastDeliveryDate)
                addProperty("order_rate", header.orderRate)
                header.contractOrderQty?.let { addProperty("contract_order_qty", it) }
                    ?: add("contract_order_qty", JsonNull.INSTANCE)
                addProperty("total_order", header.totalOrder)
                addProperty("order_type", header.orderType)
                addProperty("status", header.status)
                addProperty("notes", header.notes)
                items?.let { lines ->
                    add("items", JsonArray().apply {
                        lines.forEach { line ->
                            add(JsonObject().apply {
                                addProperty("product_id", line.productId)
                                // The web sends `order_rate || 0` — blank is a
                                // numeric zero, not an empty string.
                                if (line.rate.isBlank()) addProperty("order_rate", 0)
                                else addProperty("order_rate", line.rate)
                                addProperty("total_order", line.qty)
                                if (line.contractQty.isBlank()) add("contract_order_qty", JsonNull.INSTANCE)
                                else addProperty("contract_order_qty", line.contractQty)
                            })
                        }
                    })
                }
            }

            val response = transactionApi.postObject("invoice/order/store", body)
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == HTTP_FORBIDDEN) {
                return@safeCall Resource.Error("You do not have permission for this action.")
            }

            // A rejected store may arrive as HTTP 400 with the envelope in the
            // error body — read whichever side carries the JSON.
            val root: JsonElement? = if (response.isSuccessful) {
                response.body()
            } else {
                runCatching {
                    response.errorBody()?.string()?.let(JsonParser::parseString)
                }.getOrNull()
            }
            val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall if (response.isSuccessful) {
                    Resource.Error("Invalid response from server.")
                } else {
                    Resource.Error("Server error (${response.code()}). Please try again later.")
                }

            val success = obj.get("success")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asBoolean
            val message = obj.get("message")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null }

            if (success != true) {
                // e.g. "The PO-1 already exists.", "This branch is set up for
                // one product per order." — the server's wording is the truth.
                return@safeCall Resource.Error(message ?: "The order could not be saved.")
            }
            Resource.Success(message ?: "Order created successfully.")
        }
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
