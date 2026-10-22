package com.example.cashbookbd.ui.stockslip

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.RequisitionRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- The register -------------------------------------------------------------

data class RegisterRow(
    val date: String,
    val number: String,
    val product: String,
    val quantity: Double,
    val unit: String,
    val rate: Double,
    val money: Double,
    val reason: String,
    val warehouse: String,
    val note: String,
)

data class RegisterGroup(val label: String, val count: Int, val quantity: Double, val unit: String, val money: Double)

data class StockSlipRegisterUiState(
    val from: SimpleDate = SimpleDate.today().let { SimpleDate(it.year, it.month, 1) },
    val to: SimpleDate = SimpleDate.today(),
    val warehouses: List<SelectorOption> = emptyList(),
    val warehouse: SelectorOption = EVERY_WAREHOUSE,
    val reasons: List<SelectorOption> = emptyList(),
    val reason: SelectorOption = EVERY_REASON,
    val product: SelectorOption? = null,
    val isLoading: Boolean = false,
    val rows: List<RegisterRow> = emptyList(),
    val byReason: List<RegisterGroup> = emptyList(),
    val byProduct: List<RegisterGroup> = emptyList(),
    val total: Double = 0.0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

private val EVERY_WAREHOUSE = SelectorOption("", "Every warehouse")

/**
 * What was written off (or came in), why, and what it cost — pending and
 * approved slips alike: the stock moves at save, so a pending slip has
 * already cost (or added to) the shop. Opens on this month so far.
 */
class StockSlipRegisterViewModel(
    private val kind: StockSlipKind,
    private val api: WebApi,
    private val selectors: SelectorRepository,
    private val requisition: RequisitionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockSlipRegisterUiState())
    val uiState: StateFlow<StockSlipRegisterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            (selectors.fetch(ReportSelectorSource.WAREHOUSE) as? Resource.Success)?.let { r -> _uiState.update { it.copy(warehouses = r.data) } }
        }
        viewModelScope.launch {
            (api.loadReasons(kind) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(reasons = r.data.map { x -> SelectorOption(x.id, x.name) }) }
            }
        }
    }

    fun onFrom(d: SimpleDate) = _uiState.update { it.copy(from = d) }
    fun onTo(d: SimpleDate) = _uiState.update { it.copy(to = d) }
    fun onWarehouse(o: SelectorOption) = _uiState.update { it.copy(warehouse = o) }
    fun onReason(o: SelectorOption) = _uiState.update { it.copy(reason = o) }
    fun onProduct(o: SelectorOption) = _uiState.update { it.copy(product = o) }

    suspend fun searchProducts(q: String): Resource<List<SelectorOption>> =
        when (val r = requisition.searchItems(q)) {
            is Resource.Success -> Resource.Success(r.data.map { SelectorOption(it.id, it.name, it.unit.ifBlank { null }) })
            is Resource.Error -> r
            Resource.Loading -> Resource.Loading
        }

    fun show() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val params = buildMap {
                put("from_date", s.from.toApi())
                put("to_date", s.to.toApi())
                s.warehouse.id.takeIf { it.isNotBlank() }?.let { put(kind.warehouseKey, it) }
                s.reason.id.takeIf { it.isNotBlank() }?.let { put("reason_id", it) }
                s.product?.id?.let { put("product_id", it) }
            }
            when (val r = api.get("${kind.base}/report", params)) {
                is Resource.Success -> {
                    val data = r.data.jPayload()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rows = data.jObjs("rows").map { o ->
                                RegisterRow(
                                    date = o.jStr(kind.dateKey), number = o.jStr(kind.numberKey),
                                    product = o.jStr("product_name"), quantity = o.jNum("quantity"),
                                    unit = o.jStr("unit_name"), rate = o.jNum("rate"), money = o.jNum(kind.moneyKey),
                                    reason = o.jStr("reason_name"), warehouse = o.jStr("warehouse_name"), note = o.jStr("note"),
                                )
                            },
                            byReason = data.jObjs("by_reason").map { o ->
                                RegisterGroup(o.jStr("reason_name").ifBlank { "(no reason)" }, o.jInt("voucher_count") ?: 0, 0.0, "", o.jNum(kind.moneyKey))
                            },
                            byProduct = data.jObjs("by_product").map { o ->
                                RegisterGroup(o.jStr("product_name"), 0, o.jNum("quantity"), o.jStr("unit_name"), o.jNum(kind.moneyKey))
                            },
                            total = data.jNum(kind.totalMoneyKey),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Failed to load the register" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: StockSlipKind) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                StockSlipRegisterViewModel(
                    kind, ServiceLocator.provideWebApi(app), ServiceLocator.provideSelectorRepository(app),
                    ServiceLocator.provideRequisitionRepository(app),
                )
            }
        }
    }
}

@Composable
fun StockSlipRegisterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: StockSlipKind,
    viewModel: StockSlipRegisterViewModel = viewModel(
        key = "stock-slip-register-${kind.name}",
        factory = StockSlipRegisterViewModel.provideFactory(LocalContext.current, kind),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = kind.registerTitle, currentRoute = Routes.BRANCH_TRANSFER, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DateRangeFields(state.from, state.to, viewModel::onFrom, viewModel::onTo, "From", "To") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Warehouse", listOf(EVERY_WAREHOUSE) + state.warehouses, state.warehouse, viewModel::onWarehouse, Modifier.weight(1f))
                        AppSelectDropdown("Reason", listOf(EVERY_REASON) + state.reasons, state.reason, viewModel::onReason, Modifier.weight(1f))
                    }
                }
                item {
                    SearchableSelectDropdown(
                        selected = state.product, onSelected = viewModel::onProduct, search = viewModel::searchProducts,
                        label = "Product", emptyText = "No product found",
                    )
                }
                item { PrimaryButton(text = "Show", onClick = viewModel::show, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }
                item {
                    val sentence = if (kind == StockSlipKind.OUT) {
                        "Written off in this period: ${kitMoney(state.total)} — already inside the closing stock, so the profit is short by this much and no more."
                    } else {
                        "Came in this period: ${kitMoney(state.total)} — already inside the closing stock, so the profit is up by this much and no more."
                    }
                    Text(
                        sentence,
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item { SectionTitle("The slips") }
                item {
                    ReportTable(
                        columns = listOf(
                            ReportColumn<RegisterRow>("SL", ReportColWidth.Fixed(44.dp), TextAlign.Center) { _, i -> cellText((i + 1).toString()) },
                            ReportColumn<RegisterRow>("DATE", ReportColWidth.Fixed(92.dp)) { r, _ -> cellText(r.date.ifBlank { "-" }) },
                            ReportColumn<RegisterRow>(kind.numberHeader, ReportColWidth.Fixed(120.dp)) { r, _ -> cellText(r.number.ifBlank { "-" }) },
                            ReportColumn<RegisterRow>("PRODUCT", ReportColWidth.Fixed(170.dp)) { r, _ -> cellText(r.product.ifBlank { "-" }, maxLines = 2) },
                            ReportColumn<RegisterRow>("QTY", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ -> cellText("${kitMoney(r.quantity)} ${r.unit}".trim()) },
                            ReportColumn<RegisterRow>("RATE", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.rate)) },
                            ReportColumn<RegisterRow>(kind.moneyHeader, ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.money)) },
                            ReportColumn<RegisterRow>("REASON", ReportColWidth.Fixed(130.dp)) { r, _ -> cellText(r.reason.ifBlank { "-" }, maxLines = 2) },
                            ReportColumn<RegisterRow>("WAREHOUSE", ReportColWidth.Fixed(120.dp)) { r, _ -> cellText(r.warehouse.ifBlank { "-" }) },
                            ReportColumn<RegisterRow>("NOTE", ReportColWidth.Fixed(150.dp)) { r, _ -> cellText(r.note.ifBlank { "-" }, maxLines = 2) },
                        ),
                        data = state.rows,
                        scrollable = false,
                        noDataMessage = kind.emptyRegister,
                    )
                }
                item { SectionTitle(kind.byReasonTitle) }
                item {
                    ReportTable(
                        columns = listOf(
                            ReportColumn<RegisterGroup>("REASON", ReportColWidth.Weight(0.5f)) { r, _ -> cellText(r.label, maxLines = 2) },
                            ReportColumn<RegisterGroup>("SLIPS", ReportColWidth.Weight(0.2f), TextAlign.End) { r, _ -> cellText(r.count.toString()) },
                            ReportColumn<RegisterGroup>(kind.moneyHeader, ReportColWidth.Weight(0.3f), TextAlign.End) { r, _ -> cellText(kitMoney(r.money)) },
                        ),
                        data = state.byReason,
                        scrollable = false,
                        noDataMessage = kind.emptyGroup,
                    )
                }
                item { SectionTitle("Which goods") }
                item {
                    ReportTable(
                        columns = listOf(
                            ReportColumn<RegisterGroup>("PRODUCT", ReportColWidth.Weight(0.45f)) { r, _ -> cellText(r.label.ifBlank { "-" }, maxLines = 2) },
                            ReportColumn<RegisterGroup>("QTY", ReportColWidth.Weight(0.27f), TextAlign.End) { r, _ -> cellText("${kitMoney(r.quantity)} ${r.unit}".trim()) },
                            ReportColumn<RegisterGroup>(kind.moneyHeader, ReportColWidth.Weight(0.28f), TextAlign.End) { r, _ -> cellText(kitMoney(r.money)) },
                        ),
                        data = state.byProduct,
                        scrollable = false,
                        noDataMessage = kind.emptyGroup,
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen)
}

// ---- Reasons --------------------------------------------------------------------

data class StockSlipReasonsUiState(
    val reasons: List<SlipReason> = emptyList(),
    val isLoading: Boolean = false,
    val editingId: String? = null,
    val name: String = "",
    val needsParty: Boolean = false,
    val sort: String = "",
    val isSaving: Boolean = false,
    val askRemove: SlipReason? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * The shop's own short list of reasons. `needs_party` is the reason's own
 * answer to "does this owe a name?"; the entry form reads it off the row, so
 * adding a reason here is all it takes. A reason already used on a slip is
 * retired rather than deleted — the server decides, and says which happened.
 */
class StockSlipReasonsViewModel(private val kind: StockSlipKind, private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(StockSlipReasonsUiState())
    val uiState: StateFlow<StockSlipReasonsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.loadReasons(kind)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, reasons = r.data) }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, message = r.message, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(v: String) = _uiState.update { it.copy(name = v) }
    fun onSort(v: String) = _uiState.update { it.copy(sort = v.filter { c -> c.isDigit() }) }
    fun toggleNeedsParty() = _uiState.update { it.copy(needsParty = !it.needsParty) }
    fun clear() = _uiState.update { it.copy(editingId = null, name = "", needsParty = false, sort = "") }
    fun edit(r: SlipReason) = _uiState.update { it.copy(editingId = r.id, name = r.name, needsParty = r.needsParty, sort = r.sort.toString()) }

    fun save() {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.update { it.copy(message = "Give the reason a name") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        val body = JsonObject().apply {
            addProperty("name", s.name.trim())
            addProperty("needs_party", s.needsParty)
            addProperty("sort", s.sort.toIntOrNull() ?: 99)
            addProperty("is_active", true)
        }
        viewModelScope.launch {
            val path = s.editingId?.let { "${kind.base}/reason/update/$it" } ?: "${kind.base}/reason/store"
            when (val r = api.post(path, body)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = r.data.jStr("message").ifBlank { "Saved" }) }
                    clear()
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = r.message.ifBlank { "Save failed" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askRemove(r: SlipReason) = _uiState.update { it.copy(askRemove = r) }
    fun dismissRemove() = _uiState.update { it.copy(askRemove = null) }

    fun confirmRemove() {
        val row = _uiState.value.askRemove ?: return
        _uiState.update { it.copy(askRemove = null) }
        viewModelScope.launch {
            when (val r = api.post("${kind.base}/reason/destroy/${row.id}")) {
                is Resource.Success -> {
                    _uiState.update { it.copy(message = r.data.jStr("message").ifBlank { "Removed" }) }
                    load()
                }
                is Resource.Error -> _uiState.update { it.copy(message = r.message.ifBlank { "Remove failed" }, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: StockSlipKind) = viewModelFactory {
            initializer { StockSlipReasonsViewModel(kind, ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

@Composable
fun StockSlipReasonsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: StockSlipKind,
    viewModel: StockSlipReasonsViewModel = viewModel(
        key = "stock-slip-reasons-${kind.name}",
        factory = StockSlipReasonsViewModel.provideFactory(LocalContext.current, kind),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = kind.reasonsTitle, currentRoute = Routes.BRANCH_TRANSFER, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AppTextField(state.name, viewModel::onName, label = kind.reasonExample, caption = "Reason", modifier = Modifier.fillMaxWidth()) }
                item { AppTextField(state.sort, viewModel::onSort, label = "99", caption = "Order", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.needsParty, onCheckedChange = { viewModel.toggleNeedsParty() })
                        Text(
                            if (kind == StockSlipKind.OUT) "This one asks who it went back to" else "This one asks who it came from",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreen,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = if (state.editingId != null) "Update" else "Add",
                            onClick = viewModel::save, isLoading = state.isSaving, modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "Clear", onClick = viewModel::clear, modifier = Modifier.weight(1f))
                    }
                }
                if (!state.isLoading && state.reasons.isEmpty()) item { ReportNote("No reason yet") }
                itemsIndexed(state.reasons, key = { _, r -> r.id }) { i, r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}. ${r.name.ifBlank { "-" }}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Asks for a name: ${if (r.needsParty) "Yes" else "No"} · Order: ${r.sort} · " +
                                        "Offered: ${if (r.active) "Yes" else "Retired"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.textMuted,
                                )
                            }
                            LinkButton(text = "Edit", onClick = { viewModel.edit(r) })
                            LinkButton(text = "Remove", onClick = { viewModel.askRemove(r) }, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.askRemove?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemove,
            title = { Text("Remove Reason") },
            text = { Text("Are you sure you want to remove the reason \"${r.name}\" ?\n\nA reason already used on a slip is retired rather than deleted.") },
            confirmButton = { LinkButton(text = "Remove", onClick = viewModel::confirmRemove, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissRemove) },
        )
    }
}
