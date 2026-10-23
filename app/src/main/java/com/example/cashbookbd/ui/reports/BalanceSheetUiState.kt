package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.BalanceSheetReport
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class BalanceSheetUiState(
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
    val report: BalanceSheetReport? = null,
    val appliedBranchName: String? = null,
    val appliedRange: String? = null,
    /** The sheet's "as at" date (dd/MM/yyyy), named in note 1. */
    val appliedAsOn: String? = null,
    /**
     * The period's write-offs and Product In receipts at cost, for the
     * stock-movement note. Null = not read (yet) — writes no note, never a zero.
     */
    val stockOut: Double? = null,
    val stockIn: Double? = null,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = selectedBranch != null && !isReportLoading

    val isEmptyResult: Boolean
        get() = report != null && report.isEmpty
}
