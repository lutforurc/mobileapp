package com.example.cashbookbd.data.remote

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jStr
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException

/**
 * One way to talk to the API for the screens of the 2026-10 parity pass, so
 * each repository stops re-writing the same twenty lines of error handling.
 *
 * Every call answers a [Resource] of the response's JSON object, read the way
 * the older repositories read it:
 * - 401 is a lapsed session (the UI logs out);
 * - 403 is said as a refusal, not as a crash;
 * - `success: false` is the server's own refusal, and its `message` is shown
 *   verbatim — the web shows it verbatim too;
 * - a 422 names the first field Laravel complained about;
 * - [emptyOnNotFound] reads `notFound()` ("nothing in this range") as an
 *   empty answer: for a report, no rows is a result, not a failure.
 */
class WebApi(
    private val api: ReportApiService,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun get(
        path: String,
        params: Map<String, String> = emptyMap(),
        emptyOnNotFound: Boolean = false,
    ): Resource<JsonObject> = call(emptyOnNotFound) { api.get(path, params) }

    suspend fun post(path: String, body: JsonObject = JsonObject()): Resource<JsonObject> =
        call(false) { api.postObjectRaw(path, body) }

    /**
     * A POST whose `success: false` answer is still handed back whole — for
     * the saves that answer a question rather than a refusal (a stock
     * shortage carries its rows and whether the branch blocks). Only a lapsed
     * session, a dead network or a body that is not JSON come back as errors.
     */
    suspend fun postEnvelope(path: String, body: JsonObject): Resource<JsonObject> = withContext(io) {
        try {
            val response = api.postObjectRaw(path, body)
            if (response.code() == 401) return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            val obj = response.body().jAsObj()
                ?: response.errorBody()?.string()?.let { raw -> runCatching { JsonParser.parseString(raw).jAsObj() }.getOrNull() }
            obj?.let { Resource.Success(it) }
                ?: Resource.Error("Server error (${response.code()}). Please try again later.")
        } catch (e: InterruptedIOException) {
            Resource.Error("The server took too long to answer. Please check and try again.", isAmbiguous = true)
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    suspend fun postMap(path: String, body: Map<String, Any>): Resource<JsonObject> =
        call(false) { api.postAny(path, body) }

    suspend fun put(path: String, body: Map<String, Any>): Resource<JsonObject> =
        call(false) { api.putAny(path, body) }

    suspend fun patch(path: String, body: Map<String, Any>): Resource<JsonObject> =
        call(false) { api.patchAny(path, body) }

    suspend fun delete(path: String): Resource<JsonObject> = call(false) { api.delete(path) }

    suspend fun multipart(
        path: String,
        fields: Map<String, RequestBody>,
        parts: List<MultipartBody.Part>,
    ): Resource<JsonObject> = call(false) { api.postMultipart(path, fields, parts) }

    private suspend fun call(
        emptyOnNotFound: Boolean,
        block: suspend () -> Response<JsonElement>,
    ): Resource<JsonObject> = withContext(io) {
        try {
            val response = block()
            val code = response.code()
            if (code == 401) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val body: JsonObject? = response.body().jAsObj()
                ?: response.errorBody()?.string()?.let { raw ->
                    runCatching { JsonParser.parseString(raw).jAsObj() }.getOrNull()
                }
            val success = body?.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean
            val message = body?.let { messageOf(it) }.orEmpty()

            if (emptyOnNotFound && (code == 404 || (success == false && isNotFound(body)))) {
                return@withContext Resource.Success(JsonObject())
            }
            when {
                code == 403 -> Resource.Error(message.ifBlank { "You do not have permission to do this." })
                code == 422 -> Resource.Error(message.ifBlank { "Please check the form and try again." })
                success == false -> Resource.Error(
                    message.ifBlank { "The server refused the request." },
                    severity = body?.jStr("severity")?.ifBlank { null },
                )
                !response.isSuccessful -> Resource.Error(
                    message.ifBlank { "Server error ($code). Please try again later." },
                )
                body == null -> Resource.Error("Server error ($code). Please try again later.")
                else -> Resource.Success(body)
            }
        } catch (e: InterruptedIOException) {
            Resource.Error("The server took too long to answer. Please check and try again.", isAmbiguous = true)
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    companion object {
        const val SESSION_EXPIRED = "Your session has expired. Please log in again."

        /** The server's own words: `message`, else the first validation error. */
        fun messageOf(body: JsonObject): String {
            body.jStr("message").takeIf { it.isNotBlank() }?.let { return it }
            // Some controllers answer `{error: {message}}`.
            body.jObj("error")?.jStr("message")?.takeIf { it.isNotBlank() }?.let { return it }
            body.jStr("error").takeIf { it.isNotBlank() && it.toIntOrNull() == null }?.let { return it }
            val errors = body.get("errors") ?: body.jObj("data")?.get("errors")
            errors.jAsObj()?.entrySet()?.firstOrNull()?.value?.let { first ->
                if (first.isJsonArray) {
                    first.asJsonArray.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
                } else if (first.isJsonPrimitive) {
                    return first.asString
                }
            }
            return ""
        }

        /** `notFound()` answers `{success:false, error:404}` or a "not found" message. */
        private fun isNotFound(body: JsonObject?): Boolean {
            if (body == null) return false
            if (body.jStr("error") == "404" || body.jStr("code") == "404") return true
            return body.jStr("message").contains("not found", ignoreCase = true)
        }
    }
}
