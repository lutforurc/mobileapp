package com.example.cashbookbd.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.dashboard.DashboardScreen
import com.example.cashbookbd.ui.login.LoginScreen
import com.example.cashbookbd.ui.reports.CashBookScreen
import com.example.cashbookbd.ui.reports.LedgerScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Reports
    const val CASHBOOK = "reports/cash_book"
    const val LEDGER = "reports/ledger"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authRepository = remember { ServiceLocator.provideAuthRepository(context) }

    // Skip the login screen if a token is already stored.
    val startDestination = if (authRepository.isLoggedIn()) Routes.HOME else Routes.LOGIN

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

        composable(Routes.CASHBOOK) {
            CashBookScreen(
                navController = navController,
                onLogout = backToLogin,
            )
        }

        composable(Routes.LEDGER) {
            LedgerScreen(
                navController = navController,
                onLogout = backToLogin,
            )
        }
    }
}
