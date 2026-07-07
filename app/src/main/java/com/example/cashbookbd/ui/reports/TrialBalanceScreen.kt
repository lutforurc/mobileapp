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
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.reports.model.TrialBalanceReport
import com.example.cashbookbd.ui.reports.model.TrialBalanceRow
import java.text.DecimalFormat

/**
 * Trial Balance Details (Level 4): a filterable, horizontally-scrollable table
 * report that preserves the web layout — filter area, closing summary boxes, and
 * an 8-column table (SL, Description, Opening/Movement/Closing Dr & Cr).
 */
@Composable
fun TrialBalanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrialBalanceViewModel = viewModel(
        factory = TrialBalanceViewModel.provideFactory(LocalContext.current)
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
        title = "Trial Balance Details",
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
    state: TrialBalanceUiState,
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
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
private fun Results(state: TrialBalanceUiState, onRetry: () -> Unit) {
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
                text = "Choose a branch and date range, then tap Apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    report: TrialBalanceReport,
    branchName: String?,
    range: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary boxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryBox(
                label = "Closing Debit",
                value = formatAmount(report.closingDebit),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryBox(
                label = "Closing Credit",
                value = formatAmount(report.closingCredit),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            SummaryBox(
                label = "Difference",
                value = formatAmount(report.difference),
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }

        // Section title + subtitle
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Trial Balance Rows",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = listOfNotNull(branchName, range).joinToString("  •  ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (report.isEmpty) {
            CenterBox {
                Text(
                    text = "No trial balance data for this selection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            TrialBalanceTable(rows = report.rows)
        }
    }
}

@Composable
private fun SummaryBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private val COL_SL = 44.dp
private val COL_DESC = 190.dp
private val COL_NUM = 104.dp

@Composable
private fun TrialBalanceTable(rows: List<TrialBalanceRow>) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

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
            rows.forEachIndexed { index, row ->
                TableRow(index = index + 1, row = row)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
    ) {
        HeaderCell("SL. NO", COL_SL, TextAlign.Start)
        HeaderCell("DESCRIPTION", COL_DESC, TextAlign.Start)
        HeaderCell("OPENING DR", COL_NUM, TextAlign.End)
        HeaderCell("OPENING CR", COL_NUM, TextAlign.End)
        HeaderCell("MOVEMENT DR", COL_NUM, TextAlign.End)
        HeaderCell("MOVEMENT CR", COL_NUM, TextAlign.End)
        HeaderCell("CLOSING DR", COL_NUM, TextAlign.End)
        HeaderCell("CLOSING CR", COL_NUM, TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TableRow(index: Int, row: TrialBalanceRow) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyCell(index.toString(), COL_SL, TextAlign.Start)
        BodyCell(row.description, COL_DESC, TextAlign.Start, maxLines = 2)
        BodyCell(formatCell(row.openingDebit), COL_NUM, TextAlign.End)
        BodyCell(formatCell(row.openingCredit), COL_NUM, TextAlign.End)
        BodyCell(formatCell(row.movementDebit), COL_NUM, TextAlign.End)
        BodyCell(formatCell(row.movementCredit), COL_NUM, TextAlign.End)
        BodyCell(formatCell(row.closingDebit), COL_NUM, TextAlign.End)
        BodyCell(formatCell(row.closingCredit), COL_NUM, TextAlign.End)
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
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
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

/** Blank out zeros so the numeric columns stay readable, like the web report. */
private fun formatCell(value: Double): String =
    if (value == 0.0) "-" else amountFormat.format(value)

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
