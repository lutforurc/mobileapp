package com.example.cashbookbd.ui.analytics

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The web chart's two series colours.
private val PERIOD1_COLOR = Color(0xFF008FFB)
private val PERIOD2_COLOR = Color(0xFFFF4560)

/** One plotted series: name + per-day values (null = the shorter period's pad). */
data class CompareSeries(
    val name: String,
    val values: List<Double?>,
)

data class ComparisonUiState(
    val branches: List<SelectorOption> = emptyList(),
    val branch: SelectorOption? = null,
    val account: LedgerDropdownItem? = null,
    val p1Start: SimpleDate? = null,
    val p1End: SimpleDate? = null,
    val p2Start: SimpleDate? = null,
    val p2End: SimpleDate? = null,

    val isLoading: Boolean = false,
    val labels: List<String> = emptyList(),
    val series: List<CompareSeries> = emptyList(),
    val error: String? = null,

    val sessionExpired: Boolean = false,
)

class ComparisonViewModel(
    private val api: ReportApiService,
    private val ledgerRepository: LedgerRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState())
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = reportRepository.getBranches()
            if (result is Resource.Success) {
                // All four dates default to the branch's business date, as on
                // the web — a one-day window the clerk widens.
                val today = result.data.transactionDate ?: SimpleDate.today()
                _uiState.update { state ->
                    state.copy(
                        branches = result.data.branches.map {
                            SelectorOption(id = it.id.toString(), label = it.name)
                        },
                        branch = state.branch ?: result.data.branches.firstOrNull()
                            ?.let { SelectorOption(it.id.toString(), it.name) },
                        p1Start = state.p1Start ?: today,
                        p1End = state.p1End ?: today,
                        p2Start = state.p2Start ?: today,
                        p2End = state.p2End ?: today,
                    )
                }
                load()
            }
        }
    }

    fun onBranchSelected(option: SelectorOption) {
        _uiState.update { it.copy(branch = option) }
        load()
    }

    fun onAccountSelected(account: LedgerDropdownItem) {
        _uiState.update { it.copy(account = account) }
        load()
    }

    fun onP1Start(d: SimpleDate) { _uiState.update { it.copy(p1Start = d) }; load() }
    fun onP1End(d: SimpleDate) { _uiState.update { it.copy(p1End = d) }; load() }
    fun onP2Start(d: SimpleDate) { _uiState.update { it.copy(p2Start = d) }; load() }
    fun onP2End(d: SimpleDate) { _uiState.update { it.copy(p2End = d) }; load() }

    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    /** Refetches on every filter change, like the web (which has no Run button). */
    fun load() {
        val state = _uiState.value
        val branch = state.branch ?: return
        val p1s = state.p1Start ?: return
        val p1e = state.p1End ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val body = buildMap<String, Any> {
                        put("branch_id", branch.id.toLongOrNull() ?: 0L)
                        state.account?.id?.let { put("coal4_id", it) }
                        put("period1_start", p1s.toApi())
                        put("period1_end", p1e.toApi())
                        state.p2Start?.let { put("period2_start", it.toApi()) }
                        state.p2End?.let { put("period2_end", it.toApi()) }
                    }
                    val response = api.postAny("dashboard/branch/transaction-compare", body)
                    if (response.code() == 401) {
                        return@withContext Resource.Error(
                            "Your session has expired. Please log in again.", isUnauthorized = true,
                        )
                    }
                    val payload = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.getAsJsonObject("data")?.getAsJsonObject("data")
                        ?.getAsJsonObject("period1")
                        ?: return@withContext Resource.Error("Could not load the comparison.")
                    val labels = payload.get("labels")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString }
                        .orEmpty()
                    val series = payload.get("series")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            CompareSeries(
                                name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                                values = o.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                                    ?.map { v ->
                                        v.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
                                            ?.asString?.toDoubleOrNull()
                                    }
                                    .orEmpty(),
                            )
                        }
                        .orEmpty()
                    Resource.Success(labels to series)
                } catch (e: java.io.IOException) {
                    Resource.Error("No internet connection. Please check your network and try again.")
                } catch (e: Exception) {
                    Resource.Error("Something went wrong. Please try again.")
                }
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, labels = result.data.first, series = result.data.second)
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
                val appContext = context.applicationContext
                ComparisonViewModel(
                    api = ServiceLocator.provideReportApiService(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The web's Item Comparison: one account's daily received totals across two
 * date ranges, drawn as two lines so a season can be read against the last.
 */
@Composable
fun ComparisonScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComparisonViewModel = viewModel(
        factory = ComparisonViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Item Comparison",
        currentRoute = Routes.ANALYTICS_COMPARISON,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches,
                    selected = state.branch,
                    onSelected = viewModel::onBranchSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SearchableLedgerDropdown(
                    selectedLedger = state.account,
                    onLedgerSelected = viewModel::onAccountSelected,
                    searchLedgers = viewModel::searchAccounts,
                    label = "Select Account",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Start Date (P1)",
                        value = state.p1Start?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.p1Start, viewModel::onP1Start) },
                    )
                    PickerField(
                        label = "End Date (P1)",
                        value = state.p1End?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.p1End, viewModel::onP1End) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Start Date (P2)",
                        value = state.p2Start?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.p2Start, viewModel::onP2Start) },
                    )
                    PickerField(
                        label = "End Date (P2)",
                        value = state.p2End?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.p2End, viewModel::onP2End) },
                    )
                }

                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.onBackground)

                    state.series.all { s -> s.values.all { it == null } } -> Text(
                        text = "এই পরিসরে কোনো লেনদেন নেই।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )

                    else -> {
                        CompareChart(series = state.series)
                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.series.forEachIndexed { index, s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(
                                                if (index == 0) PERIOD1_COLOR else PERIOD2_COLOR,
                                                androidx.compose.foundation.shape.CircleShape,
                                            ),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        // The peak, so the lines can be read without an axis.
                        val peak = state.series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0.0
                        Text(
                            text = "Peak: ${AmountFormat.format(peak)} • " +
                                "${state.labels.firstOrNull().orEmpty()} — ${state.labels.lastOrNull().orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.muted(),
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Two smooth-ish polylines on one canvas; nulls break the line, as on the web. */
@Composable
private fun CompareChart(series: List<CompareSeries>) {
    val gridLine = MaterialTheme.appColors.gridLine
    val maxValue = series.flatMap { it.values }.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val pointCount = series.maxOfOrNull { it.values.size } ?: 0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        val w = size.width
        val h = size.height

        // Horizontal grid: quarters.
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (pointCount < 1) return@Canvas
        val stepX = if (pointCount > 1) w / (pointCount - 1) else w

        series.forEachIndexed { index, s ->
            val color = if (index == 0) PERIOD1_COLOR else PERIOD2_COLOR
            val path = Path()
            var penDown = false
            s.values.forEachIndexed { i, value ->
                if (value == null) {
                    penDown = false
                    return@forEachIndexed
                }
                val x = stepX * i
                val y = h - (value / maxValue * h).toFloat()
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(color, radius = 5f, center = Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 5f))
        }
    }
}

private fun pickDate(context: Context, initial: SimpleDate?, onPicked: (SimpleDate) -> Unit) {
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
