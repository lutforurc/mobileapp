package com.example.cashbookbd.ui.invoice.model

/**
 * One order from the Trading invoice's purchase/sales order search
 * (`invoice/order/search`). A selected Sales Order auto-fills the entry line from
 * its customer/product/qty/rate; a Purchase Order only records its id and number.
 */
data class OrderOption(
    val id: String,
    val orderNumber: String,
    val customerName: String,
    val productName: String,
    val rate: Double?,
    val remainingQty: Double?,
)

/** One product option from the invoice product search. */
data class InvoiceProduct(
    val id: String,
    val name: String,
    val unit: String,
    /** Purchase price, used to pre-fill a purchase line (null when unknown). */
    val purchasePrice: Double?,
    /** Sale price — pre-fills the Combined Invoice's sales rate (null when unknown). */
    val salesPrice: Double? = null,
)

/** One added line of the Combined (purchase + sales) invoice. */
data class CombinedLine(
    val product: InvoiceProduct,
    val qty: Double,
    val purchasePrice: Double,
    val salesPrice: Double,
    val bag: String = "",
    val variance: String = "",
    /** Weight-variance direction code: "" none, "+" increase, "-" decrease. */
    val varianceType: String = "",
) {
    val purchaseTotal: Double get() = qty * purchasePrice
    val salesTotal: Double get() = qty * salesPrice

    /** Variance-adjusted quantity — the basis of the web's profit figure. */
    val adjustedQty: Double
        get() {
            val v = variance.toDoubleOrNull() ?: 0.0
            return when (varianceType) {
                "+" -> qty + v
                "-" -> qty - v
                else -> qty
            }
        }
}

/**
 * An Electronics sale's installment plan (only when the customer isn't Cash).
 * [startDate]/[earlyPaymentDate] are optional — the server defaults the start to
 * next month. Mirrors the web's `installmentData`.
 */
data class InstallmentInput(
    val amount: Double,
    val numberOfInstallments: Int,
    val startDate: String,
    val isEarlyPayment: Boolean,
    val earlyDiscount: Double,
    val earlyPaymentDate: String,
)

/** One added product line on an invoice. */
data class InvoiceLine(
    val product: InvoiceProduct,
    val qty: Double,
    val price: Double,
    /** IMEI/serial, Electronics sales only; blank otherwise. */
    val serialNo: String = "",
    // Trading sales only; blank/empty otherwise.
    val warehouseId: String = "",
    val warehouseName: String = "",
    val bag: String = "",
    val variance: String = "",
    /** Weight-variance direction code: "" none, "+" increase, "-" decrease. */
    val varianceType: String = "",
) {
    val amount: Double get() = qty * price
}
