package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.SoftwareInfo
import com.example.cashbookbd.data.repository.SoftwareInfoRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Software Information form: prefills the stored company details and
 * saves the five fields together.
 */
class SoftwareInfoViewModel(
    private val repository: SoftwareInfoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoftwareInfoUiState())
    val uiState: StateFlow<SoftwareInfoUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getSoftwareInfo()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = result.data.name,
                        mobile = result.data.mobile,
                        email = result.data.email,
                        website = result.data.website,
                        address = result.data.address,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onMobile(value: String) = _uiState.update { it.copy(mobile = value) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun onWebsite(value: String) = _uiState.update { it.copy(website = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }

    fun save() {
        val state = _uiState.value
        // The report footer needs at least a name or a mobile to print.
        if (state.name.isBlank() && state.mobile.isBlank()) {
            _uiState.update { it.copy(error = "Enter at least the company name or mobile") }
            return
        }
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.updateSoftwareInfo(
                SoftwareInfo(
                    name = state.name,
                    mobile = state.mobile,
                    email = state.email,
                    website = state.website,
                    address = state.address,
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
    fun onSavedMessageShown() = _uiState.update { it.copy(savedMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                SoftwareInfoViewModel(
                    repository = ServiceLocator.provideSoftwareInfoRepository(context.applicationContext),
                )
            }
        }
    }
}
