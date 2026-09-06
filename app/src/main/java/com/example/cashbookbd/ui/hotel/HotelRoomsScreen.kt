package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.cashbookbd.data.repository.HotelResourceRow
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a room is sold, worded as the web words it. */
internal val SALE_MODE_OPTIONS = listOf(
    SelectorOption("whole", "Whole room only"),
    SelectorOption("seat", "By the seat only"),
    SelectorOption("both", "Either — whole or by the seat"),
)

internal fun saleModeLabel(mode: String): String =
    SALE_MODE_OPTIONS.firstOrNull { it.id == mode }?.label ?: mode

private val ALL_BUILDINGS = SelectorOption("", "All buildings")

data class HotelRoomsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<HotelResourceRow> = emptyList(),
    val search: String = "",
    val buildings: List<SelectorOption> = listOf(ALL_BUILDINGS),
    val building: SelectorOption = ALL_BUILDINGS,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    /** The row whose delete is being confirmed. */
    val confirmDelete: HotelResourceRow? = null,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelRoomsViewModel(
    private val repository: HotelSetupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelRoomsUiState())
    val uiState: StateFlow<HotelRoomsUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    init {
        viewModelScope.launch {
            val result = repository.fetchBuildingDdl()
            if (result is Resource.Success) {
                _uiState.update { s ->
                    s.copy(buildings = listOf(ALL_BUILDINGS) + result.data.map { SelectorOption(it.value.toString(), it.label) })
                }
            }
        }
        load(page = 1)
    }

    /** Coming back from the form: the list underneath has to show what the save did. */
    fun onResume() {
        if (loadedOnce) load(page = _uiState.value.currentPage)
    }

    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }

    fun search() = load(page = 1)

    fun onBuilding(option: SelectorOption) {
        _uiState.update { it.copy(building = option) }
        load(page = 1)
    }

    fun goToPage(page: Int) = load(page)

    fun askDelete(row: HotelResourceRow?) = _uiState.update { it.copy(confirmDelete = row) }

    /** Refused server-side once a booking has touched it; the sentence says to set it inactive instead. */
    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = repository.deleteResource(row.id)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isDeleting = false, confirmDelete = null, message = result.data) }
                    load(page = _uiState.value.currentPage)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false, confirmDelete = null, message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun load(page: Int) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchResources(
                search = state.search,
                buildingId = state.building.id.toLongOrNull(),
                page = page,
            )
            loadedOnce = true
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { HotelRoomsViewModel(repository = HotelSetupRepository.get(context)) }
        }
    }
}

/**
 * Rooms & Seats — the inventory, one row per room, hall or centre.
 *
 * Seats are deliberately absent from the list: a twelve-room hotel with four
 * beds a room would otherwise be sixty rows, fifty-eight of which nobody at
 * the desk thinks of as a thing you can look at. They are reached through
 * their room, on the form.
 */
@Composable
fun HotelRoomsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelRoomsViewModel = viewModel(
        factory = HotelRoomsViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val allowed = Permissions.hasAny(sessionState.permissions, listOf("hotel.resource.view"))
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.onResume() }
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
        title = "Rooms & Seats",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!allowed) {
            NoAccess("You don't have access to the rooms.")
            return@AuthenticatedShell
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        label = "Room number or name",
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(text = "Search", onClick = viewModel::search, compact = true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppSelectDropdown(
                        label = "Building",
                        options = state.buildings,
                        selected = state.building,
                        onSelected = viewModel::onBuilding,
                        modifier = Modifier.weight(1f),
                    )
                    AddButton(
                        text = "Add",
                        onClick = { navController.navigate(HotelMenu.roomForm(null)) },
                        compact = true,
                    )
                }

                when {
                    state.isLoading && state.rows.isEmpty() -> Box(
                        Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null && state.rows.isEmpty() -> Box(
                        Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error.orEmpty(), color = MaterialTheme.appColors.textOnScreen, textAlign = TextAlign.Center)
                            LinkButton(text = "Retry", onClick = viewModel::search)
                        }
                    }

                    else -> RoomsTable(
                        rows = state.rows,
                        onEdit = { navController.navigate(HotelMenu.roomForm(it.id)) },
                        onDelete = viewModel::askDelete,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinkButton(
                        text = "Prev",
                        enabled = state.currentPage > 1 && !state.isLoading,
                        onClick = { viewModel.goToPage(state.currentPage - 1) },
                    )
                    Text(
                        text = "Page ${state.currentPage} of ${state.lastPage} · ${state.total} " +
                            if (state.total == 1) "room" else "rooms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    LinkButton(
                        text = "Next",
                        enabled = state.currentPage < state.lastPage && !state.isLoading,
                        onClick = { viewModel.goToPage(state.currentPage + 1) },
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) viewModel.askDelete(null) },
            title = { Text("Delete ${row.displayName}?") },
            text = {
                Text(
                    "Its beds go with it. A room that has ever been booked cannot be " +
                        "deleted — set it inactive instead, so the older bookings still read.",
                )
            },
            confirmButton = {
                PrimaryButton(text = "Delete", onClick = viewModel::confirmDelete, isLoading = state.isDeleting, compact = true)
            },
            dismissButton = {
                LinkButton(text = "Keep it", onClick = { viewModel.askDelete(null) }, enabled = !state.isDeleting)
            },
        )
    }
}

@Composable
private fun RoomsTable(
    rows: List<HotelResourceRow>,
    onEdit: (HotelResourceRow) -> Unit,
    onDelete: (HotelResourceRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.appColors.textMuted
    val success = MaterialTheme.appColors.success
    val columns = listOf(
        ReportColumn<HotelResourceRow>("Room", ReportColWidth.Fixed(160.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(row.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val kind = listOf(row.typeName, row.roomTypeName).filter { it.isNotBlank() }.joinToString(" · ")
                    if (kind.isNotBlank()) {
                        Text(kind, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Four and a count rather than the lot: the twelfth tick is
                    // not what anybody is scanning this column for.
                    if (row.facilities.isNotEmpty()) {
                        val shown = row.facilities.take(4).joinToString(" · ")
                        val more = row.facilities.size - 4
                        Text(
                            text = if (more > 0) "$shown +$more more" else shown,
                            style = MaterialTheme.typography.labelSmall, color = muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        ReportColumn("Where", ReportColWidth.Fixed(130.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(row.buildingName.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // No floor is a real answer, not a gap: cottages have none.
                    Text(row.floorName.ifBlank { "no floor" }, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
        },
        ReportColumn("Sold", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(if (row.kind == "room") saleModeLabel(row.saleMode) else "By the sitting", maxLines = 2)
        },
        ReportColumn("Rent", ReportColWidth.Fixed(90.dp), align = TextAlign.End) { row, _ ->
            // A dash, not 0.00: a room sold only by the bed has no whole-room rent.
            cellText(row.rent?.let { AmountFormat.format(it) } ?: "—")
        },
        ReportColumn("Beds", ReportColWidth.Fixed(70.dp), align = TextAlign.Center) { row, _ ->
            cellText(
                when {
                    row.kind != "room" -> "—"
                    row.seatsCount > row.activeSeatsCount -> "${row.activeSeatsCount} / ${row.seatsCount}"
                    else -> row.activeSeatsCount.toString()
                }
            )
        },
        ReportColumn("Status", ReportColWidth.Fixed(80.dp), align = TextAlign.Center) { row, _ ->
            if (row.status == 1) cellText("Active", color = success) else cellText("Inactive", color = muted)
        },
        ReportColumn("Action", ReportColWidth.Fixed(90.dp), align = TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDelete(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.appColors.danger)
                    }
                }
            }
        },
    )
    ReportTable(
        columns = columns,
        data = rows,
        modifier = modifier,
        noDataMessage = "No rooms yet. Add a building and a room type first, then the rooms.",
    )
}

/** The gate every setup screen closes with — one sentence, centred. */
@Composable
internal fun NoAccess(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.appColors.textOnScreenMuted, textAlign = TextAlign.Center)
    }
}
