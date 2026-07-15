package com.example.cashbookbd.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.dto.ReceiveRequest
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.dashboard.model.ReceivedFromHo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    /**
     * First open: show the cached dashboard instantly (if any) and then refresh
     * from the network in the background. With no cache, fall back to a normal
     * full-screen load.
     */
    private fun start() {
        viewModelScope.launch {
            val cached = repository.getCachedDashboard()
            if (cached != null) {
                _uiState.update { it.copy(dashboard = cached, isLoading = false) }
            }
            fetch(isRefresh = cached != null)
        }
    }

    /** Full-screen load (retry after error). */
    fun load() = fetch(isRefresh = false)

    /** Pull-to-refresh: keep the current content visible while re-fetching. */
    fun refresh() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return

        _uiState.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            when (val result = repository.getDashboard()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        dashboard = result.data,
                        errorMessage = null,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Confirms ("receives") one head-office remittance for [row].
     *
     * Enforces API contract RULE 2 (non-idempotent, no retry):
     *  - Ignores the tap if the row is already received, already processed this
     *    session, or a request is already in flight (the button is disabled too).
     *  - Sends exactly one POST; never retries.
     *  - On success, flips the row locally (no full refetch just for this).
     *  - On an ambiguous failure (timeout/lost connection) it does NOT re-post;
     *    it re-fetches the dashboard to learn whether the POST actually landed.
     */
    fun onReceive(row: ReceivedFromHo) {
        val mtmId = row.mtmId
        val current = _uiState.value.rowAction(mtmId)
        if (row.confirmed || current.processedLocally || current.inFlight) return

        setRowAction(mtmId) { it.copy(inFlight = true) }

        viewModelScope.launch {
            val result = repository.receiveSpecificItem(
                ReceiveRequest(
                    mtmId = row.mtmId,
                    branchId = row.branchId,
                    remarks = row.remarks,
                    amount = row.amount,
                )
            )
            when (result) {
                is Resource.Success -> {
                    setRowAction(mtmId) { it.copy(inFlight = false, processedLocally = true) }
                    _uiState.update {
                        it.copy(snackbar = DashboardSnackbar(result.data, isError = false))
                    }
                }

                is Resource.Error -> {
                    // Re-enable the row; NEVER auto-retry the POST.
                    setRowAction(mtmId) { it.copy(inFlight = false) }
                    _uiState.update {
                        it.copy(
                            snackbar = DashboardSnackbar(result.message, isError = true),
                            sessionExpired = it.sessionExpired || result.isUnauthorized,
                        )
                    }
                    // Ambiguous outcome: reconcile from the server instead of re-posting.
                    if (result.isAmbiguous) fetch(isRefresh = true)
                }

                Resource.Loading -> Unit
            }
        }
    }

    private fun setRowAction(mtmId: Int, transform: (RowActionState) -> RowActionState) {
        _uiState.update { state ->
            val updated = transform(state.rowAction(mtmId))
            state.copy(rowActions = state.rowActions + (mtmId to updated))
        }
    }

    /** Consume the one-shot snackbar once the UI has shown it. */
    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbar = null) }
    }

    /** Consume the one-shot session-expired signal once the UI has navigated away. */
    fun onSessionExpiredHandled() {
        _uiState.update { it.copy(sessionExpired = false) }
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                DashboardViewModel(ServiceLocator.provideDashboardRepository(context.applicationContext))
            }
        }
    }
}