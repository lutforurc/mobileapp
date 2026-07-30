package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
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

/** One row of the SMS Sent Logs (`admin/sms/sent-list`). */
data class SmsLogRow(
    val id: String,
    val mobile: String,
    val message: String,
    val provider: String,
    val status: String,
    val attempts: String,
    val queuedAt: String,
    val sentAt: String,
    val createdAt: String,
)

/** A page of [SmsLogRow]s with the paginator meta the footer needs. */
data class SmsLogPage(
    val rows: List<SmsLogRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** One SMS template's editable fields (`admin/sms/templates/{id}`). */
data class SmsTemplateDetail(
    val id: String,
    val code: String,
    val name: String,
    val body: String,
    val active: Boolean,
    /** The branch the template belongs to, round-tripped on update. */
    val branchId: Long?,
    val placeholders: List<String>,
)

/** What the template form submits to `templates/store` / `templates/update/{id}`. */
data class SmsTemplateDraft(
    /** null = create; otherwise the id `update/{id}` targets. */
    val templateId: String?,
    /** null omits the field and lets the server default to the user's branch. */
    val branchId: Long?,
    val code: String,
    val name: String,
    val body: String,
    val active: Boolean,
)

/**
 * The SMS module: the sent-log list and the template create/edit endpoints.
 *
 * The sent-list is a POST whose filters travel in the JSON body but whose page
 * number goes in the **query string** — Laravel's paginator reads `?page=` from
 * the URL, not the body. Both list endpoints answer an empty page with the
 * API's `notFound()` ("No SMS Sent Logs found.", possibly as HTTP 404), which
 * is an empty result, not an error.
 */
class SmsRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."

        /** "2026-07-30T09:15:42.000000Z" / "2026-07-30 09:15:42" → date + HH:mm. */
        private val TIMESTAMP = Regex("""^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})""")
    }

    /**
     * A page of the SMS Sent Logs. `admin/sms/sent-list` filters by branch and
     * a mobile-number `like` search; the payload sits at `data.data` as
     * `{items: [...], pagination: {...}}`.
     */
    suspend fun loadLogs(
        page: Int,
        branchId: Long?,
        mobile: String,
        perPage: Int = 10,
    ): Resource<SmsLogPage> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("per_page", perPage)
            branchId?.let { addProperty("branch_id", it) }
            mobile.trim().takeIf { it.isNotBlank() }?.let { addProperty("mobile", it) }
        }
        try {
            // The paginator reads the page from the query string, not the body.
            val response = transactionApi.postObject("admin/sms/sent-list?page=$page", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            // notFound() — no logs at all, or no match — is an empty list.
            if (response.code() == HTTP_NOT_FOUND) {
                return@withContext Resource.Success(SmsLogPage(emptyList(), 1, 1, 0))
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(SmsLogPage(emptyList(), 1, 1, 0))
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            val payload = json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val rows = payload?.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.toLogRow() }
                .orEmpty()
            val pagination = payload?.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject
            Resource.Success(
                SmsLogPage(
                    rows = rows,
                    currentPage = pagination.intOr("current_page", page),
                    lastPage = pagination.intOr("last_page", 1),
                    total = pagination.intOr("total", rows.size),
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

    /** Loads one template for the edit prefill (`admin/sms/templates/{id}`). */
    suspend fun getTemplate(id: String): Resource<SmsTemplateDetail> = withContext(ioDispatcher) {
        try {
            val response = reportApi.get("admin/sms/templates/$id", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Couldn't load this SMS template."
                )
            }
            val payload = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Couldn't load this SMS template.")
            Resource.Success(
                SmsTemplateDetail(
                    id = payload.str("id").orEmpty(),
                    code = payload.str("code").orEmpty(),
                    name = payload.str("name").orEmpty(),
                    body = payload.str("body").orEmpty(),
                    active = payload.boolLoose("status"),
                    branchId = payload.str("branch_id")?.toLongOrNull(),
                    placeholders = payload.get("placeholders")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
                        .orEmpty(),
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
     * Creates or updates a template. The server lowercases and sanitises the
     * code to `[a-z0-9_]`; a duplicate or invalid one comes back as a 422
     * message, surfaced verbatim. Returns the server's confirmation message.
     */
    suspend fun saveTemplate(draft: SmsTemplateDraft): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            draft.branchId?.let { addProperty("branch_id", it) }
            addProperty("code", draft.code.trim())
            addProperty("name", draft.name.trim())
            addProperty("body", draft.body)
            addProperty("status", if (draft.active) 1 else 0)
        }
        val path = draft.templateId
            ?.let { "admin/sms/templates/update/$it" }
            ?: "admin/sms/templates/store"
        try {
            val response = transactionApi.postObject(path, body)
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
            Resource.Success(
                json?.message()?.takeIf { it.isNotBlank() } ?: "SMS template saved successfully"
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Maps a sent-list item to an [SmsLogRow]. */
    private fun JsonObject.toLogRow(): SmsLogRow = SmsLogRow(
        id = str("id").orEmpty(),
        mobile = str("mobile").orEmpty(),
        message = str("message").orEmpty(),
        provider = str("provider").orEmpty(),
        status = str("status").orEmpty(),
        attempts = str("attempts").orEmpty(),
        queuedAt = prettyTimestamp(str("queued_at")),
        sentAt = prettyTimestamp(str("sent_at")),
        createdAt = prettyTimestamp(str("created_at")),
    )

    /** Trims an ISO/SQL timestamp down to "yyyy-MM-dd HH:mm" for the table. */
    private fun prettyTimestamp(raw: String?): String {
        val value = raw?.trim().orEmpty()
        val match = TIMESTAMP.find(value) ?: return value
        return "${match.groupValues[1]} ${match.groupValues[2]}"
    }

    /** A trimmed non-blank string field, reading numbers as their text too. */
    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    /** A flag that may arrive as a JSON boolean or a "1"/"0"/"true" string. */
    private fun JsonObject.boolLoose(key: String): Boolean {
        val primitive = get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: return false
        if (primitive.isBoolean) return primitive.asBoolean
        val text = primitive.asString.trim().lowercase()
        return text == "1" || text == "true"
    }

    /** Reads a paginator meta int that may come as a JSON number or string. */
    private fun JsonObject?.intOr(key: String, default: Int): Int =
        this?.get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt() ?: default

    /**
     * The response's JSON, from wherever Retrofit put it: a non-2xx reply (a
     * plain Laravel 422) lands in `errorBody()`, not `body()` — reading only the
     * latter would throw away the very reason the request was rejected.
     */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /**
     * The reason a request was rejected: `message` (Laravel's validation reply
     * and the API's notFound() helper), `error.message`, or the first entry of
     * Laravel's `errors` bag.
     */
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
