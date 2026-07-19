package com.example.cashbookbd.ui.register

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/**
 * Public company sign-up. One screen, two steps: the registration form, then the
 * OTP entry. On successful verification the token is already stored, so
 * [onRegistered] enters the app (dashboard) just like a fresh login;
 * [onBackToLogin] returns to the sign-in screen.
 */
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel = viewModel(
        factory = RegistrationViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onRegistered()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onInfoShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            when (state.step) {
                RegisterStep.FORM -> RegistrationForm(
                    state = state,
                    viewModel = viewModel,
                    onBackToLogin = onBackToLogin,
                )

                RegisterStep.OTP -> OtpStep(
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun RegistrationForm(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
    onBackToLogin: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Text("Register your company", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Create an account to start using CashBook.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Field(
        label = "Company Name",
        value = state.companyName,
        onChange = viewModel::onCompanyName,
        enabled = !state.isSubmitting,
        placeholder = "ABC Traders Ltd",
    )
    Field(
        label = "User Name",
        value = state.userName,
        onChange = viewModel::onUserName,
        enabled = !state.isSubmitting,
        placeholder = "John Doe",
    )
    Field(
        label = "Contact Person",
        value = state.contactPerson,
        onChange = viewModel::onContactPerson,
        enabled = !state.isSubmitting,
        placeholder = "John Doe",
    )
    Field(
        label = "Mobile",
        value = state.mobile,
        onChange = viewModel::onMobile,
        enabled = !state.isSubmitting,
        keyboardType = KeyboardType.Phone,
        placeholder = "0171x-xxxxxx",
    )
    Field(
        label = "Email",
        value = state.email,
        onChange = viewModel::onEmail,
        enabled = !state.isSubmitting,
        keyboardType = KeyboardType.Email,
        placeholder = "abc@example.com",
    )
    Field(
        label = "Address",
        value = state.address,
        onChange = viewModel::onAddress,
        enabled = !state.isSubmitting,
        placeholder = "H # 123, Road # 45, Gulshan, Dhaka.",
    )
    PasswordField(
        label = "Password (min 8 characters)",
        value = state.password,
        onChange = viewModel::onPassword,
        visible = state.isPasswordVisible,
        onToggle = viewModel::togglePasswordVisibility,
        enabled = !state.isSubmitting,
        error = if (state.passwordTooShort) "At least 8 characters." else null,
    )
    PasswordField(
        label = "Confirm Password",
        value = state.confirmPassword,
        onChange = viewModel::onConfirmPassword,
        visible = state.isConfirmPasswordVisible,
        onToggle = viewModel::toggleConfirmPasswordVisibility,
        enabled = !state.isSubmitting,
        error = if (state.passwordMismatch) "Passwords do not match." else null,
        imeAction = ImeAction.Done,
    )

    Spacer(Modifier.height(20.dp))

    PrimaryButton(
        text = "Request OTP",
        onClick = {
            keyboard?.hide()
            viewModel.requestOtp()
        },
        enabled = state.canRequestOtp,
        isLoading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Already have an account?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinkButton(text = "Sign In", onClick = onBackToLogin, enabled = !state.isSubmitting)
    }
}

@Composable
private fun OtpStep(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Text("Verify OTP", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = "OTP sent to ${maskMobile(state.mobile)}. Enter it to complete registration.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    Text(
        text = "OTP Code",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OtpInput(
        value = state.otp,
        onValueChange = viewModel::onOtp,
        enabled = !state.isSubmitting,
        onFilled = {
            keyboard?.hide()
            viewModel.verifyOtp()
        },
    )

    Spacer(Modifier.height(20.dp))

    PrimaryButton(
        text = "Verify OTP",
        onClick = {
            keyboard?.hide()
            viewModel.verifyOtp()
        },
        icon = Icons.AutoMirrored.Filled.Send,
        enabled = state.canVerify,
        isLoading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(10.dp))

    SecondaryButton(
        text = "Resend OTP",
        onClick = {
            keyboard?.hide()
            viewModel.resendOtp()
        },
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        LinkButton(
            text = "Wrong information? Back to registration",
            onClick = viewModel::backToForm,
            enabled = !state.isSubmitting,
        )
    }
}

/** The web's `*******1636`: everything but the last four digits masked. */
private fun maskMobile(mobile: String): String {
    val trimmed = mobile.trim()
    if (trimmed.length <= 4) return trimmed
    return "*".repeat(trimmed.length - 4) + trimmed.takeLast(4)
}

private const val OTP_LENGTH = 6

/**
 * Six boxes backed by one hidden field, matching the web. The real
 * [BasicTextField] captures the keyboard; the boxes are drawn in its
 * decoration, each showing its digit, with the next-to-fill box highlighted.
 * Fires [onFilled] once all six digits are entered.
 */
@Composable
private fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onFilled: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = { new ->
            val digits = new.filter(Char::isDigit).take(OTP_LENGTH)
            onValueChange(digits)
            if (digits.length == OTP_LENGTH) onFilled()
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        // The field itself is never shown — the decoration is the whole UI.
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(OTP_LENGTH) { index ->
                    val digit = value.getOrNull(index)?.toString() ?: ""
                    val active = index == value.length
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 54.dp)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        // "Show"/"Hide" text, the same password toggle the login screen uses, so
        // the affordance is identical across the app.
        trailingIcon = { LinkButton(text = if (visible) "Hide" else "Show", onClick = onToggle) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
}
