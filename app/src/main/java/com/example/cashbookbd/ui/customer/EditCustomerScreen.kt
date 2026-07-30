package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditCustomerUiState(
    val opening: String = "",
    val ledgerPage: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && (opening.isNotBlank() || ledgerPage.isNotBlank())
}

class EditCustomerViewModel(
    private val customerId: String,
    private val repository: CustomerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditCustomerUiState())
    val uiState: StateFlow<EditCustomerUiState> = _uiState.asStateFlow()

    fun onOpening(value: String) = _uiState.update { it.copy(opening = value) }
    fun onLedgerPage(value: String) = _uiState.update { it.copy(ledgerPage = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.updateOpeningLedger(customerId, state.opening, state.ledgerPage)) {
                is Resource.Success -> _uiState.update { it.copy(isSaving = false, savedMessage = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, customerId: String) = viewModelFactory {
            initializer {
                EditCustomerViewModel(
                    customerId = customerId,
                    repository = ServiceLocator.provideCustomerRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * Sets a customer's opening balance and/or ledger page — the essential part of
 * the web's inline customer edit. Opening is one-time (the server rejects a change
 * once set); a blank field is left unchanged.
 */
@Composable
fun EditCustomerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    customerId: String? = null,
) {
    if (customerId.isNullOrBlank()) {
        AuthenticatedShell(
            title = "Edit Customer",
            currentRoute = Routes.CUSTOMERS,
            navController = navController,
            onLogout = onLogout,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Customer not found.", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    val viewModel: EditCustomerViewModel = viewModel(
        key = customerId,
        factory = EditCustomerViewModel.provideFactory(LocalContext.current, customerId),
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
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Opening / Ledger",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Set the opening balance (needed at the start of business) and/or the ledger page. " +
                        "Opening can be set once; leave a field blank to keep it unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                AppTextField(
                    value = state.opening,
                    onValueChange = viewModel::onOpening,
                    label = "Enter opening balance",
                    caption = "Opening Balance",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.ledgerPage,
                    onValueChange = viewModel::onLedgerPage,
                    label = "Enter ledger page",
                    caption = "Ledger Page",
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Save",
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
