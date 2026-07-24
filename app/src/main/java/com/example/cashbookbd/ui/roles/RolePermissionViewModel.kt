package com.example.cashbookbd.ui.roles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.PermissionItem
import com.example.cashbookbd.data.repository.RoleOption
import com.example.cashbookbd.data.repository.RoleRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Role & Permission screen: loads the roles and the permission
 * catalogue, then the selected role's permissions, and assigns a new set.
 */
class RolePermissionViewModel(
    private val repository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RolePermissionUiState())
    val uiState: StateFlow<RolePermissionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val rolesResult = async { repository.loadRoles() }
            val permissionsResult = async { repository.loadPermissions() }
            val roles = rolesResult.await()
            val permissions = permissionsResult.await()

            val failure = listOfNotNull(
                roles as? Resource.Error,
                permissions as? Resource.Error,
            ).firstOrNull()

            if (failure != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = failure.message,
                        sessionExpired = it.sessionExpired || failure.isUnauthorized,
                    )
                }
                return@launch
            }

            val roleList = (roles as Resource.Success).data
            _uiState.update {
                it.copy(
                    isLoading = false,
                    roles = roleList,
                    permissions = (permissions as Resource.Success).data,
                )
            }
            // Preselect the first role, like the web.
            roleList.firstOrNull()?.let(::selectRole)
        }
    }

    fun selectRole(role: RoleOption) {
        _uiState.update {
            it.copy(selectedRole = role, selectedPermissionIds = emptySet(), isLoadingPermissions = true)
        }
        viewModelScope.launch {
            when (val result = repository.loadSelectedPermissionIds(role.id)) {
                is Resource.Success -> _uiState.update { state ->
                    // Ignore a stale response if the user switched roles meanwhile.
                    if (state.selectedRole?.id != role.id) state
                    else state.copy(isLoadingPermissions = false, selectedPermissionIds = result.data.toSet())
                }
                is Resource.Error -> _uiState.update { state ->
                    if (state.selectedRole?.id != role.id) state
                    else state.copy(
                        isLoadingPermissions = false,
                        message = result.message,
                        sessionExpired = state.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun togglePermission(id: Int) = _uiState.update { state ->
        if (state.isReadonly) return@update state
        val next = state.selectedPermissionIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        state.copy(selectedPermissionIds = next)
    }

    fun toggleGroup(permissions: List<PermissionItem>, checked: Boolean) = _uiState.update { state ->
        if (state.isReadonly) return@update state
        val ids = permissions.map { it.id }
        val next = state.selectedPermissionIds.toMutableSet()
        if (checked) next.addAll(ids) else next.removeAll(ids.toSet())
        state.copy(selectedPermissionIds = next)
    }

    fun toggleAll(checked: Boolean) = _uiState.update { state ->
        if (state.isReadonly) return@update state
        val ids = state.visiblePermissions.map { it.id }
        val next = state.selectedPermissionIds.toMutableSet()
        if (checked) next.addAll(ids) else next.removeAll(ids.toSet())
        state.copy(selectedPermissionIds = next)
    }

    fun save() {
        val state = _uiState.value
        val role = state.selectedRole ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.assignPermissions(role.id, state.selectedPermissionIds.toList())) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    // Re-read to reflect exactly what the server stored.
                    selectRole(role)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                RolePermissionViewModel(ServiceLocator.provideRoleRepository(context.applicationContext))
            }
        }
    }
}
