package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BalanceSheetRepository
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

/**
 * Drives the Balance Sheet screen: loads branches, seeds a default date range,
 * and runs `POST /reports/balance-sheet` via [BalanceSheetRepository].
 */
class BalanceSheetViewModel(
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
    private val balanceSheetRepository: BalanceSheetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalanceSheetUiState())
    val uiState: StateFlow<BalanceSheetUiState> = _uiState.asStateFlow()

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
            _uiState.update { it.copy(startDate = trDate, endDate = trDate) }
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

    fun reset() {
        _uiState.update {
            it.copy(
                startDate = defaultDate,
                endDate = defaultDate,
                report = null,
                reportError = null,
                appliedBranchName = null,
                appliedRange = null,
            )
        }
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }

        viewModelScope.launch {
            val result = balanceSheetRepository.fetch(
                branchId = branch.id,
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        report = result.data,
                        reportError = null,
                        appliedBranchName = branch.name,
                        appliedRange = "${state.startDate.toDisplay()} — ${state.endDate.toDisplay()}",
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
                BalanceSheetViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                    balanceSheetRepository = ServiceLocator.provideBalanceSheetRepository(appContext),
                )
            }
        }
    }
}
