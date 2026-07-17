package com.example.cashbookbd.ui.vrsettings

import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.vrsettings.VrKind

data class VrSettingsFormUiState(
    val title: String = "VR Settings",
    val isSupported: Boolean = true,
    val kind: VrKind? = null,
    val actionLabel: String = "Submit",

    // Delete forms.
    val voucherNo: String = "",
    /** Voucher Delete only: server says the voucher is already deleted — offer force. */
    val requiresConfirmation: Boolean = false,

    // Date-change form.
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    /** "1" Received, "2" Payment, "3" Journal. */
    val voucherType: String = "1",
    val presentDate: SimpleDate = SimpleDate.today(),
    val changeDate: SimpleDate = SimpleDate.today(),
    val startVoucher: String = "",
    val endVoucher: String = "",

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val isDateChange: Boolean get() = kind == VrKind.DATE_CHANGE

    val canSubmit: Boolean
        get() = isSupported && !isSubmitting && when (kind) {
            VrKind.DATE_CHANGE ->
                selectedBranch != null && startVoucher.isNotBlank() && endVoucher.isNotBlank()
            VrKind.VOUCHER_DELETE, VrKind.INSTALLMENT_DELETE -> voucherNo.isNotBlank()
            null -> false
        }
}
