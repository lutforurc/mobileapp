package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** The company details printed in every report footer (`settings/software-info`). */
data class SoftwareInfo(
    val name: String = "",
    val mobile: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
)

/**
 * Backs the Software Information screen — a port of the web's
 * /settings/software-info form. Reads and updates the tenant's company
 * name/mobile/email/website/address.
 */
class SoftwareInfoRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /**
     * The stored info (`settings/software-info` → data.data). A null payload —
     * nothing saved yet — maps to a blank [SoftwareInfo], not an error.
     */
    suspend fun getSoftwareInfo(): Resource<SoftwareInfo> = withContext(ioDispatcher) {
        try {
            val response = api.get("settings/software-info", emptyMap())
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
            Resource.Success(
                SoftwareInfo(
                    name = model?.str("name").orEmpty(),
                    mobile = model?.str("mobile").orEmpty(),
                    email = model?.str("email").orEmpty(),
                    website = model?.str("website").orEmpty(),
                    address = model?.str("address").orEmpty(),
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
     * Saves the info (`settings/software-info/update`). All five fields are
     * always sent, blanks included, so clearing a field sticks.
     */
    suspend fun updateSoftwareInfo(info: SoftwareInfo): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "name" to info.name.trim(),
            "mobile" to info.mobile.trim(),
            "email" to info.email.trim(),
            "website" to info.website.trim(),
            "address" to info.address.trim(),
        )
        try {
            val response = api.post("settings/software-info/update", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A validation failure arrives as success:false at 201.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(
                json?.message()?.takeIf { it.isNotBlank() }
                    ?: "Software information updated successfully."
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
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
