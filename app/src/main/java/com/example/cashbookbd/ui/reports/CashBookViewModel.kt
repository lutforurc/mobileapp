package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CashBookViewModel(
    private val repository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashBookUiState())
    val uiState: StateFlow<CashBookUiState> = _uiState.asStateFlow()

    /** True once the default date range has been seeded, so later sources don't override it. */
    private var dateDefaulted = false

    init {
        applyDashboardTransactionDate()
        loadBranches()
    }

    /**
     * Seed both Start and End date from the dashboard's `trDate` (e.g. "03/07/2026"),
     * which is the backend's current business date. Reads the cached dashboard first
     * (instant, populated by the Home screen) and falls back to a network fetch.
     */
    private fun applyDashboardTransactionDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
            _uiState.update { it.copy(startDate = trDate, endDate = trDate) }
        }
    }

    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = repository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    // Fallback: use the branch list's transaction date only if the
                    // dashboard's trDate didn't already set the range.
                    val branchTrDate = result.data.transactionDate
                    val applyBranchDate = !dateDefaulted && branchTrDate != null
                    if (applyBranchDate) dateDefaulted = true
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        // Preselect the first branch for convenience.
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                        startDate = if (applyBranchDate) branchTrDate!! else it.startDate,
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

    fun onStartDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun onEndDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }

        viewModelScope.launch {
            val result = repository.getCashBook(
                branchId = branch.id,
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isReportLoading = false, report = result.data, reportError = null)
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
                CashBookViewModel(
                    repository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}
