package com.example.cashbookbd.ui.products

import com.example.cashbookbd.ui.reports.model.SelectorOption

data class AddProductUiState(
    // Loaded dropdown choices.
    val categories: List<SelectorOption> = emptyList(),
    val units: List<SelectorOption> = emptyList(),
    val productTypes: List<SelectorOption> = emptyList(),
    val brands: List<SelectorOption> = emptyList(),
    val isLoadingOptions: Boolean = true,
    val optionsError: String? = null,
    // Selections + typed fields.
    val category: SelectorOption? = null,
    val productType: SelectorOption? = null,
    val unit: SelectorOption? = null,
    val brand: SelectorOption? = null,
    val name: String = "",
    val description: String = "",
    val purchasePrice: String = "",
    val salesPrice: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /**
     * The web requires category, product type, name, unit, and numeric purchase
     * and sales prices; brand and description are optional.
     */
    val canSave: Boolean
        get() = !isSaving &&
            category != null &&
            productType != null &&
            unit != null &&
            name.isNotBlank() &&
            purchasePrice.toDoubleOrNull() != null &&
            salesPrice.toDoubleOrNull() != null
}
