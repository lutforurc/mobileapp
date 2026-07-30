package com.example.cashbookbd.ui.orders

import com.example.cashbookbd.data.repository.AveragePriceReport
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * State for the Average Price report (web `/orders/avg-price`): branch, order
 * and report-type filters, and the fetched [report].
 */
data class AveragePriceUiState(
    // Filter form
    val branches: List<SelectorOption> = emptyList(),
    val selectedBranch: SelectorOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    val selectedOrder: SelectorOption? = null,
    val selectedReportType: SelectorOption = REPORT_TYPES.first(),

    // Report
    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val report: AveragePriceReport? = null,

    val sessionExpired: Boolean = false,
) {
    val canRun: Boolean
        get() = selectedBranch != null && selectedOrder != null && !isReportLoading

    val isEmptyResult: Boolean
        get() = report != null && report.isEmpty

    companion object {
        /**
         * The web's Report Type choices. Type 2 (Sales) currently 500s on the
         * server; it stays offered and the server error is surfaced as-is.
         */
        val REPORT_TYPES = listOf(
            SelectorOption(id = "1", label = "Purchase"),
            SelectorOption(id = "2", label = "Sales"),
        )
    }
}
