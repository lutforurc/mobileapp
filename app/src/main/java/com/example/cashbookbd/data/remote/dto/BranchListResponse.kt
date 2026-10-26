package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /branch/ddl/protected-branch` — the branch dropdown source.
 *
 * `foundData()` wraps the payload once, and this endpoint's payload is itself
 * `{ data: [...], transactionDate: ... }`, so the branch list sits three levels
 * deep:
 *
 * {
 *   "success": true,
 *   "data": {
 *     "data": {                        <-- [BranchListEnvelope]
 *       "data": [ {id, name} ],        <-- [BranchListPayload.items]
 *       "transactionDate": "03/07/2026"
 *     },
 *     "transaction_date": ""
 *   }
 * }
 */
data class BranchListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: BranchListEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class BranchListEnvelope(
    @SerializedName("data") val payload: BranchListPayload? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class BranchListPayload(
    @SerializedName("data") val items: List<BranchOptionDto>? = null,
    @SerializedName("transactionDate") val transactionDate: String? = null,
)

data class BranchOptionDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    /**
     * The branch's "handle each customer by an employee" switch. The Due List
     * offers its Employee filter only where this is on (web 015380a1).
     */
    @SerializedName("need_customer_handle_by_employee") val needCustomerHandleByEmployee: Boolean? = null,
)
