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
    canClearOpening: Boolean = false,
    canClearTransactions: Boolean = false,
    steps: List<BranchStep> = BranchForm.steps,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddBranchUiState(
            branchId = branchId,
            values = defaultValues(),
            canClearOpening = canClearOpening,
            canClearTransactions = canClearTransactions,
            steps = steps,
        ),
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
    /**
     * A new branch starts with every toggle off, an active status, and the
     * settings almost every branch turns out to want anyway.
     *
     * Without these the pickers opened empty and had to be answered before the
     * form would save, though the answer was the same one nearly every time.
     * paper_size is not here because its options come from the server; it is
     * filled in once they arrive.
     */
    private fun defaultValues(): Map<String, String> =
        BranchForm.toggleKeys.associateWith { "0" } + mapOf(
            "status" to "1",
            "pad_heading_print" to "1",     // Branch Pad Heading
            "print_size" to "1",            // Normal Printer
            "pad_print_mode" to "software", // Software Generated Pad Head
            "money_format" to "1",          // Taka ... Only
        )

    fun load() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            if (branchId != null) loadForEdit(branchId) else loadOptions()
        }
    }

    private suspend fun loadOptions() {
        when (val result = repository.loadFormOptions()) {
            is Resource.Success -> _uiState.update { state ->
                state.copy(
                    isLoadingOptions = false,
                    branchTypes = result.data.branchTypes,
                    businessTypes = result.data.businessTypes,
                    paperSizes = result.data.paperSizes,
                    // The endpoint sorts is_default first, so the head of the
                    // list is the size this installation prints on. Only filled
                    // when nothing is chosen yet -- reloading the options must
                    // not overwrite a size already picked.
                    values = if (state.values["paper_size"].isNullOrBlank()) {
                        result.data.paperSizes.firstOrNull()
                            ?.let { state.values + ("paper_size" to it.id) }
                            ?: state.values
                    } else {
                        state.values
                    },
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
                                // A meta the branch never wrote arrives as the
                                // boolean false — an unset text field, not the
                                // word "false".
                                if (value == "false") "" else value
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

    fun onClearOpeningRequested() = _uiState.update { it.copy(confirmClearOpening = true) }
    fun onClearOpeningDismissed() = _uiState.update { it.copy(confirmClearOpening = false) }

    /**
     * Clears this branch's opening balances. Not undoable -- the figures are
     * kept nowhere else -- so it is only reached through the confirmation, and
     * what the server reports having cleared stays on screen afterwards rather
     * than passing by in a snackbar.
     */
    fun clearOpening() {
        val id = _uiState.value.branchId ?: return
        if (_uiState.value.isClearingOpening) return

        _uiState.update {
            it.copy(
                confirmClearOpening = false,
                isClearingOpening = true,
                clearedOpeningMessage = null,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.clearOpening(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isClearingOpening = false, clearedOpeningMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isClearingOpening = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onClearTransactionsRequested() = _uiState.update { it.copy(confirmClearTransactions = true) }
    fun onClearTransactionsDismissed() = _uiState.update { it.copy(confirmClearTransactions = false) }

    /**
     * Withdraws every voucher this branch holds from the books in one stroke.
     * Nothing is deleted — the server marks the vouchers inactive, so they stop
     * showing in reports, ledgers and balances while the entries stay on record.
     */
    fun clearTransactions() {
        val id = _uiState.value.branchId ?: return
        if (_uiState.value.clearInFlight) return

        _uiState.update {
            it.copy(
                confirmClearTransactions = false,
                isClearingTransactions = true,
                clearedTransactionsMessage = null,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.clearTransactions(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isClearingTransactions = false, clearedTransactionsMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isClearingTransactions = false,
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
                val appContext = context.applicationContext
                val session = ServiceLocator.provideSessionManager(appContext)
                AddBranchViewModel(
                    repository = ServiceLocator.provideBranchRepository(appContext),
                    branchId = branchId,
                    canClearOpening = com.example.cashbookbd.session.Permissions.has(
                        session.permissions,
                        "branch.opening.clear",
                    ),
                    canClearTransactions = com.example.cashbookbd.session.Permissions.has(
                        session.permissions,
                        "branch.transaction.clear",
                    ),
                    steps = BranchForm.stepsFor(session.permissions),
                )
            }
        }
    }
}
