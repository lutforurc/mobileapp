package com.example.cashbookbd.ui.invoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.core.AmountFormat

/**
 * A Sales/Purchase invoice entry form: a party (customer/supplier), a running
 * list of product lines (product search + qty + price), and the paid/received
 * amount, discount and notes. The server derives the date, contra legs and
 * voucher number. Unsupported invoice types show a "coming soon" message.
 */
@Composable
fun InvoiceFormScreen(
    invoiceKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceFormViewModel = viewModel(
        factory = InvoiceFormViewModel.provideFactory(LocalContext.current, invoiceKey)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = state.title,
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!state.isSupported) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This invoice form isn't available in the mobile app yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchableLedgerDropdown(
                selectedLedger = state.party?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
                onLedgerSelected = { viewModel.onPartySelected(TxnSelection(it.id.toString(), it.name)) },
                searchLedgers = viewModel::searchAccounts,
                label = state.partyLabel,
            )

            if (state.isTrading) {
                TradingHeaderFields(state = state, viewModel = viewModel)
            }

            if (state.showInvoiceNo) {
                OutlinedTextField(
                    value = state.invoiceNo,
                    onValueChange = viewModel::onInvoiceNoChange,
                    label = { Text(if (state.showInvoiceDate) "Invoice No" else "Supplier Invoice No (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.showInvoiceDate) {
                FieldButton(
                    text = "Invoice Date: ${state.invoiceDate.toDisplay()}",
                    onClick = { showDatePicker(context, state.invoiceDate, viewModel::onInvoiceDateChange) },
                    icon = Icons.Filled.DateRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(state.amountLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.discount,
                    onValueChange = viewModel::onDiscountChange,
                    label = { Text("Discount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isElectronics) {
                InstallmentSection(state = state, viewModel = viewModel)
            }

            PrimaryButton(
                text = "Save Invoice",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The product picker sits below the invoice itself: the invoice
            // fields are what the user came for, adding items is the side task.
            ProductEntry(
                state = state,
                viewModel = viewModel,
                onProductSelected = viewModel::onProductSelected,
                searchProducts = viewModel::searchProducts,
                onQtyChange = viewModel::onQtyChange,
                onPriceChange = viewModel::onPriceChange,
                onSerialNoChange = viewModel::onSerialNoChange,
                onAdd = viewModel::addLine,
            )

            if (state.lines.isNotEmpty()) {
                LinesList(lines = state.lines, onRemove = viewModel::removeLine, total = state.total)
            }
        }
    }
}

/**
 * Trading invoice-level extras: vehicle number and the purchase/sales order
 * pickers. Selecting a sales order auto-fills the customer and the entry line.
 */
@Composable
private fun TradingHeaderFields(state: InvoiceFormUiState, viewModel: InvoiceFormViewModel) {
    OutlinedTextField(
        value = state.vehicleNumber,
        onValueChange = viewModel::onVehicleNumberChange,
        label = { Text("Vehicle Number") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SearchableSelectDropdown(
        selected = state.purchaseOrder?.let { SelectorOption(it.id, it.orderNumber, it.customerName) },
        onSelected = viewModel::onPurchaseOrderSelected,
        search = viewModel::searchPurchaseOrders,
        label = "Purchase Order",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No order found",
    )
    SearchableSelectDropdown(
        selected = state.salesOrder?.let { SelectorOption(it.id, it.orderNumber, it.customerName) },
        onSelected = viewModel::onSalesOrderSelected,
        search = viewModel::searchSalesOrders,
        label = "Sales Order",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No order found",
    )
}

/**
 * Electronics installment plan: a toggle that reveals amount, count, an optional
 * start date, and an early-payment sub-section. Mirrors the web's Installment
 * Sale popup; the server only requires amount and count.
 */
@Composable
private fun InstallmentSection(state: InvoiceFormUiState, viewModel: InvoiceFormViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.isInstallment, onCheckedChange = viewModel::onInstallmentToggle)
            Text("Installment Sale", style = MaterialTheme.typography.bodyLarge)
        }

        if (state.isInstallment) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.installmentAmount,
                    onValueChange = viewModel::onInstallmentAmountChange,
                    label = { Text("Installment Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.installmentsNo,
                    onValueChange = viewModel::onInstallmentsNoChange,
                    label = { Text("Installments No.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            FieldButton(
                text = "Start Date: ${state.installmentStartDate?.toDisplay() ?: "Auto (next month)"}",
                onClick = {
                    showDatePicker(
                        context,
                        state.installmentStartDate ?: com.example.cashbookbd.ui.reports.model.SimpleDate.today(),
                        viewModel::onInstallmentStartDate,
                    )
                },
                icon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.isEarlyPayment, onCheckedChange = viewModel::onEarlyPaymentToggle)
                Text("Early Payment", style = MaterialTheme.typography.bodyLarge)
            }

            if (state.isEarlyPayment) {
                OutlinedTextField(
                    value = state.earlyDiscount,
                    onValueChange = viewModel::onEarlyDiscountChange,
                    label = { Text("Early Discount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldButton(
                    text = "Early Payment Date: ${state.earlyPaymentDate?.toDisplay() ?: "—"}",
                    onClick = {
                        showDatePicker(
                            context,
                            state.earlyPaymentDate ?: com.example.cashbookbd.ui.reports.model.SimpleDate.today(),
                            viewModel::onEarlyPaymentDate,
                        )
                    },
                    icon = Icons.Filled.DateRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProductEntry(
    state: InvoiceFormUiState,
    viewModel: InvoiceFormViewModel,
    onProductSelected: (SelectorOption) -> Unit,
    searchProducts: suspend (String) -> com.example.cashbookbd.core.Resource<List<SelectorOption>>,
    onQtyChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onSerialNoChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    // No Card here — the product entry sits directly on the screen background.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add Product", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        SearchableSelectDropdown(
            selected = state.selectedProduct?.let { SelectorOption(it.id, it.name, it.unit) },
            onSelected = onProductSelected,
            search = searchProducts,
            label = "Select Product",
            emptyText = "No product found",
        )

        // Electronics (Computer and Accessories) sales take an optional IMEI/serial.
        if (state.isElectronics) {
            OutlinedTextField(
                value = state.serialNo,
                onValueChange = onSerialNoChange,
                label = { Text("Serial No (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isTrading) {
            AppSelectDropdown(
                label = "Warehouse",
                options = state.warehouses,
                selected = state.selectedWarehouse,
                onSelected = viewModel::onWarehouseSelected,
                placeholder = "Not Applicable",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.bag,
                    onValueChange = viewModel::onBagChange,
                    label = { Text("Bag Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    AppSelectDropdown(
                        label = "Variance Type",
                        options = VARIANCE_TYPES,
                        selected = state.varianceType,
                        onSelected = viewModel::onVarianceTypeSelected,
                    )
                }
            }
            OutlinedTextField(
                value = state.variance,
                onValueChange = viewModel::onVarianceChange,
                label = { Text("Weight Variance") },
                singleLine = true,
                enabled = state.varianceEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.qty,
                onValueChange = onQtyChange,
                label = { Text("Qty" + state.selectedProduct?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.price,
                onValueChange = onPriceChange,
                label = { Text("Price") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        PrimaryButton(
            text = "Add Item",
            onClick = onAdd,
            enabled = state.canAddLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LinesList(lines: List<InvoiceLine>, onRemove: (Int) -> Unit, total: Double) {
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
                            text = "${AmountFormat.format(line.qty)} × ${AmountFormat.format(line.price)} = ${AmountFormat.format(line.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (line.serialNo.isNotBlank()) {
                            Text(
                                text = "Serial: ${line.serialNo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val lineExtras = buildList {
                            if (line.warehouseName.isNotBlank()) add(line.warehouseName)
                            if (line.bag.isNotBlank()) add("Bag ${line.bag}")
                            if (line.variance.isNotBlank() && line.varianceType.isNotBlank()) {
                                add("Var ${line.varianceType}${line.variance}")
                            }
                        }
                        if (lineExtras.isNotEmpty()) {
                            Text(
                                text = lineExtras.joinToString("  •  "),
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
                Text("Total", fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(total), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun showDatePicker(
    context: Context,
    current: com.example.cashbookbd.ui.reports.model.SimpleDate,
    onPicked: (com.example.cashbookbd.ui.reports.model.SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(com.example.cashbookbd.ui.reports.model.SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
