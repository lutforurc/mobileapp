package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.DueListReport
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class DueListUiState(
    // Filter form
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val endDate: SimpleDate = SimpleDate.today(),
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    // Report
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val report: DueListReport? = null,
    val appliedBranchName: String? = null,
    val appliedEndDate: String? = null,

    /**
     * The web's Ageing switch (61ea28f5: on by default, "since nobody found
     * the switch"). Client state only — the server always sends the buckets;
     * this decides whether the five columns are drawn.
     */
    val showAgeing: Boolean = true,
    /** Client Type, sent as party_type_id on Apply — blank is every party. */
    val partyType: com.example.cashbookbd.ui.reports.model.SelectorOption = com.example.cashbookbd.ui.customer.ALL_CLIENT_TYPES,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = selectedBranch != null && !isReportLoading

    val isEmptyResult: Boolean
        get() = report != null && report.isEmpty
}
