package com.example.cashbookbd.ui.hotel

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
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
import com.example.cashbookbd.data.repository.HotelBillOwnership
import com.example.cashbookbd.data.repository.HotelFolio
import com.example.cashbookbd.data.repository.HotelFolioLine
import com.example.cashbookbd.data.repository.HotelFolioPayment
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.data.repository.HotelFolioWrite
import com.example.cashbookbd.data.repository.HotelParty
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.data.repository.HotelTill
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the money is being taken — the server's three words. */
private val MONEY_PURPOSES = listOf("advance" to "Advance", "settlement" to "Settlement", "refund" to "Refund")

/** How it arrived. */
private val MONEY_METHODS = listOf(
    "cash" to "Cash",
    "bank" to "Bank",
    "card" to "Card",
    "mobile" to "Mobile",
    "adjustment" to "Adjustment",
)

/** Used only when the folio answers with no charge types of its own. */
private val FALLBACK_CHARGE_TYPES = listOf(
    SelectorOption("restaurant", "Restaurant"),
    SelectorOption("catering", "Catering"),
    SelectorOption("laundry", "Laundry"),
    SelectorOption("hall_rent", "Hall rent"),
    SelectorOption("ticket", "Ticket"),
    SelectorOption("other", "Other"),
)

enum class FolioDialog { BILL_NIGHTS, CHARGE, MONEY, DISCOUNT, TRANSFER }

data class FolioChargeDraft(
    val chargeType: String = "",
    val description: String = "",
    val quantity: String = "1",
    val unitRate: String = "",
    val date: SimpleDate = SimpleDate.today(),
)

data class FolioMoneyDraft(
    val purpose: String = "settlement",
    val amount: String = "",
    val method: String = "cash",
    val tillId: Long? = null,
    val date: SimpleDate = SimpleDate.today(),
    val reference: String = "",
    val notes: String = "",
)

data class FolioDiscountDraft(
    val rate: String = "",
    val amount: String = "",
    val reason: String = "",
)

data class FolioTransferDraft(
    val party: HotelParty? = null,
    /** Send it back to the guest instead of on to a company. */
    val backToGuest: Boolean = false,
    val reason: String = "",
    val date: SimpleDate = SimpleDate.today(),
)

data class HotelFolioUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val folio: HotelFolio? = null,
    /** Whose bill it is; null when that read failed, which only hides "Bill it to…". */
    val bill: HotelBillOwnership? = null,
    val tills: List<HotelTill> = emptyList(),
    val dialog: FolioDialog? = null,
    val charge: FolioChargeDraft = FolioChargeDraft(),
    val money: FolioMoneyDraft = FolioMoneyDraft(),
    val discount: FolioDiscountDraft = FolioDiscountDraft(),
    val transfer: FolioTransferDraft = FolioTransferDraft(),
    val isWorking: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelFolioViewModel(
    private val repository: HotelFolioRepository,
    private val hotelRepository: HotelRepository,
    private val bookingId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelFolioUiState())
    val uiState: StateFlow<HotelFolioUiState> = _uiState.asStateFlow()

    /** The last party search, so a picked option can be turned back into its party. */
    private var lastParties: List<HotelParty> = emptyList()

    init {
        load()
    }

    /**
     * The folio and whose bill it is, read together. The second failing is not
     * a failure of the screen: the bill still shows, only "Bill it to…" and the
     * carried banner have nothing to say.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val folio = async { repository.fetchFolio(bookingId) }
            val bill = async { repository.fetchBillOwnership(bookingId) }
            when (val result = folio.await()) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, folio = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
            (bill.await() as? Resource.Success)?.let { r -> _uiState.update { it.copy(bill = r.data) } }
        }
    }

    private fun reloadOwnership() {
        viewModelScope.launch {
            (repository.fetchBillOwnership(bookingId) as? Resource.Success)
                ?.let { r -> _uiState.update { it.copy(bill = r.data) } }
        }
    }

    // ---- Dialogs -----------------------------------------------------------

    fun open(dialog: FolioDialog) {
        val folio = _uiState.value.folio ?: return
        when (dialog) {
            FolioDialog.CHARGE -> {
                val first = folio.chargeTypes.firstOrNull()
                _uiState.update {
                    it.copy(
                        dialog = dialog,
                        charge = FolioChargeDraft(
                            chargeType = first?.code ?: FALLBACK_CHARGE_TYPES.first().id,
                            unitRate = first?.defaultRate?.takeIf { r -> r > 0 }?.let { r -> plain(r) }.orEmpty(),
                        ),
                    )
                }
            }
            FolioDialog.MONEY -> {
                _uiState.update {
                    it.copy(
                        dialog = dialog,
                        money = FolioMoneyDraft(
                            // What is owed is the usual answer; in credit, blank.
                            amount = if (folio.balance > 0) plain(folio.balance) else "",
                            tillId = it.tills.firstOrNull()?.id,
                        ),
                    )
                }
                if (_uiState.value.tills.isEmpty()) loadTills()
            }
            FolioDialog.DISCOUNT -> _uiState.update {
                it.copy(
                    dialog = dialog,
                    discount = FolioDiscountDraft(
                        rate = folio.booking.discountRate?.takeIf { r -> r > 0 }?.let { r -> hotelRate(r) }.orEmpty(),
                        amount = folio.booking.discountAmount?.takeIf { a -> a > 0 }?.let { a -> plain(a) }.orEmpty(),
                        reason = folio.booking.discountReason,
                    ),
                )
            }
            FolioDialog.TRANSFER -> _uiState.update { it.copy(dialog = dialog, transfer = FolioTransferDraft()) }
            FolioDialog.BILL_NIGHTS -> _uiState.update { it.copy(dialog = dialog) }
        }
    }

    fun close() {
        if (!_uiState.value.isWorking) _uiState.update { it.copy(dialog = null) }
    }

    fun onCharge(transform: (FolioChargeDraft) -> FolioChargeDraft) =
        _uiState.update { it.copy(charge = transform(it.charge)) }

    fun onMoney(transform: (FolioMoneyDraft) -> FolioMoneyDraft) =
        _uiState.update { it.copy(money = transform(it.money)) }

    fun onDiscount(transform: (FolioDiscountDraft) -> FolioDiscountDraft) =
        _uiState.update { it.copy(discount = transform(it.discount)) }

    fun onTransfer(transform: (FolioTransferDraft) -> FolioTransferDraft) =
        _uiState.update { it.copy(transfer = transform(it.transfer)) }

    private fun loadTills() {
        viewModelScope.launch {
            when (val result = repository.fetchTills()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        tills = result.data,
                        money = if (it.money.tillId == null) it.money.copy(tillId = result.data.firstOrNull()?.id) else it.money,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Writes ------------------------------------------------------------

    fun billNights() = write { repository.billNights(bookingId) }

    fun saveCharge() {
        val d = _uiState.value.charge
        val quantity = d.quantity.trim().toDoubleOrNull()
        val rate = d.unitRate.trim().toDoubleOrNull()
        when {
            d.chargeType.isBlank() -> return say("What kind of charge is it?")
            d.description.isBlank() -> return say("Say what was sold — it goes on the guest's bill.")
            quantity == null || quantity <= 0 -> return say("How many?")
            rate == null || rate < 0 -> return say("At what rate?")
        }
        write { repository.addCharge(bookingId, d.chargeType, d.description, quantity!!, rate!!, d.date.toApi()) }
    }

    fun saveMoney() {
        val d = _uiState.value.money
        val amount = d.amount.trim().toDoubleOrNull()
        val till = d.tillId
        when {
            amount == null || amount <= 0 -> return say("How much?")
            till == null -> return say("Which account did the money go into?")
        }
        write {
            repository.receiveMoney(
                bookingId, d.purpose, amount!!, d.method, d.date.toApi(), till!!, d.reference, d.notes,
            )
        }
    }

    fun saveDiscount() {
        val d = _uiState.value.discount
        val rate = d.rate.trim().toDoubleOrNull()
        val amount = d.amount.trim().toDoubleOrNull()
        when {
            (rate ?: 0.0) > 0 && (amount ?: 0.0) > 0 ->
                return say("Give the discount as a percentage or as an amount, not both")
            d.reason.isBlank() -> return say("Say why the discount was allowed — it goes on the record beside the figure.")
        }
        write { repository.giveDiscount(bookingId, rate, amount, d.reason) }
    }

    /** Both figures zero is how the server is told to take the discount off. */
    fun clearDiscount() {
        val d = _uiState.value.discount
        write { repository.giveDiscount(bookingId, 0.0, 0.0, d.reason.ifBlank { "Discount removed" }) }
    }

    fun saveTransfer() {
        val d = _uiState.value.transfer
        if (!d.backToGuest && d.party == null) return say("Who is the bill going to?")
        val toParty = if (d.backToGuest) null else d.party?.id
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val result = repository.transferBill(bookingId, toParty, d.reason, d.date.toApi())) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isWorking = false, dialog = null, message = result.data) }
                    load()
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

    /**
     * Every folio write answers with the whole folio again, so the screen never
     * asks twice — and whose bill it is is re-read beside it, because taking
     * money changes what is outstanding.
     */
    private fun write(block: suspend () -> Resource<HotelFolioWrite>) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val result = block()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            dialog = null,
                            message = result.data.message,
                            folio = result.data.folio ?: it.folio,
                        )
                    }
                    if (result.data.folio == null) load() else reloadOwnership()
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

    /** The company's customer list, for "Bill it to…". */
    suspend fun searchParties(query: String): Resource<List<SelectorOption>> =
        when (val result = hotelRepository.searchParties(query)) {
            is Resource.Success -> {
                lastParties = result.data
                Resource.Success(result.data.map { it.asOption() })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Success(emptyList())
        }

    fun onPartyPicked(option: SelectorOption) {
        val party = lastParties.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update { it.copy(transfer = it.transfer.copy(party = party, backToGuest = false)) }
    }

    private fun say(text: String) = _uiState.update { it.copy(message = text) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /** A figure for an input box: no grouping, no trailing zeros. */
    private fun plain(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else value.toString()

    companion object {
        fun provideFactory(context: Context, bookingId: Long) = viewModelFactory {
            initializer {
                HotelFolioViewModel(
                    repository = HotelFolioRepository.get(context.applicationContext),
                    hotelRepository = ServiceLocator.provideHotelRepository(context.applicationContext),
                    bookingId = bookingId,
                )
            }
        }
    }
}

internal fun HotelParty.asOption(): SelectorOption =
    SelectorOption(id.toString(), if (mobile.isBlank()) name else "$name · $mobile")

/**
 * The folio — the guest's bill, and the money against it.
 *
 * What the desk asks of it is one question in three parts: what has been
 * charged, what has been paid, and what is owed. Those three are the tiles;
 * everything below is how they got there. Nights go on through one button so
 * a night can never be charged twice; a meal or a ticket goes on by hand; money
 * comes in against a named till and out only as a refund the server checks
 * against what was taken. Every write answers with the bill again, and every
 * refusal is the server's own sentence.
 */
@Composable
fun HotelFolioScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    modifier: Modifier = Modifier,
    viewModel: HotelFolioViewModel = viewModel(
        factory = HotelFolioViewModel.provideFactory(LocalContext.current, bookingId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    // Seeing the bill and touching it are different permissions: the desk reads
    // it, the cashier bills it, and moving it to a company or ending the stay
    // are each their own.
    val canBill = Permissions.hasAny(sessionState.permissions, listOf("hotel.folio.bill"))
    val canCheckOut = Permissions.hasAny(sessionState.permissions, listOf("hotel.booking.checkout"))
    val canTransfer = Permissions.hasAny(sessionState.permissions, listOf("hotel.booking.transfer"))

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
        title = "Bill",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.folio == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.folio == null -> Column(
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

                else -> state.folio?.let { folio ->
                    FolioBody(
                        folio = folio,
                        bill = state.bill,
                        canBill = canBill,
                        canCheckOut = canCheckOut,
                        canTransfer = canTransfer,
                        isWorking = state.isWorking,
                        onOpen = viewModel::open,
                        onPrintBill = { navController.navigate(HotelMenu.billPaper(bookingId)) },
                        onPrintReceipt = { paymentId -> navController.navigate(HotelMenu.billPaper(bookingId, paymentId)) },
                        onCheckOut = { navController.navigate(HotelMenu.checkOut(bookingId)) },
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    FolioDialogs(state = state, viewModel = viewModel, context = context)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolioBody(
    folio: HotelFolio,
    bill: HotelBillOwnership?,
    canBill: Boolean,
    canCheckOut: Boolean,
    canTransfer: Boolean,
    isWorking: Boolean,
    onOpen: (FolioDialog) -> Unit,
    onPrintBill: () -> Unit,
    onPrintReceipt: (Long) -> Unit,
    onCheckOut: () -> Unit,
) {
    val booking = folio.booking
    val totals = folio.totals
    val chartReady = folio.chartMissing.isEmpty()
    val closed = booking.status == "checked_out" || booking.status == "cancelled"
    val hasDiscount = totals.discount > 0

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
                    text = if (booking.isWalkIn) {
                        "Walk-in, no room · ${hotelDate(booking.checkInDate)}"
                    } else {
                        "${hotelDate(booking.checkInDate)} → ${hotelDate(booking.checkOutDate)}" +
                            if (booking.nights > 0) " · ${booking.nights} night${if (booking.nights == 1) "" else "s"}" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }

        // The chart complaint first: nothing below it can move money until it is fixed.
        if (!chartReady) {
            item {
                HotelBanner(
                    text = "The chart of accounts is not ready, so no money can be taken." +
                        if (folio.chartMissing.isNotEmpty()) " Missing: ${folio.chartMissing.joinToString(", ")}." else "",
                    color = MaterialTheme.appColors.danger,
                )
            }
        }
        if (bill?.carried == true) {
            item {
                HotelBanner(
                    text = "This bill is ${bill.owedByName}'s. ${hotelMoney(bill.outstanding)} outstanding on their account. " +
                        "Money taken from here settles them, not the guest.",
                    color = MaterialTheme.appColors.info,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HotelMoneyTile(label = "Charged", value = hotelMoney(totals.rounded), modifier = Modifier.weight(1f))
                HotelMoneyTile(label = "Paid", value = hotelMoney(folio.paid), modifier = Modifier.weight(1f))
                // Negative is the ordinary state of a booking with an advance and
                // no nights billed yet — it must read as "in hand", not as a debt.
                HotelMoneyTile(
                    label = if (folio.balance < 0) "In hand" else "Owed",
                    value = hotelMoney(Math.abs(folio.balance)),
                    valueColor = if (folio.balance > 0) MaterialTheme.appColors.danger else MaterialTheme.appColors.success,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { WorkingLine(folio) }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canBill && !booking.isWalkIn) {
                    PrimaryButton(
                        text = if (folio.unbilledNights > 0) {
                            "Bill ${folio.unbilledNights} night${if (folio.unbilledNights == 1) "" else "s"}"
                        } else {
                            "Nights all billed"
                        },
                        onClick = { onOpen(FolioDialog.BILL_NIGHTS) },
                        enabled = folio.unbilledNights > 0 && chartReady && !isWorking,
                        compact = true,
                    )
                }
                if (canBill) {
                    SecondaryButton(
                        text = "Add a charge",
                        onClick = { onOpen(FolioDialog.CHARGE) },
                        enabled = !isWorking && !closed,
                        compact = true,
                    )
                    SecondaryButton(
                        text = "Take money",
                        onClick = { onOpen(FolioDialog.MONEY) },
                        enabled = !isWorking && chartReady,
                        compact = true,
                    )
                    if (!closed && folio.canDiscount) {
                        SecondaryButton(
                            text = if (hasDiscount) "Discount…" else "Give a discount",
                            onClick = { onOpen(FolioDialog.DISCOUNT) },
                            enabled = !isWorking,
                            compact = true,
                        )
                    }
                }
                SecondaryButton(
                    text = "Print the bill",
                    onClick = onPrintBill,
                    enabled = folio.lines.isNotEmpty(),
                    compact = true,
                )
                if (canCheckOut && booking.status == "checked_in") {
                    SecondaryButton(text = "Check out", onClick = onCheckOut, enabled = !isWorking, compact = true)
                }
                if (canTransfer && bill != null && bill.outstanding > 0) {
                    SecondaryButton(
                        text = "Bill it to…",
                        onClick = { onOpen(FolioDialog.TRANSFER) },
                        enabled = !isWorking && !closed,
                        compact = true,
                    )
                }
            }
        }

        item { HotelSectionTitle("The bill") }
        item {
            if (folio.lines.isEmpty()) {
                Text(
                    text = if (booking.isWalkIn) {
                        "Nothing sold yet — add what was sold as a charge."
                    } else {
                        "Nothing on the bill yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            } else {
                ReportTable(
                    columns = lineColumns(),
                    data = foldBill(folio.lines),
                    scrollable = false,
                    noDataMessage = "Nothing on the bill yet.",
                )
            }
        }
        if (hasDiscount || booking.discountReason.isNotBlank()) {
            item {
                Text(
                    text = buildString {
                        append("Discount ")
                        if (totals.discountRate > 0) append(hotelRate(totals.discountRate)).append("% — ")
                        append(hotelMoney(totals.discount))
                        if (booking.discountReason.isNotBlank()) append(" · ").append(booking.discountReason)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item { HotelSectionTitle("Money", modifier = Modifier.padding(top = 6.dp)) }
        item {
            if (folio.payments.isEmpty()) {
                Text(
                    text = "No money taken yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            } else {
                ReportTable(
                    columns = paymentColumns(onPrintReceipt),
                    data = folio.payments,
                    scrollable = false,
                    noDataMessage = "No money taken yet.",
                )
            }
        }
        if (folio.unpostedRows > 0) {
            item {
                Text(
                    text = "${folio.unpostedRows} row${if (folio.unpostedRows == 1) " is" else "s are"} not in the books yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.warning,
                )
            }
        }
        if (booking.notes.isNotBlank()) {
            item {
                Text(
                    text = "Notes: ${booking.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }
    }
}

/**
 * How the figure was arrived at, in one line — each part only when it is not
 * zero, so a bill with no service charge does not say "service 0% 0.00".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkingLine(folio: HotelFolio) {
    val t = folio.totals
    val parts = mutableListOf<Pair<String, Boolean>>()
    if (t.base != 0.0) parts += "Charges ${hotelMoney(t.base)}" to false
    if (t.serviceCharge != 0.0) parts += "service ${hotelRate(t.serviceChargeRate)}% ${hotelMoney(t.serviceCharge)}" to false
    if (t.vat != 0.0) {
        parts += if (t.vatBands.size > 1) {
            "VAT " + t.vatBands.joinToString(" · ") { "${hotelRate(it.rate)}% ${hotelMoney(it.vat)}" } to false
        } else {
            val rate = t.vatBands.firstOrNull()?.rate
            "VAT ${rate?.let { hotelRate(it) + "% " }.orEmpty()}${hotelMoney(t.vat)}" to false
        }
    }
    if (t.gross != t.base) parts += "gross ${hotelMoney(t.gross)}" to false
    if (t.discount != 0.0) {
        parts += "less discount ${if (t.discountRate > 0) hotelRate(t.discountRate) + "% " else ""}${hotelMoney(t.discount)}" to true
    }
    if (t.rounding != 0.0 || t.net != t.rounded) parts += "rounded ${hotelMoney(t.net)} → ${hotelMoney(t.rounded)}" to false
    if (parts.isEmpty()) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { index, (text, primary) ->
            Text(
                text = if (index == 0) text else "· $text",
                style = MaterialTheme.typography.labelSmall,
                color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.textOnScreenMuted,
            )
        }
    }
}


@Composable
private fun lineColumns(): List<ReportColumn<HotelFolioLine>> {
    val amber = MaterialTheme.appColors.warning
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(36.dp), TextAlign.Center) { r, _ -> cellText(r.lineNo.toString()) },
        ReportColumn("DESCRIPTION", ReportColWidth.Fixed(220.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(text = r.description.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    Text(
                        text = r.chargeType.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        },
        ReportColumn("QTY", ReportColWidth.Fixed(56.dp), TextAlign.End) { r, _ ->
            cellText(if (r.quantity == Math.rint(r.quantity)) r.quantity.toLong().toString() else r.quantity.toString())
        },
        ReportColumn("RATE", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ -> cellText(hotelMoney(r.unitRate)) },
        ReportColumn("BASE", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(hotelMoney(r.baseAmount)) },
        ReportColumn("SC", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
            cellText(if (r.serviceChargeAmount == 0.0) "—" else hotelMoney(r.serviceChargeAmount))
        },
        ReportColumn("VAT", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
            cellText(if (r.vatAmount == 0.0) "—" else hotelMoney(r.vatAmount))
        },
        ReportColumn("TOTAL", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
            cellText(hotelMoney(r.lineTotal), bold = true)
        },
        ReportColumn("VOUCHER", ReportColWidth.Fixed(120.dp)) { r, _ ->
            if (r.vrNo.isNotBlank()) {
                cellText(r.vrNo, maxLines = 2)
            } else {
                ReportTableCell.Slot {
                    Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                        HotelPill(text = "not posted", color = amber)
                    }
                }
            }
        },
    )
}

@Composable
private fun paymentColumns(onPrintReceipt: (Long) -> Unit): List<ReportColumn<HotelFolioPayment>> {
    val amber = MaterialTheme.appColors.warning
    val danger = MaterialTheme.appColors.danger
    val primary = MaterialTheme.colorScheme.primary
    return listOf(
        ReportColumn("DATE", ReportColWidth.Fixed(90.dp)) { r, _ -> cellText(hotelDate(r.paymentDate).ifBlank { "—" }) },
        ReportColumn("RECEIPT", ReportColWidth.Fixed(130.dp)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(text = r.paymentNo.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    LinkButton(text = "Print", onClick = { onPrintReceipt(r.id) })
                }
            }
        },
        ReportColumn("PURPOSE", ReportColWidth.Fixed(100.dp)) { r, _ ->
            ReportTableCell.Slot {
                Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    HotelPill(
                        text = r.purpose.replaceFirstChar { it.uppercase() },
                        color = if (r.isRefund) danger else primary,
                    )
                }
            }
        },
        ReportColumn("METHOD", ReportColWidth.Fixed(90.dp)) { r, _ ->
            cellText(r.method.replaceFirstChar { it.uppercase() }.ifBlank { "—" })
        },
        ReportColumn("AMOUNT", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
            if (r.isRefund) {
                cellText("−${hotelMoney(r.amount)}", color = danger, bold = true)
            } else {
                cellText(hotelMoney(r.amount), bold = true)
            }
        },
        ReportColumn("REFERENCE", ReportColWidth.Fixed(120.dp)) { r, _ ->
            cellText(listOf(r.reference, r.notes).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }, maxLines = 2)
        },
        ReportColumn("VOUCHER", ReportColWidth.Fixed(120.dp)) { r, _ ->
            if (r.vrNo.isNotBlank()) {
                cellText(r.vrNo, maxLines = 2)
            } else {
                ReportTableCell.Slot {
                    Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                        HotelPill(text = "not posted", color = amber)
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
//  Dialogs — every real-money act sits behind one
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolioDialogs(state: HotelFolioUiState, viewModel: HotelFolioViewModel, context: Context) {
    val folio = state.folio ?: return
    val dialog = state.dialog ?: return
    val working = state.isWorking

    when (dialog) {
        FolioDialog.BILL_NIGHTS -> AlertDialog(
            onDismissRequest = viewModel::close,
            title = { Text("Bill the nights") },
            text = {
                Text(
                    "Put ${folio.unbilledNights} night${if (folio.unbilledNights == 1) "" else "s"} on the bill, " +
                        "at the rates in force on each night? This posts the room rent to the ledger.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Bill ${folio.unbilledNights} night${if (folio.unbilledNights == 1) "" else "s"}",
                    onClick = viewModel::billNights,
                    enabled = !working,
                    isLoading = working,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Not now", onClick = viewModel::close, enabled = !working) },
        )

        FolioDialog.CHARGE -> {
            val d = state.charge
            val options = folio.chargeTypes
                .map { SelectorOption(it.code, it.name.ifBlank { it.code.replace('_', ' ') }) }
                .ifEmpty { FALLBACK_CHARGE_TYPES }
            AlertDialog(
                onDismissRequest = viewModel::close,
                title = { Text("Add a charge") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Room rent goes on through Bill the nights, never by hand — " +
                                "otherwise a night can be charged twice.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                        AppSelectDropdown(
                            label = "Charge",
                            options = options,
                            selected = options.firstOrNull { it.id == d.chargeType },
                            onSelected = { option ->
                                val rate = folio.chargeTypes.firstOrNull { it.code == option.id }?.defaultRate
                                viewModel.onCharge {
                                    it.copy(
                                        chargeType = option.id,
                                        unitRate = if (it.unitRate.isBlank() && rate != null && rate > 0) {
                                            if (rate == Math.rint(rate)) rate.toLong().toString() else rate.toString()
                                        } else {
                                            it.unitRate
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = d.description,
                            onValueChange = { v -> viewModel.onCharge { it.copy(description = v) } },
                            label = "What was sold",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = d.quantity,
                                onValueChange = { v -> viewModel.onCharge { it.copy(quantity = v) } },
                                label = "Qty",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                            )
                            AppTextField(
                                value = d.unitRate,
                                onValueChange = { v -> viewModel.onCharge { it.copy(unitRate = v) } },
                                label = "Rate",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                            )
                        }
                        PickerField(
                            label = "Date",
                            value = d.date.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pickMoneyDate(context, d.date) { picked -> viewModel.onCharge { it.copy(date = picked) } } },
                        )
                        val qty = d.quantity.toDoubleOrNull() ?: 0.0
                        val rate = d.unitRate.toDoubleOrNull() ?: 0.0
                        if (qty > 0 && rate > 0) {
                            Text(
                                text = "Base ${AmountFormat.format(qty * rate)} — service charge and VAT are added by the server.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                        }
                    }
                },
                confirmButton = {
                    PrimaryButton(text = "Add", onClick = viewModel::saveCharge, enabled = !working, isLoading = working, compact = true)
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::close, enabled = !working) },
            )
        }

        FolioDialog.MONEY -> {
            val d = state.money
            val tillOptions = state.tills.map { SelectorOption(it.id.toString(), it.label) }
            AlertDialog(
                onDismissRequest = viewModel::close,
                title = { Text("Take money") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MONEY_PURPOSES.forEach { (value, label) ->
                                FilterChip(
                                    selected = d.purpose == value,
                                    onClick = { viewModel.onMoney { it.copy(purpose = value) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (d.purpose == "refund") {
                            Text(
                                text = "A refund cannot exceed what this booking holds (${hotelMoney(folio.paid)}).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.danger,
                            )
                        } else if (folio.booking.status == "hold") {
                            Text(
                                text = "Money on a hold confirms it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                        }
                        AppTextField(
                            value = d.amount,
                            onValueChange = { v -> viewModel.onMoney { it.copy(amount = v) } },
                            label = "Amount",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardType.Decimal,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MONEY_METHODS.forEach { (value, label) ->
                                FilterChip(
                                    selected = d.method == value,
                                    onClick = { viewModel.onMoney { it.copy(method = value) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        AppSelectDropdown(
                            label = "Into which account",
                            options = tillOptions,
                            selected = tillOptions.firstOrNull { it.id == d.tillId?.toString() },
                            onSelected = { option -> viewModel.onMoney { it.copy(tillId = option.id.toLongOrNull()) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = if (state.tills.isEmpty()) "No cash or bank account set up" else "Pick one",
                        )
                        PickerField(
                            label = "Date",
                            value = d.date.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pickMoneyDate(context, d.date) { picked -> viewModel.onMoney { it.copy(date = picked) } } },
                        )
                        AppTextField(
                            value = d.reference,
                            onValueChange = { v -> viewModel.onMoney { it.copy(reference = v.take(100)) } },
                            label = "Reference (cheque, transaction id)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = d.notes,
                            onValueChange = { v -> viewModel.onMoney { it.copy(notes = v.take(255)) } },
                            label = "Notes",
                            modifier = Modifier.fillMaxWidth(),
                            multiline = true,
                        )
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = if (d.purpose == "refund") "Refund" else "Receive",
                        onClick = viewModel::saveMoney,
                        enabled = !working && state.tills.isNotEmpty(),
                        isLoading = working,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::close, enabled = !working) },
            )
        }

        FolioDialog.DISCOUNT -> {
            val d = state.discount
            val hasOne = folio.totals.discount > 0
            AlertDialog(
                onDismissRequest = viewModel::close,
                title = { Text(if (hasOne) "Discount" else "Give a discount") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Off the gross — charges plus service and VAT. A percentage follows the bill; " +
                                "an amount is the amount. One or the other, not both.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = d.rate,
                                onValueChange = { v -> viewModel.onDiscount { it.copy(rate = v, amount = if (v.isNotBlank()) "" else it.amount) } },
                                label = "Percent",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                            )
                            AppTextField(
                                value = d.amount,
                                onValueChange = { v -> viewModel.onDiscount { it.copy(amount = v, rate = if (v.isNotBlank()) "" else it.rate) } },
                                label = "Amount",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                            )
                        }
                        AppTextField(
                            value = d.reason,
                            onValueChange = { v -> viewModel.onDiscount { it.copy(reason = v.take(255)) } },
                            label = "Why it was allowed",
                            modifier = Modifier.fillMaxWidth(),
                            caption = "Kept on the booking beside the figure, with who allowed it.",
                            multiline = true,
                        )
                        if (hasOne) {
                            LinkButton(
                                text = "Remove the discount",
                                onClick = viewModel::clearDiscount,
                                enabled = !working,
                                color = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                },
                confirmButton = {
                    PrimaryButton(text = "Allow", onClick = viewModel::saveDiscount, enabled = !working, isLoading = working, compact = true)
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::close, enabled = !working) },
            )
        }

        FolioDialog.TRANSFER -> {
            val d = state.transfer
            val bill = state.bill
            AlertDialog(
                onDismissRequest = viewModel::close,
                title = { Text("Bill it to…") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (bill != null) {
                            Text(
                                text = "This bill is ${bill.owedByName}'s. ${hotelMoney(bill.outstanding)} outstanding.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = bill.note.ifBlank {
                                    "Only what is still outstanding moves. Money already received stays with whoever paid it."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                        }
                        if (bill?.carried == true) {
                            FilterChip(
                                selected = d.backToGuest,
                                onClick = { viewModel.onTransfer { it.copy(backToGuest = !it.backToGuest, party = null) } },
                                label = { Text("Back to the guest") },
                            )
                        }
                        if (!d.backToGuest) {
                            SearchableSelectDropdown(
                                selected = d.party?.asOption(),
                                onSelected = viewModel::onPartyPicked,
                                search = viewModel::searchParties,
                                modifier = Modifier.fillMaxWidth(),
                                label = "Company or party",
                                placeholder = "Type a name…",
                                emptyText = "Nobody on the customer list by that name",
                            )
                        }
                        AppTextField(
                            value = d.reason,
                            onValueChange = { v -> viewModel.onTransfer { it.copy(reason = v.take(255)) } },
                            label = "Reason",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PickerField(
                            label = "Date",
                            value = d.date.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pickMoneyDate(context, d.date) { picked -> viewModel.onTransfer { it.copy(date = picked) } } },
                        )
                        if (bill != null && bill.history.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Before now", style = MaterialTheme.typography.labelMedium, fontWeight = AppFontWeight.SemiBold)
                            bill.history.forEach { h ->
                                Text(
                                    text = "${hotelDate(h.date)} · ${hotelMoney(h.amount)} · ${h.from} → ${h.to}" +
                                        if (h.reason.isNotBlank()) " · ${h.reason}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.textMuted,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = bill?.let { "Move ${hotelMoney(it.outstanding)}" } ?: "Move",
                        onClick = viewModel::saveTransfer,
                        enabled = !working && (d.backToGuest || d.party != null),
                        isLoading = working,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::close, enabled = !working) },
            )
        }
    }
}
