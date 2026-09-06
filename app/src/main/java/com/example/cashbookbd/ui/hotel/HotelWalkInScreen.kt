package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelWalkInUiState(
    val bookerName: String = "",
    val bookerMobile: String = "",
    val adults: String = "",
    val children: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    /** The new booking's id once the sale is recorded — the screen opens its bill. */
    val createdId: Long? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = bookerName.isNotBlank() && !isSaving
}

class HotelWalkInViewModel(
    private val repository: HotelFolioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelWalkInUiState())
    val uiState: StateFlow<HotelWalkInUiState> = _uiState.asStateFlow()

    fun onName(v: String) = _uiState.update { it.copy(bookerName = v.take(150)) }
    fun onMobile(v: String) = _uiState.update { it.copy(bookerMobile = v.take(30)) }
    fun onAdults(v: String) = _uiState.update { it.copy(adults = v.filter { c -> c.isDigit() }.take(3)) }
    fun onChildren(v: String) = _uiState.update { it.copy(children = v.filter { c -> c.isDigit() }.take(3)) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.storeWalkIn(
                bookerName = s.bookerName,
                bookerMobile = s.bookerMobile,
                statedAdults = s.adults.toIntOrNull(),
                statedChildren = s.children.toIntOrNull(),
                notes = s.notes,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = if (result.data.bookingId == null) result.data.message else null,
                        createdId = result.data.bookingId,
                    )
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                HotelWalkInViewModel(repository = HotelFolioRepository.get(context.applicationContext))
            }
        }
    }
}

/**
 * A walk-in sale — somebody who is not staying buys a meal, a ticket, a
 * laundry load.
 *
 * It is a booking with no room, bed or hall on it: the server forces it
 * confirmed, sets both dates to the day served, and counts no nights. So this
 * form asks only who it was, and on success opens the bill straight away,
 * because the bill is where what was sold goes on — as charges, by hand. The
 * form itself is replaced on the back stack; there is nothing to come back to.
 */
@Composable
fun HotelWalkInScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelWalkInViewModel = viewModel(
        factory = HotelWalkInViewModel.provideFactory(LocalContext.current),
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
    LaunchedEffect(state.createdId) {
        val id = state.createdId ?: return@LaunchedEffect
        navController.navigate(HotelMenu.folio(id)) {
            popUpTo(HotelMenu.ROUTE_WALK_IN) { inclusive = true }
        }
    }

    AuthenticatedShell(
        title = "Walk-in Sale",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "No room, no nights — a bill to put what was sold on. Record who it was, " +
                        "then add the charges on the bill that opens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                AppTextField(
                    value = state.bookerName,
                    onValueChange = viewModel::onName,
                    label = "Name",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.bookerMobile,
                    onValueChange = viewModel::onMobile,
                    label = "Mobile",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Phone,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.adults,
                        onValueChange = viewModel::onAdults,
                        label = "Adults",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        value = state.children,
                        onValueChange = viewModel::onChildren,
                        label = "Children",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
                AppTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = "Notes",
                    modifier = Modifier.fillMaxWidth(),
                    multiline = true,
                )
                PrimaryButton(
                    text = "Record the sale",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
