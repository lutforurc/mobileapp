package com.example.cashbookbd.invoice

/**
 * The inventory systems a branch can run (`com_branches.inventory_system_id`,
 * fixed ids ↔ slugs). This — not the business type — is what selects the
 * Purchase/Sales invoice variant, exactly like the web's `PurchaseIndex` /
 * `SalesIndex` switching on `currentBranch.inventory_system_id`:
 *
 *  - Purchase: 2 → Electronics, 3 → Construction, 4 → Trading, anything else
 *    (1/general included) → Construction (the default).
 *  - Sales: 2 → Electronics, 4 → Trading, anything else (1/3 included) → General.
 */
const val GENERAL_INVENTORY_SYSTEM_ID = 1
const val ELECTRONICS_INVENTORY_SYSTEM_ID = 2
const val CONSTRUCTION_INVENTORY_SYSTEM_ID = 3
const val TRADING_INVENTORY_SYSTEM_ID = 4

/**
 * Construction business type. Only affects the dashboard: the web serves
 * business_type 7 its own dashboard (Top Purchase from `dashboard/data` plus the
 * head-office receive panel) while every other type gets the ComputerAccessories
 * layout — see the web's `DashboardIndex`. The invoice variants no longer read
 * the business type.
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
     * The endpoint used when the current branch runs the Electronics inventory
     * system (id 2). When set, such branches also get a per-line serial-number
     * field and send `serial_no` on each product line.
     */
    val electronicsEndpoint: String? = null,
    /**
     * The endpoint used when the current branch runs the Trading inventory
     * system (id 4). Sales leaves this null because General and Trading share
     * one endpoint (`trading/sales/api-store`) — only the extra inputs differ;
     * Purchase sets it, since Trading purchases post to their own store.
     */
    val tradingEndpoint: String? = null,
)

/** Registry of the buildable invoice forms (General/Trading + default Purchase). */
object InvoiceForms {

    val all: List<InvoiceSpec> = listOf(
        InvoiceSpec(
            key = "sales",
            title = "Sales",
            kind = InvoiceKind.SALES,
            // General/Trading/Construction inventory systems; Electronics
            // branches use [electronicsEndpoint] instead (web SalesIndex).
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
            // Construction is the web PurchaseIndex's default — id 3 AND every
            // id without its own entry (1/general included) land here.
            endpoint = "construction/purchase/api-store",
            partyLabel = "Select Supplier",
            amountLabel = "Payment Amount",
            amountKey = "paymentAmt",
            autoFillPrice = true,
            showInvoiceNo = true,
            electronicsEndpoint = "electronics/purchase/store",
            tradingEndpoint = "trading/purchase/api-store",
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
