package com.example.cashbookbd.customer

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry in the Customers menu, mirroring the web sidebar's "Customers" group. */
data class CustomerItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/** The Customers menu registry and its permission rules. */
object CustomerMenu {

    /**
     * The web sidebar renders its "Customers" link with no permission of its own —
     * it shows for anyone who can see the group at all, so it inherits the group's
     * permissions here. The CoA levels each gate on their own `coa.lN.view`.
     */
    private val SECTION_PERMISSIONS: List<String> = MenuPermissions.map["customer"].orEmpty()

    val all: List<CustomerItem> = listOf(
        CustomerItem("customers", "Customers", SECTION_PERMISSIONS),
        CustomerItem("coaL4", "CoA L4", listOf("coa.l4.view")),
    )

    private val byKey: Map<String, CustomerItem> = all.associateBy { it.key }

    fun byKey(key: String?): CustomerItem? = key?.let { byKey[it] }

    /** True when the user can see the Customers parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "customer")

    /** Customers entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<CustomerItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
