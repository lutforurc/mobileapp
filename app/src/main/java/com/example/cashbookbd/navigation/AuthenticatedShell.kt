package com.example.cashbookbd.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Shared chrome for every authenticated screen: a navigation drawer plus a top
 * bar with a hamburger button, screen [title] and optional [actions]. Each
 * screen supplies its own [currentRoute] (for drawer highlighting) and body via
 * [content].
 */
@Composable
fun AuthenticatedShell(
    title: String,
    currentRoute: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    // The single "Reports" section is shown when the user has any report permission.
    val canReports = ReportMenu.hasParentAccess(sessionState.permissions)
    // The "Transaction" section is shown when the user has any transaction permission.
    val canTransactions = TransactionMenu.hasParentAccess(sessionState.permissions)

    val themeManager = remember { ServiceLocator.provideThemeManager(context) }
    val themeMode by themeManager.mode.collectAsStateWithLifecycle()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                canReports = canReports,
                canTransactions = canTransactions,
                isDark = isDark,
                onThemeChange = { dark ->
                    themeManager.setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                },
                onDestinationClick = { route ->
                    scope.launch { drawerState.close() }
                    // Compare against the ACTUAL current destination, not the
                    // drawer-highlight label: report detail screens report
                    // currentRoute = REPORTS, so tapping "Reports" from one must
                    // still navigate back to the Reports list (it wasn't, before).
                    if (route != navController.currentDestination?.route) {
                        navController.navigate(route) {
                            // Keep the dashboard as the single base of the stack.
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
    ) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    actions()
                }

                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AppDrawerContent(
    currentRoute: String,
    canReports: Boolean,
    canTransactions: Boolean,
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onDestinationClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "CashBook",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
            )

            NavigationDrawerItem(
                label = { Text("Dashboard") },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                selected = currentRoute == Routes.HOME,
                onClick = { onDestinationClick(Routes.HOME) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            // Single "Transaction" section — its child forms are listed inside
            // TransactionHomeScreen, filtered by permission.
            if (canTransactions) {
                NavigationDrawerItem(
                    label = { Text("Transaction") },
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    selected = currentRoute == Routes.TRANSACTIONS,
                    onClick = { onDestinationClick(Routes.TRANSACTIONS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Single "Reports" section — its child reports are listed inside
            // ReportsHomeScreen, filtered by permission. Shown only when the user
            // has any report permission.
            if (canReports) {
                NavigationDrawerItem(
                    label = { Text("Reports") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    selected = currentRoute == Routes.REPORTS ||
                        currentRoute == Routes.CASHBOOK ||
                        currentRoute == Routes.LEDGER,
                    onClick = { onDestinationClick(Routes.REPORTS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))

            NavigationDrawerItem(
                label = { Text("Dark mode") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = { onThemeChange(!isDark) },
                badge = { Switch(checked = isDark, onCheckedChange = onThemeChange) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            NavigationDrawerItem(
                label = { Text("Log out") },
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}
