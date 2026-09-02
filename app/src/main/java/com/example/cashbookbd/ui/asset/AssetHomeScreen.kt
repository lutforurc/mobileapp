package com.example.cashbookbd.ui.asset

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
import com.example.cashbookbd.asset.AssetItem
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.theme.appColors

/**
 * The Assets section: the web's one sidebar child ("Categories & Register")
 * opens a screen of tabs, and on a phone each tab is a screen of its own — so
 * they are listed here, in the web's order, filtered to what the user may see.
 *
 * The order is the order of the job: categories first (an asset cannot be
 * entered without one), then the register, then the yearly charge that reads
 * what both wrote.
 */
@Composable
fun AssetHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val items = AssetMenu.visible(sessionState.permissions)

    AuthenticatedShell(
        title = "Assets",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any asset screen.",
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
                AssetRowItem(
                    item = item,
                    onClick = {
                        when (item.key) {
                            AssetMenu.CATEGORIES_KEY -> navController.navigate(AssetMenu.ROUTE_CATEGORIES)
                            AssetMenu.REGISTER_KEY -> navController.navigate(AssetMenu.ROUTE_REGISTER)
                            AssetMenu.DEPRECIATION_KEY -> navController.navigate(AssetMenu.ROUTE_DEPRECIATION)
                            AssetMenu.SCHEDULE_KEY -> navController.navigate(AssetMenu.ROUTE_SCHEDULE)
                            AssetMenu.HANDOVERS_KEY -> navController.navigate(AssetMenu.ROUTE_HANDOVERS)
                            AssetMenu.VERIFICATION_KEY -> navController.navigate(AssetMenu.ROUTE_VERIFICATION)
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AssetRowItem(item: AssetItem, onClick: () -> Unit) {
    // Work in progress is the one tab still only on the web; it is listed so the
    // menu reads like the web's and says so, rather than opening an empty screen.
    val ready = item.supported

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ready) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ready) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.appColors.textMuted
                    },
                )
                Text(
                    text = if (ready) {
                        item.hint
                    } else {
                        "Not available in the app yet — use the web for now."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textMuted,
                )
            }
            if (ready) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
