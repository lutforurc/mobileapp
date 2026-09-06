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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.launch

/**
 * One tab of the web's Rooms & Seats Setup, as a row that opens it. A tab is
 * either a list on the shared engine ([listKey]) or a screen drawn by hand
 * ([route]) — the form whose fields depend on each other, the drawing, the
 * rate history — and never both.
 */
private data class SetupTab(
    val title: String,
    val subtitle: String,
    val anyOf: List<String>,
    val listKey: String? = null,
    val route: String? = null,
)

/** In the web's order: what a property is made of first, then what it offers, then the money. */
private val TABS = listOf(
    SetupTab(
        "Buildings",
        "The blocks the property is made of. A floor sits in one of these.",
        listOf("hotel.building.view"),
        listKey = "hotelBuildings",
    ),
    SetupTab(
        "Floors",
        "Each floor of a building, and what its rooms are numbered from.",
        listOf("hotel.floor.view"),
        listKey = "hotelFloors",
    ),
    SetupTab(
        "Room Types",
        "What a room is: how many it holds, whether it is sold whole or by the seat, and for how much.",
        listOf("hotel.room.type.view"),
        listKey = "hotelRoomTypes",
    ),
    SetupTab(
        "Facilities",
        "What a room may offer — AC, Wi-Fi, a projector. The company's one list, shared by every property.",
        listOf("hotel.resource.view"),
        listKey = "hotelFacilities",
    ),
    SetupTab(
        "Rooms & Seats",
        "The inventory. Every room is split into beds, because a bed is what a booking locks.",
        listOf("hotel.resource.view"),
        route = HotelMenu.ROUTE_ROOMS,
    ),
    SetupTab(
        "Sittings",
        "The parts of a day a hall is let for: a seminar takes the morning, a wedding the evening.",
        listOf("hotel.resource.view"),
        listKey = "hotelSlots",
    ),
    SetupTab(
        "Layout",
        "The property drawn: buildings side by side, floors stacked, every room a tile.",
        listOf("hotel.resource.view"),
        route = HotelMenu.ROUTE_LAYOUT,
    ),
    SetupTab(
        "Charges",
        "What may go on a bill, and which income head each one earns into.",
        listOf("hotel.charge.type.view"),
        listKey = "hotelChargeTypes",
    ),
    SetupTab(
        "Service Charge",
        "One rate, the property's own. VAT belongs to the item and is set on Room Types and Charges.",
        listOf("hotel.charge.type.view"),
        route = HotelMenu.ROUTE_TAX_RATES,
    ),
)

/**
 * Rooms & Seats Setup: the nine tabs the web lays out on one screen, each a
 * row here. The master data rides the shared list engine; the rooms form,
 * the drawing and the service charge have screens of their own, because a
 * form whose fields depend on each other and a drawing are not lists.
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
    val repository = remember { HotelSetupRepository.get(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var seeding by remember { mutableStateOf(false) }

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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs) { tab ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = tab.route ?: tab.listKey?.let { Routes.appListView(it) }
                                target?.let { navController.navigate(it) }
                            },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                            // A property set up after the standard list shipped
                            // starts with an empty tick list, and typing
                            // twenty-two rows to say a room has air conditioning
                            // is not a step anybody finishes. Adds what is
                            // missing and touches nothing that is there.
                            if (tab.listKey == "hotelFacilities") {
                                LinkButton(
                                    text = if (seeding) "Adding…" else "Add the standard twenty-two",
                                    enabled = !seeding,
                                    onClick = {
                                        seeding = true
                                        scope.launch {
                                            val result = repository.seedStandardFacilities()
                                            seeding = false
                                            when (result) {
                                                is Resource.Success -> snackbarHostState.showSnackbar(result.data)
                                                is Resource.Error -> {
                                                    if (result.isUnauthorized) onLogout()
                                                    else snackbarHostState.showSnackbar(result.message)
                                                }
                                                Resource.Loading -> Unit
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
