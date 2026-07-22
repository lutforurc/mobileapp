package com.example.cashbookbd.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.cashbookbd.hrm.HrmMenu
import com.example.cashbookbd.subscription.SubscriptionMenu
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.components.AccountMenu
import com.example.cashbookbd.ui.components.NotificationBell
import com.example.cashbookbd.ui.components.accountMenuItems
import com.example.cashbookbd.ui.theme.ThemeMode
import com.example.cashbookbd.ui.theme.brand
import kotlinx.coroutines.launch

/**
 * Maps a notification's web target path to a mobile route, or null when the app
 * has no screen for it (the item then just opens/reads without navigating). More
 * mappings can be added as those screens land on mobile.
 */
private fun notificationRoute(to: String): String? = when {
    to.startsWith("/reports/due-installments") -> Routes.reportView("dueInstallments")
    to.startsWith("/subscription/my-plan") -> Routes.SUBSCRIPTION_MY_PLAN
    else -> null
}

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

    // The in-app notification center, shared app-wide so the badge is consistent
    // on every screen. Loads once, then refreshes when the bell is opened.
    val notificationCenter = remember { ServiceLocator.provideNotificationCenter(context) }
    val notificationState by notificationCenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionState.permissions) {
        notificationCenter.ensureLoaded(sessionState.permissions)
    }

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
    // The "HRM" section is shown when the user has any HRM permission.
    val canHrm = HrmMenu.hasParentAccess(sessionState.permissions)
    // The "Customers" section is shown when the user has any customer permission.
    val canCustomers = CustomerMenu.hasParentAccess(sessionState.permissions)
    // "Subscription" shows for any authenticated user (My Plan is universal).
    val canSubscription = SubscriptionMenu.hasParentAccess(sessionState.permissions)

    val themeManager = remember { ServiceLocator.provideThemeManager(context) }
    val themeMode by themeManager.mode.collectAsStateWithLifecycle()
    val fullScreenManager = remember { ServiceLocator.provideFullScreenManager(context) }
    val isFullScreen by fullScreenManager.enabled.collectAsStateWithLifecycle()
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
                canHrm = canHrm,
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
                    // The in-app notification center (web's DropdownNotification),
                    // to the left of the account menu.
                    NotificationBell(
                        state = notificationState,
                        onRefresh = { notificationCenter.refresh(sessionState.permissions) },
                        onOpen = { item ->
                            notificationRoute(item.to)?.let { navigateTo(it) }
                            // Admin broadcasts are read once opened; derived alerts
                            // stay until the situation clears or the user dismisses.
                            if (item.id.startsWith("admin")) notificationCenter.dismiss(item)
                        },
                        onDismiss = { notificationCenter.dismiss(it) },
                    )
                    // Right-hand account menu, matching the web's DropdownUser:
                    // the drawer stays module navigation, this stays "me".
                    AccountMenu(
                        userName = sessionState.settings?.userName,
                        transactionDate = sessionState.settings?.transactionDate,
                        photoUrl = sessionState.settings?.userPhotoUrl,
                        isDark = isDark,
                        onThemeChange = { dark ->
                            themeManager.setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        isFullScreen = isFullScreen,
                        onFullScreenChange = fullScreenManager::setEnabled,
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
    canHrm: Boolean,
    canCustomers: Boolean,
    canSubscription: Boolean,
    onDestinationClick: (String) -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // The brand sheet's "Signature gradient" (teal → deep blue) as the
            // drawer header, so the drawer opens with the brand itself. Both
            // the gradient and its text colour come from the palette, so the
            // header follows a theme change like everything else.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(MaterialTheme.brand.gradient))
                    .padding(start = 28.dp, top = 48.dp, bottom = 24.dp),
            ) {
                Text(
                    text = "CashBook",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.brand.onGradient,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Each section is one DrawerItem; child screens live inside the
            // section's own home screen, filtered by permission.
            DrawerItem("Dashboard", Icons.Filled.Home, currentRoute == Routes.HOME) {
                onDestinationClick(Routes.HOME)
            }
            if (canTransactions) {
                DrawerItem("Transaction", Icons.Filled.Create, currentRoute == Routes.TRANSACTIONS) {
                    onDestinationClick(Routes.TRANSACTIONS)
                }
            }
            if (canInvoices) {
                DrawerItem("Invoice", Icons.Filled.ShoppingCart, currentRoute == Routes.INVOICES) {
                    onDestinationClick(Routes.INVOICES)
                }
            }
            if (canReports) {
                DrawerItem(
                    "Reports",
                    Icons.AutoMirrored.Filled.List,
                    currentRoute == Routes.REPORTS ||
                        currentRoute == Routes.CASHBOOK ||
                        currentRoute == Routes.BANKBOOK ||
                        currentRoute == Routes.LEDGER,
                ) {
                    onDestinationClick(Routes.REPORTS)
                }
            }
            if (canVrSettings) {
                DrawerItem("VR Settings", Icons.Filled.Build, currentRoute == Routes.VR_SETTINGS) {
                    onDestinationClick(Routes.VR_SETTINGS)
                }
            }
            if (canAdmin) {
                DrawerItem("Admin", Icons.Filled.AccountBox, currentRoute == Routes.ADMIN) {
                    onDestinationClick(Routes.ADMIN)
                }
            }
            if (canHrm) {
                DrawerItem("HRM", Icons.Filled.Face, currentRoute == Routes.HRM) {
                    onDestinationClick(Routes.HRM)
                }
            }
            if (canCustomers) {
                DrawerItem("Customers", Icons.Filled.Person, currentRoute == Routes.CUSTOMERS) {
                    onDestinationClick(Routes.CUSTOMERS)
                }
            }
            if (canSubscription) {
                DrawerItem("Subscription", Icons.Filled.Star, currentRoute == Routes.SUBSCRIPTION) {
                    onDestinationClick(Routes.SUBSCRIPTION)
                }
            }

            // Dark mode and Log Out deliberately live in the top-bar account
            // menu only (as on the web), so the drawer stays pure navigation.
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * One drawer destination with the brand colours: the selected item sits in a
 * filled teal (primary) pill with white content; unselected items stay quiet.
 */
@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
