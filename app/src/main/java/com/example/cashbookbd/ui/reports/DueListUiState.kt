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

    /**
     * The Employee filter (web 015380a1). Shown only where the SELECTED branch
     * asks for `need_customer_handle_by_employee` and the classification is not
     * Supplier — the field lives on the customer form alone.
     */
    val handleByEmployeeOn: Boolean = false,
    val employees: List<com.example.cashbookbd.ui.reports.model.SelectorOption> = emptyList(),
    val employee: com.example.cashbookbd.ui.reports.model.SelectorOption = ALL_EMPLOYEES,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = selectedBranch != null && !isReportLoading

    val isEmptyResult: Boolean
        get() = report != null && report.isEmpty
}

/** "All Employees" — the empty id means no `handle_by_employee_id` is sent. */
val ALL_EMPLOYEES = com.example.cashbookbd.ui.reports.model.SelectorOption(id = "", label = "All Employees")

/** "No Employee" — the parties nobody has been given to. */
val NO_EMPLOYEE = com.example.cashbookbd.ui.reports.model.SelectorOption(id = "none", label = "No Employee")
