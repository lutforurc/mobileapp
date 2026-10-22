package com.example.cashbookbd.ui.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- Switch Branch ---------------------------------------------------------------

data class SwitchBranchUiState(
    val loading: Boolean = true,
    val branches: List<SelectorOption> = emptyList(),
    val currentId: String = "",
    val currentName: String = "",
    val selected: SelectorOption? = null,
    val isSaving: Boolean = false,
    val formError: String? = null,
    /** Switched: the shell reloads everything and lands on the dashboard. */
    val switched: String? = null,
    val sessionExpired: Boolean = false,
) {
    val alreadyHere: Boolean get() = selected != null && selected.id == currentId
}

/**
 * Move this login to another branch (SwitchBranch.tsx). Personal: no
 * permission stands in front of it. The list is `branch/ddl/all-branch`, the
 * access rule the rest of the app applies, and the API checks the choice
 * again. The web ends in a full reload because every branch-scoped figure on
 * screen belongs to the branch just left; here the session is read again and
 * the back stack is cleared to the dashboard.
 */
class SwitchBranchViewModel(
    private val api: WebApi,
    private val session: SessionRepository,
    private val appContext: Context,
    currentId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SwitchBranchUiState(currentId = currentId))
    val uiState: StateFlow<SwitchBranchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = api.get("branch/ddl/all-branch")) {
                is Resource.Success -> {
                    val list = r.data.jRows().map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            branches = list,
                            currentName = list.firstOrNull { b -> b.id == it.currentId }?.label.orEmpty(),
                            selected = list.firstOrNull { b -> b.id == it.currentId },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(loading = false, formError = "Could not load the branch list.", sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSelect(o: SelectorOption) = _uiState.update { it.copy(selected = o, formError = null) }

    fun submit() {
        val s = _uiState.value
        val target = s.selected ?: return
        if (s.isSaving || s.alreadyHere) return
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val body = JsonObject().apply { addProperty("branch_id", target.id.toLongOrNull() ?: 0L) }
            when (val r = api.post("user/switch-branch", body)) {
                is Resource.Success -> {
                    ServiceLocator.clearDashboardCache(appContext)
                    session.refresh()
                    _uiState.update { it.copy(isSaving = false, switched = r.data.jStr("message").ifBlank { "Working branch changed." }) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, formError = r.message.ifBlank { "Could not switch branch." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                SwitchBranchViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    session = ServiceLocator.provideSessionRepository(app),
                    appContext = app,
                    currentId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId?.toString().orEmpty(),
                )
            }
        }
    }
}

@Composable
fun SwitchBranchScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: SwitchBranchViewModel = viewModel(factory = SwitchBranchViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    LaunchedEffect(state.switched) {
        if (state.switched == null) return@LaunchedEffect
        navController.navigate(Routes.HOME) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    AuthenticatedShell(title = "Switch Branch", currentRoute = Routes.PROFILE, navController = navController, onLogout = onLogout) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Working in: ${state.currentName.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.appColors.textOnScreenMuted)
            state.formError?.let { FormErrorBox(it) }
            if (state.loading) {
                ReportLoading()
            } else {
                AppSelectDropdown("Branch", state.branches, state.selected, viewModel::onSelect)
            }
            if (state.alreadyHere) {
                Text("This is already your working branch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted)
            }
            if (!state.loading && state.branches.size <= 1) {
                Text(
                    "You have access to one branch, so there is nothing to switch to.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            PrimaryButton(
                text = "Switch Branch",
                onClick = viewModel::submit,
                isLoading = state.isSaving,
                enabled = !state.loading && !state.isSaving && !state.alreadyHere && state.selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FormErrorBox(text: String) {
    val danger = MaterialTheme.appColors.danger
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(danger.copy(alpha = 0.10f), MaterialTheme.shapes.small).padding(12.dp),
        color = danger,
        style = MaterialTheme.typography.bodySmall,
    )
}

// ---- Change Password ---------------------------------------------------------------

private const val PASSWORD_MIN_LENGTH = 8

/** 0–4 from what the password is made of: a nudge, not a gate. */
private fun strengthOf(value: String): Int {
    if (value.isEmpty()) return 0
    var score = 0
    if (value.length >= PASSWORD_MIN_LENGTH) score++
    if (value.length >= 12) score++
    if (value.any { it.isLowerCase() } && value.any { it.isUpperCase() }) score++
    if (value.any { it.isDigit() }) score++
    if (value.any { !it.isLetterOrDigit() }) score++
    return minOf(score, 4)
}

data class ChangePasswordUiState(
    val current: String = "",
    val password: String = "",
    val confirmation: String = "",
    val isSaving: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val lengthOk: Boolean get() = password.length >= PASSWORD_MIN_LENGTH
    val differentOk: Boolean get() = password.isNotEmpty() && password != current
    val matchOk: Boolean get() = password.isNotEmpty() && password == confirmation
    val canSubmit: Boolean get() = current.isNotEmpty() && lengthOk && differentOk && matchOk
}

/**
 * The signed-in user's own password (ChangePassword.tsx). The current one is
 * checked by the API before the new one is written; every other device is
 * signed out, this one stays.
 */
class ChangePasswordViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrent(v: String) = _uiState.update { it.copy(current = v, formError = null) }
    fun onPassword(v: String) = _uiState.update { it.copy(password = v, formError = null) }
    fun onConfirmation(v: String) = _uiState.update { it.copy(confirmation = v, formError = null) }

    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit || s.isSaving) return
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("current_password", s.current)
                addProperty("password", s.password)
                addProperty("password_confirmation", s.confirmation)
            }
            when (val r = api.post("user/change-password", body)) {
                is Resource.Success -> _uiState.update {
                    ChangePasswordUiState(message = r.data.jStr("message").ifBlank { "Password changed successfully." })
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, formError = r.message.ifBlank { "Password change failed." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { ChangePasswordViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

@Composable
fun ChangePasswordScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: ChangePasswordViewModel = viewModel(factory = ChangePasswordViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val strength = strengthOf(state.password)
    val strengthLabel = listOf("Too short", "Weak", "Fair", "Good", "Strong")[strength]
    val strengthColor = when (strength) {
        0, 1 -> MaterialTheme.appColors.danger
        2 -> MaterialTheme.appColors.warning
        3 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.appColors.success
    }

    AuthenticatedShell(title = "Change Password", currentRoute = Routes.PROFILE, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Confirm who you are with your current password, then choose a new one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted,
                )
                state.formError?.let { FormErrorBox(it) }
                PasswordBox(state.current, viewModel::onCurrent, "Current Password", "Enter your current password")
                Text("NEW PASSWORD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
                PasswordBox(state.password, viewModel::onPassword, "New Password", "At least $PASSWORD_MIN_LENGTH characters")
                // Four segments that fill as the password gets harder to guess.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..4).forEach { step ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    if (state.password.isNotEmpty() && step <= maxOf(strength, 1)) strengthColor else MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.extraSmall,
                                ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.password.isNotEmpty()) strengthLabel else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.password.isNotEmpty()) strengthColor else Color.Transparent,
                    )
                }
                PasswordBox(state.confirmation, viewModel::onConfirmation, "Confirm New Password", "Enter the new password again")

                Text("Requirements", fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen)
                // Grey until there is something to judge, then green or red.
                Requirement(state.lengthOk, state.password.isNotEmpty(), "At least $PASSWORD_MIN_LENGTH characters")
                Requirement(state.differentOk, state.password.isNotEmpty(), "Different from your current password")
                Requirement(state.matchOk, state.confirmation.isNotEmpty(), "Both new password fields match")

                PrimaryButton(
                    text = "Update Password",
                    onClick = viewModel::submit,
                    isLoading = state.isSaving,
                    enabled = state.canSubmit && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Your other signed-in devices will be signed out.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
                Text(
                    "Longer is stronger. Mix letters, numbers and symbols, and avoid your name, phone number or anything you use elsewhere. " +
                        "Forgotten your current password? Sign out and use Forgot Password on the login screen.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PasswordBox(value: String, onChange: (String) -> Unit, caption: String, placeholder: String) {
    var shown by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = onChange,
        label = placeholder,
        caption = caption,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        leadingIcon = Icons.Filled.Lock,
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Text(if (shown) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Requirement(ok: Boolean, judged: Boolean, text: String) {
    val tone = when {
        ok -> MaterialTheme.appColors.success
        judged -> MaterialTheme.appColors.danger
        else -> MaterialTheme.appColors.textOnScreenMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (ok || !judged) Icons.Filled.CheckCircle else Icons.Filled.Close, contentDescription = null, tint = tone)
        Text(text, style = MaterialTheme.typography.bodySmall, color = tone)
    }
}
