package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetCategoryOption
import com.example.cashbookbd.data.repository.AssetChargedYear
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.data.repository.AssetRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The status filter, in the order the register is thought about. */
private val ASSET_STATUSES = listOf(
    "" to "All",
    "in_use" to "In use",
    "disposed" to "Disposed",
    "written_off" to "Written off",
)

internal fun assetStatusName(status: String): String = when (status) {
    "in_use" -> "In use"
    "disposed" -> "Disposed"
    "written_off" -> "Written off"
    else -> status.ifBlank { "—" }
}

/** One asset's years, opened from its Worth-now cell. */
data class AssetYearsShown(
    val asset: AssetRow,
    val years: List<AssetChargedYear>,
    val writtenDownValue: Double,
)

data class AssetRegisterUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<AssetRow> = emptyList(),
    val categories: List<AssetCategoryOption> = emptyList(),
    val search: String = "",
    val categoryFilter: Long? = null,
    val statusFilter: String = "",
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val message: String? = null,
    val pendingDelete: AssetRow? = null,
    val isDeleting: Boolean = false,
    val years: AssetYearsShown? = null,
    val isYearsLoading: Boolean = false,
    val sessionExpired: Boolean = false,
)

class AssetRegisterViewModel(
    private val repository: AssetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetRegisterUiState())
    val uiState: StateFlow<AssetRegisterUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isBranchesLoading = false,
                            branches = result.data.branches,
                            // The user's own property first — where their assets are.
                            selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                        )
                    }
                    load(page = 1)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch) }
        load(page = 1)
    }

    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }

    fun search() = load(page = 1)

    fun onCategory(id: Long?) {
        _uiState.update { it.copy(categoryFilter = id) }
        load(page = 1)
    }

    fun onStatus(value: String) {
        _uiState.update { it.copy(statusFilter = value) }
        load(page = 1)
    }

    fun goToPage(page: Int) = load(page)

    fun load(page: Int = _uiState.value.currentPage) {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchRegister(
                branchId = state.selectedBranch?.id,
                categoryId = state.categoryFilter,
                status = state.statusFilter,
                search = state.search,
                page = page,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        categories = result.data.categories,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
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

    /**
     * Every year charged against one asset, as it was charged — the answer to
     * "why is it worth that", which is asked of the Worth-now cell.
     */
    fun openYears(row: AssetRow) {
        _uiState.update { it.copy(isYearsLoading = true) }
        viewModelScope.launch {
            when (val result = repository.fetchAsset(row.id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isYearsLoading = false,
                        years = AssetYearsShown(
                            asset = result.data.asset ?: row,
                            years = result.data.depreciations,
                            writtenDownValue = result.data.writtenDownValue,
                        ),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isYearsLoading = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closeYears() = _uiState.update { it.copy(years = null) }

    fun askDelete(row: AssetRow) = _uiState.update { it.copy(pendingDelete = row) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.deleteAsset(row.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isDeleting = false, pendingDelete = null, message = result.data)
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        // Usually the refusal: a year has been charged, so the
                        // asset is disposed of rather than deleted. The server's
                        // sentence says exactly that.
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSaved(message: String) {
        _uiState.update { it.copy(message = message) }
        load()
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetRegisterViewModel(
                    repository = AssetRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The register: every asset the company owns, one row each.
 *
 * ⚠️ ONE ROW IS ONE THING, and there is no quantity box. Four identical chairs
 * bought on one invoice are four rows, because they are moved, sold and written
 * off one at a time.
 *
 * ⚠️ WHAT IT IS WORTH IS NOT TYPED, IT IS WORKED OUT: cost less every year
 * charged. The line under the figure says where it came from, because "worth
 * now" is the number somebody queries and "you typed it" is not an answer.
 */
@Composable
fun AssetRegisterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetRegisterViewModel = viewModel(
        factory = AssetRegisterViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    // Selling an asset writes a voucher, so it answers to the permission that
    // writes vouchers rather than the one that reads the register.
    val canDispose = Permissions.hasAny(
        sessionState.permissions, listOf(AssetMenu.PERM_DEPRECIATION_RUN),
    )

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    OnAssetSaved(navController) { viewModel.onSaved(it) }

    val categoryOptions = remember(state.categories) {
        listOf(SelectorOption("", "Every category")) +
            state.categories.map { SelectorOption(it.id.toString(), it.label) }
    }
    val statusOptions = remember {
        ASSET_STATUSES.map { (id, label) -> SelectorOption(id, label) }
    }

    AuthenticatedShell(
        title = "Asset Register",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssetBranchField(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranch,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        label = "Code, name or serial",
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Search",
                        onClick = viewModel::search,
                        isLoading = state.isLoading,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSelectDropdown(
                        label = "Category",
                        options = categoryOptions,
                        selected = categoryOptions.firstOrNull {
                            it.id == (state.categoryFilter?.toString() ?: "")
                        } ?: categoryOptions.first(),
                        onSelected = { viewModel.onCategory(it.id.toLongOrNull()) },
                        modifier = Modifier.weight(1f),
                    )
                    AppSelectDropdown(
                        label = "Status",
                        options = statusOptions,
                        selected = statusOptions.firstOrNull { it.id == state.statusFilter }
                            ?: statusOptions.first(),
                        onSelected = { viewModel.onStatus(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddButton(
                        text = "Add an asset",
                        onClick = {
                            navController.navigate(
                                AssetMenu.registerForm(null, state.selectedBranch?.id),
                            )
                        },
                        compact = true,
                    )
                    if (state.message != null) {
                        LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                    }
                }
                state.message?.let { AssetNotice(text = it, tone = AssetTone.Success) }
            }

            when {
                state.error != null -> AssetError(
                    message = state.error!!,
                    onRetry = { viewModel.load() },
                )
                state.isLoading && state.rows.isEmpty() -> AssetLoading()
                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = registerColumns(
                            canDispose = canDispose,
                            onEdit = { row ->
                                navController.navigate(
                                    AssetMenu.registerForm(row.id, state.selectedBranch?.id),
                                )
                            },
                            onYears = viewModel::openYears,
                            onDispose = { navController.navigate(AssetMenu.disposal(it.id)) },
                            onCare = { navController.navigate(AssetMenu.care(it.id)) },
                            onDelete = viewModel::askDelete,
                        ),
                        data = state.rows,
                        noDataMessage =
                            "Nothing in the register yet. Add a category first, then the assets " +
                                "that belong to it.",
                    )
                    if (state.isLoading || state.isYearsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            if (state.lastPage > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinkButton(
                        text = "Previous",
                        onClick = { viewModel.goToPage(state.currentPage - 1) },
                        enabled = state.currentPage > 1 && !state.isLoading,
                    )
                    Text(
                        text = "Page ${state.currentPage} of ${state.lastPage} · ${state.total} assets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    LinkButton(
                        text = "Next",
                        onClick = { viewModel.goToPage(state.currentPage + 1) },
                        enabled = state.currentPage < state.lastPage && !state.isLoading,
                    )
                }
            }
        }
    }

    state.years?.let { shown -> AssetYearsDialog(shown, viewModel::closeYears) }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Remove this asset?") },
            text = {
                Text(
                    "\"${row.name}\" will be removed. Nothing has been charged against it, so " +
                        "nothing in the ledger moves.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Remove",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isDeleting,
                    isLoading = state.isDeleting,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancelDelete) },
        )
    }
}

/** Every year charged against one asset, as it was charged. */
@Composable
private fun AssetYearsDialog(shown: AssetYearsShown, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${shown.asset.name} — every year charged") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                if (shown.asset.openingAccumDep > 0) {
                    AssetLine(
                        label = "Brought forward from the old books",
                        value = AmountFormat.format(shown.asset.openingAccumDep),
                    )
                }
                if (shown.years.isEmpty()) {
                    Text(
                        text = "Nothing charged by this system yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.textMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                } else {
                    shown.years.forEach { year ->
                        AssetLine(
                            label = onTheDay(year.yearEnding),
                            sublabel = "${percentText(year.rate)} · ${year.days} day(s) · " +
                                "on ${AmountFormat.format(year.openingWdv)}",
                            value = AmountFormat.format(year.amount),
                        )
                    }
                }
                AssetLine(
                    label = "Worth now",
                    value = AmountFormat.format(shown.writtenDownValue),
                    strong = true,
                    divider = false,
                )
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onClose) },
    )
}

@Composable
private fun registerColumns(
    canDispose: Boolean,
    onEdit: (AssetRow) -> Unit,
    onYears: (AssetRow) -> Unit,
    onDispose: (AssetRow) -> Unit,
    onCare: (AssetRow) -> Unit,
    onDelete: (AssetRow) -> Unit,
): List<ReportColumn<AssetRow>> {
    val muted = MaterialTheme.appColors.textMuted
    val action = MaterialTheme.appColors.action
    return listOf(
        ReportColumn("ASSET", ReportColWidth.Fixed(160.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(
                        text = row.code + if (row.serialNo.isNotBlank()) " · ${row.serialNo}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        },
        ReportColumn("CATEGORY", ReportColWidth.Fixed(130.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.categoryName.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    if (row.location.isNotBlank()) {
                        Text(
                            text = row.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        ReportColumn("BOUGHT", ReportColWidth.Fixed(96.dp), TextAlign.Center) { row, _ ->
            cellText(onTheDay(row.purchaseDate))
        },
        ReportColumn("COST", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.format(row.cost))
        },
        ReportColumn("WORTH NOW", ReportColWidth.Fixed(160.dp), TextAlign.End) { row, _ ->
            val brought = row.openingAccumDep
            val here = row.chargedHere
            ReportTableCell.Slot {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = AmountFormat.format(row.writtenDownValue),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (brought > 0 || here > 0) {
                        // The years are one tap away rather than a screen away:
                        // the question "why is it worth that" is asked of this
                        // cell, so the answer opens from it.
                        Text(
                            text = buildString {
                                if (brought > 0) append("${AmountFormat.format(brought)} brought forward")
                                if (brought > 0 && here > 0) append(" · ")
                                if (here > 0) {
                                    append("${AmountFormat.format(here)} over ${row.yearsCharged} year(s)")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = action,
                            maxLines = 3,
                            modifier = Modifier.clickable { onYears(row) },
                        )
                    } else {
                        Text(
                            text = "nothing charged yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        ReportColumn("STATUS", ReportColWidth.Fixed(96.dp), TextAlign.Center) { row, _ ->
            cellText(assetStatusName(row.status), color = muted)
        },
        ReportColumn("ACTION", ReportColWidth.Fixed(180.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { onEdit(row) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit this asset",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Only while it is still in use: an asset that has already
                    // gone cannot go twice.
                    if (canDispose && row.status == "in_use") {
                        IconButton(onClick = { onDispose(row) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sell it, or write it off",
                                tint = MaterialTheme.appColors.warning,
                            )
                        }
                    }
                    // Shown for a disposed asset too: the questions asked after
                    // something has gone are exactly the ones this answers.
                    IconButton(onClick = { onCare(row) }) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription =
                                "Who has it, whether it was there, what it has cost to keep",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!row.locked) {
                        IconButton(onClick = { onDelete(row) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription =
                                    "Remove — nothing has been charged against it yet",
                                tint = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                }
            }
        },
    )
}
