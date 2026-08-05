package com.example.cashbookbd.ui.login

import com.example.cashbookbd.ui.theme.faint
import com.example.cashbookbd.ui.theme.asTint
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.components.appTextFieldColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.example.cashbookbd.data.repository.DeviceLimitBlock
import com.example.cashbookbd.data.repository.LoginDevice
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.theme.accents
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Navigate away once, when login succeeds.
    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) onLoginSuccess()
    }

    // Surface errors in a snackbar.
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.onErrorShown()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app-wide background is the brand teal; this screen's teal links
        // and outlined fields sit directly on it, so it keeps the neutral
        // surface instead.
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sign in to continue to CashBook",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            // Identifier: email / phone / username
            OutlinedTextField(
            colors = appTextFieldColors(),
                value = uiState.identifier,
                onValueChange = viewModel::onIdentifierChange,
                label = { Text("Email, phone or username") },
                singleLine = true,
                enabled = !uiState.isLoading,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Password
            OutlinedTextField(
            colors = appTextFieldColors(),
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                enabled = !uiState.isLoading,
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    LinkButton(
                        text = if (uiState.isPasswordVisible) "Hide" else "Show",
                        onClick = viewModel::togglePasswordVisibility,
                    )
                },
                visualTransformation = if (uiState.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        viewModel.login()
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Remember me: when off, the session is dropped on the next cold start.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isLoading) {
                        viewModel.onRememberMeChange(!uiState.rememberMe)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = uiState.rememberMe,
                    onCheckedChange = { viewModel.onRememberMeChange(it) },
                    enabled = !uiState.isLoading,
                )
                Text(
                    text = "Remember me",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Plan device limit reached: sign a device out here, then retry — a
            // port of the web sign-in's DeviceLimitNotice.
            uiState.deviceLimit?.let { block ->
                Spacer(Modifier.height(16.dp))
                DeviceLimitPanel(
                    block = block,
                    releasingId = uiState.releasingDeviceId,
                    error = uiState.deviceLimitError,
                    onSignOut = viewModel::releaseDevice,
                    onDismiss = viewModel::dismissDeviceLimit,
                )
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = "Log in",
                onClick = {
                    keyboardController?.hide()
                    viewModel.login()
                },
                enabled = uiState.isSubmitEnabled,
                isLoading = uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Password reset by OTP, like the web sign-in's link.
            LinkButton(
                text = "Forgot password?",
                onClick = onForgotPasswordClick,
                enabled = !uiState.isLoading,
            )

            // Mirrors the web sign-in's "Register your company" affordance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New here?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkButton(
                    text = "Register your company",
                    onClick = onRegisterClick,
                    enabled = !uiState.isLoading,
                )
            }
        }
    }
}

@Composable
private fun DeviceLimitPanel(
    block: DeviceLimitBlock,
    releasingId: Long?,
    error: String?,
    onSignOut: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val amber = MaterialTheme.accents.amber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape)
            .border(1.dp, amber.faint(), AppShape)
            .background(amber.asTint())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "Device limit reached",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val limitSuffix = block.deviceLimit?.let {
            " Your plan allows $it device${if (it == 1) "" else "s"} per user."
        }.orEmpty()
        Text(
            text = block.message + limitSuffix,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        block.devices.forEach { device ->
            DeviceRow(
                device = device,
                releasing = releasingId == device.id,
                enabled = releasingId == null,
                onSignOut = { onSignOut(device.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = "Sign out of one device to continue, or upgrade your plan for more devices.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceRow(
    device: LoginDevice,
    releasing: Boolean,
    enabled: Boolean,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = deviceSubtitle(device),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (releasing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            LinkButton(
                text = "Sign out",
                onClick = onSignOut,
                enabled = enabled,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun deviceSubtitle(device: LoginDevice): String {
    val prefix = device.ip?.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
    val used = device.lastUsed?.takeIf { it.isNotBlank() } ?: "Never used"
    return "${prefix}Last used $used"
}
