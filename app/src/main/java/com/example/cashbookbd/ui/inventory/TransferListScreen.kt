package com.example.cashbookbd.ui.inventory

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ComparisonRow
import com.example.cashbookbd.data.repository.InventoryMovementRepository
import com.example.cashbookbd.data.repository.TransferChallan
import com.example.cashbookbd.data.repository.TransferComparison
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// transfer_status meanings, as the web's TRANSFER_STATUS constant reads them.
private const val STATUS_IN_TRANSIT = 1
private const val STATUS_RECEIVED = 3

data class TransferListUiState(
    /** 0 = Issued challans, 1 = Received challans. */
    val tab: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val challans: List<TransferChallan> = emptyList(),

    // One challan compared at a time — two open at once and it stops being
    // clear which set of figures belongs to which voucher.
    val comparedId: Long? = null,
    val comparingId: Long? = null,
    val comparison: TransferComparison? = null,

    val sessionExpired: Boolean = false,
)

class TransferListViewModel(
    private val repository: InventoryMovementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferListUiState())
    val uiState: StateFlow<TransferListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onTabSelected(tab: Int) {
        if (tab == _uiState.value.tab) return
        _uiState.update {
            it.copy(tab = tab, challans = emptyList(), comparedId = null, comparison = null)
        }
        load()
    }

    fun load() {
        val received = _uiState.value.tab == 1
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchChallans(received)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, challans = result.data)
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

    /** Opens what was sent against what arrived, for one challan. */
    fun toggleComparison(challan: TransferChallan) {
        val state = _uiState.value
        if (state.comparedId == challan.id) {
            _uiState.update { it.copy(comparedId = null, comparison = null) }
            return
        }
        if (state.comparingId != null) return
        _uiState.update { it.copy(comparingId = challan.id) }
        viewModelScope.launch {
            when (val result = repository.fetchComparison(challan.id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(comparingId = null, comparedId = challan.id, comparison = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        comparingId = null,
                        comparedId = null,
                        comparison = null,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                TransferListViewModel(
                    repository = ServiceLocator.provideInventoryMovementRepository(
                        context.applicationContext,
                    ),
                )
            }
        }
    }
}

/**
 * The branch transfer register: issued challans on one tab, received ones on
 * the other, each row opening its issued-vs-received figures product by
 * product. The difference is the column worth reading, so it is the one that
 * carries colour: red where less landed than left, amber where more did, and
 * plain ink where the two agree — which is every line nobody needs to look
 * into.
 */
@Composable
fun TransferListScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferListViewModel = viewModel(
        factory = TransferListViewModel.provideFactory(LocalContext.current)
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
        title = "Branch Transfer List",
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = state.tab) {
                Tab(
                    selected = state.tab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = { Text("Issued") },
                )
                Tab(
                    selected = state.tab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = { Text("Received") },
                )
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // A request that failed is not the same as a period with no
                    // challans, and the list must not read as the second when
                    // it was the first.
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }

                state.challans.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.tab == 0) "No challan issued yet." else "No challan received yet.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.challans, key = { it.id }) { challan ->
                        ChallanCard(
                            challan = challan,
                            isOpen = state.comparedId == challan.id,
                            isComparing = state.comparingId == challan.id,
                            comparison = state.comparison.takeIf { state.comparedId == challan.id },
                            onToggle = { viewModel.toggleComparison(challan) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallanCard(
    challan: TransferChallan,
    isOpen: Boolean,
    isComparing: Boolean,
    comparison: TransferComparison?,
    onToggle: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challan.vrNo.ifBlank { challan.challanNumber.ifBlank { "#${challan.id}" } },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = onScreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val info = listOf(challan.date, challan.challanNumber)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" | ")
                    if (info.isNotBlank()) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.labelSmall,
                            color = onScreen.muted(),
                        )
                    }
                }
                StatusPill(challan.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${challan.fromBranch.ifBlank { "-" }} → ${challan.toBranch.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen,
            )
            LinkButton(
                text = when {
                    isComparing -> "Loading…"
                    isOpen -> "Hide issued vs received"
                    else -> "Issued vs received"
                },
                onClick = onToggle,
                enabled = !isComparing,
            )
            if (isOpen && comparison != null) {
                ComparisonTable(comparison)
            }
        }
    }
}

/** In Transit amber, Received green — the web list's status pills. */
@Composable
private fun StatusPill(status: Int) {
    val (label, color) = when (status) {
        STATUS_IN_TRANSIT -> "In Transit" to MaterialTheme.accents.amber
        STATUS_RECEIVED -> "Received" to MaterialTheme.appColors.success
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.SemiBold,
        color = color,
    )
}

/**
 * The figures product by product. The difference carries the colour: red where
 * less landed than left, amber where more did, plain ink where the two agree.
 */
@Composable
private fun ComparisonTable(comparison: TransferComparison) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val gridLine = MaterialTheme.appColors.gridLine

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!comparison.isReceived) {
            Text(
                text = "Nothing received against this challan yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell("Product", 2f)
            HeaderCell("Issued", 1f, TextAlign.End)
            HeaderCell("Rcv", 1f, TextAlign.End)
            HeaderCell("Dmg", 1f, TextAlign.End)
            HeaderCell("Short", 1f, TextAlign.End)
            HeaderCell("Diff", 1f, TextAlign.End)
        }
        HorizontalDivider(color = gridLine)
        if (comparison.rows.isEmpty()) {
            Text(
                text = "No product on this challan",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.muted(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            comparison.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = gridLine)
                ComparisonRowLine(row, nameWeight = 2f)
            }
            comparison.totals?.let { totals ->
                HorizontalDivider(color = gridLine)
                ComparisonRowLine(totals.copy(productName = "Total"), nameWeight = 2f, bold = true)
            }
        }
    }
}

@Composable
private fun ComparisonRowLine(row: ComparisonRow, nameWeight: Float, bold: Boolean = false) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val weight = if (bold) AppFontWeight.SemiBold else null

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Cell(row.productName, nameWeight, TextAlign.Start, onScreen, weight)
        Cell(qty(row.issuedQty), 1f, TextAlign.End, onScreen, weight)
        Cell(qty(row.receivedQty), 1f, TextAlign.End, onScreen, weight)
        Cell(qty(row.damagedQty), 1f, TextAlign.End, onScreen, weight)
        Cell(qty(row.shortQty), 1f, TextAlign.End, onScreen, weight)
        val diffColor = when {
            row.difference < 0 -> MaterialTheme.colorScheme.error
            row.difference > 0 -> MaterialTheme.accents.amber
            else -> onScreen
        }
        Cell(
            text = if (row.difference > 0) "+${qty(row.difference)}" else qty(row.difference),
            weight = 1f,
            align = TextAlign.End,
            color = diffColor,
            fontWeight = if (row.difference != 0.0) AppFontWeight.SemiBold else weight,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    align: TextAlign,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = fontWeight,
        color = color,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
    )
}

/** Whole quantities without a trailing .0; zero as "0", as the web shows. */
private fun qty(value: Double): String = AmountFormat.format(value, 0)
