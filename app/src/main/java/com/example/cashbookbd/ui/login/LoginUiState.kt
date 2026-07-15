package com.example.cashbookbd.ui.login

/**
 * Immutable snapshot of everything the login screen needs to render.
 * Held in the ViewModel so it survives configuration changes.
 */
data class LoginUiState(
    // Empty by default; debug builds seed dev credentials in LoginViewModel.
    val identifier: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
) {
    /** The login button is enabled only when both fields are filled and we're idle. */
    val isSubmitEnabled: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !isLoading
}
