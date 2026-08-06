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

    /** Product-tracking context — the bank forms mirror the web's field. */
    private val trackedContext: String? = when (spec?.kind) {
        com.example.cashbookbd.transaction.TxnKind.BANK_RECEIVED -> "received"
        com.example.cashbookbd.transaction.TxnKind.BANK_PAYMENT -> "payment"
        else -> null
    }

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            title = spec?.title ?: "Transaction",
            isSupported = spec != null,
            fields = spec?.fields.orEmpty(),
            remarksLabel = spec?.remarksLabel ?: "Remarks",
            amountLabel = spec?.amountLabel ?: "Amount (Tk.)",
            isBankBatch = trackedContext != null,
            batchTotalLabel = if (trackedContext == "received") "Received Total" else "Payment Total",
        )
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    init {
        if (spec?.fields?.any { it.picker == TxnPicker.BANK } == true) loadBankAccounts()
        // With no party picked yet, the every-party products come back.
        if (trackedContext != null) loadTrackedProducts(coa4Id = null)
    }

    /**
     * The party-scoped tracked products, like the web's useTrackedProducts:
     * refetched for the picked transaction account; an empty answer hides the
     * dropdown. A stale selection no longer in the list is dropped.
     */
    private fun loadTrackedProducts(coa4Id: String?) {
        val context = trackedContext ?: return
        viewModelScope.launch {
            val products = transactionRepository.fetchTrackedProducts(context, coa4Id)
                .map { (id, name) -> SelectorOption(id, name) }
            _uiState.update { state ->
                val stillValid = state.trackedProduct?.let { sel -> products.any { it.id == sel.id } } == true
                state.copy(
                    trackedProducts = products,
                    trackedProduct = if (stillValid) state.trackedProduct else null,
                )
            }
        }
    }

    fun onTrackedProductSelected(option: SelectorOption) {
        _uiState.update { it.copy(trackedProduct = option) }
    }

    // ---- Bank multi-row batch (the web's Add New table) --------------------

    /** Adds the typed row to the batch; the bank account stays put. */
    fun addLine() {
        val state = _uiState.value
        val account = state.selections["account"] ?: return
        val amount = state.amount.toDoubleOrNull() ?: return
        if (amount <= 0) return
        _uiState.update {
            it.copy(
                lines = it.lines + com.example.cashbookbd.data.repository.CashVoucherLine(
                    account = account,
                    remarks = it.remarks.trim(),
                    amount = amount,
                    trackedProductId = it.trackedProduct?.id.orEmpty(),
                    trackedProductName = it.trackedProduct?.label.orEmpty(),
                ),
                // The web clears the whole in-form row after Add — account,
                // remarks, amount and the picked product; the bank stays.
                selections = it.selections - "account",
                remarks = "",
                amount = "",
                trackedProduct = null,
            )
        }
        // Back to the every-party product list for the next row.
        if (trackedContext != null) loadTrackedProducts(coa4Id = null)
    }

    /** Loads a pending row back into the form (and removes it from the batch). */
    fun editLine(index: Int) {
        _uiState.update { state ->
            val line = state.lines.getOrNull(index) ?: return@update state
            state.copy(
                selections = state.selections + ("account" to line.account),
                remarks = line.remarks,
                amount = if (line.amount % 1.0 == 0.0) {
                    line.amount.toLong().toString()
                } else {
                    line.amount.toString()
                },
                trackedProduct = line.trackedProductId.takeIf { it.isNotBlank() }
                    ?.let { SelectorOption(id = it, label = line.trackedProductName) },
                lines = state.lines.filterIndexed { i, _ -> i != index },
            )
        }
        // The loaded row's party scopes the product list again.
        if (trackedContext != null) {
            loadTrackedProducts(coa4Id = _uiState.value.selections["account"]?.id)
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
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
        val accountChanged = key == "account" &&
            _uiState.value.selections[key]?.id != selection.id
        _uiState.update { it.copy(selections = it.selections + (key to selection)) }
        // A new party means a new product list — the web clears the picked
        // product on an account change (isSameAccount) and refetches.
        if (accountChanged && trackedContext != null) {
            _uiState.update { it.copy(trackedProduct = null) }
            loadTrackedProducts(coa4Id = selection.id)
        }
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

        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = if (state.isBankBatch) {
                transactionRepository.submitBankVoucher(
                    spec = currentSpec,
                    bank = state.selections.getValue("bank"),
                    lines = state.lines,
                )
            } else {
                transactionRepository.submit(
                    spec = currentSpec,
                    selections = state.selections,
                    amount = state.amount.toDoubleOrNull() ?: 0.0,
                    remarks = state.remarks.trim(),
                    trackedProductId = state.trackedProduct?.id.orEmpty(),
                )
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    // Clear the form for the next entry on success. The bank
                    // batch keeps the bank account, like the web.
                    it.copy(
                        isSubmitting = false,
                        message = result.data,
                        isError = false,
                        selections = if (it.isBankBatch) {
                            it.selections.filterKeys { k -> k == "bank" }
                        } else {
                            emptyMap()
                        },
                        amount = "",
                        remarks = "",
                        trackedProduct = null,
                        lines = emptyList(),
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
