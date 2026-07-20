package com.example.cashbookbd.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.DropdownAnchorField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Creates a user. Collects what `user/store` requires: a name, a branch, a role,
 * a password, and at least one of email/phone.
 */
@Composable
fun AddUserScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddUserViewModel = viewModel(
        factory = AddUserViewModel.provideFactory(LocalContext.current)
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

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    // Created: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Add User",
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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.optionsError != null) {
                    Text(
                        text = state.optionsError!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinkButton(
                        text = "Retry",
                        onClick = viewModel::loadOptions,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Field("User Name", state.name, viewModel::onName)

                Field("Email", state.email, viewModel::onEmail, keyboard = KeyboardType.Email)
                Field("Phone", state.phone, viewModel::onPhone, keyboard = KeyboardType.Phone)
                if (!state.hasContact) {
                    Hint("Give an email or a phone — at least one is required.")
                }

                BranchDropdown(
                    branches = state.branches,
                    selected = state.branch,
                    isLoading = state.isLoadingOptions,
                    onSelected = viewModel::onBranch,
                )
                RoleDropdown(
                    roles = state.roles,
                    selected = state.role,
                    isLoading = state.isLoadingOptions,
                    onSelected = viewModel::onRole,
                )

                Field(
                    "Password",
                    state.password,
                    viewModel::onPassword,
                    isPassword = true,
                    isError = state.passwordTooShort,
                )
                if (state.passwordTooShort) {
                    Hint("At least $MIN_PASSWORD_LENGTH characters.", isError = true)
                }
                Field(
                    "Confirm Password",
                    state.confirmPassword,
                    viewModel::onConfirmPassword,
                    isPassword = true,
                    isError = state.passwordsMismatch,
                )
                if (state.passwordsMismatch) {
                    Hint("Passwords don't match.", isError = true)
                }

                PrimaryButton(
                    text = "Save User",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Hint(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isError: Boolean = false,
) {
    AppTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardType = if (isPassword) KeyboardType.Password else keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownAnchorField(
            label = "Branch",
            valueText = selected?.name,
            placeholder = if (isLoading) "Loading…" else "",
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = { onSelected(branch); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun RoleDropdown(
    roles: List<SelectorOption>,
    selected: SelectorOption?,
    isLoading: Boolean,
    onSelected: (SelectorOption) -> Unit,
) {
    AppSelectDropdown(
        label = "Role",
        options = roles,
        selected = selected,
        onSelected = onSelected,
        placeholder = if (isLoading) "Loading…" else "",
    )
}
