package com.example.cashbookbd.ui.admin

import com.example.cashbookbd.admin.AdminKind
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class AdminFormUiState(
    val title: String = "Admin",
    val isSupported: Boolean = true,
    val kind: AdminKind? = null,
    val actionLabel: String = "Submit",

    // Day Close.
    val currentDate: SimpleDate = SimpleDate.today(),
    val nextDate: SimpleDate = SimpleDate.today(),

    // Voucher Approval (date range).
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    // Approval Remove / Change Voucher Type.
    val voucherNo: String = "",

    // Change Voucher Type.
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    val voucherTypes: List<SelectorOption> = emptyList(),
    val selectedType: SelectorOption? = null,
    val isTypesLoading: Boolean = false,

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isSupported && !isSubmitting && when (kind) {
            AdminKind.DAY_CLOSE, AdminKind.VOUCHER_APPROVAL -> true
            AdminKind.APPROVAL_REMOVE -> voucherNo.isNotBlank()
            AdminKind.CHANGE_VOUCHER_TYPE ->
                selectedBranch != null && voucherNo.isNotBlank() && selectedType != null
            null -> false
        }
}
