package com.example.cashbookbd.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Mirrors `GET /dashboard/data`, which the backend wraps with the `foundData()`
 * helper. That helper double-nests the payload:
 *
 * {
 *   "success": true,
 *   "message": "",
 *   "data": {
 *     "data": { ...actual dashboard payload... },   <-- [DashboardPayload]
 *     "transaction_date": ""
 *   },
 *   "error": { "code": 0 }
 * }
 *
 * Note: money fields (`debit`/`credit`) arrive as decimal *strings* because they
 * come from SQL `SUM()` on decimal columns, so they're typed as [String] here
 * and parsed in the mapping layer.
 */
data class DashboardResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: DashboardEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class DashboardEnvelope(
    @SerializedName("data") val payload: DashboardPayload? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class DashboardPayload(
    @SerializedName("branch") val branch: BranchDto? = null,
    @SerializedName("trDate") val trDate: String? = null,
    @SerializedName("totalTransaction") val totalTransaction: TransactionSumDto? = null,
    @SerializedName("todayReceived") val todayReceived: TransactionSumDto? = null,
    @SerializedName("receiveDetails") val receiveDetails: ReceiveDetailsDto? = null,
    @SerializedName("transactionText") val transactionText: String? = null,
    @SerializedName("last_update") val lastUpdate: String? = null,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("topProductDays") val topProductDays: Int? = null,
    @SerializedName("topProductsPurchase") val topProductsPurchase: List<TopProductDto>? = null,
)

data class BranchDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("bangla") val bangla: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("branch_types_id") val branchTypesId: Int? = null,
)

/** A `SUM(debit)/SUM(credit)` row; the whole object is null when there are no rows. */
data class TransactionSumDto(
    @SerializedName("branch_id") val branchId: Long? = null,
    @SerializedName("debit") val debit: String? = null,
    @SerializedName("credit") val credit: String? = null,
)

/**
 * `GET /dashboard/branch/monthly-purchase-sales`. The non-construction
 * dashboards read their Top Sales / Top Purchase lists from here (the chart
 * arrays in the same payload are ignored — the app draws no charts).
 */
data class MonthlyTopProductsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: MonthlyTopProductsEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class MonthlyTopProductsEnvelope(
    @SerializedName("data") val payload: MonthlyTopProductsPayload? = null,
)

data class MonthlyTopProductsPayload(
    @SerializedName("topProductDays") val topProductDays: Int? = null,
    @SerializedName("topProductsSales") val topProductsSales: List<TopProductDto>? = null,
    @SerializedName("topProductsPurchase") val topProductsPurchase: List<TopProductDto>? = null,
)

data class TopProductDto(
    @SerializedName("product_id") val productId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("qty") val qty: String? = null,
)

/**
 * `receiveDetails` = { "receivedDetails": {...}, "authBranch": {...} }.
 *
 * `receivedDetails` is a PHP associative array keyed by pay-branch id, so it
 * serializes as a JSON object like `{ "3": [ ... ], "5": [ ... ] }` — or as an
 * empty array `[]` when there are no rows. [PhpEmptyArrayAsMapFactory] handles
 * the empty-array case so this [Map] deserializes cleanly either way.
 */
data class ReceiveDetailsDto(
    @SerializedName("receivedDetails") val receivedDetails: Map<String, List<ReceivedItemDto>>? = null,
    @SerializedName("authBranch") val authBranch: BranchDto? = null,
)

data class ReceivedItemDto(
    /** Id of the source head-office voucher; the key for the receive action. */
    @SerializedName("mtm_id") val mtmId: Int? = null,
    /** Head office's branch id (NOT the receiving branch) — sent back in the receive body. */
    @SerializedName("branch_id") val branchId: Int? = null,
    @SerializedName("pay_branch") val payBranch: Long? = null,
    @SerializedName("branch") val branch: Long? = null,
    @SerializedName("vr_no") val vrNo: String? = null,
    @SerializedName("vr_date") val vrDate: String? = null,
    @SerializedName("remarks") val remarks: String? = null,
    /**
     * POLYMORPHIC (API contract RULE 3): boolean `false` when not yet received,
     * but the string `"1"` when already processed. Typed as [String] because
     * Gson's string adapter coerces a JSON boolean to `"false"`/`"true"` rather
     * than crashing — the defensive handling the contract's custom serializer is
     * for. Interpret via truthiness (non-blank, not "false"/"0").
     */
    @SerializedName("remittance") val remittance: String? = null,
    @SerializedName("debit") val debit: String? = null,
)

/**
 * POST /api/accounts/payment/specific-item body. All four fields come verbatim
 * from the tapped row.
 *
 * ⚠️ NON-IDEMPOTENT: posting the same [mtmId] twice creates a second voucher and
 * debits cash twice. The call must never be auto-retried (see [com.example.cashbookbd.data.repository.DashboardRepository]).
 */
data class ReceiveRequest(
    @SerializedName("mtm_id") val mtmId: Int,
    @SerializedName("branch_id") val branchId: Int,
    @SerializedName("remarks") val remarks: String,
    @SerializedName("amount") val amount: Double,
)

/**
 * Receive-action response. Success is the [success] flag, NOT the HTTP status —
 * a business failure returns HTTP 201 (2xx). The user-facing text is in
 * [message] (on success it's the new voucher no, e.g. "Vr. No. 1-25070042").
 */
data class ReceiveResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: JsonElement? = null,
    @SerializedName("error") val error: ApiError? = null,
)