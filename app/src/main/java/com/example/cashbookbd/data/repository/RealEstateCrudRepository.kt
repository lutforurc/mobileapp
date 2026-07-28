package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.RealEstateApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Backs the config-driven Real Estate add/edit forms
 * ([com.example.cashbookbd.realestate.RealEstateCrudForms]): the typeahead DDL
 * searches, the edit-record load and the store/update POSTs.
 *
 * The Laravel side wraps everything in the `foundData`/`notFound` envelope with
 * the payload at `data.data`, and reports failures as `{"success":false,
 * "message"}` under HTTP 200/201 — so every parse branches on the `success`
 * field, not the status code. Validation failures are real Laravel 422 error
 * bags (`{message, errors}`), surfaced as the first field error.
 */
class RealEstateCrudRepository(
    private val api: RealEstateApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Searches one of the real-estate DDL endpoints (`?q=` keyword). Options
     * arrive at `data.data` as `[{value, label, label_2, label_3, …}]`; an empty
     * result may come back as `success:false` ("No … found."), which is an empty
     * list here, never an error.
     */
    suspend fun searchDdl(path: String, query: String): Resource<List<SelectorOption>> = request {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@request Resource.Success(emptyList())

        val response = api.get(path, mapOf("q" to trimmed))
        checkHttp(response)?.let { return@request it }
        val root = response.body() ?: return@request Resource.Error("Invalid response from server.")
        if (root.successFlag() == false) return@request Resource.Success(emptyList())

        val options = unwrap(root).rows().mapNotNull { row ->
            val obj = row.asObjectOrNull() ?: return@mapNotNull null
            val value = obj.text("value") ?: return@mapNotNull null
            val sublabel = listOfNotNull(obj.text("label_2"), obj.text("label_3"))
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            SelectorOption(
                id = value,
                label = obj.text("label").orEmpty(),
                sublabel = sublabel.ifBlank { null },
            )
        }
        Resource.Success(options)
    }

    /** GET `editPath/{id}` — the record the edit form prefills from, at `data.data`. */
    suspend fun fetchEditRecord(editPath: String, id: String): Resource<JsonObject> = request {
        val response = api.get("$editPath/$id", emptyMap())
        checkHttp(response)?.let { return@request it }
        val root = response.body() ?: return@request Resource.Error("Invalid response from server.")
        if (root.successFlag() == false) {
            return@request Resource.Error(root.message() ?: "Record not found.")
        }
        unwrap(root).asObjectOrNull()
            ?.let { Resource.Success(it) }
            ?: Resource.Error("Record not found.")
    }

    /**
     * POSTs a store/update/upsert body. The outcome message comes from wherever
     * the backend put it: a 422's error bag (first field error wins), the
     * notFound envelope's `message` at HTTP 200/201, or the success `message`
     * (else [fallback]).
     */
    suspend fun save(path: String, body: JsonObject, fallback: String): Resource<String> = request {
        val response = api.post(path, body)
        val root = response.body() ?: response.errorBody()?.charStream()?.let { reader ->
            runCatching { JsonParser.parseReader(reader) }.getOrNull()
        }
        val obj = root?.asObjectOrNull()
        // Laravel 422 error bag: {message, errors: {field: [msg, …]}}.
        val errorMessage = obj?.firstBagError() ?: obj?.message()

        checkHttp(response)?.let { httpError ->
            // Prefer the server's own reason (the 422 bag / a guarded message) —
            // except on a 401, whose isUnauthorized flag must survive so the UI
            // can force re-login.
            return@request if (!errorMessage.isNullOrBlank() && !httpError.isUnauthorized) {
                Resource.Error(errorMessage)
            } else {
                httpError
            }
        }

        if (obj?.successFlag() == false) {
            return@request Resource.Error(
                errorMessage ?: "Something went wrong. Please try again.",
            )
        }
        Resource.Success(obj?.message()?.takeIf { it.isNotBlank() } ?: fallback)
    }

    // ---- Shared plumbing ----

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }

    /** Runs [block] on IO with the shared error mapping every repository uses. */
    private suspend fun <T> request(block: suspend () -> Resource<T>): Resource<T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                when (e.code()) {
                    HTTP_UNAUTHORIZED -> Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> Resource.Error("You do not have permission for this action.")
                    else -> Resource.Error("Server error (${e.code()}). Please try again later.")
                }
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /** Maps 401/403/5xx (and any other non-2xx) to a [Resource.Error]; null when fine. */
    private fun checkHttp(response: Response<JsonElement>): Resource.Error? = when {
        response.code() == HTTP_UNAUTHORIZED -> Resource.Error(
            "Your session has expired. Please log in again.",
            isUnauthorized = true,
        )
        response.code() == HTTP_FORBIDDEN ->
            Resource.Error("You do not have permission for this action.")
        !response.isSuccessful ->
            Resource.Error("Server error (${response.code()}). Please try again later.")
        else -> null
    }

    /** Peels the `data` / `data.data` envelope produced by the backend helpers. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** The payload as a row list: itself when an array, else empty. */
    private fun JsonElement.rows(): List<JsonElement> =
        if (isJsonArray) asJsonArray.toList() else emptyList()

    private fun JsonElement.successFlag(): Boolean? = asObjectOrNull()
        ?.get("success")?.takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive }?.asBoolean

    private fun JsonElement.message(): String? = asObjectOrNull()?.text("message")

    /** The first message of a Laravel validation bag (`errors: {field: [msg]}`). */
    private fun JsonObject.firstBagError(): String? =
        get("errors")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()?.firstNotNullOfOrNull { (_, value) ->
                when {
                    value.isJsonArray -> value.asJsonArray.firstOrNull()
                        ?.takeIf { it.isJsonPrimitive }?.asString
                    value.isJsonPrimitive -> value.asString
                    else -> null
                }?.takeIf { it.isNotBlank() }
            }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
}
