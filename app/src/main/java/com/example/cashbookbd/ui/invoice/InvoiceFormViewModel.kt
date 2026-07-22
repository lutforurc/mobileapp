package com.example.cashbookbd.ui.invoice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.ELECTRONICS_INVENTORY_SYSTEM_ID
import com.example.cashbookbd.invoice.InvoiceForms
import com.example.cashbookbd.invoice.InvoiceKind
import com.example.cashbookbd.invoice.TRADING_INVENTORY_SYSTEM_ID
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.invoice.model.OrderOption
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
    private val selectorRepository: SelectorRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val spec = InvoiceForms.byKey(invoiceKey)

    /**
     * The current branch's inventory system — the key the web's PurchaseIndex/
     * SalesIndex switch on. The settings `branch` is the same row the web reads
     * via `user/current-branch`. Resolved once from the loaded settings.
     */
    private val inventorySystemId: Int? = sessionManager.state.value.settings?.inventorySystemId

    /**
     * True when this form should submit as an Electronics invoice: the branch
     * runs the Electronics inventory system (id 2) and the spec offers an
     * electronics endpoint — adds a per-line serial number (both kinds) and the
     * installment plan (sales only).
     */
    private val isElectronics: Boolean =
        spec?.electronicsEndpoint != null && inventorySystemId == ELECTRONICS_INVENTORY_SYSTEM_ID

    /**
     * True for a Trading-branch (inventory system 4) form — adds vehicle/order
     * pickers and per-line warehouse/bag/variance. Sales (returns included)
     * keeps the shared endpoint; a purchase posts to [InvoiceSpec.tradingEndpoint].
     */
    private val isTrading: Boolean =
        inventorySystemId == TRADING_INVENTORY_SYSTEM_ID &&
            (spec?.kind == InvoiceKind.SALES || spec?.tradingEndpoint != null)

    /** Last product search results, so a picked option maps back to its unit/price. */
    private var productCache: Map<String, com.example.cashbookbd.ui.invoice.model.InvoiceProduct> = emptyMap()

    /** Last order search results, so a picked order maps back to its full row. */
    private var orderCache: Map<String, OrderOption> = emptyMap()

    private val _uiState = MutableStateFlow(
        InvoiceFormUiState(
            title = spec?.title ?: "Invoice",
            isSupported = spec != null,
            partyLabel = spec?.partyLabel ?: "Select Party",
            amountLabel = spec?.amountLabel ?: "Amount",
            autoFillPrice = spec?.autoFillPrice == true,
            showInvoiceNo = spec?.showInvoiceNo == true,
            showInvoiceDate = spec?.showInvoiceDate == true,
            isElectronics = isElectronics,
            isTrading = isTrading,
            // The installment plan and the sales-order picker are sales-side
            // features; an Electronics/Trading purchase must not show them.
            showInstallment = isElectronics && spec?.kind == InvoiceKind.SALES,
            showSalesOrderPicker = isTrading && spec?.kind == InvoiceKind.SALES,
        )
    )
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    init {
        if (isTrading) loadWarehouses()
    }

    private fun loadWarehouses() {
        viewModelScope.launch {
            val result = selectorRepository.fetch(ReportSelectorSource.WAREHOUSE)
            if (result is Resource.Success) {
                // Prepend "Not Applicable" (empty id) so a picked warehouse can be cleared, as on the web.
                val options = listOf(SelectorOption(id = "", label = "Not Applicable")) + result.data
                _uiState.update { it.copy(warehouses = options) }
            }
        }
    }

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
    fun onSerialNoChange(value: String) = _uiState.update { it.copy(serialNo = value) }
    fun onAmountChange(value: String) = _uiState.update { it.copy(amount = value.decimalOnly()) }
    fun onDiscountChange(value: String) = _uiState.update { it.copy(discount = value.decimalOnly()) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onInvoiceNoChange(value: String) = _uiState.update { it.copy(invoiceNo = value) }
    fun onInvoiceDateChange(date: com.example.cashbookbd.ui.reports.model.SimpleDate) =
        _uiState.update { it.copy(invoiceDate = date) }

    // ---- Installment (Electronics) ----

    fun onInstallmentToggle(on: Boolean) = _uiState.update {
        if (on) it.copy(isInstallment = true)
        // Turning it off clears the plan so a later submit sends none.
        else it.copy(
            isInstallment = false,
            installmentAmount = "",
            installmentsNo = "",
            installmentStartDate = null,
            isEarlyPayment = false,
            earlyDiscount = "",
            earlyPaymentDate = null,
        )
    }

    fun onInstallmentAmountChange(value: String) = _uiState.update { it.copy(installmentAmount = value.decimalOnly()) }
    fun onInstallmentsNoChange(value: String) = _uiState.update { it.copy(installmentsNo = value.filter(Char::isDigit)) }
    fun onInstallmentStartDate(date: com.example.cashbookbd.ui.reports.model.SimpleDate) =
        _uiState.update { it.copy(installmentStartDate = date) }

    fun onEarlyPaymentToggle(on: Boolean) = _uiState.update {
        if (on) it.copy(
            isEarlyPayment = true,
            // Web defaults the eligibility date to 90 days out; the user can change it.
            earlyPaymentDate = it.earlyPaymentDate ?: com.example.cashbookbd.ui.reports.model.SimpleDate.today().plusDays(90),
        ) else it.copy(isEarlyPayment = false, earlyDiscount = "", earlyPaymentDate = null)
    }

    fun onEarlyDiscountChange(value: String) = _uiState.update { it.copy(earlyDiscount = value.decimalOnly()) }
    fun onEarlyPaymentDate(date: com.example.cashbookbd.ui.reports.model.SimpleDate) =
        _uiState.update { it.copy(earlyPaymentDate = date) }

    // ---- Trading ----

    fun onVehicleNumberChange(value: String) = _uiState.update { it.copy(vehicleNumber = value) }
    fun onBagChange(value: String) = _uiState.update { it.copy(bag = value.decimalOnly()) }
    fun onVarianceChange(value: String) = _uiState.update { it.copy(variance = value.decimalOnly()) }
    fun onWarehouseSelected(option: SelectorOption) = _uiState.update { it.copy(selectedWarehouse = option) }

    fun onVarianceTypeSelected(option: SelectorOption) = _uiState.update {
        // Clearing the direction ("Not Applicable") also clears any typed variance.
        if (option.id.isEmpty()) it.copy(varianceType = option, variance = "")
        else it.copy(varianceType = option)
    }

    /** Searches purchase orders (order_type 1) for the picker. */
    suspend fun searchPurchaseOrders(query: String): Resource<List<SelectorOption>> =
        invoiceRepository.searchOrders(query, orderType = "1").mapOrders()

    /** Searches sales orders (order_type 2) for the picker. */
    suspend fun searchSalesOrders(query: String): Resource<List<SelectorOption>> =
        invoiceRepository.searchOrders(query, orderType = "2").mapOrders()

    private fun Resource<List<OrderOption>>.mapOrders(): Resource<List<SelectorOption>> = when (this) {
        is Resource.Success -> {
            orderCache = data.associateBy { it.id }
            Resource.Success(data.map { SelectorOption(it.id, it.orderNumber, it.customerName) })
        }
        is Resource.Error -> this
        Resource.Loading -> Resource.Loading
    }

    /** A purchase order only records its id/number — no auto-fill. */
    fun onPurchaseOrderSelected(option: SelectorOption) {
        _uiState.update { it.copy(purchaseOrder = orderCache[option.id]) }
    }

    /**
     * A sales order fills the current entry line: it resolves the order's customer
     * to a party account, its product to a product option, and copies the order
     * qty and rate (mirroring the web). Lookups that miss leave that field as-is.
     */
    fun onSalesOrderSelected(option: SelectorOption) {
        val order = orderCache[option.id] ?: return
        _uiState.update { it.copy(salesOrder = order) }
        viewModelScope.launch {
            if (order.customerName.isNotBlank()) {
                (ledgerRepository.searchLedgers(order.customerName, acType = "3") as? Resource.Success)
                    ?.data?.firstOrNull()
                    ?.let { c -> _uiState.update { it.copy(party = TxnSelection(c.id.toString(), c.name)) } }
            }
            if (order.productName.isNotBlank()) {
                (invoiceRepository.searchProducts(order.productName) as? Resource.Success)
                    ?.data?.firstOrNull()
                    ?.let { p ->
                        productCache = productCache + (p.id to p)
                        _uiState.update {
                            it.copy(
                                selectedProduct = p,
                                qty = order.remainingQty?.takeIf { q -> q > 0 }?.toString() ?: it.qty,
                                price = order.rate?.takeIf { r -> r > 0 }?.toString() ?: it.price,
                            )
                        }
                    }
            }
        }
    }

    /** Adds the current product entry as a line and clears the entry fields. */
    fun addLine() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val qty = state.qty.toDoubleOrNull() ?: return
        val price = state.price.toDoubleOrNull() ?: return
        if (qty <= 0 || price <= 0) return
        _uiState.update {
            it.copy(
                lines = it.lines + InvoiceLine(
                    product = product,
                    qty = qty,
                    price = price,
                    serialNo = if (it.isElectronics) it.serialNo.trim() else "",
                    warehouseId = if (it.isTrading) it.selectedWarehouse?.id.orEmpty() else "",
                    warehouseName = if (it.isTrading) it.selectedWarehouse?.label.orEmpty() else "",
                    bag = if (it.isTrading) it.bag.trim() else "",
                    variance = if (it.isTrading && it.varianceEnabled) it.variance.trim() else "",
                    varianceType = if (it.isTrading) it.varianceType.id else "",
                ),
                selectedProduct = null,
                qty = "",
                price = "",
                serialNo = "",
                // Keep the picked warehouse (usually the same across lines); clear the rest.
                bag = "",
                variance = "",
                varianceType = VARIANCE_TYPES.first(),
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
                invoiceDate = if (state.showInvoiceDate) state.invoiceDate.toApi() else "",
                electronics = isElectronics,
                installment = if (state.showInstallment && state.isInstallment) state.toInstallmentInput() else null,
                trading = if (isTrading) state.toTradingExtras() else null,
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
                        serialNo = "",
                        lines = emptyList(),
                        amount = "",
                        discount = "",
                        notes = "",
                        invoiceNo = "",
                        isInstallment = false,
                        installmentAmount = "",
                        installmentsNo = "",
                        installmentStartDate = null,
                        isEarlyPayment = false,
                        earlyDiscount = "",
                        earlyPaymentDate = null,
                        vehicleNumber = "",
                        purchaseOrder = null,
                        salesOrder = null,
                        selectedWarehouse = null,
                        bag = "",
                        variance = "",
                        varianceType = VARIANCE_TYPES.first(),
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
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}
