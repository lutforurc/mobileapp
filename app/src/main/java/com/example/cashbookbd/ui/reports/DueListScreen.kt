package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
            if (uiState.report != null && !uiState.isEmptyResult) {
                AgeingToggle(checked = uiState.showAgeing, onChecked = viewModel::onShowAgeing)
            }
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
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No dues found for this selection.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        else -> DueTable(report = state.report, showAgeing = state.showAgeing)
    }
}

/** The web's Ageing switch, in the row above the table. */
@Composable
private fun AgeingToggle(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "Ageing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.scale(0.8f),
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 48.dp
private val COL_PARTY = 200.dp
private val COL_PAGE = 92.dp
private val COL_AREA = 80.dp
private val COL_NUM = 104.dp
private val COL_LAST_PAID = 112.dp
private val COL_BUCKET = 96.dp

/**
 * The web's column order: Sl · Customer/Supplier · Page · Area Code · Debit ·
 * Credit, then — with the Ageing switch on — Last Paid and the four buckets.
 */
@Composable
private fun dueListColumns(showAgeing: Boolean): List<ReportColumn<DueRow>> {
    val danger = MaterialTheme.appColors.danger
    val success = MaterialTheme.appColors.success
    val warning = MaterialTheme.appColors.warning
    return buildList {
        add(ReportColumn("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
            cellText((i + 1).toString(), align = TextAlign.Center)
        })
        add(ReportColumn("CUSTOMER/SUPPLIER", ReportColWidth.Fixed(COL_PARTY)) { r, _ ->
            ReportTableCell.Slot { PartyCell(r) }
        })
        add(ReportColumn("PAGE", ReportColWidth.Fixed(COL_PAGE)) { r, _ ->
            cellText(r.page ?: "-", maxLines = 2)
        })
        add(ReportColumn("AREA", ReportColWidth.Fixed(COL_AREA)) { r, _ ->
            cellText(r.areaCode ?: "-")
        })
        add(ReportColumn("DEBIT", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
            cellText(formatCell(r.debit), align = TextAlign.End)
        })
        add(ReportColumn("CREDIT", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
            cellText(formatCell(r.credit), align = TextAlign.End)
        })
        if (!showAgeing) return@buildList
        add(ReportColumn("LAST PAID", ReportColWidth.Fixed(COL_LAST_PAID), TextAlign.Center) { r, _ ->
            ReportTableCell.Slot { LastPaidCell(r, success = success, warning = warning, danger = danger) }
        })
        listOf("0-30 d", "31-60 d", "61-90 d").forEachIndexed { index, header ->
            add(ReportColumn(header, ReportColWidth.Fixed(COL_BUCKET), TextAlign.End) { r, _ ->
                cellText(formatCell(r.ageing.getOrElse(index) { 0.0 }), align = TextAlign.End)
            })
        }
        add(ReportColumn("90+ DAYS", ReportColWidth.Fixed(COL_BUCKET), TextAlign.End) { r, _ ->
            ReportTableCell.Slot { OverNinetyCell(r, danger = danger) }
        })
    }
}

@Composable
private fun DueTable(report: com.example.cashbookbd.ui.reports.model.DueListReport, showAgeing: Boolean) {
    val columns = dueListColumns(showAgeing)
    ReportTable(
        columns = columns,
        data = report.rows,
        footerRows = dueFooterRows(report, columns.size),
    )
}

/**
 * Last Paid (web d6044d19 / 23063974): the receipt date over its age, coloured
 * by how recent it is — the thresholds are the bucket edges, 30 and 90 days.
 * A party never paid on these books says "never", in red, with no date line:
 * a quiet account reads differently from a slow one.
 */
@Composable
private fun LastPaidCell(
    row: DueRow,
    success: androidx.compose.ui.graphics.Color,
    warning: androidx.compose.ui.graphics.Color,
    danger: androidx.compose.ui.graphics.Color,
) {
    val tone = when {
        row.lastPaidDays == null -> danger
        row.lastPaidDays <= 30 -> success
        row.lastPaidDays <= 90 -> warning
        else -> danger
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (row.lastPaid == null) {
            Text(
                text = "never",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = danger,
            )
        } else {
            Text(
                text = displayDate(row.lastPaid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            row.lastPaidAge?.let {
                Text(
                    text = it.format(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = tone,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The 90+ bucket in red when anything sits in it, with the oldest item's age
 * beneath (web 5f491c2f / baad5aec) — the figure says how much, the age says
 * how long somebody has been waiting.
 */
@Composable
private fun OverNinetyCell(row: DueRow, danger: androidx.compose.ui.graphics.Color) {
    val amount = row.ageing.getOrElse(3) { 0.0 }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = formatCell(amount),
            style = MaterialTheme.typography.bodySmall,
            color = if (amount > 0) danger else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        if (amount > 0 && row.oldestDays > 90) {
            row.oldestAge?.let {
                Text(
                    text = it.format(),
                    style = MaterialTheme.typography.labelSmall,
                    color = danger,
                    maxLines = 1,
                )
            }
        }
    }
}

/** yyyy-MM-dd → dd/MM/yyyy; anything else verbatim. */
private fun displayDate(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw) ?: return raw
    val (y, mo, d) = m.destructured
    return "$d/$mo/$y"
}

/** Total and the net Balance rows, mirroring the backend's summary lines. */
private fun dueFooterRows(
    report: com.example.cashbookbd.ui.reports.model.DueListReport,
    columnCount: Int,
): List<List<ReportFooterCell>> = listOf(
    dueFooterRow("Total", report.totalDebit, report.totalCredit, columnCount),
    dueFooterRow(
        "Balance",
        // Net balance shows on the side it falls: receivable → debit, advance → credit.
        if (report.netBalance >= 0) report.netBalance else 0.0,
        if (report.netBalance < 0) -report.netBalance else 0.0,
        columnCount,
    ),
)

/**
 * A bold Total / Balance footer row with the label under the party column.
 * The ageing columns stay blank on purpose: the buckets already sum to each
 * row's debit, and a second total would invite adding them to it.
 */
private fun dueFooterRow(label: String, debit: Double, credit: Double, columnCount: Int): List<ReportFooterCell> =
    buildList {
        add(ReportFooterCell(ReportTableCell.Empty))                    // SL
        add(ReportFooterCell(cellText(label, bold = true)))             // party
        add(ReportFooterCell(ReportTableCell.Empty))                    // page
        add(ReportFooterCell(ReportTableCell.Empty))                    // area
        add(ReportFooterCell(cellText(formatCell(debit), align = TextAlign.End, bold = true)))
        add(ReportFooterCell(cellText(formatCell(credit), align = TextAlign.End, bold = true)))
        repeat(columnCount - size) { add(ReportFooterCell(ReportTableCell.Empty)) }
    }

/** The party column's stacked name / phone / address block. */
@Composable
private fun PartyCell(row: DueRow) {
    // The cell draws on the teal screen backdrop, so every line takes an on-teal
    // ink: full-strength for the name, a faded on-background for the sub-lines.
    // onSurface/onSurfaceVariant are the card inks and wash out on the teal.
    val onScreen = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = row.customer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = onScreen,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        row.mobile?.let {
            // Grouped by the branch's pattern (dc17c5a); digits stay stored.
            val mobileFormat = com.example.cashbookbd.di.ServiceLocator
                .provideSessionManager(androidx.compose.ui.platform.LocalContext.current)
                .state.collectAsStateWithLifecycle().value.settings?.mobileNumberFormat.orEmpty()
            Text(
                text = com.example.cashbookbd.core.MobileFormat.format(it, mobileFormat),
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.address?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
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
