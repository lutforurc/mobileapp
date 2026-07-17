package com.example.cashbookbd.ui.user

import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption

/** Shortest password the server accepts (`min:6`). */
const val MIN_PASSWORD_LENGTH = 6

data class AddUserUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val branches: List<BranchOption> = emptyList(),
    val roles: List<SelectorOption> = emptyList(),
    val branch: BranchOption? = null,
    val role: SelectorOption? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val isLoadingOptions: Boolean = false,
    val optionsError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The server takes either an email or a phone, but needs at least one. */
    val hasContact: Boolean get() = email.isNotBlank() || phone.isNotBlank()

    val passwordTooShort: Boolean
        get() = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH

    val passwordsMismatch: Boolean
        get() = confirmPassword.isNotEmpty() && password != confirmPassword

    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            hasContact &&
            branch != null &&
            role != null &&
            password.length >= MIN_PASSWORD_LENGTH &&
            password == confirmPassword
}
