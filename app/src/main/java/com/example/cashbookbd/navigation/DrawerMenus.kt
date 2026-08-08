package com.example.cashbookbd.navigation

/**
 * The arrangeable drawer sections, under the SAME ids the web sidebar stores in
 * its preferences record (`SIDEBAR_MENUS`) — that identity is what lets one
 * saved arrangement drive both clients. "subscription" is the one mobile-only
 * tail entry; ids the web knows and mobile doesn't (product_tracking,
 * customer_dashboard) simply never render here, exactly as unknown ids are
 * dropped on the web.
 */
data class DrawerMenuDef(val id: String, val title: String)

object DrawerMenus {

    val all: List<DrawerMenuDef> = listOf(
        DrawerMenuDef("dashboard", "Dashboard"),
        DrawerMenuDef("reseller", "Reseller Dashboard"),
        DrawerMenuDef("transaction", "Transaction"),
        DrawerMenuDef("invoice", "Invoice"),
        DrawerMenuDef("branch-transfer", "Branch Transfer"),
        DrawerMenuDef("reports", "Reports"),
        DrawerMenuDef("product_tracking", "Product Tracking"),
        DrawerMenuDef("requisition", "Requisition"),
        DrawerMenuDef("real-estate", "Real Estate"),
        DrawerMenuDef("products", "Products"),
        DrawerMenuDef("admin", "Admin"),
        DrawerMenuDef("vr_settings", "VR Settings"),
        DrawerMenuDef("hrm", "HRM"),
        DrawerMenuDef("customer-supplier", "Customers"),
        DrawerMenuDef("al-charts", "Analytics"),
        DrawerMenuDef("subscription", "Subscription"),
    )

    fun titleOf(id: String): String? = all.firstOrNull { it.id == id }?.title
}
