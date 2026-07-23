package com.example.cashbookbd.ui.user

import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * The Edit User form. Mirrors the web's EditUser, minus the password fields —
 * `user/user-update` does not change the password (the temporary-password action
 * on the list does that instead), so showing password inputs here would mislead.
 */
data class EditUserUiState(
    val userId: String = "",
    /** Read-only; the update endpoint keeps the email unchanged. */
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val lang: String = "",
    val branches: List<BranchOption> = emptyList(),
    val roles: List<SelectorOption> = emptyList(),
    val branch: BranchOption? = null,
    val role: SelectorOption? = null,
    val sidebarMenu: Boolean = false,
    val useFilterParameter: Boolean = false,

    /** True while the prefill + pickers load. */
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean
        get() = !isSaving && name.isNotBlank() && branch != null && role != null
}
