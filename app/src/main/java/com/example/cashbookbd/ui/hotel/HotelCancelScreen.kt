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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.data.repository.HotelCancellation
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
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

data class HotelCancelUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: HotelCancellation? = null,
    val reason: String = "",
    val refund: String = "",
    val tillId: Long? = null,
    val cancelledOn: SimpleDate = SimpleDate.today(),
    val confirm: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val done: Boolean = false,
) {
    val refundAmount: Double get() = refund.trim().toDoubleOrNull() ?: 0.0
    val retained: Double get() = maxOf(0.0, (info?.amountHeld ?: 0.0) - refundAmount)
}

class HotelCancelViewModel(
    private val repository: HotelFolioRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelCancelUiState())
    val uiState: StateFlow<HotelCancelUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchCancellation(bookingId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        info = result.data,
                        tillId = it.tillId ?: result.data.tills.firstOrNull()?.id,
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

    fun onReason(v: String) = _uiState.update { it.copy(reason = v.take(255)) }
    fun onRefund(v: String) = _uiState.update { it.copy(refund = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onTill(option: SelectorOption) = _uiState.update { it.copy(tillId = option.id.toLongOrNull()) }
    fun onDate(date: SimpleDate) = _uiState.update { it.copy(cancelledOn = date) }

    /** The guards the server would answer with, said before the dialog rather than after. */
    fun askConfirm() {
        val s = _uiState.value
        val info = s.info ?: return
        val refund = s.refundAmount
        when {
            s.refund.isNotBlank() && s.refund.trim().toDoubleOrNull() == null -> return say("The refund is not a figure.")
            refund > info.amountHeld + 0.005 ->
                return say("This booking has only ${hotelMoney(info.amountHeld)} against it — a refund cannot exceed it.")
            refund > 0 && s.tillId == null -> return say("Name the cash or bank account the refund is paid from")
        }
        _uiState.update { it.copy(confirm = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirm = false) }

    fun post() {
        val s = _uiState.value
        if (s.isWorking) return
        _uiState.update { it.copy(isWorking = true, confirm = false) }
        viewModelScope.launch {
            val result = repository.cancelBooking(
                bookingId = bookingId,
                reason = s.reason,
                refundAmount = s.refundAmount,
                coa4Id = s.tillId,
                cancelledOn = s.cancelledOn.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isWorking = false, message = result.data, done = true) }
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

    private fun say(text: String) = _uiState.update { it.copy(message = text) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, bookingId: Long) = viewModelFactory {
            initializer {
                HotelCancelViewModel(
                    repository = HotelFolioRepository.get(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

/**
 * Cancellation — the nights go back, and the money is settled.
 *
 * Whatever the booking holds is split in two and neither half is optional:
 * what is refunded goes back from a named till, and what is retained is
 * cancellation income — kept in the advance head it would sit there for ever
 * pretending to be somebody's money. So the retained figure is shown live
 * beside the refund, and a refund needs the cashier's permission on top of
 * the right to cancel. A booking already billed cannot be cancelled at all:
 * the guest has been charged and the VAT has fallen due, and that stay is
 * checked out instead.
 */
@Composable
fun HotelCancelScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelCancelViewModel = viewModel(
        factory = HotelCancelViewModel.provideFactory(LocalContext.current, bookingId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    // Paying money OUT of the till is the cashier's permission, the same one
    // that guards the till on the folio. A clerk with only the right to cancel
    // can still call off a booking nobody has paid on.
    val canRefund = Permissions.hasAny(sessionState.permissions, listOf("hotel.folio.bill"))

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
        title = "Cancel Booking",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.info == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.info == null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }

                else -> state.info?.let { info ->
                    CancelBody(state = state, info = info, canRefund = canRefund, context = context, viewModel = viewModel)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.confirm && state.info != null) {
        val info = state.info!!
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text("Cancel ${info.booking.bookingNo}?") },
            text = {
                Text(
                    buildString {
                        append("${info.nightsHeld} night${if (info.nightsHeld == 1) "" else "s"} go back on sale.")
                        if (info.amountHeld > 0) {
                            append(" Of the ${hotelMoney(info.amountHeld)} held, ")
                            append(hotelMoney(state.refundAmount)).append(" is refunded and ")
                            append(hotelMoney(state.retained)).append(" is retained as cancellation income.")
                        }
                        append(" This cannot be undone.")
                    }
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Cancel the booking",
                    onClick = viewModel::post,
                    enabled = !state.isWorking,
                    isLoading = state.isWorking,
                    compact = true,
                    containerColor = MaterialTheme.appColors.danger,
                )
            },
            dismissButton = { LinkButton(text = "Keep it", onClick = viewModel::dismissConfirm, enabled = !state.isWorking) },
        )
    }
}

@Composable
private fun CancelBody(
    state: HotelCancelUiState,
    info: HotelCancellation,
    canRefund: Boolean,
    context: Context,
    viewModel: HotelCancelViewModel,
) {
    val booking = info.booking
    val muted = MaterialTheme.appColors.textMuted
    val billed = info.billedLines > 0
    val alreadyOver = booking.status == "cancelled" || booking.status == "checked_out"
    val tillOptions = info.tills.map { SelectorOption(it.id.toString(), it.label) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = booking.bookingNo.ifBlank { "Booking #${booking.id}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    HotelStatusChip(status = booking.status)
                }
                Text(
                    text = listOf(booking.bookerName, booking.bookerMobile).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${hotelDate(booking.checkInDate)} → ${hotelDate(booking.checkOutDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }

        when {
            booking.status == "cancelled" -> item {
                HotelBanner(text = "That booking is already cancelled.", color = MaterialTheme.appColors.info)
            }
            booking.status == "checked_out" -> item {
                HotelBanner(text = "A booking that has been checked out cannot be cancelled.", color = MaterialTheme.appColors.info)
            }
            billed -> item {
                HotelBanner(
                    text = "This booking has already been billed, so it cannot be cancelled — the guest has been " +
                        "charged and the VAT has fallen due. Check it out instead, and settle or carry what is owed.",
                    color = MaterialTheme.appColors.danger,
                )
            }
        }
        if (info.chartMissing.isNotEmpty() && info.amountHeld > 0) {
            item {
                HotelBanner(
                    text = "The chart of accounts is not ready, so the money held cannot be settled. " +
                        "Missing: ${info.chartMissing.joinToString(", ")}.",
                    color = MaterialTheme.appColors.danger,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HotelMoneyTile(label = "Nights held", value = info.nightsHeld.toString(), modifier = Modifier.weight(1f))
                HotelMoneyTile(label = "Money held", value = hotelMoney(info.amountHeld), modifier = Modifier.weight(1f))
                HotelMoneyTile(
                    label = "On the bill",
                    value = if (info.billedLines == 0) "—" else "${info.billedLines} line${if (info.billedLines == 1) "" else "s"}",
                    valueColor = if (billed) MaterialTheme.appColors.danger else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!billed && !alreadyOver) {
            item {
                PickerField(
                    label = "Cancelled on",
                    value = state.cancelledOn.toDisplay(),
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickMoneyDate(context, state.cancelledOn, viewModel::onDate) },
                )
            }
            item {
                AppTextField(
                    value = state.reason,
                    onValueChange = viewModel::onReason,
                    label = "Why",
                    modifier = Modifier.fillMaxWidth(),
                    caption = "Kept on the booking, with who cancelled it.",
                    multiline = true,
                )
            }
            if (info.amountHeld > 0) {
                item { HotelSectionTitle("The money") }
                if (canRefund) {
                    item {
                        AppTextField(
                            value = state.refund,
                            onValueChange = viewModel::onRefund,
                            label = "Refund",
                            modifier = Modifier.fillMaxWidth(),
                            caption = "Never more than the ${hotelMoney(info.amountHeld)} held. Leave at zero to keep it all.",
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    if (state.refundAmount > 0) {
                        item {
                            AppSelectDropdown(
                                label = "Paid from",
                                options = tillOptions,
                                selected = tillOptions.firstOrNull { it.id == state.tillId?.toString() },
                                onSelected = viewModel::onTill,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = if (tillOptions.isEmpty()) "No cash or bank account set up" else "Pick one",
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Refunding needs the cashier's permission; cancelling now retains the whole " +
                                "${hotelMoney(info.amountHeld)} as cancellation income.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.warning,
                        )
                    }
                }
                item {
                    // Said live, because the retained half is income and the
                    // clerk should see what they are keeping before they press.
                    Text(
                        text = "Retained as cancellation income: ${hotelMoney(state.retained)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = AppFontWeight.SemiBold,
                        color = if (state.retained > 0) MaterialTheme.colorScheme.primary else muted,
                    )
                }
            }
            item {
                PrimaryButton(
                    text = "Cancel the booking",
                    onClick = viewModel::askConfirm,
                    enabled = !state.isWorking && !state.isLoading,
                    isLoading = state.isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    containerColor = MaterialTheme.appColors.danger,
                )
            }
        }
    }
}
