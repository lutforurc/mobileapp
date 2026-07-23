package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BankBookRow
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * Bank Book — the bank-side twin of the Cash Book: same filters and layout,
 * plus the Transaction Bank column, because unlike the drawer there is more
 * than one bank the money could have moved through.
 */
@Composable
fun BankBookScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BankBookViewModel = viewModel(
        factory = BankBookViewModel.provideFactory(LocalContext.current)
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
        title = "Bank Book",
        currentRoute = Routes.BANKBOOK,
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
                BankBookResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------

@Composable
private fun FilterCard(
    state: BankBookUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

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
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
            )
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
private fun BankBookResults(state: BankBookUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }

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

        else -> BankBookTable(rows = state.report.rows)
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

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 56.dp
private val COL_DATE = 96.dp
private val COL_VR = 100.dp
private val COL_DESC = 210.dp
private val COL_BANK = 150.dp
private val COL_AMOUNT = 116.dp

// [summaryColor] inks the opening-balance and total rows, which draw on a pale
// secondaryContainer band where the body's on-teal ink washes out.
private fun bankBookColumns(summaryColor: Color) = listOf(
    ReportColumn<BankBookRow>("Sl. No.", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { r, _ ->
        // The opening row's serial is 0 and the summary rows have none; both
        // read as a dash rather than a meaningless number.
        cellText(r.serial.ifBlank { "-" }, align = TextAlign.Center, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<BankBookRow>("Vr Date", ReportColWidth.Fixed(COL_DATE)) { r, _ ->
        cellText(r.date.ifBlank { "-" }, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<BankBookRow>("Vr No", ReportColWidth.Fixed(COL_VR)) { r, _ ->
        cellText(r.voucherNo.ifBlank { "-" }, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<BankBookRow>("Description", ReportColWidth.Fixed(COL_DESC)) { r, _ ->
        ReportTableCell.Slot { DescriptionCell(row = r) }
    },
    ReportColumn<BankBookRow>("Transaction Bank", ReportColWidth.Fixed(COL_BANK)) { r, _ ->
        cellText(r.bank.ifBlank { "-" }, bold = r.isSummary, maxLines = 2, color = r.summaryInk(summaryColor))
    },
    ReportColumn<BankBookRow>("Received", ReportColWidth.Fixed(COL_AMOUNT), TextAlign.End) { r, _ ->
        cellText(amountOrDash(r.received), align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<BankBookRow>("Payment", ReportColWidth.Fixed(COL_AMOUNT), TextAlign.End) { r, _ ->
        cellText(amountOrDash(r.payment), align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
)

/** Summary rows take the band's on-colour; normal rows keep the default ink. */
private fun BankBookRow.summaryInk(summaryColor: Color): Color =
    if (isSummary) summaryColor else Color.Unspecified

/**
 * The name, plus the lines the backend packs around it: the second half of an
 * invoice's `nam`, the remarks, and the order number — each shown only when the
 * row actually carries it.
 */
@Composable
private fun DescriptionCell(row: BankBookRow) {
    // Normal rows draw on the teal backdrop, summary rows on the pale band; take
    // the matching on-colour so the title and the muted sub-lines both stay
    // readable. onSurfaceVariant is a card ink and washes out on the teal.
    val onScreen = if (row.isSummary) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = row.title.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (row.isSummary) FontWeight.Bold else FontWeight.Normal,
            color = onScreen,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.remarks.isNotBlank()) {
            Text(
                text = row.remarks,
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.orderNumber.isNotBlank()) {
            Text(
                text = row.orderNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BankBookTable(rows: List<BankBookRow>) {
    // The opening balance and the three appended totals are the server's own
    // rows — never recomputed here — and are tinted so they read apart from the
    // transactions between them.
    val summaryBg = MaterialTheme.colorScheme.secondaryContainer
    val summaryInk = MaterialTheme.colorScheme.onSecondaryContainer
    val columns = remember(summaryInk) { bankBookColumns(summaryInk) }
    ReportTable(
        columns = columns,
        data = rows,
        rowBackground = { row, _ -> if (row.isSummary) summaryBg else null },
        noDataMessage = "No transactions found for this period.",
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
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
