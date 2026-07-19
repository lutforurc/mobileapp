package com.example.cashbookbd.ui.branch

import com.example.cashbookbd.ui.reports.model.SelectorOption

data class AddBranchUiState(
    /**
     * The branch being edited, or null when creating. Also the hashed id the
     * update endpoint resolves on.
     */
    val branchId: String? = null,
    /**
     * Every field the server returned for this branch, kept so the update can
     * post back the settings this form does not show. Empty when creating.
     */
    val existingFields: Map<String, String> = emptyMap(),
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
    val isEditing: Boolean get() = branchId != null

    val screenTitle: String get() = if (isEditing) "Edit Branch" else "Add Branch"

    val saveLabel: String get() = if (isEditing) "Update Branch" else "Save Branch"

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
