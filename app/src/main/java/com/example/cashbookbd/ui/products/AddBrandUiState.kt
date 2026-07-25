package com.example.cashbookbd.ui.products

data class AddBrandUiState(
    val name: String = "",
    val address: String = "",
    val contacts: String = "",
    val email: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web requires name, address and contact; email is optional. */
    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            address.isNotBlank() &&
            contacts.isNotBlank()
}
