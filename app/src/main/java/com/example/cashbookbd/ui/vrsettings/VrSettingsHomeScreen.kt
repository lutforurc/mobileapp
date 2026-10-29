package com.example.cashbookbd.ui.vrsettings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.vrsettings.VrSettingsItem
import com.example.cashbookbd.vrsettings.VrSettingsMenu

/**
 * The "VR Settings" parent section. Lists every voucher-settings screen the user
 * is permitted to open (from [VrSettingsMenu.visible]); each routes to its form.
 */
@Composable
fun VrSettingsHomeScreen(
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
        "vr_settings", VrSettingsMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "VR Settings",
        currentRoute = Routes.VR_SETTINGS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any voucher settings.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "vr_settings",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("vr_settings", subPrefs),
        ) { item ->
            VrSettingsRowItem(
                item = item,
                onClick = {
                    val route = when {
                        // Voucher History has its own screen (card feed).
                        item.key == "history" -> Routes.VOUCHER_HISTORY
                        item.key == "reportMismatch" -> com.example.cashbookbd.navigation.ParityRoutes.REPORT_MISMATCH
                        AppLists.byKey(item.key) != null -> Routes.appListView(item.key)
                        else -> Routes.vrSettingsView(item.key)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}

@Composable
private fun VrSettingsRowItem(item: VrSettingsItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(
        title = item.title,
        onClick = onClick,
        subtitle = if (!item.supported) "Coming soon" else null,
    )
}
