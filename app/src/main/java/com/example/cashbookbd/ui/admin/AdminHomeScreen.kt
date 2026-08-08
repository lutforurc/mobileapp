package com.example.cashbookbd.ui.admin

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.admin.AdminItem
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes

/**
 * The "Admin" parent section. Lists every admin screen the user is permitted to
 * open (from [AdminMenu.visible]); the supported ones route to their form.
 */
@Composable
fun AdminHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    // The user's own inside-the-menu arrangement (the web's sidebar-sub).
    val menuPrefs = remember { ServiceLocator.provideMenuPreferencesRepository(context) }
    val subPrefs by menuPrefs.subState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { menuPrefs.refreshSub() }

    val items = com.example.cashbookbd.navigation.WebMenuIds.arrange(
        "admin", AdminMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Admin",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any admin screens.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { item ->
                AdminRowItem(
                    item = item,
                    onClick = {
                        val route = when {
                            item.key == AdminMenu.HIGHLIGHT_RULES_KEY -> Routes.HIGHLIGHT_RULES
                            // Bespoke User List (list + edit + temp password); wins
                            // over the shared read-only list its key also matches.
                            item.key == AdminMenu.USER_LIST_KEY -> Routes.USER_LIST
                            // Bespoke Role & Permission management (picker + toggles).
                            item.key == AdminMenu.ROLES_KEY -> Routes.ROLES
                            item.key == AdminMenu.ADD_ROLES_KEY -> Routes.ADD_ROLE
                            // Software Information's single-record form.
                            item.key == "softwareInfo" -> Routes.SOFTWARE_INFO
                            item.key == "arrangeMenu" -> Routes.ARRANGE_MENU
                            item.key == "productTracking" -> Routes.PRODUCT_TRACKING_SETTINGS
                            item.key == "inventorySystems" -> Routes.INVENTORY_SYSTEMS
                            item.key == "inAppMessages" -> Routes.IN_APP_MESSAGES
                            item.key == "orderWithTransaction" -> Routes.ORDER_WITH_TRANSACTION
                            item.key == "averagePrice" -> Routes.AVERAGE_PRICE
                            item.key == "smsLogs" -> Routes.SMS_LOGS
                            item.key == "adminNotifications" -> Routes.ADMIN_NOTIFICATIONS
                            item.key == "addPermission" -> Routes.ADD_PERMISSION
                            item.key == "addGroupReport" -> Routes.GROUP_REPORT_SETUP
                            AppLists.byKey(item.key) != null -> Routes.appListView(item.key)
                            else -> Routes.adminView(item.key)
                        }
                        navController.navigate(route)
                    },
                )
            }
        }
    }
}

@Composable
private fun AdminRowItem(item: AdminItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                if (!item.supported) {
                    Text(
                        text = "Coming soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
