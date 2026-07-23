package com.example.cashbookbd.ui.invoice

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.invoice.model.CombinedLine
import com.example.cashbookbd.ui.invoice.model.InvoiceProduct
import com.example.cashbookbd.ui.invoice.model.OrderOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Combined Invoice — a port of the web's TradingCombinedEntry: one form that
 * books a purchase voucher and a sales voucher together (Trading branches only).
 * Pick a supplier and a customer, optionally link a purchase and a sales order,
 * enter a single cash amount (posted to both sides, or only sales via the
 * Both/Sales toggle), add product lines carrying both a purchase and a sales
 * rate, then Save — the server creates both vouchers under one combined number.
 */

data class CombinedInvoiceUiState(
    val showNotesApplyTo: Boolean = false,

    // Header
    val supplier: TxnSelection? = null,
    val customer: TxnSelection? = null,
    val vehicleNumber: String = "",
    val purchaseOrder: OrderOption? = null,
    val salesOrder: OrderOption? = null,
    val amount: String = "",
    /** false = "Both" (amount hits both sides); true = "Sales" (supplier paid 0). */
    val onlySalesPosting: Boolean = false,
    val discount: String = "",
    val notes: String = "",
    /** "both" | "purchase" | "sales" — only meaningful when [showNotesApplyTo]. */
    val notesApplyTo: String = "both",

    // Current line entry
    val selectedProduct: InvoiceProduct? = null,
    val bag: String = "",
    val variance: String = "",
    val varianceType: SelectorOption = VARIANCE_TYPES.first(),
    val qty: String = "",
    val purchaseRate: String = "",
    val salesRate: String = "",

    val lines: List<CombinedLine> = emptyList(),

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val purchaseTotal: Double get() = lines.sumOf { it.purchaseTotal }
    val salesTotal: Double get() = lines.sumOf { it.salesTotal }

    val varianceEnabled: Boolean get() = varianceType.id.isNotEmpty()

    val canAddLine: Boolean
        get() = selectedProduct != null &&
            (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (purchaseRate.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (salesRate.toDoubleOrNull() ?: -1.0) >= 0.0

    val canSubmit: Boolean
        get() = supplier != null &&
            customer != null &&
            (amount.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            lines.isNotEmpty() &&
            !isSubmitting
}

class CombinedInvoiceViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val ledgerRepository: LedgerRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private var productCache: Map<String, InvoiceProduct> = emptyMap()
    private var orderCache: Map<String, OrderOption> = emptyMap()

    private val _uiState = MutableStateFlow(
        CombinedInvoiceUiState(
            showNotesApplyTo = sessionManager.state.value.settings?.combinedInvoiceNote == true,
        )
    )
    val uiState: StateFlow<CombinedInvoiceUiState> = _uiState.asStateFlow()

    // ---- Parties -----------------------------------------------------------

    fun onSupplierSelected(party: TxnSelection) = _uiState.update { it.copy(supplier = party) }
    fun onCustomerSelected(party: TxnSelection) = _uiState.update { it.copy(customer = party) }

    /** Party accounts (suppliers/customers) — COA level-4 with acType=3. */
    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "3")

    // ---- Orders ------------------------------------------------------------

    suspend fun searchPurchaseOrders(query: String): Resource<List<SelectorOption>> =
        invoiceRepository.searchOrders(query, orderType = "1").mapOrders()

    suspend fun searchSalesOrders(query: String): Resource<List<SelectorOption>> =
        invoiceRepository.searchOrders(query, orderType = "2").mapOrders()

    private fun Resource<List<OrderOption>>.mapOrders(): Resource<List<SelectorOption>> = when (this) {
        is Resource.Success -> {
            orderCache = orderCache + data.associateBy { it.id }
            Resource.Success(data.map { SelectorOption(it.id, it.orderNumber, it.customerName) })
        }
        is Resource.Error -> this
        Resource.Loading -> Resource.Loading
    }

    /** A purchase order resolves the supplier and pre-fills the purchase rate. */
    fun onPurchaseOrderSelected(option: SelectorOption) {
        val order = orderCache[option.id] ?: return
        _uiState.update {
            it.copy(
                purchaseOrder = order,
                purchaseRate = order.rate?.takeIf { r -> r > 0 }?.toString() ?: it.purchaseRate,
            )
        }
        resolveParty(order.customerName) { party -> _uiState.update { it.copy(supplier = party) } }
    }

    /** A sales order resolves the customer and pre-fills the sales rate. */
    fun onSalesOrderSelected(option: SelectorOption) {
        val order = orderCache[option.id] ?: return
        _uiState.update {
            it.copy(
                salesOrder = order,
                salesRate = order.rate?.takeIf { r -> r > 0 }?.toString() ?: it.salesRate,
            )
        }
        resolveParty(order.customerName) { party -> _uiState.update { it.copy(customer = party) } }
    }

    private fun resolveParty(name: String, apply: (TxnSelection) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            (ledgerRepository.searchLedgers(name, acType = "3") as? Resource.Success)
                ?.data?.firstOrNull()
                ?.let { apply(TxnSelection(it.id.toString(), it.name)) }
        }
    }

    // ---- Header fields -----------------------------------------------------

    fun onVehicleNumberChange(value: String) = _uiState.update { it.copy(vehicleNumber = value) }
    fun onAmountChange(value: String) = _uiState.update { it.copy(amount = value.decimalOnly()) }
    fun onDiscountChange(value: String) = _uiState.update { it.copy(discount = value.decimalOnly()) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onOnlySalesToggle(checked: Boolean) = _uiState.update { it.copy(onlySalesPosting = checked) }

    fun onNotesApplyToSelected(value: String) = _uiState.update { it.copy(notesApplyTo = value) }

    // ---- Line entry --------------------------------------------------------

    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> {
                productCache = result.data.associateBy { it.id }
                Resource.Success(result.data.map { SelectorOption(it.id, it.name, it.unit) })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onProductSelected(option: SelectorOption) {
        val product = productCache[option.id] ?: return
        _uiState.update {
            it.copy(
                selectedProduct = product,
                // Pre-fill both rates from the product, like the web picker.
                purchaseRate = product.purchasePrice?.takeIf { p -> p > 0 }?.toString() ?: it.purchaseRate,
                salesRate = product.salesPrice?.takeIf { p -> p > 0 }?.toString() ?: it.salesRate,
            )
        }
    }

    fun onBagChange(value: String) = _uiState.update { it.copy(bag = value.decimalOnly()) }
    fun onVarianceChange(value: String) = _uiState.update { it.copy(variance = value.decimalOnly()) }
    fun onQtyChange(value: String) = _uiState.update { it.copy(qty = value.decimalOnly()) }
    fun onPurchaseRateChange(value: String) = _uiState.update { it.copy(purchaseRate = value.decimalOnly()) }
    fun onSalesRateChange(value: String) = _uiState.update { it.copy(salesRate = value.decimalOnly()) }

    fun onVarianceTypeSelected(option: SelectorOption) = _uiState.update {
        if (option.id.isEmpty()) it.copy(varianceType = option, variance = "")
        else it.copy(varianceType = option)
    }

    fun addLine() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val qty = state.qty.toDoubleOrNull() ?: return
        val purchaseRate = state.purchaseRate.toDoubleOrNull() ?: return
        val salesRate = state.salesRate.toDoubleOrNull() ?: return
        if (qty <= 0 || purchaseRate < 0 || salesRate < 0) return
        _uiState.update {
            it.copy(
                lines = it.lines + CombinedLine(
                    product = product,
                    qty = qty,
                    purchasePrice = purchaseRate,
                    salesPrice = salesRate,
                    bag = it.bag.trim(),
                    variance = if (it.varianceEnabled) it.variance.trim() else "",
                    varianceType = it.varianceType.id,
                ),
                selectedProduct = null,
                bag = "",
                variance = "",
                varianceType = VARIANCE_TYPES.first(),
                qty = "",
                purchaseRate = "",
                salesRate = "",
            )
        }
    }

    fun removeLine(index: Int) = _uiState.update {
        if (index !in it.lines.indices) it
        else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = invoiceRepository.submitCombined(
                supplier = state.supplier!!,
                customer = state.customer!!,
                purchaseOrderNumber = state.purchaseOrder?.id.orEmpty(),
                salesOrderNumber = state.salesOrder?.id.orEmpty(),
                amount = state.amount.toDoubleOrNull() ?: 0.0,
                onlySalesPosting = state.onlySalesPosting,
                salesDiscount = state.discount.toDoubleOrNull() ?: 0.0,
                vehicleNumber = state.vehicleNumber.trim(),
                notes = state.notes.trim(),
                notesApplyTo = if (state.showNotesApplyTo) state.notesApplyTo else "both",
                lines = state.lines,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    CombinedInvoiceUiState(
                        showNotesApplyTo = it.showNotesApplyTo,
                        // Keep the parties for the next entry, like the cash forms.
                        supplier = it.supplier,
                        customer = it.customer,
                        message = result.data,
                        isError = false,
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
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                CombinedInvoiceViewModel(
                    invoiceRepository = ServiceLocator.provideInvoiceRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}

@Composable
fun CombinedInvoiceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CombinedInvoiceViewModel = viewModel(
        factory = CombinedInvoiceViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Combined Invoice",
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchableLedgerDropdown(
                selectedLedger = state.supplier?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
                onLedgerSelected = { viewModel.onSupplierSelected(TxnSelection(it.id.toString(), it.name)) },
                searchLedgers = viewModel::searchAccounts,
                label = "Select Supplier",
            )
            SearchableLedgerDropdown(
                selectedLedger = state.customer?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
                onLedgerSelected = { viewModel.onCustomerSelected(TxnSelection(it.id.toString(), it.name)) },
                searchLedgers = viewModel::searchAccounts,
                label = "Select Customer",
            )
            AppTextField(
                value = state.vehicleNumber,
                onValueChange = viewModel::onVehicleNumberChange,
                label = "Vehicle Number",
                modifier = Modifier.fillMaxWidth(),
            )
            SearchableSelectDropdown(
                selected = state.purchaseOrder?.let { SelectorOption(it.id, it.orderNumber, it.customerName) },
                onSelected = viewModel::onPurchaseOrderSelected,
                search = viewModel::searchPurchaseOrders,
                label = "Select Purchase Order",
                placeholder = "Type 3+ chars to search…",
                emptyText = "No order found",
            )
            SearchableSelectDropdown(
                selected = state.salesOrder?.let { SelectorOption(it.id, it.orderNumber, it.customerName) },
                onSelected = viewModel::onSalesOrderSelected,
                search = viewModel::searchSalesOrders,
                label = "Select Sales Order",
                placeholder = "Type 3+ chars to search…",
                emptyText = "No order found",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = "Transaction Amount",
                    caption = "Amount Tk.",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = state.discount,
                    onValueChange = viewModel::onDiscountChange,
                    label = "Discount Amount",
                    caption = "Discount",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            // The Both/Sales posting toggle — Sales pays the supplier 0.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.onlySalesPosting,
                    onCheckedChange = viewModel::onOnlySalesToggle,
                    enabled = (state.amount.toDoubleOrNull() ?: 0.0) > 0.0,
                )
                Text(
                    text = if (state.onlySalesPosting) "Post amount: Sales only" else "Post amount: Both sides",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            AppTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes",
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.showNotesApplyTo) {
                AppSelectDropdown(
                    label = "Notes apply to",
                    options = NOTES_APPLY_OPTIONS,
                    selected = NOTES_APPLY_OPTIONS.firstOrNull { it.id == state.notesApplyTo },
                    onSelected = { viewModel.onNotesApplyToSelected(it.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProductEntry(state = state, viewModel = viewModel)

            PrimaryButton(
                text = "Save Combined Invoice",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.lines.isNotEmpty()) {
                CombinedLinesList(
                    lines = state.lines,
                    purchaseTotal = state.purchaseTotal,
                    salesTotal = state.salesTotal,
                    onRemove = viewModel::removeLine,
                )
            }
        }
    }
}

/** Product picker + Bag/Variance/Qty/Purchase Rate/Sales Rate + Add. */
@Composable
private fun ProductEntry(state: CombinedInvoiceUiState, viewModel: CombinedInvoiceViewModel) {
    SearchableSelectDropdown(
        selected = state.selectedProduct?.let { SelectorOption(it.id, it.name, it.unit) },
        onSelected = viewModel::onProductSelected,
        search = viewModel::searchProducts,
        label = "Select Product",
        placeholder = "Type to search…",
        emptyText = "No product found",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.bag,
            onValueChange = viewModel::onBagChange,
            label = "Bag Number",
            caption = "Bag",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppSelectDropdown(
            label = "Variance Type",
            options = VARIANCE_TYPES,
            selected = state.varianceType,
            onSelected = viewModel::onVarianceTypeSelected,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.variance,
            onValueChange = viewModel::onVarianceChange,
            label = "Weight Variance",
            caption = "Weight Variance",
            enabled = state.varianceEnabled,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = "Quantity",
            caption = "Quantity",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.purchaseRate,
            onValueChange = viewModel::onPurchaseRateChange,
            label = "Purchase Rate",
            caption = "Purchase Rate",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.salesRate,
            onValueChange = viewModel::onSalesRateChange,
            label = "Sales Rate",
            caption = "Sales Rate",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
    SecondaryButton(
        text = "Add Item",
        onClick = viewModel::addLine,
        enabled = state.canAddLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The pending lines, with the web's Purchase Tk. / Sales Tk. totals. */
@Composable
private fun CombinedLinesList(
    lines: List<CombinedLine>,
    purchaseTotal: Double,
    salesTotal: Double,
    onRemove: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            text = "Qty ${AmountFormat.format(line.qty)}${line.product.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Pur ${AmountFormat.format(line.purchasePrice)} → ${AmountFormat.format(line.purchaseTotal)}   •   " +
                                "Sale ${AmountFormat.format(line.salesPrice)} → ${AmountFormat.format(line.salesTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (line.variance.isNotBlank() && line.varianceType.isNotBlank()) {
                            Text(
                                text = "Variance ${line.varianceType}${line.variance}" +
                                    (line.bag.takeIf { it.isNotBlank() }?.let { "   •   Bag $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Purchase Tk.", fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(purchaseTotal), fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sales Tk.", fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(salesTotal), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The notes apply-to options, matching the web's Both/Purchase/Sales switch. */
private val NOTES_APPLY_OPTIONS = listOf(
    SelectorOption(id = "both", label = "Both"),
    SelectorOption(id = "purchase", label = "Purchase"),
    SelectorOption(id = "sales", label = "Sales"),
)
