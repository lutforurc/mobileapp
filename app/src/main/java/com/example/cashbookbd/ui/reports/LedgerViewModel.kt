package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LedgerViewModel(
    private val ledgerRepository: LedgerRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    /** True once the default date range has been seeded, so later sources don't override it. */
    private var dateDefaulted = false

    init {
        applyDashboardTransactionDate()
        loadBranches()
    }

    // ---- Branch + date defaults --------------------------------------------

    /** Seed both dates from the dashboard's `trDate` (the backend's business date). */
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
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    val branchTrDate = result.data.transactionDate
                    val applyBranchDate = !dateDefaulted && branchTrDate != null
                    if (applyBranchDate) dateDefaulted = true
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

    // ---- Ledger search -----------------------------------------------------

    /**
     * Search passthrough for [SearchableLedgerDropdown]; the component owns the
     * debounce and loading/empty/error state.
     */
    suspend fun searchLedgers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    /** Called when the user picks a ledger from the dropdown. */
    fun onLedgerSelected(item: LedgerDropdownItem) {
        _uiState.update { it.copy(selectedLedger = item) }
    }

    // ---- Report ------------------------------------------------------------

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        val ledger = state.selectedLedger ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }

        viewModelScope.launch {
            val result = ledgerRepository.getLedgerReport(
                branchId = branch.id,
                ledgerId = ledger.id.toLong(),
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isReportLoading = false, statement = result.data, reportError = null)
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
                LedgerViewModel(
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}
