package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder

/**
 * One pickable requisition item from `requisition/items` — the picker searches
 * products AND expense heads AND labour items in one list, so each option
 * carries which kind it is ([type], the server's `is_item_coa`).
 */
data class RequisitionItemOption(
    /** The item's id (`value`). */
    val id: String,
    /** Display name (`label`). */
    val name: String,
    /** "1" product item / "2" COA (expense head) / "3" labour item (`label_2`). */
    val type: String,
    /** The item's unit (`label_3`); "" when it has none. */
    val unit: String,
    /** The purchase price the form pre-fills (`label_4`); null when absent. */
    val purchasePrice: Double?,
)

/** One pending line of a requisition batch. Day/qty/price stay the typed strings. */
data class RequisitionLine(
    val item: RequisitionItemOption,
    val remarks: String,
    val day: String,
    val qty: String,
    val price: String,
) {
    /** day × qty × price — the server derives `req_total` from this per line. */
    val amount: Double
        get() = (day.toDoubleOrNull() ?: 0.0) *
            (qty.toDoubleOrNull() ?: 0.0) *
            (price.toDoubleOrNull() ?: 0.0)
}

/**
 * Backs the Requisition create form: the combined product/expense-head/labour
 * item search and the store call. A 401 sets [Resource.Error.isUnauthorized].
 */
class RequisitionRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /**
     * Item search (`requisition/items?q=`) → `{value, label, label_2, label_3,
     * label_4}` options. The backend expects 3+ characters (the UI enforces it).
     */
    suspend fun searchItems(query: String): Resource<List<RequisitionItemOption>> =
        withContext(ioDispatcher) {
            try {
                val response = api.get(
                    "requisition/items?q=" + URLEncoder.encode(query.trim(), "UTF-8"),
                )
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Resource.Error("Couldn't search items (${response.code()}).")
                }
                Resource.Success(parseItems(response.body()))
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                if (e.code() == HTTP_UNAUTHORIZED) {
                    Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
                } else {
                    Resource.Error("Server error (${e.code()}). Please try again later.")
                }
            } catch (e: Exception) {
                Resource.Error("Couldn't search items.")
            }
        }

    private fun parseItems(root: JsonElement?): List<RequisitionItemOption> {
        if (root == null) return emptyList()
        // Usually a bare array; peel data / data.data defensively.
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        val array = payload.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.text("value").ifBlank { return@mapNotNull null }
            RequisitionItemOption(
                id = id,
                name = o.text("label").ifBlank { id },
                type = o.text("label_2"),
                unit = o.text("label_3"),
                purchasePrice = o.text("label_4").replace(",", "").toDoubleOrNull(),
            )
        }
    }

    /**
     * Stores the requisition (`requisition/store`) — the web form's payload,
     * with each line carrying its picked option's `type` (stored server-side as
     * `is_item_coa`). The server derives `req_total` from Σ(day×qty×price).
     *
     * RESPONSE QUIRK: the endpoint returns HTTP 200 with an EMPTY body on
     * success (a missing `return` server-side), so any 2xx counts as success —
     * there is no voucher number to show. A parseable body with `success:false`
     * (or a non-2xx status) is the failure path.
     *
     * ⚠️ Posts a REAL requisition voucher server-side — never auto-retry.
     */
    suspend fun submit(
        startDate: String,
        endDate: String,
        notes: String,
        requisitionAmt: String,
        lines: List<RequisitionLine>,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val body = JsonObject().apply {
                addProperty("mtmId", "")
                addProperty("requisitionAmt", requisitionAmt)
                addProperty("notes", notes)
                addProperty("startDate", startDate)
                addProperty("endDate", endDate)
                add("currentProduct", JsonNull.INSTANCE)
                addProperty("searchInvoice", "")
                add("products", JsonArray().apply {
                    lines.forEachIndexed { index, line -> add(productRow(line, index)) }
                })
            }
            val response = api.postObject("requisition/store", body)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission for this action."
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            // A body may still carry an explicit failure; an empty body is success.
            val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            val success = obj?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
                val errorMessage = obj.getAsJsonObject("error")
                    ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
                return@withContext Resource.Error(
                    errorMessage ?: message ?: "The requisition could not be saved."
                )
            }
            Resource.Success("Requisition saved.")
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

    /** One `products[]` row, as the web form shapes it (day/qty/price as typed). */
    private fun productRow(line: RequisitionLine, index: Int): JsonObject = JsonObject().apply {
        // Distinct per-row client ids, like the web's Date.now() keys.
        addProperty("id", System.currentTimeMillis() + index)
        addProperty("product", line.item.id.toLongOrNull() ?: 0L)
        // CRITICAL: the picked option's label_2 — the server stores it as is_item_coa.
        addProperty("type", line.item.type)
        addProperty("product_name", line.item.name)
        addProperty("remarks", line.remarks)
        addProperty("unit", line.item.unit)
        addProperty("day", line.day)
        addProperty("qty", line.qty)
        addProperty("price", line.price)
    }

    /** The primitive at [key] as trimmed text; "" when absent/null/non-primitive. */
    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
}
