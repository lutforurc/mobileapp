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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelAmenityDue
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.data.repository.InventoryMovementRepository
import com.example.cashbookbd.data.repository.InventoryProduct
import com.example.cashbookbd.data.repository.MaterialIssueLine
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

data class HotelAmenityIssueUiState(
    val isLoading: Boolean = false,
    val from: SimpleDate = SimpleDate.today().plusDays(-30),
    val to: SimpleDate = SimpleDate.today().plusDays(-1),
    val buildings: List<SelectorOption> = emptyList(),
    val building: SelectorOption? = null,
    val warehouses: List<SelectorOption> = emptyList(),
    val warehouse: SelectorOption? = null,
    val due: HotelAmenityDue? = null,
    val lines: List<MaterialIssueLine> = emptyList(),
    val receivedBy: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = lines.isNotEmpty() && building != null && !isSaving
}

/**
 * Soap and towels still leave the store through material issue, on one
 * voucher — this screen only works the quantity out from the amenity kits
 * and the nights already sold, then hands an ordinary editable issue form
 * to the storekeeper. Pressed twice over the same dates, "Load" answers
 * with the remainder, never a second batch.
 */
class HotelAmenityIssueViewModel(
    private val setupRepository: HotelSetupRepository,
    private val inventoryRepository: InventoryMovementRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelAmenityIssueUiState())
    val uiState: StateFlow<HotelAmenityIssueUiState> = _uiState.asStateFlow()

    private var lastProducts: List<InventoryProduct> = emptyList()

    init {
        loadBuildings()
        loadWarehouses()
    }

    private fun loadBuildings() {
        viewModelScope.launch {
            when (val result = setupRepository.fetchBuildingDdl()) {
                is Resource.Success -> _uiState.update {
                    val options = result.data.map { SelectorOption(it.value.toString(), it.label) }
                    it.copy(buildings = options, building = it.building ?: options.singleOrNull())
                }
                else -> Unit
            }
        }
    }

    private fun loadWarehouses() {
        viewModelScope.launch {
            when (val result = selectorRepository.fetch(ReportSelectorSource.WAREHOUSE)) {
                is Resource.Success -> _uiState.update { it.copy(warehouses = result.data) }
                else -> Unit
            }
        }
    }

    fun onFrom(date: SimpleDate) = _uiState.update { it.copy(from = date, due = null) }
    fun onTo(date: SimpleDate) = _uiState.update { it.copy(to = date, due = null) }
    fun onBuilding(option: SelectorOption) = _uiState.update { it.copy(building = option, due = null) }
    fun onWarehouse(option: SelectorOption) = _uiState.update { it.copy(warehouse = option) }
    fun onReceivedBy(v: String) = _uiState.update { it.copy(receivedBy = v) }
    fun onNote(v: String) = _uiState.update { it.copy(note = v) }

    fun loadDue() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = setupRepository.fetchAmenityDue(
                from = state.from.toApi(),
                to = state.to.toApi(),
                buildingId = state.building?.id?.toLongOrNull(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    val loaded = result.data.rows.map { row ->
                        MaterialIssueLine(
                            product = InventoryProduct(row.productId.toString(), row.productName, row.unitName, null),
                            qty = row.due,
                            building = "",
                            workItem = "",
                            note = "",
                        )
                    }
                    // Loading again keeps what a clerk already added by hand and
                    // only replaces the kit-worked-out lines, so a second press
                    // does not throw away a manual addition.
                    val manual = it.lines.filterNot { line -> result.data.rows.any { r -> r.productId.toString() == line.product.id } }
                    it.copy(isLoading = false, due = result.data, lines = loaded + manual)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = inventoryRepository.searchProducts(query)) {
            is Resource.Success -> {
                lastProducts = result.data
                Resource.Success(result.data.map { SelectorOption(it.id, it.name, it.unit.takeIf { u -> u.isNotBlank() }) })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun addProduct(option: SelectorOption) {
        val product = lastProducts.firstOrNull { it.id == option.id }
            ?: InventoryProduct(option.id, option.label, option.sublabel.orEmpty(), null)
        _uiState.update { s ->
            if (s.lines.any { it.product.id == product.id }) return@update s
            s.copy(lines = s.lines + MaterialIssueLine(product, 1.0, "", "", ""))
        }
    }

    fun onLineQty(index: Int, value: String) = _uiState.update {
        val lines = it.lines.toMutableList()
        if (index !in lines.indices) return@update it
        lines[index] = lines[index].copy(qty = value.toDoubleOrNull() ?: 0.0)
        it.copy(lines = lines)
    }

    fun removeLine(index: Int) = _uiState.update { it.copy(lines = it.lines.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val state = _uiState.value
        val buildingId = state.building?.id?.toLongOrNull() ?: return
        if (!state.canSave) return
        if (state.lines.any { it.qty <= 0 }) {
            return _uiState.update { it.copy(message = "Every line needs a quantity above zero.") }
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = inventoryRepository.submitMaterialIssue(
                endpoint = "material-issue/store",
                issueDate = SimpleDate.today().toApi(),
                fromWarehouseId = state.warehouse?.id?.toLongOrNull(),
                hotelBuildingId = buildingId,
                receivedBy = state.receivedBy,
                note = state.note,
                lines = state.lines,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isSaving = false, message = result.data, saved = true) }
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
                val appContext = context.applicationContext
                HotelAmenityIssueViewModel(
                    setupRepository = HotelSetupRepository.get(appContext),
                    inventoryRepository = ServiceLocator.provideInventoryMovementRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun HotelAmenityIssueScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelAmenityIssueViewModel = viewModel(factory = HotelAmenityIssueViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
        if (state.saved) navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Amenity Issue",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = "From",
                            value = state.from.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickMoneyDate(context, state.from, viewModel::onFrom) },
                        )
                        PickerField(
                            label = "To",
                            value = state.to.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickMoneyDate(context, state.to, viewModel::onTo) },
                        )
                    }
                }
                item {
                    AppSelectDropdown(
                        label = "Building",
                        options = state.buildings,
                        selected = state.building,
                        onSelected = viewModel::onBuilding,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PrimaryButton(
                        text = "Load from amenity kits",
                        onClick = viewModel::loadDue,
                        enabled = state.building != null && !state.isLoading,
                        isLoading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.due?.let { due ->
                    item {
                        Text(
                            text = due.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                    if (due.settled.isNotEmpty()) {
                        item {
                            Text(
                                text = "Already covered: " + due.settled.joinToString(", ") { "${it.productName} (${it.issued})" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.success,
                            )
                        }
                    }
                }
                item { HotelSectionTitle("Lines") }
                if (state.lines.isEmpty()) {
                    item {
                        Text(
                            "Nothing yet — load from the kits, or add a product by hand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                }
                items(state.lines.size, key = { state.lines[it].product.id }) { index ->
                    val line = state.lines[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.SemiBold)
                                Text(line.product.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
                            }
                            AppTextField(
                                value = if (line.qty == line.qty.toLong().toDouble()) line.qty.toLong().toString() else line.qty.toString(),
                                onValueChange = { v -> viewModel.onLineQty(index, v) },
                                label = "Qty",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.width(90.dp),
                            )
                            LinkButton(text = "✕", onClick = { viewModel.removeLine(index) }, color = MaterialTheme.appColors.danger)
                        }
                    }
                }
                item {
                    SearchableSelectDropdown(
                        selected = null,
                        onSelected = viewModel::addProduct,
                        search = viewModel::searchProducts,
                        modifier = Modifier.fillMaxWidth(),
                        label = "Add a product",
                        placeholder = "Type a product name…",
                        emptyText = "No product by that name",
                    )
                }
                item {
                    AppSelectDropdown(
                        label = "From warehouse (optional)",
                        options = state.warehouses,
                        selected = state.warehouse,
                        onSelected = viewModel::onWarehouse,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    AppTextField(
                        value = state.receivedBy,
                        onValueChange = viewModel::onReceivedBy,
                        label = "Received by",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    AppTextField(
                        value = state.note,
                        onValueChange = viewModel::onNote,
                        label = "Note",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PrimaryButton(
                        text = "Save the issue",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
