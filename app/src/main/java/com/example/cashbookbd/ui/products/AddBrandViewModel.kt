package com.example.cashbookbd.ui.products

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.NewBrand
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Add Brand form: collects the fields and saves them. */
class AddBrandViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBrandUiState())
    val uiState: StateFlow<AddBrandUiState> = _uiState.asStateFlow()

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }
    fun onContacts(value: String) = _uiState.update { it.copy(contacts = value) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.storeBrand(
                NewBrand(
                    name = state.name,
                    address = state.address,
                    contacts = state.contacts,
                    email = state.email,
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
                AddBrandViewModel(ServiceLocator.provideProductRepository(context.applicationContext))
            }
        }
    }
}
