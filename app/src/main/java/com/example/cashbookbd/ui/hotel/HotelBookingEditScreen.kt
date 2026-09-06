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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.cashbookbd.data.repository.HotelAvailability
import com.example.cashbookbd.data.repository.HotelBookingDetail
import com.example.cashbookbd.data.repository.HotelBookingEdit
import com.example.cashbookbd.data.repository.HotelEditSummary
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.data.repository.HotelRoom
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelBookingEditUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val detail: HotelBookingDetail? = null,
    val checkIn: SimpleDate? = null,
    val checkOut: SimpleDate? = null,
    val availability: HotelAvailability? = null,
    val isLoadingRooms: Boolean = false,
    val roomsError: String? = null,
    val pickedRooms: Set<Long> = emptySet(),
    val pickedSeats: Set<Long> = emptySet(),
    val bookerName: String = "",
    val bookerMobile: String = "",
    val adults: String = "",
    val children: String = "",
    val notes: String = "",
    /** hold / confirmed, or blank when the status is not the form's to change. */
    val status: String = "",
    val reason: String = "",
    /** The dry run's answer; cleared by any change so a stale preview is never confirmed. */
    val summary: HotelEditSummary? = null,
    val confirm: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val done: Boolean = false,
)

class HotelBookingEditViewModel(
    private val repository: HotelFolioRepository,
    private val hotelRepository: HotelRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelBookingEditUiState())
    val uiState: StateFlow<HotelBookingEditUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * The booking with its three pick lists, then what is free on its dates.
     * The lists come from the server — rooms let whole, beds let one at a
     * time, hall sittings — because working them out here would mean the
     * form re-deriving what a bed is.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchBookingForEdit(bookingId)) {
                is Resource.Success -> {
                    val d = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = d,
                            checkIn = simpleDateOf(d.booking.checkInDate),
                            checkOut = simpleDateOf(d.booking.checkOutDate),
                            pickedRooms = d.roomIds.toSet(),
                            pickedSeats = d.seatIds.toSet(),
                            bookerName = d.booking.bookerName,
                            bookerMobile = d.booking.bookerMobile,
                            adults = d.statedAdults.toString(),
                            children = d.statedChildren.toString(),
                            notes = d.booking.notes,
                            status = d.booking.status.takeIf { s -> s == "hold" || s == "confirmed" }.orEmpty(),
                        )
                    }
                    if (!d.booking.isWalkIn) loadRooms()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadRooms() {
        val s = _uiState.value
        val checkIn = s.checkIn ?: return
        val checkOut = s.checkOut ?: return
        if (checkOut.toApi() <= checkIn.toApi()) {
            _uiState.update { it.copy(roomsError = "Check-out has to be after check-in.", availability = null) }
            return
        }
        _uiState.update { it.copy(isLoadingRooms = true, roomsError = null) }
        viewModelScope.launch {
            when (val result = hotelRepository.fetchAvailability(checkIn.toApi(), checkOut.toApi())) {
                is Resource.Success -> _uiState.update { it.copy(isLoadingRooms = false, availability = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingRooms = false,
                        roomsError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onCheckIn(date: SimpleDate) {
        _uiState.update { s ->
            val out = s.checkOut
            s.copy(
                checkIn = date,
                // A night is check-in to the morning after; the out date follows.
                checkOut = if (out == null || out.toApi() <= date.toApi()) date.plusDays(1) else out,
                summary = null,
            )
        }
        loadRooms()
    }

    fun onCheckOut(date: SimpleDate) {
        _uiState.update { it.copy(checkOut = date, summary = null) }
        loadRooms()
    }

    /** A room picked whole lets go of any of its beds picked singly, and the other way round. */
    fun toggleRoom(room: HotelRoom) = _uiState.update { s ->
        if (room.id in s.pickedRooms) {
            s.copy(pickedRooms = s.pickedRooms - room.id, summary = null)
        } else {
            s.copy(
                pickedRooms = s.pickedRooms + room.id,
                pickedSeats = s.pickedSeats - room.seats.map { it.id }.toSet(),
                summary = null,
            )
        }
    }

    fun toggleSeat(room: HotelRoom, seatId: Long) = _uiState.update { s ->
        if (seatId in s.pickedSeats) {
            s.copy(pickedSeats = s.pickedSeats - seatId, summary = null)
        } else {
            s.copy(pickedSeats = s.pickedSeats + seatId, pickedRooms = s.pickedRooms - room.id, summary = null)
        }
    }

    fun onBookerName(v: String) = _uiState.update { it.copy(bookerName = v.take(150), summary = null) }
    fun onBookerMobile(v: String) = _uiState.update { it.copy(bookerMobile = v.take(30), summary = null) }
    fun onAdults(v: String) = _uiState.update { it.copy(adults = v.filter { c -> c.isDigit() }.take(3), summary = null) }
    fun onChildren(v: String) = _uiState.update { it.copy(children = v.filter { c -> c.isDigit() }.take(3), summary = null) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v, summary = null) }
    fun onStatus(v: String) = _uiState.update { it.copy(status = v, summary = null) }
    fun onReason(v: String) = _uiState.update { it.copy(reason = v.take(255)) }

    private fun edit(): HotelBookingEdit? {
        val s = _uiState.value
        val d = s.detail ?: return null
        return HotelBookingEdit(
            roomIds = s.pickedRooms.toList(),
            seatIds = s.pickedSeats.toList(),
            // Echoed back untouched: the lists are the whole wanted shape, and a
            // hall left out of them would be a hall dropped.
            sittings = d.sittings,
            checkIn = s.checkIn?.toApi().orEmpty(),
            checkOut = s.checkOut?.toApi().orEmpty(),
            bookerName = s.bookerName,
            bookerMobile = s.bookerMobile,
            statedAdults = s.adults.toIntOrNull() ?: 0,
            statedChildren = s.children.toIntOrNull() ?: 0,
            notes = s.notes,
            reason = s.reason,
            status = s.status,
        )
    }

    private fun guard(): String? {
        val s = _uiState.value
        val d = s.detail ?: return "The booking has not loaded."
        if (s.bookerName.isBlank()) return "Whose booking is it? A name is needed."
        if (!d.booking.isWalkIn && s.pickedRooms.isEmpty() && s.pickedSeats.isEmpty() && d.sittings.isEmpty()) {
            return "A booking cannot be emptied. Cancel it instead — that gives the nights back and settles the money."
        }
        return null
    }

    /** The dry run: the same figures the write would apply, and nothing written. */
    fun preview() {
        guard()?.let { return say(it) }
        val edit = edit() ?: return
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val result = repository.updateBooking(bookingId, edit, dryRun = true)) {
                is Resource.Success -> _uiState.update { it.copy(isWorking = false, summary = result.data, message = result.data.message) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askSave() {
        guard()?.let { return say(it) }
        _uiState.update { it.copy(confirm = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirm = false) }

    fun save() {
        val edit = edit() ?: return
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, confirm = false) }
        viewModelScope.launch {
            when (val result = repository.updateBooking(bookingId, edit, dryRun = false)) {
                is Resource.Success -> _uiState.update { it.copy(isWorking = false, message = result.data.message, done = true) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun say(text: String) = _uiState.update { it.copy(message = text) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, bookingId: Long) = viewModelFactory {
            initializer {
                HotelBookingEditViewModel(
                    repository = HotelFolioRepository.get(context.applicationContext),
                    hotelRepository = ServiceLocator.provideHotelRepository(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

/**
 * Changing a booking after it is taken — a DIFF, never a rebuild.
 *
 * The booking keeps its number (it is on a receipt in somebody's pocket) and
 * its payments; what changes is the nights — the ones no longer wanted go,
 * the new ones are held. A night already on the bill is never dropped: the
 * line is posted and the VAT on it has fallen due, so the server refuses the
 * whole edit and names them. "Preview" asks the server what would change
 * without writing, which is what the clerk reads before "Save".
 *
 * Rooms this booking already holds show as taken on the availability grid;
 * they stay pickable here, because letting go of one is exactly the edit.
 */
@Composable
fun HotelBookingEditScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelBookingEditViewModel = viewModel(
        factory = HotelBookingEditViewModel.provideFactory(LocalContext.current, bookingId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.onMessageShown()
        if (state.done) navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Change Booking",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.detail == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.detail == null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }

                else -> state.detail?.let { detail ->
                    EditBody(state = state, detail = detail, context = context, viewModel = viewModel)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.confirm) {
        val summary = state.summary
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text("Save the changes?") },
            text = {
                Text(
                    if (summary != null) {
                        buildString {
                            append("${summary.nightsAdding} night${if (summary.nightsAdding == 1) "" else "s"} added, ")
                            append("${summary.nightsDropping} dropped")
                            if (summary.roomsLeaving.isNotEmpty()) append("; leaving ${summary.roomsLeaving.joinToString(", ")}")
                            append(". Stay ${hotelDate(summary.checkInDate)} → ${hotelDate(summary.checkOutDate)}")
                            if (summary.nights > 0) append(", ${summary.nights} night${if (summary.nights == 1) "" else "s"}")
                            append(".")
                        }
                    } else {
                        "The booking is changed to what is on this form. Nights no longer wanted go back on sale; " +
                            "new ones are held. A night already on the bill will be refused."
                    }
                )
            },
            confirmButton = {
                PrimaryButton(text = "Save", onClick = viewModel::save, enabled = !state.isWorking, isLoading = state.isWorking, compact = true)
            },
            dismissButton = { LinkButton(text = "Not yet", onClick = viewModel::dismissConfirm, enabled = !state.isWorking) },
        )
    }
}

@Composable
private fun EditBody(
    state: HotelBookingEditUiState,
    detail: HotelBookingDetail,
    context: Context,
    viewModel: HotelBookingEditViewModel,
) {
    val booking = detail.booking
    val muted = MaterialTheme.appColors.textMuted
    val closed = booking.status == "checked_out" || booking.status == "cancelled" || booking.status == "expired"
    val ownRooms = detail.roomIds.toSet()
    val ownSeats = detail.seatIds.toSet()
    val canEdit = !closed && !booking.isWalkIn

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = booking.bookingNo.ifBlank { "Booking #${booking.id}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    HotelStatusChip(status = booking.status)
                }
                if (detail.billedToPartyName.isNotBlank()) {
                    Text(
                        text = "Billed to ${detail.billedToPartyName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
            }
        }

        when {
            booking.status == "checked_out" -> item {
                HotelBanner(
                    text = "This booking is checked out — it cannot be changed. Take a new booking instead.",
                    color = MaterialTheme.appColors.info,
                )
            }
            closed -> item {
                HotelBanner(text = "This booking is ${hotelStatusLabel(booking.status).lowercase()} and cannot be changed.", color = MaterialTheme.appColors.info)
            }
            booking.isWalkIn -> item {
                HotelBanner(
                    text = "A walk-in sale holds no room or nights — what was sold lives on its bill.",
                    color = MaterialTheme.appColors.info,
                )
            }
        }
        if (!canEdit) return@LazyColumn

        // What it holds now, in the clerk's words, before the grid asks them to
        // pick again.
        if (detail.rooms.isNotEmpty() || detail.sittings.isNotEmpty()) {
            item {
                Text(
                    text = buildString {
                        if (detail.rooms.isNotEmpty()) {
                            append("Holds now: ")
                            append(
                                detail.rooms.joinToString(", ") { r ->
                                    r.displayName.ifBlank { "Room ${r.roomId}" } +
                                        if (r.letAs == "seat") " (${r.beds} bed${if (r.beds == 1) "" else "s"})" else ""
                                }
                            )
                            append(".")
                        }
                        if (detail.sittings.isNotEmpty()) {
                            if (isNotEmpty()) append(" ")
                            append("Halls, kept as they are: ")
                            append(
                                detail.sittings.joinToString(", ") { s ->
                                    listOf(s.hall, s.sitting, hotelDate(s.date)).filter { it.isNotBlank() }.joinToString(" ")
                                }
                            )
                            append(".")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    label = "Check-in",
                    value = state.checkIn?.toDisplay().orEmpty(),
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = { pickMoneyDate(context, state.checkIn, viewModel::onCheckIn) },
                )
                PickerField(
                    label = "Check-out",
                    value = state.checkOut?.toDisplay().orEmpty(),
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = { pickMoneyDate(context, state.checkOut, viewModel::onCheckOut) },
                )
            }
        }

        item {
            HotelSectionTitle(
                text = "Rooms and beds" + (state.availability?.let { a ->
                    if (a.nights > 0) " · ${a.nights} night${if (a.nights == 1) "" else "s"}" else ""
                } ?: ""),
            )
        }
        when {
            state.isLoadingRooms -> item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.roomsError != null -> item {
                Text(text = state.roomsError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.danger)
            }
            state.availability != null -> {
                val rooms = state.availability!!.rooms
                if (rooms.isEmpty()) {
                    item {
                        Text(text = "No rooms on this property yet.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                items(rooms.size, key = { rooms[it].id }) { index ->
                    val room = rooms[index]
                    RoomPickRow(
                        room = room,
                        pickedWhole = room.id in state.pickedRooms,
                        pickedSeats = state.pickedSeats,
                        ownRoom = room.id in ownRooms,
                        ownSeats = ownSeats,
                        enabled = !state.isWorking,
                        onToggleRoom = { viewModel.toggleRoom(room) },
                        onToggleSeat = { seatId -> viewModel.toggleSeat(room, seatId) },
                    )
                }
            }
        }

        item { HotelSectionTitle("Who", modifier = Modifier.padding(top = 6.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = state.bookerName,
                    onValueChange = viewModel::onBookerName,
                    label = "Booked by",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.bookerMobile,
                    onValueChange = viewModel::onBookerMobile,
                    label = "Mobile",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Phone,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.adults,
                        onValueChange = viewModel::onAdults,
                        label = "Adults",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        value = state.children,
                        onValueChange = viewModel::onChildren,
                        label = "Children",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
                AppTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Notes",
                    modifier = Modifier.fillMaxWidth(),
                    multiline = true,
                )
            }
        }

        // The status is the form's to move only between held and confirmed.
        // Guests already in the room cannot go back to either — the server
        // says so — so a checked-in booking shows no chips.
        if (booking.status == "hold" || booking.status == "confirmed") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Status", style = MaterialTheme.typography.labelMedium, color = muted)
                    FilterChip(
                        selected = state.status == "hold",
                        onClick = { viewModel.onStatus("hold") },
                        label = { Text("Held") },
                    )
                    FilterChip(
                        selected = state.status == "confirmed",
                        onClick = { viewModel.onStatus("confirmed") },
                        label = { Text("Confirmed") },
                    )
                }
            }
        }

        item {
            AppTextField(
                value = state.reason,
                onValueChange = viewModel::onReason,
                label = "Why it is changing",
                modifier = Modifier.fillMaxWidth(),
                caption = "Kept on any night dropped, with who dropped it.",
            )
        }

        state.summary?.let { summary ->
            item {
                val tone = if (summary.refused) MaterialTheme.appColors.danger else MaterialTheme.appColors.info
                HotelBanner(
                    text = buildString {
                        append(summary.message).append(" ")
                        append("${summary.nightsAdding} night${if (summary.nightsAdding == 1) "" else "s"} added · ")
                        append("${summary.nightsDropping} dropped")
                        if (summary.billedDropping > 0) append(" · ${summary.billedDropping} of them already billed")
                        if (summary.roomsLeaving.isNotEmpty()) append(" · leaving ${summary.roomsLeaving.joinToString(", ")}")
                        append(" · ${hotelDate(summary.checkInDate)} → ${hotelDate(summary.checkOutDate)}")
                        if (summary.nights > 0) append(", ${summary.nights} night${if (summary.nights == 1) "" else "s"}")
                    },
                    color = tone,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                SecondaryButton(
                    text = "Preview",
                    onClick = viewModel::preview,
                    enabled = !state.isWorking && !state.isLoadingRooms,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = viewModel::askSave,
                    enabled = !state.isWorking && !state.isLoadingRooms && state.summary?.refused != true,
                    isLoading = state.isWorking,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One room of the grid, compactly: a checkbox for the whole room where it may
 * be let whole, and one per bed where it is sold by the bed. A room this
 * booking already holds reads as taken on the grid but stays pickable here.
 */
@Composable
private fun RoomPickRow(
    room: HotelRoom,
    pickedWhole: Boolean,
    pickedSeats: Set<Long>,
    ownRoom: Boolean,
    ownSeats: Set<Long>,
    enabled: Boolean,
    onToggleRoom: () -> Unit,
    onToggleSeat: (Long) -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    val sellsWhole = room.saleMode == "whole" || room.saleMode == "both" || room.saleMode.isBlank()
    val wholePickable = sellsWhole && (room.isFree || ownRoom || pickedWhole)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sellsWhole) {
                    Checkbox(
                        checked = pickedWhole,
                        onCheckedChange = { onToggleRoom() },
                        enabled = enabled && wholePickable,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append(room.displayName)
                            if (room.roomType.isNotBlank()) append(" · ").append(room.roomType)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            val place = listOf(room.buildingName, room.floorName).filter { it.isNotBlank() }.joinToString(" / ")
                            if (place.isNotBlank()) append(place).append(" · ")
                            room.rent?.let { append(hotelMoney(it)).append("/night · ") }
                            when {
                                ownRoom -> append("held by this booking")
                                room.isFree -> append("free")
                                room.blockedReason.isNotBlank() -> append(room.blockedReason)
                                room.takenBy.isNotBlank() -> append(room.takenBy)
                                else -> append(room.state.replace('_', ' '))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
            if (room.sellsSeats && room.seats.isNotEmpty()) {
                room.seats.forEach { seat ->
                    val own = seat.id in ownSeats
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 24.dp),
                    ) {
                        Checkbox(
                            checked = seat.id in pickedSeats,
                            onCheckedChange = { onToggleSeat(seat.id) },
                            enabled = enabled && (seat.isFree || own || seat.id in pickedSeats) && !pickedWhole,
                        )
                        Text(
                            text = buildString {
                                append(seat.label)
                                seat.rent?.let { append(" · ").append(hotelMoney(it)) }
                                when {
                                    own -> append(" · this booking")
                                    !seat.isFree && seat.takenBy.isNotBlank() -> append(" · ").append(seat.takenBy)
                                    !seat.isFree -> append(" · ").append(seat.state.replace('_', ' '))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (seat.isFree || own) MaterialTheme.colorScheme.onBackground else muted,
                        )
                    }
                }
            }
        }
    }
}
