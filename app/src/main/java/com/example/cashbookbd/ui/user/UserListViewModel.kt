package com.example.cashbookbd.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.UserRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the User List: search, pagination, and the temporary-password action. */
class UserListViewModel(
    private val repository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    init {
        load(page = 1)
    }

    /** Refreshes the current page without the spinner — the on-resume reload. */
    fun reloadCurrent() = load(page = _uiState.value.currentPage, silent = true)

    fun load(page: Int, silent: Boolean = false) {
        if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val current = _uiState.value
            when (val result = repository.loadUsers(page, USERS_PER_PAGE, current.searchQuery, current.allCompanies)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    /** Runs the current query from the first page (the web's Search button). */
    fun onSearch() = load(page = 1)

    /** Flipping the company scope starts over from page 1, like the web. */
    fun onAllCompanies(on: Boolean) {
        if (_uiState.value.allCompanies == on) return
        _uiState.update { it.copy(allCompanies = on) }
        load(page = 1)
    }

    fun nextPage() {
        if (_uiState.value.canNext) load(_uiState.value.currentPage + 1)
    }

    fun prevPage() {
        if (_uiState.value.canPrev) load(_uiState.value.currentPage - 1)
    }

    fun generateTemporaryPassword(userId: String) {
        if (_uiState.value.tempPasswordForId != null) return
        _uiState.update { it.copy(tempPasswordForId = userId) }
        viewModelScope.launch {
            when (val result = repository.generateTemporaryPassword(userId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(tempPasswordForId = null, tempPassword = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        tempPasswordForId = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Switches a row's sign-in on/off — the web's toggle: not optimistic (the
     * switch waits, disabled), and a success writes a local override rather
     * than refetching the page.
     */
    fun toggleStatus(userId: String, enabled: Boolean) {
        if (_uiState.value.statusBusyId != null) return
        _uiState.update { it.copy(statusBusyId = userId) }
        viewModelScope.launch {
            when (val result = repository.toggleStatus(userId, enabled)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        statusBusyId = null,
                        statusOverrides = it.statusOverrides + (userId to enabled),
                        actionMessage = result.data,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        statusBusyId = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun dismissTemporaryPassword() = _uiState.update { it.copy(tempPassword = null) }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                UserListViewModel(ServiceLocator.provideUserRepository(context.applicationContext))
            }
        }
    }
}
