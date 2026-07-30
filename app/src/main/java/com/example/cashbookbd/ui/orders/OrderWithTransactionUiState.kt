package com.example.cashbookbd.ui.orders

import com.example.cashbookbd.data.repository.OrderTransactionReport
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * State for the Order With Transaction report (web `/orders/with-transaction`):
 * the branch + order filter, and the fetched [report].
 */
data class OrderWithTransactionUiState(
    // Filter form
    val branches: List<SelectorOption> = emptyList(),
    val selectedBranch: SelectorOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    val selectedOrder: SelectorOption? = null,

    // Report
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val report: OrderTransactionReport? = null,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = selectedBranch != null && selectedOrder != null && !isReportLoading
}
