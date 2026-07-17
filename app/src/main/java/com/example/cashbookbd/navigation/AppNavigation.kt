package com.example.cashbookbd.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.common.PermissionGate
import com.example.cashbookbd.ui.dashboard.DashboardScreen
import com.example.cashbookbd.ui.login.LoginScreen
import com.example.cashbookbd.ui.reports.BalanceSheetReportScreen
import com.example.cashbookbd.ui.reports.CashBookScreen
import com.example.cashbookbd.ui.reports.DueListScreen
import com.example.cashbookbd.ui.reports.GenericReportScreen
import com.example.cashbookbd.ui.reports.LedgerScreen
import com.example.cashbookbd.ui.reports.ProfitLossReportScreen
import com.example.cashbookbd.ui.reports.ReportsHomeScreen
import com.example.cashbookbd.ui.reports.TrialBalanceScreen
import com.example.cashbookbd.ui.invoice.InvoiceFormScreen
import com.example.cashbookbd.ui.invoice.InvoiceHomeScreen
import com.example.cashbookbd.ui.transaction.TransactionFormScreen
import com.example.cashbookbd.ui.transaction.TransactionHomeScreen
import com.example.cashbookbd.ui.vrsettings.VrSettingsFormScreen
import com.example.cashbookbd.ui.vrsettings.VrSettingsHomeScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Reports
    const val REPORTS = "reports/home"
    const val CASHBOOK = "reports/cash_book"
    const val LEDGER = "reports/ledger"
    const val TRIAL_BALANCE_L3 = "reports/trial_balance_l3"
    const val TRIAL_BALANCE_L4 = "reports/trial_balance_l4"
    const val PROFIT_LOSS = "reports/profit_loss"
    const val BALANCE_SHEET = "reports/balance_sheet"
    const val DUE_LIST = "reports/due_list"

    // Generic report flow: reports/view/{key}
    const val REPORT_VIEW = "reports/view/{key}"
    const val REPORT_KEY_ARG = "key"

    fun reportView(key: String): String = "reports/view/$key"

    // Transaction section
    const val TRANSACTIONS = "transactions/home"
    const val TXN_VIEW = "transactions/view/{key}"
    const val TXN_KEY_ARG = "key"

    fun txnView(key: String): String = "transactions/view/$key"

    // Invoice section
    const val INVOICES = "invoices/home"
    const val INVOICE_VIEW = "invoices/view/{key}"
    const val INVOICE_KEY_ARG = "key"

    fun invoiceView(key: String): String = "invoices/view/$key"

    // VR Settings section
    const val VR_SETTINGS = "vr-settings/home"
    const val VR_SETTINGS_VIEW = "vr-settings/view/{key}"
    const val VR_SETTINGS_KEY_ARG = "key"

    fun vrSettingsView(key: String): String = "vr-settings/view/$key"
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
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        // Clear the back stack so Back doesn't return to login.
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
            PermissionGate(anyOf = item?.anyOf ?: emptyList()) {
                TransactionFormScreen(
                    txnKey = key,
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

        composable(Routes.TRIAL_BALANCE_L3) {
            PermissionGate(anyOf = listOf("cashbook.view", "trial.balance.l3")) {
                TrialBalanceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                    title = "Trial Balance Group",
                    reportPath = com.example.cashbookbd.data.repository.TrialBalanceRepository.PATH_LEVEL3,
                )
            }
        }

        composable(Routes.TRIAL_BALANCE_L4) {
            PermissionGate(anyOf = listOf("cashbook.view", "trial.balance.l4")) {
                TrialBalanceScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.PROFIT_LOSS) {
            PermissionGate(anyOf = listOf("cashbook.view", "profit.loss")) {
                ProfitLossReportScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.BALANCE_SHEET) {
            PermissionGate(anyOf = listOf("cashbook.view", "balancesheet.view")) {
                BalanceSheetReportScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.DUE_LIST) {
            PermissionGate(anyOf = listOf("ledger.due.view")) {
                DueListScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.CASHBOOK) {
            PermissionGate(anyOf = listOf("cashbook.view")) {
                CashBookScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }

        composable(Routes.LEDGER) {
            PermissionGate(anyOf = listOf("ledger.view", "ledger.customer")) {
                LedgerScreen(
                    navController = navController,
                    onLogout = backToLogin,
                )
            }
        }
    }
}
