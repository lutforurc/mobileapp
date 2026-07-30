package com.example.cashbookbd.ui.roles

import com.example.cashbookbd.ui.theme.asDivider
import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.AppFontWeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.PermissionItem
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import java.util.Locale

/**
 * Role & permission management — a port of the web's Roles page. Pick a role,
 * toggle its permissions (grouped, with per-group and "All" switches), and
 * Update. Plan/global roles are shown read-only. "Add Role" opens the create form.
 */
@Composable
fun RolePermissionScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RolePermissionViewModel = viewModel(
        factory = RolePermissionViewModel.provideFactory(LocalContext.current)
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
    // Coming back from Add Role: reload the role list so the new role appears.
    val savedHandle = navController.currentBackStackEntry?.savedStateHandle
    val savedMessage by savedHandle
        ?.getStateFlow<String?>(Routes.CREATED_MESSAGE, null)
        ?.collectAsStateWithLifecycle() ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    LaunchedEffect(savedMessage) {
        val message = savedMessage ?: return@LaunchedEffect
        savedHandle?.set(Routes.CREATED_MESSAGE, null)
        viewModel.load()
        snackbarHostState.showSnackbar(message)
    }

    AuthenticatedShell(
        title = "Roles",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.roles.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.loadError != null && state.roles.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.loadError!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "Retry", onClick = viewModel::load)
                }

                else -> RolePermissionBody(
                    state = state,
                    viewModel = viewModel,
                    onAddRole = { navController.navigate(Routes.ADD_ROLE) },
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RolePermissionBody(
    state: RolePermissionUiState,
    viewModel: RolePermissionViewModel,
    onAddRole: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val roleOptions = remember(state.roles) {
        state.roles.map { SelectorOption(id = it.id.toString(), label = it.name) }
    }
    val selectedOption = state.selectedRole?.let { SelectorOption(it.id.toString(), it.name) }
    val groups = remember(state.visiblePermissions) {
        state.visiblePermissions
            .groupBy { it.groupName }
            .toSortedMap()
            .map { (group, perms) -> PermissionGroup(group, perms.sortedBy { it.name }) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddButton(text = "Add Role", onClick = onAddRole, compact = true)
            if (!state.isReadonly) {
                PrimaryButton(
                    text = "Update",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        AppSelectDropdown(
            label = "Role",
            options = roleOptions,
            selected = selectedOption,
            onSelected = { option ->
                state.roles.firstOrNull { it.id.toString() == option.id }?.let(viewModel::selectRole)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
                color = onScreen,
                modifier = Modifier.weight(1f),
            )
            if (!state.isReadonly) {
                ToggleControl(label = "All", checked = state.allSelected, onCheckedChange = viewModel::toggleAll)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = onScreen.asDivider(),
        )

        if (state.isReadonly) {
            Text(
                text = "This is a global/plan role. Its permissions are shared across companies and can't be changed here.",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.muted(),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        when {
            state.isLoadingPermissions -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            groups.isEmpty() -> Text(
                text = if (state.isReadonly) "This role has no permissions." else "No permissions found.",
                style = MaterialTheme.typography.bodyMedium,
                color = onScreen.muted(),
                modifier = Modifier.padding(vertical = 24.dp),
            )

            else -> groups.forEach { group ->
                PermissionGroupSection(
                    group = group,
                    state = state,
                    onScreen = onScreen,
                    viewModel = viewModel,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionGroupSection(
    group: PermissionGroup,
    state: RolePermissionUiState,
    onScreen: androidx.compose.ui.graphics.Color,
    viewModel: RolePermissionViewModel,
) {
    val allInGroup = group.permissions.all { it.id in state.selectedPermissionIds }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.SemiBold,
            color = onScreen,
            modifier = Modifier.weight(1f),
        )
        if (!state.isReadonly) {
            ToggleControl(
                label = "All",
                checked = allInGroup,
                onCheckedChange = { checked -> viewModel.toggleGroup(group.permissions, checked) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    group.permissions.forEach { permission ->
        PermissionRow(
            permission = permission,
            checked = permission.id in state.selectedPermissionIds,
            enabled = !state.isReadonly,
            onScreen = onScreen,
            onToggle = { viewModel.togglePermission(permission.id) },
        )
    }
}

@Composable
private fun PermissionRow(
    permission: PermissionItem,
    checked: Boolean,
    enabled: Boolean,
    onScreen: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Text(
            text = formatPermissionName(permission.name),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) onScreen else onScreen.muted(),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ToggleControl(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 6.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "ledger.create.view" -> "Ledger Create View", mirroring the web's formatRoleNameForCashBook. */
private fun formatPermissionName(name: String): String =
    name.split('.').joinToString(" ") { part ->
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
