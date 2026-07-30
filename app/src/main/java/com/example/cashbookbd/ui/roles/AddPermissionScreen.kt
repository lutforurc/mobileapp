package com.example.cashbookbd.ui.roles

import android.content.Context
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.cashbookbd.data.repository.PermissionItem
import com.example.cashbookbd.data.repository.RoleRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Permission management, a port of the web's /user-management/create-permission:
 * the create form on top (dotted key + group, with an "add new group" mode),
 * then the selected group's existing permissions with inline rename. The server
 * restricts writes to company 1 — its 403 message is surfaced verbatim.
 */

data class AddPermissionUiState(
    // Catalogue (groups + full permission list)
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val groups: List<String> = emptyList(),
    val permissions: List<PermissionItem> = emptyList(),

    // Create form
    val name: String = "",
    val selectedGroup: String? = null,
    /** True while the group field is free text ("Add new group" mode). */
    val newGroupMode: Boolean = false,
    val newGroupName: String = "",
    val isSaving: Boolean = false,

    // Inline rename
    val renamingId: Int? = null,
    val renameText: String = "",
    val isRenaming: Boolean = false,

    /** One-shot snackbar text (save/rename outcome, duplicate message, 403). */
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving

    /** The group whose members the list below shows (dropdown pick or new-group text). */
    val activeGroup: String?
        get() = if (newGroupMode) newGroupName.trim().ifBlank { null } else selectedGroup

    /** The permissions of [activeGroup], for the rename list. */
    val groupPermissions: List<PermissionItem>
        get() = activeGroup?.let { group -> permissions.filter { it.groupName == group } }.orEmpty()
}

class AddPermissionViewModel(
    private val repository: AdminExtrasRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPermissionUiState())
    val uiState: StateFlow<AddPermissionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val groupsResult = repository.loadPermissionGroups()
            val permissionsResult = roleRepository.loadPermissions()
            val error = (groupsResult as? Resource.Error) ?: (permissionsResult as? Resource.Error)
            if (error != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error.message,
                        sessionExpired = error.isUnauthorized,
                    )
                }
                return@launch
            }
            val permissions = (permissionsResult as? Resource.Success)?.data.orEmpty()
            // The endpoint's groups, padded with any group a permission already
            // carries that the endpoint missed.
            val groups = ((groupsResult as? Resource.Success)?.data.orEmpty() +
                permissions.map { it.groupName }).distinct().sorted()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    groups = groups,
                    permissions = permissions,
                    // Drop a selection whose group vanished server-side.
                    selectedGroup = state.selectedGroup?.takeIf { it in groups },
                )
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value.take(255)) }

    fun onGroup(option: SelectorOption) = _uiState.update { it.copy(selectedGroup = option.id) }

    fun onNewGroupName(value: String) = _uiState.update { it.copy(newGroupName = value.take(255)) }

    fun startNewGroup() = _uiState.update { it.copy(newGroupMode = true, newGroupName = "", renamingId = null) }

    fun useExistingGroup() = _uiState.update { it.copy(newGroupMode = false, newGroupName = "") }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.createPermission(state.name, state.activeGroup)) {
                is Resource.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            message = result.data,
                            name = "",
                            // A brand-new group is now real: select it in the dropdown.
                            selectedGroup = current.activeGroup ?: current.selectedGroup,
                            newGroupMode = false,
                            newGroupName = "",
                        )
                    }
                    load()
                }
                // Duplicate name arrives here as success:false "Permission name already exists.".
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun startRename(item: PermissionItem) = _uiState.update {
        it.copy(renamingId = item.id, renameText = item.name)
    }

    fun onRenameText(value: String) = _uiState.update { it.copy(renameText = value.take(255)) }

    fun cancelRename() = _uiState.update { it.copy(renamingId = null, renameText = "") }

    fun saveRename() {
        val state = _uiState.value
        val id = state.renamingId ?: return
        val newName = state.renameText.trim()
        if (newName.isEmpty() || state.isRenaming) return
        val row = state.permissions.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(isRenaming = true) }
        viewModelScope.launch {
            // The row keeps the group it is listed under (the UI-selected group).
            when (val result = repository.updatePermission(id, newName, state.activeGroup ?: row.groupName)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isRenaming = false, renamingId = null, renameText = "", message = result.data)
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isRenaming = false,
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
                    AddPermissionViewModel(
                        ServiceLocator.provideAdminExtrasRepository(appContext),
                        ServiceLocator.provideRoleRepository(appContext),
                    )
                }
            }
        }
    }
}

@Composable
fun AddPermissionScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPermissionViewModel = viewModel(
        factory = AddPermissionViewModel.provideFactory(LocalContext.current)
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
        title = "Add Permission",
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
                PermissionForm(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
                GroupPermissionList(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PermissionForm(state: AddPermissionUiState, viewModel: AddPermissionViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val onScreenMuted = onScreen.copy(alpha = 0.75f)

    Text(
        text = "Add Permission",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onScreen,
    )
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.name,
        onValueChange = viewModel::onName,
        label = "module.action",
        caption = "Permission Name",
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Dotted key, e.g. voucher.approval",
        style = MaterialTheme.typography.labelSmall,
        color = onScreenMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))

    if (state.newGroupMode) {
        AppTextField(
            value = state.newGroupName,
            onValueChange = viewModel::onNewGroupName,
            label = "New group name",
            caption = "New Group",
            modifier = Modifier.fillMaxWidth(),
        )
        LinkButton(text = "Choose existing group", onClick = viewModel::useExistingGroup)
    } else {
        AppSelectDropdown(
            label = "Group",
            options = state.groups.map { SelectorOption(it, it) },
            selected = state.selectedGroup?.let { SelectorOption(it, it) },
            onSelected = viewModel::onGroup,
            placeholder = if (state.isLoading) "Loading…" else "Select Group",
            modifier = Modifier.fillMaxWidth(),
        )
        LinkButton(text = "Add new group", onClick = viewModel::startNewGroup)
    }
    Spacer(Modifier.height(8.dp))

    PrimaryButton(
        text = "Save Permission",
        onClick = viewModel::save,
        enabled = state.canSave,
        isLoading = state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GroupPermissionList(state: AddPermissionUiState, viewModel: AddPermissionViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val group = state.activeGroup

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (group != null) "Permissions in \"$group\"" else "Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onScreen,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh permissions",
                tint = onScreen.copy(alpha = 0.8f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    when {
        state.isLoading && state.permissions.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        // The company-1 403 message lands here as a clear message.
        state.loadError != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = state.loadError, color = onScreen, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Retry", onClick = viewModel::load)
        }

        group == null -> Text(
            text = "Pick a group to see its existing permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = onScreen.copy(alpha = 0.75f),
        )

        state.groupPermissions.isEmpty() -> Text(
            text = "No permissions in this group yet.",
            style = MaterialTheme.typography.bodySmall,
            color = onScreen.copy(alpha = 0.75f),
        )

        else -> Column(modifier = Modifier.fillMaxWidth()) {
            state.groupPermissions.forEachIndexed { index, item ->
                PermissionRow(index = index, item = item, state = state, viewModel = viewModel)
                HorizontalDivider(color = onScreen.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    index: Int,
    item: PermissionItem,
    state: AddPermissionUiState,
    viewModel: AddPermissionViewModel,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.renamingId == item.id) {
            AppTextField(
                value = state.renameText,
                onValueChange = viewModel::onRenameText,
                label = "Permission name",
                modifier = Modifier.weight(1f),
            )
            if (state.isRenaming) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = viewModel::saveRename,
                    enabled = state.renameText.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save rename",
                        tint = onScreen,
                    )
                }
                IconButton(onClick = viewModel::cancelRename) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel rename",
                        tint = onScreen.copy(alpha = 0.8f),
                    )
                }
            }
        } else {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = onScreen,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.startRename(item) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename ${item.name}",
                    // primary sinks into the teal backdrop — use the on-colour.
                    tint = onScreen,
                )
            }
        }
    }
}
