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
import androidx.compose.material.icons.filled.Lock
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
import com.example.cashbookbd.data.repository.BuildingDetailRow
import com.example.cashbookbd.data.repository.ProjectSummaryRow
import com.example.cashbookbd.data.repository.UntaggedExpenseRow
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
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
 * Project Cost — one screen, three tab-selected reports: Project Summary,
 * Building Detail, and Expenses Without a Project, plus the amber integrity
 * panel for tagged money none of them can show. The untagged tab's rows carry
 * a Tag button that deep-links into Project Expense with the voucher loaded —
 * or a lock, where approval has closed the door.
 */
@Composable
fun ProjectCostReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectCostReportViewModel = viewModel(
        factory = ProjectCostReportViewModel.provideFactory(androidx.compose.ui.platform.LocalContext.current),
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
        title = "Project Cost Report",
        currentRoute = Routes.REAL_ESTATE,
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
                // ---- Filters ----
                ReportBranchDropdown(
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
                            showReportDatePicker(context, state.startDate, viewModel::onStartDate)
                        },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showReportDatePicker(context, state.endDate, viewModel::onEndDate)
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
                    ProjectCostSection.entries.forEach { section ->
                        SectionTab(
                            section = section,
                            isActive = state.section == section,
                            badgeCount = if (section == ProjectCostSection.UNTAGGED) state.untaggedRows.size else 0,
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

                // ---- Integrity panel ----
                if (state.integrity.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.appColors.warningTint, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "${state.integrity.size} recorded cost(s) the reports above cannot show",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = AppFontWeight.SemiBold,
                            color = MaterialTheme.appColors.warning,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "A deleted project or a moved building takes its tagged money " +
                                "out of every report at once. This panel is the only place it " +
                                "still shows.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.warning,
                        )
                        state.integrity.forEach { row ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${row.vrNo} · ${row.head} · ${AmountFormat.format(row.amount)} — ${row.problem}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.warning,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ---- Result ----
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    !state.sectionLoaded -> EmptyState(
                        "Select a branch and date range, then tap Apply.",
                    )

                    state.section == ProjectCostSection.SUMMARY -> SummaryResult(state.summaryRows)

                    state.section == ProjectCostSection.BUILDING -> BuildingResult(state.buildingRows)

                    else -> UntaggedResult(
                        rows = state.untaggedRows,
                        onTag = { row ->
                            navController.navigate(Routes.projectExpenseFor(row.vrNo))
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
private fun SectionTab(
    section: ProjectCostSection,
    isActive: Boolean,
    badgeCount: Int,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
        )
        if (badgeCount > 0) {
            Text(
                text = badgeCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ReportBranchDropdown(
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
private fun SummaryResult(rows: List<ProjectSummaryRow>) {
    if (rows.isEmpty()) {
        EmptyState("No cost has been tagged to a project in this range yet.")
        return
    }
    ReportTable(
        columns = summaryColumns(),
        data = rows,
        scrollable = false,
    )
    TotalLine(
        label = "Total",
        values = listOf(
            "Direct " + AmountFormat.format(rows.sumOf { it.directCost }),
            "Project-wide " + AmountFormat.format(rows.sumOf { it.commonCost }),
            AmountFormat.format(rows.sumOf { it.totalCost }),
        ),
    )
}

@Composable
private fun summaryColumns(): List<ReportColumn<ProjectSummaryRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Project", ReportColWidth.Weight(1.1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.projectName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        maxLines = 2,
                    )
                    Text(
                        // "not recorded" is a different statement than zero.
                        text = if (row.totalSqft > 0) {
                            "${AmountFormat.format(row.totalSqft)} sqft"
                        } else {
                            "area not recorded"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        ReportColumn("Direct", ReportColWidth.Fixed(88.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.directCost), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Project-wide", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.commonCost), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Total", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.totalCost), align = TextAlign.End, bold = true, color = onScreen)
        },
        ReportColumn("Per sqft", ReportColWidth.Fixed(80.dp), TextAlign.End) { row, _ ->
            cellText(
                // Null means no recorded area — a different answer than zero cost.
                row.costPerSqft?.let { AmountFormat.format(it) } ?: "no area",
                align = TextAlign.End,
                color = onScreen,
            )
        },
    )
}

@Composable
private fun BuildingResult(rows: List<BuildingDetailRow>) {
    if (rows.isEmpty()) {
        EmptyState("No cost has been tagged to a project in this range yet.")
        return
    }
    ReportTable(
        columns = buildingColumns(),
        data = rows,
        scrollable = false,
    )
    TotalLine(
        label = "Total",
        values = listOf(
            "Direct " + AmountFormat.format(rows.sumOf { it.directCost }),
            "Allocated " + AmountFormat.format(rows.sumOf { it.allocatedCost }),
            AmountFormat.format(rows.sumOf { it.totalCost }),
        ),
    )
}

@Composable
private fun buildingColumns(): List<ReportColumn<BuildingDetailRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Building", ReportColWidth.Weight(1.1f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.buildingName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        maxLines = 2,
                    )
                    Text(
                        text = buildString {
                            append(row.projectName)
                            if (row.buildingSqft > 0) {
                                append(" · ${AmountFormat.format(row.buildingSqft)} sqft · ${row.sqftPct}%")
                            }
                        },
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
        ReportColumn("Direct", ReportColWidth.Fixed(88.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.directCost), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Allocated", ReportColWidth.Fixed(88.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.allocatedCost), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Total", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.totalCost), align = TextAlign.End, bold = true, color = onScreen)
        },
    )
}

@Composable
private fun UntaggedResult(
    rows: List<UntaggedExpenseRow>,
    onTag: (UntaggedExpenseRow) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState("Every expense in this range carries a project. That is the answer you want here.")
        return
    }
    ReportTable(
        columns = untaggedColumns(onTag),
        data = rows,
        scrollable = false,
    )
    TotalLine(label = "Total", values = listOf(AmountFormat.format(rows.sumOf { it.amount })))
}

@Composable
private fun untaggedColumns(
    onTag: (UntaggedExpenseRow) -> Unit,
): List<ReportColumn<UntaggedExpenseRow>> {
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
                    if (row.isApproved) {
                        // Approved vouchers cannot be edited: remove the
                        // approval first, then tag it.
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Approved — locked for editing",
                            tint = MaterialTheme.appColors.warning,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        IconButton(onClick = { onTag(row) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Open this voucher and give it a project",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
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
private fun EmptyState(text: String) {
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
private fun TotalLine(label: String, values: List<String>) {
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

private fun showReportDatePicker(context: Context, current: SimpleDate?, onPicked: (SimpleDate) -> Unit) {
    val initial = current ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
