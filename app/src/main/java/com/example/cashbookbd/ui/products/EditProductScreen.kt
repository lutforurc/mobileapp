package com.example.cashbookbd.ui.products

import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.data.repository.ProductUpdate
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The web's warrantyType constant, verbatim. */
private val WARRANTY_TYPES = listOf(
    SelectorOption("0", "Not Applicable"),
    SelectorOption("1", "Warranty"),
    SelectorOption("2", "Guarantee"),
    SelectorOption("3", "Custom"),
)

data class EditProductUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,

    val categories: List<SelectorOption> = emptyList(),
    val productTypes: List<SelectorOption> = emptyList(),
    val units: List<SelectorOption> = emptyList(),
    val brands: List<SelectorOption> = emptyList(),
    /** The branch's warranty_controll: shows the warranty fields, like the web. */
    val showWarranty: Boolean = false,

    val category: SelectorOption? = null,
    val productType: SelectorOption? = null,
    val unit: SelectorOption? = null,
    val brand: SelectorOption? = null,
    val name: String = "",
    val description: String = "",
    val purchasePrice: String = "",
    val salesPrice: String = "",
    val orderLevel: String = "",
    val warrantyType: SelectorOption = WARRANTY_TYPES.first(),
    val warrantyDays: String = "",

    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web's required set: category, type, name, description, unit, prices. */
    val canSave: Boolean
        get() = category != null && productType != null && unit != null &&
            name.isNotBlank() && description.isNotBlank() &&
            purchasePrice.toDoubleOrNull() != null && salesPrice.toDoubleOrNull() != null &&
            !isSaving
}

class EditProductViewModel(
    private val productId: String,
    private val repository: ProductRepository,
    showWarranty: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProductUiState(showWarranty = showWarranty))
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    /** Echoed back on update — the server writes manufacture_id unconditionally. */
    private var manufactureId: String = ""

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val options = repository.loadProductFormOptions()
            if (options is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = options.message,
                        sessionExpired = it.sessionExpired || options.isUnauthorized,
                    )
                }
                return@launch
            }
            val detail = repository.fetchProductEdit(productId)
            if (detail is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = detail.message,
                        sessionExpired = it.sessionExpired || detail.isUnauthorized,
                    )
                }
                return@launch
            }

            val opts = (options as Resource.Success).data
            val data = (detail as Resource.Success).data
            manufactureId = data.manufactureId

            fun List<SelectorOption>.byId(id: String): SelectorOption? =
                firstOrNull { it.id == id }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    categories = opts.categories,
                    productTypes = opts.productTypes,
                    units = opts.units,
                    brands = opts.brands,
                    category = opts.categories.byId(data.categoryId),
                    // The web falls back to the first option when the stored id
                    // is not in the list.
                    productType = opts.productTypes.byId(data.productType)
                        ?: opts.productTypes.firstOrNull(),
                    unit = opts.units.byId(data.unitId) ?: opts.units.firstOrNull(),
                    brand = opts.brands.byId(data.manufactureId),
                    name = data.name,
                    description = data.description,
                    purchasePrice = data.purchasePrice,
                    salesPrice = data.salesPrice,
                    orderLevel = data.orderLevel,
                    warrantyType = WARRANTY_TYPES.firstOrNull { w -> w.id == data.warrantyType }
                        ?: WARRANTY_TYPES.first(),
                    warrantyDays = data.warrantyDays,
                )
            }
        }
    }

    fun onCategory(value: SelectorOption) = _uiState.update { it.copy(category = value) }
    fun onProductType(value: SelectorOption) = _uiState.update { it.copy(productType = value) }
    fun onUnit(value: SelectorOption) = _uiState.update { it.copy(unit = value) }
    fun onBrand(value: SelectorOption) = _uiState.update {
        manufactureId = value.id
        it.copy(brand = value)
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun onPurchasePrice(value: String) = _uiState.update { it.copy(purchasePrice = value) }
    fun onSalesPrice(value: String) = _uiState.update { it.copy(salesPrice = value) }
    fun onOrderLevel(value: String) = _uiState.update { it.copy(orderLevel = value) }

    /** Changing the type clears the days, as the web does. */
    fun onWarrantyType(value: SelectorOption) =
        _uiState.update { it.copy(warrantyType = value, warrantyDays = "") }

    fun onWarrantyDays(value: String) = _uiState.update { it.copy(warrantyDays = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.updateProduct(
                ProductUpdate(
                    productId = productId,
                    name = state.name,
                    description = state.description,
                    categoryId = state.category?.id.orEmpty(),
                    productType = state.productType?.id.orEmpty(),
                    unitId = state.unit?.id.orEmpty(),
                    purchasePrice = state.purchasePrice,
                    salesPrice = state.salesPrice,
                    orderLevel = state.orderLevel,
                    manufactureId = manufactureId,
                    warrantyType = if (state.showWarranty) state.warrantyType.id else null,
                    warrantyDays = if (state.showWarranty) state.warrantyDays else null,
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
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
        fun provideFactory(context: Context, productId: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                EditProductViewModel(
                    productId = productId,
                    repository = ServiceLocator.provideProductRepository(appContext),
                    showWarranty = ServiceLocator.provideSessionManager(appContext)
                        .state.value.settings?.warrantyControll == true,
                )
            }
        }
    }
}

/**
 * Edits a product — the web's Edit Product form: category, type, name,
 * description, prices, the branch-gated warranty pair, unit and order level.
 * The update echoes the brand, which the form does not offer to change beyond
 * picking another (the server writes it on every save).
 */
@Composable
fun EditProductScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    productId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: EditProductViewModel = viewModel(
        factory = EditProductViewModel.provideFactory(context, productId)
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
        title = "Edit Product",
        currentRoute = Routes.PRODUCTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Center {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.loadError != null -> Center {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.loadError!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::load)
                    }
                }

                else -> EditForm(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EditForm(state: EditProductUiState, viewModel: EditProductViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppSelectDropdown(
            label = "Category",
            options = state.categories,
            selected = state.category,
            onSelected = viewModel::onCategory,
            placeholder = "Select category",
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
            caption = "Product Description",
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
        if (state.showWarranty) {
            AppSelectDropdown(
                label = "Warranty/Guarantee Type",
                options = WARRANTY_TYPES,
                selected = state.warrantyType,
                onSelected = viewModel::onWarrantyType,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.warrantyDays,
                onValueChange = viewModel::onWarrantyDays,
                label = "Enter warranty days",
                caption = "Warranty Days",
                // Types 1/2 take a day count; Custom takes free text, like the web.
                keyboardType = if (state.warrantyType.id in listOf("1", "2")) {
                    KeyboardType.Number
                } else {
                    KeyboardType.Text
                },
                enabled = state.warrantyType.id != "0",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppSelectDropdown(
            label = "Unit",
            options = state.units,
            selected = state.unit,
            onSelected = viewModel::onUnit,
            placeholder = "Select unit",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.orderLevel,
            onValueChange = viewModel::onOrderLevel,
            label = "Enter order level",
            caption = "Order Level",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "Brand (optional)",
            options = state.brands,
            selected = state.brand,
            onSelected = viewModel::onBrand,
            placeholder = "Select brand",
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.canSave && !state.isSaving) {
            Text(
                text = "Category, Product Type, Name, Description, Unit and both prices are required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        PrimaryButton(
            text = "Update",
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
