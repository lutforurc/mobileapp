package com.example.cashbookbd.ui.products

data class AddUnitUiState(
    val name: String = "",
    val shortName: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web requires the unit name, short name and description. */
    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            shortName.isNotBlank() &&
            description.isNotBlank()
}
