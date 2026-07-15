package com.example.cashbookbd.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
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

    fun onRememberMeChange(value: Boolean) {
        _uiState.update { it.copy(rememberMe = value) }
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
            when (val result = authRepository.login(state.identifier, state.password, state.rememberMe)) {
                is Resource.Success -> {
                    // Load the user's permissions before entering the app so menus
                    // and screens gate correctly. A settings failure here isn't fatal:
                    // the token is valid, so continue with no permissions (the user
                    // can still reach non-gated screens) and let boot/refresh retry.
                    sessionRepository.refresh()
                    _uiState.update {
                        it.copy(isLoading = false, isLoginSuccessful = true)
                    }
                }

                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                Resource.Loading -> Unit // Not emitted by the repository; nothing to do.
            }
        }
    }

    companion object {
        // Login prefill comes from BuildConfig, which is sourced from
        // local.properties for debug and is always empty for release (see
        // app/build.gradle.kts). No credentials are hardcoded here, so nothing
        // sensitive ships in a built/installed app.
        private fun initialState(): LoginUiState {
            val identifier = BuildConfig.DEV_LOGIN_IDENTIFIER
            val password = BuildConfig.DEV_LOGIN_PASSWORD
            return if (identifier.isNotBlank() || password.isNotBlank()) {
                LoginUiState(identifier = identifier, password = password)
            } else {
                LoginUiState()
            }
        }

        /** Builds the ViewModel with its repository dependency resolved from the [ServiceLocator]. */
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                LoginViewModel(
                    authRepository = ServiceLocator.provideAuthRepository(appContext),
                    sessionRepository = ServiceLocator.provideSessionRepository(appContext),
                )
            }
        }
    }
}
