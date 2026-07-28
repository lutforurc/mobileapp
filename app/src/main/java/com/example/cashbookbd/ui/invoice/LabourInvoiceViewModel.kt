package com.example.cashbookbd.ui.invoice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LabourInvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.invoice.model.LabourItem
import com.example.cashbookbd.ui.invoice.model.LabourLine
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Drives the Construction Labour Invoice form (the web's
 * ConstructionLabourInvoice): the supplier account, a running list of labour
 * item lines, the auto-computed payment amount, discount and notes, then
 * submits via [LabourInvoiceRepository].
 */
class LabourInvoiceViewModel(
    private val labourInvoiceRepository: LabourInvoiceRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    /** Last item search results, so a picked option maps back to its unit/price. */
    private var itemCache: Map<String, LabourItem> = emptyMap()

    private val _uiState = MutableStateFlow(LabourInvoiceUiState())
    val uiState: StateFlow<LabourInvoiceUiState> = _uiState.asStateFlow()

    // ---- Header fields -----------------------------------------------------

    fun onSupplierSelected(party: TxnSelection) = _uiState.update {
        // The Cash supplier's payment is forced — snap it to the computed value
        // so a stale manual edit can't ride along into the disabled field.
        val next = it.copy(supplier = party)
        if (next.isCashSupplier) next.withComputedPayment() else next
    }

    /** Supplier accounts — COA level-4 with acType=3, like the other invoice forms. */
    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "3")

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onBillNoChange(value: String) = _uiState.update { it.copy(billNo = value) }
    fun onBillDateChange(date: SimpleDate) = _uiState.update { it.copy(billDate = date) }

    fun onDiscountChange(value: String) = _uiState.update {
        it.copy(discount = value.decimalOnly()).withComputedPayment()
    }

    /** Manual payment edits — ignored for Cash, whose amount is forced. */
    fun onPaymentChange(value: String) = _uiState.update {
        if (it.isCashSupplier) it else it.copy(paymentAmt = value.decimalOnly())
    }

    // ---- Line entry --------------------------------------------------------

    /** Item search that caches the full items so a pick can read unit/price. */
    suspend fun searchLabourItems(query: String): Resource<List<SelectorOption>> =
        when (val result = labourInvoiceRepository.searchLabourItems(query)) {
            is Resource.Success -> {
                itemCache = result.data.associateBy { it.id }
                Resource.Success(
                    result.data.map {
                        SelectorOption(id = it.id, label = it.name, sublabel = it.category.ifBlank { it.unit })
                    }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onItemSelected(option: SelectorOption) {
        val item = itemCache[option.id] ?: return
        _uiState.update {
            it.copy(
                selectedItem = item,
                // Pre-fill the price from the item's purchase price (label_4).
                price = item.purchasePrice?.takeIf { p -> p > 0 }?.toString() ?: it.price,
            )
        }
    }

    fun onQtyChange(value: String) = _uiState.update { it.copy(qty = value.decimalOnly()) }
    fun onPriceChange(value: String) = _uiState.update { it.copy(price = value.decimalOnly()) }

    /**
     * Adds the current entry as a line — or, when a table row is being edited,
     * replaces that row — then clears the entry fields and recomputes payment.
     */
    fun addLine() {
        val state = _uiState.value
        if (!state.canAddLine) return
        val line = LabourLine(
            item = state.selectedItem!!,
            qty = state.qty.trim(),
            price = state.price.trim(),
        )
        _uiState.update {
            val editing = it.editingIndex
            it.copy(
                lines = if (editing != null && editing in it.lines.indices) {
                    it.lines.mapIndexed { i, existing -> if (i == editing) line else existing }
                } else {
                    it.lines + line
                },
                editingIndex = null,
                selectedItem = null,
                qty = "",
                price = "",
            ).withComputedPayment()
        }
    }

    /** Loads a table row back into the entry fields; Add then replaces it. */
    fun editLine(index: Int) = _uiState.update {
        val line = it.lines.getOrNull(index) ?: return@update it
        it.copy(
            editingIndex = index,
            selectedItem = line.item,
            qty = line.qty,
            price = line.price,
        )
    }

    fun removeLine(index: Int) = _uiState.update {
        if (index !in it.lines.indices) return@update it
        val editing = it.editingIndex
        it.copy(
            lines = it.lines.filterIndexed { i, _ -> i != index },
            // Keep an in-progress edit pointing at the same row.
            editingIndex = when {
                editing == null || editing == index -> null
                editing > index -> editing - 1
                else -> editing
            },
        ).withComputedPayment()
    }

    // ---- Submit ------------------------------------------------------------

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = labourInvoiceRepository.submit(
                supplier = state.supplier!!,
                billNo = state.billNo.trim(),
                billDate = state.billDate?.toApi().orEmpty(),
                paymentAmt = state.paymentAmt.trim(),
                discount = state.discount.toDoubleOrNull() ?: 0.0,
                notes = state.notes.trim(),
                lines = state.lines,
            )
            when (result) {
                // Full reset like the web — only the success message survives.
                is Resource.Success -> _uiState.update {
                    LabourInvoiceUiState(message = result.data, isError = false)
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /**
     * Payment auto-calc, as the web's effect: total − discount, kept at two
     * decimals. Runs on every line/discount change (and a Cash supplier pick).
     */
    private fun LabourInvoiceUiState.withComputedPayment(): LabourInvoiceUiState {
        val net = total - (discount.toDoubleOrNull() ?: 0.0)
        return copy(paymentAmt = String.format(Locale.US, "%.2f", net))
    }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                LabourInvoiceViewModel(
                    labourInvoiceRepository = ServiceLocator.provideLabourInvoiceRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}
