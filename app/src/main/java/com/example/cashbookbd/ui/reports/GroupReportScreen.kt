package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.cashbookbd.data.repository.GroupReport
import com.example.cashbookbd.data.repository.GroupReportRepository
import com.example.cashbookbd.data.repository.GroupReportRow
import com.example.cashbookbd.data.repository.GroupReportSection
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
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Description wide, then a fixed pair per month + the Line Total pair.
private val COL_DESCRIPTION = 190.dp
private val COL_PAIR = 92.dp

/** The web's Report Group select, "All group" merged client-side. */
private val GROUP_CHOICES = listOf(
    SelectorOption("1", "Operating Cost"),
    SelectorOption("2", "Purchase Cost"),
    SelectorOption("0", "All group"),
)

data class GroupReportUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val group: SelectorOption = GROUP_CHOICES.first(),
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val report: GroupReport? = null,

    val sessionExpired: Boolean = false,
)

class GroupReportViewModel(
    private val repository: GroupReportRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupReportUiState())
    val uiState: StateFlow<GroupReportUiState> = _uiState.asStateFlow()

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

    fun onGroupSelected(option: SelectorOption) = _uiState.update { it.copy(group = option) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetch(
                branchId = branch.id.toString(),
                reportGroup = state.group.id,
                // The server validates strict d/m/Y.
                startDate = state.startDate.toDisplay(),
                endDate = state.endDate.toDisplay(),
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
                GroupReportViewModel(
                    repository = ServiceLocator.provideGroupReportRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}

/**
 * The web's Group Report verbatim: one pivot table per section, months across
 * the top, a Payment|Bill (or Qty|Amount) pair per month, a Line Total pair,
 * and a bold Grand Total row. "All group" stacks both sections.
 */
@Composable
fun GroupReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupReportViewModel = viewModel(
        factory = GroupReportViewModel.provideFactory(LocalContext.current),
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
        title = "Group Report",
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
                    label = "Report Group",
                    options = GROUP_CHOICES,
                    selected = state.group,
                    onSelected = viewModel::onGroupSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickGroupDate(context, state.startDate, viewModel::onStartDate) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickGroupDate(context, state.endDate, viewModel::onEndDate) },
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

                state.isLoading && state.report == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.report != null -> GroupReportBody(report = state.report!!)
            }
        }
    }
}

@Composable
private fun GroupReportBody(report: GroupReport) {
    if (report.isEmpty) {
        Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No data found",
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        report.sections.forEach { section ->
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            GroupSectionTable(months = report.months, section = section)
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun GroupSectionTable(months: List<String>, section: GroupReportSection) {
    val leftHeader = if (section.isPurchase) "Qty" else "Payment"
    val rightHeader = if (section.isPurchase) "Amount" else "Bill"

    val columns = buildList<ReportColumn<GroupReportRow>> {
        add(
            ReportColumn(
                header = "Description",
                width = ReportColWidth.Fixed(COL_DESCRIPTION),
                render = { row, index ->
                    // Purchase Cost numbers its products "(n) name", like the web.
                    val label = if (section.isPurchase) "(${index + 1}) ${row.name}" else row.name
                    cellText(label, maxLines = 3)
                },
            )
        )
        months.forEach { month ->
            add(
                ReportColumn(
                    header = leftHeader,
                    width = ReportColWidth.Fixed(COL_PAIR),
                    align = TextAlign.End,
                    render = { row, _ -> cellText(amountText(row.byMonth[month]?.first ?: 0.0)) },
                )
            )
            add(
                ReportColumn(
                    header = rightHeader,
                    width = ReportColWidth.Fixed(COL_PAIR),
                    align = TextAlign.End,
                    render = { row, _ -> cellText(amountText(row.byMonth[month]?.second ?: 0.0)) },
                )
            )
        }
        add(
            ReportColumn(
                header = leftHeader,
                width = ReportColWidth.Fixed(COL_PAIR),
                align = TextAlign.End,
                render = { row, _ -> cellText(amountText(row.lineLeft), bold = true) },
            )
        )
        add(
            ReportColumn(
                header = rightHeader,
                width = ReportColWidth.Fixed(COL_PAIR),
                align = TextAlign.End,
                render = { row, _ -> cellText(amountText(row.lineRight), bold = true) },
            )
        )
    }

    val headerGroups = buildList {
        add(ReportHeaderGroup("", 1))
        months.forEach { add(ReportHeaderGroup(it, 2)) }
        add(ReportHeaderGroup("Line Total", 2))
    }

    // The bold Grand Total row: per-month column sums, then the overall pair.
    val footer = buildList {
        add(
            ReportFooterCell(
                cellText("Grand Total", align = TextAlign.End, bold = true),
            )
        )
        months.forEach { month ->
            add(
                ReportFooterCell(
                    cellText(
                        amountText(section.rows.sumOf { it.byMonth[month]?.first ?: 0.0 }),
                        align = TextAlign.End,
                        bold = true,
                    )
                )
            )
            add(
                ReportFooterCell(
                    cellText(
                        amountText(section.rows.sumOf { it.byMonth[month]?.second ?: 0.0 }),
                        align = TextAlign.End,
                        bold = true,
                    )
                )
            )
        }
        add(
            ReportFooterCell(
                cellText(amountText(section.rows.sumOf { it.lineLeft }), align = TextAlign.End, bold = true),
            )
        )
        add(
            ReportFooterCell(
                cellText(amountText(section.rows.sumOf { it.lineRight }), align = TextAlign.End, bold = true),
            )
        )
    }

    ReportTable(
        columns = columns,
        data = section.rows,
        footerRows = listOf(footer),
        headerGroups = headerGroups,
        scrollable = false,
    )
}

/** The web's amountText: positives with separators, zero *and* negatives "-". */
private fun amountText(value: Double): String =
    if (value > 0) AmountFormat.format(value) else "-"

private fun pickGroupDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
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
