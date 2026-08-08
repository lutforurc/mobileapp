package com.example.cashbookbd.ui.hrm

import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.hrm.model.HolidayEvent
import com.example.cashbookbd.ui.hrm.model.HolidayRecord
import com.example.cashbookbd.ui.hrm.model.WeeklyHolidayPolicy
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** The web's "All Branches" option (blank id, applied client-side only). */
private val ALL_BRANCHES = BranchOption(id = 0L, name = "All Branches")

private val TYPE_OPTIONS = listOf(
    "All Types" to "",
    "Holiday" to "holiday",
    "Weekly Holiday" to "weekly",
)

data class HolidayCalendarUiState(
    val branches: List<BranchOption> = listOf(ALL_BRANCHES),
    val selectedBranch: BranchOption = ALL_BRANCHES,
    val isBranchesLoading: Boolean = false,

    val monthYear: MonthYear = MonthYear.current(),
    /** "" = all, "holiday", "weekly" — the web's type filter values. */
    val typeFilter: String = "",

    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val events: List<HolidayEvent> = emptyList(),
    /** The month the loaded events belong to (the grid draws this month). */
    val appliedMonth: MonthYear = MonthYear.current(),
    val appliedBranchName: String = ALL_BRANCHES.name,

    val sessionExpired: Boolean = false,
) {
    val canLoad: Boolean get() = !isLoading
}

class HolidayCalendarViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HolidayCalendarUiState())
    val uiState: StateFlow<HolidayCalendarUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = listOf(ALL_BRANCHES) + result.data.branches,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(selectedBranch = branch, loaded = false) }

    fun onMonthSelected(monthYear: MonthYear) =
        _uiState.update { it.copy(monthYear = monthYear, loaded = false) }

    fun onTypeSelected(type: String) =
        _uiState.update { it.copy(typeFilter = type, loaded = false) }

    fun load() {
        val state = _uiState.value
        if (state.isLoading) return

        val monthStart = monthEdge(state.monthYear, first = true)
        val monthEnd = monthEdge(state.monthYear, first = false)

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // The web fires both fetches and merges client-side.
            val holidays = hrmRepository.holidays(dateFrom = monthStart, dateTo = monthEnd)
            if (holidays is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = holidays.message,
                        sessionExpired = it.sessionExpired || holidays.isUnauthorized,
                    )
                }
                return@launch
            }
            val weekly = hrmRepository.weeklyHolidays()
            if (weekly is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = weekly.message,
                        sessionExpired = it.sessionExpired || weekly.isUnauthorized,
                    )
                }
                return@launch
            }

            val branchNames = state.branches.associate { it.id.toString() to it.name }
            val events = buildCalendarEvents(
                holidays = (holidays as Resource.Success).data,
                weekly = (weekly as Resource.Success).data,
                branchId = state.selectedBranch.id,
                branchNames = branchNames,
                typeFilter = state.typeFilter,
                monthStart = monthStart,
                monthEnd = monthEnd,
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loaded = true,
                    events = events,
                    appliedMonth = state.monthYear,
                    appliedBranchName = state.selectedBranch.name,
                )
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                HolidayCalendarViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Event synthesis — the web's calendarRows, verbatim
// ---------------------------------------------------------------------------

private val DAY_NAMES = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
)

private fun monthEdge(monthYear: MonthYear, first: Boolean): String {
    val cal = Calendar.getInstance().apply { clear(); set(monthYear.year, monthYear.month - 1, 1) }
    val day = if (first) 1 else cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    return String.format(Locale.US, "%04d-%02d-%02d", monthYear.year, monthYear.month, day)
}

private fun buildCalendarEvents(
    holidays: List<HolidayRecord>,
    weekly: List<WeeklyHolidayPolicy>,
    branchId: Long,
    branchNames: Map<String, String>,
    typeFilter: String,
    monthStart: String,
    monthEnd: String,
): List<HolidayEvent> {
    fun nameOf(id: String): String = branchNames[id] ?: "All Branches"
    val filterId = if (branchId == 0L) "" else branchId.toString()

    val rows = ArrayList<HolidayEvent>()

    // Stored holidays: branch filter is client-side, like the web.
    holidays.forEach { h ->
        if (filterId.isNotBlank() && h.branchId.isNotBlank() && h.branchId != filterId) return@forEach
        if (h.holidayDate < monthStart || h.holidayDate > monthEnd) return@forEach
        rows += HolidayEvent(
            id = "holiday-${h.id.ifBlank { h.holidayDate }}-${h.branchId.ifBlank { "all" }}",
            calendarDate = h.holidayDate,
            title = h.holidayName.ifBlank { "-" },
            type = "Holiday",
            subType = h.holidayType.ifBlank { "-" },
            branchName = nameOf(h.branchId),
            paid = h.isPaid,
            optional = h.isOptional,
            remarks = h.remarks,
        )
    }

    // Weekly policies; when none survive, the web fabricates a Friday policy
    // ("default-friday") and paints every Friday — reproduced for parity.
    var enabled = weekly.filter { policy ->
        policy.isEnabled &&
            (filterId.isBlank() || policy.branchId.isBlank() || policy.branchId == filterId)
    }
    if (enabled.isEmpty()) {
        enabled = listOf(
            WeeklyHolidayPolicy(
                id = "default-friday",
                branchId = filterId,
                dayOfWeek = 5,
                isEnabled = true,
                effectiveFrom = "",
                effectiveTo = "",
                remarks = "Default weekly holiday",
            )
        )
    }
    for (policy in enabled) {
        val start = maxOf(monthStart, policy.effectiveFrom.ifBlank { monthStart })
        val last = minOf(monthEnd, policy.effectiveTo.ifBlank { monthEnd })
        val cal = calendarOf(start) ?: continue
        val end = calendarOf(last) ?: continue
        val dayName = DAY_NAMES.getOrNull(policy.dayOfWeek) ?: continue
        while (!cal.after(end) && rows.size < 370) {
            // Calendar's DAY_OF_WEEK is 1-based Sunday; the policy stores 0-based.
            if (cal.get(Calendar.DAY_OF_WEEK) - 1 == policy.dayOfWeek) {
                val dateKey = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH),
                )
                rows += HolidayEvent(
                    id = "weekly-${policy.id.ifBlank { policy.dayOfWeek.toString() }}-" +
                        "$dateKey-${policy.branchId.ifBlank { "all" }}",
                    calendarDate = dateKey,
                    title = "$dayName Weekly Holiday",
                    type = "Weekly Holiday",
                    subType = dayName,
                    branchName = nameOf(policy.branchId),
                    paid = true,
                    optional = false,
                    remarks = policy.remarks,
                )
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    return rows
        .filter {
            when (typeFilter) {
                "holiday" -> it.isHoliday
                "weekly" -> it.isWeekly
                else -> true
            }
        }
        .sortedWith(compareBy({ it.calendarDate }, { it.type }))
}

private fun calendarOf(date: String): Calendar? {
    val parts = date.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 3) return null
    return Calendar.getInstance().apply { clear(); set(parts[0], parts[1] - 1, parts[2]) }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * Holiday Calendar — the web's month grid: weekday header, tinted holiday /
 * weekly-holiday cells with event chips, legend, and the five summary tiles.
 * Tapping a day opens its events (the web's hover tooltip).
 */
@Composable
fun HolidayCalendarScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HolidayCalendarViewModel =
        viewModel(factory = HolidayCalendarViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Holiday Calendar",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HrmMonthField(
                        label = "Month / Year",
                        value = state.monthYear,
                        onSelected = viewModel::onMonthSelected,
                        modifier = Modifier.weight(1f),
                    )
                    TypeDropdown(
                        selected = state.typeFilter,
                        onSelected = viewModel::onTypeSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HrmBranchDropdown(
                        branches = state.branches,
                        selected = state.selectedBranch,
                        isLoading = state.isBranchesLoading,
                        onSelected = viewModel::onBranchSelected,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Load",
                        onClick = viewModel::load,
                        enabled = state.canLoad,
                        isLoading = state.isLoading,
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

                !state.loaded -> Box(
                    Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Choose your filters, then tap Load.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                else -> CalendarContent(state)
            }
        }
    }
}

@Composable
private fun TypeDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = TYPE_OPTIONS.firstOrNull { it.second == selected }?.first ?: "All Types"

    Box(modifier = modifier) {
        PickerField(
            label = "Type",
            value = label,
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TYPE_OPTIONS.forEach { (title, value) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarContent(state: HolidayCalendarUiState) {
    var selectedDate by remember { mutableStateOf<String?>(null) }
    val eventsByDate = remember(state.events) { state.events.groupBy { it.calendarDate } }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // Summary tiles — the web's five cards.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CalendarTile("Total Days", state.events.size)
            CalendarTile("Holiday", state.events.count { it.isHoliday })
            CalendarTile("Weekly Holiday", state.events.count { it.isWeekly })
            CalendarTile("Paid", state.events.count { it.paid })
            CalendarTile("Optional", state.events.count { it.optional })
        }

        // Month heading + legend.
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "${MONTH_NAMES[state.appliedMonth.month - 1]} ${state.appliedMonth.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.SemiBold,
            )
            Text(
                text = state.appliedBranchName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendChip(MaterialTheme.colorScheme.primaryContainer, "Holiday")
                LegendChip(MaterialTheme.colorScheme.secondaryContainer, "Weekly")
                LegendChip(MaterialTheme.colorScheme.tertiaryContainer, "Optional")
            }
        }
        Spacer(Modifier.height(8.dp))

        // Weekday header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            DAY_NAMES.forEach { day ->
                Text(
                    text = day.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }

        // The month grid, always whole weeks (leading/trailing padding slots).
        val cal = Calendar.getInstance().apply {
            clear(); set(state.appliedMonth.year, state.appliedMonth.month - 1, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - 1
        val slots: List<Int?> = List(firstDayOffset) { null } + (1..daysInMonth).toList()
        val trailing = (7 - slots.size % 7) % 7
        val allSlots = slots + List(trailing) { null }
        val monthPart = String.format(Locale.US, "%02d", state.appliedMonth.month)

        allSlots.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                week.forEach { day ->
                    val dateKey = day?.let {
                        "${state.appliedMonth.year}-$monthPart-" +
                            String.format(Locale.US, "%02d", it)
                    }
                    DayCell(
                        day = day,
                        events = dateKey?.let { eventsByDate[it] }.orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { if (dateKey != null) selectedDate = dateKey },
                    )
                }
            }
        }
    }

    selectedDate?.let { date ->
        DayEventsSheet(
            date = date,
            events = eventsByDate[date].orEmpty(),
            onDismiss = { selectedDate = null },
        )
    }
}

@Composable
private fun DayCell(
    day: Int?,
    events: List<HolidayEvent>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val hasHoliday = events.any { it.isHoliday }
    val hasWeekly = events.any { it.isWeekly }
    val hasOptional = events.any { it.optional }

    val background = when {
        day == null -> MaterialTheme.colorScheme.surfaceVariant
        hasHoliday && hasOptional -> MaterialTheme.colorScheme.tertiaryContainer
        hasHoliday -> MaterialTheme.colorScheme.primaryContainer
        hasWeekly -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val onCell = when {
        day == null -> MaterialTheme.colorScheme.onSurfaceVariant
        hasHoliday && hasOptional -> MaterialTheme.colorScheme.onTertiaryContainer
        hasHoliday -> MaterialTheme.colorScheme.onPrimaryContainer
        hasWeekly -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(background)
            .clickable(enabled = day != null && events.isNotEmpty(), onClick = onClick)
            .padding(3.dp),
    ) {
        if (day != null) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.SemiBold,
                color = onCell,
            )
            // Up to three chips, then "+N more", as on the web.
            events.take(3).forEach { event ->
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = onCell,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (events.size > 3) {
                Text(
                    text = "+${events.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = onCell,
                )
            }
        }
    }
}

@Composable
private fun LegendChip(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
    }
}

@Composable
private fun CalendarTile(label: String, value: Int) {
    SummaryTile {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The day's events — the mobile stand-in for the web's hover tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEventsSheet(
    date: String,
    events: List<HolidayEvent>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            events.forEach { event ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    val detail = buildList {
                        add(event.type)
                        add(event.branchName)
                        if (event.paid) add("Paid")
                        if (event.optional) add("Optional")
                        if (event.remarks.isNotBlank()) add(event.remarks)
                    }.joinToString("  •  ")
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
