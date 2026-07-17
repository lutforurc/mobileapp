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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.reports.model.SelectorOption
import java.text.DecimalFormat

private val amountFormat = DecimalFormat("#,##0.##")

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

            ProductEntry(
                state = state,
                onProductSelected = viewModel::onProductSelected,
                searchProducts = viewModel::searchProducts,
                onQtyChange = viewModel::onQtyChange,
                onPriceChange = viewModel::onPriceChange,
                onAdd = viewModel::addLine,
            )

            if (state.lines.isNotEmpty()) {
                LinesList(lines = state.lines, onRemove = viewModel::removeLine, total = state.total)
            }

            if (state.showInvoiceNo) {
                OutlinedTextField(
                    value = state.invoiceNo,
                    onValueChange = viewModel::onInvoiceNoChange,
                    label = { Text("Supplier Invoice No (optional)") },
                    singleLine = true,
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

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save Invoice")
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProductEntry(
    state: InvoiceFormUiState,
    onProductSelected: (SelectorOption) -> Unit,
    searchProducts: suspend (String) -> com.example.cashbookbd.core.Resource<List<SelectorOption>>,
    onQtyChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
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

            OutlinedButton(
                onClick = onAdd,
                enabled = state.canAddLine,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Item")
            }
        }
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
                            text = "${amountFormat.format(line.qty)} × ${amountFormat.format(line.price)} = ${amountFormat.format(line.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                Text(amountFormat.format(total), fontWeight = FontWeight.Bold)
            }
        }
    }
}
