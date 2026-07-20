package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.DueRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.core.AmountFormat

/**
 * Due List: filter (branch + end date), a Total Due summary, and each customer's
 * dues as a compact label/value row block. `GET /reports/duelist?branch_id=&enddate=`.
 */
@Composable
fun DueListScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DueListViewModel = viewModel(
        factory = DueListViewModel.provideFactory(LocalContext.current)
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
        title = "Due List",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
                onReset = viewModel::reset,
            )
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    state: DueListUiState,
    onBranchSelected: (BranchOption) -> Unit,
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
        PickerField(
            label = "End Date",
            value = state.endDate.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDatePicker(context, state.endDate, onEndDate) },
        )

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
private fun Results(state: DueListUiState, onRetry: () -> Unit) {
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
                text = "Choose a branch and end date, then tap Apply.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No dues found for this selection.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> DueTable(report = state.report)
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 48.dp
private val COL_PARTY = 200.dp
private val COL_PAGE = 92.dp
private val COL_NUM = 104.dp

private val dueListColumns = listOf(
    ReportColumn<DueRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
        cellText((i + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<DueRow>("CUSTOMER/SUPPLIER", ReportColWidth.Fixed(COL_PARTY)) { r, _ ->
        ReportTableCell.Slot { PartyCell(r) }
    },
    ReportColumn<DueRow>("PAGE", ReportColWidth.Fixed(COL_PAGE)) { r, _ ->
        cellText(r.page ?: "-", maxLines = 2)
    },
    ReportColumn<DueRow>("DEBIT", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.debit), align = TextAlign.End)
    },
    ReportColumn<DueRow>("CREDIT", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.credit), align = TextAlign.End)
    },
)

@Composable
private fun DueTable(report: com.example.cashbookbd.ui.reports.model.DueListReport) {
    ReportTable(
        columns = dueListColumns,
        data = report.rows,
        footerRows = dueFooterRows(report),
    )
}

/** Total and the net Balance rows, mirroring the backend's summary lines. */
private fun dueFooterRows(
    report: com.example.cashbookbd.ui.reports.model.DueListReport,
): List<List<ReportFooterCell>> = listOf(
    dueFooterRow("Total", report.totalDebit, report.totalCredit),
    dueFooterRow(
        "Balance",
        // Net balance shows on the side it falls: receivable → debit, advance → credit.
        if (report.netBalance >= 0) report.netBalance else 0.0,
        if (report.netBalance < 0) -report.netBalance else 0.0,
    ),
)

/** A bold Total / Balance footer row with the label under the party column. */
private fun dueFooterRow(label: String, debit: Double, credit: Double): List<ReportFooterCell> =
    listOf(
        ReportFooterCell(ReportTableCell.Empty),                    // SL
        ReportFooterCell(cellText(label, bold = true)),             // party
        ReportFooterCell(ReportTableCell.Empty),                    // page
        ReportFooterCell(cellText(formatCell(debit), align = TextAlign.End, bold = true)),
        ReportFooterCell(cellText(formatCell(credit), align = TextAlign.End, bold = true)),
    )

/** The party column's stacked name / phone / address block. */
@Composable
private fun PartyCell(row: DueRow) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = row.customer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        row.mobile?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.address?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Blank out zeros so the numeric columns stay readable. */
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
