package com.example.cashbookbd.ui.asset

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
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
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val KINDS = listOf(SelectorOption("asset", "Asset"), SelectorOption("expense", "Expense"))
private val STATUSES = listOf(SelectorOption("1", "In use"), SelectorOption("0", "Switched off"))
private val ALL_KINDS = SelectorOption("", "All kinds")
private val ALL_HEADS = SelectorOption("", "All heads")
private val ALL_STATUS = SelectorOption("", "All")
private const val PER_PAGE = 10

data class AssetCatalogueItem(
    val id: Long,
    val name: String,
    val code: String,
    val kind: String,
    val categoryId: String,
    val categoryName: String,
    val expenseHeadId: String,
    val headName: String,
    val unitId: String,
    val unit: String,
    val lastRate: Double,
    val active: Boolean,
)

data class AssetItemForm(
    val id: Long? = null,
    val name: String = "",
    val kind: String = "asset",
    val categoryId: String = "",
    val expenseHeadId: String = "",
    val code: String = "",
    val unitId: String = "",
    val lastRate: String = "",
    val status: String = "1",
)

data class AssetItemsUiState(
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val rows: List<AssetCatalogueItem> = emptyList(),
    val categories: List<SelectorOption> = emptyList(),
    val expenseHeads: List<SelectorOption> = emptyList(),
    val units: List<SelectorOption> = emptyList(),
    val form: AssetItemForm? = null,
    val search: String = "",
    val kindFilter: SelectorOption = ALL_KINDS,
    val headFilter: SelectorOption = ALL_HEADS,
    val statusFilter: SelectorOption = ALL_STATUS,
    val page: Int = 1,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** Only the heads some item of the chosen kind actually lands on. */
    val headOptions: List<SelectorOption>
        get() = listOf(ALL_HEADS) + rows
            .filter { kindFilter.id.isEmpty() || it.kind == kindFilter.id }
            .map { it.headName }.filter { it.isNotBlank() }.distinct().sorted()
            .map { SelectorOption(it, it) }

    /** Filters work on the list already loaded — a catalogue of a few dozen rows. */
    val filtered: List<AssetCatalogueItem>
        get() {
            val term = search.trim().lowercase()
            return rows.filter {
                (term.isEmpty() || it.name.lowercase().contains(term) || it.code.lowercase().contains(term)) &&
                    (kindFilter.id.isEmpty() || it.kind == kindFilter.id) &&
                    (headFilter.id.isEmpty() || it.headName == headFilter.id) &&
                    (statusFilter.id.isEmpty() || (if (it.active) "1" else "0") == statusFilter.id)
            }
        }

    val filtering: Boolean
        get() = search.isNotBlank() || kindFilter.id.isNotEmpty() || headFilter.id.isNotEmpty() || statusFilter.id.isNotEmpty()

    val lastPage: Int get() = maxOf(1, (filtered.size + PER_PAGE - 1) / PER_PAGE)
}

/**
 * Fixed Assets → Items (web AssetItemsTab, 36044207 / beac625b): the short
 * list a purchase is picked from. An item is neither stock nor an asset; its
 * CATEGORY nominates the ledger head a purchase debits — or, for an expense
 * item, its Expense head is charged the same day. Removing switches it off.
 */
class AssetItemsViewModel(
    private val api: WebApi,
    private val products: ProductRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssetItemsUiState())
    val uiState: StateFlow<AssetItemsUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            // The same unit list a product is measured in.
            (products.loadProductFormOptions() as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(units = r.data.units) }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.get("asset/items")) {
                is Resource.Success -> {
                    val data = r.data.jPayload()
                    fun options(key: String) = data.jObjs(key).map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rows = data.jObjs("rows").map { o ->
                                AssetCatalogueItem(
                                    id = o.jStr("id").toLongOrNull() ?: 0L,
                                    name = o.jStr("name"),
                                    code = o.jStr("code"),
                                    kind = o.jStr("kind").ifBlank { "asset" },
                                    categoryId = o.jStr("category_id"),
                                    categoryName = o.jStr("category_name"),
                                    expenseHeadId = o.jStr("expense_coa4_id"),
                                    headName = o.jStr("head_name"),
                                    unitId = o.jStr("unit_id"),
                                    unit = o.jStr("unit"),
                                    lastRate = o.jNum("last_rate"),
                                    active = o.jStr("status") == "1",
                                )
                            },
                            categories = options("categories"),
                            expenseHeads = options("expense_heads"),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Could not read the items" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearch(v: String) = _uiState.update { it.copy(search = v, page = 1) }
    // A head of the other kind would leave the list empty for no visible reason.
    fun onKindFilter(o: SelectorOption) = _uiState.update { it.copy(kindFilter = o, headFilter = ALL_HEADS, page = 1) }
    fun onHeadFilter(o: SelectorOption) = _uiState.update { it.copy(headFilter = o, page = 1) }
    fun onStatusFilter(o: SelectorOption) = _uiState.update { it.copy(statusFilter = o, page = 1) }
    fun onPage(p: Int) = _uiState.update { it.copy(page = p.coerceIn(1, it.lastPage)) }

    fun newItem() = _uiState.update { it.copy(form = AssetItemForm()) }
    fun cancel() = _uiState.update { it.copy(form = null) }
    fun onForm(f: AssetItemForm) = _uiState.update { it.copy(form = f) }
    fun edit(row: AssetCatalogueItem) = _uiState.update {
        it.copy(
            form = AssetItemForm(
                id = row.id, name = row.name, kind = row.kind, categoryId = row.categoryId,
                expenseHeadId = row.expenseHeadId.takeIf { h -> h != "0" }.orEmpty(), code = row.code,
                unitId = row.unitId.takeIf { u -> u != "0" }.orEmpty(),
                lastRate = if (row.lastRate == 0.0) "" else row.lastRate.toString(),
                status = if (row.active) "1" else "0",
            ),
        )
    }

    fun save() {
        val f = _uiState.value.form ?: return
        val isExpense = f.kind == "expense"
        val problem = when {
            f.name.isBlank() -> "What is the item called?"
            !isExpense && f.categoryId.isBlank() -> "Choose the category — it decides where the cost lands."
            isExpense && f.expenseHeadId.isBlank() -> "Choose the expense head — it is where the cost lands."
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(message = problem) }
            return
        }
        val body = JsonObject().apply {
            f.id?.let { addProperty("id", it) } ?: add("id", JsonNull.INSTANCE)
            addProperty("name", f.name)
            addProperty("kind", f.kind)
            if (isExpense) add("category_id", JsonNull.INSTANCE) else addProperty("category_id", f.categoryId.toLong())
            if (isExpense) addProperty("expense_coa4_id", f.expenseHeadId.toLong()) else add("expense_coa4_id", JsonNull.INSTANCE)
            if (f.code.isBlank()) add("code", JsonNull.INSTANCE) else addProperty("code", f.code)
            if (f.unitId.isBlank()) add("unit_id", JsonNull.INSTANCE) else addProperty("unit_id", f.unitId.toLong())
            addProperty("last_rate", f.lastRate.toDoubleOrNull() ?: 0.0)
            addProperty("status", f.status.toInt())
        }
        write("asset/items/store", body, "Saved") { _uiState.update { it.copy(form = null) } }
    }

    fun switchOff(row: AssetCatalogueItem) = write("asset/items/delete/${row.id}", JsonObject(), "Switched off") {}

    private fun write(path: String, body: JsonObject, fallback: String, onOk: () -> Unit) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = api.post(path, body)) {
                is Resource.Success -> {
                    onOk()
                    _uiState.update { it.copy(saving = false, message = r.data.jStr("message").ifBlank { fallback }) }
                    load()
                }
                is Resource.Error -> _uiState.update { it.copy(saving = false, message = r.message, sessionExpired = r.isUnauthorized) }
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
                AssetItemsViewModel(ServiceLocator.provideWebApi(app), ServiceLocator.provideProductRepository(app))
            }
        }
    }
}

@Composable
fun AssetItemsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: AssetItemsViewModel = viewModel(factory = AssetItemsViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textOnScreenMuted
    val pageRows = state.filtered.drop((state.page - 1) * PER_PAGE).take(PER_PAGE)

    AuthenticatedShell(title = "Items", currentRoute = AssetMenu.ROUTE_HOME, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val f = state.form
                    if (f == null) {
                        PrimaryButton(text = "Add an item", onClick = viewModel::newItem)
                    } else {
                        ItemForm(f, state, viewModel)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(state.search, viewModel::onSearch, label = "Item name or code", caption = "Search", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSelectDropdown("Kind", listOf(ALL_KINDS) + KINDS, state.kindFilter, viewModel::onKindFilter, Modifier.weight(1f))
                            AppSelectDropdown("Status", listOf(ALL_STATUS) + STATUSES, state.statusFilter, viewModel::onStatusFilter, Modifier.weight(1f))
                        }
                        AppSelectDropdown("Head", state.headOptions, state.headFilter, viewModel::onHeadFilter)
                        Text("${state.filtered.size} / ${state.rows.size}", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
                if (!state.isLoading && pageRows.isEmpty()) {
                    item {
                        ReportNote(
                            if (state.filtering) "No item matches these filters."
                            else "No items yet. Add one under a category — that category is what decides where its cost lands.",
                        )
                    }
                }
                items(pageRows, key = { it.id }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.name, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                if (row.code.isNotBlank()) Text(row.code, style = MaterialTheme.typography.labelSmall, color = muted)
                                // The head is the account a purchase of this item will debit.
                                Text(
                                    "${if (row.kind == "expense") "Expense" else row.categoryName.ifBlank { "—" }} · " +
                                        row.headName.ifBlank { "no head nominated" },
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                Text(
                                    "Unit: ${row.unit.ifBlank { "—" }} · Last Rate: ${if (row.lastRate > 0) kitMoney(row.lastRate) else "—"} · " +
                                        if (row.active) "In use" else "Switched off",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                LinkButton(text = "Edit", onClick = { viewModel.edit(row) })
                                if (row.active) {
                                    LinkButton(
                                        text = "Switch off", enabled = !state.saving,
                                        onClick = { viewModel.switchOff(row) }, color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.lastPage > 1) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            LinkButton(text = "Previous", enabled = state.page > 1, onClick = { viewModel.onPage(state.page - 1) })
                            Text("Page ${state.page} of ${state.lastPage}", style = MaterialTheme.typography.labelMedium)
                            LinkButton(text = "Next", enabled = state.page < state.lastPage, onClick = { viewModel.onPage(state.page + 1) })
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ItemForm(f: AssetItemForm, state: AssetItemsUiState, viewModel: AssetItemsViewModel) {
    val unitOptions = listOf(SelectorOption("", "Select a unit")) + state.units
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (f.id != null) "Editing ${f.name.ifBlank { "an item" }}" else "New item",
                fontWeight = AppFontWeight.SemiBold,
            )
            AppTextField(f.name, { viewModel.onForm(f.copy(name = it)) }, label = "Office chair, executive", caption = "Item Name *", modifier = Modifier.fillMaxWidth())
            AppSelectDropdown("Kind *", KINDS, KINDS.firstOrNull { it.id == f.kind }, { viewModel.onForm(f.copy(kind = it.id)) })
            if (f.kind == "expense") {
                AppSelectDropdown(
                    "Expense Head *", state.expenseHeads, state.expenseHeads.firstOrNull { it.id == f.expenseHeadId },
                    { viewModel.onForm(f.copy(expenseHeadId = it.id)) }, placeholder = "Select an expense head",
                )
            } else {
                AppSelectDropdown(
                    "Category *", state.categories, state.categories.firstOrNull { it.id == f.categoryId },
                    { viewModel.onForm(f.copy(categoryId = it.id)) }, placeholder = "Select a category",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(f.code, { viewModel.onForm(f.copy(code = it)) }, label = "CHR-01", caption = "Code", modifier = Modifier.weight(1f))
                AppSelectDropdown("Unit", unitOptions, unitOptions.firstOrNull { it.id == f.unitId }, { viewModel.onForm(f.copy(unitId = it.id)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    f.lastRate, { v -> viewModel.onForm(f.copy(lastRate = v.filter { c -> c.isDigit() || c == '.' })) },
                    label = "0.00", caption = "Last Rate (Tk.)", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                )
                AppSelectDropdown("Status", STATUSES, STATUSES.firstOrNull { it.id == f.status }, { viewModel.onForm(f.copy(status = it.id)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = if (f.id != null) "Save item" else "Add item", onClick = viewModel::save, isLoading = state.saving)
                SecondaryButton(text = "Cancel", onClick = viewModel::cancel)
            }
        }
    }
}
