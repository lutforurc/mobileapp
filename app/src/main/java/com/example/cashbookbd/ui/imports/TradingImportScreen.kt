package com.example.cashbookbd.ui.imports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.SheetReader
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Trading Purchase / Sales Invoice Import (TradingPurchaseImport.tsx and its
 * mirror): rows pasted or uploaded from Excel, each matched to a party, a
 * product and (when named) an order the books already hold, then saved as one
 * ordinary trading invoice per row through the screen's own store endpoint.
 */
enum class TradingImportKind(
    val title: String,
    val partyHeader: String,
    val moneyHeader: String,
    val moneyTotalLabel: String,
    val orderType: String,
    val storePath: String,
    val partyAliases: List<String>,
    val rateAliases: List<String>,
    val moneyAliases: List<String>,
) {
    PURCHASE(
        title = "Trading Purchase Invoice Import", partyHeader = "Supplier", moneyHeader = "Payment", moneyTotalLabel = "Payment Total",
        orderType = "1", storePath = "trading/purchase/api-store",
        partyAliases = listOf("supplier", "customer", "party", "client"),
        rateAliases = listOf("rate", "price", "purchaserate", "purchaseprice"),
        moneyAliases = listOf("payment", "paymentamount", "paid"),
    ),
    SALES(
        title = "Trading Sales Invoice Import", partyHeader = "Customer", moneyHeader = "Received", moneyTotalLabel = "Received Total",
        orderType = "2", storePath = "trading/sales/api-store",
        partyAliases = listOf("customer", "party", "client"),
        rateAliases = listOf("rate", "price", "salesrate"),
        moneyAliases = listOf("received", "receivedamount", "paid"),
    );

    val columns: List<String>
        get() = listOf("Sl. No", partyHeader, "Product", "Qty", "Rate", "Weight Variance", moneyHeader, "Truck Number", "Order Number", "Notes")
}

data class TradingImportRow(
    val slNo: String,
    val party: String,
    val product: String,
    val qty: Double,
    val rate: Double,
    /** null: the column was left empty. */
    val variance: Double?,
    val money: Double,
    val truck: String,
    val order: String,
    val notes: String,
    // Filled by "Check with Software".
    val partyId: String = "",
    val partyName: String = "",
    val productId: String = "",
    val productName: String = "",
    val unit: String = "",
    val orderId: String = "",
    val orderText: String = "",
    val errors: List<String> = emptyList(),
    val saveError: String = "",
    val saved: Boolean = false,
)

data class TradingImportUiState(
    val kind: TradingImportKind,
    val rawText: String = "",
    val showPaste: Boolean = false,
    val rows: List<TradingImportRow> = emptyList(),
    val checked: Boolean = false,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

private fun norm(v: String) = v.trim().lowercase().replace(Regex("\\s+"), " ")
private fun normHeader(v: String) = norm(v).replace(Regex("[\\s_-]+"), "")
private fun normOrder(v: String) = norm(v).replace(Regex("[\\s_-]+"), "")
private fun hasOrder(v: String) = norm(v).let { it.isNotEmpty() && it != "-" }
private fun parseNum(v: String) = v.replace(",", "").trim().toDoubleOrNull() ?: 0.0

class TradingImportViewModel(
    private val kind: TradingImportKind,
    private val api: WebApi,
    private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TradingImportUiState(kind = kind))
    val uiState: StateFlow<TradingImportUiState> = _uiState.asStateFlow()

    fun togglePaste() = _uiState.update { it.copy(showPaste = !it.showPaste) }
    fun onRaw(v: String) = _uiState.update { it.copy(rawText = v, rows = parse(SheetReader.fromPaste(v)), checked = false) }
    fun reset() = _uiState.update { it.copy(rawText = "", rows = emptyList(), checked = false) }
    fun back() = _uiState.update { it.copy(checked = false, rows = it.rows.map { r -> r.copy(errors = emptyList(), saveError = "") }) }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            val table = withContext(Dispatchers.IO) { runCatching { SheetReader.read(appContext, uri) } }
            table.onSuccess { t ->
                val text = t.filter { r -> r.any { it.isNotBlank() } }.joinToString("\n") { r -> r.joinToString("\t") { it.trim() } }
                _uiState.update { it.copy(rawText = text, rows = parse(t), checked = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(message = (e as? SheetReader.UnsupportedSheet)?.message ?: "This file could not be read.") }
            }
        }
    }

    /** A sheet without its heading row is read in the screen's own column order. */
    private fun parse(table: List<List<String>>): List<TradingImportRow> {
        var lines = table.filter { r -> r.any { it.isNotBlank() } }
        if (lines.isEmpty()) return emptyList()
        val first = normHeader(lines.first().joinToString(""))
        val partyWord = if (kind == TradingImportKind.PURCHASE) "supplier" else "customer"
        if (!(first.contains(partyWord) && first.contains("product") && first.contains("qty"))) {
            lines = listOf(kind.columns) + lines
        }
        if (lines.size < 2) return emptyList()
        val headers = lines.first().map(::normHeader)
        fun value(line: List<String>, aliases: List<String>): String {
            for (a in aliases) {
                val i = headers.indexOf(a)
                if (i >= 0) return line.getOrNull(i).orEmpty().trim()
            }
            return ""
        }
        return lines.drop(1).map { line ->
            val variance = value(line, listOf("weightvariance", "variance", "weightvarience")).replace(",", "").trim()
            TradingImportRow(
                slNo = value(line, listOf("slno", "serial", "serialno")),
                party = value(line, kind.partyAliases),
                product = value(line, listOf("product", "productname", "item", "itemname")),
                qty = parseNum(value(line, listOf("qty", "quantity"))),
                rate = parseNum(value(line, kind.rateAliases)),
                variance = if (variance.isEmpty()) null else (variance.toDoubleOrNull() ?: 0.0),
                money = parseNum(value(line, kind.moneyAliases)),
                truck = value(line, listOf("trucknumber", "truckno", "vehicle", "vehiclenumber")),
                order = value(line, listOf("ordernumber", "orderno", "order")),
                notes = value(line, listOf("notes", "note", "remarks", "remark")),
            )
        }
    }

    /** The closest match by name, else the first offered — the web's rule. */
    private fun pick(options: List<JsonObject>, name: String): JsonObject? {
        val n = norm(name)
        return options.firstOrNull { o ->
            val l = norm(o.jStr("label"))
            l == n || l.contains(n) || n.contains(l)
        } ?: options.firstOrNull()
    }

    private suspend fun resolveParty(name: String): JsonObject? {
        if (name.isBlank()) return null
        val r = api.get("chart_of_accounts/ddl/l4-list", mapOf("searchName" to name, "acType" to "3"))
        return pick((r as? Resource.Success)?.data?.jRows().orEmpty(), name)
    }

    private suspend fun resolveProduct(name: String): JsonObject? {
        if (name.isBlank()) return null
        val r = api.get("product/ddl/list", mapOf("q" to name))
        return pick((r as? Resource.Success)?.data?.jRows().orEmpty(), name)
    }

    private suspend fun resolveOrder(number: String): JsonObject? {
        if (!hasOrder(number)) return null
        val r = api.get("invoice/order/search", mapOf("q" to number, "order_type" to kind.orderType))
        val want = normOrder(number)
        return (r as? Resource.Success)?.data?.jRows().orEmpty().firstOrNull { o ->
            listOf("label", "order_number", "order_no", "label_1").map { normOrder(o.jStr(it)) }
                .any { c -> c.isNotEmpty() && (c == want || c.endsWith(want) || c.contains(want)) }
        }
    }

    fun check() {
        val rows = _uiState.value.rows
        if (rows.isEmpty()) {
            _uiState.update { it.copy(message = "Please paste Excel rows with header first.") }
            return
        }
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            val parties = mutableMapOf<String, JsonObject?>()
            val products = mutableMapOf<String, JsonObject?>()
            val orders = mutableMapOf<String, JsonObject?>()
            val checked = rows.map { row ->
                val party = parties.getOrPut(norm(row.party)) { resolveParty(row.party) }
                val product = products.getOrPut(norm(row.product)) { resolveProduct(row.product) }
                val order = if (hasOrder(row.order)) orders.getOrPut(normOrder(row.order)) { resolveOrder(row.order) } else null
                val errors = buildList {
                    if (row.party.isBlank()) add("${kind.partyHeader} missing")
                    if (party == null) add("${kind.partyHeader} not found: ${row.party.ifBlank { "-" }}")
                    if (product == null) add("Product not found: ${row.product.ifBlank { "-" }}")
                    if (hasOrder(row.order) && order == null) add("Order not found: ${row.order}")
                    if (row.qty <= 0) add("Qty must be greater than 0")
                    if (row.rate <= 0) add("Rate must be greater than 0")
                }
                row.copy(
                    partyId = party?.jStr("value").orEmpty(), partyName = party?.jStr("label") ?: row.party,
                    productId = product?.jStr("value").orEmpty(), productName = product?.jStr("label") ?: row.product,
                    unit = product?.jStr("label_5").orEmpty(),
                    orderId = order?.jStr("value").orEmpty(), orderText = order?.jStr("label") ?: row.order,
                    errors = errors,
                )
            }
            _uiState.update { it.copy(checking = false, checked = true, rows = checked) }
        }
    }

    fun save() {
        val s = _uiState.value
        if (!s.checked) {
            _uiState.update { it.copy(message = "Please preview the import first.") }
            return
        }
        if (s.rows.any { it.errors.isNotEmpty() }) {
            _uiState.update { it.copy(message = "Please fix import errors before saving.") }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            var failed = 0
            val base = System.currentTimeMillis()
            val results = s.rows.mapIndexed { index, row ->
                if (row.saved) return@mapIndexed row
                val body = JsonObject().apply {
                    addProperty("mtmId", "")
                    addProperty("account", row.partyId)
                    addProperty("accountName", row.partyName)
                    if (kind == TradingImportKind.PURCHASE) {
                        addProperty("paymentAmt", plain(row.money))
                        addProperty("purchaseOrderNumber", row.orderId)
                        addProperty("purchaseOrderText", if (row.orderId.isNotBlank()) row.orderText else "")
                        addProperty("invoice_no", "")
                        addProperty("invoice_date", "")
                    } else {
                        addProperty("receivedAmt", plain(row.money))
                        addProperty("salesOrderNumber", row.orderId)
                        addProperty("salesOrderText", if (row.orderId.isNotBlank()) row.orderText else "")
                        addProperty("purchaseOrderNumber", "")
                        addProperty("purchaseOrderText", "")
                    }
                    addProperty("discountAmt", 0)
                    addProperty("vehicleNumber", row.truck)
                    addProperty("notes", row.notes)
                    add("currentProduct", JsonNull.INSTANCE)
                    addProperty("searchInvoice", "")
                    add("products", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("id", base + index)
                            addProperty("product", row.productId.toLongOrNull() ?: 0L)
                            addProperty("product_name", row.productName)
                            addProperty("unit", row.unit)
                            addProperty("qty", row.qty)
                            addProperty("price", row.rate)
                            addProperty("bag", "")
                            addProperty("warehouse", "")
                            val v = row.variance
                            addProperty("variance", if (v == null || v == 0.0) "" else plain(abs(v)))
                            addProperty("variance_type", when { v == null -> ""; v < 0 -> "-"; v > 0 -> "+"; else -> "" })
                        })
                    })
                }
                val r = api.postEnvelope(kind.storePath, body)
                if (r is Resource.Success && r.data.jBool("success")) {
                    row.copy(saved = true, saveError = "")
                } else {
                    failed++
                    row.copy(
                        saveError = when (r) {
                            is Resource.Success -> WebApi.messageOf(r.data).ifBlank { "Not saved." }
                            is Resource.Error -> r.message
                            else -> "Not saved."
                        },
                    )
                }
            }
            val savedCount = results.count { it.saved } - s.rows.count { it.saved }
            _uiState.update {
                if (failed == 0) {
                    it.copy(saving = false, rows = emptyList(), rawText = "", checked = false,
                        message = "$savedCount ${if (kind == TradingImportKind.PURCHASE) "purchase" else "sales"} invoices imported successfully.")
                } else {
                    it.copy(saving = false, rows = results, message = "$failed row(s) were not saved; the rest were.")
                }
            }
        }
    }

    private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: TradingImportKind) = viewModelFactory {
            initializer { TradingImportViewModel(kind, ServiceLocator.provideWebApi(context.applicationContext), context.applicationContext) }
        }
    }
}

@Composable
fun TradingImportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: TradingImportKind,
    viewModel: TradingImportViewModel = viewModel(key = "trading-import-${kind.name}", factory = TradingImportViewModel.provideFactory(LocalContext.current, kind)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(viewModel::loadFile) }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = kind.title, currentRoute = Routes.INVOICES, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.checked) {
                    item {
                        Text("Columns: ${kind.columns.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(text = "Upload", onClick = { picker.launch("*/*") }, modifier = Modifier.weight(1f))
                            SecondaryButton(text = if (state.showPaste) "Hide Paste Box" else "Paste Rows", onClick = viewModel::togglePaste, modifier = Modifier.weight(1f))
                        }
                    }
                    if (state.showPaste) {
                        item {
                            AppTextField(
                                state.rawText, viewModel::onRaw, label = "Paste Excel rows here", caption = "Rows",
                                multiline = true, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            )
                        }
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryBox("Invoices", state.rows.size.toString(), Modifier.weight(1f))
                            SummaryBox("Invoice Total", kitMoney(state.rows.sumOf { it.qty * it.rate }), Modifier.weight(1f))
                            SummaryBox(kind.moneyTotalLabel, kitMoney(state.rows.sumOf { it.money }), Modifier.weight(1f))
                        }
                    }
                }
                if (state.rows.isEmpty()) {
                    item { ReportNote("Paste or upload Excel rows to preview imported data here.") }
                } else {
                    itemsIndexed(state.rows) { i, row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${row.slNo.ifBlank { (i + 1).toString() }}. ${if (state.checked) row.partyName else row.party}",
                                    fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${if (state.checked) row.productName else row.product} · Qty ${kitMoney(row.qty)} ${row.unit} · Rate ${kitMoney(row.rate)}" +
                                        (if (state.checked) " · Total ${kitMoney(row.qty * row.rate)}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "Weight Variance ${row.variance?.let { plainNum(it) } ?: "-"} · ${kind.moneyHeader} ${kitMoney(row.money)} · " +
                                        "Truck ${row.truck.ifBlank { "-" }} · Order ${(if (state.checked) row.orderText else row.order).ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                if (row.notes.isNotBlank()) Text("Notes: ${row.notes}", style = MaterialTheme.typography.labelSmall, color = muted)
                                if (state.checked) {
                                    when {
                                        row.saved -> Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.success)
                                        row.saveError.isNotBlank() -> Text(row.saveError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.danger)
                                        row.errors.isNotEmpty() -> Text(row.errors.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.danger)
                                        else -> Text("OK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.success)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.checked) {
                            PrimaryButton(
                                text = if (state.checking) "Checking..." else "Check with Software",
                                onClick = viewModel::check, isLoading = state.checking, modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(text = "Reset", onClick = viewModel::reset, modifier = Modifier.weight(1f))
                        } else {
                            SecondaryButton(text = "Back", onClick = viewModel::back, enabled = !state.saving, modifier = Modifier.weight(1f))
                            PrimaryButton(text = if (state.saving) "Saving..." else "Save", onClick = viewModel::save, isLoading = state.saving, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun plainNum(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun SummaryBox(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
            Text(value, fontWeight = AppFontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}
