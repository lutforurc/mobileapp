package com.example.cashbookbd.ui.hrm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.cashbookbd.ui.hrm.model.MonthlySummaryRow
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** One matrix row: an employee and their day → glyph map. */
data class MatrixRow(
    val employeeId: Long,
    val name: String,
    val serial: String,
    val days: Map<Int, String>,
)

data class MonthlyAttendanceUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val monthYear: MonthYear = MonthYear.current(),
    /** 0 = Summary, 1 = Matrix. */
    val tab: Int = 0,

    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val summaryRows: List<MonthlySummaryRow> = emptyList(),
    val matrixRows: List<MatrixRow> = emptyList(),
    val daysInMonth: Int = 30,

    val sessionExpired: Boolean = false,
) {
    val canLoad: Boolean get() = selectedBranch != null && !isLoading
}

class MonthlyAttendanceViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyAttendanceUiState())
    val uiState: StateFlow<MonthlyAttendanceUiState> = _uiState.asStateFlow()

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
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
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

    fun onTabSelected(tab: Int) = _uiState.update { it.copy(tab = tab) }

    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        val calendar = Calendar.getInstance()
        calendar.set(state.monthYear.year, state.monthYear.month - 1, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthPart = String.format(Locale.US, "%02d", state.monthYear.month)
        val dateFrom = "${state.monthYear.year}-$monthPart-01"
        val dateTo = "${state.monthYear.year}-$monthPart-" +
            String.format(Locale.US, "%02d", daysInMonth)

        _uiState.update { it.copy(isLoading = true, error = null, daysInMonth = daysInMonth) }
        viewModelScope.launch {
            val summary = hrmRepository.monthlySummaryRows(
                branchId = branch.id,
                month = state.monthYear.month,
                year = state.monthYear.year,
            )
            if (summary is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = summary.message,
                        sessionExpired = it.sessionExpired || summary.isUnauthorized,
                    )
                }
                return@launch
            }

            val cells = hrmRepository.attendanceMatrix(
                branchId = branch.id,
                dateFrom = dateFrom,
                dateTo = dateTo,
            )
            if (cells is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = cells.message,
                        sessionExpired = it.sessionExpired || cells.isUnauthorized,
                    )
                }
                return@launch
            }

            // Group day cells per employee. A leave row wins over a same-day
            // entry (server may send both when leave overlaps an entry).
            val byEmployee = LinkedHashMap<Long, MatrixRow>()
            (cells as Resource.Success).data.forEach { cell ->
                val existing = byEmployee[cell.employeeId]
                val glyph = glyphFor(cell.status, cell.approvalStatus)
                val days = existing?.days.orEmpty().toMutableMap()
                val current = days[cell.day]
                if (current == null || glyph == GLYPH_LEAVE) days[cell.day] = glyph
                byEmployee[cell.employeeId] = MatrixRow(
                    employeeId = cell.employeeId,
                    name = existing?.name?.ifBlank { cell.employeeName } ?: cell.employeeName,
                    serial = existing?.serial?.ifBlank { cell.employeeSerial } ?: cell.employeeSerial,
                    days = days,
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    loaded = true,
                    summaryRows = (summary as Resource.Success).data,
                    matrixRows = byEmployee.values.sortedBy { row -> row.name },
                )
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                MonthlyAttendanceViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

// The web matrix's status glyphs.
private const val GLYPH_LEAVE = "L"

private fun glyphFor(status: String, approvalStatus: String): String {
    if (approvalStatus.equals("rejected", ignoreCase = true)) return "✕"
    return when (status.lowercase(Locale.US)) {
        "present", "early_out" -> "✓"
        "late" -> "!"
        "absent", "rejected" -> "✕"
        "leave" -> GLYPH_LEAVE
        "holiday", "weekly_holiday" -> "○"
        "half_day" -> "½"
        else -> "•"
    }
}

/**
 * Monthly Attendance — the web page's two tabs: the per-employee summary and
 * the day-by-day glyph matrix ("Attendance for the Month of …").
 */
@Composable
fun MonthlyAttendanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MonthlyAttendanceViewModel =
        viewModel(factory = MonthlyAttendanceViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Monthly Attendance",
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
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            TabRow(selectedTabIndex = state.tab) {
                Tab(
                    selected = state.tab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = { Text("Summary") },
                )
                Tab(
                    selected = state.tab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = { Text("Month Matrix") },
                )
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !state.loaded -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Choose your filters, then tap Load.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.tab == 0 -> SummaryTab(state)

                else -> MatrixTab(state)
            }
        }
    }
}

@Composable
private fun SummaryTab(state: MonthlyAttendanceUiState) {
    if (state.summaryRows.isEmpty()) {
        EmptyNote("No attendance summary for this month.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
    ) {
        items(state.summaryRows, key = { it.employeeId }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = row.employeeName +
                            row.employeeSerial.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        LabelValue("Present", row.presentDays.dash(), Modifier.weight(1f))
                        LabelValue("Paid Lv", row.paidLeaveDays.dash(), Modifier.weight(1f))
                        LabelValue("Unpaid Lv", row.unpaidLeaveDays.dash(), Modifier.weight(1f))
                        LabelValue("Absent", row.absentDays.dash(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        LabelValue("Late", row.lateCount.dash(), Modifier.weight(1f))
                        LabelValue("Early Out", row.earlyOutCount.dash(), Modifier.weight(1f))
                        LabelValue("Half Day", row.halfDays.dash(), Modifier.weight(1f))
                        LabelValue("Payable", row.payableDays.dash(), Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val NAME_COL = 128.dp
private val DAY_COL = 30.dp

@Composable
private fun MatrixTab(state: MonthlyAttendanceUiState) {
    if (state.matrixRows.isEmpty()) {
        EmptyNote("No attendance entries for this month.")
        return
    }
    val hScroll = rememberScrollState()
    val days = (1..state.daysInMonth).toList()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Attendance for the Month of ${state.monthYear.toDisplay()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Header: employee column + one column per day, sharing one scroll state
        // with every body row so the grid pans as a unit.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = "Employee",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(NAME_COL).padding(start = 16.dp),
            )
            Row(modifier = Modifier.horizontalScroll(hScroll)) {
                days.forEach { day ->
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(DAY_COL),
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.matrixRows, key = { it.employeeId }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(NAME_COL).padding(start = 16.dp),
                    )
                    Row(modifier = Modifier.horizontalScroll(hScroll)) {
                        days.forEach { day ->
                            val glyph = row.days[day] ?: "-"
                            Text(
                                text = glyph,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = when (glyph) {
                                    "✕", "!" -> MaterialTheme.colorScheme.error
                                    "✓" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.width(DAY_COL),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "✓ Present   ! Late   ✕ Absent   L Leave   ○ Holiday   ½ Half Day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Counts render as the report tables do: 0 shows as a dash. */
private fun Double.dash(): String =
    if (this == 0.0) "-" else if (this == toLong().toDouble()) toLong().toString() else toString()
