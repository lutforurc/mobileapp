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

    /** Why the Start/End pair is not a range; null once both are typed and agree. */
    val rangeError: String?
        get() = if (startVoucher.isBlank() || endVoucher.isBlank()) null
        else validateVoucherRange(startVoucher, endVoucher)

    val canSubmit: Boolean
        get() = isSupported && !isSubmitting && when (kind) {
            VrKind.DATE_CHANGE ->
                selectedBranch != null && startVoucher.isNotBlank() && endVoucher.isNotBlank() && rangeError == null
            VrKind.VOUCHER_DELETE, VrKind.INSTALLMENT_DELETE -> voucherNo.isNotBlank()
            null -> false
        }
}

/**
 * A voucher number is `<type>-<YY><MM><sequence>` (10-261000001). The type,
 * year and month come out of the number itself, never the voucher's date —
 * the web's voucherNumber.ts.
 */
private val VOUCHER_NUMBER = Regex("^(\\d+)-(\\d{2})(\\d{2})(\\d{5,})$")

private data class VoucherNumber(val type: String, val year: String, val month: String, val sequence: Long)

private fun parseVoucherNumber(value: String): VoucherNumber? {
    val m = VOUCHER_NUMBER.matchEntire(value.trim()) ?: return null
    val (type, year, month, seq) = m.destructured
    if (month.toInt() !in 1..12) return null
    return VoucherNumber(type, year, month, seq.toLongOrNull() ?: return null)
}

fun validateVoucherRange(startValue: String, endValue: String): String? {
    val start = parseVoucherNumber(startValue) ?: return "Start voucher number is not a valid voucher number."
    val end = parseVoucherNumber(endValue) ?: return "End voucher number is not a valid voucher number."
    return when {
        start.type != end.type -> "Start and End voucher numbers must have the same voucher type."
        start.year != end.year || start.month != end.month ->
            "Start and End voucher numbers must belong to the same month and year."
        end.sequence < start.sequence -> "End voucher number cannot be earlier than the Start voucher number."
        else -> null
    }
}
