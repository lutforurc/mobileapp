package com.example.cashbookbd.invoice

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the Invoice menu, mirroring the web sidebar's "Invoice" group.
 * [supported] is false for entries whose web form has no mobile equivalent yet
 * (file imports, labour, branch transfer) — they still appear but open a
 * "not available yet" screen.
 */
data class InvoiceItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
    val supported: Boolean,
)

/** The Invoice menu registry and its permission rules. */
object InvoiceMenu {

    val all: List<InvoiceItem> = listOf(
        InvoiceItem("purchase", "Purchase", listOf("purchase.create"), supported = true),
        InvoiceItem("purchaseImport", "Purchase Import", listOf("purchase.create"), supported = false),
        InvoiceItem("sales", "Sales", listOf("sales.create"), supported = true),
        InvoiceItem("salesImport", "Sales Import", listOf("sales.create"), supported = false),
        InvoiceItem("purchaseReturn", "Purchase Return", listOf("purchase.create"), supported = true),
        InvoiceItem("salesReturn", "Sales Return", listOf("sales.create"), supported = true),
        InvoiceItem("labourInvoice", "Labour Invoice", listOf("labour.invoice.create"), supported = false),
        InvoiceItem(
            "branchIssue",
            "Branch Issue",
            listOf("branch.transfer.create", "inventory.transfer.create", "product.transfer.create"),
            supported = false,
        ),
    )

    private val byKey: Map<String, InvoiceItem> = all.associateBy { it.key }

    fun byKey(key: String?): InvoiceItem? = key?.let { byKey[it] }

    /** True when the user can see the Invoice parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "invoice") ||
            Permissions.hasAny(permissions, listOf("branch.transfer.create"))

    /** Invoice entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<InvoiceItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
