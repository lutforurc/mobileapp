package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.CashFlowStatementReport
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class CashFlowStatementUiState(
    val branch: BranchPick = BranchPick(),
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: CashFlowStatementReport? = null,
    val appliedBranchName: String? = null,
    val appliedRange: String? = null,
    val hasRun: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = branch.selected != null && startDate != null && endDate != null && !isLoading
}
