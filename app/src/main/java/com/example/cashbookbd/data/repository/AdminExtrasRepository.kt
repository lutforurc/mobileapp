package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.AdminNotificationsApiService
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One platform notification row (`admin/notifications`). */
data class AdminNotificationRow(
    val id: Long,
    val title: String,
    val message: String,
    val tone: String,
    val audienceType: String,
    val branchId: Long?,
    val roleId: Long?,
    val startsAt: String?,
    val expiresAt: String?,
    val createdAt: String?,
    val readCount: Int,
)

/** The create-notification form as `POST admin/notifications` expects it. */
data class AdminNotificationForm(
    val title: String,
    val message: String,
    val tone: String,
    val audienceType: String,
    /** Sent only when [audienceType] is "branch". */
    val branchId: Long?,
    /** Sent only when [audienceType] is "role". */
    val roleId: Long?,
    /** yyyy-MM-dd, or null for "never expires" (sent as an explicit null). */
    val expiresAt: String?,
)

/** One report item of the group-report setup (`{id, name}`). */
data class GroupReportItem(
    val id: Long,
    val name: String,
)

/** The raw setup payload: every pickable option plus the current selection. */
data class GroupReportSetup(
    val options: List<GroupReportItem>,
    val selected: List<GroupReportItem>,
)

/**
 * Backs the three "admin extras" screens: platform notifications
 * (`admin/notifications`, PLATFORM company only — the server's 403 message is
 * surfaced verbatim), permission create/rename (`role/permission/...`, also
 * company-1 restricted), and the group-report setup
 * (`reports/group-report/setup/...`, whose GET returns raw JSON without the
 * usual envelope).
 *
 * Every write follows the app's verdict rules: JSON `success` is the outcome,
 * a 403 surfaces the server's own message, and a 422 error bag surfaces its
 * first message. A 401 sets [Resource.Error.isUnauthorized].
 */
class AdminExtrasRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val extrasApi: AdminNotificationsApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    // ------------------------------------------------------------------
    // Admin notifications
    // ------------------------------------------------------------------

    /** Every notification of the platform (`GET admin/notifications`). */
    suspend fun fetchNotifications(): Resource<List<AdminNotificationRow>> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("admin/notifications", emptyMap())
            failureOrNull(response)?.let { return@safeCall it }
            val json = response.jsonObject()
            if (json?.boolOrNull("success") == false) {
                // notFound() with a blank message just means "no notifications yet".
                val message = json.message()
                return@safeCall if (message == null) Resource.Success(emptyList()) else Resource.Error(message)
            }
            val rows = json?.obj("data")?.obj("data")?.array("notifications")
            Resource.Success(rows?.toNotificationRows().orEmpty())
        }
    }

    /** Publishes a notification (`POST admin/notifications`). */
    suspend fun createNotification(form: AdminNotificationForm): Resource<String> {
        val body = JsonObject().apply {
            addProperty("title", form.title)
            addProperty("message", form.message)
            addProperty("tone", form.tone)
            addProperty("audience_type", form.audienceType)
            form.branchId?.let { addProperty("branch_id", it) }
            form.roleId?.let { addProperty("role_id", it) }
            if (form.expiresAt != null) {
                addProperty("expires_at", form.expiresAt)
            } else {
                add("expires_at", JsonNull.INSTANCE)
            }
        }
        return write("Notification published successfully.") {
            transactionApi.postObject("admin/notifications", body)
        }
    }

    /** Removes a notification (`DELETE admin/notifications/{id}`). */
    suspend fun deleteNotification(id: Long): Resource<String> =
        write("Notification deleted successfully.") { extrasApi.delete("admin/notifications/$id") }

    // ------------------------------------------------------------------
    // Permissions (create / rename)
    // ------------------------------------------------------------------

    /** The distinct permission group names (`GET role/permission-groups`, data.data = strings). */
    suspend fun loadPermissionGroups(): Resource<List<String>> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("role/permission-groups", emptyMap())
            failureOrNull(response)?.let { return@safeCall it }
            val json = response.jsonObject()
            if (json?.boolOrNull("success") == false) {
                // notFound() (no groups yet) — an empty catalogue, not an error.
                return@safeCall Resource.Success(emptyList())
            }
            val array = json?.obj("data")?.array("data") ?: JsonArray()
            Resource.Success(
                array
                    .mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            )
        }
    }

    /**
     * Creates a permission (`POST role/permission/create/new`). A duplicate name
     * arrives as `success:false` with "Permission name already exists.".
     */
    suspend fun createPermission(name: String, groupName: String?): Resource<String> =
        write("Permission created successfully.") {
            transactionApi.postObject("role/permission/create/new", permissionBody(name, groupName))
        }

    /** Renames a permission (`PUT role/permission/update/{id}`), same body as create. */
    suspend fun updatePermission(id: Int, name: String, groupName: String?): Resource<String> =
        write("Permission updated successfully.") {
            extrasApi.put("role/permission/update/$id", permissionBody(name, groupName))
        }

    private fun permissionBody(name: String, groupName: String?) = JsonObject().apply {
        addProperty("name", name.trim())
        val group = groupName?.trim()?.takeIf { it.isNotBlank() }
        if (group != null) addProperty("group_name", group) else add("group_name", JsonNull.INSTANCE)
    }

    // ------------------------------------------------------------------
    // Group report setup
    // ------------------------------------------------------------------

    /**
     * The setup for [group] (1 = Operating Cost, 2 = Purchase Cost) via
     * `GET reports/group-report/setup/{group}`. RAW JSON, no envelope:
     * `{group, options:[{id,name}], selected:[{id,name}]}`.
     */
    suspend fun loadGroupReportSetup(group: Int): Resource<GroupReportSetup> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("reports/group-report/setup/$group", emptyMap())
            failureOrNull(response)?.let { return@safeCall it }
            val json = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall Resource.Error("Invalid response from server.")
            Resource.Success(
                GroupReportSetup(
                    options = (json.array("options") ?: JsonArray()).toGroupReportItems(),
                    selected = (json.array("selected") ?: JsonArray()).toGroupReportItems(),
                )
            )
        }
    }

    /**
     * Adds [itemId] to [groupId]'s report (`POST reports/group-report/setup/items`).
     * A duplicate arrives as `success:false` with the server's message.
     */
    suspend fun addGroupReportItem(groupId: Int, itemId: Long): Resource<String> {
        val body = JsonObject().apply {
            addProperty("group_id", groupId)
            addProperty("item_id", itemId)
        }
        return write("Item added successfully.") {
            transactionApi.postObject("reports/group-report/setup/items", body)
        }
    }

    /** Removes [deleteId] from report [reportType] (`POST reports/group-report/setup/items/delete`). */
    suspend fun removeGroupReportItem(reportType: Int, deleteId: Long): Resource<String> {
        val body = JsonObject().apply {
            addProperty("report_type", reportType)
            addProperty("delete_id", deleteId)
        }
        return write("Item removed successfully.") {
            transactionApi.postObject("reports/group-report/setup/items/delete", body)
        }
    }

    // ------------------------------------------------------------------
    // Shared plumbing
    // ------------------------------------------------------------------

    /** A write that returns the server message, or [fallback] when it sends none. */
    private suspend fun write(fallback: String, call: suspend () -> Response<JsonElement>): Resource<String> =
        withContext(ioDispatcher) {
            safeCall {
                val response = call()
                failureOrNull(response)?.let { return@safeCall it }
                val json = response.jsonObject()
                if (json?.boolOrNull("success") == false) {
                    return@safeCall Resource.Error(json.message() ?: "Could not save.")
                }
                Resource.Success(json?.message() ?: fallback)
            }
        }

    /**
     * Maps an HTTP failure to its user-facing error: 401 → session expired,
     * 403 → the server's own "not allowed" message (the company-1 restriction),
     * 422/other non-2xx → the body's message or error bag. Null when 2xx/201.
     */
    private fun failureOrNull(response: Response<JsonElement>): Resource.Error? = when {
        response.code() == HTTP_UNAUTHORIZED ->
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)

        response.code() == HTTP_FORBIDDEN ->
            Resource.Error(response.jsonObject()?.message() ?: "You do not have permission for this action.")

        !response.isSuccessful && response.code() != 201 ->
            Resource.Error(
                response.jsonObject()?.message()
                    ?: "Server error (${response.code()}). Please try again later."
            )

        else -> null
    }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error(NO_NETWORK)
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    // ---- Row parsing ------------------------------------------------------

    private fun JsonArray.toNotificationRows(): List<AdminNotificationRow> = mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = obj.longOrNull("id") ?: return@mapNotNull null
        AdminNotificationRow(
            id = id,
            title = obj.strOrNull("title").orEmpty(),
            message = obj.strOrNull("message").orEmpty(),
            tone = obj.strOrNull("tone") ?: "info",
            audienceType = obj.strOrNull("audience_type") ?: "global",
            branchId = obj.longOrNull("branch_id"),
            roleId = obj.longOrNull("role_id"),
            startsAt = obj.strOrNull("starts_at"),
            expiresAt = obj.strOrNull("expires_at"),
            createdAt = obj.strOrNull("created_at"),
            readCount = obj.intOrNull("read_count") ?: 0,
        )
    }

    private fun JsonArray.toGroupReportItems(): List<GroupReportItem> = mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = obj.longOrNull("id") ?: return@mapNotNull null
        GroupReportItem(id = id, name = obj.strOrNull("name") ?: "#$id")
    }

    // ---- JSON helpers -----------------------------------------------------

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonObject(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a request was rejected: `message`, `error.message`, or a field error. */
    private fun JsonObject.message(): String? =
        strOrNull("message")
            ?: obj("error")?.strOrNull("message")
            ?: firstFieldError()

    /** Reads `errors: {field: ["reason", …]}` — Laravel's 422 validation bag. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = obj("errors") ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.strOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.longOrNull(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val primitive = get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: return null
        if (primitive.isBoolean) return primitive.asBoolean
        return when (primitive.asString.trim().lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
    }
}
