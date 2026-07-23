package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
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
import com.example.cashbookbd.ui.components.HighlightedText
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.highlightBorderColor
import com.example.cashbookbd.ui.components.rememberHighlightRules
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.HighlightRule
import com.example.cashbookbd.report.matchHighlightRule
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.CashBookRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.core.AmountFormat

@Composable
fun CashBookScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashBookViewModel = viewModel(
        factory = CashBookViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Expired/rejected token -> clear session and return to login.
    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Cash Book",
        currentRoute = Routes.CASHBOOK,
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
            )

            Box(modifier = Modifier.weight(1f)) {
                CashBookResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

@Composable
private fun FilterCard(
    state: CashBookUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

    // No card background — the form sits directly on the screen. Tight vertical
    // padding keeps the gap above the branch field and below the Apply button small.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
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

        Spacer(Modifier.height(16.dp))

        FilterActions(
            onApply = onApply,
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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

/**
 * A read-only text field for the filter form. It's not directly editable — a
 * transparent overlay forwards taps to [onClick] (dropdown / date picker).
 */
// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
private fun CashBookResults(state: CashBookUiState, onRetry: () -> Unit) {
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

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No transactions found for this period.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> CashBookTable(rows = state.report.rows)
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

// Columns for the horizontally-scrollable table.
// Order: Date | VR No | Description | Received (credit) | Payment (debit)
//
// [summaryColor] is the ink for the total/balance rows, which draw on a pale
// secondaryContainer band — the body's on-teal ink washes out there, so summary
// cells take the band's own on-colour (normal rows keep the backdrop default).
private fun cashBookColumns(rules: List<HighlightRule>, summaryColor: Color) = listOf(
    ReportColumn<CashBookRow>("Date", ReportColWidth.Fixed(96.dp)) { r, _ ->
        cellText(r.date, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<CashBookRow>("VR No", ReportColWidth.Fixed(100.dp)) { r, _ ->
        cellText(r.voucherNo, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<CashBookRow>("Description", ReportColWidth.Fixed(210.dp)) { r, _ ->
        if (r.remarks.isBlank()) {
            cellText(r.particulars, bold = r.isSummary, maxLines = 3, color = r.summaryInk(summaryColor))
        } else {
            ReportTableCell.Slot {
                CashBookDescriptionCell(row = r, rule = matchHighlightRule(r.remarks, rules))
            }
        }
    },
    ReportColumn<CashBookRow>("Received", ReportColWidth.Fixed(116.dp), TextAlign.End) { r, _ ->
        cellText(amountOrDash(r.credit), align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<CashBookRow>("Payment", ReportColWidth.Fixed(116.dp), TextAlign.End) { r, _ ->
        cellText(amountOrDash(r.debit), align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
)

/** Summary rows take the band's on-colour; normal rows keep the default ink. */
private fun CashBookRow.summaryInk(summaryColor: Color): Color =
    if (isSummary) summaryColor else Color.Unspecified

/**
 * Description plus the voucher's free-text remarks beneath it (as on the web
 * report), the remarks boxed in a highlight rule's colour when one matches.
 */
@Composable
private fun CashBookDescriptionCell(row: CashBookRow, rule: HighlightRule?) {
    // Normal rows draw on the screen backdrop (light on-teal ink); summary rows
    // draw on the pale secondaryContainer band and need its dark on-colour, or
    // the on-teal ink washes out. onSurfaceVariant is unreadable on the teal.
    val onScreen = if (row.isSummary) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (row.particulars.isNotBlank()) {
            Text(
                text = row.particulars,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (row.isSummary) FontWeight.Bold else FontWeight.Normal,
                color = onScreen,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        }
        HighlightedText(
            text = row.remarks,
            borderColor = highlightBorderColor(rule),
            color = onScreen.copy(alpha = 0.75f),
            maxLines = 3,
        )
    }
}

@Composable
private fun CashBookTable(rows: List<CashBookRow>) {
    val rules = rememberHighlightRules()
    val summaryBg = MaterialTheme.colorScheme.secondaryContainer
    val summaryInk = MaterialTheme.colorScheme.onSecondaryContainer
    val columns = remember(rules, summaryInk) { cashBookColumns(rules, summaryInk) }
    ReportTable(
        columns = columns,
        data = rows,
        rowBackground = { row, _ -> if (row.isSummary) summaryBg else null },
    )
}

/** Received/Payment cells read cleaner when zeros show as a dash. */
private fun amountOrDash(value: Double): String = AmountFormat.formatOrDash(value)

private fun showDatePicker(
    context: Context,
    initial: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            // DatePickerDialog months are 0-based; SimpleDate is 1-based.
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
