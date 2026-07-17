package com.example.cashbookbd.invoice

/** Sales vs Purchase — chooses body keys and price behaviour. */
enum class InvoiceKind { SALES, PURCHASE }

/**
 * A product-line invoice entry form (Sales / Purchase). The date, cash/party
 * contra legs and voucher number are all derived server-side; the client sends
 * the party account, product lines, paid/received amount, discount and notes.
 */
data class InvoiceSpec(
    val key: String,
    val title: String,
    val kind: InvoiceKind,
    val endpoint: String,
    val partyLabel: String,
    val amountLabel: String,
    /** Body key for the cash amount: `receivedAmt` (sales) / `paymentAmt` (purchase). */
    val amountKey: String,
    /** Purchase auto-fills each line's price from the product's purchase price. */
    val autoFillPrice: Boolean,
    /** Purchase shows an optional supplier invoice-no field. */
    val showInvoiceNo: Boolean,
)

/** Registry of the buildable invoice forms (General/Trading + default Purchase). */
object InvoiceForms {

    val all: List<InvoiceSpec> = listOf(
        InvoiceSpec(
            key = "sales",
            title = "Sales",
            kind = InvoiceKind.SALES,
            endpoint = "trading/sales/api-store",
            partyLabel = "Select Customer",
            amountLabel = "Received Amount",
            amountKey = "receivedAmt",
            autoFillPrice = false,
            showInvoiceNo = false,
        ),
        InvoiceSpec(
            key = "purchase",
            title = "Purchase",
            kind = InvoiceKind.PURCHASE,
            endpoint = "construction/purchase/api-store",
            partyLabel = "Select Supplier",
            amountLabel = "Payment Amount",
            amountKey = "paymentAmt",
            autoFillPrice = true,
            showInvoiceNo = true,
        ),
    )

    private val byKey: Map<String, InvoiceSpec> = all.associateBy { it.key }

    fun byKey(key: String?): InvoiceSpec? = key?.let { byKey[it] }
}
