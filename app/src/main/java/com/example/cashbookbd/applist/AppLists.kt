package com.example.cashbookbd.applist

/** HTTP method used to fetch a list. */
enum class ListMethod { GET, POST }

/** One column of a list table. [key] supports dot paths for nested fields. */
data class AppListColumn(
    val key: String,
    val label: String,
    val numeric: Boolean = false,
)

/**
 * A read-only list screen: fetch [endpoint] (with [params]) and render the
 * returned rows as a table of [columns]. The row array is located defensively
 * (top-level array, `data.data`, or a paginator's `data.data.data`).
 */
data class AppListSpec(
    val key: String,
    val title: String,
    val endpoint: String,
    val method: ListMethod,
    val params: Map<String, String> = emptyMap(),
    val columns: List<AppListColumn>,
    val anyOf: List<String> = emptyList(),
)

/**
 * Registry of the read-only list screens shared by the VR Settings and Admin
 * sections. Keyed by the same item key their menus use.
 */
object AppLists {

    val all: List<AppListSpec> = listOf(
        // ---- VR Settings ----
        AppListSpec(
            key = "recycleBin",
            title = "Recycle Bin",
            endpoint = "voucher-settings/recyclebin",
            method = ListMethod.POST,
            params = mapOf("page" to "1", "per_page" to "50"),
            columns = listOf(
                AppListColumn("vr_no", "Voucher No"),
                AppListColumn("vr_date", "Date"),
                AppListColumn("coal_name", "Name"),
                AppListColumn("delete_by", "Deleted By"),
                AppListColumn("delete_at", "Deleted At"),
                AppListColumn("debit", "Debit", numeric = true),
                AppListColumn("credit", "Credit", numeric = true),
            ),
            anyOf = listOf("voucher.recycle"),
        ),
        AppListSpec(
            key = "logChanges",
            title = "Log Changes",
            endpoint = "history/log-activities",
            method = ListMethod.POST,
            params = mapOf("page" to "1", "per_page" to "50"),
            columns = listOf(
                AppListColumn("main_transaction.vr_no", "Vr No"),
                AppListColumn("main_transaction.vr_date", "Vr Date"),
                AppListColumn("action", "Action"),
                AppListColumn("created_at", "Action Time"),
                AppListColumn("action_by_user.name", "Action By"),
                AppListColumn("branch.name", "Branch"),
                AppListColumn("status_label", "Status"),
            ),
            anyOf = listOf("voucher.changes"),
        ),

        // ---- Admin ----
        AppListSpec(
            key = "branchList",
            title = "Branch List",
            endpoint = "branch/branch-list",
            method = ListMethod.GET,
            params = mapOf("page" to "1", "per_page" to "100", "search" to ""),
            columns = listOf(
                AppListColumn("name", "Branch Name"),
                AppListColumn("contact_person", "Contact Person"),
                AppListColumn("business_type", "Business Type"),
                AppListColumn("phone", "Phone"),
                AppListColumn("status_label", "Status"),
            ),
            anyOf = listOf("branch.view"),
        ),
        AppListSpec(
            key = "companyList",
            title = "Company List",
            endpoint = "company/company-list",
            method = ListMethod.GET,
            params = mapOf("page" to "1", "per_page" to "100", "search" to ""),
            columns = listOf(
                AppListColumn("name", "Company"),
                AppListColumn("contact_person", "Contact Person"),
                AppListColumn("phone", "Phone"),
                AppListColumn("mobile", "Mobile"),
            ),
            anyOf = listOf("branch.view"),
        ),
        AppListSpec(
            key = "userList",
            title = "User List",
            endpoint = "user/user-list",
            method = ListMethod.GET,
            params = mapOf("page" to "1", "per_page" to "100", "search" to "", "owners_only" to "0"),
            columns = listOf(
                AppListColumn("name", "User Name"),
                AppListColumn("branch", "Branch"),
                AppListColumn("email", "Email"),
                AppListColumn("role_name", "Role"),
            ),
            anyOf = listOf("all.user.view", "user.view"),
        ),
        AppListSpec(
            key = "companyUser",
            title = "Company User",
            endpoint = "user/user-list",
            method = ListMethod.GET,
            params = mapOf("page" to "1", "per_page" to "100", "search" to "", "owners_only" to "1"),
            columns = listOf(
                AppListColumn("name", "User Name"),
                AppListColumn("company", "Company"),
                AppListColumn("branch", "Branch"),
                AppListColumn("email", "Email"),
            ),
            anyOf = listOf("all.user.view", "user.view"),
        ),
        AppListSpec(
            key = "onlineUsers",
            title = "Online Users",
            endpoint = "user/online-users",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "User Name"),
                AppListColumn("branch", "Branch"),
                AppListColumn("email", "Email"),
                AppListColumn("last_seen_at", "Last Seen"),
            ),
            anyOf = listOf("all.user.view", "user.view"),
        ),
        AppListSpec(
            key = "orders",
            title = "Orders",
            endpoint = "invoice/order/list",
            method = ListMethod.GET,
            params = mapOf("page" to "1", "per_page" to "100", "search" to "", "status" to "1"),
            columns = listOf(
                AppListColumn("order_number", "Order No"),
                AppListColumn("order_date", "Date"),
                AppListColumn("order_for", "Order For"),
                AppListColumn("product_name", "Product"),
                AppListColumn("order_rate", "Rate", numeric = true),
                AppListColumn("total_order", "Order Qty", numeric = true),
                AppListColumn("trx_quantity", "Trx Qty", numeric = true),
            ),
            anyOf = listOf("order.view"),
        ),
        AppListSpec(
            key = "roles",
            title = "Roles",
            endpoint = "role/role-list",
            method = ListMethod.GET,
            columns = listOf(AppListColumn("name", "Role")),
            anyOf = listOf("roles.view"),
        ),
    )

    private val byKey: Map<String, AppListSpec> = all.associateBy { it.key }

    fun byKey(key: String?): AppListSpec? = key?.let { byKey[it] }
}
