package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
                        modifier = Modifier.height(20.dp),
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

@Composable
private fun PickerField(
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
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

        else -> ReportContent(
            report = state.report,
            branchName = state.appliedBranchName,
            endDate = state.appliedEndDate,
        )
    }
}

@Composable
private fun ReportContent(
    report: com.example.cashbookbd.ui.reports.model.DueListReport,
    branchName: String?,
    endDate: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val subtitle = listOfNotNull(branchName, endDate?.let { "As on $it" }, "${report.rows.size} party(ies)")
            .joinToString("  •  ")
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryBox("Total Debit", report.totalDebit, modifier = Modifier.weight(1f))
            SummaryBox("Total Credit", report.totalCredit, modifier = Modifier.weight(1f))
            SummaryBox("Net Balance", report.netBalance, modifier = Modifier.weight(1f), accent = true)
        }

        Box(modifier = Modifier.weight(1f)) {
            DueTable(rows = report.rows)
        }
    }
}

@Composable
private fun SummaryBox(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatAmount(value),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 44.dp
private val COL_NAME = 180.dp
private val COL_REF = 120.dp
private val COL_MOBILE = 110.dp
private val COL_NUM = 104.dp

@Composable
private fun DueTable(rows: List<DueRow>) {
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp),
        ) {
            HeaderCell("SL. NO", COL_SL, TextAlign.Start)
            HeaderCell("NAME", COL_NAME, TextAlign.Start)
            HeaderCell("PAGE / REF", COL_REF, TextAlign.Start)
            HeaderCell("MOBILE", COL_MOBILE, TextAlign.Start)
            HeaderCell("DEBIT", COL_NUM, TextAlign.End)
            HeaderCell("CREDIT", COL_NUM, TextAlign.End)
            HeaderCell("BALANCE", COL_NUM, TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyCell((index + 1).toString(), COL_SL, TextAlign.Start)
                    BodyCell(row.customer, COL_NAME, TextAlign.Start, maxLines = 2)
                    BodyCell(row.reference ?: "-", COL_REF, TextAlign.Start, maxLines = 2)
                    BodyCell(row.mobile ?: "-", COL_MOBILE, TextAlign.Start)
                    BodyCell(formatCell(row.debit), COL_NUM, TextAlign.End)
                    BodyCell(formatCell(row.credit), COL_NUM, TextAlign.End)
                    BodyCell(formatCell(row.balance), COL_NUM, TextAlign.End, bold = true)
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 2,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    align: TextAlign,
    maxLines: Int = 1,
    bold: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
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

private fun formatAmount(value: Double): String = amountFormat.format(value)

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
