package com.example.cashbookbd.ui.requisition

import com.example.cashbookbd.data.repository.RequisitionItemOption
import com.example.cashbookbd.data.repository.RequisitionLine
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class RequisitionFormUiState(
    val notes: String = "",
    // Both default to the branch's transaction (business) date once it loads.
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    // Current (not-yet-added) line entry.
    val selectedItem: RequisitionItemOption? = null,
    val remarks: String = "",
    val day: String = "",
    val qty: String = "",
    val price: String = "",

    val lines: List<RequisitionLine> = emptyList(),

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    /** Σ(day×qty×price) over the pending lines — the Requisition Amount. */
    val total: Double get() = lines.sumOf { it.amount }

    /**
     * Day, qty and price must all be > 0 — the web fails silently on a blank
     * day; requiring it here keeps a line's amount from being 0.
     */
    val canAdd: Boolean
        get() = selectedItem != null &&
            (day.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (price.toDoubleOrNull() ?: 0.0) > 0.0

    val canSave: Boolean
        get() = lines.isNotEmpty() && !isSubmitting
}
