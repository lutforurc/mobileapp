package com.example.cashbookbd.ui.hotel

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
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelItem
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.theme.appColors

/**
 * The Hotel section, mirroring the web's sidebar group: the screens the front
 * desk opens daily first, the setup that is sat down to once at the end.
 */
@Composable
fun HotelHomeScreen(
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
        "hotel", HotelMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Hotel",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any hotel screen.",
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
                HotelRowItem(
                    item = item,
                    onClick = {
                        when (item.key) {
                            HotelMenu.DASHBOARD_KEY -> navController.navigate(Routes.HOTEL_DASHBOARD)
                            HotelMenu.SETUP_KEY -> navController.navigate(Routes.HOTEL_SETUP)
                            HotelMenu.BOOKINGS_KEY -> navController.navigate(Routes.HOTEL_BOOKINGS)
                            HotelMenu.HALL_BOOKINGS_KEY -> navController.navigate(HotelMenu.ROUTE_HALL_BOOKING)
                            HotelMenu.CALENDAR_KEY -> navController.navigate(Routes.HOTEL_CALENDAR)
                            HotelMenu.HOUSEKEEPING_KEY -> navController.navigate(Routes.HOTEL_HOUSEKEEPING)
                            HotelMenu.REPORTS_KEY -> navController.navigate(Routes.HOTEL_REPORTS)
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HotelRowItem(item: HotelItem, onClick: () -> Unit) {
    // Every entry opens a screen now (phase 3 landed 2026-09-06); the flag
    // stays so a future entry can be listed before it is built.
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
                if (!ready) {
                    Text(
                        text = "Not available in the app yet — use the web for now.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
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
