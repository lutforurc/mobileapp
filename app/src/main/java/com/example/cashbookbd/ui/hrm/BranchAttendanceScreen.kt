package com.example.cashbookbd.ui.hrm

import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.hrm.model.BranchAttendanceRow
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** The web's "All Branches" option (sent as a blank branch_id). */
private val ALL_BRANCHES = BranchOption(id = 0L, name = "All Branches")

data class BranchAttendanceUiState(
    val branches: List<BranchOption> = listOf(ALL_BRANCHES),
    val selectedBranch: BranchOption = ALL_BRANCHES,
    val isBranchesLoading: Boolean = false,

    val monthYear: MonthYear = MonthYear.current(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val rows: List<BranchAttendanceRow> = emptyList(),

    val sessionExpired: Boolean = false,
) {
    val canLoad: Boolean get() = !isLoading
}

class BranchAttendanceViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BranchAttendanceUiState())
    val uiState: StateFlow<BranchAttendanceUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = listOf(ALL_BRANCHES) + result.data.branches,
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

    fun onBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(selectedBranch = branch, loaded = false) }

    fun onMonthSelected(monthYear: MonthYear) =
        _uiState.update { it.copy(monthYear = monthYear, loaded = false) }

    fun load() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = hrmRepository.branchAttendanceRows(
                branchId = state.selectedBranch.id,
                month = state.monthYear.month,
                year = state.monthYear.year,
                branchNames = state.branches.associate { it.id.toString() to it.name },
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, loaded = true, rows = result.data)
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
                BranchAttendanceViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/** The web's summaryNumber: integers plain, fractions to one decimal. */
private fun summaryNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

// Sl. No. | Branch/Project | 11 numeric columns — the web's table, verbatim.
private val branchAttendanceColumns = listOf(
    ReportColumn<BranchAttendanceRow>("SL. NO.", ReportColWidth.Fixed(56.dp), TextAlign.Center) { _, index ->
        cellText((index + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<BranchAttendanceRow>("BRANCH/PROJECT", ReportColWidth.Fixed(140.dp)) { r, _ ->
        cellText(r.branchName, maxLines = 2)
    },
    ReportColumn<BranchAttendanceRow>("EMPLOYEES", ReportColWidth.Fixed(84.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.employeeCount.toDouble()), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("WORKING", ReportColWidth.Fixed(76.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.workingDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("PRESENT", ReportColWidth.Fixed(76.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.presentDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("PAID LEAVE", ReportColWidth.Fixed(88.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.paidLeaveDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("UNPAID LEAVE", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.unpaidLeaveDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("ABSENT", ReportColWidth.Fixed(72.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.absentDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("LATE", ReportColWidth.Fixed(60.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.lateCount), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("EARLY OUT", ReportColWidth.Fixed(84.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.earlyOutCount), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("HALF DAY", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.halfDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("PAYABLE", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.payableDays), align = TextAlign.End)
    },
    ReportColumn<BranchAttendanceRow>("DEDUCTION", ReportColWidth.Fixed(88.dp), TextAlign.End) { r, _ ->
        cellText(summaryNumber(r.deductionDays), align = TextAlign.End)
    },
)

/**
 * Branch Attendance — the web's per-branch roll-up of the monthly summary:
 * eight summary tiles and one table row per branch. The table deliberately has
 * no totals footer; the tiles carry the totals, as on the web.
 */
@Composable
fun BranchAttendanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: BranchAttendanceViewModel =
        viewModel(factory = BranchAttendanceViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Branch Attendance",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                HrmBranchDropdown(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranchSelected,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HrmMonthField(
                        label = "Month / Year",
                        value = state.monthYear,
                        onSelected = viewModel::onMonthSelected,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Load",
                        onClick = viewModel::load,
                        enabled = state.canLoad,
                        isLoading = state.isLoading,
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

                !state.loaded -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Choose your filters, then tap Load.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                else -> ResultContent(state)
            }
        }
    }
}

@Composable
private fun ResultContent(state: BranchAttendanceUiState) {
    val rows = state.rows
    val leave = rows.sumOf { it.paidLeaveDays + it.unpaidLeaveDays }

    Column(modifier = Modifier.fillMaxSize()) {
        // The web's eight summary cards.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BranchTile("Branches", rows.size.toString())
            BranchTile("Employees", rows.sumOf { it.employeeCount }.toString())
            BranchTile("Present", summaryNumber(rows.sumOf { it.presentDays }))
            BranchTile("Absent", summaryNumber(rows.sumOf { it.absentDays }))
            BranchTile("Leave", summaryNumber(leave))
            BranchTile("Late", summaryNumber(rows.sumOf { it.lateCount }))
            BranchTile("Early Out", summaryNumber(rows.sumOf { it.earlyOutCount }))
            BranchTile("Deduction", summaryNumber(rows.sumOf { it.deductionDays }))
        }

        ReportTable(
            columns = branchAttendanceColumns,
            data = rows,
            noDataMessage = "No data found",
        )
    }
}

@Composable
private fun BranchTile(label: String, value: String) {
    SummaryTile {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
