package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.cashbookbd.data.repository.HkCounts
import com.example.cashbookbd.data.repository.HotelCollection
import com.example.cashbookbd.data.repository.HotelOpsRepository
import com.example.cashbookbd.data.repository.HotelPerformance
import com.example.cashbookbd.data.repository.HotelPerformanceDay
import com.example.cashbookbd.data.repository.HotelRegisterCounts
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class HotelDashboardUiState(
    val branches: List<BranchOption> = emptyList(),
    val branch: BranchOption? = null,
    val isLoading: Boolean = false,
    /** Tonight's three numbers — null when the register cannot be read. */
    val counts: HotelRegisterCounts? = null,
    /** The board's counts — null when housekeeping is not visible to this user. */
    val rooms: HkCounts? = null,
    /** The month so far — null when the report cannot be read or the branch lets no rooms. */
    val run: HotelPerformance? = null,
    /** Money taken this month — null when the report cannot be read. */
    val takings: HotelCollection? = null,
    val sessionExpired: Boolean = false,
)

class HotelDashboardViewModel(
    private val repository: HotelOpsRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
    private val canHousekeeping: Boolean,
    private val canReports: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelDashboardUiState(isLoading = true))
    val uiState: StateFlow<HotelDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val branches = hotelOpsLoadBranches(reportRepository, ownBranchId)
            if (branches.unauthorized) {
                _uiState.update { it.copy(isLoading = false, sessionExpired = true) }
                return@launch
            }
            _uiState.update { it.copy(branches = branches.branches, branch = branches.selected) }
            load()
        }
    }

    /**
     * Four reads, three permissions, and each lands on its own. A clerk who
     * may see the register and not the takings must get the register, and one
     * refusal in an all-or-nothing wait would have blanked the page for them.
     *
     * Tonight is the CALENDAR date, never the branch's transaction date: the
     * books may be closed to July while a guest sleeps here in August.
     */
    fun load() {
        val branchId = _uiState.value.branch?.id
        val today = SimpleDate.today()
        val monthStart = HotelOpsDates.monthStart(today)
        _uiState.update { it.copy(isLoading = true, counts = null, rooms = null, run = null, takings = null) }

        val jobs = mutableListOf<Job>()
        if (canReports) {
            // counts_only: the register's rows carry guests' NIDs and nothing
            // here displays one — the dashboard asks for the numbers alone.
            jobs += viewModelScope.launch {
                settle(repository.fetchRegister(today.toApi(), "in_house", branchId, countsOnly = true)) {
                    copy(counts = it.counts)
                }
            }
            jobs += viewModelScope.launch {
                settle(repository.fetchPerformance(monthStart.toApi(), today.toApi(), branchId)) { copy(run = it) }
            }
            jobs += viewModelScope.launch {
                settle(repository.fetchCollection(monthStart.toApi(), today.toApi(), branchId)) { copy(takings = it) }
            }
        }
        if (canHousekeeping) {
            jobs += viewModelScope.launch {
                settle(repository.fetchBoard(branchId)) { copy(rooms = it.counts) }
            }
        }
        viewModelScope.launch {
            jobs.joinAll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** A band that cannot be read disappears — it never shows an error. Only a lost session is acted on. */
    private inline fun <T> settle(
        result: Resource<T>,
        into: HotelDashboardUiState.(T) -> HotelDashboardUiState,
    ) {
        when (result) {
            is Resource.Success -> _uiState.update { it.into(result.data) }
            is Resource.Error -> if (result.isUnauthorized) _uiState.update { it.copy(sessionExpired = true) }
            Resource.Loading -> Unit
        }
    }

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(branch = branch) }
        load()
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val session = ServiceLocator.provideSessionManager(app)
                HotelDashboardViewModel(
                    repository = HotelOpsRepository.get(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = session.state.value.settings?.branchId,
                    canHousekeeping = session.can("hotel.housekeeping.view"),
                    canReports = session.can("hotel.report.view"),
                )
            }
        }
    }
}

/**
 * The dashboard a hotel opens the morning on.
 *
 * What a property asks at nine o'clock is not what sold and what was bought —
 * on a motel those read nought forever — but who is in the building, who is
 * arriving, what can still be sold tonight, which rooms are not ready, and
 * what the month has been worth. Every figure is the report's own: nothing
 * here counts a night or divides an average, so this page and the reports
 * cannot disagree by a rounding rule.
 */
@Composable
fun HotelDashboardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelDashboardViewModel = viewModel(
        factory = HotelDashboardViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    val run = state.run
    val totals = run?.totals
    // The last night of the range IS tonight, because the range ends today.
    val tonight = run?.daily?.lastOrNull()
    val nights = run?.daily.orEmpty()
    val notReady = state.rooms?.let { it.dirty + it.cleaning }

    AuthenticatedShell(
        title = "Hotel Dashboard",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.branch?.name ?: "The property",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = AppFontWeight.Bold,
                    )
                    Text(
                        text = when {
                            run != null -> "This month so far · ${HotelOpsDates.display(run.from)} to ${HotelOpsDates.display(run.to)}"
                            state.isLoading -> "Reading the property…"
                            else -> "This month so far"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                LinkButton(
                    text = "Refresh",
                    onClick = viewModel::load,
                    icon = Icons.Filled.Refresh,
                    enabled = !state.isLoading,
                )
            }
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            HotelOpsBranchPicker(
                branches = state.branches,
                selected = state.branch,
                onSelected = viewModel::onBranch,
            )

            // Tonight. The desk's band, first: at nine in the morning nobody
            // is asking about the month.
            if (state.counts != null || tonight != null || state.rooms != null) {
                TonightBand(counts = state.counts, tonight = tonight, rooms = run?.rooms, board = state.rooms, notReady = notReady)
            }

            // The month. The owner's band.
            if (totals != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotelOpsFigure(
                        label = "Occupancy",
                        value = hotelOpsPercent(totals.occupancy, 2),
                        working = "${totals.roomNightsSold} of ${totals.roomNightsAvailable} room-nights",
                        modifier = Modifier.weight(1f),
                    )
                    HotelOpsFigure(
                        label = "ADR",
                        value = hotelOpsMoney(totals.adr),
                        working = "per room-night SOLD",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotelOpsFigure(
                        label = "RevPAR",
                        value = hotelOpsMoney(totals.revpar),
                        working = "per room the property HAS",
                        tone = MaterialTheme.colorScheme.primary,
                        lead = true,
                        modifier = Modifier.weight(1f),
                    )
                    HotelOpsFigure(
                        label = "Room revenue",
                        value = hotelOpsMoney(totals.revenue),
                        working = "${run.rooms} rooms over ${run.days} nights",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (nights.isNotEmpty() && run != null) {
                NightByNight(nights = nights, rooms = run.rooms)
            }

            if (run != null && run.byRoomType.isNotEmpty()) {
                ByRoomType(run = run)
            }

            state.takings?.let { MoneyTaken(takings = it) }

            if (!state.isLoading && state.counts == null && state.rooms == null && run == null && state.takings == null) {
                HotelOpsProblem(
                    text = "Nothing on this property can be read with your permissions.",
                    onRetry = viewModel::load,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TonightBand(
    counts: HotelRegisterCounts?,
    tonight: HotelPerformanceDay?,
    rooms: Int?,
    board: HkCounts?,
    notReady: Int?,
) {
    val dash = "—"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotelOpsFigure(
                label = "In the building",
                value = counts?.inHouse?.toString() ?: dash,
                working = "guests sleeping here tonight",
                tone = MaterialTheme.appColors.info,
                modifier = Modifier.weight(1f),
            )
            HotelOpsFigure(
                label = "Arriving",
                value = counts?.arrivals?.toString() ?: dash,
                working = "expected at the desk today",
                tone = MaterialTheme.appColors.success,
                modifier = Modifier.weight(1f),
            )
            HotelOpsFigure(
                label = "Leaving",
                value = counts?.departures?.toString() ?: dash,
                working = "rooms to turn round today",
                tone = MaterialTheme.appColors.danger,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Free is rooms LESS sold LESS held. A room on hold is neither
            // sold nor free, and offering it is how the desk promises a bed
            // somebody is already waiting on.
            HotelOpsFigure(
                label = "Free tonight",
                value = tonight?.free?.toString() ?: dash,
                working = tonight?.let {
                    buildString {
                        append(it.sold).append(" let")
                        if (it.held > 0) append(", ").append(it.held).append(" on hold")
                        append(" of ").append(rooms ?: 0)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            HotelOpsFigure(
                label = "Not ready",
                value = notReady?.toString() ?: dash,
                working = if (board != null) {
                    buildString {
                        append(board.dirty).append(" dirty, ").append(board.cleaning).append(" being done")
                        if (board.outOfOrder > 0) append(", ").append(board.outOfOrder).append(" out of order")
                    }
                } else {
                    "housekeeping not visible to you"
                },
                tone = if ((notReady ?: 0) > 0) MaterialTheme.appColors.warning else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Every night in the range, empty ones included — a strip drawn only from the
 * nights that sold something closes the gaps, and the gaps are the question.
 * Every bar is measured against the same ceiling, the property's room count.
 */
@Composable
private fun NightByNight(nights: List<HotelPerformanceDay>, rooms: Int) {
    HotelOpsSection(
        title = "Night by night",
        footer = "Occupancy each night, against the $rooms rooms this property has today.",
    ) {
        val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                nights.forEach { night ->
                    val fraction = (night.occupancy / 100.0).toFloat().coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(AppShape)
                            .background(fill),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // A label on every column would not fit thirty-one across a
                // phone; the first, the last and every fifth night are enough
                // to read the strip by.
                nights.forEachIndexed { index, night ->
                    val day = HotelOpsDates.dayOf(night.date)
                    val labelled = index == 0 || index == nights.lastIndex || (day.toIntOrNull() ?: 0) % 5 == 0
                    Text(
                        text = if (labelled) day else "",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.appColors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ByRoomType(run: HotelPerformance) {
    HotelOpsSection(
        title = "By room type",
        footer = "A floor at 40% beside one at 95% is a pricing question the single ADR hides.",
    ) {
        Column {
            run.byRoomType.forEachIndexed { index, type ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.appColors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type.name.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = AppFontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${type.rooms} rooms · ${type.sold} of ${type.roomNightsAvailable} sold",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                    Column(modifier = Modifier.width(88.dp)) {
                        HotelOpsBar(fraction = (type.occupancy / 100.0).toFloat(), modifier = Modifier.fillMaxWidth())
                        Text(
                            text = hotelOpsPercent(type.occupancy, 2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (type.sold > 0) hotelOpsMoney(type.adr) else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = AppFontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text("ADR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
                    }
                }
            }
        }
    }
}

/**
 * Netted: a refund is stored positive — the direction lives in the purpose —
 * so "In hand" is the server's signed total, never the column added up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneyTaken(takings: HotelCollection) {
    val totals = takings.totals
    HotelOpsSection(title = "Money taken this month") {
        Column {
            MoneyRow(label = "Received", value = hotelOpsMoney(totals.received), tone = MaterialTheme.appColors.success)
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            MoneyRow(label = "Given back", value = hotelOpsMoney(totals.refunded), tone = MaterialTheme.appColors.danger)
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            MoneyRow(label = "In hand", value = hotelOpsMoney(totals.net), tone = MaterialTheme.colorScheme.primary, lead = true)
            if (totals.byMethod.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.appColors.divider)
                FlowRow(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    totals.byMethod.forEach { item ->
                        Text(
                            text = "${item.name}: ${hotelOpsMoney(item.amount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            if (takings.unposted > 0) {
                Text(
                    text = "${takings.unposted} ${if (takings.unposted == 1) "receipt is" else "receipts are"} not in the ledger.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.appColors.warningTint)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    text = "${totals.count} receipts, every one of them posted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MoneyRow(
    label: String,
    value: String,
    tone: androidx.compose.ui.graphics.Color,
    lead: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (lead) AppFontWeight.SemiBold else AppFontWeight.Normal,
            color = MaterialTheme.appColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (lead) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = AppFontWeight.Bold,
            color = tone,
        )
    }
}
