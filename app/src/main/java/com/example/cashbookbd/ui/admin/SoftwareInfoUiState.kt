package com.example.cashbookbd.ui.admin

data class SoftwareInfoUiState(
    val isLoading: Boolean = true,
    /** Prefill load failure — shown full-screen with a Retry. */
    val loadError: String? = null,
    val name: String = "",
    val mobile: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** Name and mobile feed the report footers, so at least one must be present. */
    val canSave: Boolean
        get() = !isSaving && !isLoading && (name.isNotBlank() || mobile.isNotBlank())
}
