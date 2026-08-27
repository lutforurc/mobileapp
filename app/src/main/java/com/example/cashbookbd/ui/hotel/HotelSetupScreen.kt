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
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.theme.appColors

/** One tab of the web's Rooms & Seats Setup, as a row that opens its list. */
private data class SetupTab(
    val listKey: String,
    val title: String,
    val subtitle: String,
    val anyOf: List<String>,
)

private val TABS = listOf(
    SetupTab(
        "hotelBuildings", "Buildings",
        "The blocks the property is made of. A floor sits in one of these.",
        listOf("hotel.building.view"),
    ),
    SetupTab(
        "hotelFloors", "Floors",
        "Each floor of a building, and what its rooms are numbered from.",
        listOf("hotel.floor.view"),
    ),
    SetupTab(
        "hotelRoomTypes", "Room Types",
        "What a room is: how many it holds, whether it is sold whole or by the seat, and for how much.",
        listOf("hotel.room.type.view"),
    ),
    SetupTab(
        "hotelChargeTypes", "Charge Types",
        "What may go on a bill, and which income head each one earns into.",
        listOf("hotel.charge.type.view"),
    ),
)

/**
 * Rooms & Seats Setup: the four lists the web lays out as tabs of one screen.
 *
 * The rooms and seats themselves (the web's elevation grid, where a floor of
 * twelve is drawn and numbered in one go) are not here yet — they are a
 * drawing, and this phase is the master data underneath it.
 */
@Composable
fun HotelSetupScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val tabs = TABS.filter { Permissions.hasAny(sessionState.permissions, it.anyOf) }

    AuthenticatedShell(
        title = "Rooms & Seats Setup",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any hotel setup screen.",
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
            items(tabs) { tab ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.appListView(tab.listKey)) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tab.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = tab.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
