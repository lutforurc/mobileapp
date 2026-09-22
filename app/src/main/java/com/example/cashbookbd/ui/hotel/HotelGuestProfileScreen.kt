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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cashbookbd.data.repository.HotelGuestNote
import com.example.cashbookbd.data.repository.HotelGuestProfile
import com.example.cashbookbd.data.repository.HotelGuestStay
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelGuestProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: HotelGuestProfile? = null,
    val newNote: String = "",
    val isSavingNote: Boolean = false,
    val deletingNoteId: Long? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * One guest, every stay — "has this person been here before, and how did it
 * go?" No new table for the history: it is every [HotelGuestStay] this NID or
 * mobile touches, joined together. The one thing that is new is the notes —
 * a sentence about the person, kept apart from any one booking.
 */
class HotelGuestProfileViewModel(
    private val repository: HotelRepository,
    private val nationalId: String,
    private val mobile: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelGuestProfileUiState())
    val uiState: StateFlow<HotelGuestProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchGuestHistory(nationalId, mobile)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, profile = result.data) }
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

    fun onNewNote(v: String) = _uiState.update { it.copy(newNote = v.take(500)) }

    fun saveNote() {
        val profile = _uiState.value.profile ?: return
        val note = _uiState.value.newNote.trim()
        if (note.isEmpty()) return
        val keyKind: String
        val key: String
        if (profile.guestNationalId.isNotBlank()) {
            keyKind = "national_id"
            key = profile.guestNationalId
        } else {
            keyKind = "mobile"
            key = profile.guestMobile.ifBlank { profile.keyMobile }
        }
        if (key.isBlank()) return
        _uiState.update { it.copy(isSavingNote = true) }
        viewModelScope.launch {
            when (val result = repository.addGuestNote(keyKind, key, note)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSavingNote = false,
                        newNote = "",
                        profile = it.profile?.let { p -> p.copy(notes = listOf(result.data) + p.notes) },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSavingNote = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteNote(id: Long) {
        _uiState.update { it.copy(deletingNoteId = id) }
        viewModelScope.launch {
            when (val result = repository.deleteGuestNote(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        deletingNoteId = null,
                        profile = it.profile?.let { p -> p.copy(notes = p.notes.filterNot { n -> n.id == id }) },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        deletingNoteId = null,
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
        fun provideFactory(context: Context, nationalId: String, mobile: String) = viewModelFactory {
            initializer {
                HotelGuestProfileViewModel(
                    repository = ServiceLocator.provideHotelRepository(context.applicationContext),
                    nationalId = nationalId,
                    mobile = mobile,
                )
            }
        }
    }
}

@Composable
fun HotelGuestProfileScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    nationalId: String,
    mobile: String,
    modifier: Modifier = Modifier,
    viewModel: HotelGuestProfileViewModel = viewModel(
        factory = HotelGuestProfileViewModel.provideFactory(LocalContext.current, nationalId, mobile),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDeleteId by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

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
    }

    AuthenticatedShell(
        title = "Guest History",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.profile == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.profile == null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }

                else -> state.profile?.let { profile ->
                    if (!profile.found) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Nobody on file yet under that NID or mobile.",
                                color = MaterialTheme.appColors.textOnScreenMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        GuestProfileBody(
                            profile = profile,
                            state = state,
                            viewModel = viewModel,
                            onAskDelete = { confirmDeleteId = it },
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Remove this note?") },
            text = { Text("A wrong note is removed and a right one written — it cannot be undone.") },
            confirmButton = {
                PrimaryButton(
                    text = "Remove",
                    onClick = { viewModel.deleteNote(id); confirmDeleteId = null },
                    containerColor = MaterialTheme.appColors.danger,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = { confirmDeleteId = null }) },
        )
    }
}

@Composable
private fun GuestProfileBody(
    profile: HotelGuestProfile,
    state: HotelGuestProfileUiState,
    viewModel: HotelGuestProfileViewModel,
    onAskDelete: (Long) -> Unit,
) {
    val totals = profile.totals
    val muted = MaterialTheme.appColors.textMuted

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = profile.guestName.ifBlank { "Unnamed" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = AppFontWeight.Bold,
                )
                val lines = listOfNotNull(
                    profile.guestMobile.takeIf { it.isNotBlank() },
                    profile.guestNationalId.takeIf { it.isNotBlank() }?.let { "NID: $it" },
                    profile.guestAddress.takeIf { it.isNotBlank() },
                )
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HotelMoneyTile(label = "Stays", value = totals.stays.toString(), modifier = Modifier.weight(1f))
                HotelMoneyTile(
                    label = "No-shows",
                    value = totals.noShows.toString(),
                    valueColor = if (totals.noShows > 0) MaterialTheme.appColors.warning else null,
                    modifier = Modifier.weight(1f),
                )
                HotelMoneyTile(
                    label = "Owes",
                    value = hotelMoney(totals.due),
                    valueColor = if (totals.due > 0) MaterialTheme.appColors.danger else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HotelMoneyTile(label = "Cancelled", value = totals.cancelled.toString(), modifier = Modifier.weight(1f))
                HotelMoneyTile(label = "Upcoming", value = totals.upcoming.toString(), modifier = Modifier.weight(1f))
                HotelMoneyTile(
                    label = "Last stay",
                    value = totals.lastStay.takeIf { it.isNotBlank() }?.let { hotelDate(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { HotelSectionTitle("Notes") }
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = state.newNote,
                    onValueChange = viewModel::onNewNote,
                    label = "Add a note",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            PrimaryButton(
                text = "Save note",
                onClick = viewModel::saveNote,
                enabled = state.newNote.isNotBlank() && !state.isSavingNote,
                isLoading = state.isSavingNote,
                compact = true,
            )
        }
        if (profile.notes.isEmpty()) {
            item {
                Text("No notes yet.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        } else {
            items(profile.notes.size, key = { profile.notes[it].id }) { index ->
                val note = profile.notes[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.note, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = listOf(note.by, note.createdAt.take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                            )
                        }
                        LinkButton(
                            text = if (state.deletingNoteId == note.id) "…" else "Remove",
                            onClick = { onAskDelete(note.id) },
                            color = MaterialTheme.appColors.danger,
                            enabled = state.deletingNoteId == null,
                        )
                    }
                }
            }
        }

        item { HotelSectionTitle("Every stay") }
        if (profile.stays.isEmpty()) {
            item { Text("No stays on file.", style = MaterialTheme.typography.bodySmall, color = muted) }
        } else {
            items(profile.stays.size, key = { profile.stays[it].id }) { index ->
                GuestStayRow(profile.stays[index])
            }
        }
    }
}

@Composable
private fun GuestStayRow(stay: HotelGuestStay) {
    val muted = MaterialTheme.appColors.textMuted
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stay.bookingNo.ifBlank { "Booking #${stay.id}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                HotelStatusChip(status = stay.status)
                if (!stay.wasGuest) {
                    Text("telephoned only", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Text(
                text = "${hotelDate(stay.checkInDate)} → ${hotelDate(stay.checkOutDate)}" +
                    if (stay.rooms.isNotEmpty()) " · ${stay.rooms.joinToString(", ")}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
            if (stay.billed > 0) {
                Text(
                    text = "Billed ${hotelMoney(stay.billed)} · Paid ${hotelMoney(stay.paid)}" +
                        if (stay.due > 0 && !stay.carried) " · Due ${hotelMoney(stay.due)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stay.due > 0 && !stay.carried) MaterialTheme.appColors.danger else muted,
                )
            }
        }
    }
}
