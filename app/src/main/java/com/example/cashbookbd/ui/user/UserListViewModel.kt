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

    fun load(page: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.loadUsers(page, USERS_PER_PAGE, _uiState.value.searchQuery)) {
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

    fun dismissTemporaryPassword() = _uiState.update { it.copy(tempPassword = null) }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                UserListViewModel(ServiceLocator.provideUserRepository(context.applicationContext))
            }
        }
    }
}
