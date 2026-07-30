package com.example.cashbookbd.ui.reseller

import com.example.cashbookbd.ui.reseller.model.ResellerDashboard

/**
 * Immutable UI state for the reseller self-service dashboard.
 *
 * @param sessionExpired one-shot signal that the token was rejected (401); the
 * screen observes it to navigate back to login, then clears it via
 * [ResellerDashboardViewModel.onSessionExpiredHandled].
 */
data class ResellerDashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dashboard: ResellerDashboard? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)
