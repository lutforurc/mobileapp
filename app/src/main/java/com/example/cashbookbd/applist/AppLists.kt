package com.example.cashbookbd.applist

import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.MenuPermissions

/** HTTP method used to fetch a list. */
enum class ListMethod { GET, POST }

/** One column of a list table. [key] supports dot paths for nested fields. */
data class AppListColumn(
    val key: String,
    val label: String,
    val numeric: Boolean = false,
)

/**
 * The list toolbar's "+ Add" button: [label] opens [route]. Declaring it here
 * keeps the toolbar config-driven — no per-list branching in the screen.
 */
data class ListAddAction(
    val label: String,
    val route: String,
)

/**
 * A per-row status switch, shown in a trailing "Action" column. Flipping it posts
 * `{id: <row [idKey]>, status: 0|1}` to [endpoint], mirroring the web list's
 * Action toggle.
 */
data class ListStatusToggle(
    val endpoint: String,
    /** Row field holding the id the server expects back. */
    val idKey: String = "id",
    /** Row field holding the current status (1 = on). */
    val statusKey: String = "status",
)

/**
 * A list screen: fetch [endpoint] (with [params]) and render the returned rows as
 * a table of [columns]. The row array is located defensively (top-level array,
 * `data.data`, or a paginator's `data.data.data`). Read-only unless it declares a
 * [statusToggle].
 */
data class AppListSpec(
    val key: String,
    val title: String,
    val endpoint: String,
    val method: ListMethod,
    val params: Map<String, String> = emptyMap(),
    val columns: List<AppListColumn>,
    val anyOf: List<String> = emptyList(),
    /** Server-side paginated (sends `page`/`per_page`, response is a paginator). */
    val paginated: Boolean = false,
    val perPage: Int = 25,
    /** When set, each row gets an Action column with a status switch. */
    val statusToggle: ListStatusToggle? = null,
    /** When set, the toolbar shows a "+ Add" button opening the create screen. */
    val addAction: ListAddAction? = null,
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
            paginated = true,
        ),
        AppListSpec(
            key = "logChanges",
            title = "Log Changes",
            endpoint = "history/log-activities",
            method = ListMethod.POST,
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
            paginated = true,
        ),

        // ---- Admin ----
        AppListSpec(
            key = "branchList",
            title = "Branch List",
            endpoint = "branch/branch-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Branch Name"),
                AppListColumn("contact_person", "Contact Person"),
                AppListColumn("business_type", "Business Type"),
                AppListColumn("phone", "Phone"),
                // No Status column — the Action toggle already shows it.
            ),
            anyOf = listOf("branch.view"),
            paginated = true,
            statusToggle = ListStatusToggle(endpoint = "branch/branch-status"),
            addAction = ListAddAction(label = "Add Branch", route = Routes.BRANCH_ADD),
        ),
        AppListSpec(
            key = "companyList",
            title = "Company List",
            endpoint = "company/company-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Company"),
                AppListColumn("contact_person", "Contact Person"),
                AppListColumn("phone", "Phone"),
                AppListColumn("mobile", "Mobile"),
            ),
            anyOf = listOf("branch.view"),
            paginated = true,
        ),
        AppListSpec(
            key = "userList",
            title = "User List",
            endpoint = "user/user-list",
            method = ListMethod.GET,
            params = mapOf("search" to "", "owners_only" to "0"),
            columns = listOf(
                AppListColumn("name", "User Name"),
                AppListColumn("branch", "Branch"),
                AppListColumn("email", "Email"),
                AppListColumn("role_name", "Role"),
            ),
            anyOf = listOf("all.user.view", "user.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add User", route = Routes.USER_ADD),
        ),
        AppListSpec(
            key = "companyUser",
            title = "Company User",
            endpoint = "user/user-list",
            method = ListMethod.GET,
            params = mapOf("search" to "", "owners_only" to "1"),
            columns = listOf(
                AppListColumn("name", "User Name"),
                AppListColumn("company", "Company"),
                AppListColumn("branch", "Branch"),
                AppListColumn("email", "Email"),
            ),
            anyOf = listOf("all.user.view", "user.view"),
            paginated = true,
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
            params = mapOf("search" to "", "status" to "1"),
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
            paginated = true,
        ),
        AppListSpec(
            key = "roles",
            title = "Roles",
            endpoint = "role/role-list",
            method = ListMethod.GET,
            columns = listOf(AppListColumn("name", "Role")),
            anyOf = listOf("roles.view"),
        ),

        // ---- Customers ----
        AppListSpec(
            key = "customers",
            title = "Customers",
            endpoint = "contact/details",
            method = ListMethod.POST,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Name"),
                AppListColumn("national_id", "National ID"),
                AppListColumn("manual_address", "Address"),
                AppListColumn("ledger_page", "Ledger Page"),
                AppListColumn("mobile", "Mobile"),
            ),
            anyOf = MenuPermissions.map["customer"].orEmpty(),
            paginated = true,
        ),
        AppListSpec(
            key = "coaL4",
            title = "CoA L4",
            endpoint = "coal4/coal4-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Chart of Account L4"),
                AppListColumn("l3_name", "CoA L3"),
                AppListColumn("l2_name", "CoA L2"),
            ),
            anyOf = listOf("coa.l4.view"),
            paginated = true,
        ),

        // ---- Subscription ----
        AppListSpec(
            key = "subscriptionBilling",
            title = "Billing History",
            endpoint = "subscription/payments",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("paid_at", "Date"),
                AppListColumn("plan_name", "Plan"),
                AppListColumn("payment_method", "Method"),
                AppListColumn("amount", "Amount", numeric = true),
                AppListColumn("billing_months", "Months", numeric = true),
                AppListColumn("transaction_id", "Transaction ID"),
                AppListColumn("payment_status", "Status"),
            ),
        ),
        AppListSpec(
            key = "subscriptionAdminPlans",
            title = "Subscription Plans",
            endpoint = "admin/subscription/plans",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Plan"),
                AppListColumn("billing_interval", "Billing"),
                AppListColumn("price", "Price", numeric = true),
                AppListColumn("trial_days", "Trial", numeric = true),
                AppListColumn("max_users", "Max Users", numeric = true),
                AppListColumn("status_label", "Status"),
            ),
            anyOf = listOf("subscription.plans"),
        ),
    )

    private val byKey: Map<String, AppListSpec> = all.associateBy { it.key }

    fun byKey(key: String?): AppListSpec? = key?.let { byKey[it] }
}
