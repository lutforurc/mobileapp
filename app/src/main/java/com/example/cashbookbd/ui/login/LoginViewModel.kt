package com.example.cashbookbd.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdentifierChange(value: String) {
        _uiState.update { it.copy(identifier = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /** Clears a shown error after the UI has consumed it (e.g. snackbar dismissed). */
    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.login(state.identifier, state.password)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isLoginSuccessful = true)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                Resource.Loading -> Unit // Not emitted by the repository; nothing to do.
            }
        }
    }

    companion object {
        /** Builds the ViewModel with its repository dependency resolved from the [ServiceLocator]. */
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                LoginViewModel(ServiceLocator.provideAuthRepository(context.applicationContext))
            }
        }
    }
}
