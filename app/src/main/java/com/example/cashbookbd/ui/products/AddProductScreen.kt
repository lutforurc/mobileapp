package com.example.cashbookbd.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
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

/**
 * Adds a product — a port of the web's "New Product" form. Loads the category,
 * product-type, unit and brand dropdowns first, then collects the fields.
 * Category, Product Type, Name, Unit and both prices are required; Brand and
 * Description are optional.
 */
@Composable
fun AddProductScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddProductViewModel = viewModel(
        factory = AddProductViewModel.provideFactory(LocalContext.current)
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
        title = "Add Product",
        currentRoute = Routes.PRODUCTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingOptions -> Center {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.optionsError != null -> Center {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.optionsError!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::loadOptions)
                    }
                }

                else -> ProductForm(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ProductForm(state: AddProductUiState, viewModel: AddProductViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppSelectDropdown(
            label = "Brand (optional)",
            options = state.brands,
            selected = state.brand,
            onSelected = viewModel::onBrand,
            placeholder = "Select brand",
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Category",
            options = state.categories,
            selected = state.category,
            onSelected = viewModel::onCategory,
            placeholder = "Select category",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = "Enter product name",
            caption = "Product Name",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.description,
            onValueChange = viewModel::onDescription,
            label = "Enter description",
            caption = "Description (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Product Type",
            options = state.productTypes,
            selected = state.productType,
            onSelected = viewModel::onProductType,
            placeholder = "Select product type",
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Unit",
            options = state.units,
            selected = state.unit,
            onSelected = viewModel::onUnit,
            placeholder = "Select unit",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.purchasePrice,
            onValueChange = viewModel::onPurchasePrice,
            label = "Enter purchase price",
            caption = "Purchase Price",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.salesPrice,
            onValueChange = viewModel::onSalesPrice,
            label = "Enter sales price",
            caption = "Sales Price",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.canSave) {
            Text(
                text = "Category, Product Type, Name, Unit and both prices are required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
        PrimaryButton(
            text = "Save Product",
            onClick = viewModel::save,
            enabled = state.canSave,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
