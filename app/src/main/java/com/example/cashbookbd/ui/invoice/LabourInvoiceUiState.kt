package com.example.cashbookbd.ui.invoice

import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.ui.invoice.model.LabourItem
import com.example.cashbookbd.ui.invoice.model.LabourLine
import com.example.cashbookbd.ui.reports.model.SimpleDate

/** The Cash ledger's fixed COA level-4 id — its payment amount is forced. */
internal const val LABOUR_CASH_ACCOUNT_ID = "17"

data class LabourInvoiceUiState(
    val supplier: TxnSelection? = null,
    val notes: String = "",
    val billNo: String = "",
    /** Optional — the web form starts with no bill date. */
    val billDate: SimpleDate? = null,

    // Current (not-yet-added) labour item entry.
    val selectedItem: LabourItem? = null,
    val qty: String = "",
    val price: String = "",
    /** When set, "Add" replaces this line instead of appending (the table's edit). */
    val editingIndex: Int? = null,

    val lines: List<LabourLine> = emptyList(),

    val discount: String = "0",
    /** Auto-computed total − discount (2 decimals); editable unless Cash. */
    val paymentAmt: String = "",

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = lines.sumOf { it.amount }

    /**
     * The Cash supplier's payment is forced to the computed amount — the web
     * disables the field when the picked account id is 17.
     */
    val isCashSupplier: Boolean get() = supplier?.id == LABOUR_CASH_ACCOUNT_ID

    val canAddLine: Boolean
        get() = selectedItem != null &&
            (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (price.toDoubleOrNull() ?: 0.0) > 0.0

    val canSubmit: Boolean
        get() = supplier != null &&
            lines.isNotEmpty() &&
            paymentAmt.isNotBlank() &&
            (discount.toDoubleOrNull() ?: 0.0) >= 0.0 &&
            !isSubmitting
}
