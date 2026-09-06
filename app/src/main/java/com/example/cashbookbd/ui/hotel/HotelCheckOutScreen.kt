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
import com.example.cashbookbd.data.repository.HotelCheckOutPlan
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.data.repository.HotelParty
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
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

data class HotelCheckOutUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val plan: HotelCheckOutPlan? = null,
    val departure: SimpleDate? = null,
    /** The rooms picked to leave; null until the plan's own default has been read. */
    val chosen: Set<Long>? = null,
    /** Who the balance is billed to when it is not being paid here. */
    val party: HotelParty? = null,
    val reason: String = "",
    val confirm: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    /** The booking is closed — the screen goes back to the list. */
    val done: Boolean = false,
)

class HotelCheckOutViewModel(
    private val repository: HotelFolioRepository,
    private val hotelRepository: HotelRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelCheckOutUiState())
    val uiState: StateFlow<HotelCheckOutUiState> = _uiState.asStateFlow()

    private var lastParties: List<HotelParty> = emptyList()

    init {
        load()
    }

    /**
     * The plan for the date and rooms as they stand. The figures are PROJECTED
     * — the nights still to be billed are counted in — so every change of date
     * or room re-reads it rather than working it out here.
     */
    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Absent means every room; an empty list would read as absent too,
            // which is why toggling the last room off does not re-read.
            val ids = s.chosen?.toList()?.takeIf { it.isNotEmpty() }
            when (val result = repository.fetchCheckOutPlan(bookingId, s.departure?.toApi(), ids)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        plan = result.data,
                        departure = it.departure ?: simpleDateOf(result.data.departureDate),
                        chosen = it.chosen ?: result.data.rooms
                            .filter { room -> room.chosen && !room.alreadyLeft }
                            .map { room -> room.roomResourceId }
                            .toSet(),
                    )
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

    fun onDeparture(date: SimpleDate) {
        _uiState.update { it.copy(departure = date) }
        load()
    }

    fun toggleRoom(roomId: Long) {
        val next = (_uiState.value.chosen ?: emptySet()).let { if (roomId in it) it - roomId else it + roomId }
        _uiState.update { it.copy(chosen = next) }
        if (next.isNotEmpty()) load()
    }

    fun onReason(value: String) = _uiState.update { it.copy(reason = value.take(255)) }

    suspend fun searchParties(query: String): Resource<List<SelectorOption>> =
        when (val result = hotelRepository.searchParties(query)) {
            is Resource.Success -> {
                lastParties = result.data
                Resource.Success(result.data.map { it.asOption() })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Success(emptyList())
        }

    fun onPartyPicked(option: SelectorOption) {
        val party = lastParties.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update { it.copy(party = party) }
    }

    fun clearParty() = _uiState.update { it.copy(party = null) }

    fun askConfirm() {
        val s = _uiState.value
        val plan = s.plan ?: return
        if (plan.rooms.size > 1 && s.chosen.isNullOrEmpty()) {
            return say("Name at least one room to check out.")
        }
        _uiState.update { it.copy(confirm = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirm = false) }

    /**
     * The write. Whether this closes the booking is read off the plan that was
     * on screen — the answer that comes back describes what is left, which is
     * the wrong thing to decide "are we done" from. A partial check-out stays
     * here with the plan re-read; a final one goes back to the list.
     */
    fun post() {
        val s = _uiState.value
        val plan = s.plan ?: return
        if (s.isWorking) return
        val ids = if (plan.rooms.size > 1) s.chosen?.toList() else null
        val closes = plan.closesBooking
        _uiState.update { it.copy(isWorking = true, confirm = false) }
        viewModelScope.launch {
            val result = repository.checkOut(bookingId, s.departure?.toApi(), s.party?.id, s.reason, ids)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isWorking = false, message = result.data.message, done = closes) }
                    if (!closes) {
                        _uiState.update { it.copy(chosen = null, party = null, reason = "") }
                        load()
                    }
                }
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
                HotelCheckOutViewModel(
                    repository = HotelFolioRepository.get(context.applicationContext),
                    hotelRepository = ServiceLocator.provideHotelRepository(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

/**
 * Check-out — the stay ends and the bill is settled or carried.
 *
 * The server answers with a PLAN before anything is written: which rooms
 * leave, which nights still go on the bill, what that makes the balance. One
 * room can leave while the rest sleep on, so with more than one room the
 * clerk names the ones going. A balance has to be somebody's: it is paid on
 * the folio first, or the party it is billed to is named here — the server
 * refuses a final check-out that leaves money owing to nobody, and says so in
 * its own words, which are shown verbatim.
 */
@Composable
fun HotelCheckOutScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelCheckOutViewModel = viewModel(
        factory = HotelCheckOutViewModel.provideFactory(LocalContext.current, bookingId),
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
        // The sentence is read before the list comes back: "checked out,
        // 3 seat-nights released — settled in full" is the receipt of the act.
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.onMessageShown()
        if (state.done) navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Check Out",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.plan == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.plan == null -> Column(
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

                else -> state.plan?.let { plan ->
                    CheckOutBody(
                        state = state,
                        plan = plan,
                        context = context,
                        viewModel = viewModel,
                        onOpenBill = { navController.navigate(HotelMenu.folio(bookingId)) },
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.confirm && state.plan != null) {
        val plan = state.plan!!
        val chosenRooms = plan.rooms.filter { it.roomResourceId in (state.chosen ?: emptySet()) && !it.alreadyLeft }
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(if (plan.closesBooking) "Check out" else "Check out ${chosenRooms.size} room${if (chosenRooms.size == 1) "" else "s"}") },
            text = {
                Text(
                    buildString {
                        append(plan.booking.bookingNo).append(" · ").append(plan.booking.bookerName)
                        append(" — leaving on ").append(state.departure?.toDisplay() ?: hotelDate(plan.departureDate)).append(".")
                        if (plan.rooms.size > 1 && chosenRooms.isNotEmpty()) {
                            append(" Rooms: ").append(chosenRooms.joinToString(", ") { it.room }).append(".")
                        }
                        if (plan.nightsToBill > 0) {
                            append(" ${plan.nightsToBill} night${if (plan.nightsToBill == 1) "" else "s"} go on the bill.")
                        }
                        if (plan.nightsReleased > 0) {
                            append(" ${plan.nightsReleased} released.")
                        }
                        when {
                            !plan.closesBooking -> append(" The rest of the party stays and the bill stays open.")
                            plan.balance > 0 && state.party != null ->
                                append(" ${hotelMoney(plan.balance)} is carried to ${state.party!!.name}.")
                            plan.balance > 0 ->
                                append(" ${hotelMoney(plan.balance)} is still owed — the server will refuse unless it is paid or billed to a party.")
                            plan.balance < 0 ->
                                append(" ${hotelMoney(-plan.balance)} is in hand — refund it on the bill first.")
                            else -> append(" Settled in full.")
                        }
                    }
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Check out",
                    onClick = viewModel::post,
                    enabled = !state.isWorking,
                    isLoading = state.isWorking,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Not yet", onClick = viewModel::dismissConfirm, enabled = !state.isWorking) },
        )
    }
}

@Composable
private fun CheckOutBody(
    state: HotelCheckOutUiState,
    plan: HotelCheckOutPlan,
    context: Context,
    viewModel: HotelCheckOutViewModel,
    onOpenBill: () -> Unit,
) {
    val booking = plan.booking
    val chosen = state.chosen ?: emptySet()
    val muted = MaterialTheme.appColors.textMuted

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
                Text(
                    text = listOf(booking.bookerName, booking.bookerMobile).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Booked ${hotelDate(booking.checkInDate)} → ${hotelDate(booking.checkOutDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }

        item {
            PickerField(
                label = "Leaving on",
                value = state.departure?.toDisplay() ?: hotelDate(plan.departureDate),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
                onClick = { pickMoneyDate(context, state.departure, viewModel::onDeparture) },
            )
        }
        if (plan.leavingEarly) {
            item {
                Text(
                    text = "Leaving early — booked out on ${hotelDate(plan.bookedOutOn)}; " +
                        "${plan.nightsReleased} night${if (plan.nightsReleased == 1) "" else "s"} go back on sale.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.warning,
                )
            }
        }

        if (plan.chartMissing.isNotEmpty()) {
            item {
                HotelBanner(
                    text = "The chart of accounts is not ready, so the nights cannot be billed. " +
                        "Missing: ${plan.chartMissing.joinToString(", ")}.",
                    color = MaterialTheme.appColors.danger,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HotelMoneyTile(label = "To be charged", value = hotelMoney(plan.totals.rounded), modifier = Modifier.weight(1f))
                HotelMoneyTile(label = "Paid", value = hotelMoney(plan.paid), modifier = Modifier.weight(1f))
                HotelMoneyTile(
                    label = if (plan.balance < 0) "In hand" else "To settle",
                    value = hotelMoney(Math.abs(plan.balance)),
                    valueColor = if (plan.balance > 0) MaterialTheme.appColors.danger else MaterialTheme.appColors.success,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (plan.nightsToBill > 0) {
            item {
                Text(
                    text = "This counts the ${plan.nightsToBill} night${if (plan.nightsToBill == 1) "" else "s"} still to be billed. " +
                        "The folio will not show them until check-out puts them on it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        if (plan.billedAhead > 0) {
            item {
                Text(
                    text = "${plan.billedAhead} night${if (plan.billedAhead == 1) " is" else "s are"} already on the bill " +
                        "beyond the departure and stay${if (plan.billedAhead == 1) "s" else ""} there.",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }

        // One room can leave while the rest sleep — but only when there is a
        // rest. A single room is the whole party and needs no picker.
        if (plan.rooms.size > 1) {
            item { HotelSectionTitle("Who is leaving") }
            items(plan.rooms.size, key = { plan.rooms[it].roomResourceId }) { index ->
                val room = plan.rooms[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = !room.alreadyLeft && room.roomResourceId in chosen,
                            onCheckedChange = { viewModel.toggleRoom(room.roomResourceId) },
                            enabled = !room.alreadyLeft && !state.isWorking,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = room.room.ifBlank { "Room ${room.roomResourceId}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = AppFontWeight.SemiBold,
                            )
                            Text(
                                text = if (room.alreadyLeft) {
                                    "Already left — ${room.nightsHeld} night${if (room.nightsHeld == 1) "" else "s"} kept"
                                } else {
                                    buildString {
                                        append(hotelDate(room.firstNight)).append(" → ").append(hotelDate(room.lastNight))
                                        append(" · ").append(room.nightsHeld).append(" held")
                                        if (room.roomResourceId in chosen) {
                                            if (room.nightsToBill > 0) append(" · ").append(room.nightsToBill).append(" to bill")
                                            if (room.nightsToRelease > 0) append(" · ").append(room.nightsToRelease).append(" released")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = if (plan.closesBooking) {
                        "Everyone leaves — this closes the booking."
                    } else {
                        "${plan.roomsLeaving} leaving, ${plan.roomsStaying} staying — the bill stays open."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }

        if (plan.closesBooking && plan.balance > 0) {
            item { HotelSectionTitle("Bill the balance to") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A balance has to be somebody's. Take the money on the bill, or name the party " +
                            "it is billed to and it is carried to their account.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    SearchableSelectDropdown(
                        selected = state.party?.asOption(),
                        onSelected = viewModel::onPartyPicked,
                        search = viewModel::searchParties,
                        modifier = Modifier.fillMaxWidth(),
                        label = "Company or party",
                        placeholder = "Type a name…",
                        emptyText = "Nobody on the customer list by that name",
                    )
                    if (state.party != null) {
                        LinkButton(text = "Clear — they pay here", onClick = viewModel::clearParty)
                    }
                    AppTextField(
                        value = state.reason,
                        onValueChange = viewModel::onReason,
                        label = "Reason",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                SecondaryButton(text = "Open the bill", onClick = onOpenBill, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = if (plan.closesBooking) "Check out" else "Check out ${chosen.size} room${if (chosen.size == 1) "" else "s"}",
                    onClick = viewModel::askConfirm,
                    enabled = !state.isWorking && !state.isLoading && (plan.rooms.size <= 1 || chosen.isNotEmpty()),
                    isLoading = state.isWorking,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
