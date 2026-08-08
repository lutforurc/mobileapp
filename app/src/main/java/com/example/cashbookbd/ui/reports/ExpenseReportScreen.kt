package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ExpenseReport
import com.example.cashbookbd.data.repository.ExpenseReportRepository
import com.example.cashbookbd.data.repository.ExpenseReportRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Two-tier header: Sl | (Group) | Description | Opening Dr/Cr | Movement | Closing.
private val COL_SL = 44.dp
private val COL_GROUP = 150.dp
private val COL_DESCRIPTION = 180.dp
private val COL_AMOUNT = 92.dp

/** The web's two readings of the same figures. */
private val VIEW_MODES = listOf(
    SelectorOption("group", "Group Report"),
    SelectorOption("details", "Details Report"),
)

data class ExpenseReportUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val viewMode: SelectorOption = VIEW_MODES.first(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val report: ExpenseReport? = null,
    /** The expense head opened inline on the group view (0 = none). */
    val expandedGroupId: Long = 0,

    val sessionExpired: Boolean = false,
)

class ExpenseReportViewModel(
    private val repository: ExpenseReportRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseReportUiState())
    val uiState: StateFlow<ExpenseReportUiState> = _uiState.asStateFlow()

    private var dateDefaulted = false

    init {
        seedDates()
        loadBranches()
    }

    /** The web opens on Jan 1 of the business-date year → the business date. */
    private fun seedDates() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
            _uiState.update {
                it.copy(startDate = trDate.copy(month = 1, day = 1), endDate = trDate)
            }
        }
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    val trDate = result.data.transactionDate
                    val applyDate = !dateDefaulted && trDate != null
                    if (applyDate) dateDefaulted = true
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = state.selectedBranch ?: result.data.branches.firstOrNull(),
                        startDate = if (applyDate) trDate!!.copy(month = 1, day = 1) else state.startDate,
                        endDate = if (applyDate) trDate!! else state.endDate,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(option: SelectorOption) {
        val branch = _uiState.value.branches.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }
    fun onViewModeSelected(option: SelectorOption) = _uiState.update { it.copy(viewMode = option) }

    /** Tapping a head opens its accounts inline; tapping again closes them. */
    fun toggleGroup(coa3Id: Long) = _uiState.update {
        it.copy(expandedGroupId = if (it.expandedGroupId == coa3Id) 0 else coa3Id)
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, expandedGroupId = 0) }
        viewModelScope.launch {
            val result = repository.fetch(
                branchId = branch.id.toString(),
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ExpenseReportViewModel(
                    repository = ServiceLocator.provideExpenseReportRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}

/**
 * The web's Expense Report: the Trial Balance Group layout narrowed to expense
 * heads. The group view opens a head's level-4 accounts inline on tap; the
 * details view lists every account of every head at once with a Group column.
 * Both are cut from what one Apply fetched, so they can never disagree.
 */
@Composable
fun ExpenseReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseReportViewModel = viewModel(
        factory = ExpenseReportViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Expense Report",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = viewModel::onBranchSelected,
                    placeholder = if (state.isBranchesLoading) "Loading…" else "",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppSelectDropdown(
                    label = "View",
                    options = VIEW_MODES,
                    selected = state.viewMode,
                    onSelected = viewModel::onViewModeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickExpenseDate(context, state.startDate, viewModel::onStartDate) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickExpenseDate(context, state.endDate, viewModel::onEndDate) },
                    )
                }
                PrimaryButton(
                    text = "Apply",
                    onClick = viewModel::apply,
                    isLoading = state.isLoading,
                    enabled = state.selectedBranch != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::apply)
                }

                state.isLoading && state.report == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !state.hasApplied -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Choose your filters, then tap Apply.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                state.report != null -> {
                    if (state.viewMode.id == "details") {
                        ExpenseDetailsTable(report = state.report!!)
                    } else {
                        ExpenseGroupTable(
                            report = state.report!!,
                            expandedGroupId = state.expandedGroupId,
                            onToggle = viewModel::toggleGroup,
                        )
                    }
                }
            }
        }
    }
}

/** A row of the group view: an expense head, or one of its accounts inline. */
private sealed interface ExpRow {
    data class Group(val row: ExpenseReportRow, val sl: Int) : ExpRow
    data class Detail(val row: ExpenseReportRow) : ExpRow
}

@Composable
private fun ExpenseGroupTable(
    report: ExpenseReport,
    expandedGroupId: Long,
    onToggle: (Long) -> Unit,
) {
    val rows = buildList {
        report.groups.forEachIndexed { index, group ->
            add(ExpRow.Group(group, index + 1))
            if (group.id == expandedGroupId) {
                report.details.filter { it.coa3Id == group.id }
                    .forEach { add(ExpRow.Detail(it)) }
            }
        }
    }

    val columns = buildList<ReportColumn<ExpRow>> {
        add(
            ReportColumn(
                header = "Sl. No",
                width = ReportColWidth.Fixed(COL_SL),
                align = TextAlign.Center,
                render = { row, _ ->
                    when (row) {
                        is ExpRow.Group -> cellText(row.sl.toString(), bold = true)
                        is ExpRow.Detail -> ReportTableCell.Empty
                    }
                },
            )
        )
        add(
            ReportColumn(
                header = "Description",
                width = ReportColWidth.Fixed(COL_DESCRIPTION),
                render = { row, _ ->
                    when (row) {
                        is ExpRow.Group -> ReportTableCell.Slot {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(row.row.id) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = row.row.groupName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = AppFontWeight.Bold,
                                )
                            }
                        }
                        is ExpRow.Detail -> cellText(row.row.accountName, startPadding = 12.dp, maxLines = 2)
                    }
                },
            )
        )
        expenseAmountColumns<ExpRow> { row ->
            when (row) {
                is ExpRow.Group -> row.row
                is ExpRow.Detail -> row.row
            }
        }.forEach { add(it) }
    }

    val headerGroups = listOf(
        ReportHeaderGroup("", 1),
        ReportHeaderGroup("", 1),
        ReportHeaderGroup("Opening", 2),
        ReportHeaderGroup("Movement", 2),
        ReportHeaderGroup("Closing", 2),
    )

    val detailBg = MaterialTheme.colorScheme.surfaceVariant
    ReportTable(
        columns = columns,
        data = rows,
        headerGroups = headerGroups,
        footerRows = listOf(grandTotalFooter(report.groups, leadingSpan = 2)),
        rowBackground = { row, _ -> if (row is ExpRow.Group) null else detailBg },
    )
}

@Composable
private fun ExpenseDetailsTable(report: ExpenseReport) {
    // Ordered by group so a head's accounts stay together, like the web.
    val groupOrder = report.groups.mapIndexed { i, g -> g.id to i }.toMap()
    val rows = report.details.sortedBy { groupOrder[it.coa3Id] ?: Int.MAX_VALUE }

    val columns = buildList<ReportColumn<ExpenseReportRow>> {
        add(
            ReportColumn(
                header = "Sl. No",
                width = ReportColWidth.Fixed(COL_SL),
                align = TextAlign.Center,
                render = { _, index -> cellText((index + 1).toString()) },
            )
        )
        add(
            ReportColumn(
                header = "Group",
                width = ReportColWidth.Fixed(COL_GROUP),
                render = { row, _ -> cellText(row.groupName, maxLines = 2) },
            )
        )
        add(
            ReportColumn(
                header = "Description",
                width = ReportColWidth.Fixed(COL_DESCRIPTION),
                render = { row, _ -> cellText(row.accountName, maxLines = 2) },
            )
        )
        expenseAmountColumns<ExpenseReportRow> { it }.forEach { add(it) }
    }

    val headerGroups = listOf(
        ReportHeaderGroup("", 1),
        ReportHeaderGroup("", 1),
        ReportHeaderGroup("", 1),
        ReportHeaderGroup("Opening", 2),
        ReportHeaderGroup("Movement", 2),
        ReportHeaderGroup("Closing", 2),
    )

    ReportTable(
        columns = columns,
        data = rows,
        headerGroups = headerGroups,
        footerRows = listOf(grandTotalFooter(report.details, leadingSpan = 3)),
    )
}

/** The six Dr/Cr columns shared by both views. */
private fun <T> expenseAmountColumns(figure: (T) -> ExpenseReportRow): List<ReportColumn<T>> {
    fun col(header: String, value: (ExpenseReportRow) -> Double) = ReportColumn<T>(
        header = header,
        width = ReportColWidth.Fixed(COL_AMOUNT),
        align = TextAlign.End,
        render = { row, _ -> cellText(AmountFormat.formatOrDash(value(figure(row)))) },
    )
    return listOf(
        col("Dr") { it.openingDebit },
        col("Cr") { it.openingCredit },
        col("Dr") { it.movementDebit },
        col("Cr") { it.movementCredit },
        col("Dr") { it.closingDebit },
        col("Cr") { it.closingCredit },
    )
}

/** The web tfoot: "Grand Total" over the leading columns, then the six sums. */
private fun grandTotalFooter(
    rows: List<ExpenseReportRow>,
    leadingSpan: Int,
): List<ReportFooterCell> = buildList {
    add(
        ReportFooterCell(
            cellText("Grand Total", align = TextAlign.End, bold = true),
            colSpan = leadingSpan,
        )
    )
    listOf<(ExpenseReportRow) -> Double>(
        { it.openingDebit }, { it.openingCredit },
        { it.movementDebit }, { it.movementCredit },
        { it.closingDebit }, { it.closingCredit },
    ).forEach { value ->
        add(
            ReportFooterCell(
                cellText(
                    AmountFormat.formatOrDash(rows.sumOf(value)),
                    align = TextAlign.End,
                    bold = true,
                )
            )
        )
    }
}

private fun pickExpenseDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
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
