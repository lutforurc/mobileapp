package com.example.cashbookbd.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.EditUser
import com.example.cashbookbd.data.repository.UserRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Edit User form: loads the branch/role pickers and the user's current
 * values together, then submits the update.
 */
class EditUserViewModel(
    private val userId: String,
    private val repository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUserUiState(userId = userId))
    val uiState: StateFlow<EditUserUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            // The pickers and the prefill come from three endpoints; fetch together.
            val branchesDeferred = async { repository.loadAllBranches() }
            val rolesDeferred = async { repository.loadRoles() }
            val detailDeferred = async { repository.showUser(userId) }
            val branchesResult = branchesDeferred.await()
            val rolesResult = rolesDeferred.await()
            val detailResult = detailDeferred.await()

            val failure = listOfNotNull(
                branchesResult as? Resource.Error,
                rolesResult as? Resource.Error,
                detailResult as? Resource.Error,
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

            val branches = (branchesResult as Resource.Success).data
            val roles = (rolesResult as Resource.Success).data
            val detail = (detailResult as Resource.Success).data

            _uiState.update {
                it.copy(
                    isLoading = false,
                    email = detail.email,
                    name = detail.name,
                    phone = detail.phone,
                    lang = detail.lang,
                    branches = branches,
                    roles = roles,
                    branch = branches.firstOrNull { b -> b.id.toString() == detail.branchId },
                    role = roles.firstOrNull { r -> r.id == detail.roleId },
                    sidebarMenu = detail.sidebarMenu,
                    useFilterParameter = detail.useFilterParameter,
                )
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value) }
    fun onLang(value: String) = _uiState.update { it.copy(lang = value) }
    fun onBranch(option: BranchOption) = _uiState.update { it.copy(branch = option) }
    fun onRole(option: SelectorOption) = _uiState.update { it.copy(role = option) }
    fun onSidebarMenu(value: Boolean) = _uiState.update { it.copy(sidebarMenu = value) }
    fun onUseFilterParameter(value: Boolean) = _uiState.update { it.copy(useFilterParameter = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val branch = state.branch ?: return
        val role = state.role ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.update(
                EditUser(
                    userId = userId,
                    name = state.name,
                    phone = state.phone,
                    branchId = branch.id.toString(),
                    roleId = role.id,
                    lang = state.lang,
                    sidebarMenu = state.sidebarMenu,
                    useFilterParameter = state.useFilterParameter,
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, userId: String) = viewModelFactory {
            initializer {
                EditUserViewModel(
                    userId = userId,
                    repository = ServiceLocator.provideUserRepository(context.applicationContext),
                )
            }
        }
    }
}
