package com.example.cashbookbd.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.NewUser
import com.example.cashbookbd.data.repository.ReportRepository
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

/** Backs the Add User form: loads its branch/role pickers, then creates the user. */
class AddUserViewModel(
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddUserUiState())
    val uiState: StateFlow<AddUserUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            // The two pickers come from different endpoints; fetch them together.
            val branchesResult = async { reportRepository.getBranches() }
            val rolesResult = async { userRepository.loadRoles() }
            val branches = branchesResult.await()
            val roles = rolesResult.await()

            val failure = listOfNotNull(
                branches as? Resource.Error,
                roles as? Resource.Error,
            ).firstOrNull()

            val branchOptions = (branches as? Resource.Success)?.data?.branches.orEmpty()
            _uiState.update { state ->
                state.copy(
                    isLoadingOptions = false,
                    branches = branchOptions,
                    roles = (roles as? Resource.Success)?.data.orEmpty(),
                    // The web preselects the first branch; most users are scoped
                    // to a single one, so leaving it blank would be busywork.
                    branch = state.branch ?: branchOptions.firstOrNull(),
                    optionsError = failure?.message,
                    sessionExpired = state.sessionExpired || failure?.isUnauthorized == true,
                )
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value) }
    fun onPassword(value: String) = _uiState.update { it.copy(password = value) }
    fun onConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value) }
    fun onBranch(option: BranchOption) = _uiState.update { it.copy(branch = option) }
    fun onRole(option: SelectorOption) = _uiState.update { it.copy(role = option) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val branch = state.branch ?: return
        val role = state.role ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = userRepository.store(
                NewUser(
                    name = state.name,
                    email = state.email,
                    phone = state.phone,
                    branchId = branch.id.toString(),
                    roleId = role.id,
                    password = state.password,
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
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                AddUserViewModel(
                    userRepository = ServiceLocator.provideUserRepository(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                )
            }
        }
    }
}
