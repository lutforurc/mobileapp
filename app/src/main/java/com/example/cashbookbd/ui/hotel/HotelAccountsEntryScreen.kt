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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The hotel's Payment Entry and Received Entry (web b255a96e / a41123c5):
 * mirrors of each other, so one screen with the differences here.
 */
enum class HotelEntryKind(
    val heading: String,
    val subheading: String,
    val endpoint: String,
    val amountLabel: String,
    val rowNoteLabel: String,
    val rowNotePlaceholder: String,
    val detailsTitle: String,
    val summaryTitle: String,
    val methodLabel: String,
    val sideLabel: String,
    val totalLabel: String,
    val saveLabel: String,
    val bankMissing: String,
    val headMissing: String,
    val amountMissing: String,
    val notesPlaceholder: String,
    /** Payment Entry keeps the clerk's own handwritten number. */
    val manualNumber: Boolean,
) {
    PAYMENT(
        heading = "Payment Entry", subheading = "Accounts Payment",
        endpoint = "hotel-setup/payment-entries/store",
        amountLabel = "Amount (Tk.) *", rowNoteLabel = "Notes", rowNotePlaceholder = "Optional",
        detailsTitle = "Payment Details", summaryTitle = "Payment Summary",
        methodLabel = "Payment Method", sideLabel = "Payment From",
        totalLabel = "Total Payment (Tk.)", saveLabel = "Save Payment",
        bankMissing = "Select the bank account paid from.",
        headMissing = "select the account head the money is paid to.",
        amountMissing = "enter the payment amount.",
        notesPlaceholder = "Optional note for the payment",
        manualNumber = true,
    ),
    RECEIVED(
        heading = "Received Entry", subheading = "Accounts Receipt",
        endpoint = "hotel-setup/received-entries/store",
        amountLabel = "Received Amount (Tk.) *", rowNoteLabel = "Received From", rowNotePlaceholder = "Who paid (optional)",
        detailsTitle = "Received Details", summaryTitle = "Received Summary",
        methodLabel = "Received Method", sideLabel = "Received To",
        totalLabel = "Total Received (Tk.)", saveLabel = "Save Received",
        bankMissing = "Select the bank account the money is received into.",
        headMissing = "select the account head the money came in against.",
        amountMissing = "enter the received amount.",
        notesPlaceholder = "Optional note for the receipt",
        manualNumber = false,
    ),
}

data class HotelEntryRow(val key: Int, val account: LedgerDropdownItem? = null, val amount: String = "", val remarks: String = "")

private val MODES = listOf(SelectorOption("cash", "Cash"), SelectorOption("bank", "Bank"))

data class HotelEntryUiState(
    val kind: HotelEntryKind,
    val mode: SelectorOption = MODES.first(),
    val rows: List<HotelEntryRow> = listOf(HotelEntryRow(1)),
    val nextKey: Int = 2,
    val banks: List<SelectorOption> = emptyList(),
    val bank: SelectorOption? = null,
    val manualNo: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    /** Bumped on reset so the head pickers clear what they show. */
    val resetKey: Int = 0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isCash: Boolean get() = mode.id == "cash"
    val total: Double get() = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
}

/**
 * Several heads, one real voucher: a line per head and a single cash or bank
 * line for the money that moved, so it lands in the ledger, the Cash Book and
 * the Bank Book like any other payment or receipt. A row is all or nothing —
 * a head with no amount, or an amount with no head, is refused before the
 * server is asked. The two sides balance by construction.
 */
class HotelEntryViewModel(
    kind: HotelEntryKind,
    private val api: WebApi,
    private val ledgers: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HotelEntryUiState(kind = kind))
    val uiState: StateFlow<HotelEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            (api.get("coal3/l4-list/2") as? Resource.Success)?.let { r ->
                val banks = r.data.jRows().map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                // Most companies keep one bank; picking it saves the clerk a tap.
                _uiState.update { it.copy(banks = banks, bank = banks.singleOrNull()) }
            }
        }
    }

    suspend fun searchHeads(q: String) = ledgers.searchLedgers(q, acType = "")

    fun onMode(o: SelectorOption) = _uiState.update { it.copy(mode = o) }
    fun onBank(o: SelectorOption) = _uiState.update { it.copy(bank = o) }
    fun onManualNo(v: String) = _uiState.update { it.copy(manualNo = v) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }
    fun addRow() = _uiState.update { it.copy(rows = it.rows + HotelEntryRow(it.nextKey), nextKey = it.nextKey + 1) }

    /** Never the last one: the voucher always has a line. */
    fun removeRow(key: Int) = _uiState.update { if (it.rows.size <= 1) it else it.copy(rows = it.rows.filterNot { r -> r.key == key }) }

    fun updateRow(key: Int, change: (HotelEntryRow) -> HotelEntryRow) =
        _uiState.update { it.copy(rows = it.rows.map { r -> if (r.key == key) change(r) else r }) }

    fun reset() = _uiState.update {
        it.copy(
            mode = MODES.first(), rows = listOf(HotelEntryRow(it.nextKey)), nextKey = it.nextKey + 1,
            bank = it.banks.singleOrNull(), manualNo = "", notes = "", resetKey = it.resetKey + 1,
        )
    }

    fun save() {
        val s = _uiState.value
        val kind = s.kind
        val error = when {
            !s.isCash && s.bank == null -> kind.bankMissing
            else -> s.rows.withIndex().firstNotNullOfOrNull { (i, r) ->
                when {
                    r.account == null -> "Row ${i + 1}: ${kind.headMissing}"
                    (r.amount.toDoubleOrNull() ?: 0.0) <= 0 -> "Row ${i + 1}: ${kind.amountMissing}"
                    else -> null
                }
            }
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("mode", s.mode.id)
                if (s.isCash) add("bank_account", JsonNull.INSTANCE) else addProperty("bank_account", s.bank!!.id)
                if (kind.manualNumber) {
                    s.manualNo.trim().takeIf { it.isNotEmpty() }?.let { addProperty("manual_voucher_no", it) }
                        ?: add("manual_voucher_no", JsonNull.INSTANCE)
                }
                s.notes.trim().takeIf { it.isNotEmpty() }?.let { addProperty("notes", it) } ?: add("notes", JsonNull.INSTANCE)
                add("rows", JsonArray().apply {
                    s.rows.forEach { r ->
                        add(JsonObject().apply {
                            addProperty("account", r.account!!.id.toString())
                            addProperty("amount", r.amount.toDouble())
                            r.remarks.trim().takeIf { it.isNotEmpty() }?.let { addProperty("remarks", it) } ?: add("remarks", JsonNull.INSTANCE)
                        })
                    }
                })
            }
            when (val r = api.post(kind.endpoint, body)) {
                is Resource.Success -> {
                    val vr = r.data.jPayload().jStr("vr_no")
                    reset()
                    _uiState.update { it.copy(isSaving = false, message = "Voucher $vr saved") }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = r.message.ifBlank { "Could not save" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: HotelEntryKind) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                HotelEntryViewModel(kind, ServiceLocator.provideWebApi(app), ServiceLocator.provideLedgerRepository(app))
            }
        }
    }
}

private fun tk(v: Double) = String.format(Locale.US, "%,.2f", v)

@Composable
fun HotelAccountsEntryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: HotelEntryKind,
    viewModel: HotelEntryViewModel = viewModel(key = "hotel-entry-${kind.name}", factory = HotelEntryViewModel.provideFactory(LocalContext.current, kind)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = kind.heading, currentRoute = Routes.HOTEL, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(kind.subheading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Account Heads", fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen, modifier = Modifier.weight(1f))
                        SecondaryButton(text = "Add Row", onClick = viewModel::addRow, compact = true)
                    }
                }
                itemsIndexed(state.rows, key = { _, r -> r.key }) { _, row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            key(state.resetKey, row.key) {
                                SearchableLedgerDropdown(
                                    selectedLedger = row.account,
                                    onLedgerSelected = { a -> viewModel.updateRow(row.key) { it.copy(account = a) } },
                                    searchLedgers = viewModel::searchHeads,
                                    label = "Account Head *",
                                    placeholder = "Search and select Account Head",
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppTextField(
                                    row.amount, { v -> viewModel.updateRow(row.key) { it.copy(amount = v.filter { c -> c.isDigit() || c == '.' }) } },
                                    label = "0.00", caption = kind.amountLabel, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                                )
                                AppTextField(
                                    row.remarks, { v -> viewModel.updateRow(row.key) { it.copy(remarks = v) } },
                                    label = kind.rowNotePlaceholder, caption = kind.rowNoteLabel, modifier = Modifier.weight(1f),
                                )
                            }
                            if (state.rows.size > 1) {
                                LinkButton(text = "Remove this head", onClick = { viewModel.removeRow(row.key) }, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Text(kind.detailsTitle, fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen) }
                item { AppSelectDropdown(if (kind == HotelEntryKind.PAYMENT) "Payment mode" else "Received into", MODES, state.mode, viewModel::onMode) }
                if (!state.isCash) {
                    item { AppSelectDropdown("Bank Account *", state.banks, state.bank, viewModel::onBank, placeholder = "Select a bank account") }
                }
                if (kind.manualNumber) {
                    item {
                        Column {
                            AppTextField(state.manualNo, viewModel::onManualNo, label = "Optional", caption = "Voucher No", modifier = Modifier.fillMaxWidth())
                            Text(
                                "Optional. Your own number for this payment; the system numbers it 2-… either way.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                    }
                }
                item { AppTextField(state.notes, viewModel::onNotes, label = kind.notesPlaceholder, caption = "Notes", multiline = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(kind.summaryTitle, fontWeight = AppFontWeight.SemiBold)
                            SummaryLine(kind.methodLabel, if (state.isCash) "Cash" else "Bank", muted)
                            SummaryLine(kind.sideLabel, if (state.isCash) "Cash in Hand" else state.bank?.label ?: "—", muted)
                            SummaryLine("Account Heads", state.rows.size.toString(), muted)
                            HorizontalDivider()
                            // The two sides come to the same figure by construction;
                            // the Difference is shown so that is seen, not assumed.
                            SummaryLine("Total Debit (Tk.)", tk(state.total), muted)
                            SummaryLine("Total Credit (Tk.)", tk(state.total), muted)
                            SummaryLine("Difference (Tk.)", tk(0.0), muted)
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(kind.totalLabel, fontWeight = AppFontWeight.SemiBold)
                                Text(tk(state.total), fontWeight = AppFontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(text = "Reset", onClick = viewModel::reset, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            text = if (state.isSaving) "Saving..." else kind.saveLabel,
                            onClick = viewModel::save, isLoading = state.isSaving, enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, muted: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = muted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold)
    }
}
