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
    /** Every one of these is also required (the web ANDs some sidebar gates). */
    val allOf: List<String> = emptyList(),
    /** Shown only to branches of this business type (web sidebar condition). */
    val businessTypeId: Int? = null,
)

/** The Invoice menu registry and its permission rules. */
object InvoiceMenu {

    /** Routes to the dedicated Combined Invoice screen (see InvoiceHomeScreen). */
    const val COMBINED_KEY = "tradingCombined"

    val all: List<InvoiceItem> = listOf(
        InvoiceItem("purchase", "Purchase", listOf("purchase.create"), supported = true),
        InvoiceItem("purchaseImport", "Purchase Import", listOf("purchase.create"), supported = false),
        InvoiceItem("sales", "Sales", listOf("sales.create"), supported = true),
        InvoiceItem("salesImport", "Sales Import", listOf("sales.create"), supported = false),
        // The web sidebar shows this to Trading (business type 8) branches whose
        // user holds BOTH purchase.create and sales.create.
        InvoiceItem(
            COMBINED_KEY,
            "Combined Invoice",
            listOf("purchase.create", "sales.create"),
            supported = true,
            allOf = listOf("purchase.create", "sales.create"),
            businessTypeId = 8,
        ),
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
    fun visible(permissions: List<Permission>?, businessTypeId: Int? = null): List<InvoiceItem> =
        all.filter { item ->
            Permissions.hasAny(permissions, item.anyOf) &&
                item.allOf.all { Permissions.has(permissions, it) } &&
                (item.businessTypeId == null || item.businessTypeId == businessTypeId)
        }
}
