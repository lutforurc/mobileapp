package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /reports/bankbook`, wrapped by `foundData()` so the row list lives at
 * `data.data`.
 *
 * Every field is nullable on purpose. The array mixes three kinds of row and
 * the three summary rows the backend appends are PHP arrays that simply omit
 * `sl_number`, `balance` and the cumulative columns — absent, not null.
 *
 * The money columns are typed [String] rather than a number because their type
 * flips: transaction rows carry MySQL decimals as strings ("32500.00") while
 * the appended summary rows carry plain integers (67500). Gson coerces both
 * into a String, and the mapping layer parses once.
 */
data class BankBookResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: BankBookEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class BankBookEnvelope(
    @SerializedName("data") val rows: List<BankBookRowDto>? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class BankBookRowDto(
    @SerializedName("sl_number") val slNumber: String? = null,
    /** Comes back as dd/MM/yyyy, though the request takes yyyy-MM-dd. */
    @SerializedName("vr_date") val vrDate: String? = null,
    @SerializedName("vr_no") val vrNo: String? = null,
    /** May carry HTML on voucher types 3, 4 and 6. */
    @SerializedName("nam") val particulars: String? = null,
    @SerializedName("remarks") val remarks: String? = null,
    /** Only populated for branches whose business_type_id is 8. */
    @SerializedName("order_number") val orderNumber: String? = null,
    /** The bank the money moved through; null on every summary row. */
    @SerializedName("from_bank") val fromBank: String? = null,
    /** Received — the naming is inverted (see the mapper). */
    @SerializedName("credit") val credit: String? = null,
    /** Payment — the naming is inverted (see the mapper). */
    @SerializedName("debit") val debit: String? = null,
    @SerializedName("mtm_id") val mtmId: Long? = null,
    @SerializedName("is_approved") val isApproved: String? = null,
    @SerializedName("combined_number") val combinedNumber: String? = null,
)
