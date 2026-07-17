package com.example.cashbookbd.ui.invoice

import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.invoice.model.InvoiceProduct
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class InvoiceFormUiState(
    val title: String = "Invoice",
    val isSupported: Boolean = true,
    val partyLabel: String = "Select Party",
    val amountLabel: String = "Amount",
    val autoFillPrice: Boolean = false,
    val showInvoiceNo: Boolean = false,
    val showInvoiceDate: Boolean = false,

    val party: TxnSelection? = null,

    // Current (not-yet-added) product line entry.
    val selectedProduct: InvoiceProduct? = null,
    val qty: String = "",
    val price: String = "",

    val lines: List<InvoiceLine> = emptyList(),

    val amount: String = "",
    val discount: String = "",
    val notes: String = "",
    val invoiceNo: String = "",
    val invoiceDate: SimpleDate = SimpleDate.today(),

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = lines.sumOf { it.amount }

    val canAddLine: Boolean
        get() = selectedProduct != null &&
            (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (price.toDoubleOrNull() ?: 0.0) > 0.0

    val canSubmit: Boolean
        get() = isSupported &&
            !isSubmitting &&
            party != null &&
            lines.isNotEmpty() &&
            amount.isNotBlank()
}
