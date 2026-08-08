package com.example.cashbookbd.ui.hrm

import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.cashbookbd.ui.hrm.model.OvertimeMatrixData
import com.example.cashbookbd.ui.hrm.model.OvertimeMatrixRow
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class OvertimeMatrixUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val data: OvertimeMatrixData? = null,
    /** The day axis ("YYYY-MM-DD"), capped at 62 like the web. */
    val dates: List<String> = emptyList(),
    val title: String = "Overtime Report",

    val sessionExpired: Boolean = false,
) {
    val canLoad: Boolean get() = selectedBranch != null && !isLoading
}

class OvertimeMatrixViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OvertimeMatrixUiState())
    val uiState: StateFlow<OvertimeMatrixUiState> = _uiState.asStateFlow()

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

    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date, loaded = false) }

    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date, loaded = false) }

    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = hrmRepository.overtimeMatrix(
                branchId = branch.id,
                dateFrom = state.startDate.toApi(),
                dateTo = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loaded = true,
                        data = result.data,
                        dates = dateAxis(state.startDate, state.endDate),
                        title = overtimeTitle(state.startDate, state.endDate),
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
                OvertimeMatrixViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** The web's report title: month form for one month, range form otherwise. */
private fun overtimeTitle(from: SimpleDate, to: SimpleDate): String =
    if (from.year == to.year && from.month == to.month) {
        "Overtime for the Month of ${MONTH_NAMES[from.month - 1]} ${from.year}"
    } else {
        "Overtime from ${from.toApi()} to ${to.toApi()}"
    }

/** Every date in the range, capped at 62 days — the web caps silently too. */
private fun dateAxis(from: SimpleDate, to: SimpleDate): List<String> {
    val dates = ArrayList<String>()
    val cal = Calendar.getInstance().apply { clear(); set(from.year, from.month - 1, from.day) }
    val end = Calendar.getInstance().apply { clear(); set(to.year, to.month - 1, to.day) }
    while (!cal.after(end) && dates.size < 62) {
        dates += String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return dates
}

/** OT hours cell: two decimals when positive, a muted dash otherwise. */
private fun fmtHours(value: Double): String =
    if (value > 0.0) String.format(Locale.US, "%.2f", value) else "-"

private val OT_SL_COL = 36.dp
private val OT_NAME_COL = 124.dp
private val OT_DAY_COL = 44.dp
private val OT_TOTAL_COL = 56.dp

/**
 * Overtime Report — the web's employee × day matrix: one row per eligible
 * employee, one column per day of the range, OT hours in each cell, with a row
 * Total, per-day totals, and the grand total.
 */
@Composable
fun OvertimeMatrixScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: OvertimeMatrixViewModel =
        viewModel(factory = OvertimeMatrixViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Overtime Report",
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
                    HrmDateField(
                        label = "From",
                        value = state.startDate,
                        context = context,
                        onSelected = viewModel::onStartDate,
                        modifier = Modifier.weight(1f),
                    )
                    HrmDateField(
                        label = "To",
                        value = state.endDate,
                        context = context,
                        onSelected = viewModel::onEndDate,
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

                else -> MatrixContent(state)
            }
        }
    }
}

@Composable
private fun MatrixContent(state: OvertimeMatrixUiState) {
    val data = state.data ?: return
    if (data.rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No overtime data found.", color = MaterialTheme.appColors.textOnScreenMuted)
        }
        return
    }

    val hScroll = rememberScrollState()
    val dates = state.dates
    val dayTotals = dates.map { date -> data.rows.sumOf { it.hoursByDate[date] ?: 0.0 } }
    val grandTotal = dayTotals.sum()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // The overtime-mode summary tiles: total hours and total amount.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OtTile("OT Hr.", String.format(Locale.US, "%.2f", data.totalOtMinutes / 60.0))
            OtTile("OT Amount", String.format(Locale.US, "%,.0f", data.totalOtAmount))
        }

        // Header: # + Name frozen; the day columns and Total share one scroll
        // state with every body row so the grid pans as a unit.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("#", OT_SL_COL, TextAlign.Center)
            HeaderCell("Name", OT_NAME_COL, TextAlign.Start)
            Row(modifier = Modifier.horizontalScroll(hScroll)) {
                dates.forEach { date ->
                    HeaderCell(dayOfMonthLabel(date), OT_DAY_COL, TextAlign.Center)
                }
                HeaderCell("Total", OT_TOTAL_COL, TextAlign.Center)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(data.rows, key = { _, row -> row.employeeId }) { index, row ->
                MatrixRowLine(index = index, row = row, dates = dates, hScroll = hScroll)
            }
        }

        // The web's totals footer: per-day totals and the grand total.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(OT_SL_COL + OT_NAME_COL),
            )
            Row(modifier = Modifier.horizontalScroll(hScroll)) {
                dayTotals.forEach { total ->
                    Text(
                        text = fmtHours(total),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = AppFontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(OT_DAY_COL),
                    )
                }
                Text(
                    text = fmtHours(grandTotal),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(OT_TOTAL_COL),
                )
            }
        }
    }
}

@Composable
private fun MatrixRowLine(
    index: Int,
    row: OvertimeMatrixRow,
    dates: List<String>,
    hScroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(OT_SL_COL),
        )
        Text(
            text = row.employeeName.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(OT_NAME_COL),
        )
        Row(modifier = Modifier.horizontalScroll(hScroll)) {
            dates.forEach { date ->
                val value = row.hoursByDate[date] ?: 0.0
                Text(
                    text = fmtHours(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (value > 0.0) AppFontWeight.SemiBold else AppFontWeight.Normal,
                    color = if (value > 0.0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.appColors.textOnScreenMuted
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(OT_DAY_COL),
                )
            }
            Text(
                text = fmtHours(row.totalHours),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(OT_TOTAL_COL),
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
    )
}

@Composable
private fun OtTile(label: String, value: String) {
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

/** "2026-08-05" → "5", the web's day-of-month header. */
private fun dayOfMonthLabel(date: String): String =
    date.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull()?.toString() ?: date
