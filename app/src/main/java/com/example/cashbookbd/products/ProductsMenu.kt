package com.example.cashbookbd.products

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry in the Products menu, mirroring the web sidebar's "Products" group. */
data class ProductItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/**
 * The Products menu registry — the master-data lists (Brand, Category, Product,
 * Unit). Each is a read-only [com.example.cashbookbd.applist.AppLists] table.
 */
object ProductsMenu {

    val all: List<ProductItem> = listOf(
        ProductItem("brandList", "Brand List", listOf("brand.list")),
        ProductItem("categoryList", "Category List", listOf("category.view")),
        ProductItem("productList", "Product List", listOf("products.view")),
        // The stock alert reports, each on its own web-sidebar permission. The
        // endpoints are scoped to the signed-in user's branch server-side.
        ProductItem("lowStock", "Low Stock", listOf("low.stock")),
        ProductItem("negativeStock", "Negative Stock", listOf("negative.stock")),
        ProductItem("slowMoving", "Slow Moving", listOf("slow.moving")),
        ProductItem("warehouseDifference", "Warehouse Difference", listOf("warehouse.difference")),
        ProductItem("productUnit", "Product Unit", listOf("product.unit")),
    )

    private val byKey: Map<String, ProductItem> = all.associateBy { it.key }

    fun byKey(key: String?): ProductItem? = key?.let { byKey[it] }

    /** True when the user can see the Products parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "products")

    /** Products entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<ProductItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
