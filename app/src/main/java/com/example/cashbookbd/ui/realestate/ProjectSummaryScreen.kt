package com.example.cashbookbd.ui.realestate

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectOverviewRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class ProjectSummaryUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,

    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val rows: List<ProjectOverviewRow> = emptyList(),
    val loadError: String? = null,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean get() = selectedBranch != null && !isLoading
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * The all-projects summary (web 92b3798): every project of the branch on one
 * line — expense, income, purchase, labour, units/sold, received/outstanding
 * and a signed P&L, with a client-summed Total row like the web's tfoot.
 * Reads `real-estate/reports/project-summary-all`.
 */
class ProjectSummaryViewModel(
    private val projectCostRepository: ProjectCostRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSummaryUiState())
    val uiState: StateFlow<ProjectSummaryUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    // The web seeds both pickers with the branch's transaction
                    // date; the server does not filter by them yet, but the
                    // fields mean the same thing on both clients.
                    val transactionDate = result.data.transactionDate
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = state.selectedBranch ?: result.data.branches.firstOrNull(),
                        startDate = state.startDate ?: transactionDate,
                        endDate = state.endDate ?: transactionDate,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(selectedBranch = branch) }

    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val result = projectCostRepository.projectOverview(
                branchId = branch.id,
                startDate = state.startDate?.toApi().orEmpty(),
                endDate = state.endDate?.toApi().orEmpty(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasLoaded = true, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        loadError = result.message,
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
                ProjectSummaryViewModel(
                    projectCostRepository = ServiceLocator.provideProjectCostRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun ProjectSummaryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: ProjectSummaryViewModel =
        viewModel(factory = ProjectSummaryViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Project Summary",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = { option ->
                        state.branches.firstOrNull { it.id.toString() == option.id }
                            ?.let(viewModel::onBranchSelected)
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryDateField(
                        label = "Start Date",
                        value = state.startDate,
                        context = context,
                        onSelected = viewModel::onStartDate,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryDateField(
                        label = "End Date",
                        value = state.endDate,
                        context = context,
                        onSelected = viewModel::onEndDate,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                FilterActions(
                    onApply = viewModel::load,
                    canApply = state.canApply,
                    isLoading = state.isLoading,
                )
                state.loadError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

                !state.hasLoaded -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Choose your filters, then tap Apply.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                state.rows.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No projects found for the selected criteria.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> ProjectSummaryTable(rows = state.rows)
            }
        }
    }
}

@Composable
private fun ProjectSummaryTable(rows: List<ProjectOverviewRow>) {
    val money: (Double) -> String = { AmountFormat.formatOrDash(it) }
    // The web's signed P&L: green when income covers expense, red when not.
    val plColor: @Composable (Double) -> androidx.compose.ui.graphics.Color = { value ->
        if (value >= 0) MaterialTheme.appColors.success else MaterialTheme.appColors.danger
    }

    val columns = listOf(
        ReportColumn<ProjectOverviewRow>("Project", ReportColWidth.Fixed(150.dp)) { r, _ ->
            cellText(r.projectName)
        },
        ReportColumn("Expense", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(money(r.totalExpense), align = TextAlign.End)
        },
        ReportColumn("Income", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(money(r.totalIncome), align = TextAlign.End)
        },
        ReportColumn("Purchase", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(money(r.totalPurchase), align = TextAlign.End)
        },
        ReportColumn("Labour", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(money(r.totalLabour), align = TextAlign.End)
        },
        ReportColumn("Units", ReportColWidth.Fixed(64.dp), TextAlign.Center) { r, _ ->
            cellText(r.totalUnits.toString(), align = TextAlign.Center)
        },
        ReportColumn("Sold", ReportColWidth.Fixed(56.dp), TextAlign.Center) { r, _ ->
            cellText(r.soldUnits.toString(), align = TextAlign.Center)
        },
        ReportColumn("Received", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(money(r.receivedAmount), align = TextAlign.End)
        },
        ReportColumn("Outstanding", ReportColWidth.Fixed(112.dp), TextAlign.End) { r, _ ->
            ReportTableCell.Slot {
                Text(
                    text = money(r.outstandingAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.warning,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        },
        ReportColumn("P&L", ReportColWidth.Fixed(112.dp), TextAlign.End) { r, _ ->
            ReportTableCell.Slot {
                Text(
                    text = (if (r.profitLoss >= 0) "+" else "") + money(r.profitLoss),
                    style = MaterialTheme.typography.bodySmall,
                    color = plColor(r.profitLoss),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        },
    )

    // The web's tfoot: everything re-summed client-side from the rows on show.
    val totalPl = rows.sumOf { it.profitLoss }
    val onScreen = MaterialTheme.colorScheme.onBackground
    fun total(text: String, align: TextAlign = TextAlign.End) =
        ReportFooterCell(cellText(text, align = align, bold = true, color = onScreen))
    val footer = listOf(
        total("Total", TextAlign.Start),
        total(money(rows.sumOf { it.totalExpense })),
        total(money(rows.sumOf { it.totalIncome })),
        total(money(rows.sumOf { it.totalPurchase })),
        total(money(rows.sumOf { it.totalLabour })),
        total(rows.sumOf { it.totalUnits }.toString(), TextAlign.Center),
        total(rows.sumOf { it.soldUnits }.toString(), TextAlign.Center),
        total(money(rows.sumOf { it.receivedAmount })),
        total(money(rows.sumOf { it.outstandingAmount })),
        total((if (totalPl >= 0) "+" else "") + money(totalPl)),
    )

    ReportTable(columns = columns, data = rows, footerRows = listOf(footer))
}

@Composable
private fun SummaryDateField(
    label: String,
    value: SimpleDate?,
    context: Context,
    onSelected: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerField(
        label = label,
        value = value?.toDisplay().orEmpty(),
        placeholder = "dd/mm/yyyy",
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = {
            val seed = value ?: SimpleDate(2026, 1, 1)
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(SimpleDate(year, month + 1, day)) },
                seed.year,
                seed.month - 1,
                seed.day,
            ).show()
        },
    )
}
