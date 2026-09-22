package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One thing somebody did to a customer or a product — a lighter cousin of [AuditEvent], no voucher to point at. */
data class ChangeLogEvent(
    val id: Long,
    val at: String,
    val user: String,
    val action: String,
    val changes: List<AuditChange>,
)

data class ChangeLogView(
    val subjectName: String,
    /** Mobile for a customer, code for a product — whatever the caller asked for. */
    val subjectSubtitle: String,
    val events: List<ChangeLogEvent>,
    val note: String,
)

/**
 * "What happened to this record" — a customer's or a product's own trail,
 * shown as `field: old → new`; a create or a delete has nothing to diff
 * against, so it shows the record's own few fields plainly instead.
 */
class ChangeLogRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetchCustomerHistory(customerId: String): Resource<ChangeLogView> =
        fetchHistory("contact/customer/history/$customerId", subjectKey = "customer", subtitleKey = "mobile")

    /** For [com.example.cashbookbd.applist.ListHistoryAction]-declared lists — any subject shape. */
    suspend fun fetchHistory(path: String, subjectKey: String, subtitleKey: String): Resource<ChangeLogView> =
        withContext(ioDispatcher) {
            try {
                val response = api.get(path, emptyMap())
                if (response.code() == 401) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                }
                if (response.code() == 403) {
                    return@withContext Resource.Error("You do not have permission to see this history.")
                }
                val payload = payloadOf(response.body())
                    ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
                if (payload.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                    return@withContext Resource.Error(
                        payload.get("message")?.takeUnless { it.isJsonNull }?.asString ?: "That history could not be read.",
                    )
                }
                val body = bodyOf(response.body()) ?: payload
                val subject = body.obj(subjectKey)
                Resource.Success(
                    ChangeLogView(
                        subjectName = subject?.text("name").orEmpty(),
                        subjectSubtitle = subject?.text(subtitleKey).orEmpty(),
                        events = body.arr("events").mapNotNull { it.asObject()?.toEvent() },
                        note = body.text("note"),
                    )
                )
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    private fun JsonObject.toEvent(): ChangeLogEvent? {
        val id = long("id") ?: return null
        return ChangeLogEvent(
            id = id,
            at = text("at").replace('T', ' ').take(19),
            user = text("user"),
            action = text("action"),
            changes = arr("changes").mapNotNull { el ->
                val o = el.asObject() ?: return@mapNotNull null
                AuditChange(field = o.text("field"), old = o.text("old"), new = o.text("new"))
            },
        )
    }

    /** Peels the `data` / `data.data` envelope, keeping the `success`/`message` sibling too. */
    private fun bodyOf(body: JsonElement?): JsonObject? {
        var payload: JsonElement? = body?.takeIf { it.isJsonObject }
        repeat(2) {
            val inner = payload?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun payloadOf(body: JsonElement?): JsonObject? = body?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()
}
