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

/**
 * Backs the branch form in both modes: with a [branchId] it loads that branch
 * and updates it, without one it loads the pickers and creates a new branch.
 */
class AddBranchViewModel(
    private val repository: BranchRepository,
    private val branchId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBranchUiState(branchId = branchId))
    val uiState: StateFlow<AddBranchUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        if (branchId != null) {
            loadForEdit(branchId)
            return
        }
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

    /**
     * Loads the branch and prefills the form. The pickers arrive in the same
     * response, so this replaces [loadOptions] rather than running alongside it.
     */
    private fun loadForEdit(id: String) {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            when (val result = repository.loadForEdit(id)) {
                is Resource.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoadingOptions = false,
                            branchTypes = data.branchTypes,
                            businessTypes = data.businessTypes,
                            existingFields = data.fields,
                            name = data.field("name"),
                            address = data.field("address"),
                            phone = data.field("phone"),
                            contactPerson = data.field("contact_person"),
                            email = data.field("email"),
                            branchType = data.branchTypes
                                .firstOrNull { o -> o.id == data.field("branch_types_id") },
                            businessType = data.businessTypes
                                .firstOrNull { o -> o.id == data.field("business_type_id") },
                        )
                    }
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

        val edited = NewBranch(
            name = state.name,
            branchTypeId = branchType.id,
            businessTypeId = businessType.id,
            address = state.address,
            phone = state.phone,
            contactPerson = state.contactPerson,
            email = state.email,
        )

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = if (state.isEditing) {
                repository.update(existing = state.existingFields, branch = edited)
            } else {
                repository.store(edited)
            }
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
        fun provideFactory(context: Context, branchId: String? = null) = viewModelFactory {
            initializer {
                AddBranchViewModel(
                    repository = ServiceLocator.provideBranchRepository(context.applicationContext),
                    branchId = branchId,
                )
            }
        }
    }
}
