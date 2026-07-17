package com.example.cashbookbd.ui.branch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BranchRepository
import com.example.cashbookbd.data.repository.NewBranch
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Add Branch form: loads its two pickers, then creates the branch. */
class AddBranchViewModel(
    private val repository: BranchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBranchUiState())
    val uiState: StateFlow<AddBranchUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            when (val result = repository.loadFormOptions()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoadingOptions = false,
                        branchTypes = result.data.branchTypes,
                        businessTypes = result.data.businessTypes,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingOptions = false,
                        optionsError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value) }
    fun onContactPerson(value: String) = _uiState.update { it.copy(contactPerson = value) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun onBranchType(option: SelectorOption) = _uiState.update { it.copy(branchType = option) }
    fun onBusinessType(option: SelectorOption) = _uiState.update { it.copy(businessType = option) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val branchType = state.branchType ?: return
        val businessType = state.businessType ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.store(
                NewBranch(
                    name = state.name,
                    branchTypeId = branchType.id,
                    businessTypeId = businessType.id,
                    address = state.address,
                    phone = state.phone,
                    contactPerson = state.contactPerson,
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
                AddBranchViewModel(
                    repository = ServiceLocator.provideBranchRepository(context.applicationContext),
                )
            }
        }
    }
}
