package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.cashbookbd.data.repository.BuildingLayout
import com.example.cashbookbd.data.repository.BuildingUnit
import com.example.cashbookbd.data.repository.LayoutFloor
import com.example.cashbookbd.data.repository.UnitSaleRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Building Layout — the phone-sized port of the web's FlatLayout viewer. Pick a
 * building, get its floors top-down with a chip per unit coloured by status,
 * and tap a chip for the unit's read-only details (status, size, price, buyer).
 */

/** 1 Available, 2 Under Dev, 3 Completed, 4 Sold — the web's STATUS_LABELS. */
private fun statusLabel(status: Int): String = when (status) {
    1 -> "Available"
    2 -> "Under Dev"
    3 -> "Completed"
    4 -> "Sold"
    else -> "Unknown"
}

/** Client-side tallies for the summary tiles, like the web's layoutSummary. */
data class LayoutSummary(
    val floors: Int = 0,
    val units: Int = 0,
    val available: Int = 0,
    val sold: Int = 0,
    val underDev: Int = 0,
    val completed: Int = 0,
)

data class FlatLayoutUiState(
    val building: SelectorOption? = null,
    val layout: BuildingLayout? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedUnit: BuildingUnit? = null,
    val sessionExpired: Boolean = false,
) {
    /** Floors top-down: highest floor first, ground (1) last — like a building. */
    val sortedFloors: List<LayoutFloor>
        get() = layout?.floors.orEmpty().sortedByDescending { it.floorNo }

    val summary: LayoutSummary
        get() {
            val floors = layout?.floors.orEmpty()
            val units = floors.flatMap { f -> f.flats.flatMap { it.units } }
            return LayoutSummary(
                floors = floors.size,
                units = units.size,
                available = units.count { it.status == 1 },
                sold = units.count { it.status == 4 },
                underDev = units.count { it.status == 2 },
                completed = units.count { it.status == 3 },
            )
        }
}

class FlatLayoutViewModel(
    private val repository: UnitSaleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlatLayoutUiState())
    val uiState: StateFlow<FlatLayoutUiState> = _uiState.asStateFlow()

    suspend fun searchBuildings(query: String): Resource<List<SelectorOption>> =
        when (val result = repository.searchBuildings(query)) {
            is Resource.Success -> Resource.Success(
                result.data.map {
                    SelectorOption(
                        id = it.id.toString(),
                        label = it.label,
                        // buildings/ddl: label_2 = area, label_3 = branch.
                        sublabel = listOfNotNull(it.flatName, it.buildingName)
                            .filter { s -> s.isNotBlank() }
                            .joinToString(" • ")
                            .ifBlank { null },
                    )
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onBuildingSelected(option: SelectorOption) {
        val id = option.id.toIntOrNull() ?: return
        if (_uiState.value.building?.id == option.id) return
        _uiState.update {
            it.copy(building = option, layout = null, error = null, selectedUnit = null, isLoading = true)
        }
        viewModelScope.launch {
            when (val result = repository.getBuildingLayout(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(layout = result.data, isLoading = false, error = null)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        layout = null,
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onUnitClicked(unit: BuildingUnit) = _uiState.update { it.copy(selectedUnit = unit) }
    fun onUnitDialogDismissed() = _uiState.update { it.copy(selectedUnit = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                FlatLayoutViewModel(
                    repository = ServiceLocator.provideUnitSaleRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun FlatLayoutScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlatLayoutViewModel = viewModel(
        factory = FlatLayoutViewModel.provideFactory(LocalContext.current)
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
        title = state.layout?.building ?: "Building Layout",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchableSelectDropdown(
                selected = state.building,
                onSelected = viewModel::onBuildingSelected,
                search = viewModel::searchBuildings,
                label = "Select Building",
                placeholder = "Type 2+ chars to search…",
                emptyText = "No buildings found",
                minSearchChars = UnitSaleRepository.DDL_MIN_CHARS,
            )

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.error != null -> Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )

                state.layout == null -> Text(
                    text = "Please select a building to view floors & units.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )

                else -> {
                    SummaryTiles(state.summary)
                    state.sortedFloors.forEach { floor -> FloorCard(floor, viewModel::onUnitClicked) }
                }
            }
        }
    }

    state.selectedUnit?.let { unit ->
        UnitDetailsDialog(unit = unit, onDismiss = viewModel::onUnitDialogDismissed)
    }
}

/** Floors / Units / Available / Sold / Under Dev / Completed, two rows of three. */
@Composable
private fun SummaryTiles(summary: LayoutSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile("Floors", summary.floors, Modifier.weight(1f))
            CountTile("Units", summary.units, Modifier.weight(1f))
            CountTile("Available", summary.available, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile("Sold", summary.sold, Modifier.weight(1f))
            CountTile("Under Dev", summary.underDev, Modifier.weight(1f))
            CountTile("Completed", summary.completed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CountTile(label: String, count: Int, modifier: Modifier = Modifier) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One floor: named after its single flat when it has one, "Ground" for
 * floor_no 1, otherwise "Floor {n}" — with a chip per unit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorCard(floor: LayoutFloor, onUnitClick: (BuildingUnit) -> Unit) {
    val singleFlatName = floor.flats.singleOrNull()?.flatName?.takeIf { it.isNotBlank() }
    val title = singleFlatName
        ?: if (floor.floorNo == 1) "Ground" else "Floor ${floor.floorNo}"
    val units = floor.flats.flatMap { it.units }
    val unitCount = units.count { !it.isParking }
    val parkingCount = units.count { it.isParking }
    val countsText = listOfNotNull(
        unitCount.takeIf { it > 0 }?.let { "$it Unit" + if (it > 1) "s" else "" },
        parkingCount.takeIf { it > 0 }?.let { "$it Parking" },
    ).joinToString(", ").ifBlank { "No units" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = countsText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            floor.flats.forEachIndexed { index, flat ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                // A flat header only helps once there is more than one flat.
                if (floor.flats.size > 1 && flat.flatName.isNotBlank()) {
                    Text(
                        text = flat.flatName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (flat.units.isEmpty()) {
                    Text(
                        text = "No units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        flat.units.forEach { unit -> UnitChip(unit, onUnitClick) }
                    }
                }
            }
        }
    }
}

/**
 * A unit chip coloured by status — all colours theme-derived: Available →
 * primaryContainer, Under Dev → secondaryContainer, Completed → surfaceVariant,
 * Sold → tertiaryContainer, unknown → outlined surface. Parking shows a "P".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitChip(unit: BuildingUnit, onClick: (BuildingUnit) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (unit.status) {
        1 -> scheme.primaryContainer to scheme.onPrimaryContainer
        2 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        3 -> scheme.surfaceVariant to scheme.onSurfaceVariant
        4 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val border: BorderStroke? =
        if (unit.status !in 1..4) BorderStroke(1.dp, scheme.outline) else null

    Surface(
        onClick = { onClick(unit) },
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = unit.unitNo,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit.isParking) {
                    Spacer(Modifier.size(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = content.copy(alpha = 0.15f),
                        contentColor = content,
                    ) {
                        Text(
                            text = "P",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = statusLabel(unit.status),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

/** Read-only unit details — the web's modal without the Sale/Payment actions. */
@Composable
private fun UnitDetailsDialog(unit: BuildingUnit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(unit.unitNo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("Status", statusLabel(unit.status))
                DetailRow("Type", if (unit.isParking) "Parking" else "Unit")
                DetailRow(
                    "Size",
                    if (unit.sizeSqft > 0) "${AmountFormat.format(unit.sizeSqft, 0)} sqft" else "-",
                )
                DetailRow(
                    "Rate",
                    if (unit.salePrice > 0) AmountFormat.format(unit.salePrice, 0) else "-",
                )
                DetailRow(
                    "Total Price",
                    unit.totalPrice?.let { AmountFormat.format(it, 0) } ?: "-",
                )
                if (!unit.customerName.isNullOrBlank() || !unit.customerMobile.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow("Customer", unit.customerName ?: "-")
                    DetailRow("Mobile", unit.customerMobile ?: "-")
                }
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onDismiss) },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
