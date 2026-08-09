package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BuildingDetailRow
import com.example.cashbookbd.data.repository.ProjectCostIntegrityRow
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectSummaryRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.UntaggedExpenseRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three tabs of the Project Cost report. */
enum class ProjectCostSection(val title: String, val blurb: String) {
    SUMMARY(
        "Project Summary",
        "What each project has cost, and what that is per square foot.",
    ),
    BUILDING(
        "Building Detail",
        "Each building by expense head. Direct is what was spent on that building; " +
            "allocated is its share of costs the whole project carries.",
    ),
    UNTAGGED(
        "Expenses Without a Project",
        "Expense lines nobody tagged. They are right in the trial balance and " +
            "absent from every project figure above.",
    ),
}

data class ProjectCostReportUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,

    val section: ProjectCostSection = ProjectCostSection.SUMMARY,
    val isLoading: Boolean = false,

    val summaryRows: List<ProjectSummaryRow> = emptyList(),
    val buildingRows: List<BuildingDetailRow> = emptyList(),
    val untaggedRows: List<UntaggedExpenseRow> = emptyList(),
    /** Tagged money none of the reports can show; kept across tab switches. */
    val integrity: List<ProjectCostIntegrityRow> = emptyList(),
    /** Which sections hold fresh data; Apply marks all three stale. */
    val loaded: Set<ProjectCostSection> = emptySet(),

    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val sectionLoaded: Boolean get() = section in loaded
}

/**
 * Backs the Project Cost report — one screen, three tab-selected reports plus
 * the integrity panel. Nothing loads until Apply; a tab shown for the first
 * time loads itself lazily off the applied filters, like the web.
 */
class ProjectCostReportViewModel(
    private val repository: ProjectCostRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCostReportUiState())
    val uiState: StateFlow<ProjectCostReportUiState> = _uiState.asStateFlow()

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
    fun onSection(section: ProjectCostSection) {
        _uiState.update { it.copy(section = section) }
        val state = _uiState.value
        if (section !in state.loaded && state.selectedBranch != null && !state.isLoading) {
            load(section)
        }
    }

    private fun load(section: ProjectCostSection) {
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
                ProjectCostSection.SUMMARY -> when (val r = repository.projectSummary(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            summaryRows = r.data.rows,
                            integrity = r.data.integrity,
                            loaded = it.loaded + section,
                        )
                    }
                    is Resource.Error -> fail(r)
                    Resource.Loading -> Unit
                }
                ProjectCostSection.BUILDING -> when (val r = repository.buildingDetail(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            buildingRows = r.data.rows,
                            integrity = r.data.integrity,
                            loaded = it.loaded + section,
                        )
                    }
                    is Resource.Error -> fail(r)
                    Resource.Loading -> Unit
                }
                ProjectCostSection.UNTAGGED -> when (val r = repository.untaggedExpense(branch.id, start, end)) {
                    is Resource.Success -> _uiState.update {
                        // The untagged reply carries no integrity list; the one
                        // already on screen stays, like the web.
                        it.copy(
                            isLoading = false,
                            untaggedRows = r.data.rows,
                            loaded = it.loaded + section,
                        )
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
                ProjectCostReportViewModel(
                    repository = ServiceLocator.provideProjectCostRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    ownBranchId = ServiceLocator.provideSessionManager(appContext)
                        .state.value.settings?.branchId,
                )
            }
        }
    }
}
