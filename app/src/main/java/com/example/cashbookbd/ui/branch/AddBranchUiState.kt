package com.example.cashbookbd.ui.branch

import com.example.cashbookbd.ui.reports.model.SelectorOption

data class AddBranchUiState(
    val name: String = "",
    val branchTypes: List<SelectorOption> = emptyList(),
    val businessTypes: List<SelectorOption> = emptyList(),
    val branchType: SelectorOption? = null,
    val businessType: SelectorOption? = null,
    val address: String = "",
    val phone: String = "",
    val contactPerson: String = "",
    val email: String = "",
    /** True while the two pickers are being fetched. */
    val isLoadingOptions: Boolean = false,
    val optionsError: String? = null,
    val isSaving: Boolean = false,
    /** Failure reason from the last save attempt. */
    val error: String? = null,
    /** Set once the branch is created; the screen navigates back. */
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The server requires all of these; email is optional. */
    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            branchType != null &&
            businessType != null &&
            address.isNotBlank() &&
            phone.isNotBlank() &&
            contactPerson.isNotBlank()
}
