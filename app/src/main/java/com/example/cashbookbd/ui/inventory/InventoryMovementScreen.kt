package com.example.cashbookbd.ui.inventory

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.BranchTransferLine
import com.example.cashbookbd.data.repository.MaterialIssueLine
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * One screen for the three inventory-movement entry forms (Branch Issue, Branch
 * Receive, Material Issue), parameterized by [formKey] into
 * [com.example.cashbookbd.inventory.InventoryForms]. Ports the web's Branch
 * Transfer / Warehouse Received / Material Issue pages: a header, a product
 * line editor collecting a pending batch, and one Save that posts the batch as
 * a real voucher.
 */
@Composable
fun InventoryMovementScreen(
    formKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryMovementViewModel = viewModel(
        key = formKey,
        factory = InventoryMovementViewModel.provideFactory(LocalContext.current, formKey)
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

    // Branch Issue's stock-shortage confirm: the server listed what's short and
    // waits for an explicit "transfer anyway" (re-posted with allow_negative).
    state.shortages?.let { shortages ->
        AlertDialog(
            onDismissRequest = viewModel::dismissShortage,
            title = { Text("Stock shortage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    shortages.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Transfer anyway?", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { LinkButton(text = "Transfer Anyway", onClick = viewModel::confirmShortage) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissShortage) },
        )
    }

    AuthenticatedShell(
        title = state.title,
        // These forms live in the Invoice section of the drawer.
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!state.isSupported) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This form isn't available in the mobile app yet.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
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
            FieldButton(
                text = "${state.dateLabel}: ${state.date.toDisplay()}",
                onClick = { showDatePicker(context, state.date, viewModel::onDateChange) },
                icon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isMaterial) {
                MaterialIssueHeader(state, viewModel)
            } else {
                BranchTransferHeaderFields(state, viewModel)
            }

            LineEntry(state, viewModel)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Add Item",
                    onClick = viewModel::addLine,
                    enabled = state.canAdd,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = viewModel::submit,
                    enabled = state.canSave,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.transferLines.isNotEmpty()) {
                TransferLinesList(
                    lines = state.transferLines,
                    total = state.total,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                )
            }
            if (state.materialLines.isNotEmpty()) {
                MaterialLinesList(
                    lines = state.materialLines,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                )
            }
        }
    }
}

/** Branch Issue / Receive header: the two branch pickers and challan details. */
@Composable
private fun BranchTransferHeaderFields(
    state: InventoryMovementUiState,
    viewModel: InventoryMovementViewModel,
) {
    AppSelectDropdown(
        label = state.fromLabel,
        options = state.fromOptions,
        selected = state.fromBranch,
        onSelected = viewModel::onFromBranchSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (state.isBranchesLoading) "Loading branches…" else state.fromLabel,
    )
    AppSelectDropdown(
        label = state.toLabel,
        options = state.toOptions,
        selected = state.toBranch,
        onSelected = viewModel::onToBranchSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (state.isBranchesLoading) "Loading branches…" else state.toLabel,
    )
    state.branchesError?.let {
        Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
    }
    if (!state.isSelfFrom && state.fromBranch != null && state.fromBranch.id == state.toBranch?.id) {
        Text(
            text = "${state.fromLabel} and ${state.toLabel} must be different.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    AppTextField(
        value = state.challanNumber,
        onValueChange = viewModel::onChallanNumberChange,
        label = "Challan Number (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = state.receiverName,
        onValueChange = viewModel::onReceiverNameChange,
        label = "Receiver Name (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = state.receiverMobile,
        onValueChange = viewModel::onReceiverMobileChange,
        label = "Receiver Mobile (optional)",
        keyboardType = KeyboardType.Number,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.mobileOk) {
        Text(
            text = "Receiver mobile must be 6–32 digits.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    AppTextField(
        value = state.transport,
        onValueChange = viewModel::onTransportChange,
        label = "Transport (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = state.note,
        onValueChange = viewModel::onNoteChange,
        label = "Note (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Material Issue header: warehouse (optional), project (required), receiver, note. */
@Composable
private fun MaterialIssueHeader(
    state: InventoryMovementUiState,
    viewModel: InventoryMovementViewModel,
) {
    AppSelectDropdown(
        label = "From Warehouse (Optional)",
        options = state.warehouses,
        selected = state.selectedWarehouse,
        onSelected = viewModel::onWarehouseSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = "Not Applicable",
    )
    AppSelectDropdown(
        label = "Project / Site",
        options = state.projects,
        selected = state.selectedProject,
        onSelected = viewModel::onProjectSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (state.projects.isEmpty()) "Loading projects…" else "Select Project / Site",
    )
    AppTextField(
        value = state.receivedBy,
        onValueChange = viewModel::onReceivedByChange,
        label = "Received By (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = state.note,
        onValueChange = viewModel::onNoteChange,
        label = "Note (optional)",
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The shared product line editor; extra fields depend on the variant. */
@Composable
private fun LineEntry(
    state: InventoryMovementUiState,
    viewModel: InventoryMovementViewModel,
) {
    Text("Add Item", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)

    SearchableSelectDropdown(
        selected = state.product?.let { SelectorOption(it.id, it.name, it.unit) },
        onSelected = viewModel::onProductSelected,
        search = viewModel::searchProducts,
        label = "Select Product",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No product found",
    )

    val qtyLabel = "Qty" + state.product?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

    if (state.isMaterial) {
        AppTextField(
            value = state.qty,
            onValueChange = viewModel::onQtyChange,
            label = qtyLabel,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = state.building,
                onValueChange = viewModel::onBuildingChange,
                label = "Building/Floor",
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.workItem,
                onValueChange = viewModel::onWorkItemChange,
                label = "Work Item",
                modifier = Modifier.weight(1f),
            )
        }
        AppTextField(
            value = state.lineNote,
            onValueChange = viewModel::onLineNoteChange,
            label = "Line Note (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = state.qty,
                onValueChange = viewModel::onQtyChange,
                label = qtyLabel,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            // Rate comes with the picked product and is never sent — shown only
            // so the pending rows' amounts have a visible source.
            AppTextField(
                value = state.product?.purchasePrice?.let { AmountFormat.format(it) }.orEmpty(),
                onValueChange = {},
                label = "Rate (auto)",
                enabled = false,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = state.damagedQty,
                onValueChange = viewModel::onDamagedQtyChange,
                label = "Damaged Qty",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.shortQty,
                onValueChange = viewModel::onShortQtyChange,
                label = "Short Qty",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The pending transfer batch: qty/damaged/short/rate per row plus the total. */
@Composable
private fun TransferLinesList(
    lines: List<BranchTransferLine>,
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
                        Text(line.product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        val details = buildList {
                            add("Qty ${AmountFormat.format(line.qty)}" +
                                line.product.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
                            if (line.damagedQty > 0.0) add("Damaged ${AmountFormat.format(line.damagedQty)}")
                            if (line.shortQty > 0.0) add("Short ${AmountFormat.format(line.shortQty)}")
                            add("Rate ${AmountFormat.format(line.rate)}")
                        }
                        Text(
                            text = details.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = AmountFormat.format(line.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    IconButton(onClick = { onEdit(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit line", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove line", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontWeight = AppFontWeight.Bold)
                Text(AmountFormat.format(total), fontWeight = AppFontWeight.Bold)
            }
        }
    }
}

/** The pending material-issue batch: qty plus building/work-item/note per row. */
@Composable
private fun MaterialLinesList(
    lines: List<MaterialIssueLine>,
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
                        Text(line.product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            text = "Qty ${AmountFormat.format(line.qty)}" +
                                line.product.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val extras = buildList {
                            if (line.building.isNotBlank()) add(line.building)
                            if (line.workItem.isNotBlank()) add(line.workItem)
                            if (line.note.isNotBlank()) add(line.note)
                        }
                        if (extras.isNotEmpty()) {
                            Text(
                                text = extras.joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    IconButton(onClick = { onEdit(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit line", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove line", tint = MaterialTheme.colorScheme.error)
                    }
                }
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
