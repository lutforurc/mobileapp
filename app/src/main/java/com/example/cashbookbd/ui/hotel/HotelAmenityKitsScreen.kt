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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
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
import com.example.cashbookbd.data.repository.HotelAmenityKit
import com.example.cashbookbd.data.repository.HotelAmenityKitItem
import com.example.cashbookbd.data.repository.HotelAmenityKitsView
import com.example.cashbookbd.data.repository.HotelAmenityRoomType
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.data.repository.InventoryMovementRepository
import com.example.cashbookbd.data.repository.InventoryProduct
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val BASIS_OPTIONS = listOf(
    SelectorOption("room", "Per room — counted once, however many people"),
    SelectorOption("guest", "Per guest — counted for each person that night"),
)

data class AmenityKitDraftItem(
    val product: InventoryProduct,
    val quantity: String,
    val basis: String = "room",
)

data class AmenityKitDraft(
    val roomTypeId: Long,
    val roomTypeName: String,
    val name: String,
    val notes: String = "",
    val status: Boolean = true,
    val items: List<AmenityKitDraftItem> = emptyList(),
    val existingId: Long? = null,
)

data class HotelAmenityKitsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val view: HotelAmenityKitsView? = null,
    val editing: AmenityKitDraft? = null,
    val isSaving: Boolean = false,
    val confirmDeleteId: Long? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * What a room of each kind is made up with — §4.3. This screen issues
 * nothing and moves no stock; it writes the STANDARD an issue is later
 * measured against. One kit per room type — saving replaces it whole.
 */
class HotelAmenityKitsViewModel(
    private val repository: HotelSetupRepository,
    private val inventoryRepository: InventoryMovementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelAmenityKitsUiState())
    val uiState: StateFlow<HotelAmenityKitsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchAmenityKits()) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, view = result.data) }
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

    fun edit(roomType: HotelAmenityRoomType, existing: HotelAmenityKit?) {
        _uiState.update {
            it.copy(
                editing = AmenityKitDraft(
                    roomTypeId = roomType.id,
                    roomTypeName = roomType.name,
                    name = existing?.name ?: "${roomType.name} kit",
                    notes = existing?.notes.orEmpty(),
                    status = existing?.status ?: true,
                    items = existing?.items.orEmpty().map { i ->
                        AmenityKitDraftItem(
                            product = InventoryProduct(i.productId.toString(), i.productName, i.unitName, null),
                            quantity = if (i.quantity == i.quantity.toLong().toDouble()) i.quantity.toLong().toString() else i.quantity.toString(),
                            basis = i.basis,
                        )
                    },
                    existingId = existing?.id,
                )
            )
        }
    }

    fun cancelEdit() = _uiState.update { it.copy(editing = null) }

    fun onName(v: String) = _uiState.update { it.copy(editing = it.editing?.copy(name = v)) }
    fun onNotes(v: String) = _uiState.update { it.copy(editing = it.editing?.copy(notes = v)) }
    fun onStatus(v: Boolean) = _uiState.update { it.copy(editing = it.editing?.copy(status = v)) }

    fun addItem(product: InventoryProduct) = _uiState.update {
        val draft = it.editing ?: return@update it
        if (draft.items.any { line -> line.product.id == product.id }) {
            return@update it.copy(message = "${product.name} is on the kit already.")
        }
        it.copy(editing = draft.copy(items = draft.items + AmenityKitDraftItem(product, "1")))
    }

    fun onItemQuantity(index: Int, value: String) = _uiState.update {
        val draft = it.editing ?: return@update it
        val items = draft.items.toMutableList()
        if (index !in items.indices) return@update it
        items[index] = items[index].copy(quantity = value.filter { c -> c.isDigit() || c == '.' })
        it.copy(editing = draft.copy(items = items))
    }

    fun onItemBasis(index: Int, basis: String) = _uiState.update {
        val draft = it.editing ?: return@update it
        val items = draft.items.toMutableList()
        if (index !in items.indices) return@update it
        items[index] = items[index].copy(basis = basis)
        it.copy(editing = draft.copy(items = items))
    }

    fun removeItem(index: Int) = _uiState.update {
        val draft = it.editing ?: return@update it
        it.copy(editing = draft.copy(items = draft.items.filterIndexed { i, _ -> i != index }))
    }

    /** The last search's results, so a picked option can be turned back into a product. */
    private var lastProducts: List<InventoryProduct> = emptyList()

    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = inventoryRepository.searchProducts(query)) {
            is Resource.Success -> {
                lastProducts = result.data
                Resource.Success(result.data.map { SelectorOption(it.id, it.name, it.unit.takeIf { u -> u.isNotBlank() }) })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onProductPicked(option: SelectorOption) {
        val product = lastProducts.firstOrNull { it.id == option.id }
            ?: InventoryProduct(option.id, option.label, option.sublabel.orEmpty(), null)
        addItem(product)
    }

    fun save() {
        val draft = _uiState.value.editing ?: return
        if (draft.name.isBlank()) return _uiState.update { it.copy(message = "Give the kit a name.") }
        val items = draft.items.map {
            HotelAmenityKitItem(
                id = 0L,
                productId = it.product.id.toLongOrNull() ?: 0L,
                productName = it.product.name,
                unitName = it.product.unit,
                quantity = it.quantity.toDoubleOrNull() ?: 0.0,
                basis = it.basis,
                notes = "",
            )
        }
        if (items.any { it.quantity <= 0 }) {
            return _uiState.update { it.copy(message = "Every line needs a quantity above zero.") }
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.saveAmenityKit(draft.roomTypeId, draft.name, draft.notes, draft.status, items)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, editing = null, message = "Kit saved") }
                    load()
                }
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

    fun askDelete(id: Long) = _uiState.update { it.copy(confirmDeleteId = id) }
    fun dismissDelete() = _uiState.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = _uiState.value.confirmDeleteId ?: return
        _uiState.update { it.copy(confirmDeleteId = null, isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.deleteAmenityKit(id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    load()
                }
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
                HotelAmenityKitsViewModel(
                    repository = HotelSetupRepository.get(appContext),
                    inventoryRepository = ServiceLocator.provideInventoryMovementRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun HotelAmenityKitsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelAmenityKitsViewModel = viewModel(factory = HotelAmenityKitsViewModel.provideFactory(LocalContext.current)),
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
    }

    AuthenticatedShell(
        title = "Amenity Kits",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.view == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.view == null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }
                else -> state.view?.let { view -> AmenityKitsBody(view, viewModel) }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.editing?.let { draft ->
        AmenityKitEditDialog(draft = draft, isSaving = state.isSaving, context = context, viewModel = viewModel)
    }

    state.confirmDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Remove this kit?") },
            text = { Text("The items go with it. What has already been issued is untouched — only the standard goes.") },
            confirmButton = {
                PrimaryButton(text = "Remove", onClick = viewModel::confirmDelete, containerColor = MaterialTheme.appColors.danger, compact = true)
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissDelete) },
        )
    }
}

@Composable
private fun AmenityKitsBody(view: HotelAmenityKitsView, viewModel: HotelAmenityKitsViewModel) {
    val muted = MaterialTheme.appColors.textMuted
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(view.note, style = MaterialTheme.typography.labelSmall, color = muted)
        }
        items(view.roomTypes.size, key = { view.roomTypes[it].id }) { index ->
            val roomType = view.roomTypes[index]
            val kit = view.kits.firstOrNull { it.roomTypeId == roomType.id }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(roomType.name, style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.SemiBold)
                            Text(
                                text = if (kit != null) "${kit.items.size} item${if (kit.items.size == 1) "" else "s"} · ${kit.name}" else "No kit set up yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                            )
                        }
                        LinkButton(text = if (kit != null) "Edit" else "Set up", onClick = { viewModel.edit(roomType, kit) })
                        if (kit != null) {
                            LinkButton(text = "Remove", onClick = { viewModel.askDelete(kit.id) }, color = MaterialTheme.appColors.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmenityKitEditDialog(
    draft: AmenityKitDraft,
    isSaving: Boolean,
    context: Context,
    viewModel: HotelAmenityKitsViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEdit,
        title = { Text(draft.roomTypeName) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextField(value = draft.name, onValueChange = viewModel::onName, label = "Kit name", modifier = Modifier.fillMaxWidth())
                AppTextField(value = draft.notes, onValueChange = viewModel::onNotes, label = "Notes", modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", modifier = Modifier.weight(1f))
                    Switch(checked = draft.status, onCheckedChange = viewModel::onStatus)
                }
                Text("Items", style = MaterialTheme.typography.labelMedium, fontWeight = AppFontWeight.SemiBold)
                draft.items.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            LinkButton(text = "Remove", onClick = { viewModel.removeItem(index) }, color = MaterialTheme.appColors.danger)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = item.quantity,
                                onValueChange = { v -> viewModel.onItemQuantity(index, v) },
                                label = "Qty",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f),
                            )
                            AppSelectDropdown(
                                label = "Basis",
                                options = BASIS_OPTIONS,
                                selected = BASIS_OPTIONS.firstOrNull { it.id == item.basis },
                                onSelected = { option -> viewModel.onItemBasis(index, option.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SearchableSelectDropdown(
                    selected = null,
                    onSelected = viewModel::onProductPicked,
                    search = viewModel::searchProducts,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Add a product",
                    placeholder = "Type a product name…",
                    emptyText = "No product by that name",
                )
            }
        },
        confirmButton = {
            PrimaryButton(text = "Save", onClick = viewModel::save, enabled = !isSaving, isLoading = isSaving, compact = true)
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelEdit, enabled = !isSaving) },
    )
}
