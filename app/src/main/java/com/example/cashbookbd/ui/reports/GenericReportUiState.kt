package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.report.ReportResult
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

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
            (!ledgerRequired || selectedLedger != null)

    val isEmptyResult: Boolean
        get() = result != null && result.isEmpty
}
