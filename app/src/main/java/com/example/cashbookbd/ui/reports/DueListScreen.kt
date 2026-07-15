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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.DueRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import java.text.DecimalFormat

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
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onApply,
                enabled = state.canApply,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                if (state.isReportLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Apply")
                }
            }
            OutlinedButton(
                onClick = onReset,
                enabled = !state.isReportLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Reset")
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

        state.report == null -> CenterBox {
            Text(
                text = "Choose a branch and end date, then tap Apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No dues found for this selection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private val COL_AREA = 92.dp
private val COL_NUM = 104.dp

@Composable
private fun DueTable(report: com.example.cashbookbd.ui.reports.model.DueListReport) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScroll),
    ) {
        // Header
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("SL. NO", COL_SL, TextAlign.Start)
            GridVDivider(onHeader = true)
            HeaderCell("CUSTOMER/SUPPLIER", COL_PARTY, TextAlign.Start)
            GridVDivider(onHeader = true)
            HeaderCell("PAGE", COL_PAGE, TextAlign.Start)
            GridVDivider(onHeader = true)
            HeaderCell("AREA CODE", COL_AREA, TextAlign.Start)
            GridVDivider(onHeader = true)
            HeaderCell("DEBIT", COL_NUM, TextAlign.End)
            GridVDivider(onHeader = true)
            HeaderCell("CREDIT", COL_NUM, TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            report.rows.forEachIndexed { index, row ->
                PartyRow(serial = index + 1, row = row)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Total / Balance summary rows, mirroring the backend's summary lines.
            SummaryRow(label = "Total", debit = report.totalDebit, credit = report.totalCredit)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            SummaryRow(
                label = "Balance",
                // Net balance shows on the side it falls: receivable → debit, advance → credit.
                debit = if (report.netBalance >= 0) report.netBalance else 0.0,
                credit = if (report.netBalance < 0) -report.netBalance else 0.0,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One party line: SL, a stacked name/phone/address block, page, area code, and amounts. */
@Composable
private fun PartyRow(serial: Int, row: DueRow) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyCell(serial.toString(), COL_SL, TextAlign.Start)
        GridVDivider()

        Column(
            modifier = Modifier
                .width(COL_PARTY)
                .padding(horizontal = 8.dp, vertical = 10.dp),
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

        GridVDivider()
        BodyCell(row.page ?: "-", COL_PAGE, TextAlign.Start, maxLines = 2)
        GridVDivider()
        BodyCell(row.areaCode ?: "-", COL_AREA, TextAlign.Start)
        GridVDivider()
        BodyCell(formatCell(row.debit), COL_NUM, TextAlign.End)
        GridVDivider()
        BodyCell(formatCell(row.credit), COL_NUM, TextAlign.End)
    }
}

/** A bold Total / Balance footer row with the label under the party column. */
@Composable
private fun SummaryRow(label: String, debit: Double, credit: Double) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyCell("", COL_SL, TextAlign.Start)
        GridVDivider()
        BodyCell(label, COL_PARTY, TextAlign.Start, bold = true, color = MaterialTheme.colorScheme.onSurface)
        GridVDivider()
        BodyCell("", COL_PAGE, TextAlign.Start)
        GridVDivider()
        BodyCell("-", COL_AREA, TextAlign.Start)
        GridVDivider()
        BodyCell(formatCell(debit), COL_NUM, TextAlign.End, bold = true)
        GridVDivider()
        BodyCell(formatCell(credit), COL_NUM, TextAlign.End, bold = true)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        maxLines = 2,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    align: TextAlign,
    maxLines: Int = 1,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** Vertical grid line spanning the full height of a table row. */
@Composable
private fun GridVDivider(onHeader: Boolean = false) {
    VerticalDivider(
        color = if (onHeader) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )
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

private val amountFormat = DecimalFormat("#,##0.##")

/** Blank out zeros so the numeric columns stay readable. */
private fun formatCell(value: Double): String = if (value == 0.0) "-" else amountFormat.format(value)

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
