package com.example.cashbookbd.ui.invoice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceForms
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives a Sales/Purchase invoice form (resolved from [invoiceKey]): the party
 * account, a running list of product lines, the paid/received amount, discount
 * and notes, then submits via [InvoiceRepository].
 */
class InvoiceFormViewModel(
    invoiceKey: String,
    private val invoiceRepository: InvoiceRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val spec = InvoiceForms.byKey(invoiceKey)

    /** Last product search results, so a picked option maps back to its unit/price. */
    private var productCache: Map<String, com.example.cashbookbd.ui.invoice.model.InvoiceProduct> = emptyMap()

    private val _uiState = MutableStateFlow(
        InvoiceFormUiState(
            title = spec?.title ?: "Invoice",
            isSupported = spec != null,
            partyLabel = spec?.partyLabel ?: "Select Party",
            amountLabel = spec?.amountLabel ?: "Amount",
            autoFillPrice = spec?.autoFillPrice == true,
            showInvoiceNo = spec?.showInvoiceNo == true,
        )
    )
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    fun onPartySelected(party: TxnSelection) {
        _uiState.update { it.copy(party = party) }
    }

    /** Party accounts (customers/suppliers) — COA level-4 with acType=3. */
    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "3")

    /** Product search that caches the full products so a pick can read unit/price. */
    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> {
                productCache = result.data.associateBy { it.id }
                Resource.Success(
                    result.data.map { SelectorOption(id = it.id, label = it.name, sublabel = it.unit) }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onProductSelected(option: SelectorOption) {
        val product = productCache[option.id] ?: return
        _uiState.update {
            it.copy(
                selectedProduct = product,
                // Purchase pre-fills the price from the product; sales is typed.
                price = if (it.autoFillPrice && product.purchasePrice != null) {
                    product.purchasePrice.toString()
                } else {
                    it.price
                },
            )
        }
    }

    fun onQtyChange(value: String) = _uiState.update { it.copy(qty = value.decimalOnly()) }
    fun onPriceChange(value: String) = _uiState.update { it.copy(price = value.decimalOnly()) }
    fun onAmountChange(value: String) = _uiState.update { it.copy(amount = value.decimalOnly()) }
    fun onDiscountChange(value: String) = _uiState.update { it.copy(discount = value.decimalOnly()) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onInvoiceNoChange(value: String) = _uiState.update { it.copy(invoiceNo = value) }

    /** Adds the current product entry as a line and clears the entry fields. */
    fun addLine() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val qty = state.qty.toDoubleOrNull() ?: return
        val price = state.price.toDoubleOrNull() ?: return
        if (qty <= 0 || price <= 0) return
        _uiState.update {
            it.copy(
                lines = it.lines + InvoiceLine(product = product, qty = qty, price = price),
                selectedProduct = null,
                qty = "",
                price = "",
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
    }

    fun submit() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = invoiceRepository.submit(
                spec = currentSpec,
                party = state.party!!,
                lines = state.lines,
                amount = state.amount.trim(),
                discount = state.discount.toDoubleOrNull() ?: 0.0,
                notes = state.notes.trim(),
                invoiceNo = state.invoiceNo.trim(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.data,
                        isError = false,
                        party = null,
                        selectedProduct = null,
                        qty = "",
                        price = "",
                        lines = emptyList(),
                        amount = "",
                        discount = "",
                        notes = "",
                        invoiceNo = "",
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    companion object {
        fun provideFactory(context: Context, invoiceKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                InvoiceFormViewModel(
                    invoiceKey = invoiceKey,
                    invoiceRepository = ServiceLocator.provideInvoiceRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}
