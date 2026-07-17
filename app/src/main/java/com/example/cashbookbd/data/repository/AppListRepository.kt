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
import java.text.DecimalFormat

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
        private val amountFormat = DecimalFormat("#,##0.##")
    }

    suspend fun fetch(spec: AppListSpec): Resource<List<List<String>>> = withContext(ioDispatcher) {
        try {
            val response: Response<JsonElement> = when (spec.method) {
                ListMethod.GET -> api.get(spec.endpoint, spec.params)
                ListMethod.POST -> api.post(spec.endpoint, spec.params)
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

    private fun parse(root: JsonElement?, spec: AppListSpec): List<List<String>> {
        if (root == null) return emptyList()
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return emptyList()
        }
        val array = locateRows(unwrap(root)) ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            spec.columns.map { col -> format(dotGet(obj, col.key)) }
        }
    }

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

    private fun format(element: JsonElement?): String = when {
        element == null || element.isJsonNull -> "-"
        element.isJsonPrimitive -> {
            val text = element.asString
            val number = text.replace(",", "").toDoubleOrNull()
            when {
                number == null -> text
                number == 0.0 -> "-"
                else -> amountFormat.format(number)
            }
        }
        element.isJsonArray -> "${element.asJsonArray.size()} item(s)"
        else -> "…"
    }
}
