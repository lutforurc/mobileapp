package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.example.cashbookbd.data.repository.HotelAvailability
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.data.repository.HotelRoom
import com.example.cashbookbd.data.repository.HotelSeat
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val BOOKING_TYPES = listOf(
    SelectorOption("individual", "Individual"),
    SelectorOption("group", "Group"),
    // Corporate means the bill goes to a company and the money comes later,
    // which needs a party to bill — asked for on the web's own form and not
    // offered here, so this app cannot take a booking nobody can chase.
)

private val BOOKING_STATUSES = listOf(
    SelectorOption("hold", "Hold (tentative)"),
    SelectorOption("confirmed", "Confirmed"),
)

data class HotelNewBookingUiState(
    val checkIn: SimpleDate = SimpleDate.today(),
    val checkOut: SimpleDate = SimpleDate.today().plusDays(1),

    val isLoadingRooms: Boolean = false,
    val roomsError: String? = null,
    val availability: HotelAvailability? = null,

    /** Whole rooms picked, and beds picked — two lists, as the server wants. */
    val pickedRooms: Set<Long> = emptySet(),
    val pickedSeats: Set<Long> = emptySet(),

    val bookerName: String = "",
    val bookerMobile: String = "",
    val adults: String = "",
    val children: String = "",
    val bookingType: String = "individual",
    val status: String = "hold",
    val notes: String = "",

    val isSaving: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val hasPicked: Boolean get() = pickedRooms.isNotEmpty() || pickedSeats.isNotEmpty()

    val canSave: Boolean get() = hasPicked && bookerName.isNotBlank() && !isSaving
}

class HotelNewBookingViewModel(
    private val repository: HotelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelNewBookingUiState())
    val uiState: StateFlow<HotelNewBookingUiState> = _uiState.asStateFlow()

    init {
        loadAvailability()
    }

    fun onCheckIn(date: SimpleDate) {
        _uiState.update { state ->
            // The stay has to be at least one night; pushing arrival past
            // departure moves departure rather than refusing the tap.
            val out = if (date.toApi() >= state.checkOut.toApi()) date.plusDays(1) else state.checkOut
            state.copy(checkIn = date, checkOut = out)
        }
        loadAvailability()
    }

    fun onCheckOut(date: SimpleDate) {
        _uiState.update { state ->
            if (date.toApi() <= state.checkIn.toApi()) state else state.copy(checkOut = date)
        }
        loadAvailability()
    }

    fun loadAvailability() {
        val state = _uiState.value
        _uiState.update {
            // A new range invalidates what was picked under the old one — a
            // room free last week may be taken this week, and carrying the
            // tick over would post a booking the server has to refuse.
            it.copy(
                isLoadingRooms = true,
                roomsError = null,
                pickedRooms = emptySet(),
                pickedSeats = emptySet(),
            )
        }
        viewModelScope.launch {
            val result = repository.fetchAvailability(state.checkIn.toApi(), state.checkOut.toApi())
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoadingRooms = false, availability = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingRooms = false,
                        roomsError = result.message,
                        availability = null,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun toggleRoom(room: HotelRoom) {
        if (!room.isFree) return
        _uiState.update { state ->
            state.copy(
                pickedRooms = if (room.id in state.pickedRooms) {
                    state.pickedRooms - room.id
                } else {
                    state.pickedRooms + room.id
                },
                // Picking the whole room takes its beds with it, so a bed
                // ticked earlier is not bought twice.
                pickedSeats = state.pickedSeats - room.seats.map { it.id }.toSet(),
            )
        }
    }

    fun toggleSeat(room: HotelRoom, seat: HotelSeat) {
        if (!seat.isFree) return
        _uiState.update { state ->
            state.copy(
                pickedSeats = if (seat.id in state.pickedSeats) {
                    state.pickedSeats - seat.id
                } else {
                    state.pickedSeats + seat.id
                },
                pickedRooms = state.pickedRooms - room.id,
            )
        }
    }

    fun onBookerName(v: String) = _uiState.update { it.copy(bookerName = v) }
    fun onBookerMobile(v: String) = _uiState.update { it.copy(bookerMobile = v) }
    fun onAdults(v: String) = _uiState.update { it.copy(adults = v.filter { c -> c.isDigit() }) }
    fun onChildren(v: String) = _uiState.update { it.copy(children = v.filter { c -> c.isDigit() }) }
    fun onBookingType(option: SelectorOption) = _uiState.update { it.copy(bookingType = option.id) }
    fun onStatus(option: SelectorOption) = _uiState.update { it.copy(status = option.id) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.storeBooking(
                roomIds = state.pickedRooms.toList(),
                seatIds = state.pickedSeats.toList(),
                checkIn = state.checkIn.toApi(),
                checkOut = state.checkOut.toApi(),
                bookingType = state.bookingType,
                status = state.status,
                bookerName = state.bookerName,
                bookerMobile = state.bookerMobile,
                statedAdults = state.adults,
                statedChildren = state.children,
                notes = state.notes,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, message = result.data, saved = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                HotelNewBookingViewModel(
                    repository = ServiceLocator.provideHotelRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * Taking a booking — the telephone call.
 *
 * Dates first, because everything else depends on them: what is free is a
 * question about a range, not about a room. Then the rooms, drawn as tiles a
 * clerk taps; a room sold by the bed opens its beds instead, because the bed is
 * the inventory and the room is only how the beds are referred to.
 *
 * A tile that cannot be taken says WHY in words rather than merely greying
 * out — "sold by the bed" and "taken until Friday" are different problems with
 * different answers, and a single grey tile would say neither.
 */
@Composable
fun HotelNewBookingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelNewBookingViewModel = viewModel(
        factory = HotelNewBookingViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
        if (state.saved) navController.popBackStack()
    }

    AuthenticatedShell(
        title = "New Booking",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = "Arrival",
                            value = state.checkIn.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickHotelDate(context, state.checkIn, viewModel::onCheckIn) },
                        )
                        PickerField(
                            label = "Departure",
                            value = state.checkOut.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickHotelDate(context, state.checkOut, viewModel::onCheckOut) },
                        )
                    }
                }

                state.availability?.let { availability ->
                    item {
                        // The night count and the two times together: the count
                        // is only honest while check-out comes before check-in,
                        // and a number that decides whether a room can be sold
                        // twice should not be invisible.
                        Text(
                            text = buildString {
                                append(availability.nights)
                                append(if (availability.nights == 1) " night · " else " nights · ")
                                append("${availability.freeCount} free")
                                if (availability.checkInTime.isNotBlank()) {
                                    append(" · in ${availability.checkInTime}, out ${availability.checkOutTime}")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                }

                if (state.isLoadingRooms) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                state.roomsError?.let { error ->
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            LinkButton(text = "Retry", onClick = viewModel::loadAvailability)
                        }
                    }
                }

                val rooms = state.availability?.rooms.orEmpty()
                val grouped = rooms.groupBy { "${it.buildingName} ${it.floorName}".trim() }
                grouped.forEach { (where, list) ->
                    item(key = "head-$where") {
                        Text(
                            text = where.ifBlank { "Rooms" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = AppFontWeight.Bold,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(list.size, key = { list[it].id }) { index ->
                        val room = list[index]
                        RoomCard(
                            room = room,
                            pickedRoom = room.id in state.pickedRooms,
                            pickedSeats = state.pickedSeats,
                            onRoomTap = { viewModel.toggleRoom(room) },
                            onSeatTap = { seat -> viewModel.toggleSeat(room, seat) },
                        )
                    }
                }

                if (state.hasPicked) {
                    item {
                        Text(
                            text = "Who is booking",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = AppFontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    item {
                        AppTextField(
                            value = state.bookerName,
                            onValueChange = viewModel::onBookerName,
                            label = "Name",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        AppTextField(
                            value = state.bookerMobile,
                            onValueChange = viewModel::onBookerMobile,
                            label = "Mobile",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = state.adults,
                                onValueChange = viewModel::onAdults,
                                label = "Adults",
                                modifier = Modifier.weight(1f),
                            )
                            AppTextField(
                                value = state.children,
                                onValueChange = viewModel::onChildren,
                                label = "Children",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        AppSelectDropdown(
                            label = "Booking Type",
                            options = BOOKING_TYPES,
                            selected = BOOKING_TYPES.firstOrNull { it.id == state.bookingType },
                            onSelected = viewModel::onBookingType,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        AppSelectDropdown(
                            label = "Status",
                            options = BOOKING_STATUSES,
                            selected = BOOKING_STATUSES.firstOrNull { it.id == state.status },
                            onSelected = viewModel::onStatus,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        AppTextField(
                            value = state.notes,
                            onValueChange = viewModel::onNotes,
                            label = "Notes",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        PrimaryButton(
                            text = "Save Booking",
                            onClick = viewModel::save,
                            enabled = state.canSave,
                            isLoading = state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RoomCard(
    room: HotelRoom,
    pickedRoom: Boolean,
    pickedSeats: Set<Long>,
    onRoomTap: () -> Unit,
    onSeatTap: (HotelSeat) -> Unit,
) {
    // A room sold by the bed is never tapped as a room: its beds are what is
    // for sale, and tapping the whole thing would buy something not offered.
    val tappable = room.isFree && !room.sellsSeats

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tappable) Modifier.clickable(onClick = onRoomTap) else Modifier),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            if (room.roomType.isNotBlank()) append(room.roomType).append(" · ")
                            append("holds ${room.capacity}")
                            room.rent?.let { append(" · ").append(it.toLong()).append("/night") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                    // The reason, in words. "Taken" is a colour; "BK-2026-00042,
                    // Mr Rahman" is what the clerk repeats down the telephone.
                    val why = when {
                        room.blockedReason.isNotBlank() -> room.blockedReason
                        room.takenBy.isNotBlank() -> "taken by ${room.takenBy}"
                        else -> ""
                    }
                    if (why.isNotBlank()) {
                        Text(
                            text = why,
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor(room.state),
                        )
                    }
                }
                if (pickedRoom) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Picked",
                        tint = MaterialTheme.appColors.success,
                    )
                } else {
                    Text(
                        text = roomStateLabel(room),
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor(room.state),
                    )
                }
            }

            if (room.seats.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Beds wrap rather than scroll: a dormitory of twelve is
                    // read at a glance, not swiped through.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        room.seats.forEach { seat ->
                            SeatChip(
                                seat = seat,
                                picked = seat.id in pickedSeats,
                                onTap = { onSeatTap(seat) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatChip(seat: HotelSeat, picked: Boolean, onTap: () -> Unit) {
    val background = when {
        picked -> MaterialTheme.appColors.success
        seat.isFree -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.appColors.danger
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .then(if (seat.isFree) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = buildString {
                append(seat.label)
                seat.rent?.let { append(" · ").append(it.toLong()) }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (picked || !seat.isFree) {
                MaterialTheme.appColors.textOnAccent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun roomStateLabel(room: HotelRoom): String = when {
    room.sellsSeats && room.state == "free" -> "${room.freeBeds} of ${room.beds} beds free"
    room.state == "free" -> "Free"
    room.state == "part" -> "${room.freeBeds} of ${room.beds} beds free"
    room.state == "booked" -> "Booked"
    room.state == "in_house" -> "In house"
    room.state == "closed" -> "Closed"
    else -> room.state
}

@Composable
private fun stateColor(state: String) = when (state) {
    "free" -> MaterialTheme.appColors.success
    "part" -> MaterialTheme.appColors.warning
    "booked" -> MaterialTheme.appColors.info
    "in_house" -> MaterialTheme.appColors.danger
    else -> MaterialTheme.appColors.textMuted
}

private fun pickHotelDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
