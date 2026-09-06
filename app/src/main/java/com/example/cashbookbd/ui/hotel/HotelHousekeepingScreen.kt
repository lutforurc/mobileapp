package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
import com.example.cashbookbd.data.repository.HkBoard
import com.example.cashbookbd.data.repository.HkHistory
import com.example.cashbookbd.data.repository.HkRoom
import com.example.cashbookbd.data.repository.HotelOpsRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The four states a room can be in, in legend and button order. [hint] is what
 * the state means to the person holding the sheets.
 */
private data class HkState(val id: String, val name: String, val hint: String)

private val HK_STATES = listOf(
    HkState("dirty", "Dirty", "Somebody has left it"),
    HkState("cleaning", "Being cleaned", "Somebody is in there now"),
    HkState("clean", "Ready", "Made up, ready for a guest"),
    HkState("out_of_order", "Out of order", "Not for sale until somebody clears it"),
)

/** An unknown state reads as clean — the same rule the server applies to a room with no row. */
private fun hkLook(status: String): HkState = HK_STATES.firstOrNull { it.id == status } ?: HK_STATES[2]

data class HotelHousekeepingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val board: HkBoard? = null,
    /** The state the board is narrowed to; null shows every room. */
    val filter: String? = null,
    val branches: List<BranchOption> = emptyList(),
    val branch: BranchOption? = null,
    val isSaving: Boolean = false,
    /** The room being taken out of order, and why. */
    val blocking: HkRoom? = null,
    val blockingNote: String = "",
    val history: HkHistory? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelHousekeepingViewModel(
    private val repository: HotelOpsRepository,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelHousekeepingUiState(isLoading = true))
    val uiState: StateFlow<HotelHousekeepingUiState> = _uiState.asStateFlow()

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

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchBoard(_uiState.value.branch?.id)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, board = result.data) }
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

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(branch = branch, filter = null) }
        load()
    }

    /** Pressing the active count clears the filter: the count stays visible while the rooms are done. */
    fun toggleFilter(status: String) =
        _uiState.update { it.copy(filter = if (it.filter == status) null else status) }

    /**
     * One press moves a room. Only out of order asks first — it takes the
     * room off the market until a person clears it, so it has to say why;
     * everything else is one press because somebody with an armful of sheets
     * will not confirm forty dialogs.
     */
    fun move(room: HkRoom, status: String) {
        if (status == "out_of_order") {
            _uiState.update { it.copy(blocking = room, blockingNote = "") }
            return
        }
        post(room, status, null)
    }

    fun onBlockingNote(value: String) = _uiState.update { it.copy(blockingNote = value) }

    fun cancelBlocking() = _uiState.update { it.copy(blocking = null, blockingNote = "") }

    fun confirmBlocking() {
        val state = _uiState.value
        val room = state.blocking ?: return
        val note = state.blockingNote.trim()
        if (note.isEmpty()) return
        post(room, "out_of_order", note)
    }

    private fun post(room: HkRoom, status: String, notes: String?) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.moveRoom(room.id, status, notes)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, message = result.data, blocking = null, blockingNote = "")
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun openHistory(room: HkRoom) {
        viewModelScope.launch {
            when (val result = repository.fetchHistory(room.id)) {
                is Resource.Success -> _uiState.update { it.copy(history = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closeHistory() = _uiState.update { it.copy(history = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                HotelHousekeepingViewModel(
                    repository = HotelOpsRepository.get(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  The board's shape
// ---------------------------------------------------------------------------

internal data class HkFloorGroup(val id: String, val label: String, val rooms: List<HkRoom>)

internal data class HkBlockGroup(
    val id: String,
    val name: String,
    val code: String,
    val count: Int,
    val floors: List<HkFloorGroup>,
)

/** One rule for what goes in the gutter: "5th Floor" does not fit there, "5F" does. */
internal fun hkFloorLabel(floorNo: Int?): String = when {
    floorNo == null -> "—"
    floorNo == 0 -> "G"
    floorNo < 0 -> "B${-floorNo}"
    else -> "${floorNo}F"
}

/**
 * The property drawn the way the layout tab draws it: one card per block,
 * floors stacked with the top one at the top.
 *
 * Built by WALKING the list rather than sorting it — the server already sends
 * the rooms by block, then floor number, then room number, and a sort here
 * would be a second opinion about that order. Which end is up is a fact about
 * the drawing, so the floors are turned over here; the rooms that belong to
 * no floor go under the lot rather than on the roof.
 */
internal fun hkGroup(rooms: List<HkRoom>): List<HkBlockGroup> {
    class FloorAcc(val id: String, val label: String, val rooms: MutableList<HkRoom> = mutableListOf())
    class BlockAcc(
        val id: String,
        val name: String,
        val code: String,
        var count: Int = 0,
        val floors: MutableList<FloorAcc> = mutableListOf(),
    )

    val blocks = mutableListOf<BlockAcc>()
    rooms.forEach { room ->
        val blockId = room.buildingId?.toString() ?: room.building
        var block = blocks.lastOrNull()
        if (block == null || block.id != blockId) {
            block = BlockAcc(
                id = blockId,
                // A room in no block still has to go somewhere it can be found.
                name = room.buildingName.ifBlank { room.building }.ifBlank { "Elsewhere on the property" },
                code = if (room.buildingName.isNotBlank() && room.building != room.buildingName) room.building else "",
            )
            blocks.add(block)
        }
        block.count += 1
        val floorId = room.floorId?.toString().orEmpty()
        var floor = block.floors.lastOrNull()
        if (floor == null || floor.id != floorId) {
            floor = FloorAcc(floorId, hkFloorLabel(room.floorNo))
            block.floors.add(floor)
        }
        floor.rooms.add(room)
    }
    return blocks.map { b ->
        val (loose, placed) = b.floors.reversed().partition { it.id.isEmpty() }
        HkBlockGroup(
            id = b.id,
            name = b.name,
            code = b.code,
            count = b.count,
            floors = (placed + loose).map { HkFloorGroup(it.id, it.label, it.rooms) },
        )
    }
}

// ---------------------------------------------------------------------------
//  The screen
// ---------------------------------------------------------------------------

/**
 * Is the room ready? The housekeeping board.
 *
 * Cleanliness and occupancy are different questions and the board shows both,
 * because a dirty room somebody is still asleep in is a different job from a
 * dirty room that is empty. Whether a bed is SOLD comes from the nights;
 * whether a room is FIT TO ENTER comes from here — and the one the desk can
 * hand a key to today is only the room that is free AND ready.
 */
@Composable
fun HotelHousekeepingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelHousekeepingViewModel = viewModel(
        factory = HotelHousekeepingViewModel.provideFactory(LocalContext.current),
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
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    val rooms = remember(state.board, state.filter) {
        state.board?.rooms.orEmpty().filter { state.filter == null || it.status == state.filter }
    }
    val blocks = remember(rooms) { hkGroup(rooms) }

    AuthenticatedShell(
        title = "Housekeeping",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.branches.size > 1) {
                    item {
                        HotelOpsBranchPicker(
                            branches = state.branches,
                            selected = state.branch,
                            onSelected = viewModel::onBranch,
                        )
                    }
                }
                state.board?.let { board ->
                    item {
                        // The shape of the morning. Pressing one filters to it;
                        // pressing it again clears — a housekeeper working
                        // down the dirty rooms wants the count to stay visible.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HK_STATES.forEach { s ->
                                    HkCountTile(
                                        state = s,
                                        count = board.counts.of(s.id),
                                        active = state.filter == s.id,
                                        onClick = { viewModel.toggleFilter(s.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                LinkButton(
                                    text = "Refresh",
                                    onClick = viewModel::load,
                                    icon = Icons.Filled.Refresh,
                                    enabled = !state.isLoading,
                                )
                            }
                        }
                    }
                }
                when {
                    state.error != null -> item {
                        HotelOpsProblem(text = state.error!!, onRetry = viewModel::load)
                    }
                    state.isLoading && state.board == null -> item { HotelOpsLoading() }
                    else -> {
                        items(blocks, key = { it.id }) { block ->
                            HkBlockCard(
                                block = block,
                                saving = state.isSaving,
                                onMove = viewModel::move,
                                onHistory = viewModel::openHistory,
                            )
                        }
                        if (rooms.isEmpty()) {
                            item {
                                HotelOpsProblem(
                                    text = if (state.filter != null) {
                                        "No rooms in that state."
                                    } else {
                                        "No rooms on this property yet. Set them up on the Hotel Setup screen first."
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    HotelOpsNote(
                        "A room is marked dirty automatically when its guests check out. " +
                            "Out of order takes it off the booking screen entirely until somebody clears it.",
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.blocking?.let { room ->
        HkOutOfOrderDialog(
            room = room,
            note = state.blockingNote,
            saving = state.isSaving,
            onNote = viewModel::onBlockingNote,
            onConfirm = viewModel::confirmBlocking,
            onCancel = viewModel::cancelBlocking,
        )
    }
    state.history?.let { history ->
        HkHistoryDialog(history = history, onClose = viewModel::closeHistory)
    }
}

/** The ink a state is drawn in; colour on this board means a state and nothing else. */
@Composable
private fun hkInk(status: String): Color = when (status) {
    "dirty" -> MaterialTheme.appColors.danger
    "cleaning" -> MaterialTheme.appColors.warning
    "out_of_order" -> MaterialTheme.appColors.textMuted
    else -> MaterialTheme.appColors.success
}

@Composable
private fun hkTint(status: String): Color = when (status) {
    "dirty" -> MaterialTheme.appColors.dangerTint
    "cleaning" -> MaterialTheme.appColors.warningTint
    "out_of_order" -> MaterialTheme.appColors.cardMuted
    else -> MaterialTheme.appColors.successTint
}

@Composable
private fun HkCountTile(
    state: HkState,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = hkInk(state.id)
    Surface(
        shape = AppShape,
        color = hkTint(state.id),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (active) 2.dp else 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary else ink,
        ),
        modifier = modifier
            .clip(AppShape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .heightIn(min = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
                color = ink,
            )
            Text(
                text = state.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HkBlockCard(
    block: HkBlockGroup,
    saving: Boolean,
    onMove: (HkRoom, String) -> Unit,
    onHistory: (HkRoom) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        // The block named the way the layout grid names it — full name, code
        // beside it — and under it how many rooms are on show, so a filtered
        // board says "3 rooms" rather than leaving somebody to count cards.
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.Bold,
                )
                if (block.code.isNotBlank()) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
            }
            Text(
                text = "${block.count} ${if (block.count == 1) "room" else "rooms"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
            )
        }
        HorizontalDivider(color = MaterialTheme.appColors.divider)
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.floors.forEach { floor ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The floor marker, in ink and no colour: on this board
                    // colour means a state, and a filled chip in the gutter
                    // would join that conversation with nothing to say.
                    Text(
                        text = floor.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(32.dp)
                            .padding(top = 8.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        floor.rooms.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { room ->
                                    HkRoomCard(
                                        room = room,
                                        saving = saving,
                                        onMove = onMove,
                                        onHistory = onHistory,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HkRoomCard(
    room: HkRoom,
    saving: Boolean,
    onMove: (HkRoom, String) -> Unit,
    onHistory: (HkRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = hkInk(room.status)
    Surface(
        shape = AppShape,
        color = hkTint(room.status),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, ink),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onHistory(room) }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "What has happened to this room",
                        tint = ink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Occupancy, said separately from cleanliness.
            Text(
                text = if (room.occupied) room.guest.ifBlank { "occupied" } else "empty",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (room.notes.isNotBlank()) {
                Text(
                    text = room.notes,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.appColors.textMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HK_STATES.filter { it.id != room.status }.forEach { s ->
                    SecondaryButton(
                        text = s.name,
                        onClick = { onMove(room, s.id) },
                        enabled = !saving,
                        compact = true,
                    )
                }
            }
        }
    }
}

/**
 * The only state with a dialog behind it. The note is required and the button
 * stays dead until it is given: a room nobody can sell for a reason nobody
 * wrote down stays out of order until it is noticed.
 */
@Composable
private fun HkOutOfOrderDialog(
    room: HkRoom,
    note: String,
    saving: Boolean,
    onNote: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        title = { Text("Take this room out of order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = room.name, fontWeight = AppFontWeight.SemiBold)
                Text(
                    text = "It will not be offered on the booking screen at all — not even for " +
                        "dates it is free on — until somebody puts it back.",
                    style = MaterialTheme.typography.bodySmall,
                )
                AppTextField(
                    value = note,
                    onValueChange = { if (it.length <= 255) onNote(it) },
                    label = "Air conditioner broken, being painted…",
                    caption = "What is wrong with it",
                    multiline = true,
                    enabled = !saving,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Take it out of service",
                onClick = onConfirm,
                enabled = note.isNotBlank(),
                isLoading = saving,
            )
        },
        dismissButton = {
            LinkButton(text = "Never mind", onClick = onCancel, enabled = !saving)
        },
    )
}

@Composable
private fun HkHistoryDialog(history: HkHistory, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${history.room.ifBlank { "Room" }} — what has happened to it") },
        text = {
            if (history.rows.isEmpty()) {
                Text(
                    text = "Nothing has happened to it yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textMuted,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(history.rows) { row ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = buildString {
                                    append(row.at)
                                    append("  ")
                                    append(row.to.replace('_', ' '))
                                    if (row.from.isNotBlank()) append(" (was ${row.from.replace('_', ' ')})")
                                    append(" · ")
                                    append(row.by)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (row.notes.isNotBlank()) {
                                Text(
                                    text = row.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.appColors.textMuted,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.appColors.divider)
                    }
                }
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onClose) },
    )
}
