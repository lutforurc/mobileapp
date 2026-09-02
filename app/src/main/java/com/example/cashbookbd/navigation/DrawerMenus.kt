package com.example.cashbookbd.navigation

/**
 * The arrangeable drawer sections, under the SAME ids the web sidebar stores in
 * its preferences record (`SIDEBAR_MENUS`) — that identity is what lets one
 * saved arrangement drive both clients. "subscription" is the one mobile-only
 * tail entry; the one id the web knows and this app does not (customer_dashboard
 * — a portal for the customer themselves, not for the office) simply never
 * renders here, exactly as unknown ids are dropped on the web.
 *
 * Every drawer entry belongs in this list. One that renders but is missing here
 * still obeys an arrangement made on the web, yet cannot be moved or hidden
 * from the phone — the Arrange Menu page draws its rows from this list alone.
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
        DrawerMenuDef("hotel", "Hotel"),
        DrawerMenuDef("asset", "Assets"),
        DrawerMenuDef("products", "Products"),
        DrawerMenuDef("labour_items", "Labour Items"),
        DrawerMenuDef("admin", "Admin"),
        DrawerMenuDef("vr_settings", "VR Settings"),
        DrawerMenuDef("hrm", "HRM"),
        DrawerMenuDef("customer-supplier", "Customers"),
        DrawerMenuDef("al-charts", "Analytics"),
        DrawerMenuDef("subscription", "Subscription"),
    )

    fun titleOf(id: String): String? = all.firstOrNull { it.id == id }?.title
}
