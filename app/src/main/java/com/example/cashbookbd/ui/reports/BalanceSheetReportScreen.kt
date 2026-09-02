package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BalanceSheetColumns
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetItem
import com.example.cashbookbd.ui.reports.model.BalanceSheetReport
import com.example.cashbookbd.ui.reports.model.BalanceSheetSubsection
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.core.AmountFormat

/**
 * Balance Sheet in its standard shape (react 4c718343 / 303d2df1 / 84bf7deb):
 * ONE running table, the shape the trial balance already has — Serial ·
 * Description · Opening (Dr, Cr) · Movement (Dr, Cr) · Closing (Dr, Cr).
 * Assets sit in the Dr column, liabilities and equity in the Cr column, which
 * is what makes the grand totals agree in all three columns when the sheet
 * balances. Each side is broken by the chart's level-2 heads (Current Assets,
 * Fixed Asset, Capital Account…), and a section carrying accumulated
 * depreciation draws it as one "Less:" line then "Net" — never two lines at
 * the same level, since adding depreciation to cost is the mistake the layout
 * exists to prevent. An older server without `sections` gets the flat lists
 * under the same headings, readable without the Current/Fixed split.
 */
@Composable
fun BalanceSheetReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BalanceSheetViewModel = viewModel(
        factory = BalanceSheetViewModel.provideFactory(LocalContext.current)
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
        title = "Balance Sheet",
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
    state: BalanceSheetUiState,
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
private fun Results(state: BalanceSheetUiState, onRetry: () -> Unit) {
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
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No Balance Sheet data for this selection.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportContent(
            report = state.report,
            branchName = state.appliedBranchName,
            range = state.appliedRange,
        )
    }
}

// ---------------------------------------------------------------------------
// The sheet, as lines
// ---------------------------------------------------------------------------

/** Which money column a line's figures sit in. */
private enum class Side { DR, CR, NONE }

private enum class LineKind { SECTION, SUBSECTION, ITEM, LESS, SUBTOTAL, TOTAL, GRAND }

/**
 * One row of the running table — the web's SheetLine. A band (section),
 * a level-2 heading, a numbered group that opens its accounts, the Less line,
 * a subsection's Net/Total, a side's Total, and the grand total at the foot.
 */
private data class SheetLine(
    val kind: LineKind,
    val label: String,
    val side: Side = Side.NONE,
    val columns: BalanceSheetColumns? = null,
    val serial: Int? = null,
    /** The group behind an ITEM line, for the breakdown sheet. */
    val group: BalanceSheetGroup? = null,
    val sectionTitle: String = "",
) {
    val isBand: Boolean get() = kind == LineKind.SECTION
    val isBold: Boolean get() = kind != LineKind.ITEM && kind != LineKind.LESS && kind != LineKind.SUBSECTION
    val isSummary: Boolean get() = kind == LineKind.SUBTOTAL || kind == LineKind.TOTAL || kind == LineKind.GRAND
}

/**
 * The web's emission order (pushSectioned): band, then per level-2 head its
 * heading and numbered items, then Less/Net or Total, then the side's total;
 * and after all three sides, Total Liabilities & Equity. Serials run straight
 * through every side, as on the paper.
 */
private fun buildLines(report: BalanceSheetReport): List<SheetLine> {
    val lines = mutableListOf<SheetLine>()
    var serial = 0
    val sideTotals = mutableMapOf<String, BalanceSheetColumns>()

    val sides: List<Triple<String, Side, List<BalanceSheetSubsection>>> = if (report.hasSections) {
        listOf(
            Triple("Assets", Side.DR, report.sectioned["Assets"].orEmpty()),
            Triple("Liabilities", Side.CR, report.sectioned["Liabilities"].orEmpty()),
            Triple("Equity", Side.CR, report.sectioned["Equity"].orEmpty()),
        )
    } else {
        // The fallback for an older server: the flat lists under the same
        // headings, one unnamed subsection each, readable without the split.
        report.sections.map { section ->
            val side = if (section.title == "Assets") Side.DR else Side.CR
            Triple(
                section.title, side,
                listOf(
                    BalanceSheetSubsection(
                        name = "",
                        groups = section.groups,
                        columns = BalanceSheetColumns(section.opening, section.movement, section.closing),
                    )
                ),
            )
        }
    }

    for ((title, side, subsections) in sides) {
        if (subsections.isEmpty()) continue
        lines += SheetLine(LineKind.SECTION, title, sectionTitle = title)
        var sideTotal = BalanceSheetColumns.ZERO
        for (sub in subsections) {
            if (sub.name.isNotBlank()) lines += SheetLine(LineKind.SUBSECTION, sub.name, sectionTitle = title)
            for (group in sub.listedGroups) {
                serial += 1
                lines += SheetLine(
                    kind = LineKind.ITEM,
                    label = group.title,
                    side = side,
                    columns = BalanceSheetColumns(group.opening, group.movement, group.closing),
                    serial = serial,
                    group = group,
                    sectionTitle = title,
                )
            }
            when {
                sub.hasContra -> {
                    // Negated from what the server reports, so the deduction
                    // reads in brackets under the cost it comes off.
                    lines += SheetLine(
                        kind = LineKind.LESS,
                        label = "Less: Accumulated Depreciation",
                        side = side,
                        columns = (sub.depreciation ?: BalanceSheetColumns.ZERO).negated(),
                        sectionTitle = title,
                    )
                    lines += SheetLine(LineKind.SUBTOTAL, "Net ${sub.name}", side, sub.columns, sectionTitle = title)
                }
                sub.name.isNotBlank() ->
                    lines += SheetLine(LineKind.SUBTOTAL, "Total ${sub.name}", side, sub.columns, sectionTitle = title)
            }
            sideTotal += sub.columns
        }
        sideTotals[title] = sideTotal
        val totalLabel = if (report.hasSections || title == "Assets") "Total $title" else "$title Total"
        lines += SheetLine(LineKind.TOTAL, totalLabel, side, sideTotal, sectionTitle = title)
    }

    val liabAndEquity = (sideTotals["Liabilities"] ?: BalanceSheetColumns.ZERO) +
        (sideTotals["Equity"] ?: BalanceSheetColumns.ZERO)
    if (sideTotals.containsKey("Liabilities") || sideTotals.containsKey("Equity")) {
        lines += SheetLine(LineKind.GRAND, "Total Liabilities & Equity", Side.CR, liabAndEquity)
    }
    return lines
}

@Composable
private fun ReportContent(
    report: BalanceSheetReport,
    branchName: String?,
    range: String?,
) {
    // The group whose account breakdown is open (with its side's title), if any.
    var selectedGroup by remember { mutableStateOf<Pair<String, BalanceSheetGroup>?>(null) }
    val lines = remember(report) { buildLines(report) }
    val summaryInk = MaterialTheme.colorScheme.onSecondaryContainer
    val band = MaterialTheme.colorScheme.secondaryContainer
    val columns = remember(summaryInk) {
        sheetColumns(summaryInk) { line -> line.group?.let { selectedGroup = line.sectionTitle to it } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val subtitle = listOfNotNull(branchName, range).joinToString("  •  ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            ReportTable(
                columns = columns,
                data = lines,
                headerGroups = sheetHeaderGroups,
                rowBackground = { line, _ -> if (line.isBand || line.isSummary) band else null },
            )
        }
        DifferenceNote(report)
    }

    selectedGroup?.let { (sectionTitle, group) ->
        GroupDetailsSheet(
            sectionTitle = sectionTitle,
            group = group,
            onDismiss = { selectedGroup = null },
        )
    }
}

/**
 * The web shows its net-profit debug block only when the two sides disagree;
 * here one line says by how much, so a reader knows to look at the chart.
 */
@Composable
private fun DifferenceNote(report: BalanceSheetReport) {
    val difference = report.summary.firstOrNull { it.label == "Difference" }?.value ?: 0.0
    if (kotlin.math.abs(difference) <= 0.009) return
    Text(
        text = "Difference detected: ${formatSigned(difference)}. " +
            "Please review opening, movement, or group mapping.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.appColors.danger,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// # | Description | Opening Dr Cr | Movement Dr Cr | Closing Dr Cr — the
// trial balance's grid, so the two reports read the same way.
private val BS_COL_SL = 44.dp
private val BS_COL_DESC = 176.dp
private val BS_COL_NUM = 96.dp

private val sheetHeaderGroups = listOf(
    ReportHeaderGroup("", 1),
    ReportHeaderGroup("", 1),
    ReportHeaderGroup("OPENING", 2),
    ReportHeaderGroup("MOVEMENT", 2),
    ReportHeaderGroup("CLOSING", 2),
)

private fun sheetColumns(
    summaryInk: Color,
    onItemClick: (SheetLine) -> Unit,
): List<ReportColumn<SheetLine>> {
    fun ink(line: SheetLine) = if (line.isBand || line.isSummary) summaryInk else Color.Unspecified
    fun money(line: SheetLine, value: Double?, side: Side): ReportTableCell =
        cellText(
            text = if (value == null || line.side != side) "-" else amountCell(value),
            align = TextAlign.End,
            bold = line.isBold,
            color = ink(line),
        )
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(BS_COL_SL), TextAlign.Center) { line, _ ->
            cellText(line.serial?.toString() ?: "", align = TextAlign.Center, color = ink(line))
        },
        ReportColumn("DESCRIPTION", ReportColWidth.Fixed(BS_COL_DESC)) { line, _ ->
            if (line.kind == LineKind.ITEM) {
                ReportTableCell.Slot { ItemNameCell(line, onClick = { onItemClick(line) }) }
            } else {
                cellText(
                    text = line.label,
                    bold = line.isBold || line.kind == LineKind.SUBSECTION,
                    color = ink(line),
                    maxLines = 2,
                    // The deduction is indented under the cost it comes off.
                    startPadding = if (line.kind == LineKind.LESS) 12.dp else 0.dp,
                )
            }
        },
        ReportColumn("DR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.opening, Side.DR) },
        ReportColumn("CR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.opening, Side.CR) },
        ReportColumn("DR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.movement, Side.DR) },
        ReportColumn("CR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.movement, Side.CR) },
        ReportColumn("DR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.closing, Side.DR) },
        ReportColumn("CR", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { l, _ -> money(l, l.columns?.closing, Side.CR) },
    )
}

/** A numbered group's name with its account count beneath; taps open the breakdown. */
@Composable
private fun ItemNameCell(line: SheetLine, onClick: () -> Unit) {
    val count = line.group?.items?.size ?: 0
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = line.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count account" + if (count == 1) "" else "s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
    }
}

// PARTICULAR | OPENING | MOVEMENT | CLOSING for the breakdown's accounts.
private val groupItemColumns = listOf(
    ReportColumn<BalanceSheetItem>("PARTICULAR", ReportColWidth.Fixed(150.dp)) { item, _ ->
        cellText(item.description.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn<BalanceSheetItem>("OPENING", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { item, _ ->
        cellText(amountCell(item.opening), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetItem>("MOVEMENT", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { item, _ ->
        cellText(amountCell(item.movement), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetItem>("CLOSING", ReportColWidth.Fixed(BS_COL_NUM), TextAlign.End) { item, _ ->
        cellText(amountCell(item.closing), align = TextAlign.End, bold = true)
    },
)

/** The web's GroupDetailsModal: stats for the tapped group, then its accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailsSheet(
    sectionTitle: String,
    group: BalanceSheetGroup,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "$sectionTitle: ${group.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("Opening", formatSigned(group.opening))
                StatTile("Movement", formatSigned(group.movement))
                StatTile("Closing", formatSigned(group.closing))
                StatTile("Accounts", group.items.size.toString())
            }
            ReportTable(
                columns = groupItemColumns,
                data = group.items,
                noDataMessage = "No detailed items found for this summary.",
                scrollable = false,
            )
        }
    }
}

/** A small labelled figure box (the breakdown's stats). */
@Composable
private fun StatTile(label: String, value: String) {
    SummaryTile {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Zero is a dash and a negative is bracketed — screen and paper agree (web 84bf7deb). */
private fun amountCell(value: Double): String = when {
    kotlin.math.abs(value) < 0.005 -> "-"
    value < 0 -> "(${AmountFormat.format(-value)})"
    else -> AmountFormat.format(value)
}

/** The web's formatAmount: negatives are parenthesised — (1,234.00). */
private fun formatSigned(value: Double): String =
    if (value < 0) "(${AmountFormat.format(-value)})" else AmountFormat.format(value)

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
