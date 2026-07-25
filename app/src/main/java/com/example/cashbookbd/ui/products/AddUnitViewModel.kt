package com.example.cashbookbd.ui.products

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.NewUnit
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Add Unit form: collects the fields and saves them. */
class AddUnitViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddUnitUiState())
    val uiState: StateFlow<AddUnitUiState> = _uiState.asStateFlow()

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onShortName(value: String) = _uiState.update { it.copy(shortName = value) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.storeUnit(
                NewUnit(
                    name = state.name,
                    shortName = state.shortName,
                    description = state.description,
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                AddUnitViewModel(ServiceLocator.provideProductRepository(context.applicationContext))
            }
        }
    }
}
