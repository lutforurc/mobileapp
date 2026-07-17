package com.example.cashbookbd.ui.applist

import com.example.cashbookbd.applist.AppListColumn

data class AppListUiState(
    val title: String = "",
    val isSupported: Boolean = true,
    val columns: List<AppListColumn> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<List<String>> = emptyList(),
    val isPaginated: Boolean = false,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val sessionExpired: Boolean = false,
) {
    val canPrev: Boolean get() = isPaginated && currentPage > 1 && !isLoading
    val canNext: Boolean get() = isPaginated && currentPage < lastPage && !isLoading
    val showPagination: Boolean get() = isPaginated && lastPage > 1
}
