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
