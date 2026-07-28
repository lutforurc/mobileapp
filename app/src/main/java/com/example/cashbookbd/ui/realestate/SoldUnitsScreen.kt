package com.example.cashbookbd.ui.realestate

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.cashbookbd.data.repository.RealEstateSalesRepository
import com.example.cashbookbd.data.repository.SoldUnitCustomer
import com.example.cashbookbd.data.repository.SoldUnitsReport
import com.example.cashbookbd.data.repository.SoldUnitsTotals
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Real Estate → Sold Units — the read-only report grouped per customer: summary
 * tiles up top, then each customer's units (their priced lines), the per-customer
 * Total/Received/Due footer, and a grand total from the backend `totals`.
 * `GET real-estate/unit-sale/sold-units`, filtered by project, building, a free
 * search, an optional date range and the "Only units having due" checkbox.
 */

private val ALL_PROJECTS = SelectorOption(id = "", label = "All Projects")
private val ALL_BUILDINGS = SelectorOption(id = "", label = "All Buildings")

data class SoldUnitsUiState(
    // Filter
    val projects: List<SelectorOption> = listOf(ALL_PROJECTS),
    val buildings: List<SelectorOption> = listOf(ALL_BUILDINGS),
    val isOptionsLoading: Boolean = false,
    val optionsError: String? = null,
    val selectedProject: SelectorOption = ALL_PROJECTS,
    val selectedBuilding: SelectorOption = ALL_BUILDINGS,
    val query: String = "",
    val dateFrom: SimpleDate? = null,
    val dateTo: SimpleDate? = null,
    val dueOnly: Boolean = false,

    // Result
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val hasApplied: Boolean = false,
    val report: SoldUnitsReport? = null,

    val sessionExpired: Boolean = false,
)

class SoldUnitsViewModel(
    private val repository: RealEstateSalesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoldUnitsUiState())
    val uiState: StateFlow<SoldUnitsUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isOptionsLoading = true, optionsError = null) }
        viewModelScope.launch {
            val projects = repository.fetchProjects()
            val buildings = repository.fetchBuildings()
            _uiState.update { state ->
                val error = (projects as? Resource.Error)?.message
                    ?: (buildings as? Resource.Error)?.message
                state.copy(
                    isOptionsLoading = false,
                    optionsError = error,
                    projects = listOf(ALL_PROJECTS) + ((projects as? Resource.Success)?.data ?: emptyList()),
                    buildings = listOf(ALL_BUILDINGS) + ((buildings as? Resource.Success)?.data ?: emptyList()),
                    sessionExpired = state.sessionExpired ||
                        (projects as? Resource.Error)?.isUnauthorized == true ||
                        (buildings as? Resource.Error)?.isUnauthorized == true,
                )
            }
        }
    }

    fun onProjectSelected(option: SelectorOption) = _uiState.update { it.copy(selectedProject = option) }

    fun onBuildingSelected(option: SelectorOption) = _uiState.update { it.copy(selectedBuilding = option) }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun onDateFromSelected(date: SimpleDate) = _uiState.update { it.copy(dateFrom = date) }

    fun onDateToSelected(date: SimpleDate) = _uiState.update { it.copy(dateTo = date) }

    fun onDueOnlyToggle(checked: Boolean) = _uiState.update { it.copy(dueOnly = checked) }

    fun search() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val result = repository.fetchSoldUnits(
                projectId = state.selectedProject.id,
                buildingId = state.selectedBuilding.id,
                dateFrom = state.dateFrom?.toApi(),
                dateTo = state.dateTo?.toApi(),
                query = state.query,
                dueOnly = state.dueOnly,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun reset() = _uiState.update {
        it.copy(
            selectedProject = ALL_PROJECTS,
            selectedBuilding = ALL_BUILDINGS,
            query = "",
            dateFrom = null,
            dateTo = null,
            dueOnly = false,
            hasApplied = false,
            report = null,
            loadError = null,
        )
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SoldUnitsViewModel(
                    repository = ServiceLocator.provideRealEstateSalesRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun SoldUnitsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoldUnitsViewModel = viewModel(
        factory = SoldUnitsViewModel.provideFactory(LocalContext.current)
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
        title = "Sold Units",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        val onScreen = MaterialTheme.colorScheme.onBackground
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Filters ----------------------------------------------------
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
                Text(it, color = onScreen, style = MaterialTheme.typography.bodySmall)
            }

            AppTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Search customer / unit…",
                caption = "Search",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "From Date",
                    value = state.dateFrom?.toDisplay() ?: "",
                    placeholder = "Any",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showSoldUnitsDatePicker(
                            context,
                            state.dateFrom ?: SimpleDate.today(),
                            viewModel::onDateFromSelected,
                        )
                    },
                )
                PickerField(
                    label = "To Date",
                    value = state.dateTo?.toDisplay() ?: "",
                    placeholder = "Any",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showSoldUnitsDatePicker(
                            context,
                            state.dateTo ?: SimpleDate.today(),
                            viewModel::onDateToSelected,
                        )
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.dueOnly, onCheckedChange = viewModel::onDueOnlyToggle)
                Text(
                    text = "Only units having due",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onScreen,
                )
            }

            FilterActions(
                onApply = viewModel::search,
                onReset = viewModel::reset,
                canApply = !state.isLoading,
                isLoading = state.isLoading,
                applyText = "Search",
            )

            // ---- Results ----------------------------------------------------
            when {
                state.isLoading -> CenteredBox {
                    CircularProgressIndicator(color = onScreen)
                }

                state.loadError != null -> CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.loadError!!, color = onScreen, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::search)
                    }
                }

                !state.hasApplied || state.report == null -> CenteredBox {
                    Text(
                        text = "Choose your filters, then tap Search.",
                        color = onScreen.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }

                state.report!!.customers.isEmpty() -> CenteredBox {
                    Text(
                        text = "No sold units found for this selection.",
                        color = onScreen.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    val report = state.report!!
                    SoldUnitsSummaryTiles(report.totals)
                    report.customers.forEach { customer ->
                        SoldUnitCustomerCard(customer)
                    }
                    SoldUnitsGrandTotal(report.totals)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Summary tiles
// ---------------------------------------------------------------------------

/** Customers / Sold Units / Sold Parking / Sale Value / Received / Due. */
@Composable
private fun SoldUnitsSummaryTiles(totals: SoldUnitsTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure("Customers", countOrDash(totals.customerCount), Modifier.weight(1f))
            SummaryFigure("Sold Units", countOrDash(totals.unitCount), Modifier.weight(1f))
            SummaryFigure("Sold Parking", countOrDash(totals.parkingCount), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure("Sale Value", AmountFormat.formatOrDash(totals.totalAmount), Modifier.weight(1f))
            SummaryFigure("Received", AmountFormat.formatOrDash(totals.receivedAmount), Modifier.weight(1f))
            SummaryFigure("Due", AmountFormat.formatOrDash(totals.dueAmount), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, modifier: Modifier = Modifier) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Customer cards
// ---------------------------------------------------------------------------

/**
 * One customer's block: the stacked header (name / address / mobile), a compact
 * row per priced line, and the Total / Received / Due footer. Drawn on the
 * screen backdrop with the same faded grid lines the report tables use.
 */
@Composable
private fun SoldUnitCustomerCard(customer: SoldUnitCustomer) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val gridLine = onScreen.copy(alpha = 0.3f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = customer.customerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onScreen,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (customer.customerAddress.isNotBlank()) {
                Text(
                    text = customer.customerAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (customer.customerMobile.isNotBlank()) {
                Text(
                    text = "Cell: ${customer.customerMobile}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = gridLine)

        // One row per priced line of every unit sale.
        customer.units.forEach { unit ->
            unit.lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = line.caption.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            color = onScreen,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (line.place.isNotBlank()) {
                            Text(
                                text = line.place,
                                style = MaterialTheme.typography.labelSmall,
                                color = onScreen.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = AmountFormat.formatOrDash(line.amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        textAlign = TextAlign.End,
                    )
                }
                HorizontalDivider(color = gridLine)
            }
        }

        // Footer: Total (sale date + receipt no) / Received / Due.
        val saleInfo = customer.units
            .map { unit ->
                listOf(displayDate(unit.saleDate), unit.receiptNo)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
            }
            .filter { it.isNotBlank() }
            .joinToString(", ")
        CustomerFooterRow("Total", saleInfo, customer.totalAmount, onScreen)
        HorizontalDivider(color = gridLine)
        CustomerFooterRow("Received", null, customer.receivedAmount, onScreen)
        HorizontalDivider(color = gridLine)
        CustomerFooterRow("Due", null, customer.dueAmount, onScreen)
        HorizontalDivider(color = gridLine, thickness = 2.dp)
    }
}

@Composable
private fun CustomerFooterRow(
    label: String,
    sub: String?,
    amount: Double,
    onScreen: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = onScreen,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = AmountFormat.formatOrDash(amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = onScreen,
            textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------------------
// Grand total
// ---------------------------------------------------------------------------

@Composable
private fun SoldUnitsGrandTotal(totals: SoldUnitsTotals) {
    SummaryTile(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Grand Total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        GrandTotalLine("Sale Value", totals.totalAmount)
        GrandTotalLine("Received", totals.receivedAmount)
        GrandTotalLine("Due", totals.dueAmount)
    }
}

@Composable
private fun GrandTotalLine(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = AmountFormat.formatOrDash(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Zero counts read as "-", matching the amount cells. */
private fun countOrDash(value: Int): String = if (value == 0) "-" else value.toString()

/** The backend's yyyy-MM-dd (or already-display) sale date as dd/MM/yyyy. */
private fun displayDate(value: String): String =
    SimpleDate.fromApi(value)?.toDisplay()
        ?: SimpleDate.fromDisplay(value)?.toDisplay()
        ?: value

private fun showSoldUnitsDatePicker(
    context: Context,
    initial: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
