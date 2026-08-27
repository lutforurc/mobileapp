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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.cashbookbd.data.repository.HotelAllotment
import com.example.cashbookbd.data.repository.HotelAllotmentRoom
import com.example.cashbookbd.data.repository.HotelGuestEntry
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val GENDERS = listOf(
    SelectorOption("", "—"),
    SelectorOption("male", "Male"),
    SelectorOption("female", "Female"),
    SelectorOption("other", "Other"),
)

data class HotelAllotmentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val allotment: HotelAllotment? = null,

    /** The room whose guest sheet is open, and the sheet itself. */
    val editingRoom: HotelAllotmentRoom? = null,
    val draft: List<HotelGuestEntry> = emptyList(),

    val isSaving: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelAllotmentViewModel(
    private val repository: HotelRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelAllotmentUiState())
    val uiState: StateFlow<HotelAllotmentUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchAllotment(bookingId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, allotment = result.data)
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

    /**
     * Opens one room's sheet, seeded with whoever is already written down —
     * the list REPLACES what was there, so starting from empty would quietly
     * delete a guest recorded an hour ago.
     */
    fun openRoom(room: HotelAllotmentRoom) = _uiState.update {
        it.copy(
            editingRoom = room,
            draft = room.guests.ifEmpty { listOf(HotelGuestEntry(name = "")) },
        )
    }

    fun closeRoom() = _uiState.update { it.copy(editingRoom = null, draft = emptyList()) }

    fun addGuest() = _uiState.update { it.copy(draft = it.draft + HotelGuestEntry(name = "")) }

    fun removeGuest(index: Int) = _uiState.update { state ->
        state.copy(draft = state.draft.filterIndexed { i, _ -> i != index })
    }

    fun editGuest(index: Int, transform: (HotelGuestEntry) -> HotelGuestEntry) =
        _uiState.update { state ->
            state.copy(draft = state.draft.mapIndexed { i, g -> if (i == index) transform(g) else g })
        }

    fun saveRoom() {
        val state = _uiState.value
        val room = state.editingRoom ?: return
        val guests = state.draft.filter { it.name.isNotBlank() }
        if (guests.isEmpty() || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.allotRoom(bookingId, room.roomId, guests)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, editingRoom = null, draft = emptyList(), message = result.data)
                    }
                    load()
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
        fun provideFactory(context: Context, bookingId: Long) = viewModelFactory {
            initializer {
                HotelAllotmentViewModel(
                    repository = ServiceLocator.provideHotelRepository(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

/**
 * Allotment — the booking reopened on the day the guests arrive.
 *
 * One room at a time, because that is how a desk works: a family checks in
 * while the coach party is still unloading, and a screen demanding all five
 * rooms at once gets filled with invented names to get past it.
 *
 * The server asks for one NID and one mobile AMONG a room's guests, not from
 * every one of them — the police register is built from one identified person,
 * and twelve numbers for twelve workers will never be given. Both are said on
 * the room's card so the clerk knows what is still owed before opening it.
 */
@Composable
fun HotelAllotmentScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelAllotmentViewModel = viewModel(
        factory = HotelAllotmentViewModel.provideFactory(LocalContext.current, bookingId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        title = "Check In",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.allotment == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Column(
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

                else -> state.allotment?.let { allotment ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Column {
                                Text(
                                    text = "${allotment.bookingNo} · ${allotment.bookerName}".trim(' ', '·'),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = AppFontWeight.Bold,
                                )
                                Text(
                                    text = "${allotment.checkInDate} → ${allotment.checkOutDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                                // Both numbers, never one. Booked for twelve and
                                // ten arrived is a fact worth reporting, not a
                                // discrepancy to be reconciled away.
                                Text(
                                    text = "${allotment.arrived} arrived of ${allotment.stated} expected" +
                                        if (allotment.roomsOutstanding > 0) {
                                            " · ${allotment.roomsOutstanding} room(s) still to check in"
                                        } else {
                                            ""
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (allotment.roomsOutstanding > 0) {
                                        MaterialTheme.appColors.warning
                                    } else {
                                        MaterialTheme.appColors.success
                                    },
                                )
                            }
                        }

                        items(allotment.rooms.size, key = { allotment.rooms[it].roomId }) { index ->
                            val room = allotment.rooms[index]
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = room.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = AppFontWeight.SemiBold,
                                            )
                                            Text(
                                                text = buildString {
                                                    append(room.guests.size)
                                                    append(if (room.guests.size == 1) " guest" else " guests")
                                                    if (room.capacity > 0) append(" · holds ${room.capacity}")
                                                    if (room.letAs.isNotBlank()) {
                                                        append(" · let ")
                                                        append(if (room.letAs == "seat") "by the bed" else "whole")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.appColors.textMuted,
                                            )
                                        }
                                        SecondaryButton(
                                            text = if (room.guests.isEmpty()) "Check in" else "Edit",
                                            onClick = { viewModel.openRoom(room) },
                                            compact = true,
                                        )
                                    }
                                    // What the room still owes, said separately:
                                    // an ID is asked of one guest, a mobile of
                                    // the room, and they are answered by
                                    // different people.
                                    val owed = buildList {
                                        if (room.needsIdentified) add("one NID or passport")
                                        if (room.needsMobile) add("one mobile number")
                                    }
                                    if (owed.isNotEmpty()) {
                                        Text(
                                            text = "Still needs " + owed.joinToString(" and "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.appColors.warning,
                                        )
                                    }
                                    room.guests.forEach { guest ->
                                        Text(
                                            text = buildString {
                                                append("• ").append(guest.name)
                                                if (guest.mobile.isNotBlank()) append(" · ").append(guest.mobile)
                                                if (guest.nationalId.isNotBlank()) {
                                                    append(" · ").append(guest.nationalId)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.appColors.textMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.editingRoom?.let { room ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) viewModel.closeRoom() },
            title = { Text("Guests in ${room.displayName}") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = "One guest needs an NID or passport, and one mobile number " +
                                "serves the whole room. The rest need only a name.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                    items(state.draft.size) { index ->
                        val guest = state.draft[index]
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Guest ${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.draft.size > 1) {
                                    LinkButton(
                                        text = "Remove",
                                        onClick = { viewModel.removeGuest(index) },
                                    )
                                }
                            }
                            AppTextField(
                                value = guest.name,
                                onValueChange = { v -> viewModel.editGuest(index) { it.copy(name = v) } },
                                label = "Name",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = guest.mobile,
                                onValueChange = { v -> viewModel.editGuest(index) { it.copy(mobile = v) } },
                                label = "Mobile",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = guest.nationalId,
                                onValueChange = { v -> viewModel.editGuest(index) { it.copy(nationalId = v) } },
                                label = "NID / Passport",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = guest.address,
                                onValueChange = { v -> viewModel.editGuest(index) { it.copy(address = v) } },
                                label = "Address",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppSelectDropdown(
                                    label = "Gender",
                                    options = GENDERS,
                                    selected = GENDERS.firstOrNull { it.id == guest.gender },
                                    onSelected = { option ->
                                        viewModel.editGuest(index) { it.copy(gender = option.id) }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                AppTextField(
                                    value = guest.age,
                                    onValueChange = { v ->
                                        viewModel.editGuest(index) {
                                            it.copy(age = v.filter { c -> c.isDigit() })
                                        }
                                    },
                                    label = "Age",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    item {
                        SecondaryButton(
                            text = "Add another guest",
                            onClick = viewModel::addGuest,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::saveRoom,
                    enabled = !state.isSaving && state.draft.any { it.name.isNotBlank() },
                ) { Text(if (state.isSaving) "Saving…" else "Check in") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeRoom, enabled = !state.isSaving) { Text("Cancel") }
            },
        )
    }
}
