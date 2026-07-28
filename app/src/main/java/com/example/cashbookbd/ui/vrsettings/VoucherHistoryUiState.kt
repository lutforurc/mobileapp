package com.example.cashbookbd.ui.vrsettings

import com.example.cashbookbd.data.repository.VoucherHistoryItem
import com.example.cashbookbd.ui.reports.model.BranchOption

data class VoucherHistoryUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = true,
    val branchesError: String? = null,
    val voucherNo: String = "",
    val isLoading: Boolean = false,
    /** History load failure — shown in the results area with a Retry. */
    val error: String? = null,
    /** null until the first search; empty list means "no history found". */
    val result: List<VoucherHistoryItem>? = null,
    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean
        get() = !isLoading && selectedBranch != null && voucherNo.isNotBlank()
}
