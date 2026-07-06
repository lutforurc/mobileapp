package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /chart_of_accounts/ddl/l4-list?searchName=&acType=` — the searchable
 * "Select Ledger" source (COA level-4 accounts unioned with customer/party rows).
 *
 * Wrapped by `foundData()`, so the rows sit at `data.data`. When `searchName` is
 * blank the backend returns a bare `[]` (not this envelope), so callers must
 * avoid searching on an empty query.
 */
data class LedgerSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: LedgerSearchEnvelope? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class LedgerSearchEnvelope(
    @SerializedName("data") val items: List<LedgerSearchItemDto>? = null,
    @SerializedName("transaction_date") val transactionDate: String? = null,
)

/**
 * One ledger/account option. `value` is the ledger/account id to send as
 * `ledger_id`; `label` is the display name. `label_2`/`label_4` (mobile / code)
 * help disambiguate people with the same name.
 */
data class LedgerSearchItemDto(
    @SerializedName("value") val value: Long? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("label_2") val mobile: String? = null,
    @SerializedName("label_4") val code: String? = null,
    @SerializedName("bangla") val bangla: String? = null,
)
