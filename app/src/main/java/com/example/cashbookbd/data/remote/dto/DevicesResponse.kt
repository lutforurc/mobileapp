package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `GET /devices` (AuthController@devices).
 *
 * Unlike most endpoints this one builds its payload inline rather than through
 * `foundData()`, so it is NOT double-nested — the devices sit directly under
 * `data`, not `data.data`:
 *
 * {
 *   "success": true,
 *   "data": {
 *     "device_limit": 3,
 *     "devices": [ { "id": 12, "name": "...", "is_current": true, ... } ]
 *   },
 *   "error": { "code": 0 }
 * }
 */
data class DevicesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: DevicesPayload? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class DevicesPayload(
    @SerializedName("device_limit") val deviceLimit: Int? = null,
    @SerializedName("devices") val devices: List<DeviceDto>? = null,
)

data class DeviceDto(
    /** Sanctum personal-access-token id — the handle for revoking this device. */
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    /** True for the device running this app — it must not offer to sign itself out. */
    @SerializedName("is_current") val isCurrent: Boolean = false,
)

/** Mirrors `DELETE /devices/{tokenId}` — a plain success/message envelope. */
data class RevokeDeviceResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null,
)
