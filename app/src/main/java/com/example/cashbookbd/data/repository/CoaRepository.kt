package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
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

/** The dropdown choices the Add/Edit CoA Level 3 form needs. */
data class CoaL3FormOptions(
    /** CoA Level 2 heads (`coal2/coal2-list`). */
    val l2Options: List<SelectorOption>,
    /** Business sources; empty when the tenant has none (the select is hidden). */
    val sources: List<SelectorOption>,
)

/** The stored CoA Level 3 fields the edit form prefills (`coal3/{id}`). */
data class CoaL3Details(
    val l2Id: String,
    val sourceId: String?,
    val name: String,
)

/**
 * Backs the Add/Edit CoA Level 3 form — a port of the web's /coal3/add-coal3
 * and /coal3/edit-coal3/:id screens.
 */
class CoaRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /**
     * Loads the form's dropdowns: the L2 heads from `coal2/coal2-list` and the
     * sources that ride on the `coal3/coal3-list` payload. Sources are
     * best-effort — a failure just hides the Source select.
     */
    suspend fun loadFormOptions(): Resource<CoaL3FormOptions> = withContext(ioDispatcher) {
        try {
            val l2Resp = api.get(
                "coal2/coal2-list",
                mapOf("page" to "1", "per_page" to "500", "search" to ""),
            )
            if (l2Resp.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val l2Json = l2Resp.jsonBody()
            val rejected = l2Json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!l2Resp.isSuccessful && l2Resp.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    l2Json?.message() ?: "Server error (${l2Resp.code()}). Please try again later."
                )
            }
            // Paginator at data.data; the rows ({serial, id, name}) at data.data.data.
            val l2Options = l2Json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.toOptions().orEmpty()

            // The CoA L3 list payload carries an extra "sources" array.
            val sources = runCatching {
                val resp = api.get(
                    "coal3/coal3-list",
                    mapOf("page" to "1", "per_page" to "1", "search" to ""),
                )
                val data = resp.jsonBody()?.getAsJsonObject("data")
                val inner = data?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                (inner?.get("sources") ?: data?.get("sources"))
                    ?.takeIf { it.isJsonArray }?.asJsonArray?.toOptions().orEmpty()
            }.getOrDefault(emptyList())

            Resource.Success(CoaL3FormOptions(l2Options = l2Options, sources = sources))
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** The stored model for the edit prefill (`coal3/{id}` → data.data). */
    suspend fun loadCoaL3(id: String): Resource<CoaL3Details> = withContext(ioDispatcher) {
        try {
            val response = api.get("coal3/$id", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            val model = json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("CoA Level 3 not found.")
            val l2Id = model.str("acc_coa_level2_id")
                ?: return@withContext Resource.Error("CoA Level 3 not found.")
            Resource.Success(
                CoaL3Details(
                    l2Id = l2Id,
                    sourceId = model.str("acc_source_id"),
                    name = model.str("name").orEmpty(),
                )
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Creates (`coal3/store`) or updates (`coal3/update/{id}`) a CoA Level 3.
     * The controller reads the L2 and source under two names each, so both
     * aliases are sent; `code` mirrors `name`, as on the web. A null [sourceId]
     * is simply omitted, which Laravel reads as null.
     */
    suspend fun saveCoaL3(
        coaId: String?,
        l2Id: String,
        sourceId: String?,
        name: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val trimmed = name.trim()
        val body = buildMap {
            put("coal2_id", l2Id)
            put("l2_id", l2Id)
            sourceId?.takeIf { it.isNotBlank() }?.let {
                put("source_id", it)
                put("acc_source_id", it)
            }
            put("code", trimmed)
            put("name", trimmed)
        }
        val path = if (coaId == null) "coal3/store" else "coal3/update/$coaId"
        try {
            val response = api.post(path, body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A duplicate name / validation failure arrives as success:false.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "CoA Level 3 saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Maps an array of `{id, name}` rows to [SelectorOption]s. */
    private fun JsonArray.toOptions(): List<SelectorOption> = mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = obj.str("id") ?: return@mapNotNull null
        SelectorOption(id, obj.str("name") ?: id)
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a write was rejected: `message`, `error.message`, or a field error. */
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
