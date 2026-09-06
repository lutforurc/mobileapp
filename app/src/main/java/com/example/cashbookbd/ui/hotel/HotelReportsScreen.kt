package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.cashbookbd.data.repository.HotelCollection
import com.example.cashbookbd.data.repository.HotelCollectionRow
import com.example.cashbookbd.data.repository.HotelNamedAmount
import com.example.cashbookbd.data.repository.HotelOpsRepository
import com.example.cashbookbd.data.repository.HotelPerformance
import com.example.cashbookbd.data.repository.HotelPerformanceDay
import com.example.cashbookbd.data.repository.HotelPerformanceRoomType
import com.example.cashbookbd.data.repository.HotelRegister
import com.example.cashbookbd.data.repository.HotelRegisterRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

private val TABS = listOf("Register", "Money taken", "Performance")

/** The three questions the register answers, in the order the morning asks them. */
private val MODES = listOf(
    SelectorOption("in_house", "In the building"),
    SelectorOption("arrivals", "Arriving"),
    SelectorOption("departures", "Leaving"),
)

private val METHODS = listOf(
    SelectorOption("", "Every method"),
    SelectorOption("cash", "Cash"),
    SelectorOption("bank", "Bank"),
    SelectorOption("card", "Card"),
    SelectorOption("mobile", "Mobile"),
    SelectorOption("adjustment", "Adjustment"),
)

/** How long a typed search waits before it is sent — long enough to finish a word. */
private const val SEARCH_DEBOUNCE_MS = 400L

data class HotelReportsUiState(
    val tab: Int = 0,
    val branches: List<BranchOption> = emptyList(),
    val branch: BranchOption? = null,
    // Register
    val date: SimpleDate = SimpleDate.today(),
    val mode: SelectorOption = MODES[0],
    val query: String = "",
    val register: HotelRegister? = null,
    val registerLoading: Boolean = false,
    val registerError: String? = null,
    // Collection
    val from: SimpleDate = HotelOpsDates.monthStart(SimpleDate.today()),
    val to: SimpleDate = SimpleDate.today(),
    val method: SelectorOption = METHODS[0],
    val collection: HotelCollection? = null,
    val collectionLoading: Boolean = false,
    val collectionError: String? = null,
    // Performance
    val perfFrom: SimpleDate = HotelOpsDates.monthStart(SimpleDate.today()),
    val perfTo: SimpleDate = SimpleDate.today(),
    val performance: HotelPerformance? = null,
    val performanceLoading: Boolean = false,
    val performanceError: String? = null,
    /** The server's sentence when this branch lets no rooms — the tab shows it and nothing else. */
    val notLodging: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelReportsViewModel(
    private val repository: HotelOpsRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelReportsUiState(registerLoading = true))
    val uiState: StateFlow<HotelReportsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val branches = hotelOpsLoadBranches(reportRepository, ownBranchId)
            if (branches.unauthorized) {
                _uiState.update { it.copy(registerLoading = false, sessionExpired = true) }
                return@launch
            }
            _uiState.update { it.copy(branches = branches.branches, branch = branches.selected) }
            loadTab(force = true)
        }
    }

    /** Each tab reads when first shown and keeps its answer until an input changes. */
    private fun loadTab(force: Boolean) {
        val state = _uiState.value
        when (state.tab) {
            0 -> if (force || state.register == null) loadRegister()
            1 -> if (force || state.collection == null) loadCollection()
            else -> if (force || (state.performance == null && state.notLodging == null)) loadPerformance()
        }
    }

    fun onTab(index: Int) {
        _uiState.update { it.copy(tab = index) }
        loadTab(force = false)
    }

    fun onBranch(branch: BranchOption) {
        _uiState.update {
            it.copy(
                branch = branch,
                register = null, registerError = null,
                collection = null, collectionError = null,
                performance = null, performanceError = null, notLodging = null,
            )
        }
        loadTab(force = true)
    }

    // ---- Register ----

    fun onDate(date: SimpleDate) {
        _uiState.update { it.copy(date = date) }
        loadRegister()
    }

    fun onMode(mode: SelectorOption) {
        _uiState.update { it.copy(mode = mode) }
        loadRegister()
    }

    fun onQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadRegister()
        }
    }

    fun loadRegister() {
        val state = _uiState.value
        _uiState.update { it.copy(registerLoading = true, registerError = null) }
        viewModelScope.launch {
            val result = repository.fetchRegister(
                date = state.date.toApi(),
                mode = state.mode.id,
                branchId = state.branch?.id,
                query = state.query,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(registerLoading = false, register = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        registerLoading = false,
                        registerError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Collection ----

    fun onFrom(date: SimpleDate) {
        _uiState.update { it.copy(from = date) }
        loadCollection()
    }

    fun onTo(date: SimpleDate) {
        _uiState.update { it.copy(to = date) }
        loadCollection()
    }

    fun onMethod(method: SelectorOption) {
        _uiState.update { it.copy(method = method) }
        loadCollection()
    }

    fun loadCollection() {
        val state = _uiState.value
        _uiState.update { it.copy(collectionLoading = true, collectionError = null) }
        viewModelScope.launch {
            val result = repository.fetchCollection(
                from = state.from.toApi(),
                to = state.to.toApi(),
                branchId = state.branch?.id,
                method = state.method.id,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(collectionLoading = false, collection = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        collectionLoading = false,
                        collection = null,
                        collectionError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Performance ----

    fun onPerfFrom(date: SimpleDate) {
        _uiState.update { it.copy(perfFrom = date) }
        loadPerformance()
    }

    fun onPerfTo(date: SimpleDate) {
        _uiState.update { it.copy(perfTo = date) }
        loadPerformance()
    }

    /**
     * A branch that lets no rooms is refused with a sentence rather than
     * answered with noughts — nought occupancy is indistinguishable from a bad
     * month. That refusal hides the tab's content; every other error is shown
     * with a way to retry.
     */
    fun loadPerformance() {
        val state = _uiState.value
        _uiState.update { it.copy(performanceLoading = true, performanceError = null) }
        viewModelScope.launch {
            val result = repository.fetchPerformance(
                from = state.perfFrom.toApi(),
                to = state.perfTo.toApi(),
                branchId = state.branch?.id,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(performanceLoading = false, performance = result.data, notLodging = null)
                }
                is Resource.Error -> _uiState.update {
                    if (HotelOpsRepository.isNotLodgingRefusal(result.message)) {
                        it.copy(performanceLoading = false, performance = null, notLodging = result.message)
                    } else {
                        it.copy(
                            performanceLoading = false,
                            performance = null,
                            performanceError = result.message,
                            sessionExpired = it.sessionExpired || result.isUnauthorized,
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                HotelReportsViewModel(
                    repository = HotelOpsRepository.get(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

/**
 * Reading the property back — who was here, what came in, and how the month
 * did. Three reports on one screen because they are asked in the same breath
 * at the same moment of the morning, and three screens would be three places
 * to pick a branch and a date.
 *
 * The register says who SLEPT here, not who booked to: check-out deletes the
 * nights a guest did not sleep, and the server reads the nights. The
 * collection report is NETTED — a refund is stored positive and the direction
 * lives in the purpose, so every total here reads the server's signed figure.
 */
@Composable
fun HotelReportsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelReportsViewModel = viewModel(
        factory = HotelReportsViewModel.provideFactory(LocalContext.current),
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
        title = "Hotel Reports",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = state.tab) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.onTab(index) },
                        text = { Text(title) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HotelOpsBranchPicker(
                    branches = state.branches,
                    selected = state.branch,
                    onSelected = viewModel::onBranch,
                )
                when (state.tab) {
                    0 -> RegisterTab(
                        state = state,
                        onDate = { hotelOpsPickDate(context, state.date, viewModel::onDate) },
                        onMode = viewModel::onMode,
                        onQuery = viewModel::onQuery,
                        onRetry = viewModel::loadRegister,
                    )
                    1 -> CollectionTab(
                        state = state,
                        onFrom = { hotelOpsPickDate(context, state.from, viewModel::onFrom) },
                        onTo = { hotelOpsPickDate(context, state.to, viewModel::onTo) },
                        onMethod = viewModel::onMethod,
                        onRetry = viewModel::loadCollection,
                    )
                    else -> PerformanceTab(
                        state = state,
                        onFrom = { hotelOpsPickDate(context, state.perfFrom, viewModel::onPerfFrom) },
                        onTo = { hotelOpsPickDate(context, state.perfTo, viewModel::onPerfTo) },
                        onRetry = viewModel::loadPerformance,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Register
// ---------------------------------------------------------------------------

@Composable
private fun RegisterTab(
    state: HotelReportsUiState,
    onDate: () -> Unit,
    onMode: (SelectorOption) -> Unit,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PickerField(
            label = "Night",
            value = state.date.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
            onClick = onDate,
        )
        AppSelectDropdown(
            label = "Who",
            options = MODES,
            selected = state.mode,
            onSelected = onMode,
            modifier = Modifier.weight(1f),
        )
    }
    AppTextField(
        value = state.query,
        onValueChange = onQuery,
        label = "Name, mobile, NID, room or booking",
    )

    state.register?.counts?.let { counts ->
        val tiles = listOf(
            "In the building" to counts.inHouse,
            "Arriving" to counts.arrivals,
            "Leaving" to counts.departures,
        )
        HotelOpsGrid(items = tiles, columns = 3) { (label, value) ->
            HotelOpsFigure(label = label, value = value.toString(), modifier = Modifier.weight(1f))
        }
    }

    when {
        state.registerError != null -> HotelOpsProblem(text = state.registerError, onRetry = onRetry)
        state.registerLoading && state.register == null -> HotelOpsLoading()
        state.register != null -> ReportTable(
            columns = registerColumns(),
            data = state.register.rows,
            noDataMessage = "Nobody on this list for that night.",
            scrollable = false,
            modifier = Modifier.alpha(if (state.registerLoading) 0.6f else 1f),
        )
    }
}

@Composable
private fun registerColumns(): List<ReportColumn<HotelRegisterRow>> {
    val warning = MaterialTheme.appColors.warning
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { r, _ ->
            cellText(r.serialNo.toString())
        },
        ReportColumn("GUEST", ReportColWidth.Fixed(170.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = r.name.ifBlank { "—" } + if (r.isPrimary) " (main)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    // The booker stands in only where nobody was named, and
                    // is marked as such: a register that printed the person
                    // who telephoned as the person who slept there would be
                    // wrong about the one fact it exists to record.
                    if (!r.named) {
                        Text(
                            text = "nobody named yet — this is the booker",
                            style = MaterialTheme.typography.labelSmall,
                            color = warning,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        ReportColumn("ROOM", ReportColWidth.Fixed(110.dp)) { r, _ -> cellText(r.room.ifBlank { "—" }, maxLines = 2) },
        ReportColumn("MOBILE", ReportColWidth.Fixed(110.dp)) { r, _ -> cellText(r.mobile.ifBlank { "—" }) },
        ReportColumn("NID", ReportColWidth.Fixed(120.dp)) { r, _ -> cellText(r.nationalId.ifBlank { "—" }) },
        ReportColumn("STAY", ReportColWidth.Fixed(190.dp)) { r, _ ->
            cellText("${HotelOpsDates.display(r.checkInDate)} → ${HotelOpsDates.display(r.checkOutDate)}")
        },
        ReportColumn("BOOKING", ReportColWidth.Fixed(130.dp)) { r, _ -> cellText(r.bookingNo.ifBlank { "—" }) },
    )
}

// ---------------------------------------------------------------------------
//  Collection — "Money taken"
// ---------------------------------------------------------------------------

@Composable
private fun CollectionTab(
    state: HotelReportsUiState,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onMethod: (SelectorOption) -> Unit,
    onRetry: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = "From",
            value = state.from.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
            onClick = onFrom,
        )
        PickerField(
            label = "To",
            value = state.to.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
            onClick = onTo,
        )
    }
    AppSelectDropdown(
        label = "How",
        options = METHODS,
        selected = state.method,
        onSelected = onMethod,
    )

    when {
        state.collectionError != null -> HotelOpsProblem(text = state.collectionError, onRetry = onRetry)
        state.collectionLoading && state.collection == null -> HotelOpsLoading()
        state.collection != null -> CollectionBody(
            collection = state.collection,
            dimmed = state.collectionLoading,
        )
    }
}

@Composable
private fun CollectionBody(collection: HotelCollection, dimmed: Boolean) {
    val totals = collection.totals
    val danger = MaterialTheme.appColors.danger
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.alpha(if (dimmed) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotelOpsFigure("Receipts", totals.count.toString(), modifier = Modifier.weight(1f))
            HotelOpsFigure("Taken", hotelOpsMoney(totals.received), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotelOpsFigure(
                "Given back",
                hotelOpsMoney(totals.refunded),
                tone = if (totals.refunded > 0.0) danger else null,
                modifier = Modifier.weight(1f),
            )
            HotelOpsFigure(
                "In hand",
                hotelOpsMoney(totals.net),
                tone = primary,
                lead = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (totals.byMethod.isNotEmpty() || totals.byAccount.isNotEmpty()) {
            // By METHOD for the person counting the drawer, by ACCOUNT for
            // the person reconciling the cash book — different people asking
            // different questions of the same rows.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NamedAmounts(title = "By method", rows = totals.byMethod, modifier = Modifier.weight(1f))
                NamedAmounts(title = "By account", rows = totals.byAccount, modifier = Modifier.weight(1f))
            }
        }
        if (collection.unposted > 0) {
            HotelOpsNote(
                text = "${collection.unposted} ${if (collection.unposted == 1) "receipt is" else "receipts are"} not in the ledger.",
                color = MaterialTheme.appColors.warning,
            )
        }
        ReportTable(
            columns = collectionColumns(),
            data = collection.rows,
            noDataMessage = "No money taken in that range.",
            scrollable = false,
        )
    }
}

@Composable
private fun NamedAmounts(title: String, rows: List<HotelNamedAmount>, modifier: Modifier = Modifier) {
    HotelOpsSection(title = title, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (rows.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textMuted)
            }
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        text = hotelOpsMoney(row.amount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = AppFontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun purposeLabel(purpose: String): String = when (purpose) {
    "advance" -> "Advance"
    "settlement" -> "Settlement"
    "refund" -> "Refund"
    else -> purpose.ifBlank { "—" }
}

private fun methodLabel(method: String): String =
    method.replaceFirstChar { it.uppercase() }.ifBlank { "—" }

@Composable
private fun collectionColumns(): List<ReportColumn<HotelCollectionRow>> {
    val muted = MaterialTheme.appColors.textMuted
    val warning = MaterialTheme.appColors.warning
    val danger = MaterialTheme.appColors.danger
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { r, _ -> cellText(r.serialNo.toString()) },
        ReportColumn("RECEIPT", ReportColWidth.Fixed(130.dp)) { r, _ -> cellText(r.paymentNo.ifBlank { "—" }) },
        ReportColumn("DATE", ReportColWidth.Fixed(96.dp)) { r, _ -> cellText(HotelOpsDates.display(r.paymentDate)) },
        ReportColumn("BOOKING", ReportColWidth.Fixed(160.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(r.bookingNo.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    if (r.bookerName.isNotBlank()) {
                        Text(r.bookerName, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                    }
                }
            }
        },
        ReportColumn("FOR", ReportColWidth.Fixed(96.dp)) { r, _ -> cellText(purposeLabel(r.purpose)) },
        ReportColumn("HOW", ReportColWidth.Fixed(90.dp)) { r, _ -> cellText(methodLabel(r.method)) },
        ReportColumn("ACCOUNT", ReportColWidth.Fixed(140.dp)) { r, _ ->
            // Rows with no account are from before one was required — named
            // rather than dashed, so the breakdown still adds up to the net.
            if (r.account.isBlank()) cellText("not recorded", color = warning) else cellText(r.account, maxLines = 2)
        },
        ReportColumn("AMOUNT", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ ->
            if (r.signed < 0.0) {
                cellText("− ${hotelOpsMoney(abs(r.signed))}", color = danger)
            } else {
                cellText(hotelOpsMoney(r.signed))
            }
        },
        ReportColumn("VOUCHER", ReportColWidth.Fixed(110.dp)) { r, _ ->
            // Never a dash: a receipt with no voucher is a number somebody
            // has to account for, and a dash would let it pass as nothing.
            if (r.vrNo.isBlank()) cellText("not posted", color = warning) else cellText(r.vrNo)
        },
    )
}

// ---------------------------------------------------------------------------
//  Performance
// ---------------------------------------------------------------------------

@Composable
private fun PerformanceTab(
    state: HotelReportsUiState,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onRetry: () -> Unit,
) {
    // The tab exists only on a property that lets rooms: the refusal replaces
    // everything, filters included, so nobody re-picks dates hoping for numbers.
    state.notLodging?.let { sentence ->
        HotelOpsProblem(text = sentence)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = "From",
            value = state.perfFrom.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
            onClick = onFrom,
        )
        PickerField(
            label = "To",
            value = state.perfTo.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
            onClick = onTo,
        )
    }
    when {
        state.performanceError != null -> HotelOpsProblem(text = state.performanceError, onRetry = onRetry)
        state.performanceLoading && state.performance == null -> HotelOpsLoading()
        state.performance != null -> PerformanceBody(
            run = state.performance,
            dimmed = state.performanceLoading,
        )
    }
}

@Composable
private fun PerformanceBody(run: HotelPerformance, dimmed: Boolean) {
    val totals = run.totals
    Column(
        modifier = Modifier.alpha(if (dimmed) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            // The lead figure. Occupancy can be bought with discounts and ADR
            // can be had by selling three rooms at a high rate; RevPAR is the
            // only one of the three that both of those show up in.
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
        // The dormitory's own answer, kept small: on a property let by the bed
        // the room figure understates how full the building was.
        HotelOpsNote(
            "Beds: ${run.beds} · ${totals.bedNightsSold} of ${totals.bedNightsAvailable} bed-nights " +
                "(${hotelOpsPercent(totals.bedOccupancy, 2)})",
        )
        if (totals.heldRoomNights > 0) {
            HotelOpsNote(
                text = "${totals.heldRoomNights} room-nights are on hold and are not in any figure " +
                    "above — a hold is a telephone call that expires on its own.",
                color = MaterialTheme.appColors.warning,
            )
        }
        HotelOpsNote(
            "Rooms only — halls and community centres are let by the sitting, not the night, and " +
                "counting them would push occupancy past 100%. Confirmed, checked-in and checked-out " +
                "stays count; holds do not. Rent is the full tariff: a room let at 6,000 with 600 off " +
                "is a 6,000 room and a 600 discount, so a discount lowers the takings and never the " +
                "ADR. Measured against the ${run.rooms} rooms this property has today — a floor " +
                "opened last week makes last month read low.",
        )

        Text("By room type", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.Bold)
        ReportTable(
            columns = roomTypeColumns(),
            data = run.byRoomType,
            noDataMessage = "No rooms set up for this property.",
            scrollable = false,
        )

        Text("Night by night", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.Bold)
        ReportTable(
            columns = dailyColumns(),
            data = run.daily,
            noDataMessage = "No nights in that range.",
            scrollable = false,
        )
    }
}

/** Occupancy as a thin bar beside its number — the bar to scan by, the number to quote. */
@Composable
private fun OccupancyCell(occupancy: Double) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HotelOpsBar(fraction = (occupancy / 100.0).toFloat(), modifier = Modifier.weight(1f))
        Text(
            text = hotelOpsPercent(occupancy, 2),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun dailyColumns(): List<ReportColumn<HotelPerformanceDay>> = listOf(
    ReportColumn("NIGHT", ReportColWidth.Fixed(96.dp)) { r, _ -> cellText(HotelOpsDates.display(r.date)) },
    ReportColumn("ROOMS SOLD", ReportColWidth.Fixed(96.dp), TextAlign.Center) { r, _ ->
        cellText("${r.sold} / ${r.roomNightsAvailable}")
    },
    ReportColumn("OCCUPANCY", ReportColWidth.Fixed(150.dp)) { r, _ ->
        ReportTableCell.Slot { OccupancyCell(r.occupancy) }
    },
    ReportColumn("ROOM REVENUE", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ -> cellText(hotelOpsMoney(r.revenue)) },
    ReportColumn("ADR", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
        cellText(if (r.sold > 0) hotelOpsMoney(r.adr) else "—")
    },
    ReportColumn("RevPAR", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(hotelOpsMoney(r.revpar)) },
)

@Composable
private fun roomTypeColumns(): List<ReportColumn<HotelPerformanceRoomType>> = listOf(
    ReportColumn("ROOM TYPE", ReportColWidth.Fixed(140.dp)) { r, _ -> cellText(r.name.ifBlank { "—" }, maxLines = 2) },
    ReportColumn("ROOMS", ReportColWidth.Fixed(64.dp), TextAlign.Center) { r, _ -> cellText(r.rooms.toString()) },
    ReportColumn("ROOM-NIGHTS SOLD", ReportColWidth.Fixed(120.dp), TextAlign.Center) { r, _ ->
        cellText("${r.sold} / ${r.roomNightsAvailable}")
    },
    ReportColumn("OCCUPANCY", ReportColWidth.Fixed(150.dp)) { r, _ ->
        ReportTableCell.Slot { OccupancyCell(r.occupancy) }
    },
    ReportColumn("ROOM REVENUE", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ -> cellText(hotelOpsMoney(r.revenue)) },
    ReportColumn("ADR", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
        cellText(if (r.sold > 0) hotelOpsMoney(r.adr) else "—")
    },
    ReportColumn("RevPAR", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(hotelOpsMoney(r.revpar)) },
)
