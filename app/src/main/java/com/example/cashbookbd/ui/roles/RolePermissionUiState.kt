package com.example.cashbookbd.ui.roles

import com.example.cashbookbd.data.repository.PermissionItem
import com.example.cashbookbd.data.repository.RoleOption

data class RolePermissionUiState(
    /** Initial roles + permission-catalogue load. */
    val isLoading: Boolean = false,
    val loadError: String? = null,

    val roles: List<RoleOption> = emptyList(),
    /** The full permission catalogue (all assignable permissions). */
    val permissions: List<PermissionItem> = emptyList(),

    val selectedRole: RoleOption? = null,
    val selectedPermissionIds: Set<Int> = emptySet(),
    /** Loading the selected role's current permissions. */
    val isLoadingPermissions: Boolean = false,

    val isSaving: Boolean = false,
    /** One-shot snackbar text (save outcome / info). */
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** Plan/global/company-owned roles cannot be edited here. */
    val isReadonly: Boolean get() = selectedRole?.isReadonly == true

    /** When readonly, only the role's assigned permissions are shown (view only). */
    val visiblePermissions: List<PermissionItem>
        get() = if (isReadonly) permissions.filter { it.id in selectedPermissionIds } else permissions

    val allSelected: Boolean
        get() = visiblePermissions.isNotEmpty() && visiblePermissions.all { it.id in selectedPermissionIds }

    val canSave: Boolean get() = selectedRole != null && !isReadonly && !isSaving && !isLoadingPermissions
}

/** A permission group as rendered in the list: a header and its permissions. */
data class PermissionGroup(val name: String, val permissions: List<PermissionItem>)
