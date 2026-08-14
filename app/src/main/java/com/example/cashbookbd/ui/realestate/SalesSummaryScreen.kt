package com.example.cashbookbd.ui.realestate

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CustomerPaymentRow
import com.example.cashbookbd.data.repository.RealEstateSalesRepository
import com.example.cashbookbd.data.repository.SalesSummaryCustomer
import com.example.cashbookbd.data.repository.SalesSummaryReport
import com.example.cashbookbd.data.repository.SalesSummaryTotals
import com.example.cashbookbd.data.repository.SalesSummaryUnit
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.TutorialScreens
import com.example.cashbookbd.ui.components.TutorialVideoLink
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

private val ALL_LOCATIONS = SelectorOption(id = "", label = "All Locations")
private val ALL_PROJECTS_OPTION = SelectorOption(id = "", label = "All Projects")
private val ALL_BUILDINGS_OPTION = SelectorOption(id = "", label = "All Buildings")

/** The buyer whose payment ledger the dialog is showing, and its rows. */
data class PaymentLedgerState(
    val customer: SalesSummaryCustomer,
    val isLoading: Boolean = true,
    val rows: List<CustomerPaymentRow> = emptyList(),
    val error: String? = null,
)

data class SalesSummaryUiState(
    val areas: List<SelectorOption> = listOf(ALL_LOCATIONS),
    val projects: List<SelectorOption> = listOf(ALL_PROJECTS_OPTION),
    val buildings: List<SelectorOption> = listOf(ALL_BUILDINGS_OPTION),
    val selectedArea: SelectorOption = ALL_LOCATIONS,
    val selectedProject: SelectorOption = ALL_PROJECTS_OPTION,
    val selectedBuilding: SelectorOption = ALL_BUILDINGS_OPTION,
    val isOptionsLoading: Boolean = false,
    val optionsError: String? = null,

    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val report: SalesSummaryReport? = null,
    val loadError: String? = null,

    val paymentLedger: PaymentLedgerState? = null,

    val sessionExpired: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * The Sales Summary report: one line per buyer, and what they still owe.
 *
 * Reads its own endpoint rather than the sold-units list's, because it answers
 * to its own permission — `real.estate.sales.summary`, which is not what opens
 * the sales screen. Like the web it loads once on entry with every filter open,
 * then again on Apply.
 */
class SalesSummaryViewModel(
    private val repository: RealEstateSalesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesSummaryUiState())
    val uiState: StateFlow<SalesSummaryUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
        load()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isOptionsLoading = true, optionsError = null) }
        viewModelScope.launch {
            val areas = repository.fetchAreas()
            val projects = repository.fetchProjects()
            val buildings = repository.fetchBuildings()
            _uiState.update { state ->
                val error = (areas as? Resource.Error)?.message
                    ?: (projects as? Resource.Error)?.message
                    ?: (buildings as? Resource.Error)?.message
                state.copy(
                    isOptionsLoading = false,
                    optionsError = error,
                    areas = listOf(ALL_LOCATIONS) + ((areas as? Resource.Success)?.data ?: emptyList()),
                    projects = listOf(ALL_PROJECTS_OPTION) + ((projects as? Resource.Success)?.data ?: emptyList()),
                    buildings = listOf(ALL_BUILDINGS_OPTION) + ((buildings as? Resource.Success)?.data ?: emptyList()),
                    sessionExpired = state.sessionExpired ||
                        (areas as? Resource.Error)?.isUnauthorized == true ||
                        (projects as? Resource.Error)?.isUnauthorized == true ||
                        (buildings as? Resource.Error)?.isUnauthorized == true,
                )
            }
        }
    }

    fun onAreaSelected(option: SelectorOption) = _uiState.update { it.copy(selectedArea = option) }
    fun onProjectSelected(option: SelectorOption) = _uiState.update { it.copy(selectedProject = option) }
    fun onBuildingSelected(option: SelectorOption) = _uiState.update { it.copy(selectedBuilding = option) }

    fun load() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val result = repository.fetchSalesSummary(
                areaId = state.selectedArea.id,
                projectId = state.selectedProject.id,
                buildingId = state.selectedBuilding.id,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasLoaded = true, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    // A refused or failed read must not leave the previous
                    // filter's figures on screen under new headings.
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        report = null,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                selectedArea = ALL_LOCATIONS,
                selectedProject = ALL_PROJECTS_OPTION,
                selectedBuilding = ALL_BUILDINGS_OPTION,
            )
        }
        load()
    }

    /**
     * The buyer's money, receipt by receipt — asked by the account id, not the
     * name, so a buyer with two flats shows every receipt and a namesake never
     * leaks in. The web navigates to the payments register; on the phone the
     * same rows open in place.
     */
    fun openPaymentLedger(customer: SalesSummaryCustomer) {
        _uiState.update { it.copy(paymentLedger = PaymentLedgerState(customer = customer)) }
        viewModelScope.launch {
            when (val result = repository.fetchCustomerPayments(customer.customerId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.paymentLedger?.takeIf { it.customer.customerId == customer.customerId }
                        ?.let { ledger ->
                            state.copy(paymentLedger = ledger.copy(isLoading = false, rows = result.data))
                        } ?: state
                }
                is Resource.Error -> _uiState.update { state ->
                    state.paymentLedger?.takeIf { it.customer.customerId == customer.customerId }
                        ?.let { ledger ->
                            state.copy(
                                paymentLedger = ledger.copy(isLoading = false, error = result.message),
                                sessionExpired = state.sessionExpired || result.isUnauthorized,
                            )
                        } ?: state
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closePaymentLedger() = _uiState.update { it.copy(paymentLedger = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SalesSummaryViewModel(
                    repository = ServiceLocator.provideRealEstateSalesRepository(appContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun SalesSummaryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalesSummaryViewModel = viewModel(
        factory = SalesSummaryViewModel.provideFactory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.loadError) {
        val message = state.loadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    AuthenticatedShell(
        title = "Sales Summary",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = { TutorialVideoLink(screenKey = TutorialScreens.SALES_SUMMARY) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---- Filters ----
                AppSelectDropdown(
                    label = "Location",
                    options = state.areas,
                    selected = state.selectedArea,
                    onSelected = viewModel::onAreaSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.isOptionsLoading) "Loading locations…" else "All Locations",
                )
                AppSelectDropdown(
                    label = "Project",
                    options = state.projects,
                    selected = state.selectedProject,
                    onSelected = viewModel::onProjectSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.isOptionsLoading) "Loading projects…" else "All Projects",
                )
                AppSelectDropdown(
                    label = "Building",
                    options = state.buildings,
                    selected = state.selectedBuilding,
                    onSelected = viewModel::onBuildingSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.isOptionsLoading) "Loading buildings…" else "All Buildings",
                )
                state.optionsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
                FilterActions(
                    onApply = viewModel::load,
                    onReset = viewModel::reset,
                    canApply = !state.isLoading,
                    isLoading = state.isLoading,
                )

                Spacer(Modifier.height(2.dp))

                // ---- Result ----
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    !state.hasLoaded -> SalesSummaryEmptyState("Loading…")

                    state.report == null || state.report?.customers.isNullOrEmpty() ->
                        SalesSummaryEmptyState(state.loadError ?: "No data found")

                    else -> SalesSummaryResult(
                        report = state.report!!,
                        onOpenLedger = viewModel::openPaymentLedger,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.paymentLedger?.let { ledger ->
        PaymentLedgerDialog(ledger = ledger, onDismiss = viewModel::closePaymentLedger)
    }
}

@Composable
private fun SalesSummaryEmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.appColors.textOnScreenMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// The result table
// ---------------------------------------------------------------------------

/** "Sherpur, Bogura › Baganbari › Shantipark Tower" */
private fun placeOf(unit: SalesSummaryUnit): String =
    listOf(unit.areaName, unit.projectName, unit.buildingName)
        .filter { it.isNotBlank() }
        .joinToString(" › ")

/**
 * "4th Floor · Unit# 4/A" — the unit and parking numbers print as stored;
 * they already read "Unit# 4/A", and labelling them again gave "Unit# Unit# 4/A".
 */
private fun unitOf(unit: SalesSummaryUnit): String =
    listOf(unit.floorName, unit.unitNo, unit.parkingNo)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

@Composable
private fun SalesSummaryResult(
    report: SalesSummaryReport,
    onOpenLedger: (SalesSummaryCustomer) -> Unit,
) {
    ReportTable(
        columns = salesSummaryColumns(onOpenLedger),
        data = report.customers,
        footerRows = listOf(salesSummaryFooter(report.totals)),
        scrollable = false,
    )
}

@Composable
private fun salesSummaryColumns(
    onOpenLedger: (SalesSummaryCustomer) -> Unit,
): List<ReportColumn<SalesSummaryCustomer>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val danger = MaterialTheme.colorScheme.error
    return listOf(
        // The buyer, and under them what they bought — all in the one cell. A
        // buyer with two flats gets two pairs rather than a second row, so the
        // amount columns beside them stay one figure per customer.
        ReportColumn("Customer", ReportColWidth.Weight(1.3f)) { row, _ ->
            ReportTableCell.Slot {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.customerName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = AppFontWeight.SemiBold,
                        color = onScreen,
                        maxLines = 2,
                    )
                    if (row.customerMobile.isNotBlank()) {
                        Text(
                            text = row.customerMobile,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                    row.units.forEach { unit ->
                        val place = placeOf(unit)
                        val which = unitOf(unit)
                        if (place.isNotBlank() || which.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                        }
                        if (place.isNotBlank()) {
                            Text(place, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 2)
                        }
                        if (which.isNotBlank()) {
                            Text(which, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        }
                    }
                }
            }
        },
        ReportColumn("Sales", ReportColWidth.Fixed(82.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.totalAmount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Rcv", ReportColWidth.Fixed(82.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.receivedAmount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Due", ReportColWidth.Fixed(82.dp), TextAlign.End) { row, _ ->
            cellText(
                AmountFormat.formatOrDash(row.dueAmount),
                align = TextAlign.End,
                bold = true,
                // A number should not be told apart by colour alone, but overdue
                // keeps its red while there is something to warn about (web rule).
                color = if (row.dueAmount > 0) danger else onScreen,
            )
        },
        ReportColumn("", ReportColWidth.Fixed(40.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                IconButton(onClick = { onOpenLedger(row) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Payment ledger of ${row.customerName}",
                        tint = MaterialTheme.appColors.success,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

/**
 * Added up from the rows on screen rather than read off the payload, so the
 * foot of the report can never disagree with the column above it — the web
 * does the same, and the server's `totals` block agrees with both.
 */
@Composable
private fun salesSummaryFooter(totals: SalesSummaryTotals): List<ReportFooterCell> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportFooterCell(cellText("Grand Total", bold = true, color = onScreen)),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(totals.totalAmount), align = TextAlign.End, bold = true, color = onScreen),
        ),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(totals.receivedAmount), align = TextAlign.End, bold = true, color = onScreen),
        ),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(totals.dueAmount), align = TextAlign.End, bold = true, color = onScreen),
        ),
        ReportFooterCell(ReportTableCell.Empty),
    )
}

// ---------------------------------------------------------------------------
// Payment ledger dialog
// ---------------------------------------------------------------------------

@Composable
private fun PaymentLedgerDialog(
    ledger: PaymentLedgerState,
    onDismiss: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Column {
                Text("Payment Ledger", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = ledger.customer.customerName +
                        ledger.customer.customerMobile.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        },
        text = {
            when {
                ledger.isLoading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                ledger.error != null -> Text(
                    ledger.error,
                    color = onScreen,
                    style = MaterialTheme.typography.bodyMedium,
                )

                ledger.rows.isEmpty() -> Text(
                    "No payments recorded against this buyer yet.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(ledger.rows) { row ->
                        PaymentLedgerRow(row)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    }
                    item {
                        // CONFIRMED only, a CONFIRMED REFUND taken off — the
                        // module's one money rule, so this line agrees with the
                        // report's Rcv figure behind it.
                        val received = ledger.rows
                            .filter { it.status.equals("CONFIRMED", ignoreCase = true) }
                            .sumOf { if (it.paymentType.equals("REFUND", ignoreCase = true)) -it.amount else it.amount }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Confirmed received",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = AppFontWeight.SemiBold,
                                color = onScreen,
                            )
                            Text(
                                AmountFormat.format(received),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = AppFontWeight.SemiBold,
                                color = onScreen,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PaymentLedgerRow(row: CustomerPaymentRow) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = listOf(row.paymentDate, row.receiptNo).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = onScreen,
                maxLines = 1,
            )
            Text(
                text = listOf(row.paymentMode, row.paymentType.takeIf { !it.equals("PAYMENT", true) }.orEmpty(), row.status)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.status.equals("CONFIRMED", ignoreCase = true)) muted else MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
        Text(
            text = AmountFormat.format(if (row.paymentType.equals("REFUND", true)) -row.amount else row.amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = onScreen,
        )
    }
}
