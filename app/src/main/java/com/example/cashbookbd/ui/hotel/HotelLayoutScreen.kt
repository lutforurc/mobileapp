package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelSeatRow
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.data.repository.HotelTimes
import com.example.cashbookbd.data.repository.LayoutBuilding
import com.example.cashbookbd.data.repository.HotelLayoutFloor
import com.example.cashbookbd.data.repository.LayoutRoom
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a tile is coloured by — one meaning at a time. */
private val COLOUR_MODES = listOf(
    "room_type" to "Room type",
    "sale_mode" to "How it is sold",
    "status" to "Status",
)

/** Above this many, the pips stop being countable and a number reads better. */
private const val MAX_PIPS = 8

/** "5th Floor" is too wide for the gutter; "5F" is not. Ground is G, a basement B1. */
internal fun shortFloor(floorNo: Int?): String = when {
    floorNo == null -> "—"
    floorNo == 0 -> "G"
    floorNo < 0 -> "B${-floorNo}"
    else -> "${floorNo}F"
}

/** "14:00" as a person says it: 2:00 PM. Noon and midnight by name. */
internal fun clockTime(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val parts = value.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return value
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return value
    if (h == 12 && m == 0) return "12:00 noon"
    if (h == 0 && m == 0) return "midnight"
    val hour = if (h % 12 == 0) 12 else h % 12
    return "$hour:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}

data class HotelLayoutUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val buildings: List<LayoutBuilding> = emptyList(),
    val times: HotelTimes? = null,
    val mode: String = "room_type",
    val hideInactive: Boolean = false,
    val selected: LayoutRoom? = null,
    val seats: List<HotelSeatRow> = emptyList(),
    val seatsLoading: Boolean = false,
    val seatDrafts: Map<Long, String> = emptyMap(),
    val savingSeatId: Long? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelLayoutViewModel(
    private val repository: HotelSetupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelLayoutUiState())
    val uiState: StateFlow<HotelLayoutUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.fetchLayout(branchId = null)) {
                is Resource.Success -> _uiState.update { s ->
                    val everyRoom = r.data.buildings.flatMap { b -> b.floors.flatMap { it.rooms } + b.unfloored }
                    s.copy(
                        isLoading = false,
                        buildings = r.data.buildings,
                        times = r.data.times,
                        // The panel is fed from the grid's own row, so a reload
                        // hands it the new one; gone means deleted — close it.
                        selected = s.selected?.let { sel -> everyRoom.firstOrNull { it.id == sel.id } },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMode(mode: String) = _uiState.update { it.copy(mode = mode) }

    fun onHideInactive(on: Boolean) = _uiState.update { it.copy(hideInactive = on) }

    /** The beds are not in the layout payload — the grid never draws them singly — so they are read on open. */
    fun openRoom(room: LayoutRoom) {
        _uiState.update { it.copy(selected = room, seats = emptyList(), seatDrafts = emptyMap()) }
        if (!room.isHall) loadSeats(room.id)
    }

    private fun loadSeats(roomId: Long) {
        _uiState.update { it.copy(seatsLoading = true) }
        viewModelScope.launch {
            when (val r = repository.fetchSeats(roomId)) {
                is Resource.Success -> _uiState.update {
                    if (it.selected?.id == roomId) it.copy(seatsLoading = false, seats = r.data, seatDrafts = emptyMap()) else it
                }
                is Resource.Error -> _uiState.update {
                    it.copy(seatsLoading = false, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closePanel() = _uiState.update { it.copy(selected = null, seats = emptyList(), seatDrafts = emptyMap()) }

    fun onSeatDraft(seatId: Long, value: String) = _uiState.update { it.copy(seatDrafts = it.seatDrafts + (seatId to value)) }

    /** Repricing a bed refreshes the grid underneath too, so the header's rent range does not go stale. */
    fun saveSeat(seat: HotelSeatRow) {
        val draft = _uiState.value.seatDrafts[seat.id] ?: return
        val roomId = _uiState.value.selected?.id ?: return
        _uiState.update { it.copy(savingSeatId = seat.id) }
        viewModelScope.launch {
            when (val r = repository.updateSeat(seat.id, seat.name, draft, seat.status)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(savingSeatId = null, message = "Seat ${seat.code} saved") }
                    loadSeats(roomId)
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(savingSeatId = null, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { HotelLayoutViewModel(repository = HotelSetupRepository.get(context)) }
        }
    }
}

/** What a tile looks like: a fill, an edge, and the badge the legend and the tile agree on. */
private data class TileLook(val colour: Color, val badge: String, val label: String)

/**
 * The property drawn as buildings — an elevation, one card each, floors
 * stacked top floor first.
 *
 * Every tile carries a short BADGE as well as a colour, and that is not
 * decoration: colour alone cannot carry the meaning, and the badge is what
 * the legend and the tile agree on when the colour is gone. One meaning at a
 * time — the chips above pick which.
 */
@Composable
fun HotelLayoutScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelLayoutViewModel = viewModel(
        factory = HotelLayoutViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val allowed = Permissions.hasAny(sessionState.permissions, listOf("hotel.resource.view"))
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    // Eight colours for the things there can be any number of — room types.
    // Theme accents, never literals, and no red/green pair among them.
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.appColors.info,
        MaterialTheme.appColors.success,
        MaterialTheme.appColors.warning,
        MaterialTheme.appColors.danger,
        MaterialTheme.appColors.textMuted,
    )
    val neutral = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.appColors.textMuted
    val success = MaterialTheme.appColors.success
    val warning = MaterialTheme.appColors.warning
    val secondary = MaterialTheme.colorScheme.secondary

    val everyRoom = remember(state.buildings) {
        state.buildings.flatMap { b -> b.floors.flatMap { it.rooms } + b.unfloored }
    }
    // Colours come from the property's OWN list of types, by id, so renaming
    // one does not repaint the building — and from all of them, so hiding the
    // inactive rooms does not shift every colour along by one.
    val typeIndex = remember(everyRoom) {
        everyRoom.mapNotNull { it.roomTypeId }.distinct().sorted()
            .mapIndexed { i, id -> id to i % palette.size }.toMap()
    }
    val liveBuildings = remember(state.buildings) { state.buildings.mapNotNull { it.live() } }
    val liveRooms = remember(liveBuildings) { liveBuildings.flatMap { b -> b.floors.flatMap { it.rooms } + b.unfloored } }
    val inactiveCount = everyRoom.size - liveRooms.size
    val drawn = if (state.hideInactive) liveBuildings else state.buildings
    val drawnRooms = if (state.hideInactive) liveRooms else everyRoom

    val lookOf: (LayoutRoom) -> TileLook = { room ->
        when {
            // An inactive room is drawn as inactive whatever the chips say.
            !room.isActive -> TileLook(muted, room.roomTypeCode.ifBlank { "I" }, "Inactive — kept for older bookings")
            state.mode == "status" -> TileLook(success, "A", "Active")
            state.mode == "sale_mode" -> when (room.saleMode) {
                "whole" -> TileLook(neutral, "W", "Whole room only")
                "seat" -> TileLook(secondary, "S", "By the seat only")
                "both" -> TileLook(warning, "B", "Either way")
                else -> TileLook(neutral, "?", room.saleMode)
            }
            else -> {
                val position = room.roomTypeId?.let { typeIndex[it] }
                TileLook(
                    colour = position?.let { palette[it] } ?: neutral,
                    // The type's own short code — STD, DLX — rather than an
                    // invented letter: "Standard" and "Suite" both begin with S.
                    badge = room.roomTypeCode.ifBlank { if (room.roomTypeId != null) "·" else "—" },
                    label = room.roomType.ifBlank { "No room type" },
                )
            }
        }
    }
    val legend = remember(drawnRooms, state.mode, typeIndex) {
        drawnRooms.map(lookOf).distinctBy { it.badge + it.label }
    }

    AuthenticatedShell(
        title = "Layout",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!allowed) {
            NoAccess("You don't have access to the layout.")
            return@AuthenticatedShell
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.buildings.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                state.error != null && state.buildings.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error.orEmpty(), color = MaterialTheme.appColors.textOnScreen, textAlign = TextAlign.Center)
                            LinkButton(text = "Retry", onClick = viewModel::load)
                        }
                    }

                state.buildings.isEmpty() ->
                    // A sentence, not an empty grid: an empty grid reads as a
                    // hotel with nothing in it rather than a property nobody
                    // has described yet.
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing to draw yet. Add a building on the Buildings tab, then its floors and rooms.",
                            color = MaterialTheme.appColors.textOnScreenMuted, textAlign = TextAlign.Center,
                        )
                    }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Colour by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
                            COLOUR_MODES.forEach { (id, label) ->
                                FilterChip(selected = state.mode == id, onClick = { viewModel.onMode(id) }, label = { Text(label) })
                            }
                        }
                        // Offered only where there is something to hide.
                        if (inactiveCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(checked = state.hideInactive, onCheckedChange = viewModel::onHideInactive)
                                Text("Hide inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreen)
                                if (state.hideInactive) {
                                    Text(
                                        "$inactiveCount inactive ${if (inactiveCount == 1) "room" else "rooms"} hidden",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.appColors.textOnScreenMuted,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                    // The two times ON the drawing rather than in a settings
                    // screen: the gap between them is what keeps a turnover day
                    // from selling the same room twice.
                    state.times?.let { times ->
                        item {
                            Text(
                                "Check in ${clockTime(times.checkIn)} · Check out ${clockTime(times.checkOut)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreen,
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            legend.forEach { look ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Badge(look)
                                    Text(look.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreen)
                                }
                            }
                        }
                    }
                    if (drawn.isEmpty()) {
                        item {
                            Text(
                                "Nothing on this property is in use. Untick Hide inactive to see the $inactiveCount " +
                                    "${if (inactiveCount == 1) "room" else "rooms"} that are switched off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                    }
                    items(drawn, key = { it.id }) { building ->
                        BuildingCard(building = building, lookOf = lookOf, onSelect = viewModel::openRoom)
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.selected?.let { room ->
        RoomPanel(
            room = room,
            seats = state.seats,
            seatsLoading = state.seatsLoading,
            seatDrafts = state.seatDrafts,
            savingSeatId = state.savingSeatId,
            onSeatDraft = viewModel::onSeatDraft,
            onSaveSeat = viewModel::saveSeat,
            onEdit = {
                viewModel.closePanel()
                navController.navigate(HotelMenu.roomForm(room.id))
            },
            onClose = viewModel::closePanel,
        )
    }
}

/**
 * "Hide inactive" — only what can be let today. Read at every level: a live
 * room on a switched-off floor cannot be let either, and a floor or building
 * left with nothing goes with it. The header counts are re-summarised, because
 * "20 rooms · 40 beds" over a card drawing eighteen is simply wrong.
 */
private fun LayoutBuilding.live(): LayoutBuilding? {
    if (status != 1) return null
    val floors = floors.filter { it.status == 1 }
        .map { f -> f.copy(rooms = f.rooms.filter { it.isActive }) }
        .filter { it.rooms.isNotEmpty() }
    val unfloored = unfloored.filter { it.isActive }
    if (floors.isEmpty() && unfloored.isEmpty()) return null
    val rooms = floors.flatMap { it.rooms } + unfloored
    val nights = rooms.filterNot { it.isHall }
    val halls = rooms.filter { it.isHall }
    val rents = nights.mapNotNull { it.rent }
    val seatRents = nights.mapNotNull { it.seatRentMin } + nights.mapNotNull { it.seatRentMax }
    return copy(
        floors = floors,
        unfloored = unfloored,
        roomsCount = nights.size,
        bedsCount = nights.sumOf { it.beds },
        hallsCount = halls.size,
        seatsCount = halls.sumOf { it.capacity },
        rentMin = rents.minOrNull(),
        rentMax = rents.maxOrNull(),
        seatRentMin = seatRents.minOrNull(),
        seatRentMax = seatRents.maxOrNull(),
    )
}

@Composable
private fun BuildingCard(
    building: LayoutBuilding,
    lookOf: (LayoutRoom) -> TileLook,
    onSelect: (LayoutRoom) -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    val rent = building.rentMin?.let { min ->
        val max = building.rentMax ?: min
        if (min == max) AmountFormat.format(min) else "${AmountFormat.format(min)} – ${AmountFormat.format(max)}"
    }
    val seatRent = building.seatRentMin?.let { min ->
        val max = building.seatRentMax ?: min
        (if (min == max) AmountFormat.format(min) else "${AmountFormat.format(min)} – ${AmountFormat.format(max)}") + "/bed"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(building.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (building.code.isNotBlank()) Text(building.code, style = MaterialTheme.typography.labelSmall, color = muted)
                if (building.status != 1) Text("· inactive", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            // Halls counted apart from rooms, and their SEATS apart from beds:
            // folded in, a block with two function spaces read as two rooms
            // with no beds and looked unfinished.
            val halls = if (building.hallsCount > 0) {
                " · ${building.hallsCount} ${if (building.hallsCount == 1) "hall" else "halls"}" +
                    (if (building.seatsCount > 0) " (${building.seatsCount} seats)" else "")
            } else ""
            Text(
                "${building.roomsCount} rooms · ${building.bedsCount} beds$halls" +
                    (rent?.let { " · $it" } ?: "") + (seatRent?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.text,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.appColors.divider)
            // Top floor first: the API sends them ground-first because that is
            // their natural order; standing them up is the drawing's business.
            val stacked = building.floors.asReversed()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stacked.forEach { floor ->
                    FloorRow(label = shortFloor(floor.floorNo), rooms = floor.rooms, dimmed = floor.status != 1, lookOf = lookOf, onSelect = onSelect)
                }
                // A resort's cottages: no floor, and none invented for them.
                if (building.unfloored.isNotEmpty()) {
                    FloorRow(label = "—", rooms = building.unfloored, dimmed = false, lookOf = lookOf, onSelect = onSelect)
                }
                if (stacked.isEmpty() && building.unfloored.isEmpty()) {
                    Text("No rooms in this building yet.", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FloorRow(
    label: String,
    rooms: List<LayoutRoom>,
    dimmed: Boolean,
    lookOf: (LayoutRoom) -> TileLook,
    onSelect: (LayoutRoom) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (dimmed) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The floor marker: bold, and carrying no colour of its own. On this
        // screen colour MEANS something, and ink alone says "label", not "data".
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.appColors.text,
            modifier = Modifier.width(36.dp),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (rooms.isEmpty()) {
                Text("no rooms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted, modifier = Modifier.padding(vertical = 8.dp))
            }
            rooms.forEach { room -> RoomTile(room = room, look = lookOf(room), onSelect = onSelect) }
        }
    }
}

/**
 * One room in the elevation: the number, a badge, and the beds as pips. Three
 * things and no more — the panel that opens on a tap is where detail belongs.
 */
@Composable
private fun RoomTile(room: LayoutRoom, look: TileLook, onSelect: (LayoutRoom) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val ink = MaterialTheme.appColors.text
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .background(look.colour.copy(alpha = 0.18f))
            .border(1.dp, look.colour, shape)
            .clickable { onSelect(room) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(room.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(look.badge.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.7f))
        }
        val active = room.activeBeds
        val switchedOff = (room.beds - active).coerceAtLeast(0)
        when {
            // Pips are BEDS. A hall has none; it has chairs, and four hundred
            // of those is a number rather than a row of marks.
            room.isHall -> {
                val sittings = room.sittings.orEmpty()
                Text(
                    (if (room.capacity > 0) "${room.capacity} seats" else "no seating") +
                        (if (sittings.isNotEmpty()) " · ${sittings.size} ${if (sittings.size == 1) "sitting" else "sittings"}" else " · no sittings"),
                    style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f), maxLines = 2,
                )
            }
            active > MAX_PIPS -> Text("$active beds", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(active) {
                    Box(Modifier.size(width = 3.dp, height = 8.dp).background(ink.copy(alpha = 0.7f), RoundedCornerShape(1.dp)))
                }
                // Kept rows, drawn hollow: a room cut from four beds to two
                // still has four, and the grid should not disagree with its form.
                repeat(minOf(switchedOff, MAX_PIPS - active)) {
                    Box(Modifier.size(width = 3.dp, height = 8.dp).border(1.dp, ink.copy(alpha = 0.3f), RoundedCornerShape(1.dp)))
                }
                if (active == 0 && switchedOff == 0) Box(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Badge(look: TileLook) {
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(look.colour.copy(alpha = 0.18f))
            .border(1.dp, look.colour, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(look.badge.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.appColors.textOnScreen, maxLines = 1)
    }
}

/**
 * One room, opened from the grid. Everything a tile cannot fit — the full
 * name, the rents, every bed and what each costs — lives here. A hall is
 * measured in chairs and sold by the sitting, so it gets no bed row and no
 * bed editor; its sittings belong to the property, on the Sittings tab.
 */
@Composable
private fun RoomPanel(
    room: LayoutRoom,
    seats: List<HotelSeatRow>,
    seatsLoading: Boolean,
    seatDrafts: Map<Long, String>,
    savingSeatId: Long?,
    onSeatDraft: (Long, String) -> Unit,
    onSaveSeat: (HotelSeatRow) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    val switchedOff = (room.beds - room.activeBeds).coerceAtLeast(0)
    val facts = buildList {
        add("Sold" to (if (room.isHall) "Let by the sitting" else saleModeLabel(room.saleMode)))
        // A dash, not 0.00: a room sold only by the bed has no whole-room price.
        add(
            (if (room.isHall) "Per sitting" else "Whole room") to
                (room.rent?.let { "${AmountFormat.format(it)} / ${if (room.isHall) "sitting" else "night"}" } ?: "—")
        )
        room.seatRentMin?.let { min ->
            val max = room.seatRentMax ?: min
            add("Per bed" to (if (min == max) "${AmountFormat.format(min)} / night" else "${AmountFormat.format(min)} – ${AmountFormat.format(max)} / night"))
        }
        add((if (room.isHall) "Seats" else "Holds") to "${room.capacity} ${if (room.isHall) "chair" else "guest"}${if (room.capacity == 1) "" else "s"}")
        if (!room.isHall) {
            add("Beds" to (if (switchedOff > 0) "${room.activeBeds} in use, $switchedOff switched off" else "${room.activeBeds}"))
        }
        add("Status" to (if (room.isActive) "Active" else "Inactive"))
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(room.displayName, style = MaterialTheme.typography.titleMedium)
                Text(room.roomType.ifBlank { "No room type" }, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                label == "Status" && room.isActive -> MaterialTheme.appColors.success
                                label == "Status" -> muted
                                else -> MaterialTheme.appColors.text
                            },
                        )
                    }
                }
                if (room.isHall) {
                    val sittings = room.sittings.orEmpty()
                    Text(
                        if (sittings.isEmpty()) "No sittings set — cannot be let" else "Sittings: ${sittings.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall, color = muted,
                    )
                }
                if (room.facilities.isNotEmpty()) {
                    Text(room.facilities.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (room.description.isNotBlank()) {
                    Text(room.description, style = MaterialTheme.typography.bodySmall, color = muted, fontStyle = FontStyle.Italic)
                }
                if (!room.isHall) {
                    if (seatsLoading && seats.isEmpty()) {
                        Text("Loading the beds…", style = MaterialTheme.typography.labelSmall, color = muted)
                    } else {
                        SeatEditor(
                            title = "Beds, priced one at a time",
                            seats = seats,
                            drafts = seatDrafts,
                            savingId = savingSeatId,
                            onDraft = onSeatDraft,
                            onSave = onSaveSeat,
                        )
                    }
                }
            }
        },
        // Editing the room itself happens on the form, where the rules already
        // live. A second copy here would be a second set to keep in step.
        confirmButton = { PrimaryButton(text = "Edit this room", onClick = onEdit, compact = true) },
        dismissButton = { LinkButton(text = "Close", onClick = onClose) },
    )
}
