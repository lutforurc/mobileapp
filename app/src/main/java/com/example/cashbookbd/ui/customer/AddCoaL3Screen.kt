package com.example.cashbookbd.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton

/**
 * Adds or edits a CoA Level 3 head — a port of the web's /coal3/add-coal3 and
 * /coal3/edit-coal3/:id forms: an L2 select, a Source select (only when the
 * tenant has sources) and a Name. [coaId] null means create; set means edit,
 * prefilled from `coal3/{id}`.
 */
@Composable
fun AddCoaL3Screen(
    navController: NavHostController,
    onLogout: () -> Unit,
    coaId: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddCoaL3ViewModel = viewModel(
        key = coaId ?: "add-coal3",
        factory = AddCoaL3ViewModel.provideFactory(LocalContext.current, coaId),
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
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (state.isEdit) "Edit CoA Level 3" else "Add CoA Level 3",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isFormLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.formError != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.formError.orEmpty(),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::load)
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppSelectDropdown(
                        label = "CoA Level 2",
                        options = state.l2Options,
                        selected = state.selectedL2,
                        onSelected = viewModel::onL2Selected,
                        placeholder = "Select CoA Level 2",
                    )
                    if (state.sources.isNotEmpty()) {
                        AppSelectDropdown(
                            label = "Source",
                            options = state.sources,
                            selected = state.selectedSource,
                            onSelected = viewModel::onSourceSelected,
                            placeholder = "Select Source",
                        )
                    }
                    AppTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = "Enter name",
                        caption = "Name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.canSave && !state.isSaving) {
                        Text(
                            text = if (state.sources.isEmpty()) {
                                "CoA Level 2 and Name are required."
                            } else {
                                "CoA Level 2, Source and Name are required."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                    PrimaryButton(
                        text = if (state.isEdit) "Update" else "Save",
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
