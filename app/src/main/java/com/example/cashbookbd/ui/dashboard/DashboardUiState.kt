package com.example.cashbookbd.ui.dashboard

import com.example.cashbookbd.ui.dashboard.model.Dashboard

/**
 * Immutable UI state for the dashboard screen.
 *
 * @param sessionExpired one-shot signal that the token was rejected (401); the
 * screen observes it to navigate back to login, then calls [DashboardViewModel]
 * to keep it from firing twice.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dashboard: Dashboard? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)