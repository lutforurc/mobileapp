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
import com.example.cashbookbd.data.repository.ConnectedMemberArea
import com.example.cashbookbd.data.repository.ConnectedMemberGroup
import com.example.cashbookbd.data.repository.ConnectedMemberRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
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
import java.util.Locale

// The web's ten columns, sized for a phone's horizontal scroll.
private val COL_SL = 48.dp
private val COL_AREA = 180.dp
private val COL_PAY_MEMBER = 120.dp
private val COL_GT_MEMBER = 90.dp
private val COL_COLLECTION = 100.dp
private val COL_DP = 95.dp
private val COL_DP_COLL = 105.dp
private val COL_SALARY = 95.dp
private val COL_OUTSTANDING = 105.dp
private val COL_OVERVIEW = 90.dp

/** The group-summary figures, the web's summarizeRows verbatim. */
private data class CmSummary(
    val connectedMember: Double,
    val gtMember: Double,
    val collection: Double,
    val downpayment: Double,
    val outstanding: Double,
) {
    val collectionWithDp: Double get() = collection + downpayment
    /** The web's fixed 5% of DP + Coll. */
    val salary: Double get() = collectionWithDp * 0.05
    val payMemberPct: String get() = pct(connectedMember, gtMember)
    val overview: String get() = pct(collectionWithDp, outstanding)
}

private fun summarize(areas: List<ConnectedMemberArea>): CmSummary = CmSummary(
    connectedMember = areas.sumOf { it.connectedMember },
    gtMember = areas.sumOf { it.gtMember },
    collection = areas.sumOf { it.collection },
    downpayment = areas.sumOf { it.downpayment },
    outstanding = areas.sumOf { it.outstanding },
)

/** The web's percentage(): zero total → "0.00%". */
private fun pct(value: Double, total: Double): String =
    if (total == 0.0) "0.00%" else String.format(Locale.US, "%.2f%%", value / total * 100)

/** Member counts print raw (no separators, no dash rule), like the web. */
private fun count(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** thousandSeparator: grouped, zero as "-". */
private fun money(value: Double): String = AmountFormat.formatOrDash(value)

/** One rendered table row: an employee header or an expanded area line. */
private sealed interface CmRow {
    data class Group(val group: ConnectedMemberGroup, val expanded: Boolean) : CmRow
    data class Detail(val area: ConnectedMemberArea, val sl: Int) : CmRow
}

data class ConnectedMemberUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val groups: List<ConnectedMemberGroup> = emptyList(),
    /** Employee names whose area rows are open. */
    val expanded: Set<String> = emptySet(),

    val sessionExpired: Boolean = false,
)

class ConnectedMemberViewModel(
    private val repository: ConnectedMemberRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedMemberUiState())
    val uiState: StateFlow<ConnectedMemberUiState> = _uiState.asStateFlow()

    private var dateDefaulted = false
    private var defaultStart: SimpleDate? = null
    private var defaultEnd: SimpleDate? = null

    init {
        seedDates()
        loadBranches()
    }

    /** The web opens on the business date's month so far. */
    private fun seedDates() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
            defaultStart = trDate.copy(day = 1)
            defaultEnd = trDate
            _uiState.update { it.copy(startDate = trDate.copy(day = 1), endDate = trDate) }
        }
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    val trDate = result.data.transactionDate
                    val applyDate = !dateDefaulted && trDate != null
                    if (applyDate) {
                        dateDefaulted = true
                        defaultStart = trDate!!.copy(day = 1)
                        defaultEnd = trDate
                    }
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = state.selectedBranch ?: result.data.branches.firstOrNull(),
                        startDate = if (applyDate) trDate!!.copy(day = 1) else state.startDate,
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

    fun toggleGroup(employeeName: String) = _uiState.update {
        it.copy(
            expanded = if (employeeName in it.expanded) it.expanded - employeeName
            else it.expanded + employeeName,
        )
    }

    /** The web's Reset: default dates back, results and expansion cleared. */
    fun reset() = _uiState.update {
        it.copy(
            startDate = defaultStart ?: it.startDate,
            endDate = defaultEnd ?: it.endDate,
            groups = emptyList(),
            expanded = emptySet(),
            hasApplied = false,
            error = null,
        )
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetch(
                branchId = branch.id.toString(),
                startDate = state.startDate.toDisplay(),
                endDate = state.endDate.toDisplay(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasApplied = true,
                        groups = result.data,
                        expanded = emptySet(),
                    )
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
                ConnectedMemberViewModel(
                    repository = ServiceLocator.provideConnectedMemberRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}

/**
 * The web's Connected Member report: tap an employee row to open its areas;
 * the derived columns (Pay Member %, DP + Coll, the 5% Salary, Overview) are
 * computed exactly as the web's helpers do — including the web's own
 * inconsistency where detail-row Overview divides collection alone by
 * outstanding while the summary rows use collection + DP.
 */
@Composable
fun ConnectedMemberScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedMemberViewModel = viewModel(
        factory = ConnectedMemberViewModel.provideFactory(LocalContext.current),
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
        title = "Connected Member",
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
                    label = "Select Project",
                    options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = viewModel::onBranchSelected,
                    placeholder = if (state.isBranchesLoading) "Loading…" else "",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickCmDate(context, state.startDate, viewModel::onStartDate) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickCmDate(context, state.endDate, viewModel::onEndDate) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Apply",
                        onClick = viewModel::apply,
                        isLoading = state.isLoading,
                        enabled = state.selectedBranch != null,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Reset",
                        onClick = viewModel::reset,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
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

                state.isLoading && state.groups.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> ConnectedMemberTable(state = state, onToggle = viewModel::toggleGroup)
            }
        }
    }
}

@Composable
private fun ConnectedMemberTable(
    state: ConnectedMemberUiState,
    onToggle: (String) -> Unit,
) {
    // Flatten groups + open area rows into the display list, like the web.
    val rows = buildList {
        state.groups.forEach { group ->
            val expanded = group.employeeName in state.expanded
            add(CmRow.Group(group, expanded))
            if (expanded) {
                group.areas.forEachIndexed { i, area -> add(CmRow.Detail(area, i + 1)) }
            }
        }
    }

    val columns = listOf(
        ReportColumn<CmRow>(
            header = "Sl. No.",
            width = ReportColWidth.Fixed(COL_SL),
            align = TextAlign.Center,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> ReportTableCell.Slot {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(row.group.employeeName) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (row.expanded) "-" else "+",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = AppFontWeight.Bold,
                            )
                        }
                    }
                    is CmRow.Detail -> cellText(row.sl.toString())
                }
            },
        ),
        ReportColumn(
            header = "Area",
            width = ReportColWidth.Fixed(COL_AREA),
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> ReportTableCell.Slot {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(row.group.employeeName) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = row.group.employeeName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = AppFontWeight.Bold,
                            )
                        }
                    }
                    is CmRow.Detail -> ReportTableCell.Slot {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(
                                text = "${row.area.areaNameEng} (${row.area.areaCode})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (row.area.areaNameBng.isNotBlank()) {
                                Text(
                                    text = "${row.area.areaNameBng} (${row.area.areaCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
        ),
        ReportColumn(
            header = "Pay Member",
            width = ReportColWidth.Fixed(COL_PAY_MEMBER),
            align = TextAlign.Center,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> {
                        val s = summarize(row.group.areas)
                        cellText("${count(s.connectedMember)} (${s.payMemberPct})", bold = true)
                    }
                    is CmRow.Detail -> cellText(
                        "${count(row.area.connectedMember)} (${pct(row.area.connectedMember, row.area.gtMember)})"
                    )
                }
            },
        ),
        ReportColumn(
            header = "GT Member",
            width = ReportColWidth.Fixed(COL_GT_MEMBER),
            align = TextAlign.Center,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(count(summarize(row.group.areas).gtMember), bold = true)
                    is CmRow.Detail -> cellText(count(row.area.gtMember))
                }
            },
        ),
        ReportColumn(
            header = "Collection",
            width = ReportColWidth.Fixed(COL_COLLECTION),
            align = TextAlign.End,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(money(summarize(row.group.areas).collection), bold = true)
                    is CmRow.Detail -> cellText(money(row.area.collection))
                }
            },
        ),
        ReportColumn(
            header = "DP (Coll)",
            width = ReportColWidth.Fixed(COL_DP),
            align = TextAlign.End,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(money(summarize(row.group.areas).downpayment), bold = true)
                    is CmRow.Detail -> cellText(money(row.area.downpayment))
                }
            },
        ),
        ReportColumn(
            header = "DP + Coll",
            width = ReportColWidth.Fixed(COL_DP_COLL),
            align = TextAlign.End,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(money(summarize(row.group.areas).collectionWithDp), bold = true)
                    is CmRow.Detail -> cellText(money(row.area.collection + row.area.downpayment))
                }
            },
        ),
        ReportColumn(
            header = "Salary",
            width = ReportColWidth.Fixed(COL_SALARY),
            align = TextAlign.End,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(money(summarize(row.group.areas).salary), bold = true)
                    is CmRow.Detail -> cellText(money((row.area.collection + row.area.downpayment) * 0.05))
                }
            },
        ),
        ReportColumn(
            header = "Outstanding",
            width = ReportColWidth.Fixed(COL_OUTSTANDING),
            align = TextAlign.End,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(money(summarize(row.group.areas).outstanding), bold = true)
                    is CmRow.Detail -> cellText(money(row.area.outstanding))
                }
            },
        ),
        ReportColumn(
            header = "Overview",
            width = ReportColWidth.Fixed(COL_OVERVIEW),
            align = TextAlign.Center,
            render = { row, _ ->
                when (row) {
                    is CmRow.Group -> cellText(summarize(row.group.areas).overview, bold = true)
                    // The web's detail rows divide collection alone — kept as-is.
                    is CmRow.Detail -> cellText(pct(row.area.collection, row.area.outstanding))
                }
            },
        ),
    )

    // Grand Total over every area row, expanded or not, like the web's tfoot.
    val footerRows = if (state.groups.isEmpty()) emptyList() else {
        val s = summarize(state.groups.flatMap { it.areas })
        listOf(
            listOf(
                ReportFooterCell(cellText("Grand Total", align = TextAlign.End, bold = true), colSpan = 2),
                ReportFooterCell(cellText(count(s.connectedMember), bold = true)),
                ReportFooterCell(cellText(count(s.gtMember), bold = true)),
                ReportFooterCell(cellText(money(s.collection), bold = true)),
                ReportFooterCell(cellText(money(s.downpayment), bold = true)),
                ReportFooterCell(cellText(money(s.collectionWithDp), bold = true)),
                ReportFooterCell(cellText(money(s.salary), bold = true)),
                ReportFooterCell(cellText(money(s.outstanding), bold = true)),
                ReportFooterCell(cellText(s.overview, bold = true)),
            )
        )
    }

    val groupBg = MaterialTheme.colorScheme.secondaryContainer
    ReportTable(
        columns = columns,
        data = rows,
        footerRows = footerRows,
        rowBackground = { row, _ -> if (row is CmRow.Group) groupBg else null },
    )
}

private fun pickCmDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
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
