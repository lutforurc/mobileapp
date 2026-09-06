package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cashbookbd.data.repository.HotelBookingRow
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the list is of: stays, the walk-in meals, or everything sold. */
private val KINDS = listOf(
    "stay" to "Stays",
    "walk_in" to "Walk-in meals",
    "all" to "Everything",
)

/** The status filter, in the order the desk thinks about them. */
private val STATUSES = listOf(
    "" to "All",
    "hold" to "Hold",
    "confirmed" to "Confirmed",
    "checked_in" to "In House",
    "checked_out" to "Checked Out",
    "cancelled" to "Cancelled",
)

data class HotelBookingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<HotelBookingRow> = emptyList(),
    val status: String = "",
    val search: String = "",
    /**
     * The arrival range. Cut by ARRIVAL rather than by overlap: the question
     * this list is asked is who is coming between two days, not which stays
     * happen to touch them. Either end alone is a real filter, so both start
     * empty and either may be set on its own.
     */
    val dateFrom: SimpleDate? = null,
    val dateTo: SimpleDate? = null,
    /** stay (rooms and halls) / walk_in (the meals alone) / all. */
    val kind: String = "stay",
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val sessionExpired: Boolean = false,
)

class HotelBookingsViewModel(
    private val repository: HotelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelBookingsUiState())
    val uiState: StateFlow<HotelBookingsUiState> = _uiState.asStateFlow()

    init {
        load(page = 1)
    }

    fun onStatus(value: String) {
        _uiState.update { it.copy(status = value) }
        load(page = 1)
    }

    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }

    fun onDateFrom(date: SimpleDate?) {
        _uiState.update { it.copy(dateFrom = date) }
        load(page = 1)
    }

    fun onDateTo(date: SimpleDate?) {
        _uiState.update { it.copy(dateTo = date) }
        load(page = 1)
    }

    fun onKind(value: String) {
        _uiState.update { it.copy(kind = value) }
        load(page = 1)
    }

    fun clearDates() {
        _uiState.update { it.copy(dateFrom = null, dateTo = null) }
        load(page = 1)
    }

    fun search() = load(page = 1)

    fun goToPage(page: Int) = load(page)

    private fun load(page: Int) {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchBookings(
                status = state.status.takeIf { it.isNotBlank() },
                search = state.search,
                page = page,
                dateFrom = state.dateFrom?.toApi(),
                dateTo = state.dateTo?.toApi(),
                kind = state.kind,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                HotelBookingsViewModel(
                    repository = ServiceLocator.provideHotelRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * The bookings list — the screen the front desk opens every day.
 *
 * What this answers is the question asked at the counter twenty times a day —
 * who is coming, who is in, and which hold is about to lapse — and each row
 * opens onto the acts that follow: the arrival, the bill, check-out, cancel.
 */
@Composable
fun HotelBookingsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelBookingsViewModel = viewModel(
        factory = HotelBookingsViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    // Recording an arrival is its own permission: the booking is taken by
    // whoever answers the telephone, the arrival by whoever is at the desk.
    val canAllot = Permissions.has(sessionState.permissions, "hotel.booking.allot")
    // Ending a stay and calling one off are each their own permission too.
    val canCheckOut = Permissions.has(sessionState.permissions, "hotel.booking.checkout")
    val canCancel = Permissions.has(sessionState.permissions, "hotel.booking.cancel")
    // The hold column counts down, so it is recomputed once a minute — the
    // words change ("45 min left"), and a list nobody refreshes would still
    // say an hour when the beds had gone back.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Bookings",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                STATUSES.forEach { (value, label) ->
                    FilterChip(
                        selected = state.status == value,
                        onClick = { viewModel.onStatus(value) },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    label = "Booking no, name or mobile",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Search",
                    onClick = viewModel::search,
                    isLoading = state.isLoading,
                )
                SecondaryButton(
                    text = "New",
                    onClick = { navController.navigate(Routes.HOTEL_NEW_BOOKING) },
                )
                // A meal sold to somebody who is not staying: no room, no
                // nights — a bill to put charges on.
                SecondaryButton(
                    text = "Walk-in sale",
                    onClick = { navController.navigate(HotelMenu.ROUTE_WALK_IN) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerField(
                    label = "Arriving from",
                    value = state.dateFrom?.toDisplay().orEmpty(),
                    trailingIcon = Icons.Filled.DateRange,
                    placeholder = "Any",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickBookingDate(context, state.dateFrom, viewModel::onDateFrom)
                    },
                )
                PickerField(
                    label = "to",
                    value = state.dateTo?.toDisplay().orEmpty(),
                    trailingIcon = Icons.Filled.DateRange,
                    placeholder = "Any",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickBookingDate(context, state.dateTo, viewModel::onDateTo)
                    },
                )
                if (state.dateFrom != null || state.dateTo != null) {
                    LinkButton(text = "Clear", onClick = viewModel::clearDates)
                }
            }
            // Meals sold to somebody who is not staying are off this list
            // unless asked for: a restaurant serves more people in a fortnight
            // than the rooms take in a year, and the desk would be paging past
            // lunches to find who is arriving tonight.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KINDS.forEach { (value, label) ->
                    FilterChip(
                        selected = state.kind == value,
                        onClick = { viewModel.onKind(value) },
                        label = { Text(label) },
                    )
                }
            }

            when {
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
                    LinkButton(text = "Retry", onClick = viewModel::search)
                }

                state.isLoading && state.rows.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = bookingColumns(
                            canAllot = canAllot,
                            canCheckOut = canCheckOut,
                            canCancel = canCancel,
                            now = now,
                            onEdit = { row -> navController.navigate(HotelMenu.edit(row.id)) },
                            onAllot = { row ->
                                navController.navigate(Routes.hotelAllotment(row.id))
                            },
                            onBill = { row -> navController.navigate(HotelMenu.folio(row.id)) },
                            onCheckOut = { row -> navController.navigate(HotelMenu.checkOut(row.id)) },
                            onCancel = { row -> navController.navigate(HotelMenu.cancel(row.id)) },
                        ),
                        data = state.rows,
                        noDataMessage = "No booking found",
                    )
                    // The table stays put while the next page loads — a list
                    // that vanishes on every page turn is a list nobody trusts.
                    if (state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }

            if (state.lastPage > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinkButton(
                        text = "Previous",
                        onClick = { viewModel.goToPage(state.currentPage - 1) },
                        enabled = state.currentPage > 1 && !state.isLoading,
                    )
                    Text(
                        text = "Page ${state.currentPage} of ${state.lastPage} · ${state.total} bookings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(
                        text = "Next",
                        onClick = { viewModel.goToPage(state.currentPage + 1) },
                        enabled = state.currentPage < state.lastPage && !state.isLoading,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun bookingColumns(
    canAllot: Boolean,
    canCheckOut: Boolean,
    canCancel: Boolean,
    now: Long,
    onEdit: (HotelBookingRow) -> Unit,
    onAllot: (HotelBookingRow) -> Unit,
    onBill: (HotelBookingRow) -> Unit,
    onCheckOut: (HotelBookingRow) -> Unit,
    onCancel: (HotelBookingRow) -> Unit,
): List<ReportColumn<HotelBookingRow>> {
    val muted = MaterialTheme.appColors.textMuted
    val danger = MaterialTheme.appColors.danger
    val warning = MaterialTheme.appColors.warning
    val columns: List<ReportColumn<HotelBookingRow>> = listOf(
        ReportColumn("BOOKING", ReportColWidth.Fixed(130.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(r.bookingNo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    // "(N)" only once somebody has actually been written down.
                    HotelStatusChip(
                        status = r.status,
                        guestsCount = r.guestsCount,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        ReportColumn("GUEST", ReportColWidth.Fixed(150.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = r.bookerName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    if (r.bookerMobile.isNotBlank()) {
                        Text(
                            text = r.bookerMobile,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        ReportColumn("STAY", ReportColWidth.Fixed(160.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (r.bookingType == "walk_in") {
                        // A meal, not a stay: the day it was served, and no room.
                        Text(text = r.checkInDate, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(
                            text = "Walk-in, no room",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = "${r.checkInDate} → ${r.checkOutDate}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                        Text(
                            text = if (r.nights == 1) "1 night" else "${r.nights} nights",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        ReportColumn("PARTY", ReportColWidth.Fixed(130.dp)) { r, _ ->
            // What was said on the telephone, and — once they arrive — how many
            // of the party are actually written down.
            val guests = r.statedAdults + r.statedChildren
            val stated = buildString {
                append(r.statedRooms)
                append(if (r.statedRooms == 1) " room, " else " rooms, ")
                append(guests)
                append(if (guests == 1) " guest" else " guests")
            }
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(text = stated, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (r.guestsCount > 0) {
                        Text(
                            text = "${r.guestsCount} written down",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        ReportColumn("HOLD UNTIL", ReportColWidth.Fixed(130.dp)) { r, _ ->
            // Only a hold counts down. How long is LEFT, not when it ends —
            // the property sets its hold in hours, and that is the number the
            // desk is looking for. Everywhere else a dash rather than a blank,
            // so an empty cell is never read as a missing deadline.
            if (r.status == "hold") {
                val words = holdCountdown(r.holdUntil, now)
                ReportTableCell.Slot {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(
                            text = words.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (words.tone == HoldTone.LAPSED || words.tone == HoldTone.URGENT) {
                                AppFontWeight.Bold
                            } else {
                                AppFontWeight.Normal
                            },
                            color = when (words.tone) {
                                HoldTone.LAPSED -> danger
                                HoldTone.URGENT -> warning
                                else -> muted
                            },
                            maxLines = 1,
                        )
                        if (r.holdUntil.isNotBlank()) {
                            Text(
                                text = r.holdUntil,
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                cellText(r.holdUntil.ifBlank { "-" })
            }
        },
        ReportColumn("TYPE", ReportColWidth.Fixed(100.dp)) { r, _ ->
            cellText(
                when (r.bookingType) {
                    "individual" -> "Individual"
                    "group" -> "Group"
                    "corporate" -> "Corporate"
                    else -> r.bookingType.ifBlank { "-" }
                }
            )
        },
    )

    // The doors off a row, in the order the desk uses them: Edit, the arrival,
    // the Bill (always — a cancelled stay may still have money on it), Check
    // out, and Cancel last because it is the one nobody wants pressed by
    // accident. A cancelled or lapsed booking is a single dash.
    return columns + ReportColumn<HotelBookingRow>(
        "ACTION", ReportColWidth.Fixed(230.dp),
    ) { r, _ ->
        val closed = r.status == "cancelled" || r.status == "expired"
        if (closed) {
            cellText("—", align = TextAlign.Center)
        } else {
            val open = r.status == "hold" || r.status == "confirmed" || r.status == "checked_in"
            ReportTableCell.Slot {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (r.status != "checked_out") {
                        LinkButton(text = "Edit", onClick = { onEdit(r) })
                    }
                    // Only where somebody may record an arrival, on a booking
                    // that still has one to record — and never on a walk-in,
                    // which has no room to arrive at.
                    if (canAllot && open && r.bookingType != "walk_in") {
                        LinkButton(
                            text = if (r.status == "checked_in") "Guests" else "Check in",
                            onClick = { onAllot(r) },
                        )
                    }
                    LinkButton(text = "Bill", onClick = { onBill(r) })
                    if (canCheckOut && r.status == "checked_in") {
                        LinkButton(text = "Check out", onClick = { onCheckOut(r) })
                    }
                    if (canCancel && r.status != "checked_out") {
                        LinkButton(text = "Cancel", onClick = { onCancel(r) }, color = danger)
                    }
                }
            }
        }
    }
}

/** The range pickers, which may also be cleared — hence the nullable initial. */
private fun pickBookingDate(
    context: Context,
    initial: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
) {
    val start = initial ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}
