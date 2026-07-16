package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.report.ReportChoice
import com.example.cashbookbd.report.ReportResult
import com.example.cashbookbd.report.ReportSelector
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * One remote-dropdown filter's live state: its [config], the loaded [options]
 * (empty for searchable sources, which fetch on type), the [selected] option, and
 * loading/error flags for a static source's initial fetch.
 */
data class SelectorFieldState(
    val config: ReportSelector,
    val options: List<SelectorOption> = emptyList(),
    val selected: SelectorOption? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class GenericReportUiState(
    val title: String = "Report",
    /** False when this report's filters aren't yet available in the mobile app. */
    val isSupported: Boolean = true,
    val showStartDate: Boolean = true,
    val showEndDate: Boolean = true,
    /** True when this report shows the searchable ledger/party picker. */
    val showLedger: Boolean = false,
    /** True when a ledger/party must be selected before Apply is enabled. */
    val ledgerRequired: Boolean = false,
    val selectedLedger: LedgerDropdownItem? = null,

    /** True when this report shows the single-select choice dropdown. */
    val showChoice: Boolean = false,
    val choiceLabel: String = "",
    val choiceOptions: List<ReportChoice> = emptyList(),
    val selectedChoice: ReportChoice? = null,

    /** Extra remote-dropdown filters (category, brand, product, somity, labour). */
    val selectors: List<SelectorFieldState> = emptyList(),

    /** True when this report shows the month/year picker (Collection Sheet). */
    val showMonthYear: Boolean = false,
    val monthYear: MonthYear = MonthYear.current(),

    // Filter form
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    // Result
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val result: ReportResult? = null,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = isSupported &&
            selectedBranch != null &&
            !isReportLoading &&
            (!ledgerRequired || selectedLedger != null) &&
            (!showChoice || selectedChoice != null) &&
            selectors.all { !it.config.required || it.selected != null }

    val isEmptyResult: Boolean
        get() = result != null && result.isEmpty
}
