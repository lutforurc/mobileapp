package com.example.cashbookbd.ui.invoice.model

/**
 * One labour item from the Construction labour search
 * (`construction/ddl/labour-list?q=`): `value`/`label` plus the category
 * (`label_2`), unit (`label_3`) and purchase price (`label_4`).
 */
data class LabourItem(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    /** Purchase price — pre-fills the entry line's Price (null when unknown). */
    val purchasePrice: Double?,
)

/**
 * One added line of the Labour Invoice. [qty] and [price] keep the typed
 * strings (the web posts its reducer strings verbatim); [amount] is the
 * computed qty × price shown in the Total column.
 */
data class LabourLine(
    val item: LabourItem,
    val qty: String,
    val price: String,
) {
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val priceValue: Double get() = price.toDoubleOrNull() ?: 0.0
    val amount: Double get() = qtyValue * priceValue
}
