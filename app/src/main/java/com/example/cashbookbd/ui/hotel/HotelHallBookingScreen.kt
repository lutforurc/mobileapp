package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.cashbookbd.data.repository.HallAvailability
import com.example.cashbookbd.data.repository.HallRow
import com.example.cashbookbd.data.repository.HallSitting
import com.example.cashbookbd.data.repository.HallSittingPick
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One picked sitting: a hall, a part of a day, and the day — plus what the chip says. */
data class HallPick(
    val resourceId: Long,
    val slotId: Long,
    val date: String,
    val hall: String,
    val sitting: String,
    val rent: Double,
) {
    val key: String get() = "$resourceId|$slotId|$date"
    val label: String get() = "$hall · $sitting · ${onTheDay(date)}"
}

data class HotelHallBookingUiState(
    val branches: List<SelectorOption> = emptyList(),
    val branchId: Long? = null,
    val date: SimpleDate = SimpleDate.today(),
    val isLoading: Boolean = false,
    /** The server's sentence when there is nothing to draw — no sittings, no halls. */
    val error: String? = null,
    val availability: HallAvailability? = null,
    val picked: List<HallPick> = emptyList(),
    val booker: String = "",
    val mobile: String = "",
    val notes: String = "",
    val asking: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = picked.sumOf { it.rent }
    val chosen: Set<String> get() = picked.map { it.key }.toSet()
}

class HotelHallBookingViewModel(
    private val repository: HotelSetupRepository,
    private val reportRepository: ReportRepository,
    defaultBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelHallBookingUiState(branchId = defaultBranchId))
    val uiState: StateFlow<HotelHallBookingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { s ->
                    val options = r.data.branches.map { SelectorOption(it.id.toString(), it.name) }
                    // The signed-in user's own property, else the first one listed.
                    val branchId = s.branchId?.takeIf { id -> r.data.branches.any { it.id == id } }
                        ?: r.data.branches.firstOrNull()?.id
                    s.copy(branches = options, branchId = branchId)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
            load()
        }
    }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.fetchHalls(branchId = s.branchId, date = s.date.toApi())) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, availability = r.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false, availability = null, error = r.message,
                        sessionExpired = it.sessionExpired || r.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The picks go with the property: a sitting id belongs to one branch. */
    fun onBranch(option: SelectorOption) {
        _uiState.update { it.copy(branchId = option.id.toLongOrNull(), picked = emptyList()) }
        load()
    }

    /**
     * The picks are NOT cleared when the date changes. A wedding is the
     * evening of the 29th and the morning of the 30th, and a screen that
     * forgot the first when the clerk went looking for the second could not
     * take that booking at all.
     */
    fun onDate(date: SimpleDate) {
        _uiState.update { it.copy(date = date) }
        load()
    }

    fun toggle(hall: HallRow, cell: HallSitting) {
        if (!cell.isFree) return
        _uiState.update { s ->
            val pick = HallPick(
                resourceId = hall.id,
                slotId = cell.slotId,
                date = hall.date.ifBlank { s.date.toApi() },
                hall = hall.displayName,
                sitting = cell.slot,
                rent = hall.rent ?: 0.0,
            )
            s.copy(picked = if (pick.key in s.chosen) s.picked.filterNot { it.key == pick.key } else s.picked + pick)
        }
    }

    fun remove(pick: HallPick) = _uiState.update { s -> s.copy(picked = s.picked.filterNot { it.key == pick.key }) }

    fun clear() = _uiState.update { it.copy(picked = emptyList()) }

    fun onBooker(v: String) = _uiState.update { it.copy(booker = v) }
    fun onMobile(v: String) = _uiState.update { it.copy(mobile = v) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }

    fun ask() {
        if (_uiState.value.booker.isBlank()) {
            _uiState.update { it.copy(message = "Say who is taking it first.") }
            return
        }
        _uiState.update { it.copy(asking = true) }
    }

    fun notYet() = _uiState.update { it.copy(asking = false) }

    /**
     * Nothing here reserves anything: the claim happens at the server against
     * the unique key, and a clash comes back as a sentence saying nothing was
     * booked. Either way the grid is read again — it was out of date the
     * moment it was drawn.
     */
    fun confirm() {
        val s = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.storeHallBooking(
                branchId = s.branchId,
                sittings = s.picked.map { HallSittingPick(it.resourceId, it.date, it.slotId) },
                bookerName = s.booker,
                bookerMobile = s.mobile,
                notes = s.notes,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false, asking = false, message = result.data,
                        picked = emptyList(), booker = "", mobile = "", notes = "",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, asking = false, message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
            load()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, defaultBranchId: Long?) = viewModelFactory {
            initializer {
                HotelHallBookingViewModel(
                    repository = HotelSetupRepository.get(context),
                    reportRepository = ServiceLocator.provideReportRepository(context.applicationContext),
                    defaultBranchId = defaultBranchId,
                )
            }
        }
    }
}

/**
 * Letting a hall — the community centre screen.
 *
 * Not the room screen with different words. A room is asked about over a
 * RANGE and answered per room; a hall is asked about on ONE DATE and answered
 * per SITTING — the morning may be free while the evening is a wedding. A
 * whole day is every sitting ticked, never one box meaning "all".
 */
@Composable
fun HotelHallBookingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val allowed = Permissions.hasAny(sessionState.permissions, listOf("hotel.booking.view"))
    val viewModel: HotelHallBookingViewModel = viewModel(
        factory = HotelHallBookingViewModel.provideFactory(context, sessionState.settings?.branchId),
    )
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
        title = "Hall Booking",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!allowed) {
            NoAccess("You don't have access to bookings.")
            return@AuthenticatedShell
        }
        val muted = MaterialTheme.appColors.textMuted
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown(
                            label = "Property",
                            options = state.branches,
                            selected = state.branches.firstOrNull { it.id == state.branchId?.toString() },
                            onSelected = viewModel::onBranch,
                            modifier = Modifier.weight(1f),
                        )
                        PickerField(
                            label = "Date",
                            value = state.date.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickHallDate(context, state.date, viewModel::onDate) },
                        )
                    }
                }
                when {
                    state.isLoading && state.availability == null -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.availability == null -> item {
                        Text(
                            state.error ?: "Nothing to show. Set a hall up on Hotel Setup, and the sittings it is let for.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                    else -> items(state.availability?.halls.orEmpty(), key = { "${it.id}|${it.date}" }) { hall ->
                        HallCard(hall = hall, chosen = state.chosen, onToggle = viewModel::toggle)
                    }
                }
                if (state.picked.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${state.picked.size} ${if (state.picked.size == 1) "sitting" else "sittings"} picked",
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${AmountFormat.format(state.total)} before service charge and VAT",
                                        style = MaterialTheme.typography.labelSmall, color = muted,
                                    )
                                }
                                state.picked.forEach { pick ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(pick.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        LinkButton(text = "remove", onClick = { viewModel.remove(pick) }, color = MaterialTheme.appColors.danger)
                                    }
                                }
                                AppTextField(value = state.booker, onValueChange = viewModel::onBooker, label = "Who is taking it", caption = "Booked by")
                                AppTextField(value = state.mobile, onValueChange = viewModel::onMobile, label = "01…", caption = "Mobile")
                                AppTextField(value = state.notes, onValueChange = viewModel::onNotes, label = "Wedding — 300 guests", caption = "Note (optional)")
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrimaryButton(
                                        text = "Book these sittings",
                                        onClick = viewModel::ask,
                                        enabled = state.booker.isNotBlank(),
                                        isLoading = state.isSaving,
                                        compact = true,
                                    )
                                    LinkButton(text = "Clear", onClick = viewModel::clear, color = muted)
                                }
                                if (state.booker.isBlank()) {
                                    Text("Say who is taking it first.", style = MaterialTheme.typography.labelSmall, color = muted)
                                }
                            }
                        }
                    }
                }
                item {
                    LinkButton(text = "Room bookings →", onClick = { navController.navigate(Routes.HOTEL_BOOKINGS) })
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.asking) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) viewModel.notYet() },
            title = { Text("Take these sittings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.booker.trim()} takes ${state.picked.size} ${if (state.picked.size == 1) "sitting" else "sittings"}.")
                    Text(state.picked.joinToString(", ") { it.label }, style = MaterialTheme.typography.bodySmall)
                    // The bill is not made here: the sittings are held at the
                    // rate they were confirmed at, and billed on the folio.
                    Text("${AmountFormat.format(state.total)} before service charge and VAT. Nothing is billed yet — that happens on the folio.")
                }
            },
            confirmButton = { PrimaryButton(text = "Book", onClick = viewModel::confirm, isLoading = state.isSaving, compact = true) },
            dismissButton = { LinkButton(text = "Not yet", onClick = viewModel::notYet, enabled = !state.isSaving) },
        )
    }
}

/** One hall on the date: its name and rate, then a cell per sitting. */
@Composable
private fun HallCard(hall: HallRow, chosen: Set<String>, onToggle: (HallRow, HallSitting) -> Unit) {
    val muted = MaterialTheme.appColors.textMuted
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hall.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            // The rent on a hall is the price of ONE sitting, never of a night
            // — labelled, because the room screen means the other thing by it.
            Text(
                listOfNotNull(
                    hall.building.takeIf { it.isNotBlank() },
                    hall.capacity.takeIf { it > 0 }?.let { "$it seats" },
                    hall.rent?.let { "${AmountFormat.format(it)} a sitting" } ?: "no rate set",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = muted,
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hall.sittings.forEach { cell ->
                    SittingCell(
                        cell = cell,
                        isChosen = "${hall.id}|${cell.slotId}|${hall.date}" in chosen,
                        onClick = { onToggle(hall, cell) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SittingCell(cell: HallSitting, isChosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val edge = when {
        isChosen -> MaterialTheme.colorScheme.primary
        cell.isFree -> MaterialTheme.appColors.border
        else -> MaterialTheme.appColors.border
    }
    val fill = when {
        isChosen -> MaterialTheme.appColors.primaryTint
        cell.isFree -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.appColors.cardMuted
    }
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .clickable(enabled = cell.isFree) { onClick() }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(cell.label.ifBlank { cell.slot }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted, maxLines = 2)
        Text(
            when {
                isChosen -> "✓ Taking it"
                cell.isFree -> "Free"
                cell.state == "closed" -> "Not for sale"
                else -> "Booked"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (cell.isFree || isChosen) MaterialTheme.appColors.text else MaterialTheme.appColors.textMuted,
        )
        // The reason is a sentence, not a colour: "roof leak" and "held for the
        // whole day" are different problems with different answers.
        if (cell.blockedReason.isNotBlank()) Text(cell.blockedReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
        if (cell.takenBy.isNotBlank()) Text(cell.takenBy, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
    }
}

private fun pickHallDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
