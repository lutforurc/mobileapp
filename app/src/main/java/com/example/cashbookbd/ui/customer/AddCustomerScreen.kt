package com.example.cashbookbd.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton

/**
 * Adds a customer/supplier contact — the essential fields of the web's
 * AddCustomerSupplier form (Type, Name, Address, Mobile, Ledger Page, National ID).
 */
@Composable
fun AddCustomerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddCustomerViewModel = viewModel(
        factory = AddCustomerViewModel.provideFactory(LocalContext.current)
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
    // Saved: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Add Customer",
        currentRoute = Routes.CUSTOMERS,
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
                // Shown only when the branch collects customer areas; picking one
                // makes the server compose the address from area/thana/district.
                if (state.showArea) {
                    AppSelectDropdown(
                        label = "Select Area",
                        options = state.areas,
                        selected = state.area,
                        onSelected = viewModel::onArea,
                        placeholder = if (state.isAreasLoading) "Loading areas…" else "Select Area",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppSelectDropdown(
                    label = "Type",
                    options = CUSTOMER_TYPES,
                    selected = state.type,
                    onSelected = viewModel::onType,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = "Enter name",
                    caption = "Name",
                    modifier = Modifier.fillMaxWidth(),
                )
                // Shown only when the branch collects customer sex.
                if (state.showSex) {
                    AppSelectDropdown(
                        label = "Sex",
                        options = CUSTOMER_SEX_OPTIONS,
                        selected = state.sex,
                        onSelected = viewModel::onSex,
                        placeholder = "Select Sex",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppTextField(
                    value = state.address,
                    onValueChange = viewModel::onAddress,
                    label = "Enter Present Address",
                    caption = "Present Address",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.mobile,
                    onValueChange = viewModel::onMobile,
                    label = "Enter mobile number",
                    caption = "Mobile Number",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.ledgerPage,
                    onValueChange = viewModel::onLedgerPage,
                    label = "Enter ledger page",
                    caption = "Ledger Page (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.nationalId,
                    onValueChange = viewModel::onNationalId,
                    label = "Enter national ID",
                    caption = "National ID (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.canSave) {
                    Text(
                        text = "Type, Name, Present Address and Mobile are required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
                PrimaryButton(
                    text = "Save Customer",
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
