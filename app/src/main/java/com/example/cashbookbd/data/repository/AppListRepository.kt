package com.example.cashbookbd.data.repository

import com.example.cashbookbd.applist.AppListSpec
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
)

/** A page of list rows plus the server-side pagination meta. */
data class AppListResult(
    val rows: List<AppListRow>,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
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
            spec.params + mapOf("page" to page.toString(), "per_page" to perPage.toString())
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
                val response = api.post(
                    toggle.endpoint,
                    mapOf("id" to id, "status" to if (on) "1" else "0"),
                )
                when (response.code()) {
                    HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> return@withContext Resource.Error(
                        "You do not have permission to change this."
                    )
                }
                if (!response.isSuccessful) {
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
        val rows = array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            AppListRow(
                cells = spec.columns.map { col -> format(dotGet(obj, col.key), numeric = col.numeric) },
                id = toggle?.let { dotGet(obj, it.idKey)?.asString },
                statusOn = toggle?.let { isOn(dotGet(obj, it.statusKey)) } ?: false,
                editId = edit?.let { dotGet(obj, it.idKey)?.asString },
            )
        }
        return if (paginator != null) {
            AppListResult(
                rows = rows,
                currentPage = paginator.int("current_page", 1),
                lastPage = paginator.int("last_page", 1),
                total = paginator.int("total", rows.size),
            )
        } else {
            AppListResult(rows = rows, total = rows.size)
        }
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
    private fun format(element: JsonElement?, numeric: Boolean): String = when {
        element == null || element.isJsonNull -> "-"
        element.isJsonPrimitive -> {
            val text = element.asString
            val number = text.replace(",", "").toDoubleOrNull()
            when {
                number == null -> text
                number == 0.0 -> "-"
                numeric -> AmountFormat.format(number)
                else -> text
            }
        }
        element.isJsonArray -> "${element.asJsonArray.size()} item(s)"
        else -> "…"
    }
}
