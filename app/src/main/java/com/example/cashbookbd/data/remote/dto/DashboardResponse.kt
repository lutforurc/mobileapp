package com.example.cashbookbd.data.remote.dto

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
    @SerializedName("pay_branch") val payBranch: Long? = null,
    @SerializedName("branch") val branch: Long? = null,
    @SerializedName("vr_no") val vrNo: String? = null,
    @SerializedName("vr_date") val vrDate: String? = null,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("remittance") val remittance: String? = null,
    @SerializedName("debit") val debit: String? = null,
)