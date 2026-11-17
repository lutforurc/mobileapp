package com.example.cashbookbd.ui.production

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.production.model.ProductionInvoiceMode
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Production Sales / Purchase / Sales Return, one shared form (web
 * `ProductionSales.tsx`, `ProductionPurchase.tsx`, `ProductionSalesReturn.tsx`).
 *
 * ⚠️ A free line is its own row at price 0, never a discount on the paid row.
 * A sale's free rows are added by the scheme master; a purchase's are ticked in
 * by hand; a return has none.
 */
@Composable
fun ProductionInvoiceScreen(
    mode: ProductionInvoiceMode,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductionInvoiceViewModel = viewModel(
        key = mode.name,
        factory = ProductionInvoiceViewModel.provideFactory(LocalContext.current, mode),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val invoiceRepository = remember { ServiceLocator.provideInvoiceRepository(context) }

    val searchProducts: suspend (String) -> Resource<List<EntryProduct>> = { query ->
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> Resource.Success(
                result.data.map { product ->
                    EntryProduct(
                        id = product.id,
                        label = product.dropdownLabel,
                        unit = product.unit,
                        price = if (mode == ProductionInvoiceMode.PURCHASE) {
                            product.purchasePrice
                        } else {
                            product.salesPrice
                        },
                    )
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.error) {
        val text = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onErrorShown()
    }

    AuthenticatedShell(
        title = mode.title,
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Find a saved voucher by its number, to correct it in place.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    AppTextField(
                        value = state.searchNo,
                        onValueChange = viewModel::onSearchNo,
                        label = "e.g. 3-261000001",
                        caption = "Vr. No. (find one to edit)",
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Find",
                        onClick = viewModel::find,
                        compact = true,
                        isLoading = state.isSearching,
                    )
                }
                if (state.editingMtmId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Editing ${state.editingVrNo}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = AppFontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        LinkButton(text = "Cancel edit", onClick = viewModel::reset)
                    }
                }

                SearchableLedgerDropdown(
                    selectedLedger = state.partyId.takeIf { it.isNotBlank() }?.let {
                        LedgerDropdownItem(it.toIntOrNull() ?: 0, state.partyName, null)
                    },
                    onLedgerSelected = viewModel::onParty,
                    searchLedgers = viewModel::searchParties,
                    label = state.partyLabel,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (mode != ProductionInvoiceMode.SALE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = state.billNo,
                            onValueChange = viewModel::onBillNo,
                            label = if (state.isReturn) "Original sale invoice" else "Supplier's bill",
                            caption = if (state.isReturn) "Sale Invoice No." else "Bill No.",
                            modifier = Modifier.weight(1f),
                        )
                        AppDateField(
                            // On a return the date reads as a placeholder, not a
                            // floating label, so the two boxes line up with the
                            // invoice-number field beside it.
                            label = if (state.isReturn) "" else "Bill Date",
                            placeholder = if (state.isReturn) "Sale Invoice Date" else "",
                            date = SimpleDate.fromApi(state.billDate),
                            onPicked = viewModel::onBillDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                AppTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Optional",
                    caption = "Note",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.discount,
                        onValueChange = viewModel::onDiscount,
                        label = "",
                        caption = "Discount",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.effectivePayment,
                        onValueChange = viewModel::onPayment,
                        label = "",
                        caption = state.paymentLabel,
                        enabled = !state.isCashParty,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }

                Divider()
                Text(
                    text = if (state.isReturn) "Returned items" else "Items",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.SemiBold,
                )

                SearchableSelectProduct(
                    selected = state.draft,
                    onSelected = viewModel::onDraftProduct,
                    search = searchProducts,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.draftQty,
                        onValueChange = viewModel::onDraftQty,
                        label = "",
                        caption = "Quantity" +
                            (state.draft?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.draftPrice,
                        onValueChange = viewModel::onDraftPrice,
                        label = "",
                        caption = if (state.isReturn) "Refund price" else "Price",
                        enabled = !state.draftFree,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!state.isReturn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Free (price 0)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = state.draftFree, onCheckedChange = viewModel::onDraftFree)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (state.editingLineId != null) "Update" else "Add",
                        onClick = viewModel::addLine,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.editingLineId != null) {
                        SecondaryButton(
                            text = "Cancel",
                            onClick = viewModel::cancelLineEdit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (state.lines.isEmpty()) {
                    Text(
                        text = if (state.isReturn) "Add at least one returned item." else "No item yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                } else {
                    state.lines.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}. ${row.productName}" +
                                        if (row.free) "  ·  free" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${row.qty} ${row.unit} × ${row.price} = " +
                                        AmountFormat.format(
                                            (row.qty.toDoubleOrNull() ?: 0.0) *
                                                (row.price.toDoubleOrNull() ?: 0.0)
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                            }
                            IconButton(onClick = { viewModel.editLine(row.localId) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit line", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.removeLine(row.localId) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove line",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.appColors.danger,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append("Total ").append(AmountFormat.format(state.total))
                            append(" · Discount ").append(AmountFormat.format(state.discount.toDoubleOrNull() ?: 0.0))
                            append(" · Net ").append(AmountFormat.format(state.net))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = state.saveLabel,
                        onClick = viewModel::save,
                        enabled = state.canWrite,
                        isLoading = state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Reset",
                        onClick = viewModel::reset,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!state.canWrite) {
                    Text(
                        text = "You can read this screen but not save the permission is not granted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.shortage?.let { shortage ->
        AlertDialog(
            onDismissRequest = viewModel::dismissShortage,
            title = { Text(if (shortage.blocked) "Not enough stock" else "Not enough stock — save anyway?") },
            text = { Text(shortage.message) },
            confirmButton = {
                if (!shortage.blocked) {
                    LinkButton(text = "Continue Save", onClick = viewModel::continueAfterShortage)
                }
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissShortage) },
        )
    }
}

@Composable
private fun SearchableSelectProduct(
    selected: EntryProduct?,
    onSelected: (EntryProduct) -> Unit,
    search: suspend (String) -> Resource<List<EntryProduct>>,
) {
    val cache = remember { mutableMapOf<String, EntryProduct>() }
    com.example.cashbookbd.ui.components.SearchableSelectDropdown(
        selected = selected?.let {
            com.example.cashbookbd.ui.reports.model.SelectorOption(
                it.id, it.label, it.unit.ifBlank { null },
            )
        },
        onSelected = { option ->
            onSelected(
                cache[option.id] ?: EntryProduct(
                    option.id, option.label, option.sublabel.orEmpty(), null,
                )
            )
        },
        search = { query ->
            when (val result = search(query)) {
                is Resource.Success -> {
                    result.data.forEach { cache[it.id] = it }
                    Resource.Success(
                        result.data.map {
                            com.example.cashbookbd.ui.reports.model.SelectorOption(
                                it.id, it.label, it.unit.ifBlank { null },
                            )
                        }
                    )
                }
                is Resource.Error -> result
                Resource.Loading -> Resource.Loading
            }
        },
        label = "",
        placeholder = "Select Product",
        modifier = Modifier.fillMaxWidth(),
    )
}
