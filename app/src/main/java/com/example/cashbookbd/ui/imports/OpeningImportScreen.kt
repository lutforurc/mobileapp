package com.example.cashbookbd.ui.imports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cashbookbd.core.SheetReader
import com.example.cashbookbd.core.jArr
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One column of an opening sheet: its key, header, and every heading it may carry. */
data class ImportField(val key: String, val header: String, val aliases: List<String>, val numeric: Boolean = false)

/**
 * The two opening imports (ProductOpeningImport.tsx / CustomerOpeningImport.tsx):
 * one design — Check asks the server what every row comes to, Ok saves the
 * rows one call at a time so a long sheet never meets the time limit, and a
 * refused row stays on screen to be fixed and sent again.
 */
enum class OpeningImportKind(
    val title: String,
    val headings: List<String>,
    val fields: List<ImportField>,
    /** The column without which a row means nothing. */
    val keyField: String,
    val checkPath: String,
    val rowPath: String,
    /** The figure Σ'd into "Opening value". */
    val valueOf: (Map<String, String>) -> Double,
    val existingNameKey: String,
    val noun: String,
) {
    PRODUCT(
        title = "Product Opening Import",
        headings = listOf("sl_no", "brand_name", "group", "category", "code", "product_name", "unit", "purchase_price", "sales_price", "stock"),
        fields = listOf(
            ImportField("brand_name", "Brand", listOf("brandname", "brand")),
            ImportField("group", "Group", listOf("group", "groupname", "productgroup")),
            ImportField("category", "Category", listOf("category", "categoryname")),
            ImportField("code", "Code", listOf("code", "productcode", "itemcode")),
            ImportField("product_name", "Product Name", listOf("productname", "name", "product", "itemname")),
            ImportField("unit", "Unit", listOf("unit", "unitname")),
            ImportField("purchase_price", "P. Price", listOf("purchaseprice", "pprice", "purchase", "purchaserate"), numeric = true),
            ImportField("sales_price", "Sales Price", listOf("salesprice", "saleprice", "sprice", "sales", "salesrate"), numeric = true),
            ImportField("stock", "Stock", listOf("stock", "qty", "quantity", "openingstock"), numeric = true),
        ),
        keyField = "product_name",
        checkPath = "product/opening-import/check",
        rowPath = "product/opening-import/row",
        valueOf = { num(it["stock"]) * num(it["purchase_price"]) },
        existingNameKey = "product_name",
        noun = "product",
    ),
    CUSTOMER(
        title = "Customer Opening Import",
        headings = listOf("sl_no", "name", "type", "mobile", "customer_number", "address", "ledger_page", "opening"),
        fields = listOf(
            ImportField("name", "Name", listOf("name", "customername", "partyname", "customer", "party")),
            ImportField("type", "Type", listOf("type", "partytype", "customertype")),
            ImportField("mobile", "Mobile", listOf("mobile", "phone", "contactnumber", "contactno", "cell")),
            ImportField("customer_number", "Customer No.", listOf("customernumber", "customerno", "idfrcode", "code", "accountcode", "ledgercode")),
            ImportField("address", "Address", listOf("address", "manualaddress", "location")),
            ImportField("ledger_page", "Ledger Page", listOf("ledgerpage", "ledgerpageno", "ledgerpagenumber", "ledger", "page")),
            ImportField("opening", "Opening", listOf("opening", "openingbalance", "balance", "due", "previousdue"), numeric = true),
        ),
        keyField = "name",
        checkPath = "contact/customer/opening-import/check",
        rowPath = "contact/customer/opening-import/row",
        valueOf = { num(it["opening"]) },
        existingNameKey = "party_name",
        noun = "customer",
    );
}

private fun num(v: String?) = v?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
private fun headingKey(v: String) = v.lowercase().replace(Regex("[^a-z0-9]"), "")

data class CheckResult(val status: String, val errors: List<String>, val notes: List<String>, val creates: List<String>, val name: String)

data class ImportRow(
    val id: Long,
    val data: Map<String, String>,
    val check: CheckResult? = null,
    val saved: Boolean = false,
    val voucherNo: String = "",
    val saveError: String = "",
)

data class OpeningImportUiState(
    val kind: OpeningImportKind,
    val openingOn: Boolean,
    val productTypes: List<SelectorOption> = emptyList(),
    val productType: SelectorOption? = null,
    val rows: List<ImportRow> = emptyList(),
    val pasteText: String = "",
    val showPaste: Boolean = false,
    val checking: Boolean = false,
    val savingDone: Int = 0,
    val savingTotal: Int = 0,
    val saving: Boolean = false,
    /** Set by any edit after a Check: Ok waits for a fresh one. */
    val stale: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val pending: List<ImportRow> get() = rows.filter { !it.saved }
    val canCheck: Boolean get() = openingOn && pending.isNotEmpty() && !checking && !saving
    val canSave: Boolean
        get() = canCheck && !stale && (kind != OpeningImportKind.PRODUCT || productType != null) &&
            pending.all { it.check != null && it.check.status != "error" }
}

class OpeningImportViewModel(
    private val kind: OpeningImportKind,
    private val api: WebApi,
    private val appContext: Context,
    openingOn: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OpeningImportUiState(kind = kind, openingOn = openingOn))
    val uiState: StateFlow<OpeningImportUiState> = _uiState.asStateFlow()

    init {
        if (kind == OpeningImportKind.PRODUCT) {
            viewModelScope.launch {
                (api.get("category/category-ddl") as? Resource.Success)?.let { r ->
                    val types = r.data.jPayload().jArr("product_type").jObjects().map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                    // Stock bought to sell is Inventory; the first by name would be Asset.
                    val inventory = types.firstOrNull { it.label.equals("inventory", ignoreCase = true) } ?: types.firstOrNull()
                    _uiState.update { it.copy(productTypes = types, productType = it.productType ?: inventory) }
                }
            }
        }
    }

    fun onProductType(o: SelectorOption) = _uiState.update { it.copy(productType = o) }
    fun togglePaste() = _uiState.update { it.copy(showPaste = !it.showPaste) }
    fun onPaste(v: String) = _uiState.update { it.copy(pasteText = v) }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            val table = withContext(Dispatchers.IO) {
                runCatching { SheetReader.read(appContext, uri) }
            }
            table.onSuccess { load(it) }
                .onFailure { e -> _uiState.update { it.copy(message = (e as? SheetReader.UnsupportedSheet)?.message ?: "This file could not be read.") } }
        }
    }

    fun loadPaste() {
        val text = _uiState.value.pasteText
        if (text.isBlank()) {
            _uiState.update { it.copy(message = "Paste the rows from Excel first, heading row included.") }
            return
        }
        load(SheetReader.fromPaste(text))
    }

    /** Columns matched by their headings rather than their places. */
    private fun load(table: List<List<String>>) {
        val lines = table.filter { line -> line.any { it.isNotBlank() } }
        if (lines.isEmpty()) {
            _uiState.update { it.copy(message = "The sheet has no rows.") }
            return
        }
        val headings = lines.first().map(::headingKey)
        val columnOf = kind.fields.associate { f -> f.key to headings.indexOfFirst { it in f.aliases } }
        if ((columnOf[kind.keyField] ?: -1) < 0) {
            _uiState.update { it.copy(message = "The first row must be the heading row: ${kind.headings.joinToString(", ")}") }
            return
        }
        val rows = lines.drop(1).map { line ->
            kind.fields.associate { f -> f.key to (columnOf[f.key]?.takeIf { it >= 0 }?.let { line.getOrNull(it) }?.trim().orEmpty()) }
        }.filter { r -> r.values.any { it.isNotEmpty() } }
        if (rows.isEmpty()) {
            _uiState.update { it.copy(message = "The sheet has no rows.") }
            return
        }
        val base = System.currentTimeMillis()
        _uiState.update {
            it.copy(rows = rows.mapIndexed { i, d -> ImportRow(base + i, d) }, stale = false, showPaste = false, pasteText = "")
        }
    }

    fun editRow(id: Long, data: Map<String, String>) {
        _uiState.update { s ->
            s.copy(rows = s.rows.map { if (it.id == id) it.copy(data = data, check = null, saveError = "") else it }, stale = true)
        }
    }

    fun removeRow(id: Long) = _uiState.update { s -> s.copy(rows = s.rows.filterNot { it.id == id }, stale = true) }
    fun clear() = _uiState.update { it.copy(rows = emptyList(), stale = false) }

    private fun JsonObject.rowJson(data: Map<String, String>) = apply { data.forEach { (k, v) -> addProperty(k, v) } }

    fun check() {
        val s = _uiState.value
        if (!s.canCheck) return
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            // Every row goes, saved ones too, so a row number in a message is the grid's own.
            val body = JsonObject().apply {
                add("rows", JsonArray().apply { s.rows.forEach { add(JsonObject().rowJson(it.data)) } })
            }
            when (val r = api.post(kind.checkPath, body)) {
                is Resource.Success -> {
                    val results = (r.data.get("data").jAsObj()?.get("data").jAsArr() ?: JsonArray()).map { el ->
                        el.jAsObj()?.let { o ->
                            CheckResult(
                                status = o.jStr("status"),
                                errors = o.jArr("errors").mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString },
                                notes = o.jArr("notes").mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString },
                                creates = o.jArr("creates").mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString },
                                name = o.jStr(kind.existingNameKey),
                            )
                        }
                    }
                    val updated = s.rows.mapIndexed { i, row -> if (row.saved) row else row.copy(check = results.getOrNull(i), saveError = "") }
                    val bad = updated.count { !it.saved && it.check?.status == "error" }
                    _uiState.update {
                        it.copy(
                            checking = false, rows = updated, stale = false,
                            message = if (bad > 0) "$bad row(s) need fixing before Ok." else "Every row is ready. Press Ok to save.",
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(checking = false, message = r.message.ifBlank { "The sheet could not be checked." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        val queue = s.pending
        _uiState.update { it.copy(saving = true, savingDone = 0, savingTotal = queue.size) }
        viewModelScope.launch {
            var failed = 0
            queue.forEachIndexed { index, row ->
                val body = JsonObject().apply {
                    add("row", JsonObject().rowJson(row.data))
                    if (kind == OpeningImportKind.PRODUCT) addProperty("product_type", s.productType?.id.orEmpty())
                }
                val r = api.postEnvelope(kind.rowPath, body)
                val outcome: (ImportRow) -> ImportRow = when {
                    r is Resource.Success && r.data.jBool("success") -> { x ->
                        x.copy(saved = true, voucherNo = r.data.jPayload().jStr("opening_vr_no"), saveError = "")
                    }
                    r is Resource.Success -> { x -> x.copy(saveError = WebApi.messageOf(r.data).ifBlank { "This row could not be saved." }) }
                    r is Resource.Error -> { x -> x.copy(saveError = r.message.ifBlank { "This row could not be saved." }) }
                    else -> { x -> x }
                }
                if (!(r is Resource.Success && r.data.jBool("success"))) failed++
                if ((r as? Resource.Error)?.isUnauthorized == true) {
                    _uiState.update { it.copy(saving = false, sessionExpired = true) }
                    return@launch
                }
                _uiState.update { st -> st.copy(rows = st.rows.map { if (it.id == row.id) outcome(it) else it }, savingDone = index + 1) }
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    message = if (failed > 0) "$failed row(s) were not saved. Fix them and press Ok again." else "${queue.size} ${kind.noun}(s) saved.",
                )
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: OpeningImportKind) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                OpeningImportViewModel(
                    kind, ServiceLocator.provideWebApi(app), app,
                    // The same branch switch the lists read before they offer an opening.
                    openingOn = ServiceLocator.provideSessionManager(app).state.value.settings?.openingOngoing == true,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningImportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: OpeningImportKind,
    viewModel: OpeningImportViewModel = viewModel(key = "opening-import-${kind.name}", factory = OpeningImportViewModel.provideFactory(LocalContext.current, kind)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(viewModel::loadFile) }
    var editing by remember { mutableStateOf<ImportRow?>(null) }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(
        title = kind.title,
        currentRoute = if (kind == OpeningImportKind.PRODUCT) Routes.PRODUCTS else Routes.CUSTOMERS,
        navController = navController, onLogout = onLogout,
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.openingOn) {
                    item {
                        Text(
                            if (kind == OpeningImportKind.PRODUCT) {
                                "Opening stock is switched off for this branch. Turn it on in the branch settings to import."
                            } else {
                                "Opening balances are switched off for this branch. Turn them on in the branch settings to import."
                            },
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.appColors.warning, MaterialTheme.shapes.small).padding(10.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.warning,
                        )
                    }
                }
                if (kind == OpeningImportKind.PRODUCT) {
                    item { AppSelectDropdown("Product Type (for new products)", state.productTypes, state.productType, viewModel::onProductType) }
                }
                item {
                    Text(
                        "Headings: ${kind.headings.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(text = "Upload", onClick = { picker.launch("*/*") }, modifier = Modifier.weight(1f))
                        SecondaryButton(text = if (state.showPaste) "Hide Paste Box" else "Paste Rows", onClick = viewModel::togglePaste, modifier = Modifier.weight(1f))
                    }
                }
                if (state.showPaste) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppTextField(
                                state.pasteText, viewModel::onPaste,
                                label = "Paste from Excel, heading row included:\n${kind.headings.joinToString("\t")}",
                                caption = "Rows", multiline = true, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            )
                            SecondaryButton(text = "Load Rows", onClick = viewModel::loadPaste)
                        }
                    }
                }
                if (state.rows.isNotEmpty()) {
                    item {
                        val pending = state.pending
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Rows: ${state.rows.size}", style = MaterialTheme.typography.bodySmall)
                            Text("Saved: ${state.rows.size - pending.size}", style = MaterialTheme.typography.bodySmall)
                            Text("New: ${pending.count { it.check?.status == "new" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.info)
                            Text("Existing: ${pending.count { it.check?.status == "existing" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.success)
                            Text("To fix: ${pending.count { it.check?.status == "error" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.danger)
                            Text("Opening value: ${kitMoney(state.rows.sumOf { kind.valueOf(it.data) })}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.rows.isEmpty()) {
                    item { ReportNote("Upload the Excel sheet or paste its rows.") }
                } else {
                    itemsIndexed(state.rows, key = { _, r -> r.id }) { i, row ->
                        val locked = row.saved || state.saving || state.checking
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .then(if (row.saved) Modifier.background(MaterialTheme.appColors.success.copy(alpha = 0.08f)) else Modifier)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${i + 1} ${row.data[kind.keyField].orEmpty()}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (!row.saved) {
                                        LinkButton(text = "Edit", enabled = !locked, onClick = { editing = row })
                                        LinkButton(text = "Remove", enabled = !locked, onClick = { viewModel.removeRow(row.id) }, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Text(
                                    kind.fields.filter { it.key != kind.keyField }.joinToString(" · ") { f -> "${f.header}: ${row.data[f.key].orEmpty().ifBlank { "-" }}" },
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                ImportStatus(row, state.stale, kind)
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.saving) Text("Saving ${state.savingDone} / ${state.savingTotal}...", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(text = "Clear", onClick = viewModel::clear, enabled = !state.saving && state.rows.isNotEmpty(), modifier = Modifier.weight(1f))
                            SecondaryButton(text = if (state.checking) "Checking..." else "Check", onClick = viewModel::check, enabled = state.canCheck, isLoading = state.checking, modifier = Modifier.weight(1f))
                            PrimaryButton(text = if (state.saving) "Saving..." else "Ok", onClick = viewModel::save, enabled = state.canSave, isLoading = state.saving, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    editing?.let { row ->
        val values = remember(row.id) { mutableStateMapOf<String, String>().apply { putAll(row.data) } }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Row") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kind.fields.forEach { f ->
                        AppTextField(
                            values[f.key].orEmpty(), { values[f.key] = it }, label = f.header, caption = f.header,
                            keyboardType = if (f.numeric) KeyboardType.Decimal else KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                LinkButton(text = "Save", onClick = {
                    viewModel.editRow(row.id, values.toMap())
                    editing = null
                })
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = { editing = null }) },
        )
    }
}

@Composable
private fun ImportStatus(row: ImportRow, stale: Boolean, kind: OpeningImportKind) {
    val c = MaterialTheme.appColors
    val check = row.check
    when {
        row.saved -> Text("Saved" + if (row.voucherNo.isNotBlank()) " · ${row.voucherNo}" else "", style = MaterialTheme.typography.labelSmall, color = c.success)
        row.saveError.isNotBlank() -> Text(row.saveError, style = MaterialTheme.typography.labelSmall, color = c.danger)
        check == null -> Text(if (stale) "Changed — press Check" else "Not checked", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        check.status == "error" -> Text(check.errors.joinToString(" "), style = MaterialTheme.typography.labelSmall, color = c.danger)
        else -> Column {
            Text(
                if (check.status == "new") "New ${kind.noun}" else "Existing: ${check.name} — " + if (kind == OpeningImportKind.PRODUCT) "prices update" else "its opening changes",
                style = MaterialTheme.typography.labelSmall,
                color = if (check.status == "new") c.info else c.success,
            )
            if (check.creates.isNotEmpty()) Text("Will create — ${check.creates.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            check.notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = c.warning) }
        }
    }
}
