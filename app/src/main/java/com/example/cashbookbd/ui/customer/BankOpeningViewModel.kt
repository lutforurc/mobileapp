package com.example.cashbookbd.ui.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BankOpeningRepository
import com.example.cashbookbd.data.repository.BankOpeningRow
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Bank Opening screen: the grouped account list and per-row save/delete. */
class BankOpeningViewModel(
    private val repository: BankOpeningRepository,
    /**
     * The branch's "Opening ongoing" flag (`is_opening == 1`). Off, the web
     * screen replaces itself with a notice and never calls the API — so
     * neither does this one.
     */
    private val openingEnabled: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankOpeningUiState())
    val uiState: StateFlow<BankOpeningUiState> = _uiState.asStateFlow()

    init {
        if (openingEnabled) load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Reloads re-run the search the list shows, never text still being
            // typed — a post-save refresh must not re-filter the list unasked.
            when (val result = repository.loadAccounts(_uiState.value.submittedSearch)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    fun onSearch() {
        _uiState.update { it.copy(submittedSearch = it.searchQuery) }
        load()
    }

    fun startEdit(row: BankOpeningRow) = _uiState.update {
        it.copy(
            editing = row,
            // A zero balance is "unset" — the box starts empty, like the list
            // shows a dash. The plain string keeps trailing ".0" noise out.
            editAmount = if (row.openingBalance == 0.0) "" else formatEditAmount(row.openingBalance),
            editError = null,
        )
    }

    fun onEditAmount(value: String) = _uiState.update { it.copy(editAmount = value, editError = null) }

    /** No-op while a save is in flight: dismissing then would drop the guard
     *  and let the late result close or overwrite whatever dialog came next. */
    fun cancelEdit() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editing = null, editError = null) }
    }

    /**
     * Saves the dialog's figure. An emptied box saves as zero — that is how an
     * opening is withdrawn without the delete permission. A figure equal to
     * the one standing closes quietly (the server would answer "No changes
     * were made.") — unless its voucher is gone, when re-sending the same
     * figure is exactly how the ledger entry comes back.
     */
    fun saveEdit() {
        val state = _uiState.value
        val row = state.editing ?: return
        if (state.isSaving) return

        // Grouping commas are how a keypad writes big figures, not an error.
        val text = state.editAmount.trim().replace(",", "")
        val amount = if (text.isEmpty()) 0.0 else text.toDoubleOrNull()
        if (amount == null) {
            _uiState.update { it.copy(editError = "Opening balance must be a number.") }
            return
        }
        if (amount == row.openingBalance && !row.voucherLost) {
            _uiState.update { it.copy(editing = null, editError = null) } // Nothing changed — just close.
            return
        }

        _uiState.update { it.copy(isSaving = true, editError = null) }
        viewModelScope.launch {
            when (val result = repository.updateOpening(row.id, amount)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, editing = null, actionMessage = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    // The refusal shows inside the dialog: the snackbar sits
                    // behind its scrim, where "approved voucher" would go by
                    // unseen while the dialog just sat there refusing to close.
                    it.copy(
                        isSaving = false,
                        editError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Opening-balance delete (the voucher goes to the trash) ----

    fun askDelete(row: BankOpeningRow) = _uiState.update { it.copy(deleteRow = row) }

    /** No-op while the delete is in flight, for the same reason as [cancelEdit]. */
    fun cancelDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(deleteRow = null) }
    }

    /**
     * Deletes the pending row's opening balance and its journal voucher. The
     * server may refuse — approved voucher, another branch — with its reason.
     */
    fun confirmDelete() {
        val state = _uiState.value
        val row = state.deleteRow ?: return
        if (state.isDeleting) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.deleteOpening(row.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteRow = null,
                            // A dialog draft would read as though the balance
                            // survived, so any open edit closes with it.
                            editing = null,
                            actionMessage = result.data,
                        )
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteRow = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        /**
         * A plain decimal string for the edit box: no ".0" tail on whole
         * figures, and no scientific notation — Double.toString turns a
         * crore-scale fraction into "1.234567855E7", which BigDecimal's
         * plain form writes back out in full.
         */
        private fun formatEditAmount(value: Double): String =
            java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                BankOpeningViewModel(
                    repository = ServiceLocator.provideBankOpeningRepository(context.applicationContext),
                    openingEnabled = ServiceLocator.provideSessionManager(context.applicationContext)
                        .state.value.settings?.openingOngoing == true,
                )
            }
        }
    }
}
