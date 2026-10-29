package com.example.cashbookbd.ui.products

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
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.products.ProductItem
import com.example.cashbookbd.products.ProductsMenu

/**
 * The "Products" parent section. Lists every products master-data screen the user
 * is permitted to open (from [ProductsMenu.visible]); each routes to its read-only
 * list via the shared list engine.
 */
@Composable
fun ProductsHomeScreen(
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
        "products", ProductsMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Products",
        currentRoute = Routes.PRODUCTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any products screens.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "products",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("products", subPrefs),
        ) { item ->
            ProductRowItem(
                item = item,
                onClick = {
                    navController.navigate(
                        when (item.key) {
                            ProductsMenu.OPENING_IMPORT_KEY ->
                                com.example.cashbookbd.navigation.ParityRoutes.PRODUCT_OPENING_IMPORT
                            ProductsMenu.STOCK_STATEMENT_KEY ->
                                com.example.cashbookbd.navigation.ParityRoutes.PRODUCT_STOCK_STATEMENT
                            else -> Routes.appListView(item.key)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ProductRowItem(item: ProductItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = item.title, onClick = onClick)
}
