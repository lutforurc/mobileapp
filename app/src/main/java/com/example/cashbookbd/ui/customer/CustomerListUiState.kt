package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.data.repository.ChangeLogView
import com.example.cashbookbd.data.repository.CustomerRow

const val CUSTOMERS_PER_PAGE = 10

/**
 * The web's per-page choices (SelectOption.tsx). The last is "All", sent as
 * `per_page=0`, which the API answers with every row it matched.
 */
val CUSTOMER_PAGE_SIZES = listOf(
    com.example.cashbookbd.ui.reports.model.SelectorOption("10", "10"),
    com.example.cashbookbd.ui.reports.model.SelectorOption("20", "20"),
    com.example.cashbookbd.ui.reports.model.SelectorOption("30", "30"),
    com.example.cashbookbd.ui.reports.model.SelectorOption("50", "50"),
    com.example.cashbookbd.ui.reports.model.SelectorOption("100", "100"),
    com.example.cashbookbd.ui.reports.model.SelectorOption("0", "All"),
)

data class CustomerListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<CustomerRow> = emptyList(),

    val searchQuery: String = "",
    /** Client Type — the empty value means every party ("All Types"). */
    val partyType: com.example.cashbookbd.ui.reports.model.SelectorOption = ALL_CLIENT_TYPES,
    /**
     * How many rows a page holds — "0" is All (every row the server matched),
     * the web's own choice (ecc4602d).
     */
    val pageSize: com.example.cashbookbd.ui.reports.model.SelectorOption = CUSTOMER_PAGE_SIZES.first(),
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,

    /** The row currently being edited (dialog open), or null. */
    val editing: CustomerRow? = null,
    val editOpening: String = "",
    val editLedger: String = "",
    /**
     * A save failure shown inside the dialog. The snackbar sits behind the
     * dialog's scrim, where a refusal is easy to miss with the dialog open.
     */
    val editError: String? = null,
    /** True while the edit is being saved. */
    val isSaving: Boolean = false,

    /** The row whose opening-balance delete is awaiting confirmation. */
    val openingDeleteRow: CustomerRow? = null,
    /** True while a confirmed opening delete is in flight. */
    val isDeletingOpening: Boolean = false,

    /** One-shot snackbar text (save outcome / info). */
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,

    /** The row whose change log dialog is open, or null. */
    val historyRow: CustomerRow? = null,
    val isHistoryLoading: Boolean = false,
    val historyError: String? = null,
    val history: ChangeLogView? = null,
) {
    val canPrev: Boolean get() = currentPage > 1
    val canNext: Boolean get() = currentPage < lastPage
    val showPagination: Boolean get() = lastPage > 1
}

/** "0" means every row the server matched (the API's own per_page=0). */
val CustomerListUiState.perPageValue: Int get() = pageSize.id.toIntOrNull() ?: CUSTOMERS_PER_PAGE
