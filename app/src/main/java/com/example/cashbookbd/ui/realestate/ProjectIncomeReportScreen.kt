package com.example.cashbookbd.ui.realestate

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.ProjectIncomeDetailRow
import com.example.cashbookbd.data.repository.ProjectIncomeSummaryRow
import com.example.cashbookbd.data.repository.UntaggedIncomeRow
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.TutorialScreens
import com.example.cashbookbd.ui.components.TutorialVideoLink
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Project Income — one screen, three tab-selected reports: Income Summary,
 * Income Detail, and Income Without a Project. The cost report's mirror, with
 * two deliberate differences carried over from the web:
 *
 * Project-wide income keeps its own column instead of being spread over the
 * buildings by square feet. A building's share of the land is genuinely part
 * of what it cost to build; rent on a hoarding is not owed to a flat in
 * proportion to its floor area.
 *
 * And the report follows the SALE, not the cash — a unit sale books its whole
 * contract value on the day the flat is sold, while the money arrives over
 * months of installments.
 */
@Composable
fun ProjectIncomeReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectIncomeReportViewModel = viewModel(
        factory = ProjectIncomeReportViewModel.provideFactory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
        title = "Project Income Report",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = { TutorialVideoLink(screenKey = TutorialScreens.PROJECT_INCOME_REPORT) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // ---- Filters ----
                IncomeBranchDropdown(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranchSelected,
                )
                state.branchesError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showIncomeReportDatePicker(context, state.startDate, viewModel::onStartDate)
                        },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showIncomeReportDatePicker(context, state.endDate, viewModel::onEndDate)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilterActions(
                    onApply = viewModel::apply,
                    canApply = state.selectedBranch != null && !state.isLoading,
                    isLoading = state.isLoading,
                )

                Spacer(Modifier.height(14.dp))

                // ---- Tabs ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProjectIncomeSection.entries.forEach { section ->
                        IncomeSectionTab(
                            section = section,
                            isActive = state.section == section,
                            // No red badge on the untagged count here: branch
                            // income with no project is the expected state, not
                            // a backlog of mistakes.
                            onClick = { viewModel.onSection(section) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.section.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )

                Spacer(Modifier.height(10.dp))

                // ---- Result ----
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    !state.sectionLoaded -> IncomeEmptyState(
                        "Select a branch and date range, then tap Apply.",
                    )

                    state.section == ProjectIncomeSection.SUMMARY -> IncomeSummaryResult(state.summaryRows)

                    state.section == ProjectIncomeSection.DETAIL -> IncomeDetailResult(state.detailRows)

                    else -> UntaggedIncomeResult(
                        rows = state.untaggedRows,
                        onTag = { row ->
                            navController.navigate(Routes.projectIncomeFor(row.vrNo))
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ---------------------------------------------------------------------------
// Tabs and filters
// ---------------------------------------------------------------------------

@Composable
private fun IncomeSectionTab(
    section: ProjectIncomeSection,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val ink = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun IncomeBranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PickerField(
            label = "Select Branch",
            value = selected?.name ?: if (isLoading) "Loading branches…" else "",
            placeholder = "Select Branch",
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        onSelected(branch)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The three results
// ---------------------------------------------------------------------------

@Composable
private fun IncomeSummaryResult(rows: List<ProjectIncomeSummaryRow>) {
    if (rows.isEmpty()) {
        IncomeEmptyState("No income has been tagged to a project in this range yet.")
        return
    }
    ReportTable(
        columns = incomeSummaryColumns(),
        data = rows,
        scrollable = false,
    )
    IncomeTotalLine(
        label = "Total",
        values = listOf(
            "Building " + AmountFormat.format(rows.sumOf { it.directIncome }),
            "Project-wide " + AmountFormat.format(rows.sumOf { it.commonIncome }),
            AmountFormat.format(rows.sumOf { it.totalIncome }),
        ),
    )
}

@Composable
private fun incomeSummaryColumns(): List<ReportColumn<ProjectIncomeSummaryRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Project", ReportColWidth.Weight(1.1f)) { row, _ ->
            cellText(row.projectName.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Building", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.directIncome), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Project-wide", ReportColWidth.Fixed(104.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.commonIncome), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Total", ReportColWidth.Fixed(100.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.totalIncome), align = TextAlign.End, bold = true, color = onScreen)
        },
    )
}

@Composable
private fun IncomeDetailResult(rows: List<ProjectIncomeDetailRow>) {
    if (rows.isEmpty()) {
        IncomeEmptyState("No income has been tagged to a project in this range yet.")
        return
    }
    ReportTable(
        columns = incomeDetailColumns(),
        data = rows,
        scrollable = false,
    )
    IncomeTotalLine(label = "Total", values = listOf(AmountFormat.format(rows.sumOf { it.amount })))
}

@Composable
private fun incomeDetailColumns(): List<ReportColumn<ProjectIncomeDetailRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Building", ReportColWidth.Weight(1.1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        // A row with no building is income the whole project
                        // earned, which is not a missing name.
                        text = row.buildingName.ifBlank { "Whole project" },
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        maxLines = 2,
                    )
                    Text(
                        text = row.projectName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        },
        ReportColumn("Head", ReportColWidth.Weight(0.9f)) { row, _ ->
            cellText(row.head.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Amount", ReportColWidth.Fixed(100.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.amount), align = TextAlign.End, bold = true, color = onScreen)
        },
    )
}

@Composable
private fun UntaggedIncomeResult(
    rows: List<UntaggedIncomeRow>,
    onTag: (UntaggedIncomeRow) -> Unit,
) {
    if (rows.isEmpty()) {
        IncomeEmptyState("Every income line in this range carries a project.")
        return
    }
    ReportTable(
        columns = untaggedIncomeColumns(onTag),
        data = rows,
        scrollable = false,
    )
    IncomeTotalLine(label = "Total", values = listOf(AmountFormat.format(rows.sumOf { it.amount })))
}

@Composable
private fun untaggedIncomeColumns(
    onTag: (UntaggedIncomeRow) -> Unit,
): List<ReportColumn<UntaggedIncomeRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Voucher", ReportColWidth.Weight(0.8f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.vrNo,
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        maxLines = 1,
                    )
                    Text(
                        text = SimpleDate.fromApi(row.vrDate.take(10))?.toDisplay() ?: row.vrDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        ReportColumn("Head", ReportColWidth.Weight(1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.head.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
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
        ReportColumn("Amount", ReportColWidth.Fixed(92.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.amount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Action", ReportColWidth.Fixed(60.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Opens the voucher on the income screen so a project can be
                    // given to it. Only receipts raised there can be corrected
                    // this way; a unit sale receipt is refused by the server,
                    // which is the right answer — it is not this screen's
                    // voucher to edit.
                    IconButton(onClick = { onTag(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Open this voucher and give it a project",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun IncomeEmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.appColors.textOnScreenMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The grand-total line under a tab's table. */
@Composable
private fun IncomeTotalLine(label: String, values: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Column(horizontalAlignment = Alignment.End) {
            values.forEach { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

private fun showIncomeReportDatePicker(context: Context, current: SimpleDate?, onPicked: (SimpleDate) -> Unit) {
    val initial = current ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
