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
    // Branch's "Need Code?" — a company-unique product code.
    val showCode: Boolean = false,
    val code: String = "",
    // Branch's "Need Product Group?" — a second grouping level above Category.
    val showProductGroup: Boolean = false,
    val productGroups: List<SelectorOption> = emptyList(),
    val productGroup: SelectorOption? = null,
    // Branch's "Need Package?" — the product's pack size.
    val showPackSize: Boolean = false,
    val packSizes: List<SelectorOption> = emptyList(),
    val packSize: SelectorOption? = null,
    // Branch's warranty_controll — the warranty/guarantee type and days.
    val showWarranty: Boolean = false,
    val warrantyType: SelectorOption = WARRANTY_TYPES.first(),
    val warrantyDays: String = "",
    val measurement: MeasurementFields = MeasurementFields(),
    val orderLevel: String = "",
    // Opening stock, gated on the branch's is_opening flag.
    val showOpening: Boolean = false,
    val openingSerialNo: String = "",
    val openingQty: String = "",
    val openingRate: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /**
     * The web requires category, product type, name, description, unit, and
     * numeric purchase and sales prices; brand is optional. A measurement pair
     * must also pass its own rules.
     */
    val canSave: Boolean
        get() = !isSaving &&
            category != null &&
            productType != null &&
            unit != null &&
            name.isNotBlank() &&
            description.isNotBlank() &&
            purchasePrice.toDoubleOrNull() != null &&
            salesPrice.toDoubleOrNull() != null &&
            measurement.error == null

    /** Whether the quantity is being counted rather than typed. */
    val hasOpeningSerials: Boolean get() = openingSerialNo.isNotBlank()

    /**
     * One serial is one unit, split the way the server splits them — on newlines
     * and commas, trimmed, empties dropped — so what is shown here is what will
     * be stored.
     */
    val openingSerialCount: Int
        get() = openingSerialNo.split('\n', '\r', ',')
            .map { it.trim() }
            .count { it.isNotEmpty() }
}
