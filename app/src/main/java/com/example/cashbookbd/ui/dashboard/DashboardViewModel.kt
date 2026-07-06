package com.example.cashbookbd.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.di.ServiceLocator
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