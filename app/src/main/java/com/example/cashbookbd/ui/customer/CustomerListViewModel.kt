package com.example.cashbookbd.ui.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.data.repository.CustomerRow
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Customers list: search, pagination, and the per-row opening/ledger edit. */
class CustomerListViewModel(
    private val repository: CustomerRepository,
    /**
     * The branch's "Opening ongoing" flag (`is_opening == 1`). Off, the web
     * list drops its Opening column entirely — so the edit sends no opening
     * figure either, whatever the dialog was seeded with.
     */
    private val openingEnabled: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerListUiState())
    val uiState: StateFlow<CustomerListUiState> = _uiState.asStateFlow()

    init {
        load(page = 1)
    }

    fun load(page: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.loadCustomers(page, CUSTOMERS_PER_PAGE, _uiState.value.searchQuery)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
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
    fun onSearch() = load(page = 1)

    fun nextPage() {
        if (_uiState.value.canNext) load(_uiState.value.currentPage + 1)
    }

    fun prevPage() {
        if (_uiState.value.canPrev) load(_uiState.value.currentPage - 1)
    }

    fun startEdit(row: CustomerRow) = _uiState.update {
        it.copy(
            editing = row,
            // Pre-fill opening when it's still editable; the ledger always pre-fills.
            editOpening = if (row.isOpeningSet) row.opening else "",
            editLedger = row.ledgerPage,
            editError = null,
        )
    }

    fun onEditOpening(value: String) = _uiState.update { it.copy(editOpening = value, editError = null) }
    fun onEditLedger(value: String) = _uiState.update { it.copy(editLedger = value, editError = null) }

    /** No-op while a save is in flight: dismissing then would drop the guard
     *  and let the late result close or overwrite whatever dialog came next. */
    fun cancelEdit() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editing = null, editError = null) }
    }

    fun saveEdit() {
        val state = _uiState.value
        val row = state.editing ?: return
        if (state.isSaving) return
        // The opening may now be re-saved — the server rewrites its voucher in
        // place — so it is sent when changed, and also when the figure stands
        // but its voucher is gone (trashed from the Voucher Delete screen):
        // re-posting the same figure is how the entry comes back, as on the
        // web, which sends the field on every save. An untouched figure with a
        // live voucher is held back to spare the ledger the churn. Ledger only
        // when it changed — re-sending it can trip the character validation
        // (e.g. '#').
        val voucherLost = row.isOpeningSet && row.openingVrNo.isBlank()
        val opening = if (
            openingEnabled &&
            state.editOpening.isNotBlank() &&
            (state.editOpening != row.opening || voucherLost)
        ) {
            state.editOpening
        } else {
            null
        }
        val ledger = state.editLedger.takeIf { it.trim() != row.ledgerPage.trim() }
        if (opening == null && ledger == null) {
            _uiState.update { it.copy(editing = null) } // Nothing changed — just close.
            return
        }
        _uiState.update { it.copy(isSaving = true, editError = null) }
        viewModelScope.launch {
            when (val result = repository.updateOpeningLedger(row.id, opening, ledger)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, editing = null, actionMessage = result.data) }
                    load(_uiState.value.currentPage)
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

    fun askDeleteOpening(row: CustomerRow) = _uiState.update { it.copy(openingDeleteRow = row) }

    /** No-op while the delete is in flight, for the same reason as [cancelEdit]. */
    fun cancelDeleteOpening() {
        if (_uiState.value.isDeletingOpening) return
        _uiState.update { it.copy(openingDeleteRow = null) }
    }

    /**
     * Deletes the pending row's opening balance and its journal voucher. The
     * server may refuse — approved voucher, another branch — with its reason.
     */
    fun confirmDeleteOpening() {
        val state = _uiState.value
        val row = state.openingDeleteRow ?: return
        if (state.isDeletingOpening) return

        _uiState.update { it.copy(isDeletingOpening = true) }
        viewModelScope.launch {
            when (val result = repository.deleteOpeningBalance(row.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeletingOpening = false,
                            openingDeleteRow = null,
                            // A dialog draft would read as though the balance
                            // survived, so any open edit closes with it.
                            editing = null,
                            actionMessage = result.data,
                        )
                    }
                    load(_uiState.value.currentPage)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeletingOpening = false,
                        openingDeleteRow = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                CustomerListViewModel(
                    repository = ServiceLocator.provideCustomerRepository(context.applicationContext),
                    openingEnabled = ServiceLocator.provideSessionManager(context.applicationContext)
                        .state.value.settings?.openingOngoing == true,
                )
            }
        }
    }
}
