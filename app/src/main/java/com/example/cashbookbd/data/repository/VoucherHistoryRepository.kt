package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One "field: old → new" line of a voucher-history diff. */
data class VoucherChange(
    /** Dot-path of the changed field (nested objects flattened, e.g. "purchase_master.total"). */
    val path: String,
    val oldValue: String,
    val newValue: String,
)

/** One audit entry of a voucher's history (`history/information`). */
data class VoucherHistoryItem(
    val id: String,
    /** "Purchase Update" / "Invoice Update" / "Voucher Update", from the audited payload. */
    val title: String,
    val action: String,
    val actionBy: String,
    val createdAt: String,
    val branchName: String,
    val changes: List<VoucherChange>,
)

/**
 * Backs the Voucher History screen — a port of the web's
 * /vr-settings/voucher-history audit viewer. Posts the branch + voucher number
 * and maps each audit row's `changed_only` into display-ready diff lines.
 */
class VoucherHistoryRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."

        /** Placeholder for a missing/blank value in a diff line. */
        private const val BLANK = "-"

        /** Arrays longer than this collapse to "n item(s)" instead of listing values. */
        private const val MAX_INLINE_ARRAY = 3
    }

    /**
     * The voucher's audit trail. An unknown voucher arrives as notFound() —
     * success:false at 201 with a blank message — which maps to an empty list,
     * not an error, so the UI can show its own "no history" wording.
     */
    suspend fun loadHistory(branchId: Long, voucherNo: String): Resource<List<VoucherHistoryItem>> =
        withContext(ioDispatcher) {
            val body = JsonObject().apply {
                // The web sends the branch as a number, so this does too.
                addProperty("branch", branchId)
                addProperty("voucher_no", voucherNo.trim())
            }
            try {
                val response = api.postObject("history/information", body)
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
                }
                val json = response.jsonBody()
                // No history arrives as success:false (blank message) at 201.
                if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                    return@withContext Resource.Success(emptyList())
                }
                if (!response.isSuccessful && response.code() != 201) {
                    return@withContext Resource.Error(
                        json?.message() ?: "Server error (${response.code()}). Please try again later."
                    )
                }
                // Rows at data.data (tolerating data being the array itself).
                val rows = json?.get("data")?.let { data ->
                    when {
                        data.isJsonArray -> data.asJsonArray
                        data.isJsonObject -> data.asJsonObject.get("data")
                            ?.takeIf { it.isJsonArray }?.asJsonArray
                        else -> null
                    }
                }
                val items = rows?.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject?.toHistoryItem()
                }.orEmpty()
                Resource.Success(items)
            } catch (e: IOException) {
                Resource.Error(NO_NETWORK)
            } catch (e: HttpException) {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    private fun JsonObject.toHistoryItem(): VoucherHistoryItem? {
        val id = get("id")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val oldData = obj("old_data")
        val newData = obj("new_data")
        // The audited payload names the voucher family: purchase, sales, or plain.
        val title = when {
            newData?.has("purchase_master") == true || oldData?.has("purchase_master") == true ->
                "Purchase Update"
            newData?.has("sales_master") == true || oldData?.has("sales_master") == true ->
                "Invoice Update"
            else -> "Voucher Update"
        }
        return VoucherHistoryItem(
            id = id,
            title = title,
            action = str("action").orEmpty(),
            actionBy = obj("action_by_user")?.str("name") ?: str("action_by").orEmpty(),
            createdAt = str("created_at").orEmpty(),
            branchName = obj("branch_info")?.str("name").orEmpty(),
            changes = buildChanges(obj("changed_only"), oldData, newData),
        )
    }

    /**
     * Flattens `changed_only` into "path: old → new" lines. Nested objects
     * become dot-path keys; the old/new values are looked up at the same path
     * in `old_data`/`new_data`; arrays beyond a few items collapse to a count.
     */
    private fun buildChanges(
        changed: JsonObject?,
        oldData: JsonObject?,
        newData: JsonObject?,
    ): List<VoucherChange> {
        changed ?: return emptyList()
        val out = mutableListOf<VoucherChange>()
        collectChanges(changed, "", out, oldData, newData)
        return out
    }

    private fun collectChanges(
        element: JsonElement,
        path: String,
        out: MutableList<VoucherChange>,
        oldData: JsonObject?,
        newData: JsonObject?,
    ) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            // An {old, new} pair is already a diff leaf.
            if (obj.size() == 2 && obj.has("old") && obj.has("new")) {
                out += VoucherChange(path, display(obj.get("old")), display(obj.get("new")))
                return
            }
            obj.entrySet().forEach { (key, value) ->
                collectChanges(value, if (path.isEmpty()) key else "$path.$key", out, oldData, newData)
            }
            return
        }
        // Primitive/null/array leaf: old from old_data, new from new_data (or
        // the changed value itself when new_data lacks the path).
        out += VoucherChange(
            path = path,
            oldValue = display(oldData.valueAt(path)),
            newValue = display(newData.valueAt(path) ?: element),
        )
    }

    /** The element at a dot-path ("purchase_master.total"), or null. */
    private fun JsonObject?.valueAt(path: String): JsonElement? {
        var current: JsonElement? = this ?: return null
        for (segment in path.split('.')) {
            current = current?.takeIf { it.isJsonObject }?.asJsonObject?.get(segment) ?: return null
        }
        return current
    }

    /** A value rendered for a diff line; big arrays collapse to a count. */
    private fun display(element: JsonElement?): String = when {
        element == null || element.isJsonNull -> BLANK
        element.isJsonPrimitive -> element.asString.takeIf { it.isNotBlank() } ?: BLANK
        element.isJsonArray -> {
            val array = element.asJsonArray
            val primitives = array.all { it.isJsonPrimitive }
            if (primitives && array.size() in 1..MAX_INLINE_ARRAY) {
                array.joinToString(", ") { it.asString }
            } else {
                "${array.size()} item(s)"
            }
        }
        else -> "{…}"
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    /**
     * A nested object, tolerating the audit columns arriving as still-encoded
     * JSON text (old_data/new_data/changed_only are JSON columns server-side).
     */
    private fun JsonObject.obj(key: String): JsonObject? {
        val element = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (element.isJsonObject) return element.asJsonObject
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            return runCatching { JsonParser.parseString(element.asString) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject
        }
        return null
    }

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a call was rejected: `message`, `error.message`, or a field error. */
    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: getAsJsonObject("error")?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            ?: firstFieldError()

    /** Reads `errors: {field: ["reason", …]}` — Laravel's per-field validation. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = getAsJsonObject("errors") ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }
}
