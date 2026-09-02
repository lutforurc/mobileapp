package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.data.repository.AssetSchedule
import com.example.cashbookbd.data.repository.AssetScheduleRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetScheduleUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val asAt: String = todayApi(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val schedule: AssetSchedule? = null,
    val sessionExpired: Boolean = false,
)

class AssetScheduleViewModel(
    private val repository: AssetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetScheduleUiState())
    val uiState: StateFlow<AssetScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isBranchesLoading = true) }
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
            load()
        }
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchSchedule(state.selectedBranch?.id, state.asAt)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, schedule = result.data)
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

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch) }
        load()
    }

    fun onAsAt(value: String) {
        _uiState.update { it.copy(asAt = value) }
        load()
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetScheduleViewModel(
                    repository = AssetRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The Schedule of Fixed Assets — the note the year-end accounts print.
 *
 * ⚠️ THIS IS THE PAPER AN AUDITOR ASKS FOR FIRST, and the only place the
 * register and the ledger can be seen agreeing. It reads across, per class:
 * cost at the start + bought − sold = cost at the end; depreciation at the start
 * + this year − on what was sold = at the end; and the difference is what the
 * class is worth.
 *
 * ⚠️ EVERY FIGURE IS READ BACK, NEVER RECOMPUTED, so a rate edited since cannot
 * restate a year the accounts have already closed.
 *
 * ⚠️ A NOUGHT IS A DASH. A schedule is read down its columns: eight columns of
 * "0.00" on a class that saw no movement all year is eight figures the eye has
 * to check and discard, while a dash is read as "nothing happened" without being
 * read at all — and the rows that DID move stand out.
 */
@Composable
fun AssetScheduleScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetScheduleViewModel = viewModel(
        factory = AssetScheduleViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    val schedule = state.schedule

    AuthenticatedShell(
        title = "Schedule of Fixed Assets",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssetBranchField(
                branches = state.branches,
                selected = state.selectedBranch,
                isLoading = state.isBranchesLoading,
                onSelected = viewModel::onBranch,
                modifier = Modifier.fillMaxWidth(),
            )
            AssetDateField(
                label = "Year ending on or after",
                value = state.asAt,
                onPicked = viewModel::onAsAt,
                modifier = Modifier.fillMaxWidth(),
            )

            if (schedule != null) {
                Text(
                    text = "For the year ${onTheDay(schedule.yearStart)} — " +
                        onTheDay(schedule.yearEnding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            when {
                state.isLoading && schedule == null -> AssetLoading()
                schedule == null -> Unit
                else -> {
                    // ⚠️ Said before the table, not after it. A schedule whose
                    // year has not been charged shows an empty depreciation
                    // column, and a reader who is not told why reads it as a year
                    // in which nothing wore out.
                    if (!schedule.charged) {
                        AssetNotice(
                            text = "This year has not been charged yet, so the For the year " +
                                "column is empty. Charge it on the Depreciation screen and this " +
                                "fills in.",
                            tone = AssetTone.Warning,
                        )
                    }

                    ReportTable(
                        columns = scheduleColumns(),
                        data = schedule.rows,
                        footerRows = scheduleFooter(schedule.total, schedule.rows.isNotEmpty()),
                        noDataMessage = "Nothing on the register for that year.",
                        scrollable = false,
                    )

                    val total = schedule.total
                    if (total != null && schedule.rows.isNotEmpty()) {
                        AssetPanel(title = "The year in four figures") {
                            AssetLine(
                                label = "Cost at 30 June",
                                value = AmountFormat.formatOrDash(total.closingCost),
                            )
                            AssetLine(
                                label = "Depreciation",
                                value = AmountFormat.formatOrDash(total.closingDep),
                            )
                            AssetLine(
                                label = "Charged this year",
                                value = AmountFormat.formatOrDash(total.charge),
                            )
                            AssetLine(
                                label = "Written down value",
                                value = AmountFormat.formatOrDash(total.closingWdv),
                                strong = true,
                                divider = false,
                            )
                            // The two subtractions a reader checks first, written
                            // out so nobody has to do them on the corner of the page.
                            Text(
                                text = "${AmountFormat.formatOrDash(total.openingCost)} + " +
                                    "${AmountFormat.formatOrDash(total.additions)} − " +
                                    "${AmountFormat.formatOrDash(total.disposalsCost)} = " +
                                    "${AmountFormat.formatOrDash(total.closingCost)}  ·  " +
                                    "${AmountFormat.formatOrDash(total.openingDep)} + " +
                                    "${AmountFormat.formatOrDash(total.charge)} − " +
                                    "${AmountFormat.formatOrDash(total.disposalsDep)} = " +
                                    AmountFormat.formatOrDash(total.closingDep),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun scheduleColumns(): List<ReportColumn<AssetScheduleRow>> = listOf(
    ReportColumn("CLASS OF ASSET", ReportColWidth.Fixed(150.dp)) { row, _ ->
        cellText(row.category, maxLines = 2)
    },
    ReportColumn("RATE", ReportColWidth.Fixed(70.dp), TextAlign.End) { row, _ ->
        cellText(row.rate?.let { percentText(it) } ?: "")
    },
    ReportColumn("COST AT 1 JULY", ReportColWidth.Fixed(115.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.openingCost))
    },
    ReportColumn("ADDITIONS", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.additions))
    },
    ReportColumn("DISPOSALS", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.disposalsCost))
    },
    ReportColumn("COST AT 30 JUNE", ReportColWidth.Fixed(125.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.closingCost), bold = true)
    },
    ReportColumn("DEP. AT 1 JULY", ReportColWidth.Fixed(115.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.openingDep))
    },
    ReportColumn("FOR THE YEAR", ReportColWidth.Fixed(115.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.charge))
    },
    ReportColumn("DEP. AT 30 JUNE", ReportColWidth.Fixed(125.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.closingDep), bold = true)
    },
    ReportColumn("WRITTEN DOWN", ReportColWidth.Fixed(125.dp), TextAlign.End) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.closingWdv), bold = true)
    },
)

private fun scheduleFooter(
    total: AssetScheduleRow?,
    hasRows: Boolean,
): List<List<ReportFooterCell>> {
    if (total == null || !hasRows) return emptyList()
    return listOf(
        listOf(
            ReportFooterCell(cellText(total.category.ifBlank { "Total" }, bold = true)),
            ReportFooterCell(cellText("", TextAlign.End)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.openingCost), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.additions), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.disposalsCost), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.closingCost), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.openingDep), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.charge), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.closingDep), TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(total.closingWdv), TextAlign.End, bold = true)),
        ),
    )
}
