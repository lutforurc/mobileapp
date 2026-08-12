package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectIncomeDetailRow
import com.example.cashbookbd.data.repository.ProjectIncomeSummaryRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.UntaggedIncomeRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three tabs of the Project Income report. */
enum class ProjectIncomeSection(val title: String, val blurb: String) {
    SUMMARY(
        "Income Summary",
        "What each project has earned. Building is what a named building brought " +
            "in; project-wide is what the project earned with no building named.",
    ),
    DETAIL(
        "Income Detail",
        "Each project's earnings by income head, and by building where one was " +
            "named. Project-wide income stays in its own row rather than being " +
            "spread over the buildings.",
    ),
    UNTAGGED(
        "Income Without a Project",
        "Income lines carrying no project. Branch income that never had one " +
            "belongs here; a unit sale does not — it tags itself, so one showing " +
            "up is a sale whose unit could not be traced to a live building.",
    ),
}

data class ProjectIncomeReportUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,

    val section: ProjectIncomeSection = ProjectIncomeSection.SUMMARY,
    val isLoading: Boolean = false,

    val summaryRows: List<ProjectIncomeSummaryRow> = emptyList(),
    val detailRows: List<ProjectIncomeDetailRow> = emptyList(),
    val untaggedRows: List<UntaggedIncomeRow> = emptyList(),
    /** Which sections hold fresh data; Apply marks all three stale. */
    val loaded: Set<ProjectIncomeSection> = emptySet(),

    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val sectionLoaded: Boolean get() = section in loaded
}

/**
 * Backs the Project Income report — the cost report's mirror, one screen with
 * three tab-selected reports. Nothing loads until Apply; a tab shown for the
 * first time loads itself lazily off the applied filters, like the web.
 *
 * It follows the SALE, not the cash: a unit sale books its whole contract
 * value as income on the day the flat is sold, while the money arrives over
 * months of installments. What a project shows here is what it has sold, not
 * what it has collected — the installment and due reports answer that.
 */
class ProjectIncomeReportViewModel(
    private val repository: ProjectCostRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectIncomeReportUiState())
    val uiState: StateFlow<ProjectIncomeReportUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    val transactionDate = result.data.transactionDate
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        // The web defaults to the user's own branch.
                        selectedBranch = state.selectedBranch
                            ?: result.data.branches.firstOrNull { it.id == ownBranchId }
                            ?: result.data.branches.firstOrNull(),
                        // Web defaults: start of the business year → business date.
                        startDate = state.startDate
                            ?: transactionDate?.copy(month = 1, day = 1),
                        endDate = state.endDate ?: transactionDate,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    /** Re-reads the current tab and marks the other two stale. */
    fun apply() {
        _uiState.update { it.copy(loaded = emptySet()) }
        load(_uiState.value.section)
    }

    /** Switches tab, loading it the first time it is shown. */
    fun onSection(section: ProjectIncomeSection) {
        _uiState.update { it.copy(section = section) }
        val state = _uiState.value
        if (section !in state.loaded && state.selectedBranch != null && !state.isLoading) {
            load(section)
        }
    }

    private fun load(section: ProjectIncomeSection) {
        val state = _uiState.value
        val branch = state.selectedBranch
        if (branch == null) {
            _uiState.update { it.copy(actionMessage = "Select a branch first.") }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        val start = state.startDate?.toApi().orEmpty()
        val end = state.endDate?.toApi().orEmpty()
        viewModelScope.launch {
            when (section) {
                ProjectIncomeSection.SUMMARY -> when (val r = repository.incomeSummary(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(isLoading = false, summaryRows = r.data, loaded = it.loaded + section)
                    }
                    is Resource.Error -> fail(r)
                    Resource.Loading -> Unit
                }
                ProjectIncomeSection.DETAIL -> when (val r = repository.incomeDetail(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(isLoading = false, detailRows = r.data, loaded = it.loaded + section)
                    }
                    is Resource.Error -> fail(r)
                    Resource.Loading -> Unit
                }
                ProjectIncomeSection.UNTAGGED -> when (val r = repository.untaggedIncome(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(isLoading = false, untaggedRows = r.data, loaded = it.loaded + section)
                    }
                    is Resource.Error -> fail(r)
                    Resource.Loading -> Unit
                }
            }
        }
    }

    private fun fail(error: Resource.Error) = _uiState.update {
        it.copy(
            isLoading = false,
            actionMessage = error.message,
            sessionExpired = it.sessionExpired || error.isUnauthorized,
        )
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ProjectIncomeReportViewModel(
                    repository = ServiceLocator.provideProjectCostRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    ownBranchId = ServiceLocator.provideSessionManager(appContext)
                        .state.value.settings?.branchId,
                )
            }
        }
    }
}
