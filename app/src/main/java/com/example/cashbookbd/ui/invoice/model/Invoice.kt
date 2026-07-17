package com.example.cashbookbd.ui.invoice.model

/** One product option from the invoice product search. */
data class InvoiceProduct(
    val id: String,
    val name: String,
    val unit: String,
    /** Purchase price, used to pre-fill a purchase line (null when unknown). */
    val purchasePrice: Double?,
)

/** One added product line on an invoice. */
data class InvoiceLine(
    val product: InvoiceProduct,
    val qty: Double,
    val price: Double,
) {
    val amount: Double get() = qty * price
}
