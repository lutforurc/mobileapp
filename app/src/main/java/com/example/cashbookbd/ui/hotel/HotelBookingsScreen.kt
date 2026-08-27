package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Read-only in this phase: taking a booking, allotting rooms on arrival, the
 * folio and check-out all move money or inventory and follow next. What this
 * answers is the question asked at the counter twenty times a day — who is
 * coming, who is in, and which hold is about to lapse.
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
                        columns = bookingColumns(),
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

@Composable
private fun bookingColumns(): List<ReportColumn<HotelBookingRow>> {
    val muted = MaterialTheme.appColors.textMuted
    return listOf(
        ReportColumn("BOOKING", ReportColWidth.Fixed(130.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(r.bookingNo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(
                        text = statusLabel(r.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(r.status),
                        maxLines = 1,
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
        ReportColumn("HOLD UNTIL", ReportColWidth.Fixed(120.dp)) { r, _ ->
            // Only a hold has one; everywhere else a dash rather than a blank,
            // so an empty cell is never read as a missing deadline.
            cellText(r.holdUntil.ifBlank { "-" })
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
}

private fun statusLabel(status: String): String = when (status) {
    "hold" -> "Hold"
    "confirmed" -> "Confirmed"
    "checked_in" -> "In house"
    "checked_out" -> "Checked out"
    "cancelled" -> "Cancelled"
    "expired" -> "Expired"
    else -> status.ifBlank { "-" }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "hold" -> MaterialTheme.appColors.warning
    "confirmed" -> MaterialTheme.appColors.info
    "checked_in" -> MaterialTheme.appColors.success
    "cancelled", "expired" -> MaterialTheme.appColors.danger
    else -> MaterialTheme.appColors.textMuted
}
