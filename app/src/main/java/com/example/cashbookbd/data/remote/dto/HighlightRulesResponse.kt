package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /highlight-rules/active`, wrapped by `foundData()` so the payload lives
 * at `data.data.highlight_rules` — only the active rules, already sorted
 * priority DESC, id ASC (so the first matching phrase wins).
 */
data class HighlightRulesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: HighlightRulesEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class HighlightRulesEnvelope(
    @SerializedName("data") val data: HighlightRulesPayload? = null,
)

data class HighlightRulesPayload(
    @SerializedName("highlight_rules") val rules: List<HighlightRuleDto>? = null,
)

data class HighlightRuleDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("phrase") val phrase: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("priority") val priority: Int? = null,
    // Sent by the admin list only; the /active endpoint omits both.
    @SerializedName("status") val status: Int? = null,
    @SerializedName("description") val description: String? = null,
)

/**
 * Body of `POST/PUT /admin/highlight-rules`. Validation server-side: phrase
 * required (max 255), color one of the palette keys (else 422), priority
 * -9999..9999, status 0/1, description max 255 or null.
 */
data class HighlightRuleWriteRequest(
    @SerializedName("phrase") val phrase: String,
    @SerializedName("color") val color: String,
    @SerializedName("priority") val priority: Int,
    @SerializedName("status") val status: Int,
    @SerializedName("description") val description: String?,
)

/** Envelope of the admin store/update/delete calls — only the outcome matters. */
data class HighlightRuleWriteResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null,
)
