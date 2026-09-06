package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelCalendarDay
import com.example.cashbookbd.data.repository.HotelCalendarMonth
import com.example.cashbookbd.data.repository.HotelOpsRepository
import com.example.cashbookbd.data.repository.HotelTape
import com.example.cashbookbd.data.repository.HotelTapeCell
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two shapes of the same nights: the owner's month and the desk's tape. */
private val VIEWS = listOf("month" to "The month", "tape" to "Room by room")

/** How far the tape reaches. A month of columns is the edge of what a screen can carry. */
private val SPANS = listOf(7, 14, 21, 31)

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class HotelCalendarUiState(
    val view: String = "month",
    val branches: List<BranchOption> = emptyList(),
    val branch: BranchOption? = null,
    /** The first of the month on show. */
    val anchor: SimpleDate = HotelOpsDates.monthStart(SimpleDate.today()),
    val month: HotelCalendarMonth? = null,
    val monthLoading: Boolean = false,
    val monthError: String? = null,
    val from: SimpleDate = SimpleDate.today(),
    val span: Int = 14,
    val tape: HotelTape? = null,
    val tapeLoading: Boolean = false,
    val tapeError: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelCalendarViewModel(
    private val repository: HotelOpsRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelCalendarUiState(monthLoading = true))
    val uiState: StateFlow<HotelCalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val branches = hotelOpsLoadBranches(reportRepository, ownBranchId)
            if (branches.unauthorized) {
                _uiState.update { it.copy(monthLoading = false, sessionExpired = true) }
                return@launch
            }
            _uiState.update { it.copy(branches = branches.branches, branch = branches.selected) }
            loadCurrent()
        }
    }

    private fun loadCurrent() {
        if (_uiState.value.view == "month") loadMonth() else loadTape()
    }

    fun loadMonth() {
        val state = _uiState.value
        _uiState.update { it.copy(monthLoading = true, monthError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchMonth(state.anchor.toApi(), state.branch?.id)) {
                is Resource.Success -> _uiState.update { it.copy(monthLoading = false, month = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        monthLoading = false,
                        monthError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun loadTape() {
        val state = _uiState.value
        _uiState.update { it.copy(tapeLoading = true, tapeError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchTimeline(state.from.toApi(), state.span, state.branch?.id)) {
                is Resource.Success -> _uiState.update { it.copy(tapeLoading = false, tape = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        tapeLoading = false,
                        tape = null,
                        tapeError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onView(view: String) {
        if (_uiState.value.view == view) return
        _uiState.update { it.copy(view = view) }
        val state = _uiState.value
        if (view == "month" && state.month == null) loadMonth()
        if (view == "tape" && state.tape == null && state.tapeError == null) loadTape()
    }

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(branch = branch, month = null, tape = null, tapeError = null) }
        loadCurrent()
    }

    fun shiftMonth(by: Int) {
        _uiState.update { it.copy(anchor = HotelOpsDates.shiftMonth(it.anchor, by)) }
        loadMonth()
    }

    fun onFrom(date: SimpleDate) {
        _uiState.update { it.copy(from = date) }
        loadTape()
    }

    fun onSpan(days: Int) {
        _uiState.update { it.copy(span = days) }
        loadTape()
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                HotelCalendarViewModel(
                    repository = HotelOpsRepository.get(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

/**
 * The month laid out as weeks, Sunday first, with the leading blanks a
 * calendar needs. Bangladesh's working week begins on Sunday, and a calendar
 * that started on Monday would put the weekend in the middle of the row.
 */
internal fun hotelCalendarWeeks(days: List<HotelCalendarDay>): List<List<HotelCalendarDay?>> {
    if (days.isEmpty()) return emptyList()
    val first = SimpleDate.fromApi(days.first().date)
    val lead = if (first == null) 0 else HotelOpsDates.sundayFirstIndex(first)
    val cells: MutableList<HotelCalendarDay?> = MutableList(lead) { null }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

/**
 * How full a night was, as a step on ONE ramp. Occupancy is a magnitude and a
 * magnitude wants a single hue rising in strength; a rainbow would invent a
 * midpoint that means something, and would paint a full hotel alarm-red.
 * The number is always printed beside the colour — colour alone carries
 * nothing to a reader with colour blindness.
 */
private fun heatBucket(occupancy: Double): Int = when {
    occupancy <= 0.0 -> 0
    occupancy <= 25.0 -> 1
    occupancy <= 50.0 -> 2
    occupancy <= 75.0 -> 3
    occupancy <= 99.0 -> 4
    else -> 5
}

private val HEAT_ALPHAS = floatArrayOf(0f, 0.14f, 0.30f, 0.48f, 0.70f, 0.92f)

@Composable
private fun heatFill(bucket: Int): Color =
    if (bucket == 0) MaterialTheme.appColors.cardMuted
    else MaterialTheme.colorScheme.primary.copy(alpha = HEAT_ALPHAS[bucket])

/** The ink travels with the fill: the two strongest steps need the on-primary ink. */
@Composable
private fun heatInk(bucket: Int): Color =
    if (bucket >= 4) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

/**
 * The property over TIME rather than over one set of dates.
 *
 * The availability screen answers "what is free between these two dates" —
 * the question somebody taking a booking asks. Neither of these could be
 * asked of it: how full was August (the owner's), and which room has a
 * three-night hole next week (the desk's). Nothing here books anything: a
 * room is taken on the Bookings screen, and this is what was held when the
 * page was read.
 */
@Composable
fun HotelCalendarScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelCalendarViewModel = viewModel(
        factory = HotelCalendarViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Calendar",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VIEWS.forEach { (id, label) ->
                    FilterChip(
                        selected = state.view == id,
                        onClick = { viewModel.onView(id) },
                        label = { Text(label) },
                    )
                }
            }
            HotelOpsBranchPicker(
                branches = state.branches,
                selected = state.branch,
                onSelected = viewModel::onBranch,
            )

            if (state.view == "month") {
                MonthView(state = state, onShift = viewModel::shiftMonth, onRetry = viewModel::loadMonth)
            } else {
                TapeView(
                    state = state,
                    onFrom = { hotelOpsPickDate(context, state.from, viewModel::onFrom) },
                    onSpan = viewModel::onSpan,
                    onRetry = viewModel::loadTape,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
//  The month
// ---------------------------------------------------------------------------

@Composable
private fun MonthView(
    state: HotelCalendarUiState,
    onShift: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }, enabled = !state.monthLoading) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "The month before")
        }
        Text(
            text = HotelOpsDates.monthTitle(state.anchor),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onShift(1) }, enabled = !state.monthLoading) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "The month after")
        }
    }

    when {
        state.monthError != null -> HotelOpsProblem(text = state.monthError, onRetry = onRetry)
        state.monthLoading && state.month == null -> HotelOpsLoading()
        state.month != null -> MonthBody(month = state.month, dimmed = state.monthLoading)
    }
}

@Composable
private fun MonthBody(month: HotelCalendarMonth, dimmed: Boolean) {
    val totals = month.totals
    val figures = listOf(
        Triple("Occupancy", hotelOpsPercent(totals.occupancy, 1), null),
        Triple("Seat-nights sold", totals.sold.toString(), null),
        Triple("Held, not sold", totals.held.toString(), null),
        Triple("ADR", hotelOpsMoney(totals.adr), "per bed SOLD"),
        Triple("RevPAR", hotelOpsMoney(totals.revpar), "per bed the property HAS"),
    )
    Column(
        modifier = Modifier.alpha(if (dimmed) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HotelOpsGrid(items = figures, columns = 3) { (label, value, working) ->
            HotelOpsFigure(label = label, value = value, working = working, modifier = Modifier.weight(1f))
        }
        HotelOpsNote(
            "Room revenue is the rent held against each night — before service charge and VAT, " +
                "which is what ADR means. " + month.capacityNote,
        )

        val weeks = remember(month) { hotelCalendarWeeks(month.days) }
        val today = remember { SimpleDate.today().toApi() }
        val line = MaterialTheme.appColors.gridLine
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    )
                }
            }
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(92.dp)
                                    .border(0.5.dp, line),
                            )
                        } else {
                            MonthCell(day = day, isToday = day.date == today, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("empty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
            (0..5).forEach { bucket ->
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 12.dp)
                        .background(heatFill(bucket))
                        .border(0.5.dp, line),
                )
            }
            Text("full", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
        }
        HotelOpsNote(
            "↓ arrivals · ↑ departures. A departure is counted on the morning after the last night slept.",
        )
    }
}

@Composable
private fun MonthCell(day: HotelCalendarDay, isToday: Boolean, modifier: Modifier = Modifier) {
    val bucket = heatBucket(day.occupancy)
    val ink = heatInk(bucket)
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
    Column(
        modifier = modifier
            .height(92.dp)
            .background(heatFill(bucket))
            .border(
                width = if (isToday) 2.dp else 0.5.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.gridLine,
            )
            // Past nights recede; the ink goes with the fill so the number stays readable.
            .alpha(if (day.isPast) 0.6f else 1f)
            .padding(3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = HotelOpsDates.dayOf(day.date),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = AppFontWeight.SemiBold,
                color = ink,
            )
            // The number, always, beside the colour.
            Text(text = hotelOpsPercent(day.occupancy, 0), style = small, color = ink)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                day.sold > 0 || day.held > 0 -> buildString {
                    append(day.sold).append(" sold")
                    if (day.held > 0) append(" · ").append(day.held).append(" held")
                }
                else -> "empty"
            },
            style = small,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (day.revenue > 0.0) {
            Text(text = hotelOpsMoney(day.revenue), style = small, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (day.arrivals > 0 || day.departures > 0) {
            Text(
                text = buildString {
                    if (day.arrivals > 0) append("↓").append(day.arrivals)
                    if (day.arrivals > 0 && day.departures > 0) append(" ")
                    if (day.departures > 0) append("↑").append(day.departures)
                },
                style = small,
                color = ink,
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  The tape
// ---------------------------------------------------------------------------

private val NAME_COL = 128.dp
private val DAY_COL = 36.dp

@Composable
private fun TapeView(
    state: HotelCalendarUiState,
    onFrom: () -> Unit,
    onSpan: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    PickerField(
        label = "From",
        value = state.from.toDisplay(),
        trailingIcon = Icons.Filled.DateRange,
        onClick = onFrom,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SPANS.forEach { days ->
            FilterChip(
                selected = state.span == days,
                onClick = { onSpan(days) },
                label = { Text("$days nights") },
            )
        }
    }
    when {
        state.tapeError != null -> HotelOpsProblem(text = state.tapeError, onRetry = onRetry)
        state.tapeLoading && state.tape == null -> HotelOpsLoading()
        state.tape != null -> TapeBody(tape = state.tape, dimmed = state.tapeLoading)
    }
}

@Composable
private fun TapeBody(tape: HotelTape, dimmed: Boolean) {
    val line = MaterialTheme.appColors.gridLine
    val muted = MaterialTheme.appColors.textOnScreenMuted
    Column(
        modifier = Modifier.alpha(if (dimmed) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A fixed name column inside one horizontal scroll: the name travels
        // with its row, which is what a desk scanning for a gap needs.
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(NAME_COL)
                        .fillMaxHeight()
                        .border(0.5.dp, line)
                        .padding(4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Room", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.Bold)
                }
                tape.dates.forEach { d ->
                    Column(
                        modifier = Modifier
                            .width(DAY_COL)
                            .border(0.5.dp, line)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = d.weekday.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                        Text(
                            text = HotelOpsDates.dayOf(d.date),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = AppFontWeight.SemiBold,
                            color = if (d.isPast) muted else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            tape.rooms.forEach { room ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Column(
                        modifier = Modifier
                            .width(NAME_COL)
                            .fillMaxHeight()
                            .border(0.5.dp, line)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = AppFontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${room.capacity} ${if (room.capacity == 1) "bed" else "beds"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                    room.cells.forEach { cell -> TapeCellBox(cell = cell) }
                }
            }
        }
        HotelOpsNote(
            "Each cell says how many of that room's beds are taken that night — amber is part of a " +
                "room, red is all of it. Nothing here books anything: a room is taken on the Bookings " +
                "screen, and this is what was held when the page was read.",
        )
    }
}

/**
 * A different question from the month grid, so a different language: this
 * asks whether ONE room is free or taken — a state — and keeps the colours the
 * room grid already uses for that state, so a clerk who has learned one screen
 * has learned both.
 */
@Composable
private fun TapeCellBox(cell: HotelTapeCell) {
    val fill = when (cell.state) {
        "full" -> MaterialTheme.appColors.dangerTint
        "part" -> MaterialTheme.appColors.warningTint
        else -> MaterialTheme.appColors.cardMuted
    }
    Box(
        modifier = Modifier
            .width(DAY_COL)
            .fillMaxHeight()
            .background(fill)
            .border(0.5.dp, MaterialTheme.appColors.gridLine),
        contentAlignment = Alignment.Center,
    ) {
        // The count, not just the colour: three of eight beds gone is not a full room.
        if (cell.taken > 0) {
            Text(
                text = cell.taken.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.Bold,
            )
        }
    }
}
