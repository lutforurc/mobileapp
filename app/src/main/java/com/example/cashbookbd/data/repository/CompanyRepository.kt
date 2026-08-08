package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * One company loaded for editing (the web's /company/company-edit/:id form).
 * [hashedId] is the `company_id` hash the update endpoint resolves on.
 */
data class CompanyEditData(
    val hashedId: String,
    val name: String,
    val contactPerson: String,
    val phone: String,
    val email: String,
    val address: String,
    val notes: String,
)

/**
 * The Edit Company form's load and update.
 *
 * `company/company-edit/{id}` answers through `foundData`, so the record sits
 * under `data.data`. The update posts plain JSON — the controller reads request
 * keys directly, and only overwrites optional columns (email/address/notes)
 * when their key is present, which a full payload always satisfies. The logo
 * upload is a multipart file field and stays web-only.
 */
class CompanyRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Loads one company's editable fields. [companyId] may be numeric or hashed. */
    suspend fun loadForEdit(companyId: String): Resource<CompanyEditData> = withContext(ioDispatcher) {
        request({ body ->
            // foundData nests the record under data.data.
            val record = body?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            CompanyEditData(
                hashedId = record.str("company_id") ?: companyId,
                name = record.str("name").orEmpty(),
                contactPerson = record.str("contact_person").orEmpty(),
                phone = record.str("phone").orEmpty(),
                email = record.str("email").orEmpty(),
                address = record.str("address").orEmpty(),
                notes = record.str("notes").orEmpty(),
            )
        }) { reportApi.get("company/company-edit/$companyId", emptyMap()) }
    }

    /**
     * Updates the company. `mobile` is posted as a copy of [phone], matching the
     * web form. Returns the server's confirmation message.
     */
    suspend fun update(
        companyId: String,
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        notes: String,
        /** JPEG bytes for the light/dark logos; null leaves the stored one. */
        lightLogo: ByteArray? = null,
        darkLogo: ByteArray? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        val readMessage: (JsonObject?) -> String = { responseBody ->
            responseBody?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: "Company updated successfully."
        }
        if (lightLogo == null && darkLogo == null) {
            val body = JsonObject().apply {
                addProperty("company_id", companyId)
                addProperty("name", name.trim())
                addProperty("contact_person", contactPerson.trim())
                addProperty("phone", phone.trim())
                addProperty("mobile", phone.trim())
                addProperty("email", email.trim())
                addProperty("address", address.trim())
                addProperty("notes", notes.trim())
            }
            return@withContext request(readMessage) {
                transactionApi.postObject("company/company-update", body)
            }
        }
        // A logo rides multipart, the web form's own shape: text fields plus
        // the `company_logo` / `company_logo_dark` file parts (≤2 MB each).
        val text = { value: String ->
            value.toRequestBody("text/plain".toMediaType())
        }
        val fields = mapOf(
            "company_id" to text(companyId),
            "name" to text(name.trim()),
            "contact_person" to text(contactPerson.trim()),
            "phone" to text(phone.trim()),
            "mobile" to text(phone.trim()),
            "email" to text(email.trim()),
            "address" to text(address.trim()),
            "notes" to text(notes.trim()),
        )
        val parts = buildList {
            lightLogo?.let {
                add(
                    okhttp3.MultipartBody.Part.createFormData(
                        "company_logo", "logo.jpg", it.toRequestBody("image/jpeg".toMediaType()),
                    )
                )
            }
            darkLogo?.let {
                add(
                    okhttp3.MultipartBody.Part.createFormData(
                        "company_logo_dark", "logo_dark.jpg", it.toRequestBody("image/jpeg".toMediaType()),
                    )
                )
            }
        }
        request(readMessage) { reportApi.postMultipart("company/company-update", fields, parts) }
    }

    /**
     * Sends the request and maps the body. Failures are read from the JSON
     * `success` field before the HTTP status — the API's `notFound()` helper
     * answers 201 with `success: false` — and any server `message` is preferred
     * over a generic error, so validation reasons reach the user.
     */
    private suspend fun <T> request(
        onSuccess: (JsonObject?) -> T,
        send: suspend () -> Response<JsonElement>,
    ): Resource<T> = try {
        val response = send()
        when (response.code()) {
            HTTP_UNAUTHORIZED ->
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)

            HTTP_FORBIDDEN ->
                Resource.Error("You do not have permission to do this.")

            else -> {
                val json = response.jsonBody()
                val message = json?.get("message")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it.isNotBlank() }
                val success = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                when {
                    success == false ->
                        Resource.Error(message ?: "The server rejected the request.")

                    !response.isSuccessful && response.code() != 201 ->
                        Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")

                    else -> Resource.Success(onSuccess(json))
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

    /** The response's JSON — a non-2xx reply lands in `errorBody()`, not `body()`. */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject?.str(key: String): String? {
        val el = this?.get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }
}
