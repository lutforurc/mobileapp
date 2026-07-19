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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.subscription.SubscriptionMenu
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.components.AccountMenu
import com.example.cashbookbd.ui.components.accountMenuItems
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
    // The "Invoice" section is shown when the user has any invoice permission.
    val canInvoices = InvoiceMenu.hasParentAccess(sessionState.permissions)
    // The "VR Settings" section is shown when the user has any voucher-settings permission.
    val canVrSettings = VrSettingsMenu.hasParentAccess(sessionState.permissions)
    // The "Admin" section is shown when the user has any admin permission.
    val canAdmin = AdminMenu.hasParentAccess(sessionState.permissions)
    // The "Customers" section is shown when the user has any customer permission.
    val canCustomers = CustomerMenu.hasParentAccess(sessionState.permissions)
    // "Subscription" shows for any authenticated user (My Plan is universal).
    val canSubscription = SubscriptionMenu.hasParentAccess(sessionState.permissions)

    val themeManager = remember { ServiceLocator.provideThemeManager(context) }
    val themeMode by themeManager.mode.collectAsStateWithLifecycle()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Shared by the drawer and the account menu so both navigate identically.
    // Compare against the ACTUAL current destination, not the drawer-highlight
    // label: report detail screens report currentRoute = REPORTS, so tapping
    // "Reports" from one must still navigate back to the Reports list.
    val navigateTo: (String) -> Unit = { route ->
        if (route != navController.currentDestination?.route) {
            navController.navigate(route) {
                // Keep the dashboard as the single base of the stack.
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                canReports = canReports,
                canTransactions = canTransactions,
                canInvoices = canInvoices,
                canVrSettings = canVrSettings,
                canAdmin = canAdmin,
                canCustomers = canCustomers,
                canSubscription = canSubscription,
                onDestinationClick = { route ->
                    scope.launch { drawerState.close() }
                    navigateTo(route)
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
                    // Right-hand account menu, matching the web's DropdownUser:
                    // the drawer stays module navigation, this stays "me".
                    AccountMenu(
                        userName = sessionState.settings?.userName,
                        transactionDate = sessionState.settings?.transactionDate,
                        isDark = isDark,
                        onThemeChange = { dark ->
                            themeManager.setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        items = accountMenuItems(
                            onDashboard = { navigateTo(Routes.HOME) },
                            onMyDevices = { navigateTo(Routes.MY_DEVICES) },
                            onSubscription = if (canSubscription) {
                                { navigateTo(Routes.SUBSCRIPTION) }
                            } else {
                                null
                            },
                        ),
                        onLogout = onLogout,
                    )
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
    canInvoices: Boolean,
    canVrSettings: Boolean,
    canAdmin: Boolean,
    canCustomers: Boolean,
    canSubscription: Boolean,
    onDestinationClick: (String) -> Unit,
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

            // Single "Invoice" section — its child forms are listed inside
            // InvoiceHomeScreen, filtered by permission.
            if (canInvoices) {
                NavigationDrawerItem(
                    label = { Text("Invoice") },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    selected = currentRoute == Routes.INVOICES,
                    onClick = { onDestinationClick(Routes.INVOICES) },
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

            // Single "VR Settings" section — its child screens are listed inside
            // VrSettingsHomeScreen, filtered by permission.
            if (canVrSettings) {
                NavigationDrawerItem(
                    label = { Text("VR Settings") },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    selected = currentRoute == Routes.VR_SETTINGS,
                    onClick = { onDestinationClick(Routes.VR_SETTINGS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Single "Admin" section — its child screens are listed inside
            // AdminHomeScreen, filtered by permission.
            if (canAdmin) {
                NavigationDrawerItem(
                    label = { Text("Admin") },
                    icon = { Icon(Icons.Filled.AccountBox, contentDescription = null) },
                    selected = currentRoute == Routes.ADMIN,
                    onClick = { onDestinationClick(Routes.ADMIN) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Single "Customers" section — its child lists are listed inside
            // CustomerHomeScreen, filtered by permission.
            if (canCustomers) {
                NavigationDrawerItem(
                    label = { Text("Customers") },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    selected = currentRoute == Routes.CUSTOMERS,
                    onClick = { onDestinationClick(Routes.CUSTOMERS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // "Subscription" — My Plan / Pricing / Billing / Plans, listed inside
            // SubscriptionHomeScreen.
            if (canSubscription) {
                NavigationDrawerItem(
                    label = { Text("Subscription") },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    selected = currentRoute == Routes.SUBSCRIPTION,
                    onClick = { onDestinationClick(Routes.SUBSCRIPTION) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Dark mode and Log Out deliberately live in the top-bar account
            // menu only (as on the web), so the drawer stays pure navigation.
            Spacer(Modifier.height(12.dp))
        }
    }
}
