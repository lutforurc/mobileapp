package com.example.cashbookbd.ui.transaction

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.transaction.TransactionForms
import com.example.cashbookbd.transaction.TxnPicker
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives a single transaction entry form (resolved from [txnKey]). Collects the
 * account selections, amount and remarks, and submits via [TransactionRepository];
 * loads the bank-account dropdown when the form needs one.
 */
class TransactionFormViewModel(
    txnKey: String,
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val spec = TransactionForms.byKey(txnKey)

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            title = spec?.title ?: "Transaction",
            isSupported = spec != null,
            fields = spec?.fields.orEmpty(),
            remarksLabel = spec?.remarksLabel ?: "Remarks",
            amountLabel = spec?.amountLabel ?: "Amount (Tk.)",
        )
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    init {
        if (spec?.fields?.any { it.picker == TxnPicker.BANK } == true) loadBankAccounts()
    }

    private fun loadBankAccounts() {
        _uiState.update { it.copy(isBankLoading = true, bankError = null) }
        viewModelScope.launch {
            when (val result = transactionRepository.fetchBankAccounts()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isBankLoading = false, bankAccounts = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBankLoading = false,
                        bankError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onFieldSelected(key: String, selection: TxnSelection) {
        _uiState.update { it.copy(selections = it.selections + (key to selection)) }
    }

    fun onAmountChange(value: String) {
        // Keep digits and a single decimal point only.
        val cleaned = value.filterIndexed { i, c -> c.isDigit() || (c == '.' && !value.take(i).contains('.')) }
        _uiState.update { it.copy(amount = cleaned) }
    }

    fun onRemarksChange(value: String) {
        _uiState.update { it.copy(remarks = value) }
    }

    suspend fun searchLedgers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    suspend fun searchEmployees(query: String): Resource<List<SelectorOption>> =
        selectorRepository.fetch(source = ReportSelectorSource.EMPLOYEE, query = query)

    fun submit() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSubmit) return
        val amount = state.amount.toDoubleOrNull() ?: return

        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = transactionRepository.submit(
                spec = currentSpec,
                selections = state.selections,
                amount = amount,
                remarks = state.remarks.trim(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    // Clear the form for the next entry on success.
                    it.copy(
                        isSubmitting = false,
                        message = result.data,
                        isError = false,
                        selections = emptyMap(),
                        amount = "",
                        remarks = "",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onSessionExpiredHandled() {
        _uiState.update { it.copy(sessionExpired = false) }
    }

    companion object {
        fun provideFactory(context: Context, txnKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                TransactionFormViewModel(
                    txnKey = txnKey,
                    transactionRepository = ServiceLocator.provideTransactionRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}
