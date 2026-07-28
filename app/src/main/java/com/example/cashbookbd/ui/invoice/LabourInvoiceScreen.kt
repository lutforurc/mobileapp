package com.example.cashbookbd.ui.invoice

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.invoice.model.LabourLine
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * The Construction Labour Invoice — a port of the web's
 * ConstructionLabourInvoice (/invoice/labour-invoice): a supplier, optional
 * notes/bill number/bill date, a running list of labour item lines, and the
 * auto-computed payment amount (forced for the Cash supplier). The server books
 * a payment voucher — or, at payment 0, a credit (journal) voucher — itself.
 */
@Composable
fun LabourInvoiceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LabourInvoiceViewModel = viewModel(
        factory = LabourInvoiceViewModel.provideFactory(LocalContext.current)
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
        title = "Labour Invoice",
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

            AppTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes",
                modifier = Modifier.fillMaxWidth(),
            )

            AppTextField(
                value = state.billNo,
                onValueChange = viewModel::onBillNoChange,
                label = "Bill Number",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldButton(
                text = "Bill Date: ${state.billDate?.toDisplay() ?: "—"}",
                onClick = { showDatePicker(context, state.billDate ?: SimpleDate.today(), viewModel::onBillDateChange) },
                icon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.paymentAmt,
                    onValueChange = viewModel::onPaymentChange,
                    label = "Payment Amount",
                    caption = "Payment Amount",
                    // The Cash supplier (account 17) always pays the computed amount.
                    enabled = !state.isCashSupplier,
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Total Tk.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = AmountFormat.format(state.total),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ItemEntry(state = state, viewModel = viewModel)

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
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.lines.isNotEmpty()) {
                LabourLinesList(
                    lines = state.lines,
                    editingIndex = state.editingIndex,
                    total = state.total,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                )
            }
        }
    }
}

/** Labour item picker + Quantity (unit suffix) / Price + Add New. */
@Composable
private fun ItemEntry(state: LabourInvoiceUiState, viewModel: LabourInvoiceViewModel) {
    Text(
        text = "Add Labour Item",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    SearchableSelectDropdown(
        selected = state.selectedItem?.let { SelectorOption(it.id, it.name, it.category.ifBlank { it.unit }) },
        onSelected = viewModel::onItemSelected,
        search = viewModel::searchLabourItems,
        label = "Select Labour Item",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No labour item found",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = "Quantity" + state.selectedItem?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.price,
            onValueChange = viewModel::onPriceChange,
            label = "Price",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
    SecondaryButton(
        text = if (state.editingIndex != null) "Update Item" else "Add New",
        onClick = viewModel::addLine,
        enabled = state.canAddLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The pending lines: Item / Qty (+unit) / Rate / Total, with edit + delete. */
@Composable
private fun LabourLinesList(
    lines: List<LabourLine>,
    editingIndex: Int?,
    total: Double,
    onEdit: (Int) -> Unit,
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
                        Text(
                            text = line.item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            // The row being edited is called out in primary ink.
                            color = if (index == editingIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        Text(
                            text = "${AmountFormat.format(line.qtyValue)}" +
                                line.item.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() +
                                " × ${AmountFormat.format(line.priceValue)} = ${AmountFormat.format(line.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Tk.", fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(total), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun showDatePicker(
    context: Context,
    current: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
