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

    const val RECIPE_KEY = "productionRecipe"

    const val SCHEME_KEY = "productionScheme"

    const val ENTRY_KEY = "productionEntry"
    const val SALES_KEY = "productionSales"
    const val PURCHASE_KEY = "productionPurchase"
    const val SALES_RETURN_KEY = "productionSalesReturn"
    const val FREE_GOODS_KEY = "productionFreeGoods"
    const val EXPIRY_KEY = "productionExpiry"

    /** Reached from a Register row rather than listed as its own menu entry. */
    const val COSTING_KEY = "productionCosting"

    const val ROUTE_HOME = "production/home"
    const val ROUTE_REGISTER = "production/register"
    const val ROUTE_RECIPE = "production/recipe"
    const val ROUTE_SCHEME = "production/scheme"
    const val ROUTE_ENTRY = "production/entry"
    const val ROUTE_SALES = "production/sales"
    const val ROUTE_PURCHASE = "production/purchase"
    const val ROUTE_SALES_RETURN = "production/sales-return"
    const val ROUTE_FREE_GOODS = "production/free-goods"
    const val ROUTE_EXPIRY = "production/expiry"
    const val ROUTE_COSTING = "production/costing/{batchId}"

    fun costing(batchId: Long): String = "production/costing/$batchId"

    val all: List<ProductionItem> = listOf(
        ProductionItem(
            key = ENTRY_KEY,
            title = "Production Entry",
            subtitle = "A batch of materials turned into finished products",
            anyOf = listOf("production.create"),
        ),
        ProductionItem(
            key = SALES_KEY,
            title = "Sales",
            subtitle = "Sell finished goods, with the scheme's free rows",
            anyOf = listOf("production.create"),
        ),
        ProductionItem(
            key = PURCHASE_KEY,
            title = "Purchase",
            subtitle = "Buy materials and finished goods, free rows included",
            anyOf = listOf("production.create"),
        ),
        ProductionItem(
            key = SALES_RETURN_KEY,
            title = "Sales Return",
            subtitle = "A return against the original production sale",
            anyOf = listOf("production.sales.return.create"),
        ),
        ProductionItem(
            key = SCHEME_KEY,
            title = "Scheme",
            subtitle = "Buy X get Y rules the sales form asks",
            anyOf = listOf("production.create"),
        ),
        ProductionItem(
            key = RECIPE_KEY,
            title = "Recipe",
            subtitle = "The standard materials for one output product",
            anyOf = listOf("production.view"),
        ),
        ProductionItem(
            key = REGISTER_KEY,
            title = "Register",
            subtitle = "Batch costs, true gross profit and recipe variance",
            anyOf = listOf("production.report.view"),
        ),
        ProductionItem(
            key = FREE_GOODS_KEY,
            title = "Free Goods",
            subtitle = "What was given away in the period, at what it cost",
            anyOf = listOf("production.report.view"),
        ),
        ProductionItem(
            key = EXPIRY_KEY,
            title = "Near Expiry",
            subtitle = "Batches expired or about to expire, and what is on hand",
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
