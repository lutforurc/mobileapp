package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetItem
import com.example.cashbookbd.ui.reports.model.BalanceSheetReport
import com.example.cashbookbd.ui.reports.model.BalanceSheetSection
import com.example.cashbookbd.ui.reports.model.BalanceSheetSummaryItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.core.AmountFormat

/**
 * Balance Sheet, matching the web report: summary boxes (Total Assets,
 * Liabilities + Equity, Difference), then the Assets / Liabilities / Equity
 * sections as Particulars | Opening | Movement | Closing tables — one tappable
 * row per group (with its item count) that opens the group's item breakdown —
 * and a Final Position card at the bottom.
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

@Composable
private fun ReportContent(
    report: BalanceSheetReport,
    branchName: String?,
    range: String?,
) {
    // The group whose item breakdown is open (with its section title), if any.
    var selectedGroup by remember { mutableStateOf<Pair<String, BalanceSheetGroup>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        val subtitle = listOfNotNull(branchName, range).joinToString("  •  ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        if (report.summary.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                report.summary.forEach { SummaryBox(it) }
            }
        }

        report.sections.forEach { section ->
            SectionBlock(section, onGroupClick = { selectedGroup = section.title to it })
            Spacer(Modifier.height(12.dp))
        }

        FinalPositionCard(report = report, branchName = branchName, range = range)
    }

    selectedGroup?.let { (sectionTitle, group) ->
        GroupDetailsSheet(
            sectionTitle = sectionTitle,
            group = group,
            onDismiss = { selectedGroup = null },
        )
    }
}

@Composable
private fun SummaryBox(item: BalanceSheetSummaryItem) {
    StatTile(label = item.label, value = formatSigned(item.value))
}

/** A small labelled figure box (the summary row and the breakdown's stats). */
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

// PARTICULARS | OPENING | MOVEMENT | CLOSING — one row per group, as on the
// web. The name cell carries the item-count badge and opens the breakdown.
private val BS_COL_PARTICULARS = 170.dp
private val BS_COL_AMOUNT = 100.dp

private fun balanceSheetColumns(onGroupClick: (BalanceSheetGroup) -> Unit) = listOf(
    ReportColumn<BalanceSheetGroup>("PARTICULARS", ReportColWidth.Fixed(BS_COL_PARTICULARS)) { g, _ ->
        ReportTableCell.Slot { GroupNameCell(group = g, onClick = { onGroupClick(g) }) }
    },
    ReportColumn<BalanceSheetGroup>("OPENING", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { g, _ ->
        cellText(amountOrDash(g.opening), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetGroup>("MOVEMENT", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { g, _ ->
        cellText(amountOrDash(g.movement), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetGroup>("CLOSING", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { g, _ ->
        cellText(amountOrDash(g.closing), align = TextAlign.End)
    },
)

/** Group name in bold with the web's "N items" badge beneath; taps open the breakdown. */
@Composable
private fun GroupNameCell(group: BalanceSheetGroup, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = itemCountLabel(group.items.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.muted(),
        )
    }
}

private fun itemCountLabel(count: Int): String =
    "$count item" + if (count == 1) "" else "s"

@Composable
private fun SectionBlock(
    section: BalanceSheetSection,
    onGroupClick: (BalanceSheetGroup) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section title
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )
        ReportTable(
            columns = balanceSheetColumns(onGroupClick),
            data = section.groups,
            footerRows = listOf(
                listOf(
                    ReportFooterCell(cellText("Total ${section.title}", bold = true)),
                    ReportFooterCell(cellText(amountOrDash(section.opening), align = TextAlign.End, bold = true)),
                    ReportFooterCell(cellText(amountOrDash(section.movement), align = TextAlign.End, bold = true)),
                    ReportFooterCell(cellText(amountOrDash(section.closing), align = TextAlign.End, bold = true)),
                ),
            ),
            // Embedded in the screen's outer vertical scroll.
            scrollable = false,
        )
    }
}

/** The web's bottom "Final Position" card: Liabilities + Equity, plus a difference warning. */
@Composable
private fun FinalPositionCard(report: BalanceSheetReport, branchName: String?, range: String?) {
    val liabAndEquity = report.summary.firstOrNull { it.label == "Liabilities + Equity" }?.value ?: 0.0
    val difference = report.summary.firstOrNull { it.label == "Difference" }?.value ?: 0.0

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Final Position",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    val caption = listOfNotNull(branchName, range).joinToString("  •  ")
                    if (caption.isNotBlank()) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Liabilities + Equity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatSigned(liabAndEquity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = AppFontWeight.Bold,
                    )
                }
            }
            if (kotlin.math.abs(difference) > 0.009) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Difference detected: ${formatSigned(difference)}. " +
                        "Please review opening, movement, or group mapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// PARTICULAR | OPENING | MOVEMENT | CLOSING for the breakdown's items.
private val groupItemColumns = listOf(
    ReportColumn<BalanceSheetItem>("PARTICULAR", ReportColWidth.Fixed(150.dp)) { item, _ ->
        cellText(item.description.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn<BalanceSheetItem>("OPENING", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { item, _ ->
        cellText(amountOrDash(item.opening), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetItem>("MOVEMENT", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { item, _ ->
        cellText(amountOrDash(item.movement), align = TextAlign.End)
    },
    ReportColumn<BalanceSheetItem>("CLOSING", ReportColWidth.Fixed(BS_COL_AMOUNT), TextAlign.End) { item, _ ->
        cellText(amountOrDash(item.closing), align = TextAlign.End, bold = true)
    },
)

/** The web's GroupDetailsModal: stats for the tapped group, then its items. */
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
                StatTile("Items", group.items.size.toString())
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

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Zero renders as "-" in table cells, per the app-wide report convention. */
private fun amountOrDash(value: Double): String = AmountFormat.formatOrDash(value)

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
