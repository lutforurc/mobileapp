package com.example.cashbookbd.data.remote.dto

import com.example.cashbookbd.session.Permission
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * Mirrors `POST /settings/get-settings`, which the backend wraps with the same
 * `foundData()` helper as the dashboard (see [DashboardResponse]) — so the
 * payload is double-nested under `data.data`:
 *
 * {
 *   "success": true,
 *   "data": {
 *     "data": { "permissions": [ ... ] },   <-- [SettingsPayload]
 *     "transaction_date": ""
 *   },
 *   "error": { "code": 0 }
 * }
 */
data class SettingsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SettingsEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class SettingsEnvelope(
    @SerializedName("data") val payload: SettingsPayload? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class SettingsPayload(
    @SerializedName("permissions") val permissions: List<PermissionDto>? = null,
    @SerializedName("branch") val branch: SettingsBranchDto? = null,
    @SerializedName("user") val user: SettingsUserDto? = null,
    /**
     * The branch's current transaction date, already formatted dd/MM/yyyy by the
     * backend (`us_to_bd_date`). Shown as "Trx. Dt." in the account menu, exactly
     * as the web's DropdownUser reads `settings.data.trx_dt`.
     */
    @SerializedName("trx_dt") val trxDt: String? = null,
)

/** The signed-in user, from `settings/get-settings`. Only the fields the app reads. */
data class SettingsUserDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
)

/** The current branch, from `settings/get-settings`. Only the fields the app reads. */
data class SettingsBranchDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("business_type_id") val businessTypeId: Int? = null,
)

/**
 * A permission as sent by the backend. It may arrive as an object
 * (`{ id, name, group_name }`) or as a bare string (`"cash.received.create"`);
 * [PermissionDtoDeserializer] normalizes both into this shape.
 */
data class PermissionDto(
    val id: Long? = null,
    val name: String? = null,
    val groupName: String? = null,
) {
    /** Maps to the domain [Permission], dropping entries without a usable name. */
    fun toPermission(): Permission? =
        name?.takeIf { it.isNotBlank() }?.let { Permission(id = id, name = it, groupName = groupName) }
}

/**
 * Accepts either a JSON string or a `{ id, name, group_name }` object for a
 * permission, matching the web helper's dual-shape support. Registered on the
 * shared Gson in [com.example.cashbookbd.data.remote.NetworkModule].
 */
class PermissionDtoDeserializer : JsonDeserializer<PermissionDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): PermissionDto = when {
        json.isJsonPrimitive -> PermissionDto(name = json.asString)
        json.isJsonObject -> {
            val obj = json.asJsonObject
            fun field(key: String): JsonElement? = obj.get(key)?.takeUnless { it.isJsonNull }
            PermissionDto(
                id = field("id")?.asLong,
                name = field("name")?.asString,
                groupName = field("group_name")?.asString,
            )
        }
        else -> PermissionDto()
    }
}