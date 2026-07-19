package com.example.cashbookbd.ui.branch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BranchRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the branch wizard in both modes: with a [branchId] it loads that branch
 * and updates it, without one it loads the pickers and creates a new branch.
 */
class AddBranchViewModel(
    private val repository: BranchRepository,
    private val branchId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddBranchUiState(branchId = branchId, values = defaultValues()),
    )
    val uiState: StateFlow<AddBranchUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * A new branch starts with every toggle off and an active status, matching
     * the web's initial form state. Without this the toggles would be absent
     * from the payload rather than explicitly "0".
     */
    private fun defaultValues(): Map<String, String> =
        BranchForm.toggleKeys.associateWith { "0" } + mapOf("status" to "1")

    fun load() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            if (branchId != null) loadForEdit(branchId) else loadOptions()
        }
    }

    private suspend fun loadOptions() {
        when (val result = repository.loadFormOptions()) {
            is Resource.Success -> _uiState.update {
                it.copy(
                    isLoadingOptions = false,
                    branchTypes = result.data.branchTypes,
                    businessTypes = result.data.businessTypes,
                    paperSizes = result.data.paperSizes,
                )
            }
            is Resource.Error -> _uiState.update { it.failed(result) }
            Resource.Loading -> Unit
        }
    }

    /**
     * Loads the branch and prefills the form. The pickers arrive in the same
     * response, so this replaces [loadOptions] rather than running alongside it.
     */
    private suspend fun loadForEdit(id: String) {
        // branch/branch-edit returns the branch plus the branch/business type
        // lists, but not the paper sizes — those only come from the settings
        // endpoint, so both are fetched and the settings list wins where present.
        val options = (repository.loadFormOptions() as? Resource.Success)?.data
        when (val result = repository.loadForEdit(id)) {
            is Resource.Success -> {
                val data = result.data
                _uiState.update { state ->
                    state.copy(
                        isLoadingOptions = false,
                        branchTypes = data.branchTypes.ifEmpty { options?.branchTypes.orEmpty() },
                        businessTypes = data.businessTypes.ifEmpty { options?.businessTypes.orEmpty() },
                        paperSizes = options?.paperSizes.orEmpty(),
                        existingFields = data.fields,
                        // Normalise the flags: the server sends them as 1/0 but
                        // also as "" or null, and the toggles compare on "1".
                        values = data.fields.mapValues { (key, value) ->
                            if (key in BranchForm.toggleKeys) {
                                if (value.isOn()) "1" else "0"
                            } else {
                                value
                            }
                        },
                    )
                }
            }
            is Resource.Error -> _uiState.update { it.failed(result) }
            Resource.Loading -> Unit
        }
    }

    private fun AddBranchUiState.failed(result: Resource.Error) = copy(
        isLoadingOptions = false,
        optionsError = result.message,
        sessionExpired = sessionExpired || result.isUnauthorized,
    )

    fun onValue(key: String, value: String) =
        _uiState.update { it.copy(values = it.values + (key to value)) }

    fun onToggle(key: String, on: Boolean) = onValue(key, if (on) "1" else "0")

    fun goToStep(index: Int) = _uiState.update {
        it.copy(currentStep = index.coerceIn(it.steps.indices))
    }

    fun nextStep() = goToStep(_uiState.value.currentStep + 1)

    fun previousStep() = goToStep(_uiState.value.currentStep - 1)

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = if (state.isEditing) {
                repository.update(existing = state.existingFields, values = state.values)
            } else {
                repository.store(state.values)
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
