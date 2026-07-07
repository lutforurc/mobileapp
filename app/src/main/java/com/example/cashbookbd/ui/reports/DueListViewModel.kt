package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.DueListRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Due List screen: loads branches, seeds a default end date, and runs
 * `GET /reports/duelist` via [DueListRepository].
 */
class DueListViewModel(
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
    private val dueListRepository: DueListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DueListUiState())
    val uiState: StateFlow<DueListUiState> = _uiState.asStateFlow()

    private var defaultDate: SimpleDate = SimpleDate.today()
    private var dateDefaulted = false

    init {
        applyDashboardTransactionDate()
        loadBranches()
    }

    private fun applyDashboardTransactionDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
            defaultDate = trDate
            _uiState.update { it.copy(endDate = trDate) }
        }
    }

    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    val branchTrDate = result.data.transactionDate
                    val applyBranchDate = !dateDefaulted && branchTrDate != null
                    if (applyBranchDate) {
                        dateDefaulted = true
                        defaultDate = branchTrDate!!
                    }
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                        endDate = if (applyBranchDate) branchTrDate!! else it.endDate,
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

    fun onBranchSelected(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun onEndDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                endDate = defaultDate,
                report = null,
                reportError = null,
                appliedBranchName = null,
                appliedEndDate = null,
            )
        }
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }

        viewModelScope.launch {
            val result = dueListRepository.fetch(
                branchId = branch.id,
                endApi = state.endDate.toApi(),
                endDisplay = state.endDate.toDisplay(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        report = result.data,
                        reportError = null,
                        appliedBranchName = branch.name,
                        appliedEndDate = state.endDate.toDisplay(),
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        reportError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() {
        _uiState.update { it.copy(sessionExpired = false) }
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                DueListViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                    dueListRepository = ServiceLocator.provideDueListRepository(appContext),
                )
            }
        }
    }
}
