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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.cashbookbd.report.ReportCell
import com.example.cashbookbd.report.ReportRow
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * Generic filter → result screen for any report the [com.example.cashbookbd.report.ReportMenu]
 * marks as generic-supported. Renders a branch + date-range filter and shows the
 * parsed rows as label/value cards, which tolerate the backend's varied shapes.
 */
@Composable
fun GenericReportScreen(
    reportKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenericReportViewModel = viewModel(
        factory = GenericReportViewModel.provideFactory(LocalContext.current, reportKey)
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
        title = uiState.title,
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!uiState.isSupported) {
            CenterBox {
                Text(
                    text = "This report isn't available in the mobile app yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                onLedgerSelected = viewModel::onLedgerSelected,
                searchLedgers = viewModel::searchLedgers,
                onChoiceSelected = viewModel::onChoiceSelected,
                onSelectorSelected = viewModel::onSelectorSelected,
                searchSelector = viewModel::searchSelector,
                onMonthYear = viewModel::onMonthYearSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
            )
            Box(modifier = Modifier.weight(1f)) {
                ReportResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

@Composable
private fun FilterCard(
    state: GenericReportUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onLedgerSelected: (LedgerDropdownItem) -> Unit,
    searchLedgers: suspend (String) -> Resource<List<LedgerDropdownItem>>,
    onChoiceSelected: (com.example.cashbookbd.report.ReportChoice) -> Unit,
    onSelectorSelected: (String, SelectorOption) -> Unit,
    searchSelector: suspend (ReportSelectorSource, String) -> Resource<List<SelectorOption>>,
    onMonthYear: (MonthYear) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
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

        if (state.showChoice) {
            Spacer(Modifier.height(12.dp))
            ChoiceDropdown(
                label = state.choiceLabel,
                options = state.choiceOptions,
                selected = state.selectedChoice,
                onSelected = onChoiceSelected,
            )
        }

        state.selectors.forEach { field ->
            Spacer(Modifier.height(12.dp))
            if (field.config.source.searchable) {
                SearchableSelectDropdown(
                    selected = field.selected,
                    onSelected = { onSelectorSelected(field.config.paramKey, it) },
                    search = { query -> searchSelector(field.config.source, query) },
                    label = field.config.label,
                )
            } else {
                StaticSelectDropdown(
                    label = field.config.label,
                    options = field.options,
                    selected = field.selected,
                    isLoading = field.isLoading,
                    error = field.error,
                    onSelected = { onSelectorSelected(field.config.paramKey, it) },
                )
            }
        }

        if (state.showLedger) {
            Spacer(Modifier.height(12.dp))
            SearchableLedgerDropdown(
                selectedLedger = state.selectedLedger,
                onLedgerSelected = onLedgerSelected,
                searchLedgers = searchLedgers,
                label = if (state.ledgerRequired) "Select Ledger" else "Select Ledger (optional)",
            )
        }

        if (state.showMonthYear) {
            Spacer(Modifier.height(12.dp))
            MonthYearField(value = state.monthYear, onSelected = onMonthYear)
        }

        if (state.showStartDate || state.showEndDate) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.showStartDate) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker(context, state.startDate, onStartDate) },
                    )
                }
                if (state.showEndDate) {
                    PickerField(
                        label = "End Date",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker(context, state.endDate, onEndDate) },
                    )
                }
            }
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
                    modifier = Modifier.size(22.dp),
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
private fun ChoiceDropdown(
    label: String,
    options: List<com.example.cashbookbd.report.ReportChoice>,
    selected: com.example.cashbookbd.report.ReportChoice?,
    onSelected: (com.example.cashbookbd.report.ReportChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PickerField(
            label = label,
            value = selected?.label ?: "",
            placeholder = label,
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (options.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A load-once remote dropdown (category, brand, somity). */
@Composable
private fun StaticSelectDropdown(
    label: String,
    options: List<SelectorOption>,
    selected: SelectorOption?,
    isLoading: Boolean,
    error: String?,
    onSelected: (SelectorOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Box {
            PickerField(
                label = label,
                value = selected?.label ?: if (isLoading) "Loading…" else "",
                placeholder = if (isLoading) "Loading…" else "Select",
                trailingIcon = Icons.Filled.ArrowDropDown,
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (options.isNotEmpty()) expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Field + dialog for a month/year selection (Collection Sheet). */
@Composable
private fun MonthYearField(value: MonthYear, onSelected: (MonthYear) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    PickerField(
        label = "Month / Year",
        value = value.toDisplay(),
        trailingIcon = Icons.Filled.DateRange,
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true },
    )

    if (showDialog) {
        MonthYearPickerDialog(
            initial = value,
            onDismiss = { showDialog = false },
            onConfirm = {
                onSelected(it)
                showDialog = false
            },
        )
    }
}

private val MONTH_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

@Composable
private fun MonthYearPickerDialog(
    initial: MonthYear,
    onDismiss: () -> Unit,
    onConfirm: (MonthYear) -> Unit,
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.month) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(MonthYear(year = year, month = month)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Select Month & Year") },
        text = {
            Column {
                // Year stepper.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
                    }
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 12-month grid, 3 per row.
                for (rowStart in 0 until 12 step 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (offset in 0 until 3) {
                            val m = rowStart + offset + 1
                            val isSelected = m == month
                            Box(modifier = Modifier.weight(1f)) {
                                if (isSelected) {
                                    Button(
                                        onClick = { month = m },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(MONTH_LABELS[m - 1]) }
                                } else {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { month = m },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(MONTH_LABELS[m - 1]) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    )
}

@Composable
private fun ReportResults(state: GenericReportUiState, onRetry: () -> Unit) {
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

        state.result == null -> CenterBox {
            Text(
                text = "Choose your filters, then tap Apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No records found for this selection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportRowList(state)
    }
}

@Composable
private fun ReportRowList(state: GenericReportUiState) {
    val result = state.result ?: return
    val table = remember(result.rows) { buildTable(result.rows) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (result.summary.isNotEmpty()) {
            SummaryBoxes(result.summary)
        }
        if (table.columns.isEmpty()) {
            CenterBox {
                Text(
                    text = "No tabular data to display.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            GenericReportTable(table)
        }
    }
}

@Composable
private fun SummaryBoxes(cells: List<ReportCell>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cells.forEach { cell ->
            Column(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Renders the report through the shared [ReportTable]. Columns are derived from
 * the API row keys (numeric → right-aligned, narrower; text → left-aligned), with
 * a leading "Sl. No." column the table numbers itself.
 */
@Composable
private fun GenericReportTable(table: TableModel) {
    val columns = remember(table) { buildGenericColumns(table) }
    ReportTable(columns = columns, data = table.rows)
}

private val COL_SL = 48.dp

private fun buildGenericColumns(table: TableModel): List<ReportColumn<List<String>>> = buildList {
    add(
        ReportColumn("Sl. No.", ReportColWidth.Fixed(COL_SL)) { _, index ->
            cellText((index + 1).toString())
        },
    )
    table.columns.forEachIndexed { ci, label ->
        val numeric = table.numeric[ci]
        val align = if (numeric) TextAlign.End else TextAlign.Start
        add(
            ReportColumn(
                header = label,
                width = ReportColWidth.Fixed(if (numeric) 112.dp else 172.dp),
                align = align,
            ) { row, _ -> cellText(row.getOrNull(ci).orEmpty(), align = align, maxLines = 2) },
        )
    }
}

/** True for an API column that is just a serial/SL number (hidden — the table adds its own). */
private fun isSerialColumn(label: String): Boolean {
    val normalized = label.lowercase().replace(".", "").replace(Regex("\\s+"), " ").trim()
    return normalized in setOf(
        "sl", "slno", "sl no", "sl number", "serial", "serial no", "serial number",
    )
}

/** Column model derived once from the parsed rows. */
private data class TableModel(
    val columns: List<String>,
    val numeric: List<Boolean>,
    val rows: List<List<String>>,
)

/**
 * Builds a table from the parsed rows: columns are the union of cell labels (in
 * first-seen order); a column is numeric when every non-blank value parses as a
 * number (so amount columns right-align but dates/text don't).
 */
private fun buildTable(rows: List<ReportRow>): TableModel {
    val columnOrder = LinkedHashSet<String>()
    rows.forEach { row -> row.cells.forEach { columnOrder.add(it.label) } }
    // Drop any serial/SL column the API sends — the table renders its own "Sl. No.".
    val columns = columnOrder.filterNot { isSerialColumn(it) }

    val matrix = rows.map { row ->
        val byLabel = row.cells.associate { it.label to it.value }
        columns.map { byLabel[it].orEmpty() }
    }

    val numeric = columns.indices.map { col ->
        val values = matrix.mapNotNull { it[col].takeIf { v -> v.isNotBlank() } }
        values.isNotEmpty() && values.all { it.replace(",", "").toDoubleOrNull() != null }
    }

    return TableModel(columns = columns, numeric = numeric, rows = matrix)
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
    current: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        current.year,
        current.month - 1, // DatePicker months are 0-based.
        current.day,
    ).show()
}
