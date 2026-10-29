package com.example.cashbookbd.ui.transaction

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
import com.example.cashbookbd.transaction.TransactionItem
import com.example.cashbookbd.transaction.TransactionMenu

/**
 * The "Transaction" parent section. Lists every transaction form the current user
 * is permitted to open (from [TransactionMenu.visible]); each routes to its entry
 * form, except "Installments" which opens the Due Installments screen.
 */
@Composable
fun TransactionHomeScreen(
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
        "transaction", TransactionMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Transaction",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any transactions.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "transaction",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("transaction", subPrefs),
        ) { item ->
            TransactionRowItem(item = item, onClick = { navController.openTransaction(item) })
        }
    }
}

@Composable
private fun TransactionRowItem(item: TransactionItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = item.title, onClick = onClick)
}

/** Installments opens the customer installment schedule; the rest open a form. */
private fun NavHostController.openTransaction(item: TransactionItem) {
    // The four accounts screens have screens of their own (ui/accounts).
    val accounts = com.example.cashbookbd.accounts.AccountsMenu.byKey(item.key)
    val route = when {
        accounts != null -> accounts.route
        // The web's Installments page (receive per row) — the Due Installments
        // report stays under Reports.
        item.key == "installments" -> Routes.INSTALLMENTS
        else -> Routes.txnView(item.key)
    }
    navigate(route)
}
