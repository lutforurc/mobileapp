package com.example.cashbookbd.ui.products

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.NewProduct
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the Add Product form: loads the dropdowns, collects the fields, saves. */
class AddProductViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState())
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            when (val result = repository.loadProductFormOptions()) {
                is Resource.Success -> _uiState.update {
                    val options = result.data
                    it.copy(
                        isLoadingOptions = false,
                        categories = options.categories,
                        units = options.units,
                        productTypes = options.productTypes,
                        brands = options.brands,
                        // Pre-select the first product type/unit, like the web.
                        productType = it.productType ?: options.productTypes.firstOrNull(),
                        unit = it.unit ?: options.units.firstOrNull(),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingOptions = false,
                        optionsError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onCategory(option: SelectorOption) = _uiState.update { it.copy(category = option) }
    fun onProductType(option: SelectorOption) = _uiState.update { it.copy(productType = option) }
    fun onUnit(option: SelectorOption) = _uiState.update { it.copy(unit = option) }
    fun onBrand(option: SelectorOption) = _uiState.update { it.copy(brand = option) }
    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun onPurchasePrice(value: String) = _uiState.update { it.copy(purchasePrice = value) }
    fun onSalesPrice(value: String) = _uiState.update { it.copy(salesPrice = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val category = state.category ?: return
        val productType = state.productType ?: return
        val unit = state.unit ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.storeProduct(
                NewProduct(
                    categoryId = category.id,
                    productType = productType.id,
                    name = state.name,
                    description = state.description,
                    purchasePrice = state.purchasePrice,
                    salesPrice = state.salesPrice,
                    unitId = unit.id,
                    brandId = state.brand?.id.orEmpty(),
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
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                AddProductViewModel(ServiceLocator.provideProductRepository(context.applicationContext))
            }
        }
    }
}
