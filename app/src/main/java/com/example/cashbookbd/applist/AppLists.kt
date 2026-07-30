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
    /**
     * Maps a raw cell value to a display label ("1" -> "Active", "4" -> "Sold").
     * A value with no entry falls through to the normal formatting.
     */
    val valueMap: Map<String, String> = emptyMap(),
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
 * A per-row edit pencil, shown in the trailing "Action" column. Tapping it opens
 * [route] with the row's [idKey] appended.
 *
 * [idKey] defaults to the hashed `branch_id`-style key rather than `id` because
 * the edit and update endpoints resolve a hashed id, while the status toggle
 * posts the raw one — the same row carries both.
 */
data class ListEditAction(
    val route: String,
    val idKey: String,
)

/**
 * A per-row delete bin, shown in the trailing "Action" column. Confirming posts
 * an empty body to `{endpointBase}/{row [idKey]}` — the Real Estate module's
 * POST-style delete endpoints. The server refuses deletes with dependants
 * (e.g. an area that still has projects); its message is surfaced as-is.
 */
data class ListDeleteAction(
    val endpointBase: String,
    val idKey: String = "id",
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
    /** When set, each row gets an edit pencil in the Action column. */
    val editAction: ListEditAction? = null,
    /** When set, each row gets a delete bin (with confirm) in the Action column. */
    val deleteAction: ListDeleteAction? = null,
    /** Pagination key overrides — the check-register endpoint reads `perPage`. */
    val pageParam: String = "page",
    val perPageParam: String = "per_page",
    /**
     * Product List only: while the branch's "Opening ongoing" flag is on, each
     * row gets an opening stock entry (IMEI/qty/rate → `product/update-qty-rate`),
     * mirroring the web's inline columns. Rows must carry `product_id`,
     * `openingbalance` and `purchase` for the dialog to pre-fill.
     */
    val openingStock: Boolean = false,
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
            // Company List is gated on company.view in the web sidebar, matching
            // its Admin menu entry (not branch.view).
            anyOf = listOf("company.view"),
            paginated = true,
        ),

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
            editAction = ListEditAction(route = Routes.BRANCH_EDIT, idKey = "branch_id"),
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
            // Company User is gated on company.user in the web sidebar, matching
            // its Admin menu entry.
            anyOf = listOf("company.user", "user.view"),
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
            // Online Users is gated on online.users in the web sidebar, matching
            // its Admin menu entry.
            anyOf = listOf("online.users", "user.view"),
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
            addAction = ListAddAction(label = "Create Order", route = Routes.ORDER_ADD),
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
                AppListColumn("openingbalance", "Opening", numeric = true),
                AppListColumn("manual_address", "Address"),
                AppListColumn("ledger_page", "Ledger Page"),
                AppListColumn("mobile", "Mobile"),
            ),
            anyOf = MenuPermissions.map["customer"].orEmpty(),
            paginated = true,
            addAction = ListAddAction(label = "Add Customer", route = Routes.CUSTOMER_ADD),
            // Row pencil → set the customer's opening balance / ledger page.
            editAction = ListEditAction(route = Routes.CUSTOMER_EDIT, idKey = "id"),
        ),
        AppListSpec(
            key = "coaL1",
            title = "CoA L1",
            endpoint = "coal1/coal1-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("serial", "Sl"),
                AppListColumn("name", "Chart of Account L1"),
            ),
            anyOf = listOf("coa.l1.view"),
            paginated = true,
        ),
        AppListSpec(
            key = "coaL2",
            title = "CoA L2",
            endpoint = "coal2/coal2-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("serial", "Sl"),
                AppListColumn("name", "Chart of Account L2"),
                AppListColumn("l1_name", "CoA L1"),
            ),
            anyOf = listOf("coa.l2.view"),
            paginated = true,
        ),
        AppListSpec(
            key = "coaL3",
            title = "CoA L3",
            endpoint = "coal3/coal3-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("serial", "Sl"),
                AppListColumn("name", "Chart of Account L3"),
                AppListColumn("l2_name", "CoA L2"),
                AppListColumn("source_name", "Source"),
            ),
            anyOf = listOf("coa.l3.view"),
            paginated = true,
            addAction = ListAddAction(label = "New COA L3", route = Routes.COA_L3_ADD),
            editAction = ListEditAction(route = Routes.COA_L3_EDIT, idKey = "id"),
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

        // ---- Products (master lists) ----
        AppListSpec(
            key = "brandList",
            title = "Brand List",
            endpoint = "product/brand/list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Brand"),
                AppListColumn("contacts", "Contact"),
                AppListColumn("email", "Email"),
                AppListColumn("address", "Address"),
            ),
            anyOf = listOf("brand.list"),
            paginated = true,
            addAction = ListAddAction(label = "New Brand", route = Routes.BRAND_ADD),
        ),
        AppListSpec(
            key = "categoryList",
            title = "Category List",
            endpoint = "category/category-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Category"),
                AppListColumn("products", "Products", numeric = true),
                AppListColumn("description", "Description"),
            ),
            anyOf = listOf("category.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Category", route = Routes.CATEGORY_ADD),
        ),
        AppListSpec(
            key = "productList",
            title = "Product List",
            endpoint = "product/product-list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("name", "Product"),
                AppListColumn("category", "Category"),
                AppListColumn("brand", "Brand"),
                AppListColumn("unit", "Unit"),
                AppListColumn("purchase", "Purchase", numeric = true),
                AppListColumn("sales", "Sales", numeric = true),
            ),
            anyOf = listOf("products.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Product", route = Routes.PRODUCT_ADD),
            openingStock = true,
        ),
        AppListSpec(
            key = "productUnit",
            title = "Product Unit",
            endpoint = "product/unit/list",
            method = ListMethod.GET,
            params = mapOf("search" to ""),
            // Unit aliases: `name` = full name, `short_name` = the short code.
            columns = listOf(
                AppListColumn("name", "Unit Name"),
                AppListColumn("short_name", "Short Name"),
                AppListColumn("description", "Description"),
            ),
            anyOf = listOf("product.unit"),
            paginated = true,
            addAction = ListAddAction(label = "New Unit", route = Routes.UNIT_ADD),
        ),

        // ---- Products (stock alert reports; server-scoped to the user's own
        // branch — the endpoints take no branch param) ----
        AppListSpec(
            key = "lowStock",
            title = "Low Stock",
            endpoint = "product/low-stock",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Product"),
                AppListColumn("category", "Category"),
                AppListColumn("brand", "Brand"),
                AppListColumn("unit", "Unit"),
                AppListColumn("order_level", "Min Stock", numeric = true),
                AppListColumn("stock_in", "Stock In", numeric = true),
                AppListColumn("stock_out", "Stock Out", numeric = true),
                AppListColumn("current_stock", "Current", numeric = true),
            ),
            // Web sidebar gates each stock alert on its own permission.
            anyOf = listOf("low.stock"),
            paginated = true,
        ),
        AppListSpec(
            key = "negativeStock",
            title = "Negative Stock",
            endpoint = "product/negative-stock",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Product"),
                AppListColumn("category", "Category"),
                AppListColumn("brand", "Brand"),
                AppListColumn("unit", "Unit"),
                AppListColumn("stock_in", "Stock In", numeric = true),
                AppListColumn("stock_out", "Stock Out", numeric = true),
                AppListColumn("current_stock", "Current", numeric = true),
            ),
            anyOf = listOf("negative.stock"),
            paginated = true,
        ),
        AppListSpec(
            key = "slowMoving",
            title = "Slow Moving",
            endpoint = "product/slow-moving",
            method = ListMethod.GET,
            // The web's default idle window (90 days).
            params = mapOf("days" to "90"),
            columns = listOf(
                AppListColumn("name", "Product"),
                AppListColumn("category", "Category"),
                AppListColumn("brand", "Brand"),
                AppListColumn("unit", "Unit"),
                AppListColumn("current_stock", "Current", numeric = true),
                AppListColumn("last_movement_date", "Last Movement"),
                AppListColumn("days_without_movement", "Days Idle", numeric = true),
            ),
            anyOf = listOf("slow.moving"),
            paginated = true,
        ),
        AppListSpec(
            key = "warehouseDifference",
            title = "Warehouse Difference",
            endpoint = "product/warehouse-difference",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Product"),
                AppListColumn("category", "Category"),
                AppListColumn("brand", "Brand"),
                AppListColumn("unit", "Unit"),
                AppListColumn("warehouse_count", "Warehouses", numeric = true),
                AppListColumn("min_stock", "Min", numeric = true),
                AppListColumn("max_stock", "Max", numeric = true),
                AppListColumn("stock_difference", "Difference", numeric = true),
            ),
            anyOf = listOf("warehouse.difference"),
            paginated = true,
        ),

        // ---- Real Estate (master data; every delete is refused server-side
        // while dependants exist, and the message is shown verbatim) ----
        AppListSpec(
            key = "reAreas",
            title = "Location",
            endpoint = "real-estate/area/list",
            method = ListMethod.GET,
            // Plain root array — not paginated, no params.
            columns = listOf(
                AppListColumn("name", "Area Name"),
                AppListColumn("branch.name", "Branch"),
                AppListColumn("description", "Description"),
            ),
            anyOf = listOf("real.estate.view"),
            addAction = ListAddAction(label = "New Location", route = Routes.reCrudAdd("reAreas")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reAreas"), idKey = "id"),
            deleteAction = ListDeleteAction(endpointBase = "real-estate/area/delete"),
        ),
        AppListSpec(
            key = "reProjects",
            title = "Projects",
            endpoint = "real-estate/projects/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Project"),
                AppListColumn("area_sqft", "Area (Sft)", numeric = true),
                AppListColumn("area.name", "Location"),
                AppListColumn("area.branch.name", "Branch"),
            ),
            anyOf = listOf("real.estate.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Project", route = Routes.reCrudAdd("reProjects")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reProjects"), idKey = "id"),
            deleteAction = ListDeleteAction(endpointBase = "real-estate/projects/delete"),
        ),
        AppListSpec(
            key = "reBuildings",
            title = "Buildings",
            endpoint = "real-estate/buildings/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Building"),
                AppListColumn("project.name", "Project"),
                AppListColumn("project.area.name", "Location"),
                AppListColumn("floors_count", "Floors", numeric = true),
            ),
            anyOf = listOf("real.estate.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Building", route = Routes.reCrudAdd("reBuildings")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reBuildings"), idKey = "id"),
            deleteAction = ListDeleteAction(endpointBase = "real-estate/buildings/delete"),
        ),
        AppListSpec(
            key = "reFloors",
            title = "Floor List",
            endpoint = "real-estate/flats/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("floor_no", "Floor"),
                AppListColumn("flat_name", "Flat Name"),
                AppListColumn("building.name", "Building"),
                AppListColumn("building.project.area.name", "Location"),
                AppListColumn("total_units", "Units", numeric = true),
                AppListColumn(
                    "status", "Status",
                    valueMap = mapOf("1" to "Active", "0" to "Inactive"),
                ),
            ),
            anyOf = listOf("real.estate.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Floor", route = Routes.reCrudAdd("reFloors")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reFloors"), idKey = "id"),
            deleteAction = ListDeleteAction(endpointBase = "real-estate/flats/delete"),
        ),
        AppListSpec(
            key = "reUnits",
            title = "Unit List",
            endpoint = "real-estate/units/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("unit_no", "Unit No"),
                AppListColumn(
                    "unit_type", "Type",
                    valueMap = mapOf("unit" to "Unit", "parking" to "Parking"),
                ),
                AppListColumn("flat.flat_name", "Flat"),
                AppListColumn("flat.building.name", "Building"),
                AppListColumn("size_sqft", "Size (Sft)", numeric = true),
                AppListColumn("sale_price", "Rate", numeric = true),
                AppListColumn(
                    "status", "Status",
                    valueMap = mapOf(
                        "0" to "Inactive",
                        "1" to "Available",
                        "2" to "Under Dev",
                        "3" to "Completed",
                        "4" to "Sold",
                    ),
                ),
            ),
            anyOf = listOf("real.estate.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Unit", route = Routes.reCrudAdd("reUnits")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reUnits"), idKey = "id"),
            // The server refuses sold/allocated units with a clear message.
            deleteAction = ListDeleteAction(endpointBase = "real-estate/units/delete"),
        ),
        AppListSpec(
            key = "reChargeTypes",
            title = "Charges",
            endpoint = "real-estate/units/unit-charge-types/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Charge Type"),
                AppListColumn(
                    "effect", "Effect",
                    valueMap = mapOf("+" to "(+) Add", "-" to "(-) Subtract"),
                ),
                AppListColumn("notes", "Notes"),
                AppListColumn(
                    "is_active", "Status",
                    valueMap = mapOf("true" to "Active", "false" to "Inactive"),
                ),
                AppListColumn("sort_order", "Sort", numeric = true),
            ),
            anyOf = listOf("real.estate.view"),
            paginated = true,
            addAction = ListAddAction(label = "New Charge Type", route = Routes.reCrudAdd("reChargeTypes")),
            editAction = ListEditAction(route = Routes.reCrudEditBase("reChargeTypes"), idKey = "id"),
            // The backend has no charge-type delete endpoint.
        ),
        AppListSpec(
            key = "reCheckRegister",
            title = "Check Register",
            endpoint = "real-estate/unit-sale/payments-list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("payment_date", "Date"),
                AppListColumn("receipt_no", "Receipt"),
                AppListColumn("booking.payload.unit.label", "Unit"),
                AppListColumn("booking.payload.customer.label", "Customer"),
                AppListColumn("payment_mode", "Mode"),
                AppListColumn("reference_no", "Ref No"),
                AppListColumn("amount", "Amount", numeric = true),
                AppListColumn("status", "Status"),
                AppListColumn("cheque_collect_status", "Cheque"),
            ),
            anyOf = listOf("check.register.view"),
            paginated = true,
            // This endpoint reads camelCase `perPage` (and ignores per_page).
            perPageParam = "perPage",
            addAction = ListAddAction(label = "Add Payment", route = Routes.UNIT_PAYMENT_ADD),
            editAction = ListEditAction(route = Routes.UNIT_PAYMENT_EDIT, idKey = "id"),
        ),

        // ---- Requisition ----
        AppListSpec(
            key = "requisitions",
            title = "Requisitions",
            endpoint = "requisition/list",
            method = ListMethod.POST,
            params = mapOf("search" to ""),
            columns = listOf(
                AppListColumn("req_no", "Req No"),
                AppListColumn("vr_date", "Req Date"),
                AppListColumn("requisition_start_date", "Start"),
                AppListColumn("requisition_end_date", "End"),
                AppListColumn("project", "Project"),
                AppListColumn("notes", "Note"),
                AppListColumn("req_total", "Req Amount", numeric = true),
                AppListColumn("approved_total", "Approved", numeric = true),
            ),
            anyOf = listOf("requisition.view"),
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

        // ---- HRM ----
        AppListSpec(
            key = "hrmEmployees",
            title = "Employees",
            endpoint = "hrms/employees",
            method = ListMethod.GET,
            // No Sl/Sal. Sl columns — the table's own # column already numbers
            // rows, and the duplicates crowded the phone screen.
            columns = listOf(
                AppListColumn("name", "Employee Name"),
                AppListColumn("designation_name", "Designation"),
                AppListColumn("mobile", "Mobile"),
                AppListColumn("branch_name", "Project"),
            ),
            anyOf = listOf("employee.view"),
            paginated = true,
            statusToggle = ListStatusToggle(endpoint = "hrms/employee/status"),
            addAction = ListAddAction(label = "Add Employee", route = Routes.EMPLOYEE_ADD),
            // The employee edit/update endpoints resolve the RAW id (unlike
            // branches, whose edit route takes the hashed key).
            editAction = ListEditAction(route = Routes.EMPLOYEE_EDIT, idKey = "id"),
        ),
        AppListSpec(
            key = "hrmDesignationLevels",
            title = "Designation Levels",
            endpoint = "hrms/designation-levels/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("serial_no", "Sl"),
                AppListColumn("name", "Level Name"),
                AppListColumn("description", "Description"),
            ),
            anyOf = listOf("employee.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Level", route = Routes.hrmCrudAdd("hrmDesignationLevels")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmDesignationLevels"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmDesignations",
            title = "Designations",
            endpoint = "hrms/designations/list",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("serial_no", "Sl"),
                AppListColumn("name", "Designation"),
                AppListColumn("level_name", "Level"),
                AppListColumn("post_sequence", "Sequence"),
                AppListColumn("description", "Description"),
            ),
            anyOf = listOf("employee.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Designation", route = Routes.hrmCrudAdd("hrmDesignations")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmDesignations"), idKey = "id"),
        ),

        // ---- HRM: Attendance Setup (read-only views of the web's setup tabs) ----
        AppListSpec(
            key = "hrmShifts",
            title = "Shifts",
            endpoint = "hrms/attendance/shifts",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Shift"),
                AppListColumn("code", "Code"),
                AppListColumn("start_time", "Start"),
                AppListColumn("end_time", "End"),
                AppListColumn("grace_minutes", "Grace Min"),
            ),
            anyOf = listOf("attendance.view", "employee.view", "shift.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Shift", route = Routes.hrmCrudAdd("hrmShifts")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmShifts"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmPolicies",
            title = "Attendance Policies",
            endpoint = "hrms/attendance/policies",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Policy"),
                AppListColumn("employment_type", "Type"),
                AppListColumn("shift_name", "Shift"),
                AppListColumn("standard_work_minutes", "Work Min"),
                AppListColumn("overtime_enabled", "OT"),
            ),
            anyOf = listOf("attendance.view", "employee.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Policy", route = Routes.hrmCrudAdd("hrmPolicies")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmPolicies"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmRosters",
            title = "Shift Rosters",
            endpoint = "hrms/attendance/shift-rosters",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("duty_date", "Date"),
                AppListColumn("employee_name", "Employee"),
                AppListColumn("shift_name", "Shift"),
                AppListColumn("branch_name", "Branch"),
                AppListColumn("status", "Status"),
            ),
            anyOf = listOf("attendance.view", "employee.view", "shift.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Roster", route = Routes.hrmCrudAdd("hrmRosters")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmRosters"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmWeeklyHolidays",
            title = "Weekly Holidays",
            endpoint = "hrms/attendance/weekly-holidays",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("day_of_week", "Day"),
                AppListColumn("is_enabled", "Enabled"),
                AppListColumn("effective_from", "From"),
                AppListColumn("effective_to", "To"),
                AppListColumn("remarks", "Remarks"),
            ),
            anyOf = listOf("attendance.view", "employee.view", "holiday.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Weekly Holiday", route = Routes.hrmCrudAdd("hrmWeeklyHolidays")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmWeeklyHolidays"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmHolidaysList",
            title = "Holidays",
            endpoint = "hrms/attendance/holidays",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("holiday_date", "Date"),
                AppListColumn("holiday_name", "Holiday"),
                AppListColumn("holiday_type", "Type"),
                AppListColumn("is_paid", "Paid"),
                AppListColumn("remarks", "Remarks"),
            ),
            anyOf = listOf("attendance.view", "employee.view", "holiday.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Holiday", route = Routes.hrmCrudAdd("hrmHolidaysList")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmHolidaysList"), idKey = "id"),
        ),
        AppListSpec(
            key = "hrmLeaveTypes",
            title = "Leave Types",
            endpoint = "hrms/attendance/leave-types",
            method = ListMethod.GET,
            columns = listOf(
                AppListColumn("name", "Leave Type"),
                AppListColumn("code", "Code"),
                AppListColumn("yearly_quota", "Quota", numeric = true),
                AppListColumn("is_paid", "Paid"),
            ),
            anyOf = listOf("attendance.view", "employee.view", "leave.view"),
            paginated = true,
            addAction = ListAddAction(label = "Add Leave Type", route = Routes.hrmCrudAdd("hrmLeaveTypes")),
            editAction = ListEditAction(route = Routes.hrmCrudEditBase("hrmLeaveTypes"), idKey = "id"),
        ),
    )

    private val byKey: Map<String, AppListSpec> = all.associateBy { it.key }

    fun byKey(key: String?): AppListSpec? = key?.let { byKey[it] }
}
