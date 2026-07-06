package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.LedgerStatement
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class LedgerUiState(
    // Branch filter
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    // Date range
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    // Selected ledger (search state is owned by SearchableLedgerDropdown).
    val selectedLedger: LedgerDropdownItem? = null,

    // Report (ledger statement from /reports/api-ledger)
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val statement: LedgerStatement? = null,

    val sessionExpired: Boolean = false,
) {
    /** Apply needs a branch and a ledger chosen, with no request in flight. */
    val canApply: Boolean
        get() = selectedBranch != null && selectedLedger != null && !isReportLoading
}
