package com.example.cashbookbd.production

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Production menu (the web sidebar's Manufacturing group). */
data class ProductionItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val anyOf: List<String>,
)

/**
 * The Production (Manufacturing) menu. The web keeps nine entries here; this
 * first slice carries the two read-only reports that can be built without the
 * entry form — Production Register and its Batch Costing detail. The menu only
 * lists what the app can actually open, so no row is a dead end.
 *
 * The menu also requires the branch's `manufactur_control` switch; that check
 * lives beside the permission check in [com.example.cashbookbd.navigation.AuthenticatedShell].
 */
object ProductionMenu {

    const val REGISTER_KEY = "productionRegister"

    /** Reached from a Register row rather than listed as its own menu entry. */
    const val COSTING_KEY = "productionCosting"

    const val ROUTE_HOME = "production/home"
    const val ROUTE_REGISTER = "production/register"
    const val ROUTE_COSTING = "production/costing/{batchId}"

    fun costing(batchId: Long): String = "production/costing/$batchId"

    val all: List<ProductionItem> = listOf(
        ProductionItem(
            key = REGISTER_KEY,
            title = "Production Register",
            subtitle = "Batch costs, true gross profit and recipe variance",
            anyOf = listOf("production.report.view"),
        ),
    )

    /**
     * Any Production key opens the section — the web's own menuPermissions
     * list. The branch's manufacture switch is checked separately.
     */
    val PARENT_PERMISSIONS: List<String> = listOf(
        "production.create",
        "production.sales.return.create",
        "production.view",
        "production.report.view",
    )

    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS)

    fun visible(permissions: List<Permission>?): List<ProductionItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
