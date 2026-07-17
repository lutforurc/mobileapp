package com.example.cashbookbd.ui.applist

import com.example.cashbookbd.applist.AppListColumn
import com.example.cashbookbd.applist.ListAddAction
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
    /** Row ids whose status change is still in flight; their switch is disabled. */
    val togglingIds: Set<String> = emptySet(),
    /** One-shot message for a status change (success or failure). */
    val actionMessage: String? = null,
) {
    val canPrev: Boolean get() = isPaginated && currentPage > 1 && !isLoading
    val canNext: Boolean get() = isPaginated && currentPage < lastPage && !isLoading
    val showPagination: Boolean get() = isPaginated && lastPage > 1
}
