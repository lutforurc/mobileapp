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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.LedgerStatement
import com.example.cashbookbd.ui.reports.model.SimpleDate
import java.text.DecimalFormat

@Composable
fun LedgerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.provideFactory(LocalContext.current)
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
        title = "Ledger",
        currentRoute = Routes.LEDGER,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LedgerFilterForm(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                searchLedgers = viewModel::searchLedgers,
                onLedgerSelected = viewModel::onLedgerSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
            )

            Box(modifier = Modifier.weight(1f)) {
                LedgerResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

@Composable
private fun LedgerFilterForm(
    state: LedgerUiState,
    onBranchSelected: (BranchOption) -> Unit,
    searchLedgers: suspend (String) -> Resource<List<LedgerDropdownItem>>,
    onLedgerSelected: (LedgerDropdownItem) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
    ) {
        BranchDropdown(
            branches = state.branches,
            selected = state.selectedBranch,
            isLoading = state.isBranchesLoading,
            onSelected = onBranchSelected,
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        SearchableLedgerDropdown(
            selectedLedger = state.selectedLedger,
            onLedgerSelected = onLedgerSelected,
            searchLedgers = searchLedgers,
            modifier = Modifier.fillMaxWidth(),
        )

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

/** A read-only text field whose taps are forwarded to [onClick]. */
// ---------------------------------------------------------------------------
// Results (ledger statement from /reports/api-ledger)
// ---------------------------------------------------------------------------

@Composable
private fun LedgerResults(state: LedgerUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox { CircularProgressIndicator() }

        state.reportError != null -> CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.reportError,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.statement == null -> CenterBox {
            Text(
                text = "Choose a branch and ledger, pick a date range, then tap Apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> LedgerTable(statement = state.statement)
    }
}

// Columns for the horizontally-scrollable ledger table.
// Order: SL. NO | VR DATE | VR NO | DESCRIPTION | DEBIT | CREDIT
private val COL_SL = 56.dp
private val COL_DATE = 96.dp
private val COL_VR = 116.dp
private val COL_DESCRIPTION = 220.dp
private val COL_DEBIT = 120.dp
private val COL_CREDIT = 120.dp

private val ledgerColumns = listOf(
    ReportColumn<LedgerDisplayRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { r, _ ->
        cellText(r.sl, bold = r.isSummary, align = TextAlign.Center)
    },
    ReportColumn<LedgerDisplayRow>("VR DATE", ReportColWidth.Fixed(COL_DATE)) { r, _ ->
        cellText(r.date, bold = r.isSummary)
    },
    ReportColumn<LedgerDisplayRow>("VR NO", ReportColWidth.Fixed(COL_VR)) { r, _ ->
        cellText(r.voucherNo, bold = r.isSummary)
    },
    ReportColumn<LedgerDisplayRow>("DESCRIPTION", ReportColWidth.Fixed(COL_DESCRIPTION)) { r, _ ->
        cellText(r.description, bold = r.isSummary, maxLines = 3)
    },
    ReportColumn<LedgerDisplayRow>("DEBIT", ReportColWidth.Fixed(COL_DEBIT), TextAlign.End) { r, _ ->
        cellText(r.debit, align = TextAlign.End, bold = r.isSummary)
    },
    ReportColumn<LedgerDisplayRow>("CREDIT", ReportColWidth.Fixed(COL_CREDIT), TextAlign.End) { r, _ ->
        cellText(r.credit, align = TextAlign.End, bold = r.isSummary)
    },
)

/** One rendered line of the report (the opening-balance line, or a transaction). */
private data class LedgerDisplayRow(
    val sl: String,
    val date: String,
    val voucherNo: String,
    val description: String,
    val debit: String,
    val credit: String,
    val isSummary: Boolean,
)

/** Opening Balance line first, then the transaction rows numbered from 1. */
private fun LedgerStatement.toDisplayRows(): List<LedgerDisplayRow> {
    val list = ArrayList<LedgerDisplayRow>(rows.size + 1)
    list += LedgerDisplayRow(
        sl = "-",
        date = "",
        voucherNo = "",
        description = "Opening Balance",
        debit = amountOrDash(openingDebit),
        credit = amountOrDash(openingCredit),
        isSummary = true,
    )
    rows.forEachIndexed { index, r ->
        list += LedgerDisplayRow(
            sl = (index + 1).toString(),
            date = formatVrDate(r.date),
            voucherNo = r.voucherNo,
            description = r.description,
            debit = amountOrDash(r.debit),
            credit = amountOrDash(r.credit),
            isSummary = false,
        )
    }
    return list
}

@Composable
private fun LedgerTable(statement: LedgerStatement) {
    val summaryBg = MaterialTheme.colorScheme.secondaryContainer
    ReportTable(
        columns = ledgerColumns,
        data = statement.toDisplayRows(),
        footerRows = ledgerFooterRows(statement),
        // The Opening Balance line is styled like the summary rows.
        rowBackground = { row, _ -> if (row.isSummary) summaryBg else null },
    )
}

/** Range Total, Total, and the net Balance line — each label sits under DESCRIPTION. */
private fun ledgerFooterRows(statement: LedgerStatement): List<List<ReportFooterCell>> {
    val balance = statement.balance
    return listOf(
        ledgerFooterRow("Range Total", statement.rangeDebit, statement.rangeCredit),
        ledgerFooterRow("Total", statement.totalDebit, statement.totalCredit),
        ledgerFooterRow(
            "Balance Receivable",
            // Net balance sits on its side: receivable => debit, payable => credit.
            if (balance > 0.0) balance else 0.0,
            if (balance < 0.0) -balance else 0.0,
        ),
    )
}

private fun ledgerFooterRow(label: String, debit: Double, credit: Double): List<ReportFooterCell> =
    listOf(
        // Blank SL / VR DATE / VR NO under one span; the label sits under DESCRIPTION.
        ReportFooterCell(ReportTableCell.Empty, colSpan = 3),
        ReportFooterCell(cellText(label, bold = true)),
        ReportFooterCell(cellText(amountOrDash(debit), align = TextAlign.End, bold = true)),
        ReportFooterCell(cellText(amountOrDash(credit), align = TextAlign.End, bold = true)),
    )

private val amountFormat = DecimalFormat("#,##0.00")

/** Debit/Credit cells show "-" for zero/empty, else a comma-grouped amount. */
private fun amountOrDash(value: Double): String =
    if (value == 0.0) "-" else amountFormat.format(value)

/** The API sends `vr_date` as yyyy-MM-dd; the report shows dd/MM/yyyy. */
private fun formatVrDate(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw) ?: return raw
    val (year, month, day) = m.destructured
    return "$day/$month/$year"
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
