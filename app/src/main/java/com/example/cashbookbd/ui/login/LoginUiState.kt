package com.example.cashbookbd.ui.login

/**
 * Immutable snapshot of everything the login screen needs to render.
 * Held in the ViewModel so it survives configuration changes.
 */
data class LoginUiState(
    // Prefilled dev credentials for quick testing. Clear these before shipping.
    val identifier: String = "lutforurc@gmail.com",
    val password: String = "Lutfor01911282149#",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
) {
    /** The login button is enabled only when both fields are filled and we're idle. */
    val isSubmitEnabled: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !isLoading
}
