package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /reports/api-ledger?branch_id=&ledger_id=&start_date=&end_date=&delay=`
 *
 * Wrapped by `foundData()`, so the statement sits at `data.data`:
 *
 * {
 *   "success": true,
 *   "data": {
 *     "data": {                                  <-- [ApiLedgerStatementDto]
 *       "opening_balance": { "total_debit": "5000.00", "total_credit": "0.00" },
 *       "details": [
 *         {
 *           "vr_date": "2026-06-01",
 *           "vr_no": "JV-12",
 *           "combined_number": "...",
 *           "branch_name": "Head Office",
 *           "name": "Rezaul (Mason)",           <-- opposite party
 *           "remarks": "...",
 *           "debit": "1000.00",
 *           "credit": "0.00"
 *         }
 *       ]
 *     },
 *     "transaction_date": ""
 *   },
 *   "error": { "code": 0 }
 * }
 *
 * Money columns come back from MySQL as strings; Gson coerces them into the
 * `Double?` fields below. `opening_balance` totals are null when there are no
 * prior transactions.
 */
data class ApiLedgerReportResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ApiLedgerEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class ApiLedgerEnvelope(
    @SerializedName("data") val statement: ApiLedgerStatementDto? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

data class ApiLedgerStatementDto(
    @SerializedName("opening_balance") val openingBalance: ApiOpeningBalanceDto? = null,
    @SerializedName("details") val details: List<ApiLedgerRowDto>? = null,
    // Optional footer totals. The current backend doesn't send these (the app
    // derives them), but if a `summary` block is added later it takes priority.
    @SerializedName("summary") val summary: ApiLedgerSummaryDto? = null,
)

data class ApiLedgerSummaryDto(
    @SerializedName("range_debit") val rangeDebit: Double? = null,
    @SerializedName("range_credit") val rangeCredit: Double? = null,
    @SerializedName("total_debit") val totalDebit: Double? = null,
    @SerializedName("total_credit") val totalCredit: Double? = null,
)

data class ApiOpeningBalanceDto(
    @SerializedName("total_debit") val totalDebit: Double? = null,
    @SerializedName("total_credit") val totalCredit: Double? = null,
)

data class ApiLedgerRowDto(
    @SerializedName("vr_date") val vrDate: String? = null,
    @SerializedName("vr_no") val vrNo: String? = null,
    @SerializedName("combined_number") val combinedNumber: String? = null,
    @SerializedName("branch_name") val branchName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("remarks") val remarks: String? = null,
    /**
     * 'Cash Received' (a 1- voucher) or 'Cash Payment' (2-), decided on the
     * server; blank for every other kind. Display only — it takes the place of
     * the account name in the screen's DESCRIPTION column.
     */
    @SerializedName("cash_transaction_type") val cashTransactionType: String? = null,
    @SerializedName("debit") val debit: Double? = null,
    @SerializedName("credit") val credit: Double? = null,
    /** Pipe-separated voucher attachment file names, or null. */
    @SerializedName("voucher_image") val voucherImage: String? = null,
    /** Raw branch id — padded to 4 for the attachment URLs. */
    @SerializedName("branch_id") val branchId: String? = null,
    // The facts the row's Edit reads (web voucherEditNavigation). Kept as raw
    // elements: the server sends 1/0, "1"/"0" or true/false depending on the query.
    @SerializedName("is_approved") val isApproved: com.google.gson.JsonElement? = null,
    @SerializedName("is_bank_voucher") val isBankVoucher: com.google.gson.JsonElement? = null,
    /** A tiles discount journal's row names its receipt here. */
    @SerializedName("edit_vr_no") val editVrNo: String? = null,
    /** A screen the number cannot name (`employee_loan`). */
    @SerializedName("edit_screen") val editScreen: String? = null,
)
