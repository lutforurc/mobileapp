package com.example.cashbookbd.data.repository

import com.example.cashbookbd.applist.AppListSpec
import com.example.cashbookbd.applist.CellFormat
import com.example.cashbookbd.applist.ListMethod
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import com.example.cashbookbd.core.AmountFormat

/**
 * One rendered row: a display [cells] string per configured column, plus the raw
 * id/status the Action toggle needs (both null/false when the spec has no toggle).
 */
data class AppListRow(
    val cells: List<String>,
    val id: String? = null,
    val statusOn: Boolean = false,
    /**
     * The id the edit screen opens on. Separate from [id]: the status toggle
     * posts the raw numeric id while the edit endpoints resolve a hashed one.
     */
    val editId: String? = null,
    /** The id the delete endpoint takes, when the spec declares a delete. */
    val deleteId: String? = null,
    /** The opening-stock entry's raw fields, when the spec declares it. */
    val opening: OpeningStockRow? = null,
)

/**
 * What the Product List's opening stock dialog needs from a row: the hashed
 * `product_id` the update endpoint resolves, plus the current opening qty
 * (`openingbalance`) and rate (`purchase`) to pre-fill, exactly the fallbacks
 * the web's inline inputs use.
 */
data class OpeningStockRow(
    val productId: String,
    val name: String,
    val qty: String,
    val rate: String,
    /** The voucher the opening stock came in on, when one is live. */
    val vrNo: String = "",
)

/** One figure of the summary strip, already computed and labelled. */
data class AppListSummaryValue(
    val label: String,
    val value: Double,
    val highlight: Boolean,
)

/** A page of list rows plus the server-side pagination meta. */
data class AppListResult(
    val rows: List<AppListRow>,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    /** The spec's summary tiles, resolved against the payload's `summary`. */
    val summary: List<AppListSummaryValue> = emptyList(),
)

/**
 * Fetches a read-only [AppListSpec] and maps the returned rows to display cells,
 * one string per configured column (dot-path keys supported). The row array is
 * located defensively (top-level array, `data.data`, or a paginator's
 * `data.data.data`). A 401 sets [Resource.Error.isUnauthorized].
 */
class AppListRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val ROW_ARRAY_KEYS = listOf("data", "rows", "items", "list", "results", "users")
    }

    suspend fun fetch(
        spec: AppListSpec,
        page: Int = 1,
        perPage: Int = spec.perPage,
    ): Resource<AppListResult> = withContext(ioDispatcher) {
        val params = if (spec.paginated) {
            spec.params + mapOf(spec.pageParam to page.toString(), spec.perPageParam to perPage.toString())
        } else {
            spec.params
        }
        try {
            val response: Response<JsonElement> = when (spec.method) {
                ListMethod.GET -> api.get(spec.endpoint, params)
                ListMethod.POST -> api.post(spec.endpoint, params)
            }
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission to view this."
                )
            }
            if (!response.isSuccessful && response.code() != 201) {
                // The stock-alert endpoints answer an empty result as HTTP 404
                // with the `notFound` envelope — an empty list, not a failure.
                if (isEmptyEnvelope(response)) {
                    return@withContext Resource.Success(AppListResult(emptyList()))
                }
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            Resource.Success(parse(response.body(), spec))
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

    /**
     * Flips a row's status via the spec's [AppListSpec.statusToggle]. The server
     * reports failure in the JSON `success` field, so a 200 alone isn't success.
     */
    suspend fun setStatus(spec: AppListSpec, id: String, on: Boolean): Resource<Unit> =
        withContext(ioDispatcher) {
            val toggle = spec.statusToggle
                ?: return@withContext Resource.Error("This list has no status action.")
            try {
                // Path-id endpoints (labour-setup) take the id in the route and
                // only the status in the body; the rest post both as fields.
                val response = if (toggle.idInPath) {
                    api.post("${toggle.endpoint}/$id", mapOf("status" to if (on) "1" else "0"))
                } else {
                    api.post(
                        toggle.endpoint,
                        mapOf("id" to id, "status" to if (on) "1" else "0"),
                    )
                }
                when (response.code()) {
                    HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> return@withContext Resource.Error(
                        "You do not have permission to change this."
                    )
                }
                if (!response.isSuccessful) {
                    // A refusal may ride a real error status (labour-setup
                    // answers 404/422 with {success:false, message}) — prefer
                    // the server's own reason to a bare status code.
                    errorMessage(response)?.let { return@withContext Resource.Error(it) }
                    return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                val success = body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                if (success == false) {
                    val message = body.get("message")?.takeUnless { it.isJsonNull }?.asString
                    return@withContext Resource.Error(message ?: "Could not update the status.")
                }
                Resource.Success(Unit)
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

    /**
     * Deletes a row via the spec's [AppListSpec.deleteAction] — an empty POST to
     * `{endpointBase}/{id}`. The server refuses dependent rows with a message
     * ("There are N projects associated with this area…"), surfaced verbatim.
     */
    suspend fun delete(spec: AppListSpec, id: String): Resource<Unit> =
        withContext(ioDispatcher) {
            val action = spec.deleteAction
                ?: return@withContext Resource.Error("This list has no delete action.")
            try {
                // Body-style deletes carry the id as a field (hashed ids can
                // hold "/" or "+"); the rest append it to the path.
                val response = if (action.bodyKey != null) {
                    api.post(action.endpointBase, mapOf(action.bodyKey to id))
                } else {
                    api.post("${action.endpointBase}/$id", emptyMap())
                }
                when (response.code()) {
                    HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> return@withContext Resource.Error(
                        "You do not have permission to delete this."
                    )
                }
                if (!response.isSuccessful && response.code() != 201) {
                    // Refusals can arrive at a real 422 ("This category has N
                    // labour item(s) under it…") — surface that reason verbatim.
                    errorMessage(response)?.let { return@withContext Resource.Error(it) }
                    return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                val success = body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                if (success == false) {
                    val message = body.get("message")?.takeUnless { it.isJsonNull }?.asString
                    return@withContext Resource.Error(message ?: "Could not delete the record.")
                }
                Resource.Success(Unit)
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

    /** The `message` inside a non-2xx error body, when the server sent one. */
    private fun errorMessage(response: Response<JsonElement>): String? = try {
        response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { raw ->
            com.google.gson.JsonParser.parseString(raw)
                .takeIf { it.isJsonObject }?.asJsonObject
                ?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    /** True when a non-2xx body is the backend's `notFound` envelope (empty result). */
    private fun isEmptyEnvelope(response: Response<JsonElement>): Boolean = try {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) {
            false
        } else {
            val obj = com.google.gson.JsonParser.parseString(raw)
                .takeIf { it.isJsonObject }?.asJsonObject
            obj?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false
        }
    } catch (_: Exception) {
        false
    }

    /** Reads a status field that may arrive as 1/0, "1"/"0" or true/false. */
    private fun isOn(element: JsonElement?): Boolean {
        val text = element?.takeIf { it.isJsonPrimitive }?.asString ?: return false
        return text.equals("true", ignoreCase = true) || text.toDoubleOrNull()?.let { it != 0.0 } == true
    }

    private fun parse(root: JsonElement?, spec: AppListSpec): AppListResult {
        if (root == null) return AppListResult(emptyList())
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return AppListResult(emptyList())
        }
        val payload = unwrap(root)
        // A Laravel paginator carries current_page/last_page/total alongside its rows.
        val paginator = payload.takeIf { it.isJsonObject }?.asJsonObject
            ?.takeIf { it.has("current_page") }
        val array = locateRows(payload) ?: return AppListResult(emptyList())
        val toggle = spec.statusToggle
        val edit = spec.editAction
        val delete = spec.deleteAction
        val summary = summaryValues(payload, spec)
        val rows = array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            AppListRow(
                cells = spec.columns.map { col ->
                    val primary = if (col.format == CellFormat.ORDER_OUTSTANDING) {
                        formatOrderOutstanding(obj, col.key)
                    } else {
                        format(dotGet(obj, col.key), numeric = col.numeric, valueMap = col.valueMap, cellFormat = col.format)
                    }
                    val subline = col.sublineKey
                        ?.let { format(dotGet(obj, it), numeric = false) }
                        ?.takeIf { it.isNotBlank() && it != "-" }
                    when {
                        subline == null -> primary
                        // "-" only when both fields are empty, like the web.
                        primary.isBlank() || primary == "-" -> subline
                        else -> "$primary\n$subline"
                    }
                },
                id = toggle?.let { dotGet(obj, it.idKey)?.asString },
                statusOn = toggle?.let { isOn(dotGet(obj, it.statusKey)) } ?: false,
                editId = edit?.let { dotGet(obj, it.idKey)?.asString },
                deleteId = delete?.let { dotGet(obj, it.idKey)?.asString },
                opening = if (spec.openingStock) obj.toOpeningStockRow() else null,
            )
        }
        return if (paginator != null) {
            AppListResult(
                rows = rows,
                currentPage = paginator.int("current_page", 1),
                lastPage = paginator.int("last_page", 1),
                total = paginator.int("total", rows.size),
                summary = summary,
            )
        } else {
            AppListResult(rows = rows, total = rows.size, summary = summary)
        }
    }

    /**
     * The spec's summary tiles against the payload's `summary` object (the
     * Orders list puts it beside the paginator, api 064f58f8). Missing keys
     * are nought — never re-summed from the page rows.
     */
    private fun summaryValues(payload: JsonElement, spec: AppListSpec): List<AppListSummaryValue> {
        if (spec.summaryTiles.isEmpty()) return emptyList()
        val summary = payload.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("summary")?.takeIf { it.isJsonObject }?.asJsonObject
        fun figure(key: String): Double =
            summary?.get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
                ?.asString?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        return spec.summaryTiles.map { tile ->
            AppListSummaryValue(
                label = tile.label,
                value = tile.plus.sumOf(::figure) - tile.minus.sumOf(::figure),
                highlight = tile.highlight,
            )
        }
    }

    /**
     * The web's Orders money cell (d1d38e06 / 925b7c4a): the outstanding
     * balance on the side `order_type` names, the other side nought, and the
     * net beneath — all as magnitudes. Three short lines so a phone column can
     * carry what the web spreads under a three-line heading.
     */
    private fun formatOrderOutstanding(obj: JsonObject, key: String): String {
        val outstanding = dotGet(obj, key)?.takeIf { it.isJsonPrimitive }?.asString
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val isPurchase = dotGet(obj, "order_type")?.takeIf { it.isJsonPrimitive }?.asString
            ?.toDoubleOrNull()?.toInt() == 1
        val po = if (isPurchase) outstanding else 0.0
        val dO = if (isPurchase) 0.0 else outstanding
        fun line(prefix: String, value: Double) = "$prefix ${AmountFormat.formatOrDash(kotlin.math.abs(value))}"
        return listOf(line("PO", po), line("DO", dO), line("Net", po - dO)).joinToString("\n")
    }

    /** The opening-stock fields, or null when the row carries no `product_id`. */
    private fun JsonObject.toOpeningStockRow(): OpeningStockRow? {
        val productId = dotGet(this, "product_id")?.asString?.takeIf { it.isNotBlank() } ?: return null
        return OpeningStockRow(
            productId = productId,
            name = dotGet(this, "name")?.asString.orEmpty(),
            qty = dotGet(this, "openingbalance")?.asString ?: "0",
            rate = dotGet(this, "purchase")?.asString ?: "0",
            // Joined on status = 1 server-side, so a trashed voucher shows blank.
            vrNo = dotGet(this, "opening_vr_no")?.asString.orEmpty(),
        )
    }

    private fun JsonObject.int(key: String, default: Int): Int =
        get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt() ?: default

    /** Peels the `data` / `data.data` envelope. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** Finds the row array (the payload itself, or a paginator's `data`). */
    private fun locateRows(payload: JsonElement): JsonArray? {
        if (payload.isJsonArray) return payload.asJsonArray
        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            for (key in ROW_ARRAY_KEYS) {
                val value = obj.get(key)?.takeUnless { it.isJsonNull }
                if (value != null && value.isJsonArray) return value.asJsonArray
            }
        }
        return null
    }

    /** Reads a possibly-nested field via a dot path (e.g. `action_by_user.name`). */
    private fun dotGet(obj: JsonObject, path: String): JsonElement? {
        var current: JsonElement = obj
        for (part in path.split(".")) {
            val o = current.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            current = o.get(part)?.takeUnless { it.isJsonNull } ?: return null
        }
        return current
    }

    /**
     * Renders one cell. Only [numeric] columns get thousands separators — a text
     * column may hold digits that are an identifier rather than a quantity
     * (mobile number, national ID), and those must survive verbatim.
     */
    private fun format(
        element: JsonElement?,
        numeric: Boolean,
        valueMap: Map<String, String> = emptyMap(),
        cellFormat: CellFormat? = null,
    ): String = when {
        element == null || element.isJsonNull -> "-"
        cellFormat == CellFormat.DATE_DMY -> formatDayMonthYear(element)
        cellFormat == CellFormat.DAY_SPAN -> formatDaySpan(element)
        element.isJsonPrimitive -> {
            val text = element.asString
            // Status-style codes ("1" -> "Active", "true" -> "Active") first —
            // they are labels, not amounts.
            val mapped = valueMap[text]
            val number = text.replace(",", "").toDoubleOrNull()
            when {
                mapped != null -> mapped
                number == null -> text
                number == 0.0 -> "-"
                numeric -> AmountFormat.format(number)
                else -> text
            }
        }
        element.isJsonArray -> "${element.asJsonArray.size()} item(s)"
        else -> "…"
    }

    /**
     * dd/MM/yyyy whichever way round the date arrives (the web's
     * formatDayMonthYear). The API sends "2023-02-18" into a table where every
     * other date is day-first; a timestamp is trimmed to its date. Four-digit
     * year on purpose — on a row saying a product has not moved in three
     * years, 18/02/23 invites the reader to wonder which century.
     */
    private fun formatDayMonthYear(element: JsonElement): String {
        val text = element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.take(10) ?: return "-"
        if (text.isEmpty()) return "-"
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return "${d.padStart(2, '0')}/${mo.padStart(2, '0')}/$y"
        }
        Regex("""^(\d{1,2})/(\d{1,2})/(\d{2,4})$""").find(text)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            return "${d.padStart(2, '0')}/${mo.padStart(2, '0')}/$year"
        }
        return text
    }

    /**
     * A day count said the way people say it (the web's formatDaySpan):
     * 1274 → "3 Year 5 Month 29 Day", 45 → "1 Month 15 Day". Converted from
     * the count with a 365/30 year and month — never from the two dates — so
     * it always agrees with the figure the report worked out. Nothing recorded
     * is a dash, not "0 Day".
     */
    private fun formatDaySpan(element: JsonElement): String {
        val raw = element.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return "-"
        if (raw.isEmpty()) return "-"
        val days = raw.replace(",", "").toDoubleOrNull()?.let { kotlin.math.floor(it).toLong() } ?: return "-"
        if (days < 0) return "-"
        if (days == 0L) return "0 Day"
        val years = days / 365
        val afterYears = days % 365
        val months = afterYears / 30
        val remaining = afterYears % 30
        return buildList {
            if (years > 0) add("$years Year")
            if (months > 0) add("$months Month")
            if (remaining > 0) add("$remaining Day")
        }.joinToString(" ")
    }
}
