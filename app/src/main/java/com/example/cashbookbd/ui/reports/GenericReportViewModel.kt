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
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.ReportChoice
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the generic report screen for a single [ReportConfig] (resolved from
 * [reportKey]). Loads branches and seeds a default date range exactly like
 * [CashBookViewModel], loads any static filter dropdowns (category/brand/somity),
 * and runs the report through [GenericReportRepository].
 */
class GenericReportViewModel(
    private val reportKey: String,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
    private val genericReportRepository: GenericReportRepository,
    private val ledgerRepository: LedgerRepository,
    private val selectorRepository: SelectorRepository,
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
            selectors = config?.selectors.orEmpty().map { SelectorFieldState(config = it) },
            showMonthYear = config?.usesMonthYear == true,
            showYearOnly = config?.usesYearOnly == true,
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
                is Resource.Success -> {
                    _uiState.update {
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
                    _uiState.value.selectedBranch?.let { loadStaticSelectors(it.id) }
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

    /** Loads options for every non-searchable selector (category, brand, somity). */
    private fun loadStaticSelectors(branchId: Long) {
        _uiState.value.selectors
            .filterNot { it.config.source.searchable }
            .forEach { field ->
                val source = field.config.source
                updateSelector(source.name) { it.copy(isLoading = true, error = null) }
                viewModelScope.launch {
                    when (val result = selectorRepository.fetch(source = source, branchId = branchId)) {
                        is Resource.Success -> updateSelector(source.name) {
                            // Drop a stale selection that's no longer in the new list.
                            val stillValid = it.selected?.let { sel -> result.data.any { o -> o.id == sel.id } } == true
                            it.copy(
                                isLoading = false,
                                options = result.data,
                                selected = if (stillValid) it.selected else null,
                            )
                        }

                        is Resource.Error -> {
                            updateSelector(source.name) { it.copy(isLoading = false, error = result.message) }
                            if (result.isUnauthorized) _uiState.update { it.copy(sessionExpired = true) }
                        }

                        Resource.Loading -> Unit
                    }
                }
            }
    }

    /** Updates the selector field whose source matches [sourceName], by copy. */
    private fun updateSelector(sourceName: String, transform: (SelectorFieldState) -> SelectorFieldState) {
        _uiState.update { state ->
            state.copy(
                selectors = state.selectors.map {
                    if (it.config.source.name == sourceName) transform(it) else it
                },
            )
        }
    }

    fun onBranchSelected(branch: BranchOption) {
        val changed = _uiState.value.selectedBranch?.id != branch.id
        _uiState.update { it.copy(selectedBranch = branch) }
        // Branch-scoped dropdowns (e.g. somity) must reload for the new branch.
        if (changed) loadStaticSelectors(branch.id)
    }

    fun onLedgerSelected(ledger: LedgerDropdownItem) {
        _uiState.update { it.copy(selectedLedger = ledger) }
    }

    fun onChoiceSelected(choice: ReportChoice) {
        _uiState.update { it.copy(selectedChoice = choice) }
    }

    fun onSelectorSelected(paramKey: String, option: SelectorOption) {
        _uiState.update { state ->
            state.copy(
                selectors = state.selectors.map {
                    if (it.config.paramKey == paramKey) it.copy(selected = option) else it
                },
            )
        }
    }

    fun onMonthYearSelected(monthYear: MonthYear) {
        _uiState.update { it.copy(monthYear = monthYear) }
    }

    /** Searchable ledger/party source, reused by the shared dropdown component. */
    suspend fun searchLedgers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    /** Searchable selector source (product, labour), reused by the shared dropdown. */
    suspend fun searchSelector(
        source: ReportSelectorSource,
        query: String,
    ): Resource<List<SelectorOption>> =
        selectorRepository.fetch(
            source = source,
            query = query,
            branchId = _uiState.value.selectedBranch?.id,
        )

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

        // Selected dropdown values, keyed by the endpoint param (id, or label text
        // when the selector is configured to send its label, e.g. product_name).
        val selectorValues = state.selectors
            .mapNotNull { field ->
                val chosen = field.selected ?: return@mapNotNull null
                field.config.paramKey to if (field.config.sendLabel) chosen.label else chosen.id
            }
            .toMap()

        viewModelScope.launch {
            val result = genericReportRepository.fetch(
                config = cfg,
                branchId = branch.id,
                startDate = if (state.showStartDate) state.startDate else null,
                endDate = if (state.showEndDate) state.endDate else null,
                ledgerId = if (state.showLedger) state.selectedLedger?.id?.toLong() else null,
                choiceValue = if (state.showChoice) state.selectedChoice?.value else null,
                selectorValues = selectorValues,
                monthYear = if (cfg.monthYearParam != null) state.monthYear.toParam() else null,
                month = if (cfg.monthParam != null) state.monthYear.month.toString() else null,
                year = if (cfg.yearParam != null) state.monthYear.year.toString() else null,
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
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}
