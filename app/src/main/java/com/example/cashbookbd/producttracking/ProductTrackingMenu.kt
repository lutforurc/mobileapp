package com.example.cashbookbd.producttracking

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Product Tracking menu (web sidebar's new group). */
data class ProductTrackingItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/**
 * The web's Product Tracking group: the three screens used to sit apart — the
 * settings under Admin, the two reports under Reports — so setting a product
 * up and then reading it back meant crossing the sidebar. They are one
 * subject, and now one menu (web db96532).
 */
object ProductTrackingMenu {

    const val SETTINGS_KEY = "productTracking"
    const val STATEMENT_KEY = "productStatement"
    const val SUMMARY_KEY = "productTrackingSummary"

    val all: List<ProductTrackingItem> = listOf(
        ProductTrackingItem(
            SETTINGS_KEY, "Product Tracking",
            listOf("product.tracking.settings.view"),
        ),
        ProductTrackingItem(
            STATEMENT_KEY, "Product Statement",
            listOf("product.tracking.report.view"),
        ),
        ProductTrackingItem(
            SUMMARY_KEY, "Product Receivable / Payable",
            listOf("product.tracking.report.view"),
        ),
    )

    /**
     * Opens on any of the two: settings and reports each stand on their own
     * permission, and holding just one has to be enough to reach the menu.
     */
    val PARENT_PERMISSIONS: List<String> =
        listOf("product.tracking.settings.view", "product.tracking.report.view")

    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS)

    fun visible(permissions: List<Permission>?): List<ProductTrackingItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
