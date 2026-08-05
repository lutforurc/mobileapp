package com.example.cashbookbd.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The three stops of the reset: number → OTP → new password. */
enum class ResetStep { REQUEST, VERIFY, RESET }

data class ForgotPasswordUiState(
    val step: ResetStep = ResetStep.REQUEST,
    val mobile: String = "",
    val otp: String = "",
    val otpSessionId: String = "",
    val resetToken: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    /** Set once the password is updated; the screen returns to the login. */
    val done: Boolean = false,
)

class ForgotPasswordViewModel(
    private val api: ReportApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onMobile(v: String) = _uiState.update { it.copy(mobile = v.take(20)) }
    fun onOtp(v: String) = _uiState.update { it.copy(otp = v.filter { c -> c.isDigit() }.take(8)) }
    fun onPassword(v: String) = _uiState.update { it.copy(password = v) }
    fun onPasswordConfirm(v: String) = _uiState.update { it.copy(passwordConfirm = v) }

    /** Step 1 — the server answers the same for unknown numbers (no enumeration). */
    fun requestOtp() {
        val state = _uiState.value
        if (state.mobile.isBlank()) {
            _uiState.update { it.copy(message = "Please enter your mobile number.") }
            return
        }
        if (state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = post("forgot-password/request-otp", mapOf("mobile" to state.mobile.trim()))
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        step = ResetStep.VERIFY,
                        otp = "",
                        otpSessionId = result.data.getAsJsonObject("data")
                            ?.get("otp_session_id")?.takeUnless { e -> e.isJsonNull }?.asString.orEmpty(),
                        message = result.data.msg() ?: "If this number is registered, an OTP has been sent.",
                    )
                }
                is Resource.Error -> _uiState.update { it.copy(isBusy = false, message = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Step 2 — five tries, five minutes. */
    fun verifyOtp() {
        val state = _uiState.value
        if (state.otp.isBlank()) {
            _uiState.update { it.copy(message = "Please enter OTP.") }
            return
        }
        if (state.otpSessionId.isBlank()) {
            _uiState.update { it.copy(message = "OTP session not found. Please request OTP again.", step = ResetStep.REQUEST) }
            return
        }
        if (state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = post(
                "forgot-password/verify-otp",
                mapOf(
                    "mobile" to state.mobile.trim(),
                    "otp" to state.otp,
                    "otp_session_id" to state.otpSessionId,
                ),
            )
            when (result) {
                is Resource.Success -> {
                    val token = result.data.getAsJsonObject("data")
                        ?.get("reset_token")?.takeUnless { e -> e.isJsonNull }?.asString.orEmpty()
                    _uiState.update {
                        if (token.isBlank()) {
                            it.copy(isBusy = false, message = "Reset token was not received. Please try again.")
                        } else {
                            it.copy(isBusy = false, step = ResetStep.RESET, resetToken = token)
                        }
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isBusy = false, message = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Step 3 — minimum 8, both boxes must agree. */
    fun resetPassword() {
        val state = _uiState.value
        if (state.password.isBlank() || state.passwordConfirm.isBlank()) {
            _uiState.update { it.copy(message = "Please enter the new password in both boxes.") }
            return
        }
        if (state.password != state.passwordConfirm) {
            _uiState.update { it.copy(message = "Password and confirmation do not match.") }
            return
        }
        if (state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = post(
                "forgot-password/reset",
                mapOf(
                    "mobile" to state.mobile.trim(),
                    "reset_token" to state.resetToken,
                    "password" to state.password,
                    "password_confirmation" to state.passwordConfirm,
                ),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isBusy = false, done = true, message = result.data.msg() ?: "Password updated successfully.")
                }
                is Resource.Error -> _uiState.update { it.copy(isBusy = false, message = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun changeMobile() = _uiState.update {
        it.copy(step = ResetStep.REQUEST, otp = "", otpSessionId = "", resetToken = "")
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun JsonObject.msg(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

    /** Public endpoints; errors carry `error.message` or field errors. */
    private suspend fun post(url: String, body: Map<String, Any>): Resource<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.postAny(url, body)
                val json = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: response.errorBody()?.string()
                        ?.let { runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull() }
                        ?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
                val success = json.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                if (success == true) return@withContext Resource.Success(json)
                // The web's extraction order: message → error.message → errors.
                val message = json.msg()
                    ?: json.getAsJsonObject("error")?.get("message")
                        ?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
                    ?: json.getAsJsonObject("errors")?.entrySet()
                        ?.flatMap { (_, v) -> if (v.isJsonArray) v.asJsonArray.map { it.asString } else emptyList() }
                        ?.joinToString(" | ")?.ifBlank { null }
                Resource.Error(message ?: "Request failed. Please try again.")
            } catch (e: java.io.IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                ForgotPasswordViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                )
            }
        }
    }
}

/**
 * Password reset by OTP, in the login's own three steps: the number, the code
 * it was texted, and the new password.
 */
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = viewModel(
        factory = ForgotPasswordViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.done) {
        if (state.done) onBackToLogin()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Forgot Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = AppFontWeight.SemiBold,
            )
            when (state.step) {
                ResetStep.REQUEST -> {
                    Text(
                        text = "The registered mobile number gets a one-time code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    AppTextField(
                        value = state.mobile,
                        onValueChange = viewModel::onMobile,
                        label = "017********",
                        caption = "Mobile Number",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = if (state.isBusy) "Sending OTP..." else "Send OTP",
                        onClick = viewModel::requestOtp,
                        isLoading = state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ResetStep.VERIFY -> {
                    Text(
                        text = "OTP sent to ${maskMobile(state.mobile)} — five tries, five minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    AppTextField(
                        value = state.otp,
                        onValueChange = viewModel::onOtp,
                        label = "Enter OTP",
                        caption = "OTP",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = if (state.isBusy) "Verifying..." else "Verify OTP",
                        onClick = viewModel::verifyOtp,
                        isLoading = state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinkButton(text = "Resend OTP", onClick = viewModel::requestOtp, enabled = !state.isBusy)
                    LinkButton(text = "Change Mobile", onClick = viewModel::changeMobile, enabled = !state.isBusy)
                }
                ResetStep.RESET -> {
                    AppTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        label = "Minimum 8 characters",
                        caption = "New Password",
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.passwordConfirm,
                        onValueChange = viewModel::onPasswordConfirm,
                        label = "Type it again",
                        caption = "Confirm Password",
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = if (state.isBusy) "Updating Password..." else "Update Password",
                        onClick = viewModel::resetPassword,
                        isLoading = state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LinkButton(text = "Back to sign in", onClick = onBackToLogin, enabled = !state.isBusy)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** All but the last four digits as *, as the web shows the number back. */
private fun maskMobile(mobile: String): String =
    if (mobile.length <= 4) mobile
    else "*".repeat(mobile.length - 4) + mobile.takeLast(4)
