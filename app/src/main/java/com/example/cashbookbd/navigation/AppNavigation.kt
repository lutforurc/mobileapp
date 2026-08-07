package com.example.cashbookbd.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.hrm.HrmCrudForms
import com.example.cashbookbd.hrm.HrmMenu
import com.example.cashbookbd.products.ProductsMenu
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.common.PermissionGate
import com.example.cashbookbd.ui.dashboard.DashboardScreen
import com.example.cashbookbd.ui.reseller.ResellerDashboardScreen
import com.example.cashbookbd.ui.login.LoginScreen
import com.example.cashbookbd.ui.register.RegisterScreen
import com.example.cashbookbd.ui.reports.BalanceSheetReportScreen
import com.example.cashbookbd.ui.reports.BankBookScreen
import com.example.cashbookbd.ui.reports.CashBankScreen
import com.example.cashbookbd.ui.reports.CashBookScreen
import com.example.cashbookbd.ui.reports.DueInstallmentsScreen
import com.example.cashbookbd.ui.reports.DueListScreen
import com.example.cashbookbd.ui.reports.GenericReportScreen
import com.example.cashbookbd.ui.reports.LedgerScreen
import com.example.cashbookbd.ui.reports.ProfitLossReportScreen
import com.example.cashbookbd.ui.reports.ReportsHomeScreen
import com.example.cashbookbd.ui.reports.TrialBalanceScreen
import com.example.cashbookbd.ui.account.MyDevicesScreen
import com.example.cashbookbd.ui.admin.AdminFormScreen
import com.example.cashbookbd.ui.admin.AdminHomeScreen
import com.example.cashbookbd.ui.admin.HighlightRulesScreen
import com.example.cashbookbd.ui.branch.AddBranchScreen
import com.example.cashbookbd.ui.customer.AddCustomerScreen
import com.example.cashbookbd.ui.customer.CustomerHomeScreen
import com.example.cashbookbd.ui.customer.CustomerListScreen
import com.example.cashbookbd.ui.customer.EditCustomerScreen
import com.example.cashbookbd.ui.products.AddBrandScreen
import com.example.cashbookbd.ui.products.AddCategoryScreen
import com.example.cashbookbd.ui.products.AddProductScreen
import com.example.cashbookbd.ui.products.AddUnitScreen
import com.example.cashbookbd.ui.products.ProductsHomeScreen
import com.example.cashbookbd.ui.subscription.MyPlanScreen
import com.example.cashbookbd.ui.subscription.PricingScreen
import com.example.cashbookbd.ui.subscription.SubscriptionHomeScreen
import com.example.cashbookbd.ui.roles.AddRoleScreen
import com.example.cashbookbd.ui.roles.RolePermissionScreen
import com.example.cashbookbd.ui.user.AddUserScreen
import com.example.cashbookbd.ui.user.EditUserScreen
import com.example.cashbookbd.ui.user.UserListScreen
import com.example.cashbookbd.ui.hrm.EmployeeFormScreen
import com.example.cashbookbd.ui.hrm.HrmCrudFormScreen
import com.example.cashbookbd.ui.hrm.HrmFormScreen
import com.example.cashbookbd.ui.hrm.HrmHomeScreen
import com.example.cashbookbd.ui.invoice.CombinedInvoiceScreen
import com.example.cashbookbd.ui.invoice.InvoiceFormScreen
import com.example.cashbookbd.ui.invoice.InvoiceHomeScreen
import com.example.cashbookbd.ui.transaction.CashVoucherForms
import com.example.cashbookbd.ui.transaction.CashVoucherScreen
import com.example.cashbookbd.ui.transaction.InstallmentsScreen
import com.example.cashbookbd.ui.transaction.TransactionFormScreen
import com.example.cashbookbd.ui.transaction.TransactionHomeScreen
import com.example.cashbookbd.ui.vrsettings.VrSettingsFormScreen
import com.example.cashbookbd.ui.vrsettings.VrSettingsHomeScreen
import com.example.cashbookbd.ui.vrsettings.VoucherHistoryScreen
import com.example.cashbookbd.ui.admin.SoftwareInfoScreen
import com.example.cashbookbd.ui.orders.AddOrderScreen
import com.example.cashbookbd.ui.orders.AveragePriceScreen
import com.example.cashbookbd.ui.orders.OrderWithTransactionScreen
import com.example.cashbookbd.ui.sms.SmsLogsScreen
import com.example.cashbookbd.ui.sms.SmsTemplateFormScreen
import com.example.cashbookbd.ui.admin.AdminNotificationsScreen
import com.example.cashbookbd.ui.admin.EditCompanyScreen
import com.example.cashbookbd.ui.admin.GroupReportSetupScreen
import com.example.cashbookbd.ui.roles.AddPermissionScreen
import com.example.cashbookbd.ui.subscription.PaymentSubmitScreen
import com.example.cashbookbd.ui.subscription.SubscriptionAdminScreen
import com.example.cashbookbd.ui.customer.AddCoaL3Screen
import com.example.cashbookbd.ui.invoice.LabourInvoiceScreen
import com.example.cashbookbd.inventory.BranchTransferMenu
import com.example.cashbookbd.ui.inventory.BranchTransferHomeScreen
import com.example.cashbookbd.ui.inventory.InventoryMovementScreen
import com.example.cashbookbd.ui.inventory.TransferListScreen
import com.example.cashbookbd.ui.admin.InAppMessageFormScreen
import com.example.cashbookbd.ui.analytics.ComparisonScreen
import com.example.cashbookbd.data.repository.TradeLedgerKind
import com.example.cashbookbd.ui.account.ProfileScreen
import com.example.cashbookbd.ui.reports.TradeLedgerScreen
import com.example.cashbookbd.ui.reports.GroupReportScreen
import com.example.cashbookbd.ui.reports.ConnectedMemberScreen
import com.example.cashbookbd.ui.theme.DarkV2Trial
import com.example.cashbookbd.ui.settings.ArrangeMenuScreen
import com.example.cashbookbd.ui.auth.ForgotPasswordScreen
import com.example.cashbookbd.ui.admin.InAppMessagesAdminScreen
import com.example.cashbookbd.ui.admin.InventorySystemsScreen
import com.example.cashbookbd.ui.subscription.PlanFormScreen
import com.example.cashbookbd.ui.producttracking.ProductStatementScreen
import com.example.cashbookbd.ui.producttracking.ProductTrackingSettingsScreen
import com.example.cashbookbd.ui.producttracking.ProductTrackingSummaryScreen
import com.example.cashbookbd.ui.requisition.RequisitionFormScreen
import com.example.cashbookbd.ui.requisition.RequisitionHomeScreen
import com.example.cashbookbd.requisition.RequisitionMenu
import com.example.cashbookbd.realestate.RealEstateMenu
import com.example.cashbookbd.ui.realestate.FlatLayoutScreen
import com.example.cashbookbd.ui.realestate.InstallmentCreateScreen
import com.example.cashbookbd.ui.realestate.RealEstateCrudFormScreen
import com.example.cashbookbd.ui.realestate.RealEstateHomeScreen
import com.example.cashbookbd.ui.realestate.SoldUnitsScreen
import com.example.cashbookbd.ui.realestate.UnitPaymentScreen
import com.example.cashbookbd.ui.realestate.UnitSaleScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"
    const val HOME = "home"

    // Reports
    const val REPORTS = "reports/home"
    const val CASHBOOK = "reports/cash_book"
    const val LEDGER = "reports/ledger"
    const val BANKBOOK = "reports/bank_book"
    const val CASH_BANK = "reports/cash_bank"
    const val TRIAL_BALANCE_L3 = "reports/trial_balance_l3"
    const val TRIAL_BALANCE_L4 = "reports/trial_balance_l4"
    const val PROFIT_LOSS = "reports/profit_loss"
    const val BALANCE_SHEET = "reports/balance_sheet"
    const val DUE_LIST = "reports/due_list"
    const val DUE_INSTALLMENTS = "reports/due_installments"

    // Generic report flow: reports/view/{key}
    const val REPORT_VIEW = "reports/view/{key}"
    const val REPORT_KEY_ARG = "key"

    fun reportView(key: String): String = "reports/view/$key"

    // Transaction section
    const val TRANSACTIONS = "transactions/home"
    const val TXN_VIEW = "transactions/view/{key}"
    const val TXN_KEY_ARG = "key"

    fun txnView(key: String): String = "transactions/view/$key"

    /** The customer installment schedule (the web's Installments page), not the Due Installments report. */
    const val INSTALLMENTS = "transactions/installments"

    // Invoice section
    const val INVOICES = "invoices/home"
    const val INVOICE_VIEW = "invoices/view/{key}"
    const val INVOICE_KEY_ARG = "key"

    fun invoiceView(key: String): String = "invoices/view/$key"

    /** Combined Invoice (purchase + sales in one form) — its own screen. */
    const val COMBINED_INVOICE = "invoices/combined"

    /** Labour Invoice (construction labour bill) — its own screen. */
    const val LABOUR_INVOICE = "invoices/labour"

    // Inventory movement forms (Branch Issue / Branch Receive / Material Issue),
    // one screen parameterized by key.
    const val INVENTORY_VIEW = "inventory/view/{key}"
    const val INVENTORY_KEY_ARG = "key"

    fun inventoryView(key: String): String = "inventory/view/$key"

    /** The Branch Transfer parent section (forms + register + reports). */
    const val BRANCH_TRANSFER = "branchtransfer/home"

    /** The branch transfer register: issued/received challans + comparison. */
    const val TRANSFER_LIST = "inventory/transfers"

    /** Platform CRUD of the inventory systems the branch form picks from. */
    const val INVENTORY_SYSTEMS = "admin/inventory-systems"

    /** The signed-in user's own page (name + profile photo). */
    const val PROFILE = "account/profile"

    // Purchase/Sales Ledger — one native screen keyed by kind.
    const val TRADE_LEDGER_PATTERN = "reports/trade-ledger/{kind}"
    const val TRADE_LEDGER_KIND_ARG = "kind"

    fun tradeLedger(kind: String): String = "reports/trade-ledger/$kind"

    /** Group Report's Operating/Purchase Cost pivot (native screen). */
    const val GROUP_REPORT = "reports/group-report"

    /** Connected Member's employee/area collection summary (native screen). */
    const val CONNECTED_MEMBER = "reports/connected-member"

    /** The Analytics section's one screen: the two-period item comparison. */
    const val ANALYTICS_COMPARISON = "analytics/comparison"

    // In-app message campaigns: the admin list and its create/edit form.
    const val IN_APP_MESSAGES = "admin/in-app-messages"
    const val IN_APP_MESSAGE_ADD = "admin/in-app-messages/add"
    const val IN_APP_MESSAGE_EDIT_PATTERN = "admin/in-app-messages/edit/{messageId}"
    const val IN_APP_MESSAGE_ID_ARG = "messageId"

    fun inAppMessageEdit(id: Long): String = "admin/in-app-messages/edit/$id"

    // SaaS plan entry/edit (the plans list is the subscriptionAdminPlans AppList).
    const val PLAN_ADD = "subscription/plans/add"
    const val PLAN_EDIT = "subscription/plans/edit"
    const val PLAN_EDIT_PATTERN = "subscription/plans/edit/{planId}"
    const val PLAN_ID_ARG = "planId"

    // Product tracking: the admin settings screen and the two memo reports.
    const val PRODUCT_TRACKING_SETTINGS = "admin/product-tracking"
    const val PRODUCT_TRACKING_SUMMARY = "reports/product-tracking-summary"
    const val PRODUCT_STATEMENT = "reports/product-statement?productId={productId}"
    const val PRODUCT_STATEMENT_ID_ARG = "productId"

    /** [productId] pre-selects the product (the Summary screen's row tap). */
    fun productStatement(productId: Long?): String =
        "reports/product-statement" + (productId?.let { "?productId=$it" } ?: "")

    // Requisition section
    const val REQUISITIONS = "requisition/home"
    const val REQUISITION_CREATE = "requisition/create"

    // Real Estate section (business type 9 branches)
    const val REAL_ESTATE = "realestate/home"
    const val UNIT_SALE = "realestate/unit-sales"
    const val SOLD_UNITS = "realestate/sold-units"
    const val RE_INSTALLMENT_CREATE = "realestate/installment-create"
    const val FLAT_LAYOUT = "realestate/layout"

    /** Check Register payment entry; edit appends the payment id. */
    const val UNIT_PAYMENT_ADD = "realestate/payment/add"
    const val UNIT_PAYMENT_EDIT = "realestate/payment/edit"
    const val UNIT_PAYMENT_ID_ARG = "paymentId"

    // Config-driven Real Estate add/edit forms (areas … charge types).
    const val RE_CRUD_ADD = "realestate/crud/{crudKey}/add"
    const val RE_CRUD_EDIT = "realestate/crud/{crudKey}/edit/{crudId}"
    const val RE_CRUD_KEY_ARG = "crudKey"
    const val RE_CRUD_ID_ARG = "crudId"

    fun reCrudAdd(key: String): String = "realestate/crud/$key/add"

    /** Base for a list's pencil — the list appends "/{id}" itself. */
    fun reCrudEditBase(key: String): String = "realestate/crud/$key/edit"

    // VR Settings section
    const val VR_SETTINGS = "vr-settings/home"

    /** Voucher History (branch + voucher no → change feed) — its own screen. */
    const val VOUCHER_HISTORY = "vr-settings/history"
    const val VR_SETTINGS_VIEW = "vr-settings/view/{key}"
    const val VR_SETTINGS_KEY_ARG = "key"

    fun vrSettingsView(key: String): String = "vr-settings/view/$key"

    // Admin section
    const val ADMIN = "admin/home"
    const val ADMIN_VIEW = "admin/view/{key}"
    const val ADMIN_KEY_ARG = "key"

    fun adminView(key: String): String = "admin/view/$key"

    /** Highlight-rules management (form + list on one screen, like the web). */
    const val HIGHLIGHT_RULES = "admin/highlight-rules"

    /** User List (search + paginated list with per-row edit and temp-password). */
    const val USER_LIST = "admin/user-list"

    /** Role & Permission management (role picker + grouped permission toggles). */
    const val ROLES = "admin/roles"

    /** Add Role form, opened from the Roles screen or the Admin menu. */
    const val ADD_ROLE = "admin/roles/add"

    /** Jump Date, opened from the Day Close screen — jumps the transaction date to a chosen day. */
    const val JUMP_DATE = "admin/jumpdate"

    /** Software Information (report-footer name/mobile), a single-record form. */
    const val SOFTWARE_INFO = "admin/software-info"

    /** Create Order form, opened from the Orders list's "Create Order" button. */
    const val ORDER_ADD = "orders/add"

    /** Order-wise transaction statement (order picker → voucher rows + balance). */
    const val ORDER_WITH_TRANSACTION = "orders/with-transaction"

    /** Average landed price of an order (costs + other expenses per unit). */
    const val AVERAGE_PRICE = "orders/avg-price"

    // SMS module: the sent-log list and the template create/edit form.
    const val SMS_LOGS = "admin/sms/logs"
    const val SMS_TEMPLATE_ADD = "admin/sms/templates/add"
    const val SMS_TEMPLATE_EDIT = "admin/sms/templates/edit"
    const val SMS_TEMPLATE_ID_ARG = "templateId"

    /** Platform broadcasts (create + list); server allows company 1 only. */
    const val ADMIN_NOTIFICATIONS = "admin/notifications"

    /** Spatie permission create/rename (software-company admins only). */
    const val ADD_PERMISSION = "admin/permissions/add"

    /** Group Report's Operating/Purchase Cost ledger mapping. */
    const val GROUP_REPORT_SETUP = "admin/group-report-setup"

    /** Arrange Menu — the user's own drawer order (no permission, like the web). */
    const val ARRANGE_MENU = "settings/arrange-menu"

    /** Edit Company — base route; the list appends "/{id}" itself. */
    const val COMPANY_EDIT = "company/edit"
    const val COMPANY_ID_ARG = "companyId"

    // Subscription: manual payment submit + the platform admin console.
    const val PAYMENT_SUBMIT = "subscription/payment-submit"
    const val SUBSCRIPTION_ADMIN = "subscription/admin"

    // HRM section
    const val HRM = "hrm/home"
    const val HRM_VIEW = "hrm/view/{key}"
    const val HRM_KEY_ARG = "key"

    fun hrmView(key: String): String = "hrm/view/$key"

    // Create screens, opened from their list's "+ Add" button.
    const val BRANCH_ADD = "branch/add"
    const val USER_ADD = "user/add"

    // Edit screens, opened from their list's row pencil. The id is appended by
    // the list, so the base is stored bare and the pattern adds the argument.
    const val BRANCH_EDIT = "branch/edit"
    const val BRANCH_ID_ARG = "branchId"

    // User edit, opened from the User List row pencil. The hashed id is appended.
    const val USER_EDIT = "user/edit"
    const val USER_ID_ARG = "userId"

    // Employee create/edit (HRM), opened from the Employees list.
    const val EMPLOYEE_ADD = "employee/add"
    const val EMPLOYEE_EDIT = "employee/edit"
    const val EMPLOYEE_ID_ARG = "employeeId"

    // Config-driven HRM add/edit forms (designations, attendance setup).
    const val HRM_CRUD_ADD = "hrm/crud/{crudKey}/add"
    const val HRM_CRUD_EDIT = "hrm/crud/{crudKey}/edit/{crudId}"
    const val HRM_CRUD_KEY_ARG = "crudKey"
    const val HRM_CRUD_ID_ARG = "crudId"

    fun hrmCrudAdd(key: String): String = "hrm/crud/$key/add"

    /** Base for a list's pencil — the list appends "/{id}" itself. */
    fun hrmCrudEditBase(key: String): String = "hrm/crud/$key/edit"

    /** savedStateHandle key carrying a create confirmation back to the list. */
    const val CREATED_MESSAGE = "created_message"

    // Customers section
    const val CUSTOMERS = "customers/home"

    /** The bespoke Customers list (search + inline opening/ledger edit + Add). */
    const val CUSTOMERS_LIST = "customers/list"

    /** Add Customer form, opened from the Customers list's "+ Add" button. */
    const val CUSTOMER_ADD = "customers/add"

    /** Set a customer's opening/ledger, opened from the Customers list row pencil. */
    const val CUSTOMER_EDIT = "customer/edit"
    const val CUSTOMER_ID_ARG = "customerId"

    /** Add CoA L3 form, opened from the CoA L3 list's "New COA L3" button. */
    const val COA_L3_ADD = "coa/l3/add"

    /** Edit CoA L3 — base route; the list appends "/{id}" itself. */
    const val COA_L3_EDIT = "coa/l3/edit"
    const val COA_L3_ID_ARG = "coaId"

    // Products section (master lists: Brand / Category / Product / Unit)
    const val PRODUCTS = "products/home"

    /** Add Brand form, opened from the Brand List's "New Brand" button. */
    const val BRAND_ADD = "products/brand/add"

    /** Add Category form, opened from the Category List's "New Category" button. */
    const val CATEGORY_ADD = "products/category/add"

    /** Add Unit form, opened from the Product Unit list's "New Unit" button. */
    const val UNIT_ADD = "products/unit/add"

    /** Add Product form, opened from the Product List's "New Product" button. */
    const val PRODUCT_ADD = "products/product/add"

    // Account section (the top-bar avatar menu)
    const val MY_DEVICES = "account/my-devices"

    // Reseller self-service dashboard (top-level; gated by reseller.dashboard.view)
    const val RESELLER_DASHBOARD = "reseller/dashboard"

    // Subscription section
    const val SUBSCRIPTION = "subscription/home"
    const val SUBSCRIPTION_MY_PLAN = "subscription/my-plan"
    const val SUBSCRIPTION_PRICING = "subscription/pricing"

    // Shared read-only list screens (used by VR Settings and Admin).
    const val APP_LIST_VIEW = "list/view/{key}"
    const val APP_LIST_KEY_ARG = "key"

    fun appListView(key: String): String = "list/view/$key"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authRepository = remember { ServiceLocator.provideAuthRepository(context) }
    val sessionRepository = remember { ServiceLocator.provideSessionRepository(context) }

    // Skip the login screen if a token is already stored.
    val loggedIn = authRepository.isLoggedIn()
    val startDestination = if (loggedIn) Routes.HOME else Routes.LOGIN

    // Cold start with a stored token: reload permissions before showing the
    // authenticated UI so menus/screens gate correctly. Permissions are held in
    // memory only, so they must be re-fetched on every launch.
    var booting by remember { mutableStateOf(loggedIn) }
    LaunchedEffect(Unit) {
        if (loggedIn) {
            sessionRepository.refresh()
            booting = false
        }
    }

    if (booting) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Enter the app, clearing auth screens from the back stack so Back can't
        // return to them. Shared by login and a successful registration.
        val enterApp: () -> Unit = {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = enterApp,
                onRegisterClick = {
                    navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                },
                onForgotPasswordClick = {
                    navController.navigate(Routes.FORGOT_PASSWORD) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = enterApp,
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onBackToLogin = { navController.popBackStack() },
            )
        }

        val backToLogin: () -> Unit = {
            authRepository.logout()
            navController.navigate(Routes.LOGIN) {
                // Drop the whole authenticated stack so Back can't return to it.
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }

        composable(Routes.HOME) {
            // The reworked dark palette, trialled on the Dashboard only.
            DarkV2Trial {
                DashboardScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    // Token expired / rejected (401): clear it and force re-login.
                    onSessionExpired = backToLogin,
                )
            }
        }

        composable(Routes.REPORTS) {
            PermissionGate(anyOf = ReportMenu.PARENT_PERMISSIONS) {
                ReportsHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.REPORT_VIEW,
            arguments = listOf(navArgument(Routes.REPORT_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.REPORT_KEY_ARG).orEmpty()
            val report = ReportMenu.byKey(key)
            PermissionGate(anyOf = report?.anyOf ?: emptyList()) {
                GenericReportScreen(
                    reportKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.TRANSACTIONS) {
            PermissionGate(anyOf = TransactionMenu.all.flatMap { it.anyOf }) {
                TransactionHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.TXN_VIEW,
            arguments = listOf(navArgument(Routes.TXN_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.TXN_KEY_ARG).orEmpty()
            val item = TransactionMenu.byKey(key)
            // Cash Received/Payment have business-type variants (Head Office /
            // Trading / General), so they get their own multi-line screen.
            val cashVoucher = CashVoucherForms.byKey(key)
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                if (cashVoucher != null) {
                    CashVoucherScreen(
                        spec = cashVoucher,
                        navController = navController,
                        onLogout = backToLogin,
                    )
                } else {
                    TransactionFormScreen(
                        txnKey = key,
                        navController = navController,
                        onLogout = backToLogin,
                    )
                }
            }
        }

        composable(Routes.INSTALLMENTS) {
            // The web's Transaction → Installments page (customer schedule +
            // receive), gated like its menu entry.
            PermissionGate(anyOf = TransactionMenu.byKey("installments")?.anyOf ?: emptyList()) {
                InstallmentsScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.INVOICES) {
            PermissionGate(anyOf = InvoiceMenu.all.flatMap { it.anyOf }) {
                InvoiceHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.COMBINED_INVOICE) {
            // Trading branches with both purchase and sales create permissions.
            PermissionGate(anyOf = listOf("purchase.create", "sales.create")) {
                CombinedInvoiceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.INVOICE_VIEW,
            arguments = listOf(navArgument(Routes.INVOICE_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.INVOICE_KEY_ARG).orEmpty()
            val item = InvoiceMenu.byKey(key)
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                InvoiceFormScreen(
                    invoiceKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.LABOUR_INVOICE) {
            PermissionGate(anyOf = InvoiceMenu.byKey(InvoiceMenu.LABOUR_INVOICE_KEY)?.anyOf ?: emptyList()) {
                LabourInvoiceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.INVENTORY_VIEW,
            arguments = listOf(navArgument(Routes.INVENTORY_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.INVENTORY_KEY_ARG).orEmpty()
            // The inventory forms live in the Branch Transfer menu; gate like
            // their entries.
            PermissionGate(anyOf = BranchTransferMenu.byKey(key)?.anyOf ?: emptyList()) {
                InventoryMovementScreen(
                    formKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.TRADE_LEDGER_PATTERN,
            arguments = listOf(navArgument(Routes.TRADE_LEDGER_KIND_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val kind = if (backStackEntry.arguments?.getString(Routes.TRADE_LEDGER_KIND_ARG) == "sales") {
                TradeLedgerKind.SALES
            } else {
                TradeLedgerKind.PURCHASE
            }
            PermissionGate(
                anyOf = listOf(if (kind == TradeLedgerKind.SALES) "sales.ledger" else "purchase.ledger"),
            ) {
                TradeLedgerScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    kind = kind,
                )
            }
        }

        composable(Routes.GROUP_REPORT) {
            PermissionGate(anyOf = listOf("group.report")) {
                GroupReportScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.CONNECTED_MEMBER) {
            PermissionGate(anyOf = listOf("connected.member.view")) {
                ConnectedMemberScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.PROFILE) {
            ProfileScreen(navController = navController, onLogout = backToLogin)
        }

        composable(Routes.ANALYTICS_COMPARISON) {
            PermissionGate(anyOf = listOf("analytics.comparison")) {
                ComparisonScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.IN_APP_MESSAGES) {
            PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
                InAppMessagesAdminScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.IN_APP_MESSAGE_ADD) {
            PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
                InAppMessageFormScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(
            route = Routes.IN_APP_MESSAGE_EDIT_PATTERN,
            arguments = listOf(navArgument(Routes.IN_APP_MESSAGE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
                InAppMessageFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    messageId = backStackEntry.arguments
                        ?.getString(Routes.IN_APP_MESSAGE_ID_ARG)?.toLongOrNull(),
                )
            }
        }

        composable(Routes.PLAN_ADD) {
            PermissionGate(anyOf = listOf("subscription.plans")) {
                PlanFormScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(
            route = Routes.PLAN_EDIT_PATTERN,
            arguments = listOf(navArgument(Routes.PLAN_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            PermissionGate(anyOf = listOf("subscription.plans")) {
                PlanFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    planId = backStackEntry.arguments?.getString(Routes.PLAN_ID_ARG)?.toLongOrNull(),
                )
            }
        }

        composable(Routes.INVENTORY_SYSTEMS) {
            PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
                InventorySystemsScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PRODUCT_TRACKING_SETTINGS) {
            PermissionGate(anyOf = listOf("product.tracking.settings.view")) {
                ProductTrackingSettingsScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PRODUCT_TRACKING_SUMMARY) {
            PermissionGate(anyOf = listOf("product.tracking.report.view")) {
                ProductTrackingSummaryScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.PRODUCT_STATEMENT,
            arguments = listOf(
                navArgument(Routes.PRODUCT_STATEMENT_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            PermissionGate(anyOf = listOf("product.tracking.report.view")) {
                ProductStatementScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    initialProductId = backStackEntry.arguments
                        ?.getString(Routes.PRODUCT_STATEMENT_ID_ARG)?.toLongOrNull(),
                )
            }
        }

        composable(Routes.BRANCH_TRANSFER) {
            PermissionGate(anyOf = BranchTransferMenu.all.flatMap { it.anyOf }) {
                BranchTransferHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.TRANSFER_LIST) {
            PermissionGate(
                anyOf = BranchTransferMenu.byKey(BranchTransferMenu.TRANSFER_LIST_KEY)?.anyOf
                    ?: emptyList(),
            ) {
                TransferListScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.REQUISITIONS) {
            PermissionGate(anyOf = RequisitionMenu.all.flatMap { it.anyOf }) {
                RequisitionHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.REQUISITION_CREATE) {
            PermissionGate(anyOf = listOf("requisition.create")) {
                RequisitionFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.REAL_ESTATE) {
            PermissionGate(anyOf = RealEstateMenu.all.flatMap { it.anyOf }) {
                RealEstateHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.UNIT_SALE) {
            PermissionGate(anyOf = RealEstateMenu.permissionsFor(RealEstateMenu.UNIT_SALES_KEY)) {
                UnitSaleScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.SOLD_UNITS) {
            PermissionGate(anyOf = RealEstateMenu.permissionsFor(RealEstateMenu.SOLD_UNITS_KEY)) {
                SoldUnitsScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.RE_INSTALLMENT_CREATE) {
            PermissionGate(anyOf = RealEstateMenu.permissionsFor(RealEstateMenu.INSTALLMENT_CREATE_KEY)) {
                InstallmentCreateScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.FLAT_LAYOUT) {
            PermissionGate(anyOf = RealEstateMenu.permissionsFor(RealEstateMenu.FLAT_LAYOUT_KEY)) {
                FlatLayoutScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.UNIT_PAYMENT_ADD) {
            PermissionGate(anyOf = listOf("check.register.view")) {
                UnitPaymentScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.UNIT_PAYMENT_EDIT}/{${Routes.UNIT_PAYMENT_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("check.register.view")) {
                UnitPaymentScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    paymentId = entry.arguments?.getString(Routes.UNIT_PAYMENT_ID_ARG),
                )
            }
        }

        composable(
            route = Routes.RE_CRUD_ADD,
            arguments = listOf(navArgument(Routes.RE_CRUD_KEY_ARG) { type = NavType.StringType }),
        ) { entry ->
            PermissionGate(anyOf = listOf("real.estate.view")) {
                RealEstateCrudFormScreen(
                    crudKey = entry.arguments?.getString(Routes.RE_CRUD_KEY_ARG).orEmpty(),
                    crudId = null,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.RE_CRUD_EDIT,
            arguments = listOf(
                navArgument(Routes.RE_CRUD_KEY_ARG) { type = NavType.StringType },
                navArgument(Routes.RE_CRUD_ID_ARG) { type = NavType.StringType },
            ),
        ) { entry ->
            PermissionGate(anyOf = listOf("real.estate.view")) {
                RealEstateCrudFormScreen(
                    crudKey = entry.arguments?.getString(Routes.RE_CRUD_KEY_ARG).orEmpty(),
                    crudId = entry.arguments?.getString(Routes.RE_CRUD_ID_ARG),
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.VOUCHER_HISTORY) {
            PermissionGate(anyOf = listOf("voucher.history")) {
                VoucherHistoryScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.ORDER_ADD) {
            PermissionGate(anyOf = listOf("order.view")) {
                AddOrderScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.ORDER_WITH_TRANSACTION) {
            PermissionGate(anyOf = listOf("order.view")) {
                OrderWithTransactionScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.AVERAGE_PRICE) {
            PermissionGate(anyOf = listOf("order.avg.price")) {
                AveragePriceScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.SMS_LOGS) {
            PermissionGate(anyOf = listOf("sms.logs")) {
                SmsLogsScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.SMS_TEMPLATE_ADD) {
            PermissionGate(anyOf = listOf("sms.templates")) {
                SmsTemplateFormScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable("${Routes.SMS_TEMPLATE_EDIT}/{${Routes.SMS_TEMPLATE_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("sms.templates")) {
                SmsTemplateFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    templateId = entry.arguments?.getString(Routes.SMS_TEMPLATE_ID_ARG),
                )
            }
        }

        composable(Routes.ADMIN_NOTIFICATIONS) {
            PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
                AdminNotificationsScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.ADD_PERMISSION) {
            PermissionGate(anyOf = listOf("roles.create")) {
                AddPermissionScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.GROUP_REPORT_SETUP) {
            PermissionGate(anyOf = listOf("group.report")) {
                GroupReportSetupScreen(navController = navController, onLogout = backToLogin)
            }
        }

        // Arranging one's own drawer is a personal setting — there is nothing
        // here another user could see or change, so it carries no permission.
        composable(Routes.ARRANGE_MENU) {
            ArrangeMenuScreen(navController = navController, onLogout = backToLogin)
        }

        composable("${Routes.COMPANY_EDIT}/{${Routes.COMPANY_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("branch.view", "company.view")) {
                EditCompanyScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    companyId = entry.arguments?.getString(Routes.COMPANY_ID_ARG),
                )
            }
        }

        // Reachable for lapsed tenants, like the web's subscription-safe routes.
        composable(Routes.PAYMENT_SUBMIT) {
            PaymentSubmitScreen(navController = navController, onLogout = backToLogin)
        }

        composable(Routes.SUBSCRIPTION_ADMIN) {
            PermissionGate(anyOf = listOf("subscription.history")) {
                SubscriptionAdminScreen(navController = navController, onLogout = backToLogin)
            }
        }

        composable(Routes.SOFTWARE_INFO) {
            PermissionGate(anyOf = listOf("software.information")) {
                SoftwareInfoScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.COA_L3_ADD) {
            PermissionGate(anyOf = listOf("coa.l3.view")) {
                AddCoaL3Screen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.COA_L3_EDIT}/{${Routes.COA_L3_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("coa.l3.view")) {
                AddCoaL3Screen(
                    navController = navController,
                    onLogout = backToLogin,
                    coaId = entry.arguments?.getString(Routes.COA_L3_ID_ARG),
                )
            }
        }

        composable(Routes.VR_SETTINGS) {
            PermissionGate(anyOf = VrSettingsMenu.all.flatMap { it.anyOf }) {
                VrSettingsHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.VR_SETTINGS_VIEW,
            arguments = listOf(navArgument(Routes.VR_SETTINGS_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.VR_SETTINGS_KEY_ARG).orEmpty()
            val item = VrSettingsMenu.byKey(key)
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                VrSettingsFormScreen(
                    settingKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.ADMIN) {
            PermissionGate(anyOf = AdminMenu.all.flatMap { it.anyOf }) {
                AdminHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.HIGHLIGHT_RULES) {
            // Same gate as the web sidebar's Highlight Rules entry.
            PermissionGate(anyOf = listOf("highlight.rules")) {
                HighlightRulesScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.USER_LIST) {
            PermissionGate(anyOf = listOf("all.user.view", "user.view")) {
                UserListScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.USER_EDIT}/{${Routes.USER_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("all.user.view", "user.view")) {
                EditUserScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    userId = entry.arguments?.getString(Routes.USER_ID_ARG),
                )
            }
        }

        composable(Routes.ROLES) {
            PermissionGate(anyOf = listOf("roles.view")) {
                RolePermissionScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.ADD_ROLE) {
            PermissionGate(anyOf = listOf("roles.create")) {
                AddRoleScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.JUMP_DATE) {
            PermissionGate(anyOf = listOf("dayclose.jumpdate")) {
                AdminFormScreen(
                    adminKey = "jumpDate",
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CUSTOMER_ADD) {
            // Same gate as the Customers list this form is reached from.
            PermissionGate(anyOf = com.example.cashbookbd.session.MenuPermissions.map["customer"].orEmpty()) {
                AddCustomerScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.CUSTOMER_EDIT}/{${Routes.CUSTOMER_ID_ARG}}") { entry ->
            PermissionGate(anyOf = com.example.cashbookbd.session.MenuPermissions.map["customer"].orEmpty()) {
                EditCustomerScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    customerId = entry.arguments?.getString(Routes.CUSTOMER_ID_ARG),
                )
            }
        }

        composable(Routes.CUSTOMERS_LIST) {
            PermissionGate(anyOf = com.example.cashbookbd.session.MenuPermissions.map["customer"].orEmpty()) {
                CustomerListScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.HRM) {
            PermissionGate(anyOf = HrmMenu.all.flatMap { it.anyOf }) {
                HrmHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.HRM_VIEW,
            arguments = listOf(navArgument(Routes.HRM_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.HRM_KEY_ARG).orEmpty()
            val item = HrmMenu.byKey(key)
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                HrmFormScreen(
                    hrmKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.ADMIN_VIEW,
            arguments = listOf(navArgument(Routes.ADMIN_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.ADMIN_KEY_ARG).orEmpty()
            val item = AdminMenu.byKey(key)
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                AdminFormScreen(
                    adminKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.BRANCH_ADD) {
            PermissionGate(anyOf = listOf("branch.view")) {
                AddBranchScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.BRANCH_EDIT}/{${Routes.BRANCH_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("branch.view")) {
                AddBranchScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    branchId = entry.arguments?.getString(Routes.BRANCH_ID_ARG),
                )
            }
        }

        composable(Routes.USER_ADD) {
            PermissionGate(anyOf = listOf("all.user.view", "user.view")) {
                AddUserScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.EMPLOYEE_ADD) {
            PermissionGate(anyOf = listOf("employee.view")) {
                EmployeeFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable("${Routes.EMPLOYEE_EDIT}/{${Routes.EMPLOYEE_ID_ARG}}") { entry ->
            PermissionGate(anyOf = listOf("employee.view")) {
                EmployeeFormScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    employeeId = entry.arguments?.getString(Routes.EMPLOYEE_ID_ARG),
                )
            }
        }

        composable(
            route = Routes.HRM_CRUD_ADD,
            arguments = listOf(navArgument(Routes.HRM_CRUD_KEY_ARG) { type = NavType.StringType }),
        ) { entry ->
            val key = entry.arguments?.getString(Routes.HRM_CRUD_KEY_ARG).orEmpty()
            PermissionGate(anyOf = HrmCrudForms.byKey(key)?.anyOf ?: emptyList()) {
                HrmCrudFormScreen(
                    crudKey = key,
                    crudId = null,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(
            route = Routes.HRM_CRUD_EDIT,
            arguments = listOf(
                navArgument(Routes.HRM_CRUD_KEY_ARG) { type = NavType.StringType },
                navArgument(Routes.HRM_CRUD_ID_ARG) { type = NavType.StringType },
            ),
        ) { entry ->
            val key = entry.arguments?.getString(Routes.HRM_CRUD_KEY_ARG).orEmpty()
            PermissionGate(anyOf = HrmCrudForms.byKey(key)?.anyOf ?: emptyList()) {
                HrmCrudFormScreen(
                    crudKey = key,
                    crudId = entry.arguments?.getString(Routes.HRM_CRUD_ID_ARG),
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CUSTOMERS) {
            PermissionGate(anyOf = CustomerMenu.all.flatMap { it.anyOf }) {
                CustomerHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PRODUCTS) {
            PermissionGate(anyOf = ProductsMenu.all.flatMap { it.anyOf }) {
                ProductsHomeScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.BRAND_ADD) {
            PermissionGate(anyOf = listOf("brand.list")) {
                AddBrandScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CATEGORY_ADD) {
            PermissionGate(anyOf = listOf("category.view")) {
                AddCategoryScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.UNIT_ADD) {
            PermissionGate(anyOf = listOf("product.unit")) {
                AddUnitScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PRODUCT_ADD) {
            PermissionGate(anyOf = listOf("products.view")) {
                AddProductScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.SUBSCRIPTION) {
            SubscriptionHomeScreen(navController = navController, onLogout = backToLogin)
        }

        composable(Routes.SUBSCRIPTION_MY_PLAN) {
            MyPlanScreen(navController = navController, onLogout = backToLogin)
        }

        composable(Routes.SUBSCRIPTION_PRICING) {
            PricingScreen(navController = navController, onLogout = backToLogin)
        }

        composable(
            route = Routes.APP_LIST_VIEW,
            arguments = listOf(navArgument(Routes.APP_LIST_KEY_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString(Routes.APP_LIST_KEY_ARG).orEmpty()
            val spec = com.example.cashbookbd.applist.AppLists.byKey(key)
            // An empty anyOf means "no permission required" — pass null, since
            // PermissionGate treats an empty list as an unsatisfiable requirement.
            PermissionGate(anyOf = spec?.anyOf?.takeIf { it.isNotEmpty() }) {
                com.example.cashbookbd.ui.applist.AppListScreen(
                    listKey = key,
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.TRIAL_BALANCE_L3) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("trialBalanceLevel3")) {
                TrialBalanceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    title = "Trial Balance Group",
                    reportPath = com.example.cashbookbd.data.repository.TrialBalanceRepository.PATH_LEVEL3,
                )
            }
        }

        composable(Routes.BANKBOOK) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("bankbook")) {
                BankBookScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CASH_BANK) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("cashBankReceivedPayment")) {
                CashBankScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.TRIAL_BALANCE_L4) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("trialBalanceLevel4")) {
                TrialBalanceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PROFIT_LOSS) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("profitLoss")) {
                ProfitLossReportScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.BALANCE_SHEET) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("balanceSheet")) {
                BalanceSheetReportScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.DUE_LIST) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("dueList")) {
                DueListScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.DUE_INSTALLMENTS) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("dueInstallments")) {
                DueInstallmentsScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CASHBOOK) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("cashbook")) {
                CashBookScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.LEDGER) {
            PermissionGate(anyOf = ReportMenu.permissionsFor("ledger")) {
                LedgerScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        // No permission gate: every signed-in user may manage their own devices.
        composable(Routes.MY_DEVICES) {
            MyDevicesScreen(
                navController = navController,
                onLogout = backToLogin,
            )
        }

        composable(Routes.RESELLER_DASHBOARD) {
            PermissionGate(anyOf = listOf("reseller.dashboard.view")) {
                ResellerDashboardScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    // Token expired / rejected (401): clear it and force re-login.
                    onSessionExpired = backToLogin,
                )
            }
        }
    }
}
