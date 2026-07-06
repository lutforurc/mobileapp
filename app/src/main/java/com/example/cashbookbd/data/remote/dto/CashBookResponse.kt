package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /reports/cashbook`, wrapped by `foundData()` so the row list lives at
 * `data.data`. Money columns (`debit`/`credit`/`balance`) arrive as numbers for
 * the pushed summary rows and as decimal strings for real rows, so they're typed
 * as [String] here (Gson coerces both) and parsed in the mapping layer.
 */
data class CashBookResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: CashBookEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class CashBookEnvelope(
    @SerializedName("data") val rows: List<CashBookRowDto>? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class CashBookRowDto(
    @SerializedName("mtm_id") val mtmId: Long? = null,
    @SerializedName("vr_no") val vrNo: String? = null,
    @SerializedName("combined_number") val combinedNumber: String? = null,
    @SerializedName("nam") val particulars: String? = null,
    @SerializedName("vr_date") val vrDate: String? = null,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("pay_branch_name") val payBranchName: String? = null,
    @SerializedName("debit") val debit: String? = null,
    @SerializedName("credit") val credit: String? = null,
    @SerializedName("balance") val balance: String? = null,
)
