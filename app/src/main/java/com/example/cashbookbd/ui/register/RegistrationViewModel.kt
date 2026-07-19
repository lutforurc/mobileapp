package com.example.cashbookbd.ui.register

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.dto.RegisterOtpRequest
import com.example.cashbookbd.data.repository.RegistrationRepository
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the two-step company sign-up. Step 1 requests an OTP with the form;
 * step 2 verifies it, which creates the account and returns a token the
 * repository stores — so, like the web, this refreshes the session and reports
 * success for the screen to enter the dashboard.
 */
class RegistrationViewModel(
    private val repository: RegistrationRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onCompanyName(v: String) = edit { it.copy(companyName = v) }
    fun onUserName(v: String) = edit { it.copy(userName = v) }
    fun onContactPerson(v: String) = edit { it.copy(contactPerson = v) }
    fun onMobile(v: String) = edit { it.copy(mobile = v) }
    fun onEmail(v: String) = edit { it.copy(email = v) }
    fun onAddress(v: String) = edit { it.copy(address = v) }
    fun onPassword(v: String) = edit { it.copy(password = v) }
    fun onConfirmPassword(v: String) = edit { it.copy(confirmPassword = v) }
    fun onOtp(v: String) = edit { it.copy(otp = v.filter(Char::isDigit).take(8)) }

    fun togglePasswordVisibility() =
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun toggleConfirmPasswordVisibility() =
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }

    /** Field edits clear any showing error so a stale message doesn't linger. */
    private fun edit(transform: (RegistrationUiState) -> RegistrationUiState) =
        _uiState.update { transform(it).copy(errorMessage = null) }

    /** Step 1. Validates locally, then requests the OTP and moves to the OTP step. */
    fun requestOtp() {
        val state = _uiState.value
        if (!state.canRequestOtp) return
        if (!Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = repository.requestOtp(
                RegisterOtpRequest(
                    companyName = state.companyName.trim(),
                    userName = state.userName.trim(),
                    contactPerson = state.contactPerson.trim(),
                    mobile = state.mobile.trim(),
                    email = state.email.trim(),
                    address = state.address.trim(),
                    password = state.password,
                    passwordConfirmation = state.confirmPassword,
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        step = RegisterStep.OTP,
                        otpSessionId = result.data,
                        otp = "",
                        infoMessage = "We sent a code to ${state.mobile.trim()}.",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Step 2. Verifies the code; on success the token is stored — enter the app. */
    fun verifyOtp() {
        val state = _uiState.value
        if (!state.canVerify || state.otpSessionId.isBlank()) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.verifyOtp(state.otpSessionId, state.mobile.trim(), state.otp)) {
                is Resource.Success -> {
                    // Load permissions before entering, exactly as login does; a
                    // failure here isn't fatal — the token is valid, boot retries.
                    sessionRepository.refresh()
                    _uiState.update { it.copy(isSubmitting = false, isRegistered = true) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Re-request a fresh code with the same form (the OTP step's "Resend"). */
    fun resendOtp() {
        _uiState.update { it.copy(step = RegisterStep.FORM) }
        requestOtp()
    }

    /** Back from the OTP step to edit the form; keeps every entered value. */
    fun backToForm() = _uiState.update { it.copy(step = RegisterStep.FORM, otp = "", errorMessage = null) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }
    fun onInfoShown() = _uiState.update { it.copy(infoMessage = null) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                RegistrationViewModel(
                    repository = ServiceLocator.provideRegistrationRepository(appContext),
                    sessionRepository = ServiceLocator.provideSessionRepository(appContext),
                )
            }
        }
    }
}
