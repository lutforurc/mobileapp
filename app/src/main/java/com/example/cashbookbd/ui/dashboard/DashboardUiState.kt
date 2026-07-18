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
    /** Construction branches get the H/O receive panel and no Top Sales list. */
    val isConstruction: Boolean = false,
    /**
     * Receive-action state per row, keyed by `mtm_id` (NOT list index, so it
     * survives re-ordering and refreshes). Absent = idle.
     */
    val rowActions: Map<Int, RowActionState> = emptyMap(),
    /** One-shot snackbar message; cleared via [DashboardViewModel.onSnackbarShown]. */
    val snackbar: DashboardSnackbar? = null,
) {
    fun rowAction(mtmId: Int): RowActionState = rowActions[mtmId] ?: RowActionState()
}

/** Per-row state for the receive action. */
data class RowActionState(
    /** Request in flight — show a spinner, disable the button. */
    val inFlight: Boolean = false,
    /** Locally confirmed this session (independent of the server's initial flag). */
    val processedLocally: Boolean = false,
)

data class DashboardSnackbar(
    val message: String,
    val isError: Boolean,
)