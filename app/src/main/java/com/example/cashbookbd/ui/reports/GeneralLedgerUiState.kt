package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.reports.model.GeneralLedgerReport
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class GeneralLedgerUiState(
    // Filters
    val branch: BranchPick = BranchPick(),
    /** Null = All Accounts (the web's DdlMultiline action option). */
    val account: SelectorOption? = null,
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    /** Bumped on Reset so the Accounts Head search box clears its typed text. */
    val resetKey: Int = 0,

    // Report
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: GeneralLedgerReport? = null,
    val appliedBranchName: String? = null,
    val appliedRange: String? = null,
    val appliedAccountsHead: String = "All Accounts",

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = branch.selected != null && startDate != null && endDate != null && !isLoading

    val isEmptyResult: Boolean
        get() = report != null && report.isEmpty

    val page: Int get() = report?.pagination?.page ?: 1
    val lastPage: Int get() = report?.pagination?.lastPage ?: 1
    val canGoBack: Boolean get() = page > 1 && !isLoading
    val canGoForward: Boolean get() = page < lastPage && !isLoading
}
