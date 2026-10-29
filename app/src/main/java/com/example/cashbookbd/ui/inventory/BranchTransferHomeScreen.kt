package com.example.cashbookbd.ui.inventory

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
import com.example.cashbookbd.inventory.BranchTransferItem
import com.example.cashbookbd.inventory.BranchTransferMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.ParityRoutes
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.ui.theme.appColors

/**
 * The "Branch Transfer" parent section, mirroring the web's sidebar group:
 * the movement forms, the challan register, and the three transfer reports.
 */
@Composable
fun BranchTransferHomeScreen(
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
        "branch-transfer", BranchTransferMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Branch Transfer",
        currentRoute = Routes.BRANCH_TRANSFER,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any transfer screen.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "branch-transfer",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("branch-transfer", subPrefs),
        ) { item ->
            BranchTransferRowItem(
                item = item,
                onClick = {
                    val route = when (item.key) {
                        BranchTransferMenu.BRANCH_ISSUE_KEY,
                        BranchTransferMenu.BRANCH_RECEIVE_KEY,
                        BranchTransferMenu.MATERIAL_ISSUE_KEY,
                        -> Routes.inventoryView(item.key)
                        BranchTransferMenu.TRANSFER_LIST_KEY -> ParityRoutes.TRANSFER_LIST
                        BranchTransferMenu.RECEIVE_LIST_KEY -> ParityRoutes.RECEIVE_LIST
                        BranchTransferMenu.PRODUCT_OUT_KEY -> ParityRoutes.PRODUCT_OUT
                        BranchTransferMenu.PRODUCT_OUT_REASONS_KEY -> ParityRoutes.PRODUCT_OUT_REASONS
                        BranchTransferMenu.PRODUCT_OUT_REGISTER_KEY -> ParityRoutes.PRODUCT_OUT_REGISTER
                        BranchTransferMenu.PRODUCT_IN_KEY -> ParityRoutes.PRODUCT_IN
                        BranchTransferMenu.PRODUCT_IN_REASONS_KEY -> ParityRoutes.PRODUCT_IN_REASONS
                        BranchTransferMenu.PRODUCT_IN_REGISTER_KEY -> ParityRoutes.PRODUCT_IN_REGISTER
                        // The three reports ride the generic engine.
                        else -> Routes.reportView(item.key)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}

@Composable
private fun BranchTransferRowItem(item: BranchTransferItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = item.title, onClick = onClick)
}
