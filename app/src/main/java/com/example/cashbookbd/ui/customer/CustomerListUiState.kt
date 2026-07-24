package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.data.repository.CustomerRow

const val CUSTOMERS_PER_PAGE = 10

data class CustomerListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<CustomerRow> = emptyList(),

    val searchQuery: String = "",
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,

    /** The row currently being edited (dialog open), or null. */
    val editing: CustomerRow? = null,
    val editOpening: String = "",
    val editLedger: String = "",
    /** True while the edit is being saved. */
    val isSaving: Boolean = false,

    /** One-shot snackbar text (save outcome / info). */
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canPrev: Boolean get() = currentPage > 1
    val canNext: Boolean get() = currentPage < lastPage
    val showPagination: Boolean get() = lastPage > 1

    /** Opening can be entered only when it isn't already set (one-time on the server). */
    val openingEditable: Boolean get() = editing?.isOpeningSet == false
}
