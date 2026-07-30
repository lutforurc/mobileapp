package com.example.cashbookbd.ui.admin

import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AdminExtrasRepository
import com.example.cashbookbd.data.repository.GroupReportItem
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Group-report setup, a port of the web's /reports/group-report/setup (perm
 * group.report): pick the group (1 = Operating Cost, 2 = Purchase Cost), add
 * items from a locally-filtered searchable dropdown, and manage the selected
 * list below with per-row delete. Every write reloads the setup.
 */

private val GROUP_OPTIONS = listOf(
    SelectorOption("1", "Operating Cost"),
    SelectorOption("2", "Purchase Cost"),
)

data class GroupReportSetupUiState(
    /** The chosen group; null until the user picks one (empty default). */
    val selectedGroup: SelectorOption? = null,

    val isLoading: Boolean = false,
    val loadError: String? = null,
    val options: List<GroupReportItem> = emptyList(),
    val selected: List<GroupReportItem> = emptyList(),

    /** The item picked in the searchable dropdown, awaiting Add. */
    val pickedItem: GroupReportItem? = null,
    val isAdding: Boolean = false,

    /** Row awaiting its server delete (spinner replaces the bin icon). */
    val deletingId: Long? = null,
    /** Row the confirmation dialog is asking about; null = no dialog. */
    val confirmDelete: GroupReportItem? = null,
    /** One-shot snackbar text (add/remove outcome, duplicate message). */
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val groupId: Int? get() = selectedGroup?.id?.toIntOrNull()

    /** The options not yet selected — what the searchable dropdown offers. */
    val available: List<GroupReportItem>
        get() {
            val taken = selected.mapTo(HashSet()) { it.id }
            return options.filterNot { it.id in taken }
        }

    val canAdd: Boolean get() = groupId != null && pickedItem != null && !isAdding
}

class GroupReportSetupViewModel(
    private val repository: AdminExtrasRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupReportSetupUiState())
    val uiState: StateFlow<GroupReportSetupUiState> = _uiState.asStateFlow()

    fun onGroupSelected(option: SelectorOption) {
        _uiState.update {
            it.copy(selectedGroup = option, pickedItem = null, options = emptyList(), selected = emptyList())
        }
        load()
    }

    // One in-flight load at a time — switching Operating↔Purchase Cost must
    // cancel the previous group's request or its late response would land on
    // the newly selected group.
    private var loadJob: kotlinx.coroutines.Job? = null

    fun load() {
        val group = _uiState.value.groupId ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = repository.loadGroupReportSetup(group)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        options = result.data.options,
                        selected = result.data.selected,
                        // Drop a pick that is no longer available (just added / removed server-side).
                        pickedItem = state.pickedItem?.takeIf { pick ->
                            result.data.options.any { it.id == pick.id } &&
                                result.data.selected.none { it.id == pick.id }
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Local filter over the not-yet-selected options, for [SearchableSelectDropdown]. */
    fun searchAvailable(keyword: String): Resource<List<SelectorOption>> {
        val needle = keyword.trim()
        val matches = _uiState.value.available
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .map { SelectorOption(it.id.toString(), it.name) }
        return Resource.Success(matches)
    }

    fun onItemPicked(option: SelectorOption) = _uiState.update { state ->
        state.copy(pickedItem = state.available.firstOrNull { it.id.toString() == option.id })
    }

    fun add() {
        val state = _uiState.value
        val group = state.groupId ?: return
        val item = state.pickedItem ?: return
        if (state.isAdding) return
        _uiState.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            when (val result = repository.addGroupReportItem(group, item.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isAdding = false, pickedItem = null, message = result.data) }
                    load()
                }
                // A duplicate arrives here as success:false with the server's message.
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isAdding = false,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun requestDelete(item: GroupReportItem) = _uiState.update { it.copy(confirmDelete = item) }

    fun dismissDelete() = _uiState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val state = _uiState.value
        val item = state.confirmDelete ?: return
        val group = state.groupId ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = item.id) }
        viewModelScope.launch {
            when (val result = repository.removeGroupReportItem(group, item.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(deletingId = null, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        deletingId = null,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    GroupReportSetupViewModel(ServiceLocator.provideAdminExtrasRepository(appContext))
                }
            }
        }
    }
}

@Composable
fun GroupReportSetupScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupReportSetupViewModel = viewModel(
        factory = GroupReportSetupViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AuthenticatedShell(
        title = "Add Group Report",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                SetupForm(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
                SelectedList(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    uiState.confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Remove report item?") },
            text = { Text("\"${item.name}\" will be removed from the ${uiState.selectedGroup?.label ?: "group"} report.") },
            confirmButton = { LinkButton(text = "Remove", onClick = viewModel::confirmDelete) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissDelete) },
        )
    }
}

@Composable
private fun SetupForm(state: GroupReportSetupUiState, viewModel: GroupReportSetupViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val onScreenMuted = onScreen.copy(alpha = 0.75f)

    Text(
        text = "Group Report Setup",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onScreen,
    )
    Spacer(Modifier.height(12.dp))

    AppSelectDropdown(
        label = "Group",
        options = GROUP_OPTIONS,
        selected = state.selectedGroup,
        onSelected = viewModel::onGroupSelected,
        placeholder = "Select Group",
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.selectedGroup == null) {
        Text(
            text = "Pick a group to manage its report items.",
            style = MaterialTheme.typography.labelSmall,
            color = onScreenMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    Spacer(Modifier.height(12.dp))

    // Local filter over options minus selected — no server round trip.
    SearchableSelectDropdown(
        selected = state.pickedItem?.let { SelectorOption(it.id.toString(), it.name) },
        onSelected = viewModel::onItemPicked,
        search = { keyword -> viewModel.searchAvailable(keyword) },
        label = "Report Item",
        placeholder = if (state.isLoading) "Loading…" else "Type to filter…",
        emptyText = "No matching item left to add",
        debounceMs = 150L,
        minSearchChars = 1,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))

    PrimaryButton(
        text = "Add",
        onClick = viewModel::add,
        enabled = state.canAdd,
        isLoading = state.isAdding,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SelectedList(state: GroupReportSetupUiState, viewModel: GroupReportSetupViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    if (state.selectedGroup == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${state.selectedGroup.label} Items",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onScreen,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh list",
                tint = onScreen.copy(alpha = 0.8f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    when {
        state.isLoading && state.selected.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.loadError != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = state.loadError, color = onScreen, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Retry", onClick = viewModel::load)
        }

        else -> ReportTable(
            columns = selectedColumns(state, viewModel),
            data = state.selected,
            noDataMessage = "No items in this report yet.",
            scrollable = false,
        )
    }
}

/** The selected-items table: # | Name | delete. */
@Composable
private fun selectedColumns(
    state: GroupReportSetupUiState,
    viewModel: GroupReportSetupViewModel,
): List<ReportColumn<GroupReportItem>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return remember(state.deletingId, onScreen) {
        listOf(
            ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
                cellText((index + 1).toString(), align = TextAlign.Center, color = onScreen)
            },
            ReportColumn("Name", ReportColWidth.Weight(1f)) { r, _ ->
                cellText(r.name, maxLines = 2, color = onScreen)
            },
            ReportColumn("Action", ReportColWidth.Fixed(64.dp), TextAlign.Center) { r, _ ->
                ReportTableCell.Slot { DeleteCell(r, state.deletingId == r.id, viewModel) }
            },
        )
    }
}

@Composable
private fun DeleteCell(
    item: GroupReportItem,
    isDeleting: Boolean,
    viewModel: GroupReportSetupViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { viewModel.requestDelete(item) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${item.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
