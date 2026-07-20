package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.data.repository.TrialBalanceRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.reports.model.TrialBalanceReport
import com.example.cashbookbd.ui.reports.model.TrialBalanceRow
import com.example.cashbookbd.core.AmountFormat

/**
 * Trial Balance Details (Level 4): a filterable, horizontally-scrollable table
 * report that preserves the web layout — filter area, closing summary boxes, and
 * an 8-column table (SL, Description, Opening/Movement/Closing Dr & Cr).
 */
@Composable
fun TrialBalanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Trial Balance Details",
    reportPath: String = TrialBalanceRepository.PATH_LEVEL4,
    viewModel: TrialBalanceViewModel = viewModel(
        factory = TrialBalanceViewModel.provideFactory(LocalContext.current, reportPath)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = title,
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
                onReset = viewModel::reset,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f)) {
                Results(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------

@Composable
private fun FilterCard(
    state: TrialBalanceUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BranchDropdown(
            branches = state.branches,
            selected = state.selectedBranch,
            isLoading = state.isBranchesLoading,
            onSelected = onBranchSelected,
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                label = "Start Date",
                value = state.startDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.startDate, onStartDate) },
            )
            PickerField(
                label = "End Date",
                value = state.endDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.endDate, onEndDate) },
            )
        }

        Spacer(Modifier.height(14.dp))
        FilterActions(
            onApply = onApply,
            onReset = onReset,
            canApply = state.canApply,
            isLoading = state.isReportLoading,
        )
    }
}

@Composable
private fun BranchDropdown(
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
// Results
// ---------------------------------------------------------------------------

@Composable
private fun Results(state: TrialBalanceUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

        state.reportError != null -> CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.reportError,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.report == null -> CenterBox {
            Text(
                text = "Choose a branch and date range, then tap Apply.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportContent(
            report = state.report,
            range = state.appliedRange,
        )
    }
}

@Composable
private fun ReportContent(
    report: TrialBalanceReport,
    range: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary boxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryBox(
                label = "Closing Debit",
                value = formatAmount(report.closingDebit),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryBox(
                label = "Closing Credit",
                value = formatAmount(report.closingCredit),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            SummaryBox(
                label = "Difference",
                value = formatAmount(report.difference),
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }

        // Reporting date range (no section title / branch name).
        if (!range.isNullOrBlank()) {
            Text(
                text = "Reporting date: $range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (report.isEmpty) {
            CenterBox {
                Text(
                    text = "No trial balance data for this selection.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            TrialBalanceTable(rows = report.rows)
        }
    }
}

@Composable
private fun SummaryBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    SummaryTile(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 44.dp
private val COL_DESC = 190.dp
private val COL_NUM = 104.dp

/**
 * Grouped layout shared by both trial balance reports: a "#" serial (centered),
 * Description, then Dr/Cr pairs whose group name (OPENING/MOVEMENT/CLOSING) sits
 * in the [trialBalanceHeaderGroups] top row.
 */
private val trialBalanceGroupedColumns = listOf(
    ReportColumn<TrialBalanceRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
        cellText((i + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<TrialBalanceRow>("DESCRIPTION", ReportColWidth.Fixed(COL_DESC)) { r, _ ->
        cellText(r.description, maxLines = 2)
    },
    ReportColumn<TrialBalanceRow>("DR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.openingDebit), align = TextAlign.End)
    },
    ReportColumn<TrialBalanceRow>("CR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.openingCredit), align = TextAlign.End)
    },
    ReportColumn<TrialBalanceRow>("DR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.movementDebit), align = TextAlign.End)
    },
    ReportColumn<TrialBalanceRow>("CR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.movementCredit), align = TextAlign.End)
    },
    ReportColumn<TrialBalanceRow>("DR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.closingDebit), align = TextAlign.End)
    },
    ReportColumn<TrialBalanceRow>("CR", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.closingCredit), align = TextAlign.End)
    },
)

private val trialBalanceHeaderGroups = listOf(
    ReportHeaderGroup("", 1),          // # (serial)
    ReportHeaderGroup("", 1),          // Description
    ReportHeaderGroup("OPENING", 2),
    ReportHeaderGroup("MOVEMENT", 2),
    ReportHeaderGroup("CLOSING", 2),
)

@Composable
private fun TrialBalanceTable(rows: List<TrialBalanceRow>) {
    // Grand Total footer: label across #/Description, then each column's sum.
    val grandTotal = listOf(
        ReportFooterCell(cellText("Grand Total", bold = true), colSpan = 2),
        totalCell(rows.sumOf { it.openingDebit }),
        totalCell(rows.sumOf { it.openingCredit }),
        totalCell(rows.sumOf { it.movementDebit }),
        totalCell(rows.sumOf { it.movementCredit }),
        totalCell(rows.sumOf { it.closingDebit }),
        totalCell(rows.sumOf { it.closingCredit }),
    )
    ReportTable(
        columns = trialBalanceGroupedColumns,
        data = rows,
        headerGroups = trialBalanceHeaderGroups,
        footerRows = listOf(grandTotal),
    )
}

private fun totalCell(value: Double): ReportFooterCell =
    ReportFooterCell(cellText(formatCell(value), align = TextAlign.End, bold = true))

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun formatAmount(value: Double): String = AmountFormat.format(value)

/** Blank out zeros so the numeric columns stay readable, like the web report. */
private fun formatCell(value: Double): String = AmountFormat.formatOrDash(value)

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
