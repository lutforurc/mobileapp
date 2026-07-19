package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
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

/** The pickers the Add Branch form needs, from `settings/get-branch-settings`. */
data class BranchFormOptions(
    val branchTypes: List<SelectorOption>,
    val businessTypes: List<SelectorOption>,
    val paperSizes: List<SelectorOption>,
)

/**
 * A branch loaded for editing: [fields] is every scalar the server sent back for
 * it, kept verbatim so an update can return the ones this form does not show.
 */
data class BranchEditData(
    val fields: Map<String, String>,
    val branchTypes: List<SelectorOption>,
    val businessTypes: List<SelectorOption>,
    val paperSizes: List<SelectorOption>,
)

/**
 * Branch create/update and their picker sources.
 *
 * Both take the form's values keyed by API name, so the wizard's configuration
 * in `BranchForm` is the only place field names are written down.
 */
class BranchRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Loads the branch-type, business-type and paper-size pickers. */
    suspend fun loadFormOptions(): Resource<BranchFormOptions> = withContext(ioDispatcher) {
        call("settings/get-branch-settings", emptyMap()) { body ->
            // foundData nests the payload under data.data.
            val payload = body?.getAsJsonObject("data")?.getAsJsonObject("data")
            BranchFormOptions(
                branchTypes = payload.options("branchType"),
                businessTypes = payload.options("businessType"),
                paperSizes = payload.options("paperSize"),
            )
        }
    }

    /** Creates a branch from the wizard's values. Returns the server's message. */
    suspend fun store(values: Map<String, String>): Resource<String> = withContext(ioDispatcher) {
        call("branch/branch-store", values.trimmed()) { responseBody ->
            responseBody?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: "Branch saved successfully"
        }
    }

    /** Loads one branch's current values plus the two pickers, for editing. */
    suspend fun loadForEdit(branchId: String): Resource<BranchEditData> = withContext(ioDispatcher) {
        get("branch/branch-edit/$branchId") { body ->
            val payload = body?.getAsJsonObject("data")?.getAsJsonObject("data")
            val branch = payload?.get("branch")?.takeIf { it.isJsonObject }?.asJsonObject
            BranchEditData(
                fields = branch.scalars(),
                branchTypes = payload.options("branchType"),
                businessTypes = payload.options("businessType"),
                paperSizes = payload.options("paperSize"),
            )
        }
    }

    /**
     * Updates a branch.
     *
     * `branch/branch-update` rewrites every column and meta row from the request,
     * defaulting anything absent to 0/null — so a partial payload silently wipes
     * settings this form never shows. [existing] is therefore posted back whole,
     * with only the edited fields overwritten. `branch_id` (the hashed id the
     * endpoint resolves on) rides along inside [existing].
     */
    suspend fun update(
        existing: Map<String, String>,
        values: Map<String, String>,
    ): Resource<String> = withContext(ioDispatcher) {
        call("branch/branch-update", (existing + values).trimmed()) { responseBody ->
            responseBody?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: "Branch updated successfully"
        }
    }

    private fun Map<String, String>.trimmed(): Map<String, String> =
        mapValues { (_, value) -> value.trim() }

    /**
     * Every scalar field of a JSON object as strings. Nested objects and arrays
     * are dropped: they are relations the update endpoint does not read, and a
     * form-encoded body cannot carry them anyway.
     */
    private fun JsonObject?.scalars(): Map<String, String> {
        val obj = this ?: return emptyMap()
        return obj.entrySet().mapNotNull { (key, value) ->
            when {
                value == null || value.isJsonNull -> key to ""
                value.isJsonPrimitive -> key to value.asString
                else -> null
            }
        }.toMap()
    }

    /** GETs and maps the body via [onSuccess]. */
    private suspend fun <T> get(
        path: String,
        onSuccess: (JsonObject?) -> T,
    ): Resource<T> = request(onSuccess) { api.get(path, emptyMap()) }

    /**
     * POSTs and maps the body via [onSuccess].
     *
     * Failures are read from the JSON `success` field rather than the HTTP
     * status: the API's `notFound()` helper answers **201** with
     * `success: false` (a validation error arrives that way), so status alone
     * would read as a success.
     */
    private suspend fun <T> call(
        path: String,
        body: Map<String, String>,
        onSuccess: (JsonObject?) -> T,
    ): Resource<T> = request(onSuccess) { api.post(path, body) }

    private suspend fun <T> request(
        onSuccess: (JsonObject?) -> T,
        send: suspend () -> Response<JsonElement>,
    ): Resource<T> = try {
        val response = send()
        when {
            response.code() == HTTP_UNAUTHORIZED ->
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)

            response.code() == HTTP_FORBIDDEN ->
                Resource.Error("You do not have permission to do this.")

            else -> {
                val json = response.jsonBody()
                val success = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                if (success == false) {
                    val message = json.get("message")?.takeUnless { it.isJsonNull }?.asString
                    Resource.Error(message?.takeIf { it.isNotBlank() } ?: "The server rejected the request.")
                } else if (!response.isSuccessful && response.code() != 201) {
                    Resource.Error("Server error (${response.code()}). Please try again later.")
                } else {
                    Resource.Success(onSuccess(json))
                }
            }
        }
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

    /**
     * The response's JSON, from wherever Retrofit put it: a non-2xx reply lands
     * in `errorBody()`, not `body()`, and that is where its reason lives.
     */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** Reads a `[{id, name}]` list into picker options. */
    private fun JsonObject?.options(key: String): List<SelectorOption> {
        val array = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { element: JsonElement ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            SelectorOption(id = id, label = name)
        }
    }
}
