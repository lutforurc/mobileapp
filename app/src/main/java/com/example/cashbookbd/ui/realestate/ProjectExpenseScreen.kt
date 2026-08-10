package com.example.cashbookbd.ui.realestate

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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.ProjectExpenseLine
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FormFieldHeight
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * The cash payment screen a real-estate branch uses — the web's form of the
 * same name, reached both from Real Estate ("Project Expense") and as the
 * branch's ordinary Cash Payment, hence [title].
 *
 * Every line is an ordinary payment line. An expense line may additionally
 * record the project (and optionally the building) the money belongs to, and
 * the project cost reports read those tags; a line on any other account is
 * saved without a project dimension at all. A saved voucher can be pulled up by
 * number and rewritten in place — the untagged report's Tag button arrives here
 * with the voucher preloaded.
 */
@Composable
fun ProjectExpenseScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    initialVrNo: String? = null,
    title: String = "Project Expense",
    /** Which drawer section to light up — this screen answers to two of them. */
    drawerRoute: String = Routes.REAL_ESTATE,
    viewModel: ProjectExpenseViewModel = viewModel(
        factory = ProjectExpenseViewModel.provideFactory(
            androidx.compose.ui.platform.LocalContext.current,
            initialVrNo,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        title = title,
        currentRoute = drawerRoute,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "An ordinary cash payment. An expense line may also say which " +
                        "project — and which building — the money belongs to; without one " +
                        "it stays a branch expense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Spacer(Modifier.height(10.dp))

                // Pull a saved voucher up by number to correct it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQuery,
                        label = "Voucher number…",
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    PrimaryButton(
                        text = "Search",
                        onClick = viewModel::onSearch,
                        compact = true,
                        enabled = !state.isSearching,
                        isLoading = state.isSearching,
                        modifier = Modifier.height(FormFieldHeight),
                    )
                }

                state.editingVrNo?.let { vrNo ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Editing voucher $vrNo — Save becomes Update and rewrites it in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                if (state.paidFromBank) {
                    // A voucher reached from the untagged report may be a bank
                    // payment; the rewrite keeps it that way, and says so.
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.appColors.warningTint, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Paid from ${state.paidFromName} — not cash. Saving keeps it that way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.warning,
                        )
                    }
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

                SearchableSelectDropdown(
                    selected = state.order,
                    onSelected = viewModel::onOrderSelected,
                    search = viewModel::searchOrders,
                    label = "Select Order (Optional)",
                    placeholder = "Type 3+ chars to search…",
                    emptyText = "No order found",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // Searched rather than listed: this is the whole chart of
                // accounts now, not the expense heads alone.
                SearchableSelectDropdown(
                    selected = state.account.takeIf { it.isNotBlank() }
                        ?.let { SelectorOption(id = it, label = state.accountName) },
                    onSelected = viewModel::onAccountSelected,
                    search = viewModel::searchAccounts,
                    label = "Select Account",
                    placeholder = "Type 3+ chars to search…",
                    emptyText = "No account found",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // Only an expense head takes a project, so until one is picked
                // the box stays empty and says why rather than showing a choice
                // that would not be saved.
                val projectChoices = projectOptions(state.projects)
                AppSelectDropdown(
                    label = "Project (Optional)",
                    options = projectChoices,
                    selected = if (state.isExpenseAccount) {
                        projectChoices.firstOrNull { it.id == state.projectId }
                    } else {
                        null
                    },
                    onSelected = viewModel::onProjectSelected,
                    enabled = state.isExpenseAccount,
                    placeholder = when {
                        state.isLoadingDdls -> "Loading…"
                        state.account.isBlank() -> "Pick an account first"
                        else -> "Not tracked — this is not an expense account"
                    },
                )
                Spacer(Modifier.height(10.dp))
                val buildingChoices = buildingOptions(state.buildings)
                val buildingEnabled = state.isExpenseAccount && state.projectId.isNotBlank()
                AppSelectDropdown(
                    label = "Building",
                    options = buildingChoices,
                    selected = if (buildingEnabled) {
                        buildingChoices.firstOrNull { it.id == state.buildingId }
                    } else {
                        null
                    },
                    onSelected = viewModel::onBuildingSelected,
                    enabled = buildingEnabled,
                    placeholder = "Whole project (no single building)",
                )
                Text(
                    text = "Leave it on \"whole project\" for land, boundary wall, approvals — " +
                        "costs no single building carries.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    modifier = Modifier.padding(top = 3.dp, start = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = state.remarks,
                    onValueChange = viewModel::onRemarks,
                    label = "Remarks",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmount,
                    label = "Amount",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = state.note,
                    onValueChange = viewModel::onNote,
                    label = "Note (whole voucher)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = if (state.isRowEditing) "Save Line" else "Add New",
                        onClick = viewModel::saveLine,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isRowEditing) {
                        // While a line is open there is deliberately no Save —
                        // one control must not silently do the other's job.
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
                }

                Spacer(Modifier.height(16.dp))
                // Captured here: table render lambdas are not composable scopes.
                val editingTint = MaterialTheme.appColors.infoTint
                ReportTable(
                    columns = expenseColumns(
                        editingRowKey = state.editingRowKey,
                        onEdit = viewModel::editRow,
                        onRemove = viewModel::removeRow,
                    ),
                    data = state.rows,
                    noDataMessage = "No lines yet — Add New puts the form above into the voucher.",
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
                        text = "Payment Total",
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
private fun buildingOptions(buildings: List<SelectorOption>): List<SelectorOption> =
    remember(buildings) {
        listOf(SelectorOption(id = "", label = "Whole project (no single building)")) + buildings
    }

/**
 * The projects ddl with the web's empty option on top. A project is optional,
 * and choosing none is a real answer — the expense stays with the branch.
 */
@Composable
private fun projectOptions(projects: List<SelectorOption>): List<SelectorOption> =
    remember(projects) {
        listOf(SelectorOption(id = "", label = "Branch expense (no project)")) + projects
    }

@Composable
private fun expenseColumns(
    editingRowKey: String?,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
): List<ReportColumn<ProjectExpenseLine>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    // The tinted (row-under-edit) band is pale, where the on-teal ink washes out.
    val editingInk = MaterialTheme.colorScheme.onSurface
    fun ink(isEditing: Boolean) = if (isEditing) editingInk else onScreen
    return listOf(
        ReportColumn("Description", ReportColWidth.Weight(1.2f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.accountName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ink(row.key == editingRowKey),
                        maxLines = 2,
                    )
                    if (row.remarks.isNotBlank()) {
                        Text(
                            text = row.remarks,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        ReportColumn("Project / Building", ReportColWidth.Weight(1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        // Three different things, and the row must not read as
                        // one of the others: a project, a branch expense that
                        // could have carried one, and a line that never can.
                        text = when {
                            !row.isExpense -> "Not tracked"
                            row.projectId == null -> "Branch expense"
                            else -> row.projectName.ifBlank { "—" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ink(row.key == editingRowKey),
                        maxLines = 2,
                    )
                    if (row.isExpense && row.projectId != null) {
                        Text(
                            text = row.buildingName.ifBlank { "Whole project" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        ReportColumn("Amount", ReportColWidth.Fixed(92.dp), TextAlign.End) { row, _ ->
            ReportTableCell.Text(
                text = AmountFormat.format(row.amount.toDoubleOrNull() ?: 0.0),
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
