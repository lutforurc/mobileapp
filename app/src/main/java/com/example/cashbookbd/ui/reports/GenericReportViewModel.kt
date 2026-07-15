package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.GenericReportRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.ReportChoice
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the generic report screen for a single [ReportConfig] (resolved from
 * [reportKey]). Loads branches and seeds a default date range exactly like
 * [CashBookViewModel], then runs the report through [GenericReportRepository].
 */
class GenericReportViewModel(
    private val reportKey: String,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
    private val genericReportRepository: GenericReportRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val config: ReportConfig? = ReportMenu.byKey(reportKey)

    private val _uiState = MutableStateFlow(
        GenericReportUiState(
            title = config?.title ?: "Report",
            isSupported = config?.isGenericSupported == true,
            showStartDate = config?.startParam != null,
            showEndDate = config?.endParam != null,
            showLedger = config?.usesLedger == true,
            ledgerRequired = config?.usesLedger == true && config.ledgerRequired,
            showChoice = config?.usesChoice == true,
            choiceLabel = config?.choiceParam?.label.orEmpty(),
            choiceOptions = config?.choiceParam?.options.orEmpty(),
            // Default to the first option so Apply is usable immediately.
            selectedChoice = config?.choiceParam?.options?.firstOrNull(),
        )
    )
    val uiState: StateFlow<GenericReportUiState> = _uiState.asStateFlow()

    private var dateDefaulted = false

    init {
        if (config?.isGenericSupported == true) {
            applyDashboardTransactionDate()
            loadBranches()
        }
    }

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

    fun onLedgerSelected(ledger: LedgerDropdownItem) {
        _uiState.update { it.copy(selectedLedger = ledger) }
    }

    fun onChoiceSelected(choice: ReportChoice) {
        _uiState.update { it.copy(selectedChoice = choice) }
    }

    /** Searchable ledger/party source, reused by the shared dropdown component. */
    suspend fun searchLedgers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    fun onStartDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun onEndDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun apply() {
        val cfg = config ?: return
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }

        viewModelScope.launch {
            val result = genericReportRepository.fetch(
                config = cfg,
                branchId = branch.id,
                startDate = if (state.showStartDate) state.startDate else null,
                endDate = if (state.showEndDate) state.endDate else null,
                ledgerId = if (state.showLedger) state.selectedLedger?.id?.toLong() else null,
                choiceValue = if (state.showChoice) state.selectedChoice?.value else null,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isReportLoading = false, result = result.data, reportError = null)
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
        fun provideFactory(context: Context, reportKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                GenericReportViewModel(
                    reportKey = reportKey,
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                    genericReportRepository = ServiceLocator.provideGenericReportRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}