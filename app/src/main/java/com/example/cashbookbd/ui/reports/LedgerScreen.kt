package com.example.cashbookbd.ui.reports

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

        Button(
            onClick = onApply,
            enabled = state.canApply,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (state.isReportLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Apply")
            }
        }
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
@Composable
private fun PickerField(
    label: String,
    value: String,
    trailingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onClick: () -> Unit,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(trailingIcon, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
        )
    }
}

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
                Button(onClick = onRetry) { Text("Retry") }
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

// Column widths for the horizontally-scrollable table.
// Order: SL. NO | VR DATE | VR NO | DESCRIPTION | DEBIT | CREDIT
private val COL_SL = 56.dp
private val COL_DATE = 96.dp
private val COL_VR = 116.dp
private val COL_DESCRIPTION = 220.dp
private val COL_DEBIT = 120.dp
private val COL_CREDIT = 120.dp

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
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val displayRows = statement.toDisplayRows()

    // Header stays put; details + the summary footer scroll together, sharing
    // one horizontal scroll so every column stays aligned.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScroll),
    ) {
        TableHeader()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            displayRows.forEach { row -> TableRow(row) }
            SummaryFooter(statement)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Summary block appended after the details: Range Total, Total, Balance Receivable. */
@Composable
private fun SummaryFooter(statement: LedgerStatement) {
    val balance = statement.balance
    Column {
        HorizontalDivider(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outline,
        )
        SummaryRow(
            label = "Range Total",
            debit = amountOrDash(statement.rangeDebit),
            credit = amountOrDash(statement.rangeCredit),
        )
        SummaryRow(
            label = "Total",
            debit = amountOrDash(statement.totalDebit),
            credit = amountOrDash(statement.totalCredit),
        )
        SummaryRow(
            label = "Balance Receivable",
            // Net balance sits on its side: receivable => debit, payable => credit.
            debit = amountOrDash(if (balance > 0.0) balance else 0.0),
            credit = amountOrDash(if (balance < 0.0) -balance else 0.0),
        )
    }
}

@Composable
private fun SummaryRow(label: String, debit: String, credit: String) {
    Column {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 12.dp),
        ) {
            // Blank SL / VR DATE / VR NO; the label sits under DESCRIPTION.
            Spacer(Modifier.width(COL_SL))
            Spacer(Modifier.width(COL_DATE))
            Spacer(Modifier.width(COL_VR))
            BodyCell(label, COL_DESCRIPTION, fontWeight = FontWeight.Bold)
            BodyCell(debit, COL_DEBIT, TextAlign.End, FontWeight.Bold)
            BodyCell(credit, COL_CREDIT, TextAlign.End, FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 10.dp),
    ) {
        HeaderCell("SL. NO", COL_SL)
        HeaderCell("VR DATE", COL_DATE)
        HeaderCell("VR NO", COL_VR)
        HeaderCell("DESCRIPTION", COL_DESCRIPTION)
        HeaderCell("DEBIT", COL_DEBIT, TextAlign.End)
        HeaderCell("CREDIT", COL_CREDIT, TextAlign.End)
    }
}

@Composable
private fun TableRow(row: LedgerDisplayRow) {
    val bg = if (row.isSummary) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val weight = if (row.isSummary) FontWeight.Bold else FontWeight.Normal

    Column {
        Row(
            modifier = Modifier
                .background(bg)
                .padding(vertical = 10.dp),
        ) {
            BodyCell(row.sl, COL_SL, fontWeight = weight)
            BodyCell(row.date, COL_DATE, fontWeight = weight)
            BodyCell(row.voucherNo, COL_VR, fontWeight = weight)
            BodyCell(row.description, COL_DESCRIPTION, fontWeight = weight, maxLines = 3)
            BodyCell(row.debit, COL_DEBIT, TextAlign.End, weight)
            BodyCell(row.credit, COL_CREDIT, TextAlign.End, weight)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    align: TextAlign = TextAlign.Start,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
    )
}

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
