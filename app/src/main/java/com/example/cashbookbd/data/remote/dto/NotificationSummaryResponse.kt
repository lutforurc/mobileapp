package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `GET /notifications/summary`, wrapped by the backend's `foundData()`
 * helper — so the payload is double-nested under `data.data` (same envelope as
 * [SettingsResponse]):
 *
 * {
 *   "success": true,
 *   "data": { "data": { "notifications": [ … ] }, "transaction_date": "" },
 *   "error": { "code": 0 }
 * }
 *
 * Each notification is a ready-to-render item — derived business alerts today,
 * and (once the backend merges them) admin-authored broadcasts in the same shape.
 */
data class NotificationSummaryResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: NotificationEnvelope? = null,
)

data class NotificationEnvelope(
    @SerializedName("data") val payload: NotificationPayloadDto? = null,
)

data class NotificationPayloadDto(
    @SerializedName("notifications") val notifications: List<NotificationItemDto>? = null,
)

data class NotificationItemDto(
    /** The notification type, e.g. "low_stock" (derived) or "admin_12" (stored). */
    @SerializedName("id") val id: String? = null,
    /** Opaque key the dismiss endpoint expects back. */
    @SerializedName("notification_key") val notificationKey: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("count") val count: Int? = null,
    /** danger | warning | info | success — drives the accent colour. */
    @SerializedName("tone") val tone: String? = null,
    /** Web target path; may be blank when there is nowhere to go. */
    @SerializedName("to") val to: String? = null,
    @SerializedName("preview") val preview: List<NotificationPreviewDto>? = null,
    @SerializedName("dismissed") val dismissed: Boolean? = null,
)

data class NotificationPreviewDto(
    @SerializedName("label") val label: String? = null,
    @SerializedName("meta") val meta: String? = null,
    @SerializedName("value") val value: String? = null,
)

/** The dismiss endpoint just returns a success/message envelope. */
data class NotificationDismissResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
)
