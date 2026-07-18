package com.example.cashbookbd.invoice

/**
 * The branch business type that gets the "Electronics" (Computer and Accessories)
 * sales form — a separate endpoint and a per-line serial number. Mirrors the web
 * `SalesIndex`, which hardcodes business_type_id 4 for the Electronics form (and 8
 * for Trading, which otherwise shares the General/Trading endpoint).
 */
const val ELECTRONICS_BUSINESS_TYPE_ID = 4

/**
 * The branch business type that gets the "Trading" sales form — vehicle number,
 * purchase/sales order pickers, and per-line warehouse, bag and weight-variance.
 * The endpoint is shared with General (`trading/sales/api-store`); only the extra
 * inputs differ (web `SalesIndex` hardcodes business_type_id 8 for Trading).
 */
const val TRADING_BUSINESS_TYPE_ID = 8

/**
 * Construction business. Only affects the dashboard today: the web serves
 * business_type 7 its own dashboard (Top Purchase from `dashboard/data` plus the
 * head-office receive panel) while every other type gets the ComputerAccessories
 * layout — see the web's `DashboardIndex`.
 */
const val CONSTRUCTION_BUSINESS_TYPE_ID = 7

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
    /** Shows the supplier/original invoice-no field. */
    val showInvoiceNo: Boolean,
    /** Returns send a different body (table_data/supplier_id/netpayment/total). */
    val isReturn: Boolean = false,
    /** Returns show a required original-invoice date field. */
    val showInvoiceDate: Boolean = false,
    /** Body key prefix for a return's invoice number/date ("sales"/"purchase"). */
    val returnPrefix: String? = null,
    /**
     * Sales only: the endpoint used when the current branch is an Electronics
     * (Computer and Accessories) business. When set, such branches also get a
     * per-line serial-number field and send `serial_no` on each product line.
     */
    val electronicsEndpoint: String? = null,
)

/** Registry of the buildable invoice forms (General/Trading + default Purchase). */
object InvoiceForms {

    val all: List<InvoiceSpec> = listOf(
        InvoiceSpec(
            key = "sales",
            title = "Sales",
            kind = InvoiceKind.SALES,
            // General/Trading/etc. businesses; Electronics branches use
            // [electronicsEndpoint] instead (see the web's SalesIndex).
            endpoint = "trading/sales/api-store",
            partyLabel = "Select Customer",
            amountLabel = "Received Amount",
            amountKey = "receivedAmt",
            autoFillPrice = false,
            showInvoiceNo = false,
            electronicsEndpoint = "electronics/sales/store",
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
        InvoiceSpec(
            key = "salesReturn",
            title = "Sales Return",
            kind = InvoiceKind.SALES,
            endpoint = "sales-return/api-store",
            partyLabel = "Select Customer",
            amountLabel = "Received Amount",
            amountKey = "netpayment",
            autoFillPrice = false,
            showInvoiceNo = true,
            isReturn = true,
            showInvoiceDate = true,
            returnPrefix = "sales",
        ),
        InvoiceSpec(
            key = "purchaseReturn",
            title = "Purchase Return",
            kind = InvoiceKind.PURCHASE,
            endpoint = "purchase-return/api-store",
            partyLabel = "Select Supplier",
            amountLabel = "Payment Amount",
            amountKey = "netpayment",
            autoFillPrice = true,
            showInvoiceNo = true,
            isReturn = true,
            showInvoiceDate = true,
            returnPrefix = "purchase",
        ),
    )

    private val byKey: Map<String, InvoiceSpec> = all.associateBy { it.key }

    fun byKey(key: String?): InvoiceSpec? = key?.let { byKey[it] }
}
