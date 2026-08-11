package com.example.cashbookbd.ui.realestate

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.cashbookbd.data.repository.ProjectLabourLine
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.TutorialScreens
import com.example.cashbookbd.ui.components.TutorialVideoLink
import com.example.cashbookbd.ui.components.VoucherSearchRow
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Project Labour — the web's screen of the same name, and Project Purchase's
 * twin on purpose: a clerk who books both should not have to learn two forms.
 * Every line says which project and which building the work was for, so the
 * ledger can carry one Labour Expense debit per building — which is what lets
 * a building be asked what it has cost. There is no vehicle and no stock here;
 * labour is consumed as it is bought.
 */
@Composable
fun ProjectLabourScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectLabourViewModel = viewModel(
        factory = ProjectLabourViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val ledgerRepository = remember { ServiceLocator.provideLedgerRepository(context.applicationContext) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }

    AuthenticatedShell(
        title = "Project Labour",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = { TutorialVideoLink(screenKey = TutorialScreens.PROJECT_LABOUR) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Labour worked for a project — and for a building within it. " +
                        "The building is picked per line, since a gang's bill covers more than one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Spacer(Modifier.height(10.dp))

                VoucherSearchRow(
                    query = state.searchQuery,
                    onQuery = viewModel::onSearchQuery,
                    onSearch = viewModel::onSearch,
                    isSearching = state.isSearching,
                )
                state.editingVrNo?.let { vrNo ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Editing voucher $vrNo — Save becomes Update and rewrites it in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                Spacer(Modifier.height(12.dp))
                state.ddlError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::loadDdls)
                    Spacer(Modifier.height(8.dp))
                }

                // ---- Invoice header ----
                SearchableLedgerDropdown(
                    selectedLedger = state.supplier,
                    onLedgerSelected = viewModel::onSupplierSelected,
                    searchLedgers = { query -> ledgerRepository.searchLedgers(query, acType = "3") },
                    label = "Supplier",
                    placeholder = "Type to search supplier…",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // Notes finishes the supplier's row; the bill's own number and
                // date then start the next one together, which is the order
                // they are read off the paper bill in (web 234df22).
                AppTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Notes",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = state.invoiceNo,
                        onValueChange = viewModel::onInvoiceNo,
                        label = "Invoice No",
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = "Invoice Date",
                        value = SimpleDate.fromApi(state.invoiceDate)?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showLabourDatePicker(context, state.invoiceDate) { picked ->
                                viewModel.onInvoiceDate(picked)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = state.discount,
                        onValueChange = viewModel::onDiscount,
                        label = "Discount",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.paid,
                        onValueChange = viewModel::onPaid,
                        label = "Paid",
                        caption = if (state.isCashSupplier) "A cash bill is paid in full." else "",
                        enabled = !state.isCashSupplier,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Two figures of the same weight, as on Project Purchase —
                // what is still owed is read as often as what the bill came to.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total Tk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                        Text(
                            text = AmountFormat.format(state.total),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = AppFontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Due Tk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                        Text(
                            text = AmountFormat.format(state.due),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = AppFontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- Labour line ----
                AppSelectDropdown(
                    label = "Project",
                    options = state.projects,
                    selected = state.projects.firstOrNull { it.id == state.projectId },
                    onSelected = viewModel::onProjectSelected,
                    placeholder = if (state.isLoadingDdls) "Loading…" else "Select project",
                )
                Spacer(Modifier.height(10.dp))
                AppSelectDropdown(
                    label = "Building",
                    options = labourBuildingOptions(state.buildings),
                    selected = labourBuildingOptions(state.buildings).firstOrNull { it.id == state.buildingId },
                    onSelected = viewModel::onBuildingSelected,
                    enabled = state.projectId.isNotBlank(),
                    placeholder = "Whole project (no single building)",
                )
                Spacer(Modifier.height(10.dp))
                SearchableSelectDropdown(
                    selected = state.itemId.takeIf { it.isNotBlank() }
                        ?.let { SelectorOption(id = it, label = state.itemName) },
                    onSelected = viewModel::onItemSelected,
                    search = viewModel::searchItems,
                    label = "Labour Item",
                    placeholder = "Type to search labour item…",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = state.qty,
                        onValueChange = viewModel::onQty,
                        label = "Quantity",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.price,
                        onValueChange = viewModel::onPrice,
                        label = "Rate",
                        caption = "Filled from the item's rate; change it if this bill differs.",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = if (state.isRowEditing) "Save Line" else "Add New",
                        onClick = viewModel::saveLine,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isRowEditing) {
                        LinkButton(
                            text = "Cancel",
                            onClick = viewModel::cancelRowEdit,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryButton(
                            text = when {
                                state.isSaving -> "Saving…"
                                state.editingVrNo != null -> "Update"
                                else -> "Save"
                            },
                            onClick = viewModel::save,
                            enabled = !state.isSaving,
                            isLoading = state.isSaving,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LinkButton(
                        text = "Reset",
                        onClick = viewModel::resetAll,
                        modifier = Modifier.weight(0.7f),
                    )
                }

                Spacer(Modifier.height(16.dp))
                // Captured here: table render lambdas are not composable scopes.
                val editingTint = MaterialTheme.appColors.infoTint
                ReportTable(
                    columns = labourColumns(
                        editingRowKey = state.editingRowKey,
                        onEdit = viewModel::editRow,
                        onRemove = viewModel::removeRow,
                    ),
                    data = state.rows,
                    noDataMessage = "No labour yet — Add New puts the form above into the invoice.",
                    rowBackground = { row, _ ->
                        if (row.key == state.editingRowKey) editingTint else null
                    },
                    scrollable = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Invoice Total",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = AmountFormat.format(state.total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The buildings ddl with the web's empty option — "whole project" — on top. */
@Composable
private fun labourBuildingOptions(buildings: List<SelectorOption>): List<SelectorOption> =
    remember(buildings) {
        listOf(SelectorOption(id = "", label = "Whole project (no single building)")) + buildings
    }

@Composable
private fun labourColumns(
    editingRowKey: String?,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
): List<ReportColumn<ProjectLabourLine>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    // The tinted (row-under-edit) band is pale, where the on-teal ink washes out.
    val editingInk = MaterialTheme.colorScheme.onSurface
    fun ink(isEditing: Boolean) = if (isEditing) editingInk else onScreen
    return listOf(
        ReportColumn("Labour", ReportColWidth.Weight(1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.itemName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ink(row.key == editingRowKey),
                        maxLines = 2,
                    )
                    Text(
                        text = listOf(
                            row.projectName.ifBlank { "—" },
                            row.buildingName.ifBlank { "Whole project" },
                        ).joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        },
        ReportColumn("Qty", ReportColWidth.Fixed(64.dp), TextAlign.End) { row, _ ->
            cellText(row.qty.ifBlank { "-" }, align = TextAlign.End, color = ink(row.key == editingRowKey))
        },
        ReportColumn("Rate", ReportColWidth.Fixed(76.dp), TextAlign.End) { row, _ ->
            cellText(
                AmountFormat.format(row.price.toDoubleOrNull() ?: 0.0),
                align = TextAlign.End,
                color = ink(row.key == editingRowKey),
            )
        },
        ReportColumn("Total", ReportColWidth.Fixed(92.dp), TextAlign.End) { row, _ ->
            val total = (row.qty.toDoubleOrNull() ?: 0.0) * (row.price.toDoubleOrNull() ?: 0.0)
            cellText(
                AmountFormat.format(total),
                align = TextAlign.End,
                color = ink(row.key == editingRowKey),
            )
        },
        ReportColumn("Action", ReportColWidth.Fixed(88.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onEdit(row.key) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit this line",
                            tint = ink(row.key == editingRowKey),
                        )
                    }
                    IconButton(onClick = { onRemove(row.key) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove this line",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

/** Opens the platform date dialog on the held `YYYY-MM-DD` value (or today). */
private fun showLabourDatePicker(context: Context, current: String, onPicked: (String) -> Unit) {
    val initial = SimpleDate.fromApi(current) ?: SimpleDate.today()
    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth).toApi())
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
