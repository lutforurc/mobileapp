package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelDdlOption
import com.example.cashbookbd.data.repository.HotelResourceType
import com.example.cashbookbd.data.repository.HotelRoomDraft
import com.example.cashbookbd.data.repository.HotelSeatRow
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val STATUS_OPTIONS = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
)

private const val MAX_BULK = 100

/**
 * The numbers a run of rooms will be given: 301 asked for four times reads
 * back 301, 302, 303, 304. A-01 runs A-01..A-04, and a run started at 01
 * reaches 10 and never 010. A PREVIEW only — the server counts the run out
 * again from the same rule and is the one that decides.
 */
internal fun runOfCodes(start: String, count: Int): List<String> {
    val match = Regex("""^(.*?)(\d+)$""").find(start.trim()) ?: return emptyList()
    if (count < 1) return emptyList()
    val prefix = match.groupValues[1]
    val digits = match.groupValues[2]
    val first = digits.toLongOrNull() ?: return emptyList()
    return (0 until count).map { prefix + (first + it).toString().padStart(digits.length, '0') }
}

/**
 * Whether a facility belongs on the form for this kind of thing. `both` is
 * always in; an unknown kind shows everything rather than nothing, because
 * an empty tick list reads as "this property has no facilities set".
 */
private fun facilityFits(appliesTo: String, kind: String?): Boolean {
    if (appliesTo.isBlank() || appliesTo == "both") return true
    if (kind != "room" && kind != "hall") return true
    return appliesTo == kind
}

data class HotelRoomFormUiState(
    val roomId: Long? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val types: List<HotelResourceType> = emptyList(),
    val buildings: List<HotelDdlOption> = emptyList(),
    val floors: List<HotelDdlOption> = emptyList(),
    val roomTypes: List<HotelDdlOption> = emptyList(),
    val facilities: List<HotelDdlOption> = emptyList(),
    val displayName: String = "",
    val resourceTypeId: Long? = null,
    val buildingId: Long? = null,
    val floorId: Long? = null,
    val roomTypeId: Long? = null,
    val code: String = "",
    val name: String = "",
    val capacity: String = "2",
    val status: Int = 1,
    val saleMode: String = "whole",
    val rent: String = "",
    val seatCount: String = "1",
    val seatRent: String = "",
    val description: String = "",
    val facilityIds: Set<Long> = emptySet(),
    /** "Add a run of rooms": create only, and kept across saves — a property is set up floor after floor. */
    val several: Boolean = false,
    val count: String = "4",
    val seats: List<HotelSeatRow> = emptyList(),
    val seatDrafts: Map<Long, String> = emptyMap(),
    val savingSeatId: Long? = null,
    val isSaving: Boolean = false,
    val isSeeding: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedType: HotelResourceType? get() = types.firstOrNull { it.id == resourceTypeId }
    /** Describing a room, as opposed to a hall or a ticket. Read off the KIND, never guessed. */
    val isRoom: Boolean get() = selectedType?.isRoom == true
    val facilityKind: String? get() = when {
        selectedType?.isRoom == true -> "room"
        selectedType?.isHall == true -> "hall"
        else -> null
    }
    val facilityChoices: List<HotelDdlOption> get() = facilities.filter { facilityFits(it.appliesTo, facilityKind) }
    val needsWholeRent: Boolean get() = saleMode == "whole" || saleMode == "both"
    val needsSeatRent: Boolean get() = saleMode == "seat" || saleMode == "both"
    /** Making a run, as opposed to one room. Never true over a room that exists. */
    val bulk: Boolean get() = several && roomId == null
    val run: List<String> get() = if (bulk) runOfCodes(code, count.toIntOrNull() ?: 0) else emptyList()
}

class HotelRoomFormViewModel(
    private val repository: HotelSetupRepository,
    private val roomId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelRoomFormUiState(roomId = roomId))
    val uiState: StateFlow<HotelRoomFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Types first: a new room defaults to the "room" kind, and the
            // facility list is filtered by whatever kind is chosen.
            when (val types = repository.fetchResourceTypes()) {
                is Resource.Success -> _uiState.update { s ->
                    s.copy(
                        types = types.data,
                        resourceTypeId = s.resourceTypeId
                            ?: types.data.firstOrNull { it.isRoom }?.id.takeIf { roomId == null },
                    )
                }
                is Resource.Error -> fail(types)
                Resource.Loading -> Unit
            }
        }
        viewModelScope.launch {
            when (val r = repository.fetchBuildingDdl()) {
                is Resource.Success -> _uiState.update { it.copy(buildings = r.data) }
                is Resource.Error -> fail(r)
                Resource.Loading -> Unit
            }
        }
        viewModelScope.launch {
            when (val r = repository.fetchRoomTypeDdl()) {
                is Resource.Success -> _uiState.update { it.copy(roomTypes = r.data) }
                is Resource.Error -> fail(r)
                Resource.Loading -> Unit
            }
        }
        loadFacilities()
        if (roomId == null) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            loadRoom(roomId)
        }
    }

    private fun fail(error: Resource.Error) = _uiState.update {
        it.copy(sessionExpired = it.sessionExpired || error.isUnauthorized, message = it.message ?: error.message)
    }

    private fun loadFacilities() {
        viewModelScope.launch {
            when (val r = repository.fetchFacilityDdl()) {
                is Resource.Success -> _uiState.update { it.copy(facilities = r.data) }
                is Resource.Error -> fail(r)
                Resource.Loading -> Unit
            }
        }
    }

    /** An edit arrives with its beds and its ticks; the form is filled from that, not from the list row. */
    private fun loadRoom(id: Long) {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repository.fetchResource(id)) {
                is Resource.Success -> {
                    val room = r.data
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            displayName = room.displayName,
                            resourceTypeId = room.resourceTypeId,
                            buildingId = room.buildingId,
                            floorId = room.floorId,
                            roomTypeId = room.roomTypeId,
                            code = room.code,
                            name = room.name,
                            capacity = room.capacity.toString(),
                            status = room.status,
                            saleMode = room.saleMode,
                            rent = room.rent.trimAmount(),
                            seatCount = room.activeSeatCount.coerceAtLeast(1).toString(),
                            seatRent = "",
                            description = room.description,
                            facilityIds = room.facilityIds.toSet(),
                            seats = room.seats,
                            seatDrafts = emptyMap(),
                            several = false,
                        )
                    }
                    room.buildingId?.let { loadFloors(it) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, loadError = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The floor list belongs to one building — emptied first, so a floor from the last block cannot linger. */
    private fun loadFloors(buildingId: Long) {
        _uiState.update { it.copy(floors = emptyList()) }
        viewModelScope.launch {
            when (val r = repository.fetchFloorDdl(buildingId)) {
                is Resource.Success -> _uiState.update { it.copy(floors = r.data) }
                is Resource.Error -> fail(r)
                Resource.Loading -> Unit
            }
        }
    }

    fun onType(id: Long?) = _uiState.update { it.copy(resourceTypeId = id) }

    fun onBuilding(id: Long?) {
        // The floor belonged to the old building: clearing it is what stops a
        // room claiming the annexe's first floor while sitting in the main block.
        _uiState.update { it.copy(buildingId = id, floorId = null, floors = emptyList()) }
        id?.let { loadFloors(it) }
    }

    fun onFloor(id: Long?) = _uiState.update { it.copy(floorId = id) }

    /**
     * Picking a type fills the form in from its defaults — a deliberate act
     * that means "start from this". Everything it fills stays editable.
     */
    fun onRoomType(id: Long?) = _uiState.update { s ->
        val chosen = s.roomTypes.firstOrNull { it.value == id }
        if (chosen == null) {
            s.copy(roomTypeId = null)
        } else {
            s.copy(
                roomTypeId = chosen.value,
                capacity = chosen.capacity?.toString() ?: s.capacity,
                saleMode = chosen.defaultSaleMode ?: s.saleMode,
                seatCount = chosen.defaultSeatCount?.toString() ?: s.seatCount,
                rent = chosen.defaultWholeRent?.trimAmount().orEmpty(),
                seatRent = chosen.defaultSeatRent?.trimAmount().orEmpty(),
            )
        }
    }

    fun onCode(v: String) = _uiState.update { it.copy(code = v) }
    fun onName(v: String) = _uiState.update { it.copy(name = v) }
    fun onCapacity(v: String) = _uiState.update { it.copy(capacity = v) }
    fun onStatus(v: Int) = _uiState.update { it.copy(status = v) }
    fun onSaleMode(v: String) = _uiState.update { it.copy(saleMode = v) }
    fun onRent(v: String) = _uiState.update { it.copy(rent = v) }
    fun onSeatCount(v: String) = _uiState.update { it.copy(seatCount = v) }
    fun onSeatRent(v: String) = _uiState.update { it.copy(seatRent = v) }
    fun onDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun onSeveral(v: Boolean) = _uiState.update { it.copy(several = v) }
    fun onCount(v: String) = _uiState.update { it.copy(count = v) }

    /**
     * Ticks are toggled, never rebuilt from what is on screen: the list drawn
     * is filtered by kind, and switching a room to a hall and back must not
     * quietly strip its wardrobe and its television.
     */
    fun toggleFacility(id: Long) = _uiState.update { s ->
        s.copy(facilityIds = if (id in s.facilityIds) s.facilityIds - id else s.facilityIds + id)
    }

    fun save() {
        val s = _uiState.value
        val typeId = s.resourceTypeId ?: return say("Choose what kind of resource this is")
        val buildingId = s.buildingId ?: return say("Choose which building it is in")
        if (s.code.isBlank()) {
            return say(if (s.bulk) "The run needs a number to start from" else "The room needs a number")
        }
        if (s.bulk && s.run.isEmpty()) {
            return say("The first room number has to end in a number — 301, or A-01 — so the rest can be counted out")
        }
        // Checked here as well as on the server: a message the moment the
        // field is wrong beats one that appears after a round trip.
        if (s.isRoom && s.needsWholeRent && s.rent.trim().toDoubleOrNull() == null) {
            return say("This room is sold whole, so it needs a whole-room rent")
        }
        if (s.isRoom && s.needsSeatRent && s.seatRent.trim().toDoubleOrNull() == null && s.roomId == null) {
            return say("This room is sold by the seat, so its beds need a rent")
        }
        val draft = HotelRoomDraft(
            resourceTypeId = typeId,
            buildingId = buildingId,
            floorId = s.floorId,
            roomTypeId = s.roomTypeId,
            code = s.code,
            name = s.name,
            saleMode = s.saleMode,
            capacity = s.capacity.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
            rent = s.rent,
            seatCount = if (s.isRoom) (s.seatCount.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1) else null,
            seatRent = if (s.isRoom) s.seatRent else null,
            description = s.description,
            facilityIds = s.facilityIds.toList(),
            status = s.status,
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (s.bulk) {
                repository.bulkStoreResources(draft, s.code, s.run.size.coerceAtMost(MAX_BULK))
            } else {
                repository.saveResource(s.roomId, draft)
            }
            when (result) {
                is Resource.Success -> {
                    if (s.roomId != null) {
                        _uiState.update { it.copy(isSaving = false, message = result.data) }
                        // Re-read, so the bed list shows what the save did.
                        loadRoom(s.roomId)
                    } else {
                        // Rooms are added one floor after another: the building,
                        // floor, type, rents and TICKS are kept; the number, the
                        // name and the description are one room's own.
                        _uiState.update {
                            it.copy(isSaving = false, message = result.data, code = "", name = "", description = "")
                        }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSeatDraft(seatId: Long, value: String) = _uiState.update { it.copy(seatDrafts = it.seatDrafts + (seatId to value)) }

    /** One bed's own rent. The room form's figure never overwrites this. */
    fun saveSeat(seat: HotelSeatRow) {
        val draft = _uiState.value.seatDrafts[seat.id] ?: return
        _uiState.update { it.copy(savingSeatId = seat.id) }
        viewModelScope.launch {
            when (val r = repository.updateSeat(seat.id, seat.name, draft, seat.status)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(savingSeatId = null, message = "Seat ${seat.code} saved") }
                    roomId?.let { loadRoom(it) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(savingSeatId = null, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The standard twenty-two, copied into this company; then the list is read again. */
    fun seedFacilities() {
        _uiState.update { it.copy(isSeeding = true) }
        viewModelScope.launch {
            when (val r = repository.seedStandardFacilities()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSeeding = false, message = r.data) }
                    loadFacilities()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSeeding = false, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun say(text: String) = _uiState.update { it.copy(message = text) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /** "3000.00" as typed: "3000". The server's decimals are not what a clerk typed. */
    private fun String.trimAmount(): String {
        val d = trim().toDoubleOrNull() ?: return trim()
        return if (d == d.toLong().toDouble()) d.toLong().toString() else trim()
    }

    companion object {
        fun provideFactory(context: Context, roomId: Long?) = viewModelFactory {
            initializer { HotelRoomFormViewModel(repository = HotelSetupRepository.get(context), roomId = roomId) }
        }
    }
}

/**
 * One room, or a whole floor of them.
 *
 * The rule that shapes the form: the SEAT is the inventory, not the room. A
 * four-bed dormitory is four rows pointing at their room, and selling the room
 * whole is booking all four at once — so the form asks for a bed count rather
 * than offering a seat screen, and saving a room writes its beds with it.
 *
 * Rooms on a floor differ only in their number, so "Add a run" swaps the Name
 * field for a count and leaves everything else where it was.
 */
@Composable
fun HotelRoomFormScreen(
    roomId: Long?,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelRoomFormViewModel = viewModel(
        factory = HotelRoomFormViewModel.provideFactory(LocalContext.current, roomId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val allowed = Permissions.hasAny(sessionState.permissions, listOf("hotel.resource.view"))
    val snackbarHostState = remember { SnackbarHostState() }

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

    AuthenticatedShell(
        title = if (roomId == null) "Add Room" else "Edit Room",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!allowed) {
            NoAccess("You don't have access to the rooms.")
            return@AuthenticatedShell
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.loadError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.loadError.orEmpty(), color = MaterialTheme.appColors.textOnScreen, textAlign = TextAlign.Center)
                }
                else -> RoomForm(
                    state = state,
                    viewModel = viewModel,
                    onCancel = { navController.popBackStack() },
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RoomForm(
    state: HotelRoomFormUiState,
    viewModel: HotelRoomFormViewModel,
    onCancel: () -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    val kindOptions = state.types.map { SelectorOption(it.id.toString(), it.name) }
    val buildingOptions = state.buildings.map { SelectorOption(it.value.toString(), it.label) }
    val floorOptions = listOf(SelectorOption("", "No floor")) + state.floors.map { SelectorOption(it.value.toString(), it.label) }
    val roomTypeOptions = listOf(SelectorOption("", "None")) + state.roomTypes.map { SelectorOption(it.value.toString(), it.label) }
    val isRoom = state.isRoom
    val bulk = state.bulk
    val run = state.run

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AppSelectDropdown(
                label = "Kind",
                options = kindOptions,
                selected = kindOptions.firstOrNull { it.id == state.resourceTypeId?.toString() },
                onSelected = { viewModel.onType(it.id.toLongOrNull()) },
                placeholder = "Choose",
            )
            Help("A seat is not on this list — beds are made by splitting a room.")
        }
        item {
            AppSelectDropdown(
                label = "Building",
                options = buildingOptions,
                selected = buildingOptions.firstOrNull { it.id == state.buildingId?.toString() },
                onSelected = { viewModel.onBuilding(it.id.toLongOrNull()) },
                placeholder = "Choose a building",
            )
        }
        item {
            AppSelectDropdown(
                label = "Floor",
                options = floorOptions,
                selected = floorOptions.firstOrNull { it.id == (state.floorId?.toString() ?: "") },
                onSelected = { viewModel.onFloor(it.id.toLongOrNull()) },
                enabled = state.buildingId != null,
            )
            Help("Optional — cottages have none.")
        }
        item {
            AppSelectDropdown(
                label = "Room type",
                options = roomTypeOptions,
                selected = roomTypeOptions.firstOrNull { it.id == (state.roomTypeId?.toString() ?: "") },
                onSelected = { viewModel.onRoomType(it.id.toLongOrNull()) },
            )
            Help("Fills the rest in. Everything it fills stays editable.")
        }
        // Drawn only while making something new: a room that exists is not a run.
        if (state.roomId == null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = state.several, onCheckedChange = viewModel::onSeveral)
                    Text(
                        "Add a run of rooms — a whole floor at once",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textOnScreen,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = state.code,
                    onValueChange = viewModel::onCode,
                    label = if (bulk) "301" else "101",
                    caption = if (bulk) "First room number" else "Room number",
                    modifier = Modifier.weight(1f),
                )
                // One field, swapped rather than added: a run has no name to
                // give — twelve rooms cannot all be the Rose Room.
                if (bulk) {
                    AppTextField(
                        value = state.count,
                        onValueChange = viewModel::onCount,
                        label = "4",
                        caption = "How many rooms",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AppTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = "Only if it has one — Rose Hall",
                        caption = "Name",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = state.capacity,
                    onValueChange = viewModel::onCapacity,
                    label = "2",
                    caption = "Holds (guests)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                AppSelectDropdown(
                    label = "Status",
                    options = STATUS_OPTIONS,
                    selected = STATUS_OPTIONS.firstOrNull { it.id == state.status.toString() },
                    onSelected = { viewModel.onStatus(it.id.toIntOrNull() ?: 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            // Fixed on a hall: it is let by the sitting, and "whole" is the
            // answer to a question about beds a hall never asks.
            AppSelectDropdown(
                label = "How it is sold",
                options = SALE_MODE_OPTIONS,
                selected = SALE_MODE_OPTIONS.firstOrNull { it.id == state.saleMode },
                onSelected = { viewModel.onSaleMode(it.id) },
                enabled = isRoom,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val rentOn = !isRoom || state.needsWholeRent
                AppTextField(
                    value = state.rent,
                    onValueChange = viewModel::onRent,
                    label = if (rentOn) "3000" else "Not sold whole",
                    caption = if (isRoom) "Whole room, per night" else "Rent",
                    keyboardType = KeyboardType.Decimal,
                    enabled = rentOn,
                    modifier = Modifier.weight(1f),
                )
                if (isRoom) {
                    AppTextField(
                        value = state.seatCount,
                        onValueChange = viewModel::onSeatCount,
                        label = "1",
                        caption = "Beds in the room",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (isRoom) {
            item {
                AppTextField(
                    value = state.seatRent,
                    onValueChange = viewModel::onSeatRent,
                    label = if (state.needsSeatRent) "500" else "Not sold by the seat",
                    caption = "Rent for a new bed",
                    keyboardType = KeyboardType.Decimal,
                    enabled = state.needsSeatRent,
                )
                Help("What a bed added from here starts at. Beds already priced keep their own.")
            }
        }

        // What the room IS, under what it costs — the tick list first, the
        // sentence after, because the list is what a clerk answers in four
        // seconds and the sentence is the part only some rooms need.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (bulk) "What these rooms offer" else "What it offers",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (state.facilityIds.isEmpty()) "none on" else "${state.facilityIds.size} on",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                    val choices = state.facilityChoices
                    if (choices.isEmpty()) {
                        Text(
                            "Nothing on the list yet. The Facilities tab has the usual twenty-two — AC, Wi-Fi, a projector — in one press.",
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                        LinkButton(
                            text = if (state.isSeeding) "Adding…" else "Add the standard twenty-two",
                            onClick = viewModel::seedFacilities,
                            enabled = !state.isSeeding,
                        )
                    } else {
                        choices.forEach { f ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Switch(checked = f.value in state.facilityIds, onCheckedChange = { viewModel.toggleFacility(f.value) })
                                Text(f.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.appColors.divider)
                    AppTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescription,
                        label = "Corner room, lake side. Extra bed on request.",
                        caption = "Description",
                        multiline = true,
                    )
                    Text(
                        "For the guest — it shows on the layout and the booking screen, and on the bill where the property's paper asks for it. Anything for the desk alone is a note, not this.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    if (bulk && run.isNotEmpty()) {
                        Text(
                            "All ${run.size} rooms are created with these ticks and this description. Open one afterwards to change its own.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.warning,
                        )
                    }
                }
            }
        }

        // Read back before it is sent: a mistyped start is far cheaper to see
        // here than as twelve rooms numbered from 3011.
        if (bulk) {
            item {
                Text(
                    text = if (run.isEmpty()) {
                        "The first room number has to end in a number — 301, or A-01 — so the rest can be counted out."
                    } else {
                        val listed = if (run.size <= 8) run.joinToString(", ") else run.take(5).joinToString(", ") + " … " + run.last()
                        "Creates ${run.size} ${if (run.size == 1) "room" else "rooms"} — $listed. " +
                            "If any of those numbers is already in this building, none of them is created."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }

        if (isRoom && state.roomId != null) {
            item {
                SeatEditor(
                    title = "Beds in ${state.displayName.ifBlank { "room ${state.code}" }}",
                    seats = state.seats,
                    drafts = state.seatDrafts,
                    savingId = state.savingSeatId,
                    onDraft = viewModel::onSeatDraft,
                    onSave = viewModel::saveSeat,
                )
            }
        }
        if (isRoom && state.roomId == null) {
            item {
                Text(
                    "The beds are written when the ${if (bulk) "rooms are" else "room is"} saved. Reopen a room afterwards to price any of its beds on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = if (bulk && run.isNotEmpty()) "Create ${run.size} rooms" else "Save",
                    onClick = viewModel::save,
                    isLoading = state.isSaving,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(text = "Close", onClick = onCancel, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The beds inside one room, priced one at a time. Beds that are switched off
 * are shown rather than hidden: a room cut from four to two keeps all four
 * rows, and raising the bed count on the form revives these same rows.
 */
@Composable
internal fun SeatEditor(
    title: String,
    seats: List<HotelSeatRow>,
    drafts: Map<Long, String>,
    savingId: Long?,
    onDraft: (Long, String) -> Unit,
    onSave: (HotelSeatRow) -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (seats.isEmpty()) {
                Text("No beds yet. Save the room and its seats are written with it.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            seats.forEach { seat ->
                val draft = drafts[seat.id]
                val shown = draft ?: seat.rent?.let { r -> if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString() }.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(seat.label, style = MaterialTheme.typography.bodySmall, color = if (seat.status == 1) MaterialTheme.appColors.text else muted)
                        // Said only when it is not the ordinary answer.
                        if (seat.status != 1) Text("switched off", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                    AppTextField(
                        value = shown,
                        onValueChange = { onDraft(seat.id, it) },
                        label = "—",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.width(110.dp),
                    )
                    LinkButton(
                        text = if (savingId == seat.id) "Saving…" else "Save",
                        onClick = { onSave(seat) },
                        enabled = draft != null && savingId == null,
                    )
                }
            }
            Text(
                "A seat rate is not the room rate divided by the beds — the two are separate commercial numbers and are never added together.",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun Help(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}
