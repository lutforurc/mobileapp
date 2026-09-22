package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.data.repository.HotelFreeRoom
import com.example.cashbookbd.data.repository.HotelMoveOptions
import com.example.cashbookbd.data.repository.HotelMovePlan
import com.example.cashbookbd.data.repository.HotelMoveRoomOption
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelMoveRoomUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val options: HotelMoveOptions? = null,
    val fromDate: SimpleDate? = null,
    val fromRoomId: Long? = null,
    val toRoomId: Long? = null,
    val reason: String = "",
    val keepRate: Boolean = true,
    val isPreviewing: Boolean = false,
    val plan: HotelMovePlan? = null,
    val confirm: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val done: Boolean = false,
) {
    val movableRooms: List<HotelMoveRoomOption> get() = options?.rooms.orEmpty().filter { it.movable }
    val canPreview: Boolean get() = fromRoomId != null && toRoomId != null && !isPreviewing
}

/**
 * Moving a guest to another room mid-stay — 101's air conditioner fails and
 * the guest goes to 205. The unbilled nights re-point outright; a night
 * already on the bill keeps its line and figure, only the room it names
 * moves. [HotelMoveRoomUiState.keepRate] decides whose money the move
 * costs: the hotel's fault keeps the old rent, the guest's own upgrade pays
 * the new room's rate — and a billed night can never be re-priced either way.
 */
class HotelMoveRoomViewModel(
    private val repository: HotelFolioRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelMoveRoomUiState())
    val uiState: StateFlow<HotelMoveRoomUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(fromDate: SimpleDate? = _uiState.value.fromDate) {
        _uiState.update { it.copy(isLoading = true, error = null, plan = null) }
        viewModelScope.launch {
            when (val result = repository.fetchMoveOptions(bookingId, fromDate?.toApi())) {
                is Resource.Success -> _uiState.update { s ->
                    val movable = result.data.rooms.filter { it.movable }
                    s.copy(
                        isLoading = false,
                        options = result.data,
                        fromDate = fromDate ?: SimpleDate.fromApi(result.data.fromDate),
                        fromRoomId = s.fromRoomId?.takeIf { id -> movable.any { it.roomId == id } }
                            ?: movable.singleOrNull()?.roomId,
                        toRoomId = s.toRoomId?.takeIf { id -> result.data.freeRooms.any { it.id == id } },
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

    fun onDate(date: SimpleDate) {
        _uiState.update { it.copy(fromDate = date) }
        load(date)
    }

    fun onFromRoom(roomId: Long) = _uiState.update { it.copy(fromRoomId = roomId, toRoomId = null, plan = null) }
    fun onToRoom(roomId: Long) = _uiState.update { it.copy(toRoomId = roomId, plan = null) }
    fun onReason(v: String) = _uiState.update { it.copy(reason = v.take(255), plan = null) }
    fun onKeepRate(v: Boolean) = _uiState.update { it.copy(keepRate = v, plan = null) }

    fun preview() {
        val s = _uiState.value
        val fromRoom = s.fromRoomId ?: return
        val toRoom = s.toRoomId ?: return
        _uiState.update { it.copy(isPreviewing = true, message = null) }
        viewModelScope.launch {
            val result = repository.moveRoom(
                bookingId = bookingId,
                fromRoomId = fromRoom,
                toRoomId = toRoom,
                fromDate = s.fromDate?.toApi(),
                reason = s.reason,
                keepRate = s.keepRate,
                dryRun = true,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isPreviewing = false, plan = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isPreviewing = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askConfirm() = _uiState.update { it.copy(confirm = true) }
    fun dismissConfirm() = _uiState.update { it.copy(confirm = false) }

    fun post() {
        val s = _uiState.value
        val fromRoom = s.fromRoomId ?: return
        val toRoom = s.toRoomId ?: return
        if (s.isWorking) return
        _uiState.update { it.copy(isWorking = true, confirm = false) }
        viewModelScope.launch {
            val result = repository.moveRoom(
                bookingId = bookingId,
                fromRoomId = fromRoom,
                toRoomId = toRoom,
                fromDate = s.fromDate?.toApi(),
                reason = s.reason,
                keepRate = s.keepRate,
                dryRun = false,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isWorking = false, message = "${it.plan?.fromRoomName} → ${result.data.toRoomName} — done", done = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = result.message,
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
        fun provideFactory(context: Context, bookingId: Long) = viewModelFactory {
            initializer {
                HotelMoveRoomViewModel(
                    repository = HotelFolioRepository.get(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

@Composable
fun HotelMoveRoomScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelMoveRoomViewModel = viewModel(
        factory = HotelMoveRoomViewModel.provideFactory(LocalContext.current, bookingId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.onMessageShown()
        if (state.done) navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Move Room",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.options == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.options == null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = { viewModel.load() })
                }

                else -> state.options?.let { options ->
                    MoveRoomBody(state = state, options = options, context = context, viewModel = viewModel)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.confirm && state.plan != null) {
        val plan = state.plan!!
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text("Move to ${plan.toRoomName}?") },
            text = {
                Text(
                    "${plan.nightsMoving} night${if (plan.nightsMoving == 1) "" else "s"} from ${hotelDate(plan.fromDate)}, " +
                        "charged at ${hotelMoney(plan.charging)} a night." +
                        if (plan.toRoomDirty) " ${plan.toRoomName} is not yet made up — housekeeping will need to be told." else ""
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Move the room",
                    onClick = viewModel::post,
                    enabled = !state.isWorking,
                    isLoading = state.isWorking,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissConfirm, enabled = !state.isWorking) },
        )
    }
}

@Composable
private fun MoveRoomBody(
    state: HotelMoveRoomUiState,
    options: HotelMoveOptions,
    context: Context,
    viewModel: HotelMoveRoomViewModel,
) {
    val fromOptions = state.movableRooms.map { SelectorOption(it.roomId.toString(), it.displayName) }
    val toOptions = options.freeRooms.map { room ->
        SelectorOption(room.id.toString(), room.displayName, "${hotelMoney(room.rent)}/night")
    }
    val plan = state.plan

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.movableRooms.isEmpty()) {
            item {
                HotelBanner(
                    text = "Nothing on this booking can be moved — every room is by the bed, already left, or holds no night ahead.",
                    color = MaterialTheme.appColors.info,
                )
            }
            return@LazyColumn
        }

        item {
            PickerField(
                label = "From",
                value = state.fromDate?.toDisplay().orEmpty(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
                onClick = { pickMoneyDate(context, state.fromDate, viewModel::onDate) },
            )
        }
        item {
            AppSelectDropdown(
                label = "Room",
                options = fromOptions,
                selected = fromOptions.firstOrNull { it.id == state.fromRoomId?.toString() },
                onSelected = { option -> option.id.toLongOrNull()?.let(viewModel::onFromRoom) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Which room is the guest leaving",
            )
        }
        item {
            AppSelectDropdown(
                label = "To",
                options = toOptions,
                selected = toOptions.firstOrNull { it.id == state.toRoomId?.toString() },
                onSelected = { option -> option.id.toLongOrNull()?.let(viewModel::onToRoom) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (toOptions.isEmpty()) "Nothing free on those nights" else "Which room is the guest going to",
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep the old rate", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Off charges the new room's own rate — for a guest who asked to upgrade.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
                Switch(checked = state.keepRate, onCheckedChange = viewModel::onKeepRate)
            }
        }
        item {
            AppTextField(
                value = state.reason,
                onValueChange = viewModel::onReason,
                label = "Why",
                modifier = Modifier.fillMaxWidth(),
                caption = "Kept on the booking's history.",
            )
        }
        item {
            PrimaryButton(
                text = "Preview",
                onClick = viewModel::preview,
                enabled = state.canPreview,
                isLoading = state.isPreviewing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (plan != null) {
            item { HotelSectionTitle("What would change") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotelMoneyTile(
                        label = "Nights",
                        value = plan.nightsMoving.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    HotelMoneyTile(
                        label = "Rate",
                        value = hotelMoney(plan.charging),
                        modifier = Modifier.weight(1f),
                    )
                    HotelMoneyTile(
                        label = "Guests",
                        value = plan.guestsMoving.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (plan.billedMoving > 0) {
                item {
                    Text(
                        text = "${plan.billedMoving} of those nights ${if (plan.billedMoving == 1) "is" else "are"} already on the bill " +
                            "at ${hotelMoney(plan.oldRate)} — its line moves to the new room, the figure stays.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
            }
            if (plan.toRoomDirty) {
                item {
                    HotelBanner(
                        text = "${plan.toRoomName} is not made up yet.",
                        color = MaterialTheme.appColors.warning,
                    )
                }
            }
            if (plan.refusal != null) {
                item { HotelBanner(text = plan.refusal, color = MaterialTheme.appColors.danger) }
            } else {
                item {
                    PrimaryButton(
                        text = "Move the room",
                        onClick = viewModel::askConfirm,
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
