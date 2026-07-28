package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `GET /in-app-messages/sync`, wrapped by the backend's `foundData()`
 * helper — so the payload is double-nested under `data.data`, the same envelope
 * as [NotificationSummaryResponse]:
 *
 * {
 *   "success": true,
 *   "data": { "data": { "messages": [ … ], "synced_at": "…" } },
 *   "error": { "code": 0 }
 * }
 *
 * These are admin-authored pop-up campaigns, not notification-center items: the
 * server has already applied audience, schedule and frequency rules, so every
 * message that arrives here is one the user should actually be shown.
 */
data class InAppMessageSyncResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: InAppMessageEnvelope? = null,
)

data class InAppMessageEnvelope(
    @SerializedName("data") val payload: InAppMessagePayloadDto? = null,
)

data class InAppMessagePayloadDto(
    @SerializedName("messages") val messages: List<InAppMessageDto>? = null,
    @SerializedName("synced_at") val syncedAt: String? = null,
)

data class InAppMessageDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    /** MODAL | BANNER_TOP | BANNER_BOTTOM | CARD | IMAGE_ONLY */
    @SerializedName("layout") val layout: String? = null,
    /** APP_OPEN | LOGIN */
    @SerializedName("trigger_event") val triggerEvent: String? = null,
    @SerializedName("primary_label") val primaryLabel: String? = null,
    @SerializedName("primary_action") val primaryAction: String? = null,
    @SerializedName("secondary_label") val secondaryLabel: String? = null,
    @SerializedName("secondary_action") val secondaryAction: String? = null,
    @SerializedName("bg_color") val bgColor: String? = null,
    @SerializedName("text_color") val textColor: String? = null,
    @SerializedName("button_color") val buttonColor: String? = null,
    @SerializedName("priority") val priority: Int? = null,
    /** True when the message may only be closed through its own button. */
    @SerializedName("require_ack") val requireAck: Boolean? = null,
    @SerializedName("shown_count") val shownCount: Int? = null,
)

/** Body of `POST /in-app-messages/events` — always a batch, even for one event. */
data class InAppMessageEventRequest(
    @SerializedName("events") val events: List<InAppMessageEventDto>,
)

data class InAppMessageEventDto(
    @SerializedName("message_id") val messageId: Long,
    /** IMPRESSION | CLICK | DISMISS | ACK */
    @SerializedName("event") val event: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("action_target") val actionTarget: String? = null,
    /** ISO-8601; set when the event happened, which may predate the upload. */
    @SerializedName("occurred_at") val occurredAt: String? = null,
)

data class InAppMessageEventResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
)
