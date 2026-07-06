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
    @SerializedName("debit") val debit: Double? = null,
    @SerializedName("credit") val credit: Double? = null,
)
