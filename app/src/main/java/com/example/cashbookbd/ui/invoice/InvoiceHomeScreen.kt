package com.example.cashbookbd.ui.invoice

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceItem
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList

/**
 * The "Invoice" parent section. Lists every invoice form the current user is
 * permitted to open (from [InvoiceMenu.visible]); each routes to its entry form.
 */
@Composable
fun InvoiceHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    // A Tiles and Sanitary branch has its own Sales/Purchase screens. The flag
    // comes from `user/current-branch` as on the web, but the choice also reads
    // the branch's trade name from the settings already in hand — so it cannot
    // be missed by that one request (see TilesBranch).
    val branchApi = remember { ServiceLocator.provideWebApi(context.applicationContext) }
    var branchInfo by remember { mutableStateOf(CurrentBranchCache.value) }
    LaunchedEffect(sessionState.settings?.branchId) {
        branchInfo = CurrentBranchCache.load(branchApi, force = true)
    }
    val isTiles = TilesBranch.isTiles(sessionState.settings, branchInfo)

    // The user's own inside-the-menu arrangement (the web's sidebar-sub).
    val menuPrefs = remember { ServiceLocator.provideMenuPreferencesRepository(context) }
    val subPrefs by menuPrefs.subState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { menuPrefs.refreshSub() }

    val items = com.example.cashbookbd.navigation.WebMenuIds.arrange(
        "invoice",
        InvoiceMenu.visible(
            sessionState.permissions,
            businessTypeId = sessionState.settings?.businessTypeId,
            deductStockOnDelivery = sessionState.settings?.deductStockOnDelivery == true,
        ),
        subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Invoice",
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any invoices.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "invoice",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("invoice", subPrefs),
        ) { item ->
            InvoiceRowItem(
                item = item,
                onClick = {
                    val route = when (item.key) {
                        InvoiceMenu.COMBINED_KEY -> Routes.COMBINED_INVOICE
                        InvoiceMenu.LABOUR_INVOICE_KEY -> Routes.LABOUR_INVOICE
                        InvoiceMenu.DELIVERY_KEY -> com.example.cashbookbd.navigation.ParityRoutes.delivery(null)
                        "purchaseImport" -> com.example.cashbookbd.navigation.ParityRoutes.PURCHASE_IMPORT
                        "salesImport" -> com.example.cashbookbd.navigation.ParityRoutes.SALES_IMPORT
                        "sales" -> if (isTiles) com.example.cashbookbd.navigation.ParityRoutes.TILES_SALES else Routes.invoiceView(item.key)
                        "purchase" -> if (isTiles) com.example.cashbookbd.navigation.ParityRoutes.TILES_PURCHASE else Routes.invoiceView(item.key)
                        "salesReturn" -> if (isTiles) com.example.cashbookbd.navigation.ParityRoutes.TILES_SALES_RETURN else Routes.invoiceView(item.key)
                        "purchaseReturn" -> if (isTiles) com.example.cashbookbd.navigation.ParityRoutes.TILES_PURCHASE_RETURN else Routes.invoiceView(item.key)
                        else -> Routes.invoiceView(item.key)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}

@Composable
private fun InvoiceRowItem(item: InvoiceItem, onClick: () -> Unit) {
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
