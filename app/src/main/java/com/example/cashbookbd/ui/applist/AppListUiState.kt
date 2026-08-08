package com.example.cashbookbd.ui.applist

import com.example.cashbookbd.applist.AppListColumn
import com.example.cashbookbd.applist.ListAddAction
import com.example.cashbookbd.applist.ListDeleteAction
import com.example.cashbookbd.applist.ListEditAction
import com.example.cashbookbd.data.repository.AppListRow

/**
 * Page sizes offered by the list toolbar. The web also offers "All", which is
 * left out here: the shared table renders every row eagerly, so an unpaged fetch
 * of a few hundred rows would stall the screen.
 */
val PER_PAGE_OPTIONS: List<Int> = listOf(10, 25, 50, 100)

data class AppListUiState(
    val title: String = "",
    val isSupported: Boolean = true,
    val columns: List<AppListColumn> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<AppListRow> = emptyList(),
    val isPaginated: Boolean = false,
    /** Rows requested per page; user-selectable from [PER_PAGE_OPTIONS]. */
    val perPage: Int = 25,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val sessionExpired: Boolean = false,
    /** True when the spec declares a status toggle — adds the Action column. */
    val hasStatusToggle: Boolean = false,
    /** The toolbar's "+ Add" button, when this list has a create screen. */
    val addAction: ListAddAction? = null,
    /** The per-row edit pencil, when this list has an edit screen. */
    val editAction: ListEditAction? = null,
    /** The per-row delete bin (with confirm), when this list allows deletes. */
    val deleteAction: ListDeleteAction? = null,
    /** The row awaiting delete confirmation; null when no dialog is open. */
    val pendingDelete: AppListRow? = null,
    /** True while a confirmed delete is in flight. */
    val isDeleting: Boolean = false,
    /** Row ids whose status change is still in flight; their switch is disabled. */
    val togglingIds: Set<String> = emptySet(),
    /** One-shot message for a status change (success or failure). */
    val actionMessage: String? = null,
    /**
     * True when this list offers the per-row opening stock entry — the spec
     * declares it AND the branch's "Opening ongoing" flag is on (the same
     * `is_opening == 1` gate the web's inline columns use).
     */
    val openingEnabled: Boolean = false,
    /** The row whose opening stock dialog is open; null when closed. */
    val openingEdit: AppListRow? = null,
    /** Dialog fields: IMEI/serial lines, quantity and rate. */
    val openingSerial: String = "",
    val openingQty: String = "",
    val openingRate: String = "",
    val openingSaving: Boolean = false,
    /**
     * True when the user may delete an opening stock voucher — voucher.delete,
     * the permission the API itself checks.
     */
    val canDeleteVoucher: Boolean = false,
    /** The row whose opening-stock delete awaits confirmation; null when closed. */
    val openingDeletePending: AppListRow? = null,
    /** True while a confirmed opening delete is in flight. */
    val openingDeleting: Boolean = false,
) {
    val canPrev: Boolean get() = isPaginated && currentPage > 1 && !isLoading
    val canNext: Boolean get() = isPaginated && currentPage < lastPage && !isLoading
    val showPagination: Boolean get() = isPaginated && lastPage > 1
}
