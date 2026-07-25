package com.example.cashbookbd.ui.products

data class AddCategoryUiState(
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web requires both the name and the description. */
    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            description.isNotBlank()
}
