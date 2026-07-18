package com.example.cashbookbd.ui.subscription

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.SubscriptionRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the read-only subscription data. One class serves both custom screens:
 * the Pricing screen calls [loadPlans], the My Plan screen calls [loadCurrent].
 */
class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    fun loadPlans() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getPlans()) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, plans = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun loadCurrent() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getCurrent()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, current = result.data, hasCurrent = result.data != null)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                SubscriptionViewModel(
                    repository = ServiceLocator.provideSubscriptionRepository(context.applicationContext),
                )
            }
        }
    }
}
