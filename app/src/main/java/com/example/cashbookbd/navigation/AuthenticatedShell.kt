package com.example.cashbookbd.navigation

import com.example.cashbookbd.ui.theme.AppFontWeight
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.setValue
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
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.hrm.HrmMenu
import com.example.cashbookbd.products.ProductsMenu
import com.example.cashbookbd.subscription.SubscriptionMenu
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.data.repository.applyMenuOrder
import com.example.cashbookbd.data.repository.isMenuDivider
import com.example.cashbookbd.data.repository.menuDividerLabel
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.realestate.RealEstateMenu
import com.example.cashbookbd.requisition.RequisitionMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.components.AccountMenu
import com.example.cashbookbd.ui.components.InAppMessageOverlay
import com.example.cashbookbd.ui.components.NotificationBell
import com.example.cashbookbd.ui.components.SubscriptionIcon
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

    // Admin-authored pop-up campaigns. Fetched once per process — on a phone the
    // app opening and the session starting are the same moment — then played one
    // at a time over whatever screen is on top.
    val inAppMessages = remember { ServiceLocator.provideInAppMessageManager(context) }
    val inAppState by inAppMessages.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { inAppMessages.ensureLoaded() }

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
    // The "Requisition" section is shown when the user has any requisition permission.
    val canRequisition = RequisitionMenu.hasParentAccess(sessionState.permissions)
    // "Production" needs both doors open: a production.* key AND the branch's
    // Manufacture switch, exactly as the web sidebar checks them.
    val canProduction = com.example.cashbookbd.production.ProductionMenu
        .hasParentAccess(sessionState.permissions) &&
        sessionState.settings?.manufacturControl == true
    // "Real Estate" shows only for business type 9 branches with the section
    // permission — the same condition as the web sidebar.
    val canRealEstate = RealEstateMenu.hasParentAccess(sessionState.permissions) &&
        RealEstateMenu.visibleForBranch(sessionState.settings?.businessTypeId)
    // The "Customers" section is shown when the user has any customer permission.
    val canCustomers = CustomerMenu.hasParentAccess(sessionState.permissions)
    // The "Products" section is shown when the user has any products-list permission.
    val canProducts = ProductsMenu.hasParentAccess(sessionState.permissions)
    // "Subscription" shows for any authenticated user (My Plan is universal).
    val canSubscription = SubscriptionMenu.hasParentAccess(sessionState.permissions)
    // "Old Software" — the read-only archived-ERP screens (legacy.record.view / legacy.old.record.view).
    val canLegacy = com.example.cashbookbd.session.MenuPermissions.hasMenu(sessionState.permissions, "legacy")
    // The Delivery System dashboard: delivery.view on a deliver-to-deduct branch.
    val canDeliveryDashboard = sessionState.settings?.deductStockOnDelivery == true &&
        com.example.cashbookbd.session.Permissions.has(sessionState.permissions, "delivery.view")
    // "Company Scheme" — the IMEI-wise receivable lists (web 3b830761).
    val canCompanyScheme = com.example.cashbookbd.session.MenuPermissions.hasMenu(sessionState.permissions, "company_scheme")
    // "Reseller Dashboard" shows only for reseller accounts (reseller.dashboard.view),
    // mirroring the web's top-level Reseller Dashboard sidebar link.
    val canReseller = com.example.cashbookbd.session.MenuPermissions
        .hasMenu(sessionState.permissions, "reseller")
    // "Analytics" holds one screen (the item comparison chart), like the web.
    val canAnalytics = com.example.cashbookbd.session.Permissions
        .has(sessionState.permissions, "analytics.comparison")
    // "Branch Transfer" — the web's own group for stock crossing a branch line.
    val canBranchTransfer = com.example.cashbookbd.inventory.BranchTransferMenu
        .hasParentAccess(sessionState.permissions)
    // "Labour Items" — the web's master-data group for labour categories/items.
    val canLabourItems = com.example.cashbookbd.labour.LabourMenu
        .hasParentAccess(sessionState.permissions)

    // Tasks newly handed to this user, for the account menu's badge — the
    // web sidebar's own count (user-todos/summary). Fetched once per screen,
    // best-effort: a failed count shows no badge, never an error.
    var myTasksNewCount by remember { androidx.compose.runtime.mutableStateOf(0) }
    LaunchedEffect(Unit) {
        (ServiceLocator.provideUserTodoRepository(context)
            .summary() as? com.example.cashbookbd.core.Resource.Success)
            ?.let { myTasksNewCount = it.data.assignedNew }
    }
    // "Switch Branch" only matters when the user's own company has more than
    // one branch (web DropdownUser). Hidden until the count says so; a failed
    // fetch keeps it hidden.
    var branchCount by remember { androidx.compose.runtime.mutableStateOf(0) }
    LaunchedEffect(Unit) {
        (ServiceLocator.provideWebApi(context)
            .get("branch/ddl/all-branch", mapOf("own_company" to "1"))
            as? com.example.cashbookbd.core.Resource.Success)
            ?.let { branchCount = it.data.jRows().size }
    }
    // "Product Tracking" — the web's group for the settings + two reports.
    val canProductTracking = com.example.cashbookbd.producttracking.ProductTrackingMenu
        .hasParentAccess(sessionState.permissions)
    // "Hotel" — permission alone, never a business type: the Real Estate check
    // reads an auto-increment id that differs per tenant, and the hotel
    // permissions are granted to nobody until a property is actually set up.
    val canHotel = HotelMenu.hasParentAccess(sessionState.permissions)
    // "Assets" — the fixed-asset register; permission alone, like the hotel,
    // and granted to nobody until --asset-grant is run.
    val canAssets = com.example.cashbookbd.asset.AssetMenu.hasParentAccess(sessionState.permissions)

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
                canRequisition = canRequisition,
                canProduction = canProduction,
                canRealEstate = canRealEstate,
                canAdmin = canAdmin,
                canHrm = canHrm,
                canCustomers = canCustomers,
                canProducts = canProducts,
                canSubscription = canSubscription,
                canLegacy = canLegacy,
            canDeliveryDashboard = canDeliveryDashboard,
            canCompanyScheme = canCompanyScheme,
                canReseller = canReseller,
                canAnalytics = canAnalytics,
                canBranchTransfer = canBranchTransfer,
                canLabourItems = canLabourItems,
                canProductTracking = canProductTracking,
                canHotel = canHotel,
                canAssets = canAssets,
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
                            onProfile = { navigateTo(Routes.PROFILE) },
                            onMyTasks = { navigateTo(Routes.MY_TASKS) },
                            myTasksNewCount = myTasksNewCount,
                            onSwitchBranch = if (branchCount > 1) {
                                { navigateTo(ParityRoutes.SWITCH_BRANCH) }
                            } else {
                                null
                            },
                            onChangePassword = { navigateTo(ParityRoutes.CHANGE_PASSWORD) },
                            onAccountDeletion = { navigateTo(Routes.ACCOUNT_DELETION) },

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

                    // Drawn last so a banner sits above the screen's own content.
                    inAppState.current?.let { message ->
                        InAppMessageOverlay(
                            message = message,
                            onShown = inAppMessages::onShown,
                            onPrimary = { shown ->
                                inAppMessages.onPrimary(shown)
                                openInAppAction(context, shown.primaryAction)
                            },
                            onSecondary = { shown ->
                                inAppMessages.onSecondary(shown)
                                openInAppAction(context, shown.secondaryAction)
                            },
                            onDismiss = inAppMessages::onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Follows a campaign button's action.
 *
 * Campaign actions are authored once for every client, so they may carry a web
 * path ("/dashboard") as well as a link. Only real links are actionable on the
 * phone; a web path is ignored rather than guessing at a mobile route for it.
 */
private fun openInAppAction(context: Context, action: String?) {
    val target = action?.trim().orEmpty()
    if (!target.startsWith("http://", true) && !target.startsWith("https://", true)) return

    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
private fun AppDrawerContent(
    currentRoute: String,
    canReports: Boolean,
    canTransactions: Boolean,
    canInvoices: Boolean,
    canVrSettings: Boolean,
    canRequisition: Boolean,
    canProduction: Boolean,
    canRealEstate: Boolean,
    canAdmin: Boolean,
    canHrm: Boolean,
    canCustomers: Boolean,
    canProducts: Boolean,
    canSubscription: Boolean,
    canLegacy: Boolean,
    canDeliveryDashboard: Boolean = false,
    canCompanyScheme: Boolean = false,
    canReseller: Boolean,
    canAnalytics: Boolean,
    canBranchTransfer: Boolean,
    canLabourItems: Boolean,
    canProductTracking: Boolean,
    canHotel: Boolean,
    canAssets: Boolean,
    onDestinationClick: (String) -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // No branded header: the drawer opens straight onto its destinations.
            // The top inset still has to be cleared, or the first item sits under
            // the status bar.
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(12.dp))

            // Each section is one DrawerItem — child screens live inside the
            // section's own home screen, filtered by permission. Declared in
            // the web sidebar's order under the web's menu ids, then arranged
            // by the user's own saved preferences (the Arrange Menu page),
            // which the web sidebar shares.
            // Every section gets a distinct icon, mirroring the web sidebar's
            // one-icon-per-menu set (web af330122). Material-icons-core ships a
            // small curated set, so the nearest sensible glyph stands in.
            val permitted = buildList {
                add(DrawerEntry("dashboard", "Dashboard", Icons.Filled.Home, currentRoute == Routes.HOME, Routes.HOME))
                // The web's Dashboard group carries the Delivery System beside
                // the main page, on a branch that deducts stock on delivery.
                if (canDeliveryDashboard) add(
                    DrawerEntry(
                        "delivery_dashboard", "Delivery Dashboard", Icons.Filled.Share,
                        currentRoute == ParityRoutes.DELIVERY_DASHBOARD, ParityRoutes.DELIVERY_DASHBOARD,
                    )
                )
                if (canReseller) add(DrawerEntry("reseller", "Reseller Dashboard", ResellerIcon, currentRoute == Routes.RESELLER_DASHBOARD, Routes.RESELLER_DASHBOARD))
                if (canTransactions) add(DrawerEntry("transaction", "Transaction", TransactionIcon, currentRoute == Routes.TRANSACTIONS, Routes.TRANSACTIONS))
                if (canInvoices) add(DrawerEntry("invoice", "Invoice", InvoiceIcon, currentRoute == Routes.INVOICES, Routes.INVOICES))
                if (canBranchTransfer) add(DrawerEntry("branch-transfer", "Branch Transfer", BranchTransferIcon, currentRoute == Routes.BRANCH_TRANSFER, Routes.BRANCH_TRANSFER))
                if (canReports) add(
                    DrawerEntry(
                        "reports", "Reports", ReportsIcon,
                        currentRoute == Routes.REPORTS ||
                            currentRoute == Routes.CASHBOOK ||
                            currentRoute == Routes.BANKBOOK ||
                            currentRoute == Routes.LEDGER,
                        Routes.REPORTS,
                    )
                )
                // The web's Product Tracking group sits right after Reports.
                if (canProductTracking) add(DrawerEntry("product_tracking", "Product Tracking", ProductTrackingIcon, currentRoute == Routes.PRODUCT_TRACKING, Routes.PRODUCT_TRACKING))
                // Then the Company Scheme receivable lists, as on the web.
                if (canCompanyScheme) add(DrawerEntry("company_scheme", "Company Scheme", CompanySchemeIcon, currentRoute == ParityRoutes.COMPANY_SCHEME, ParityRoutes.COMPANY_SCHEME))
                if (canRequisition) add(DrawerEntry("requisition", "Requisition", Icons.Filled.MailOutline, currentRoute == Routes.REQUISITIONS, Routes.REQUISITIONS))
                // The web puts Production between Requisition and Real Estate.
                if (canProduction) add(
                    DrawerEntry(
                        "production", "Production", Icons.AutoMirrored.Filled.List,
                        currentRoute == com.example.cashbookbd.production.ProductionMenu.ROUTE_HOME,
                        com.example.cashbookbd.production.ProductionMenu.ROUTE_HOME,
                    )
                )
                if (canRealEstate) add(DrawerEntry("real-estate", "Real Estate", Icons.Filled.Place, currentRoute == Routes.REAL_ESTATE, Routes.REAL_ESTATE))
                // Beside Real Estate: the other property module, and the web
                // sidebar keeps them together for the same reason.
                if (canHotel) add(DrawerEntry("hotel", "Hotel", HotelIcon, currentRoute == Routes.HOTEL, Routes.HOTEL))
                // Between Hotel and Products, where the web sidebar puts it.
                if (canAssets) {
                    val assetHome = com.example.cashbookbd.asset.AssetMenu.ROUTE_HOME
                    add(DrawerEntry("asset", "Assets", AssetsIcon, currentRoute == assetHome, assetHome))
                }
                if (canProducts) add(DrawerEntry("products", "Products", ProductsIcon, currentRoute == Routes.PRODUCTS, Routes.PRODUCTS))
                // Down here with Products rather than up among the daily
                // entries (web 7895207): master data, set up once and opened
                // rarely — the menus near the top are the ones reached daily.
                if (canLabourItems) add(DrawerEntry("labour_items", "Labour Items", LabourItemsIcon, currentRoute == Routes.LABOUR_ITEMS, Routes.LABOUR_ITEMS))
                // "Old Software" — an archived old ERP's own read-only records; nothing here posts to the books.
                if (canLegacy) add(DrawerEntry("legacy", "Old Software", Icons.Filled.Info, currentRoute == Routes.LEGACY_HOME, Routes.LEGACY_HOME))
                if (canAdmin) add(DrawerEntry("admin", "Admin", AdminIcon, currentRoute == Routes.ADMIN, Routes.ADMIN))
                if (canVrSettings) add(DrawerEntry("vr_settings", "VR Settings", VrSettingsIcon, currentRoute == Routes.VR_SETTINGS, Routes.VR_SETTINGS))
                if (canHrm) add(DrawerEntry("hrm", "HRM", HrmIcon, currentRoute == Routes.HRM, Routes.HRM))
                if (canCustomers) add(DrawerEntry("customer-supplier", "Customers", Icons.Filled.Person, currentRoute == Routes.CUSTOMERS, Routes.CUSTOMERS))
                // One screen, like the web's one-item Analytics group.
                if (canAnalytics) add(DrawerEntry("al-charts", "Analytics", Icons.Filled.Info, currentRoute == Routes.ANALYTICS_COMPARISON, Routes.ANALYTICS_COMPARISON))
                // Mobile-only tail: the web keeps Subscription in the header menu.
                if (canSubscription) add(DrawerEntry("subscription", "Subscription", SubscriptionIcon, currentRoute == Routes.SUBSCRIPTION, Routes.SUBSCRIPTION))
            }

            val context = LocalContext.current
            val menuPrefsRepository = remember { ServiceLocator.provideMenuPreferencesRepository(context) }
            val menuPrefs by menuPrefsRepository.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { menuPrefsRepository.refresh() }

            val byId = permitted.associateBy { it.id }
            applyMenuOrder(permitted.map { it.id }, menuPrefs.order).forEach { id ->
                if (id in menuPrefs.hidden) return@forEach
                if (isMenuDivider(id)) {
                    DrawerSectionDivider(menuDividerLabel(id))
                } else {
                    byId[id]?.let { entry ->
                        DrawerItem(entry.label, entry.icon, entry.selected) {
                            onDestinationClick(entry.route)
                        }
                    }
                }
            }

            // Dark mode and Log Out deliberately live in the top-bar account
            // menu only (as on the web), so the drawer stays pure navigation.
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * The drawer's "Transaction" glyph: two documents with opposing arrows across
 * them — the transfer/exchange idea, so the section no longer reads as
 * "compose" (the stock Create pencil it used to carry). Drawn here because the
 * core icon set ships no such glyph; stroked and colourless, so the drawer
 * tints it with the item's own content colour.
 */
private val TransactionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Transaction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The back page — only the edges the front page doesn't cover.
            moveTo(8.5f, 8f)
            verticalLineTo(5f)
            quadTo(8.5f, 3f, 10.5f, 3f)
            horizontalLineTo(18f)
            quadTo(20f, 3f, 20f, 5f)
            verticalLineTo(13f)
            quadTo(20f, 15f, 18f, 15f)
            horizontalLineTo(15.5f)
            // The front page.
            moveTo(6f, 8f)
            horizontalLineTo(14f)
            quadTo(16f, 8f, 16f, 10f)
            verticalLineTo(18f)
            quadTo(16f, 20f, 14f, 20f)
            horizontalLineTo(6f)
            quadTo(4f, 20f, 4f, 18f)
            verticalLineTo(10f)
            quadTo(4f, 8f, 6f, 8f)
            close()
            // The two opposing arrows: one to the right, one to the left.
            moveTo(6.6f, 12.4f)
            horizontalLineTo(12.6f)
            moveTo(10.9f, 10.8f)
            lineTo(12.6f, 12.4f)
            lineTo(10.9f, 14f)
            moveTo(13.4f, 16.4f)
            horizontalLineTo(7.4f)
            moveTo(9.1f, 14.8f)
            lineTo(7.4f, 16.4f)
            lineTo(9.1f, 18f)
        }
    }.build()
}

/**
 * The drawer's "Hotel" glyph: a reception desk with the receptionist behind it
 * and a guest's suitcase in front — the check-in idea, rather than the stock
 * AccountBox it used to carry. Drawn here because the core icon set ships no
 * such glyph; stroked and colourless, so the drawer tints it like every item.
 */
private val HotelIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Hotel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The receptionist behind the desk: head, then shoulders.
            moveTo(7f, 7.4f)
            arcToRelative(2f, 2f, 0f, false, true, 4f, 0f)
            arcToRelative(2f, 2f, 0f, false, true, -4f, 0f)
            close()
            moveTo(5.9f, 13.6f)
            arcToRelative(3.1f, 3.1f, 0f, false, true, 6.2f, 0f)
            // The guest's suitcase, in front of the desk.
            moveTo(16.8f, 16.8f)
            arcToRelative(1f, 1f, 0f, false, true, 2f, 0f)
            moveTo(16f, 16.8f)
            horizontalLineTo(19.6f)
            quadTo(20.8f, 16.8f, 20.8f, 18f)
            verticalLineTo(20f)
            quadTo(20.8f, 21.2f, 19.6f, 21.2f)
            horizontalLineTo(16f)
            quadTo(14.8f, 21.2f, 14.8f, 20f)
            verticalLineTo(18f)
            quadTo(14.8f, 16.8f, 16f, 16.8f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // The reception desk.
            moveTo(3.4f, 14.2f)
            horizontalLineTo(20.6f)
        }
    }.build()
}

/**
 * The drawer's "VR Settings" glyph: a gear with a sliders panel over its
 * lower-left — the settings idea, rather than the stock Edit pencil it used to
 * carry. Drawn here because the core icon set ships no such glyph; stroked and
 * colourless, so the drawer tints it like every item.
 */
private val VrSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VrSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The gear: its hub ring, then eight teeth around it.
            moveTo(19.3f, 7.4f)
            arcToRelative(3.8f, 3.8f, 0f, false, true, -7.6f, 0f)
            arcToRelative(3.8f, 3.8f, 0f, false, true, 7.6f, 0f)
            close()
            moveTo(19.5f, 7.4f)
            horizontalLineTo(20.9f)
            moveTo(18.33f, 10.23f)
            lineTo(19.33f, 11.23f)
            moveTo(15.5f, 11.4f)
            verticalLineTo(12.8f)
            moveTo(12.67f, 10.23f)
            lineTo(11.67f, 11.23f)
            moveTo(11.5f, 7.4f)
            horizontalLineTo(10.1f)
            moveTo(12.67f, 4.57f)
            lineTo(11.67f, 3.57f)
            moveTo(15.5f, 3.4f)
            verticalLineTo(2f)
            moveTo(18.33f, 4.57f)
            lineTo(19.33f, 3.57f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The settings panel over the gear's lower-left.
            moveTo(4.8f, 12f)
            horizontalLineTo(10.2f)
            quadTo(13f, 12f, 13f, 14.8f)
            verticalLineTo(19f)
            quadTo(13f, 21.8f, 10.2f, 21.8f)
            horizontalLineTo(4.8f)
            quadTo(2f, 21.8f, 2f, 19f)
            verticalLineTo(14.8f)
            quadTo(2f, 12f, 4.8f, 12f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // The two slider tracks.
            moveTo(3.8f, 15.7f)
            horizontalLineTo(11.2f)
            moveTo(3.8f, 18.5f)
            horizontalLineTo(11.2f)
        }
        path(fill = SolidColor(Color.Black)) {
            // The slider knobs.
            moveTo(6.2f, 15.7f)
            arcToRelative(1.6f, 1.6f, 0f, true, true, 3.2f, 0f)
            arcToRelative(1.6f, 1.6f, 0f, true, true, -3.2f, 0f)
            close()
            moveTo(4f, 18.5f)
            arcToRelative(1.6f, 1.6f, 0f, true, true, 3.2f, 0f)
            arcToRelative(1.6f, 1.6f, 0f, true, true, -3.2f, 0f)
            close()
        }
    }.build()
}

/**
 * The drawer's "Reports" glyph: a document carrying a bar chart with a
 * magnifying glass beside it — the "analyse the figures" idea, rather than the
 * stock list glyph it used to carry. Drawn here because the core icon set ships
 * no such glyph; stroked and colourless, so the drawer tints it like every item.
 */
private val ReportsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Reports",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The report sheet.
            moveTo(4.5f, 3f)
            horizontalLineTo(10.5f)
            quadTo(12.5f, 3f, 12.5f, 5f)
            verticalLineTo(18.5f)
            quadTo(12.5f, 20.5f, 10.5f, 20.5f)
            horizontalLineTo(4.5f)
            quadTo(2.5f, 20.5f, 2.5f, 18.5f)
            verticalLineTo(5f)
            quadTo(2.5f, 3f, 4.5f, 3f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // The bar chart on the sheet.
            moveTo(5.2f, 17.6f)
            verticalLineTo(13.6f)
            moveTo(7.5f, 17.6f)
            verticalLineTo(10.6f)
            moveTo(9.8f, 17.6f)
            verticalLineTo(8.2f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // The magnifying glass beside the sheet: lens, then handle.
            moveTo(21.8f, 14.8f)
            arcToRelative(4.6f, 4.6f, 0f, false, true, -9.2f, 0f)
            arcToRelative(4.6f, 4.6f, 0f, false, true, 9.2f, 0f)
            close()
            moveTo(20.45f, 18.05f)
            lineTo(22f, 19.6f)
        }
    }.build()
}

/**
 * The drawer's "Branch Transfer" glyph: two branch blocks with a two-headed
 * arrow running between them — stock moving both ways across a branch line,
 * rather than the stock Send paper-plane it used to carry. Drawn here because
 * the core icon set ships no such glyph; stroked and colourless, so the drawer
 * tints it like every item.
 */
private val BranchTransferIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BranchTransfer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The two branches.
            moveTo(4f, 8f)
            horizontalLineTo(7f)
            quadTo(8.5f, 8f, 8.5f, 9.5f)
            verticalLineTo(14.5f)
            quadTo(8.5f, 16f, 7f, 16f)
            horizontalLineTo(4f)
            quadTo(2.5f, 16f, 2.5f, 14.5f)
            verticalLineTo(9.5f)
            quadTo(2.5f, 8f, 4f, 8f)
            close()
            moveTo(17f, 8f)
            horizontalLineTo(20f)
            quadTo(21.5f, 8f, 21.5f, 9.5f)
            verticalLineTo(14.5f)
            quadTo(21.5f, 16f, 20f, 16f)
            horizontalLineTo(17f)
            quadTo(15.5f, 16f, 15.5f, 14.5f)
            verticalLineTo(9.5f)
            quadTo(15.5f, 8f, 17f, 8f)
            close()
            // The two-headed transfer arrow between them.
            moveTo(9.6f, 12f)
            horizontalLineTo(14.4f)
            moveTo(11.3f, 10.3f)
            lineTo(9.6f, 12f)
            lineTo(11.3f, 13.7f)
            moveTo(12.7f, 10.3f)
            lineTo(14.4f, 12f)
            lineTo(12.7f, 13.7f)
        }
    }.build()
}

/** The drawer's "Reseller Dashboard" glyph: a shopfront with an awning. */
private val ResellerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Reseller",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The awning.
            moveTo(4.3f, 5.5f)
            horizontalLineTo(19.7f)
            quadTo(21.5f, 5.5f, 21.5f, 7.3f)
            verticalLineTo(8.4f)
            quadTo(21.5f, 9.8f, 20.1f, 9.8f)
            horizontalLineTo(3.9f)
            quadTo(2.5f, 9.8f, 2.5f, 8.4f)
            verticalLineTo(7.3f)
            quadTo(2.5f, 5.5f, 4.3f, 5.5f)
            close()
            // The shop, with its door.
            moveTo(4.2f, 9.8f)
            verticalLineTo(19.4f)
            quadTo(4.2f, 20.5f, 5.3f, 20.5f)
            horizontalLineTo(18.7f)
            quadTo(19.8f, 20.5f, 19.8f, 19.4f)
            verticalLineTo(9.8f)
            moveTo(10f, 20.5f)
            verticalLineTo(14.6f)
            quadTo(10f, 13.6f, 11f, 13.6f)
            horizontalLineTo(13f)
            quadTo(14f, 13.6f, 14f, 14.6f)
            verticalLineTo(20.5f)
        }
    }.build()
}

/** The drawer's "Product Tracking" glyph: a parcel with a location pin over it. */
private val ProductTrackingIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProductTracking",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The parcel.
            moveTo(5f, 9f)
            horizontalLineTo(14f)
            quadTo(15.5f, 9f, 15.5f, 10.5f)
            verticalLineTo(19f)
            quadTo(15.5f, 20.5f, 14f, 20.5f)
            horizontalLineTo(5f)
            quadTo(3.5f, 20.5f, 3.5f, 19f)
            verticalLineTo(10.5f)
            quadTo(3.5f, 9f, 5f, 9f)
            close()
            moveTo(3.5f, 13.6f)
            horizontalLineTo(15.5f)
            // The location pin.
            moveTo(21f, 6.6f)
            arcToRelative(2.4f, 2.4f, 0f, false, true, -4.8f, 0f)
            arcToRelative(2.4f, 2.4f, 0f, false, true, 4.8f, 0f)
            close()
            moveTo(16.8f, 8.2f)
            lineTo(18.6f, 10.6f)
            lineTo(20.4f, 8.2f)
        }
    }.build()
}

/** The drawer's "Company Scheme" glyph: a rosette badge with ribbon tails. */
private val CompanySchemeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CompanyScheme",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The badge disc.
            moveTo(16.4f, 8.8f)
            arcToRelative(4.4f, 4.4f, 0f, false, true, -8.8f, 0f)
            arcToRelative(4.4f, 4.4f, 0f, false, true, 8.8f, 0f)
            close()
            // The ribbon tails.
            moveTo(9.5f, 12.4f)
            lineTo(8.1f, 20.6f)
            lineTo(12f, 18.4f)
            lineTo(15.9f, 20.6f)
            lineTo(14.5f, 12.4f)
        }
    }.build()
}

/** The drawer's "Assets" glyph: a safe with a dial and handle. */
private val AssetsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Assets",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The safe, standing on two short feet.
            moveTo(6f, 4f)
            horizontalLineTo(18f)
            quadTo(20f, 4f, 20f, 6f)
            verticalLineTo(16f)
            quadTo(20f, 18f, 18f, 18f)
            horizontalLineTo(6f)
            quadTo(4f, 18f, 4f, 16f)
            verticalLineTo(6f)
            quadTo(4f, 4f, 6f, 4f)
            close()
            moveTo(7.5f, 18f)
            verticalLineTo(20.4f)
            moveTo(16.5f, 18f)
            verticalLineTo(20.4f)
            // The dial and the handle.
            moveTo(13.4f, 11f)
            arcToRelative(2.9f, 2.9f, 0f, false, true, -5.8f, 0f)
            arcToRelative(2.9f, 2.9f, 0f, false, true, 5.8f, 0f)
            close()
            moveTo(16.6f, 9f)
            verticalLineTo(13f)
        }
    }.build()
}

/** The drawer's "Products" glyph: an isometric parcel box. */
private val ProductsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Products",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The top, front and side faces of the box.
            moveTo(4f, 9.6f)
            lineTo(9.6f, 6f)
            lineTo(20f, 6f)
            lineTo(14.4f, 9.6f)
            close()
            moveTo(4f, 9.6f)
            lineTo(14.4f, 9.6f)
            lineTo(14.4f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(14.4f, 9.6f)
            lineTo(20f, 6f)
            lineTo(20f, 16.4f)
            lineTo(14.4f, 20f)
        }
    }.build()
}

/** The drawer's "Labour Items" glyph: a hard hat. */
private val LabourItemsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LabourItems",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The dome.
            moveTo(5.6f, 15f)
            quadTo(5.6f, 7.6f, 12f, 7.6f)
            quadTo(18.4f, 7.6f, 18.4f, 15f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // The brim.
            moveTo(3.2f, 15.2f)
            horizontalLineTo(20.8f)
        }
    }.build()
}

/** The drawer's "HRM" glyph: two people over a shared shoulder line. */
private val HrmIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Hrm",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The two heads.
            moveTo(10.7f, 8.4f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, -4.4f, 0f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, 4.4f, 0f)
            close()
            moveTo(17.7f, 8.4f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, -4.4f, 0f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, 4.4f, 0f)
            close()
            // Their shared shoulders.
            moveTo(3.8f, 16.8f)
            arcToRelative(8.2f, 4.2f, 0f, false, true, 16.4f, 0f)
        }
    }.build()
}

/** The drawer's "Admin" glyph: a shield with a tick inside it. */
private val AdminIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Admin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The shield.
            moveTo(12f, 3f)
            lineTo(18.8f, 5.8f)
            verticalLineTo(11.5f)
            quadTo(18.8f, 17.2f, 12f, 20.8f)
            quadTo(5.2f, 17.2f, 5.2f, 11.5f)
            verticalLineTo(5.8f)
            close()
            // The tick.
            moveTo(9.4f, 11.6f)
            lineTo(11.4f, 13.6f)
            lineTo(15.2f, 9.6f)
        }
    }.build()
}

/** The drawer's "Invoice" glyph: a receipt with a torn edge and line items. */
private val InvoiceIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Invoice",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The receipt, its bottom torn into a zig-zag.
            moveTo(7f, 3f)
            horizontalLineTo(17f)
            quadTo(18.5f, 3f, 18.5f, 4.5f)
            verticalLineTo(19.8f)
            lineTo(15.25f, 18.3f)
            lineTo(12f, 19.8f)
            lineTo(8.75f, 18.3f)
            lineTo(5.5f, 19.8f)
            verticalLineTo(4.5f)
            quadTo(5.5f, 3f, 7f, 3f)
            close()
            // The line items.
            moveTo(8.5f, 7.6f)
            horizontalLineTo(15.5f)
            moveTo(8.5f, 10.6f)
            horizontalLineTo(15.5f)
            moveTo(8.5f, 13.6f)
            horizontalLineTo(12.5f)
        }
    }.build()
}

/** One drawer destination as data, so the user's saved order can place it. */
private data class DrawerEntry(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selected: Boolean,
    val route: String,
)

/**
 * A divider the user placed between menus on the Arrange Menu page: a plain
 * rule when unnamed, a small uppercase heading when named — as on the web.
 */
@Composable
private fun DrawerSectionDivider(label: String) {
    if (label.isBlank()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
        )
    } else {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 2.dp),
        )
    }
}

/**
 * One drawer destination with the brand colours: the selected item sits in a
 * filled teal (primary) block with white content; unselected items stay quiet.
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
            Text(label, fontWeight = if (selected) AppFontWeight.SemiBold else AppFontWeight.Normal)
        },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        // M3's default drawer item is a full pill, the one shape the theme's
        // `shapes` mapping can't reach — pin it to the app-wide radius token.
        shape = AppShape,
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
