package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.BankBookReport
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class BankBookUiState(
    // Filter form
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    // Report
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val report: BankBookReport? = null,

    val sessionExpired: Boolean = false,
) {
    /** Apply is allowed once a branch is chosen and no request is in flight. */
    val canApply: Boolean
        get() = selectedBranch != null && !isReportLoading

    /** True after a successful load that returned no rows. */
    val isEmptyResult: Boolean
        get() = report != null && report.rows.isEmpty()
}
