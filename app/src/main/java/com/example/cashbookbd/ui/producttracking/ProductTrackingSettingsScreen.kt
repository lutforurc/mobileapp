package com.example.cashbookbd.ui.producttracking

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProductTrackingRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.TrackingProductOption
import com.example.cashbookbd.data.repository.TrackingSetting
import com.example.cashbookbd.data.repository.TrackingSettingDraft
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.muted
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "all" option both scoping dropdowns share — id 0 is a real domain value. */
private val ALL_BRANCHES = SelectorOption(id = "0", label = "All Branch")

data class TrackingSettingsUiState(
    val canManage: Boolean = false,

    // Form
    val editingId: Long? = null,
    val branches: List<SelectorOption> = listOf(ALL_BRANCHES),
    val branch: SelectorOption = ALL_BRANCHES,
    /** null = all parties (coa4 0). */
    val party: LedgerDropdownItem? = null,
    val products: List<TrackingProductOption> = emptyList(),
    val product: TrackingProductOption? = null,
    val trackSalesBill: Boolean = true,
    val trackPurchaseBill: Boolean = true,
    val trackCashReceived: Boolean = true,
    val trackCashPayment: Boolean = true,
    val isActive: Boolean = true,
    val isSaving: Boolean = false,

    // List
    val search: String = "",
    val isLoading: Boolean = false,
    val rows: List<TrackingSetting> = emptyList(),
    val listError: String? = null,
    val togglingId: Long? = null,
    val deletingId: Long? = null,
    /** The row the confirm dialog is asking about, when one is open. */
    val pendingDelete: TrackingSetting? = null,

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class ProductTrackingSettingsViewModel(
    private val repository: ProductTrackingRepository,
    private val ledgerRepository: LedgerRepository,
    private val reportRepository: ReportRepository,
    canManage: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingSettingsUiState(canManage = canManage))
    val uiState: StateFlow<TrackingSettingsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadBranches()
        loadList()
        loadProducts()
    }

    private fun loadBranches() {
        viewModelScope.launch {
            val result = reportRepository.getBranches()
            if (result is Resource.Success) {
                _uiState.update { state ->
                    state.copy(
                        branches = listOf(ALL_BRANCHES) + result.data.branches.map {
                            SelectorOption(id = it.id.toString(), label = it.name)
                        },
                    )
                }
            }
        }
    }

    /**
     * The Product dropdown offers only what is NOT yet configured for exactly
     * this (branch, party) pair — so it reloads when either changes.
     */
    private fun loadProducts() {
        val state = _uiState.value
        if (!state.canManage) return
        viewModelScope.launch {
            val result = repository.fetchAvailableProducts(
                branchId = state.branch.id.toLongOrNull() ?: 0L,
                coa4Id = state.party?.id?.toLong() ?: 0L,
            )
            if (result is Resource.Success) {
                _uiState.update { s ->
                    // Keep the row being edited pickable — it is absent from the
                    // available list precisely because it is already configured.
                    val extra = s.product?.takeIf { p -> result.data.none { it.id == p.id } }
                    s.copy(products = listOfNotNull(extra) + result.data)
                }
            }
        }
    }

    fun loadList() {
        _uiState.update { it.copy(isLoading = true, listError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchSettings(_uiState.value.search)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        listError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearchChange(value: String) {
        _uiState.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            loadList()
        }
    }

    fun onBranchSelected(option: SelectorOption) {
        _uiState.update { it.copy(branch = option, product = null) }
        loadProducts()
    }

    fun onPartySelected(party: LedgerDropdownItem?) {
        _uiState.update { it.copy(party = party, product = null) }
        loadProducts()
    }

    fun onProductSelected(option: SelectorOption) = _uiState.update { state ->
        state.copy(product = state.products.firstOrNull { it.id.toString() == option.id })
    }

    fun onTrackSalesBill(on: Boolean) = _uiState.update { it.copy(trackSalesBill = on) }
    fun onTrackPurchaseBill(on: Boolean) = _uiState.update { it.copy(trackPurchaseBill = on) }
    fun onTrackCashReceived(on: Boolean) = _uiState.update { it.copy(trackCashReceived = on) }
    fun onTrackCashPayment(on: Boolean) = _uiState.update { it.copy(trackCashPayment = on) }
    fun onIsActive(on: Boolean) = _uiState.update { it.copy(isActive = on) }

    suspend fun searchParties(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    /** Loads a row into the form for editing. */
    fun edit(row: TrackingSetting) {
        _uiState.update { state ->
            state.copy(
                editingId = row.id,
                branch = state.branches.firstOrNull { it.id == row.branchId.toString() }
                    ?: if (row.branchId == 0L) ALL_BRANCHES
                    else SelectorOption(id = row.branchId.toString(), label = row.branchName),
                party = row.coa4Id.takeIf { it > 0L }?.let {
                    LedgerDropdownItem(it.toInt(), row.partyName.ifBlank { "Party $it" }, null)
                },
                product = TrackingProductOption(id = row.productId, name = row.productName),
                trackSalesBill = row.trackSalesBill,
                trackPurchaseBill = row.trackPurchaseBill,
                trackCashReceived = row.trackCashReceived,
                trackCashPayment = row.trackCashPayment,
                isActive = row.isActive,
            )
        }
        loadProducts()
    }

    fun cancelEdit() = _uiState.update {
        it.copy(
            editingId = null, product = null, party = null, branch = ALL_BRANCHES,
            trackSalesBill = true, trackPurchaseBill = true,
            trackCashReceived = true, trackCashPayment = true, isActive = true,
        )
    }

    fun save() {
        val state = _uiState.value
        val product = state.product ?: run {
            _uiState.update { it.copy(message = "Select a product.") }
            return
        }
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.saveSetting(
                id = state.editingId,
                draft = TrackingSettingDraft(
                    productId = product.id,
                    branchId = state.branch.id.toLongOrNull() ?: 0L,
                    coa4Id = state.party?.id?.toLong() ?: 0L,
                    trackSalesBill = state.trackSalesBill,
                    trackPurchaseBill = state.trackPurchaseBill,
                    trackCashReceived = state.trackCashReceived,
                    trackCashPayment = state.trackCashPayment,
                    isActive = state.isActive,
                ),
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    cancelEdit()
                    loadProducts()
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
            // Like the web: the list reloads whatever the outcome.
            loadList()
        }
    }

    /** Deactivation stops new mappings, history stays intact — the only path for a setting in use. */
    fun toggle(row: TrackingSetting) {
        if (_uiState.value.togglingId != null) return
        _uiState.update { it.copy(togglingId = row.id) }
        viewModelScope.launch {
            val result = repository.toggleSetting(row.id, !row.isActive)
            _uiState.update {
                it.copy(
                    togglingId = null,
                    message = (result as? Resource.Error)?.message,
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
            loadList()
        }
    }

    /** Opens the confirm dialog naming what would be deleted. */
    fun requestDelete(row: TrackingSetting) = _uiState.update { it.copy(pendingDelete = row) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    /**
     * Deletes a setting added by mistake. The server is the judge of "by
     * mistake": once anything has been mapped under it the delete is refused
     * with the advice to switch Active off instead, and that refusal is shown
     * as-is.
     */
    fun confirmDelete() {
        val row = _uiState.value.pendingDelete ?: return
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(pendingDelete = null, deletingId = row.id) }
        viewModelScope.launch {
            val result = repository.deleteSetting(row.id)
            _uiState.update {
                it.copy(
                    deletingId = null,
                    message = when (result) {
                        is Resource.Success -> result.data
                        is Resource.Error -> result.message
                        Resource.Loading -> null
                    },
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
            if (result is Resource.Success) {
                // A freed product becomes addable again.
                loadProducts()
            }
            loadList()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ProductTrackingSettingsViewModel(
                    repository = ServiceLocator.provideProductTrackingRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    canManage = Permissions.has(
                        ServiceLocator.provideSessionManager(appContext).permissions,
                        "product.tracking.settings.manage",
                    ),
                )
            }
        }
    }
}

/**
 * Which products carry their own bill/collection/payment ledger — the web's
 * Settings → Product Tracking. Deactivation is the removal path for a setting
 * in use (it never touches history); Delete exists only for a row nothing has
 * been recorded under yet, and the server enforces that line, not the client.
 */
@Composable
fun ProductTrackingSettingsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductTrackingSettingsViewModel = viewModel(
        factory = ProductTrackingSettingsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        title = "Product Tracking",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.canManage) {
                    item { SettingForm(state, viewModel) }
                }
                item {
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        label = "Search product",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Tracked Products (${state.rows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                }
                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.listError != null -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(state.listError!!, color = MaterialTheme.colorScheme.onBackground)
                            LinkButton(text = "Retry", onClick = viewModel::loadList)
                        }
                    }
                    state.rows.isEmpty() -> item {
                        Text(
                            text = "No product has been added yet. Add one with the form above — " +
                                "until then the Cash Received/Payment forms show no product dropdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    else -> items(state.rows, key = { it.id }) { row ->
                        SettingRow(
                            row = row,
                            canManage = state.canManage,
                            isToggling = state.togglingId == row.id,
                            isDeleting = state.deletingId == row.id,
                            onEdit = { viewModel.edit(row) },
                            onToggle = { viewModel.toggle(row) },
                            onDelete = { viewModel.requestDelete(row) },
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete tracking setting?") },
            text = {
                Text(
                    "Remove the tracking setting for \"" +
                        row.productName.ifBlank { "Product ${row.productId}" } +
                        "\"? This only works while nothing has been recorded under it — " +
                        "once transactions exist, switch Active off instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingForm(state: TrackingSettingsUiState, viewModel: ProductTrackingSettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (state.editingId == null) "Add New Product" else "Edit Setting",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.SemiBold,
            )
            AppSelectDropdown(
                label = "Product",
                options = state.products.map {
                    SelectorOption(id = it.id.toString(), label = it.name)
                },
                selected = state.product?.let { SelectorOption(it.id.toString(), it.name) },
                onSelected = viewModel::onProductSelected,
                placeholder = "-- Select Product --",
                modifier = Modifier.fillMaxWidth(),
            )
            SearchableLedgerDropdown(
                selectedLedger = state.party,
                onLedgerSelected = viewModel::onPartySelected,
                searchLedgers = viewModel::searchParties,
                label = "Customer / Supplier",
                placeholder = "For all parties",
            )
            if (state.party != null) {
                LinkButton(text = "Make it for all parties", onClick = { viewModel.onPartySelected(null) })
            }
            AppSelectDropdown(
                label = "Branch",
                options = state.branches,
                selected = state.branch,
                onSelected = viewModel::onBranchSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            FlagSwitch("Sales Bill", state.trackSalesBill, viewModel::onTrackSalesBill)
            FlagSwitch("Purchase Bill", state.trackPurchaseBill, viewModel::onTrackPurchaseBill)
            FlagSwitch("Cash Received", state.trackCashReceived, viewModel::onTrackCashReceived)
            FlagSwitch("Cash Payment", state.trackCashPayment, viewModel::onTrackCashPayment)
            FlagSwitch("Active", state.isActive, viewModel::onIsActive)
            Text(
                text = "Switched off, no new mappings are made; past figures stay intact.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (state.editingId == null) "Add" else "Update",
                    onClick = viewModel::save,
                    isLoading = state.isSaving,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                )
                if (state.editingId != null) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = viewModel::cancelEdit,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingRow(
    row: TrackingSetting,
    canManage: Boolean,
    isToggling: Boolean,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.productName.ifBlank { "Product ${row.productId}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                    color = onScreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = if (row.isActive) MaterialTheme.appColors.success
                    else MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            Text(
                text = (if (row.coa4Id == 0L) "All parties" else row.partyName.ifBlank { "Party ${row.coa4Id}" }) +
                    " • " +
                    (if (row.branchId == 0L) "All Branch" else row.branchName.ifBlank { "Branch ${row.branchId}" }),
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            Text(
                text = listOf(
                    "Sales" to row.trackSalesBill,
                    "Purchase" to row.trackPurchaseBill,
                    "Received" to row.trackCashReceived,
                    "Payment" to row.trackCashPayment,
                ).joinToString("   ") { (label, on) -> "$label: ${if (on) "Yes" else "No"}" },
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            if (canManage) {
                Row {
                    LinkButton(text = "Edit", onClick = onEdit, enabled = !isToggling && !isDeleting)
                    Spacer(Modifier.height(0.dp))
                    LinkButton(
                        text = when {
                            isToggling -> "…"
                            row.isActive -> "Deactivate"
                            else -> "Activate"
                        },
                        onClick = onToggle,
                        enabled = !isToggling && !isDeleting,
                    )
                    Spacer(Modifier.height(0.dp))
                    // Only for a setting nothing has been recorded under —
                    // the server refuses the rest, and that answer is shown.
                    LinkButton(
                        text = if (isDeleting) "…" else "Delete",
                        onClick = onDelete,
                        enabled = !isToggling && !isDeleting,
                    )
                }
            }
        }
    }
}
