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
    /** A filled email the mailer cannot read; blank is fine (web 6573a2c6). */
    val emailInvalid: Boolean
        get() = email.isNotBlank() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())

    /** The name is the only thing the web asks for; a filled email is still checked. */
    val canSave: Boolean
        get() = !isSaving && name.isNotBlank() && !emailInvalid
}
