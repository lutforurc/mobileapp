package com.example.cashbookbd.ui.invoice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InvoiceOutcome
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

/** The fixed Cash chart-of-accounts id the web's auto-amount rule keys on. */
private const val CASH_ACCOUNT_ID = "17"

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
    private val sessionRepository: com.example.cashbookbd.data.repository.SessionRepository? = null,
    private val transactionRepository: com.example.cashbookbd.data.repository.TransactionRepository? = null,
) : ViewModel() {

    private val spec = InvoiceForms.byKey(invoiceKey)

    /**
     * The current branch's inventory system — the key the web's PurchaseIndex/
     * SalesIndex switch on. The settings `branch` is the same row the web reads
     * via `user/current-branch`. Resolved once from the loaded settings.
     */
    private var inventorySystemId: Int? = sessionManager.state.value.settings?.inventorySystemId

    /**
     * True when this form should submit as an Electronics invoice: the branch
     * runs the Electronics inventory system (id 2) and the spec offers an
     * electronics endpoint — adds a per-line serial number (both kinds) and the
     * installment plan (sales only).
     */
    private var isElectronics: Boolean =
        spec?.electronicsEndpoint != null && inventorySystemId == ELECTRONICS_INVENTORY_SYSTEM_ID

    /**
     * True for a Trading-branch (inventory system 4) form — adds vehicle/order
     * pickers and per-line warehouse/bag/variance. Sales (returns included)
     * keeps the shared endpoint; a purchase posts to [InvoiceSpec.tradingEndpoint].
     */
    private var isTrading: Boolean =
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
            showVehicleNumber = !(isElectronics && spec?.kind == InvoiceKind.SALES),
            showExtraCharges = isElectronics && spec?.kind == InvoiceKind.SALES,
            allowBlankAmount = isTrading && spec?.isReturn != true,
        )
    )
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    /** Electronics sales: a hand-typed amount stops the cash auto-fill (web). */
    private var cashAmountManuallyEdited = false

    init {
        // Every web variant has the per-line warehouse picker.
        loadWarehouses()
        // Trading invoices carry the invoice-level tracked product; with no
        // party picked yet, only the every-party products come back.
        if (isTrading && spec?.isReturn != true) loadTrackedProducts(coa4Id = null)
        // The branch's inventory system may have changed since login — the web
        // index refetches the current branch on mount, so refresh settings and
        // re-derive the variant flags when they land.
        viewModelScope.launch {
            if (sessionRepository?.refresh() !is Resource.Success) return@launch
            val fresh = sessionManager.state.value.settings?.inventorySystemId
            if (fresh == inventorySystemId) return@launch
            inventorySystemId = fresh
            isElectronics = spec?.electronicsEndpoint != null &&
                fresh == ELECTRONICS_INVENTORY_SYSTEM_ID
            isTrading = fresh == TRADING_INVENTORY_SYSTEM_ID &&
                (spec?.kind == InvoiceKind.SALES || spec?.tradingEndpoint != null)
            _uiState.update {
                it.copy(
                    isElectronics = isElectronics,
                    isTrading = isTrading,
                    showInstallment = isElectronics && spec?.kind == InvoiceKind.SALES,
                    showSalesOrderPicker = isTrading && spec?.kind == InvoiceKind.SALES,
                    showVehicleNumber = !(isElectronics && spec?.kind == InvoiceKind.SALES),
                    showExtraCharges = isElectronics && spec?.kind == InvoiceKind.SALES,
                    allowBlankAmount = isTrading && spec?.isReturn != true,
                )
            }
        }
    }

    // ---- Cash (17) party rule ---------------------------------------------

    /**
     * The web's cash-party amount: `max(0, lines + extra charges − discount)`,
     * rounded the way that variant's form rounds it — whole taka on Trading and
     * Electronics sales, floored on the Construction/General purchase, two
     * decimals elsewhere. Purchase returns keep the raw figure.
     */
    private fun cashAutoAmount(s: InvoiceFormUiState): String {
        val net = (s.total + s.extraChargesTotal - (s.discount.toDoubleOrNull() ?: 0.0))
            .coerceAtLeast(0.0)
        return when {
            spec?.isReturn == true && spec.kind == InvoiceKind.PURCHASE ->
                if (net % 1.0 == 0.0) net.toLong().toString() else net.toString()
            spec?.isReturn == true -> String.format(java.util.Locale.US, "%.2f", net)
            isTrading || s.showExtraCharges -> Math.round(net).toString()
            isElectronics -> String.format(java.util.Locale.US, "%.2f", net)
            spec?.kind == InvoiceKind.PURCHASE -> kotlin.math.floor(net).toLong().toString()
            else -> String.format(java.util.Locale.US, "%.2f", net)
        }
    }

    /** Re-derives the auto amount while a Cash party is selected. */
    private fun recalcCashAmount() {
        val s = _uiState.value
        if (s.party?.id != CASH_ACCOUNT_ID) return
        // Electronics sales stays hand-editable; a manual edit wins.
        if (s.showExtraCharges && cashAmountManuallyEdited) return
        _uiState.update { it.copy(amount = cashAutoAmount(it)) }
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
        val wasCash = _uiState.value.party?.id == CASH_ACCOUNT_ID
        val isCash = party.id == CASH_ACCOUNT_ID
        val partyChanged = _uiState.value.party?.id != party.id
        cashAmountManuallyEdited = false
        _uiState.update {
            it.copy(
                party = party,
                // Cash locks the auto amount (Electronics sales stays editable);
                // leaving cash resets it — '' on Trading, '0' elsewhere (web).
                amountLocked = isCash && !it.showExtraCharges,
                amount = when {
                    isCash -> cashAutoAmount(it)
                    wasCash -> if (isTrading) "" else "0"
                    else -> it.amount
                },
            )
        }
        // A new party means a new tracked-product list (party-scoped, web).
        if (partyChanged && isTrading && spec?.isReturn != true) {
            _uiState.update { it.copy(trackedProduct = null) }
            loadTrackedProducts(coa4Id = party.id)
        }
    }

    /**
     * The party-scoped tracked products a Trading invoice may be counted
     * against. Empty (tracking off, no permission) hides the dropdown.
     */
    private fun loadTrackedProducts(coa4Id: String?) {
        val repo = transactionRepository ?: return
        val context = if (spec?.kind == InvoiceKind.SALES) "sales" else "purchase"
        viewModelScope.launch {
            val products = repo.fetchTrackedProducts(context, coa4Id)
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

    fun onAmountChange(value: String) {
        // The locked cash amount is not typable; an Electronics-sales edit
        // switches the field to manual (the web's manual-override flag).
        if (_uiState.value.amountLocked) return
        if (_uiState.value.showExtraCharges && _uiState.value.party?.id == CASH_ACCOUNT_ID) {
            cashAmountManuallyEdited = true
        }
        _uiState.update { it.copy(amount = value.decimalOnly()) }
    }

    fun onDiscountChange(value: String) {
        _uiState.update { it.copy(discount = value.decimalOnly()) }
        recalcCashAmount()
    }

    fun onServiceChargeChange(value: String) {
        _uiState.update { it.copy(serviceCharge = value.decimalOnly()) }
        recalcCashAmount()
    }

    fun onTdsAmountChange(value: String) {
        _uiState.update { it.copy(tdsAmount = value.decimalOnly()) }
        recalcCashAmount()
    }

    fun onTransportationChange(value: String) {
        _uiState.update { it.copy(transportationAmt = value.decimalOnly()) }
        recalcCashAmount()
    }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onInvoiceNoChange(value: String) = _uiState.update { it.copy(invoiceNo = value) }
    fun onInvoiceDateChange(date: com.example.cashbookbd.ui.reports.model.SimpleDate) =
        _uiState.update { it.copy(invoiceDate = date, invoiceDateTouched = true) }

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
            if (order.partyId.isNotBlank()) {
                // The order names its party by id — the name search matches on
                // substrings ("Trade Link" also finds "N S Trade Link") and is
                // only the fallback for orders that carry no id (web ea40a1d).
                _uiState.update { it.copy(party = TxnSelection(order.partyId, order.customerName)) }
            } else if (order.customerName.isNotBlank()) {
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
                    // Every web variant carries the picked warehouse per line.
                    warehouseId = it.selectedWarehouse?.id.orEmpty(),
                    warehouseName = it.selectedWarehouse?.label.orEmpty(),
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
        recalcCashAmount()
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
        recalcCashAmount()
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        send(state, allowNegative = false)
    }

    /**
     * "Continue" after the stock-shortage question: re-posts the invoice that
     * was held, not whatever the form holds by then — the operator may have
     * touched the screen while reading.
     */
    fun confirmShortage() {
        val held = heldSubmission ?: return
        heldSubmission = null
        _uiState.update { it.copy(stockShortage = null) }
        send(held, allowNegative = true)
    }

    fun dismissShortage() {
        heldSubmission = null
        _uiState.update { it.copy(stockShortage = null) }
    }

    /**
     * The invoice waiting on the stock-shortage answer, held whole so Continue
     * can send exactly what was refused.
     */
    private var heldSubmission: InvoiceFormUiState? = null

    private fun send(state: InvoiceFormUiState, allowNegative: Boolean) {
        val currentSpec = spec ?: return

        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = invoiceRepository.submit(
                spec = currentSpec,
                party = state.party!!,
                lines = state.lines,
                // Trading saves a blank amount as 0, like the web's '' → '0'.
                amount = state.amount.trim().ifBlank { if (state.allowBlankAmount) "0" else "" },
                discount = state.discount.toDoubleOrNull() ?: 0.0,
                notes = state.notes.trim(),
                invoiceNo = state.invoiceNo.trim(),
                // Returns always post their required date; a purchase posts
                // invoice_date only once the user actually picked one (the web
                // leaves it blank otherwise).
                invoiceDate = when {
                    !state.showInvoiceDate -> ""
                    currentSpec.isReturn || state.invoiceDateTouched -> state.invoiceDate.toApi()
                    else -> ""
                },
                electronics = isElectronics,
                installment = if (state.showInstallment && state.isInstallment) state.toInstallmentInput() else null,
                trading = if (isTrading) state.toTradingExtras() else null,
                vehicleNumber = state.vehicleNumber.trim(),
                serviceCharge = state.serviceCharge.toDoubleOrNull() ?: 0.0,
                tdsAmount = state.tdsAmount.toDoubleOrNull() ?: 0.0,
                transportationAmt = state.transportationAmt.toDoubleOrNull() ?: 0.0,
                // The cash customer's invoice leaves nothing owing to track.
                trackedProductId = if (state.party?.id == CASH_ACCOUNT_ID) {
                    ""
                } else {
                    state.trackedProduct?.id.orEmpty()
                },
                allowNegative = allowNegative,
            )
            when (result) {
                is Resource.Success -> when (val outcome = result.data) {
                    // Not enough stock: nothing saved, nothing wrong. Hold the
                    // invoice and put the question.
                    is InvoiceOutcome.StockShortage -> {
                        heldSubmission = state
                        _uiState.update {
                            it.copy(isSubmitting = false, stockShortage = outcome.warning)
                        }
                    }

                    is InvoiceOutcome.Saved -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            message = outcome.message,
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
                            invoiceDateTouched = false,
                            serviceCharge = "",
                            tdsAmount = "",
                            transportationAmt = "",
                            amountLocked = false,
                            trackedProduct = null,
                        )
                    }
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
                    sessionRepository = ServiceLocator.provideSessionRepository(appContext),
                    transactionRepository = ServiceLocator.provideTransactionRepository(appContext),
                )
            }
        }
    }
}
