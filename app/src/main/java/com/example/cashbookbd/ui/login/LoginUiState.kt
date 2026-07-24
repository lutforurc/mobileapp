package com.example.cashbookbd.ui.login

import com.example.cashbookbd.data.repository.DeviceLimitBlock

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
    /** Set when login is blocked on the plan's device limit; shows the sign-out panel. */
    val deviceLimit: DeviceLimitBlock? = null,
    /** The device whose sign-out is in flight (its button shows a spinner). */
    val releasingDeviceId: Long? = null,
    /** A failed sign-out message, shown inside the device-limit panel. */
    val deviceLimitError: String? = null,
) {
    /** The login button is enabled only when both fields are filled and we're idle. */
    val isSubmitEnabled: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !isLoading
}
