package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.cashbookbd.customer.CustomerItem
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList

/**
 * The "Customers" parent section. Lists every customer screen the user is
 * permitted to open (from [CustomerMenu.visible]); each routes to its list.
 */
@Composable
fun CustomerHomeScreen(
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
        "customer-supplier", CustomerMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Customers",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any customer screens.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "customer-supplier",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("customer-supplier", subPrefs),
        ) { item ->
            CustomerRowItem(
                item = item,
                onClick = {
                    // The customer list and Bank Opening are bespoke screens;
                    // the rest use the shared read-only list.
                    val route = when (item.key) {
                        "customers" -> Routes.CUSTOMERS_LIST
                        "bankOpening" -> Routes.BANK_OPENING
                        "customerOpeningImport" -> com.example.cashbookbd.navigation.ParityRoutes.CUSTOMER_OPENING_IMPORT
                        else -> Routes.appListView(item.key)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}

@Composable
private fun CustomerRowItem(item: CustomerItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = item.title, onClick = onClick)
}
