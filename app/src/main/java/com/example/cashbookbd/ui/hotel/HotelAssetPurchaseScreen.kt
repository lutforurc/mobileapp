package com.example.cashbookbd.ui.hotel

import android.content.Context
import androidx.compose.foundation.border
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
import com.example.cashbookbd.core.jNum
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
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** One row of the `asset_items` catalogue. */
data class HotelCatalogueItem(
    val id: String,
    val name: String,
    /** "asset" goes on the register; "expense" is charged and done. */
    val kind: String,
    val unit: String,
    val lastRate: Double,
    val headName: String,
)

data class HotelPurchaseLine(
    val key: Long,
    val item: HotelCatalogueItem,
    val quantity: String,
    val rate: String,
) {
    val total: Double get() = round2((quantity.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0))
}

private fun round2(v: Double) = (v * 100).roundToLong() / 100.0

private val PAY_MODES = listOf(SelectorOption("cash", "Cash"), SelectorOption("bank", "Bank"), SelectorOption("credit", "Credit"))

data class HotelPurchaseUiState(
    val heads: List<SelectorOption> = emptyList(),
    val head: SelectorOption? = null,
    val items: List<HotelCatalogueItem> = emptyList(),
    val itemsLoading: Boolean = false,
    val lineItem: HotelCatalogueItem? = null,
    val lineQty: String = "",
    val lineRate: String = "",
    val editingKey: Long? = null,
    val rows: List<HotelPurchaseLine> = emptyList(),
    val mode: SelectorOption = PAY_MODES.first(),
    val banks: List<SelectorOption> = emptyList(),
    val bank: SelectorOption? = null,
    val chequeNo: String = "",
    val supplier: LedgerDropdownItem? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val savedVoucher: String = "",
    val savedMode: String = "cash",
    val resetKey: Int = 0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val subtotal: Double get() = rows.sumOf { it.total }
    val paid: Double get() = if (mode.id == "credit") 0.0 else round2(subtotal)
    val due: Double get() = round2(round2(subtotal) - paid)
}

/**
 * Accounts Transaction (HotelAssetPurchase.tsx): buying what a hotel is
 * furnished with — or the meat, oil and soap it uses up. Numbered as a
 * purchase (`4-`); cash, bank or credit are three vouchers (credit is a `9-`
 * owed to the supplier). The item comes from the asset catalogue, not from
 * products: the Accounts Head box only filters the list, and each line's head
 * is read from its item. No discount and no part payment — stated, not hidden.
 */
class HotelPurchaseViewModel(private val api: WebApi, private val ledgers: LedgerRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HotelPurchaseUiState())
    val uiState: StateFlow<HotelPurchaseUiState> = _uiState.asStateFlow()
    private var itemsJob: Job? = null

    init {
        viewModelScope.launch {
            (api.get("hotel-setup/asset-purchases/accounts/ddl") as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(heads = r.data.jRows().map { h -> SelectorOption(h.jStr("value"), h.jStr("label")) }) }
            }
        }
        viewModelScope.launch {
            (api.get("coal3/l4-list/2") as? Resource.Success)?.let { r ->
                val banks = r.data.jRows().map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                _uiState.update { it.copy(banks = banks, bank = banks.singleOrNull()) }
            }
        }
    }

    suspend fun searchSuppliers(q: String) = ledgers.searchLedgers(q, acType = "3")

    /** The list IS the head's items, so it is read again when the head changes. */
    fun onHead(o: SelectorOption) {
        _uiState.update { it.copy(head = o, items = emptyList(), lineItem = null, itemsLoading = true) }
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            val r = api.get("hotel-setup/asset-purchases/items/ddl", mapOf("account" to o.id))
            _uiState.update {
                it.copy(
                    itemsLoading = false,
                    items = (r as? Resource.Success)?.data?.jRows().orEmpty().map { x ->
                        HotelCatalogueItem(
                            id = x.jStr("id"), name = x.jStr("name"), kind = x.jStr("kind").ifBlank { "asset" },
                            unit = x.jStr("unit"), lastRate = x.jNum("last_rate"), headName = x.jStr("head_name"),
                        )
                    },
                )
            }
        }
    }

    /** The last price paid is a starting figure, not a rule. */
    fun onItem(o: SelectorOption) = _uiState.update { s ->
        val item = s.items.firstOrNull { it.id == o.id }
        s.copy(lineItem = item, lineRate = item?.lastRate?.takeIf { it > 0 }?.let { plain(it) }.orEmpty())
    }

    private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun onQty(v: String) = _uiState.update { it.copy(lineQty = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onRate(v: String) = _uiState.update { it.copy(lineRate = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onMode(o: SelectorOption) = _uiState.update { it.copy(mode = o) }
    fun onBank(o: SelectorOption) = _uiState.update { it.copy(bank = o) }
    fun onCheque(v: String) = _uiState.update { it.copy(chequeNo = v) }
    fun onSupplier(l: LedgerDropdownItem?) = _uiState.update { it.copy(supplier = l) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }
    fun dismissSaved() = _uiState.update { it.copy(savedVoucher = "") }

    fun addLine() {
        val s = _uiState.value
        val item = s.lineItem
        val qty = s.lineQty.toDoubleOrNull() ?: 0.0
        val error = when {
            item == null -> "Pick an item."
            qty <= 0 -> "How many?"
            // The register keeps one row per asset, so an asset is counted whole.
            item.kind == "asset" && qty % 1.0 != 0.0 -> "${item.name} is an asset and is counted in whole units."
            (s.lineRate.toDoubleOrNull() ?: 0.0) == 0.0 -> "At what rate? A line worth nothing cannot be saved."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        _uiState.update {
            val line = HotelPurchaseLine(it.editingKey ?: System.nanoTime(), item!!, it.lineQty, it.lineRate)
            it.copy(
                rows = if (it.editingKey != null) it.rows.map { r -> if (r.key == it.editingKey) line else r } else it.rows + line,
                editingKey = null, lineItem = null, lineQty = "", lineRate = "",
            )
        }
    }

    fun editLine(l: HotelPurchaseLine) = _uiState.update { it.copy(editingKey = l.key, lineItem = l.item, lineQty = l.quantity, lineRate = l.rate) }
    fun cancelEdit() = _uiState.update { it.copy(editingKey = null, lineItem = null, lineQty = "", lineRate = "") }
    fun removeLine(l: HotelPurchaseLine) {
        _uiState.update { it.copy(rows = it.rows.filterNot { r -> r.key == l.key }) }
        if (_uiState.value.editingKey == l.key) cancelEdit()
    }

    /** The mode and the bank are habits of the desk, so they stay. */
    fun reset() = _uiState.update {
        it.copy(
            rows = emptyList(), lineItem = null, lineQty = "", lineRate = "", editingKey = null, notes = "",
            head = null, items = emptyList(), supplier = null, chequeNo = "", savedVoucher = "", resetKey = it.resetKey + 1,
        )
    }

    fun save() {
        val s = _uiState.value
        val error = when {
            s.head == null -> "Choose the head this is being bought against."
            s.rows.isEmpty() -> "Add at least one line before saving."
            s.editingKey != null -> "Finish the line you are correcting first — Save item, or cancel."
            s.mode.id == "bank" && s.bank == null -> "Which bank account was it paid from?"
            s.mode.id == "credit" && s.supplier == null -> "Bought on credit from whom? Choose the supplier."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("account", s.head!!.id.toLongOrNull() ?: 0L)
                if (s.notes.isNotBlank()) addProperty("note", s.notes) else add("note", JsonNull.INSTANCE)
                addProperty("mode", s.mode.id)
                if (s.mode.id == "bank") addProperty("bank_account", s.bank!!.id.toLongOrNull() ?: 0L) else add("bank_account", JsonNull.INSTANCE)
                val cheque = s.chequeNo.trim().takeIf { s.mode.id == "bank" && it.isNotEmpty() }
                if (cheque != null) addProperty("cheque_no", cheque) else add("cheque_no", JsonNull.INSTANCE)
                if (s.mode.id == "credit") addProperty("supplier", s.supplier!!.id) else add("supplier", JsonNull.INSTANCE)
                add("rows", JsonArray().apply {
                    s.rows.forEach { r ->
                        add(JsonObject().apply {
                            addProperty("item_id", r.item.id.toLongOrNull() ?: 0L)
                            addProperty("quantity", r.quantity.toDouble())
                            addProperty("rate", round2(r.rate.toDouble()))
                        })
                    }
                })
            }
            when (val r = api.post("hotel-setup/asset-purchases/store", body)) {
                is Resource.Success -> {
                    val vr = r.data.jPayload().jStr("vr_no")
                    reset()
                    _uiState.update { it.copy(isSaving = false, savedVoucher = vr, savedMode = s.mode.id, message = "Voucher $vr saved") }
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
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                HotelPurchaseViewModel(ServiceLocator.provideWebApi(app), ServiceLocator.provideLedgerRepository(app))
            }
        }
    }
}

@Composable
fun HotelAssetPurchaseScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: HotelPurchaseViewModel = viewModel(factory = HotelPurchaseViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted
    val itemPlaceholder = when {
        state.head == null -> "Choose an Accounts Head first"
        state.itemsLoading -> "Loading…"
        state.items.isEmpty() -> "No items yet — add them in Fixed Assets → Items"
        else -> "Select an item"
    }

    AuthenticatedShell(title = "Accounts Transaction", currentRoute = Routes.HOTEL, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Asset & Inventory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted) }
                if (state.savedVoucher.isNotBlank()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.appColors.success, MaterialTheme.shapes.small).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Saved — voucher ${state.savedVoucher}. " + when (state.savedMode) {
                                    "credit" -> "It is owed to the supplier — see the Supplier Ledger."
                                    "bank" -> "It is in the bank book and the ledger like any other payment."
                                    else -> "It is in the cash book and the ledger like any other payment."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreen,
                                modifier = Modifier.weight(1f),
                            )
                            LinkButton(text = "✕", onClick = viewModel::dismissSaved)
                        }
                    }
                }
                item {
                    key(state.resetKey) {
                        AppSelectDropdown("Accounts Head *", state.heads, state.head, viewModel::onHead, placeholder = "Select a head")
                    }
                }
                // The number is the server's to give: shown, never typed.
                item {
                    AppTextField(state.savedVoucher, {}, label = "Auto-generated", caption = "Voucher No", enabled = false, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSelectDropdown(
                                "Select Item *",
                                state.items.map { SelectorOption(it.id, if (it.unit.isNotBlank()) "${it.name} (${it.unit})" else it.name) },
                                state.lineItem?.let { SelectorOption(it.id, if (it.unit.isNotBlank()) "${it.name} (${it.unit})" else it.name) },
                                viewModel::onItem,
                                placeholder = itemPlaceholder,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppTextField(state.lineQty, viewModel::onQty, label = "0", caption = "Quantity", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                                AppTextField(state.lineRate, viewModel::onRate, label = "0.00", caption = "Rate (Tk.)", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(text = if (state.editingKey != null) "Save Item" else "Add Item", onClick = viewModel::addLine, modifier = Modifier.weight(1f))
                                if (state.editingKey != null) SecondaryButton(text = "Cancel", onClick = viewModel::cancelEdit)
                            }
                        }
                    }
                }
                if (state.rows.isEmpty()) {
                    item { ReportNote("Nothing added yet — pick an item above and press Add Item.") }
                } else {
                    itemsIndexed(state.rows, key = { _, r -> r.key }) { i, row ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${(i + 1).toString().padStart(2, '0')}. ${row.item.name}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    // Where the money lands, and whether it becomes property or is spent.
                                    Text(
                                        "${row.item.headName} · ${if (row.item.kind == "expense") "Expense" else "Asset"}",
                                        style = MaterialTheme.typography.labelSmall, color = muted,
                                    )
                                    Text(
                                        "${row.quantity} ${row.item.unit} × ${kitMoney(row.rate.toDoubleOrNull() ?: 0.0)} = ${kitMoney(row.total)}".trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                LinkButton(text = "Edit", onClick = { viewModel.editLine(row) })
                                LinkButton(text = "Remove", onClick = { viewModel.removeLine(row) }, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Text("Payment Details", fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen) }
                item { AppSelectDropdown("Payment mode", PAY_MODES, state.mode, viewModel::onMode) }
                item {
                    Text(
                        when (state.mode.id) {
                            "credit" -> "Nothing paid now — the whole bill is owed to the supplier and is settled later from the payment screens."
                            "bank" -> "Paid in full from the bank account below."
                            else -> "Paid out of cash, in full."
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                if (state.mode.id == "bank") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSelectDropdown("Bank Account *", state.banks, state.bank, viewModel::onBank, Modifier.weight(1f), placeholder = "Select a bank account")
                            AppTextField(state.chequeNo, viewModel::onCheque, label = "Optional", caption = "Cheque No", modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (state.mode.id == "credit") {
                    item {
                        key(state.resetKey) {
                            SearchableLedgerDropdown(
                                selectedLedger = state.supplier, onLedgerSelected = viewModel::onSupplier,
                                searchLedgers = viewModel::searchSuppliers, label = "Supplier *", placeholder = "Select Supplier",
                            )
                        }
                    }
                }
                item { AppTextField(state.notes, viewModel::onNotes, label = "Optional note for the whole voucher", caption = "Notes", multiline = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Transaction Totals", fontWeight = AppFontWeight.SemiBold)
                            TotalsLine("Subtotal (Tk.)", kitMoney(state.subtotal))
                            // No discount yet: where it goes is a bookkeeping decision nobody has made.
                            TotalsLine("Discount Amount (Tk.)", "0 — not built yet")
                            HorizontalDivider()
                            TotalsLine("Net Total (Tk.)", kitMoney(round2(state.subtotal)), bold = true)
                            TotalsLine(
                                "Paid Amount (Tk.)",
                                kitMoney(state.paid) + if (state.mode.id == "credit") " (nothing now)" else " (the whole of it)",
                            )
                            TotalsLine("Due Amount (Tk.)", kitMoney(state.due), color = MaterialTheme.appColors.warning)
                            Text("Part payment is not built yet.", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(text = "Reset", onClick = viewModel::reset, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            text = if (state.isSaving) "Saving..." else "Save Transaction",
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
private fun TotalsLine(label: String, value: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textMuted)
        Text(
            value, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.SemiBold,
            color = color ?: androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}
