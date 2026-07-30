package com.example.cashbookbd.ui.orders

import com.example.cashbookbd.data.repository.OrderProductLine
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/** The web's Order Type select: 1 Purchase / 2 Sales / 3 Stock. */
val ORDER_TYPE_OPTIONS = listOf(
    SelectorOption(id = "1", label = "Purchase"),
    SelectorOption(id = "2", label = "Sales"),
    SelectorOption(id = "3", label = "Stock"),
)

/** The web's ORDER_STATUS constant: 1 Active / 2 Inactive. */
val ORDER_STATUS_OPTIONS = listOf(
    SelectorOption(id = "1", label = "Active"),
    SelectorOption(id = "2", label = "Inactive"),
)

/**
 * State of the Create Order form — the web's AddOrder ported. [isMultiProduct]
 * (the branch's `multi_product_order` meta, read from the session settings)
 * decides which product entry is shown: the original single-product fields, or
 * a line editor collecting a pending [lines] batch like the web's grid.
 */
data class AddOrderUiState(
    val isMultiProduct: Boolean = false,

    // Branch dropdown (the user's protected branches; first = own branch).
    val branches: List<SelectorOption> = emptyList(),
    val selectedBranch: SelectorOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    // Header.
    val orderType: SelectorOption = ORDER_TYPE_OPTIONS.first(),
    val orderFor: LedgerDropdownItem? = null,
    val orderNumber: String = "",
    val deliveryLocation: String = "",
    val orderDate: SimpleDate = SimpleDate.today(),
    val lastDeliveryDate: SimpleDate = SimpleDate.today().plusDays(30),
    /** True once the user picked Last Delivery Date, so defaults stop tracking. */
    val lastDeliveryTouched: Boolean = false,
    val referenceOrder: SelectorOption? = null,
    val notes: String = "",
    val status: SelectorOption = ORDER_STATUS_OPTIONS.first(),

    // Single-product mode fields (sent flat, exactly like the original form).
    val product: SelectorOption? = null,
    val orderRate: String = "",
    val totalOrder: String = "",
    val contractQty: String = "",

    // Multi-product mode: the current (not-yet-added) line entry…
    val lineProduct: SelectorOption? = null,
    val lineQty: String = "",
    val lineRate: String = "",
    val lineContractQty: String = "",
    // …and the pending batch shown in the lines table.
    val lines: List<OrderProductLine> = emptyList(),

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** Non-null after a successful save: handed to the Orders list, then pop. */
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /**
     * A reference order must be the opposite type: a purchase references a
     * sales order and vice versa; a stock order ("3") references nothing.
     */
    val oppositeOrderType: String
        get() = when (orderType.id) {
            "1" -> "2"
            "2" -> "1"
            else -> ""
        }

    /** The Reference Order picker only applies to purchase/sales orders. */
    val referenceEnabled: Boolean get() = oppositeOrderType.isNotEmpty()

    /** Order For is required unless the order type is Stock ("3"). */
    val needsOrderFor: Boolean get() = orderType.id != "3"

    /** A line only needs its product — zero (or blank) quantity is a valid order. */
    val canAddLine: Boolean get() = !isSubmitting && lineProduct != null

    /** Batch totals for the lines table footer, like the web grid's tfoot. */
    val totalLineQty: Double get() = lines.sumOf { it.qtyValue }
    val totalLineAmount: Double get() = lines.sumOf { it.amount }

    val canSave: Boolean
        get() = !isSubmitting &&
            selectedBranch != null &&
            orderNumber.isNotBlank() &&
            (!needsOrderFor || orderFor != null) &&
            if (isMultiProduct) lines.isNotEmpty() else product != null

    /** Why Save is disabled — one requirement at a time, like the web's toasts. */
    val saveHint: String?
        get() = when {
            isSubmitting || canSave -> null
            selectedBranch == null -> "Select a branch."
            orderNumber.isBlank() -> "Order Number is required."
            needsOrderFor && orderFor == null -> "Select a customer/supplier in Order For."
            isMultiProduct && lines.isEmpty() -> "Add at least one product."
            !isMultiProduct && product == null -> "Select a product."
            else -> null
        }
}
