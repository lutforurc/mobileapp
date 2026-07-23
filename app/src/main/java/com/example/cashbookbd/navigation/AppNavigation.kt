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
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.common.PermissionGate
import com.example.cashbookbd.ui.dashboard.DashboardScreen
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
import com.example.cashbookbd.ui.customer.CustomerHomeScreen
import com.example.cashbookbd.ui.subscription.MyPlanScreen
import com.example.cashbookbd.ui.subscription.PricingScreen
import com.example.cashbookbd.ui.subscription.SubscriptionHomeScreen
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

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
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

    // VR Settings section
    const val VR_SETTINGS = "vr-settings/home"
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

    // Account section (the top-bar avatar menu)
    const val MY_DEVICES = "account/my-devices"

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
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = enterApp,
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
            DashboardScreen(
                navController = navController,
                onLogout = backToLogin,
                // Token expired / rejected (401): clear it and force re-login.
                onSessionExpired = backToLogin,
            )
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
            PermissionGate(anyOf = listOf("branch.view")) {
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
    }
}
