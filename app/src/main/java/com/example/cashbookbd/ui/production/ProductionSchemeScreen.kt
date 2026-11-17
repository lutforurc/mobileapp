package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.example.cashbookbd.data.repository.ProductionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.production.model.ProductionSchemeRow
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchemeUiState(
    val rows: List<ProductionSchemeRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val canWrite: Boolean = false,

    val id: Long? = null,
    val name: String = "",
    val productId: String = "",
    val productName: String = "",
    val sameProduct: Boolean = true,
    val freeProductId: String = "",
    val freeProductName: String = "",
    val threshold: String = "",
    val freeQty: String = "",
    val repeatable: Boolean = false,
    val from: String = "",
    val to: String = "",
    val note: String = "",
    val isSaving: Boolean = false,

    val confirmRetire: ProductionSchemeRow? = null,
    val isRetiring: Boolean = false,
) {
    val saveLabel: String get() = if (id != null) "Update" else "Save"

    /** ø "যত কিনবে তত ফ্রি · প্রতি ১০-এ ১" — the repeat switch in words. */
    val repeatLabel: String
        get() {
            val t = threshold.toIntOrNull() ?: 0
            val f = freeQty.toIntOrNull() ?: 0
            return if (t > 0 && f > 0) {
                "যত কিনবে তত ফ্রি · প্রতি ${banglaDigits(t.toString())}-এ ${banglaDigits(f.toString())}"
            } else {
                "যত কিনবে তত ফ্রি"
            }
        }
}

private fun banglaDigits(value: String): String {
    val digits = "০১২৩৪৫৬৭৮৯"
    return value.map { c -> if (c in '0'..'9') digits[c - '0'] else c }.joinToString("")
}

class SchemeViewModel(
    private val repository: ProductionRepository,
    canWrite: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchemeUiState(canWrite = canWrite))
    val uiState: StateFlow<SchemeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchSchemes()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data)
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

    fun onName(v: String) = _uiState.update { it.copy(name = v) }

    fun onProduct(id: String, name: String) = _uiState.update { state ->
        state.copy(
            productId = id,
            productName = name,
            // The free product starts as the same one; picking a paid product
            // never silently overwrites a different free product already chosen.
            freeProductId = if (state.sameProduct) id else state.freeProductId,
            freeProductName = if (state.sameProduct) name else state.freeProductName,
        )
    }

    fun onSameProduct(on: Boolean) = _uiState.update { state ->
        state.copy(
            sameProduct = on,
            freeProductId = if (on) state.productId else state.freeProductId,
            freeProductName = if (on) state.productName else state.freeProductName,
        )
    }

    fun onFreeProduct(id: String, name: String) =
        _uiState.update { it.copy(freeProductId = id, freeProductName = name) }

    fun onThreshold(v: String) = _uiState.update { it.copy(threshold = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onFreeQty(v: String) = _uiState.update { it.copy(freeQty = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onRepeatable(on: Boolean) = _uiState.update { it.copy(repeatable = on) }
    fun onFrom(date: SimpleDate) = _uiState.update { it.copy(from = date.toApi()) }
    fun onTo(date: SimpleDate) = _uiState.update { it.copy(to = date.toApi()) }
    fun onNote(v: String) = _uiState.update { it.copy(note = v.take(255)) }

    fun edit(row: ProductionSchemeRow) = _uiState.update {
        it.copy(
            id = row.id,
            name = row.name,
            productId = row.productId?.toString().orEmpty(),
            productName = row.productName,
            sameProduct = row.freeProductId == null,
            freeProductId = (row.freeProductId ?: row.productId)?.toString().orEmpty(),
            freeProductName = row.freeProductName.ifBlank { row.productName },
            threshold = plain(row.thresholdQty),
            freeQty = plain(row.freeQty),
            repeatable = row.repeatable,
            from = row.fromDate,
            to = row.toDate,
            note = row.note,
            error = null,
        )
    }

    fun reset() = _uiState.update {
        it.copy(
            id = null, name = "", productId = "", productName = "", sameProduct = true,
            freeProductId = "", freeProductName = "", threshold = "", freeQty = "",
            repeatable = false, from = "", to = "", note = "", error = null,
        )
    }

    fun save() {
        val state = _uiState.value
        if (!state.canWrite || state.isSaving) return
        val problem = when {
            state.name.isBlank() -> "Give the scheme a name."
            state.productId.toLongOrNull() == null -> "Pick the product the scheme is on."
            (state.threshold.toDoubleOrNull() ?: 0.0) <= 0 ||
                (state.freeQty.toDoubleOrNull() ?: 0.0) <= 0 ->
                "The threshold and the free quantity must both be above 0."
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(error = problem) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("name", state.name.trim())
                addProperty("product_id", state.productId.toLongOrNull() ?: 0L)
                add(
                    "free_product_id",
                    if (state.sameProduct || state.freeProductId.isBlank()) JsonNull.INSTANCE
                    else JsonPrimitive(state.freeProductId.toLongOrNull() ?: 0L),
                )
                addProperty("threshold_qty", state.threshold.toDoubleOrNull() ?: 0.0)
                addProperty("free_qty", state.freeQty.toDoubleOrNull() ?: 0.0)
                addProperty("repeatable", if (state.repeatable) 1 else 0)
                add("from_date", state.from.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("to_date", state.to.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("note", state.note.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            }
            when (val result = repository.saveScheme(state.id, body)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    reset()
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onRetireRequested(row: ProductionSchemeRow) = _uiState.update { it.copy(confirmRetire = row) }
    fun onRetireDismissed() = _uiState.update { it.copy(confirmRetire = null) }

    fun retire() {
        val state = _uiState.value
        val row = state.confirmRetire ?: return
        if (state.isRetiring) return
        _uiState.update { it.copy(isRetiring = true, confirmRetire = null) }
        viewModelScope.launch {
            when (val result = repository.retireScheme(row.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isRetiring = false, message = "Scheme retired") }
                    if (state.id == row.id) reset()
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isRetiring = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                SchemeViewModel(
                    repository = ServiceLocator.provideProductionRepository(app),
                    canWrite = com.example.cashbookbd.session.Permissions.has(
                        ServiceLocator.provideSessionManager(app).permissions,
                        "production.create",
                    ),
                )
            }
        }
    }
}

@Composable
fun ProductionSchemeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchemeViewModel = viewModel(
        factory = SchemeViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val invoiceRepository = remember { ServiceLocator.provideInvoiceRepository(context) }
    val searchProducts: suspend (String) -> Resource<List<SelectorOption>> = { query ->
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> Resource.Success(
                result.data.map {
                    SelectorOption(it.id, it.dropdownLabel, sublabel = it.unit.ifBlank { null })
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.error) {
        val text = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onErrorShown()
    }

    AuthenticatedShell(
        title = "Scheme",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (state.id != null) "Editing ${state.name}" else "New scheme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.SemiBold,
                )
                if (!state.canWrite) {
                    Text(
                        text = "You can read schemes but not change them (production.create is not granted).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }

                AppTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = "e.g. Buy 100 get 2",
                    caption = "Scheme name",
                    modifier = Modifier.fillMaxWidth(),
                )

                SearchableSelectDropdown(
                    selected = state.productId.takeIf { it.isNotBlank() }?.let {
                        SelectorOption(it, state.productName)
                    },
                    onSelected = { viewModel.onProduct(it.id, it.label) },
                    search = searchProducts,
                    label = "Product",
                    placeholder = "Type a product name…",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Free item",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("একই পণ্য ফ্রি", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(6.dp))
                    Switch(checked = state.sameProduct, onCheckedChange = viewModel::onSameProduct)
                }

                if (state.sameProduct) {
                    AppTextField(
                        value = state.productName,
                        onValueChange = {},
                        label = "Same as the product",
                        caption = "Free item",
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SearchableSelectDropdown(
                        selected = state.freeProductId.takeIf { it.isNotBlank() }?.let {
                            SelectorOption(it, state.freeProductName)
                        },
                        onSelected = { viewModel.onFreeProduct(it.id, it.label) },
                        search = searchProducts,
                        label = "Free item",
                        placeholder = "Type a product name…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.threshold,
                        onValueChange = viewModel::onThreshold,
                        label = "",
                        caption = "Buy quantity",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.freeQty,
                        onValueChange = viewModel::onFreeQty,
                        label = "",
                        caption = "Free quantity",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDateField(
                        label = "From",
                        date = SimpleDate.fromApi(state.from),
                        onPicked = viewModel::onFrom,
                        modifier = Modifier.weight(1f),
                    )
                    AppDateField(
                        label = "To",
                        date = SimpleDate.fromApi(state.to),
                        onPicked = viewModel::onTo,
                        modifier = Modifier.weight(1f),
                    )
                }

                AppTextField(
                    value = state.note,
                    onValueChange = viewModel::onNote,
                    label = "Optional",
                    caption = "Note",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.repeatLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.repeatable, onCheckedChange = viewModel::onRepeatable)
                }
                Text(
                    text = "From and To left empty: the scheme runs until it is retired.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = state.saveLabel,
                        onClick = viewModel::save,
                        enabled = state.canWrite,
                        isLoading = state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Reset",
                        onClick = viewModel::reset,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Schemes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.SemiBold,
                )

                if (state.rows.isEmpty()) {
                    Text(
                        text = if (state.isLoading) "Loading…" else "No scheme yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                } else {
                    state.rows.forEach { row ->
                        SchemeRowCard(
                            row = row,
                            canWrite = state.canWrite,
                            onEdit = { viewModel.edit(row) },
                            onRetire = { viewModel.onRetireRequested(row) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.confirmRetire?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::onRetireDismissed,
            title = { Text("Retire scheme") },
            text = {
                Text("Retire \"${row.name}\"? The bills it already made keep it; it stops matching new sales.")
            },
            confirmButton = {
                LinkButton(
                    text = "Retire",
                    onClick = viewModel::retire,
                    color = MaterialTheme.appColors.danger,
                    enabled = !state.isRetiring,
                )
            },
            dismissButton = {
                LinkButton(text = "Cancel", onClick = viewModel::onRetireDismissed)
            },
        )
    }
}

@Composable
private fun SchemeRowCard(
    row: ProductionSchemeRow,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onRetire: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
            )
            Text(
                text = "${row.productLabel} · buy ${trim(row.thresholdQty)} get ${trim(row.freeQty)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Text(
                text = "${row.freeLabel} · ${row.datesLabel} · " +
                    (if (row.repeatable) "Repeats" else "Once") + " · ${row.status}",
                style = MaterialTheme.typography.bodySmall,
                color = if (row.isActive) MaterialTheme.appColors.success
                else MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit scheme", modifier = Modifier.size(18.dp))
        }
        if (canWrite && row.isActive) {
            IconButton(onClick = onRetire) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Retire scheme",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.appColors.danger,
                )
            }
        }
    }
}

private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
