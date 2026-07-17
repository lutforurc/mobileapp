package com.example.cashbookbd.ui.applist

import com.example.cashbookbd.applist.AppListColumn

data class AppListUiState(
    val title: String = "",
    val isSupported: Boolean = true,
    val columns: List<AppListColumn> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<List<String>> = emptyList(),
    val sessionExpired: Boolean = false,
)
