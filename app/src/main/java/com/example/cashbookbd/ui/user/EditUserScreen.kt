package com.example.cashbookbd.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Edits a user (`user/user-update`): name, phone, role, branch, language, and the
 * two feature flags. Reached from the User List row pencil with a hashed id.
 */
@Composable
fun EditUserScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    userId: String? = null,
) {
    if (userId.isNullOrBlank()) {
        // The edit route always carries an id; guard defensively regardless.
        AuthenticatedShell(
            title = "Edit User",
            currentRoute = Routes.ADMIN,
            navController = navController,
            onLogout = onLogout,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("User not found.", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    val viewModel: EditUserViewModel = viewModel(
        key = userId,
        factory = EditUserViewModel.provideFactory(LocalContext.current, userId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    // Saved: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Edit User",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.loadError != null -> Column(
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

                else -> EditUserForm(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EditUserForm(state: EditUserUiState, viewModel: EditUserViewModel) {
    val branchOptions = state.branches.map { SelectorOption(id = it.id.toString(), label = it.name) }
    val selectedBranch = state.branch?.let { SelectorOption(id = it.id.toString(), label = it.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTextField(
            value = state.email,
            onValueChange = {},
            label = "",
            caption = "Email Address",
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = "User name",
            caption = "User Name",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.phone,
            onValueChange = viewModel::onPhone,
            label = "Mobile number",
            caption = "Mobile Number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Role",
            options = state.roles,
            selected = state.role,
            onSelected = viewModel::onRole,
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Branch",
            options = branchOptions,
            selected = selectedBranch,
            onSelected = { option ->
                state.branches.firstOrNull { it.id.toString() == option.id }?.let(viewModel::onBranch)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.lang,
            onValueChange = viewModel::onLang,
            label = "e.g. bn",
            caption = "Language",
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleRow(
            label = "Sidebar Menu",
            checked = state.sidebarMenu,
            onCheckedChange = viewModel::onSidebarMenu,
        )
        ToggleRow(
            label = "Use Filter Parameter",
            checked = state.useFilterParameter,
            onCheckedChange = viewModel::onUseFilterParameter,
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                text = "Update",
                onClick = viewModel::save,
                enabled = state.canSave,
                isLoading = state.isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
