package com.example.cashbookbd.ui.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.cashbookbd.production.ProductionItem
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.MenuListRow
import com.example.cashbookbd.ui.theme.appColors

/**
 * The Production parent section. This slice lists the read-only reports; the
 * entry and sales/purchase forms follow as their screens are ported, and the
 * menu is gated on the branch's manufacture switch beside the permission.
 */
@Composable
fun ProductionHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val items = ProductionMenu.visible(sessionState.permissions)

    AuthenticatedShell(
        title = "Production",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any production screen.",
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
                ProductionRowItem(
                    item = item,
                    onClick = {
                        when (item.key) {
                            ProductionMenu.REGISTER_KEY ->
                                navController.navigate(ProductionMenu.ROUTE_REGISTER)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductionRowItem(item: ProductionItem, onClick: () -> Unit) {
    MenuListRow(title = item.title, onClick = onClick)
}
