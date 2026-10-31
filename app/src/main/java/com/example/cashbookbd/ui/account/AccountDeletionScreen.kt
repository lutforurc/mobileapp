package com.example.cashbookbd.ui.account

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.components.LinkButton
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The notice is shown in BOTH languages because the app has no per-user locale
// toggle: the same wording as the public web page, kept together so the two can
// never drift apart.
private const val NOTICE_EN =
    "Submitting a request does not immediately delete any data. Deletion will take place after the " +
        "14-day waiting period and completion of the review, including identity and ownership verification. " +
        "If the review takes longer, deletion will be delayed. You may cancel your request before deletion begins."

private const val NOTICE_BN =
    "আপনার অনুরোধ জমা দেওয়ার সঙ্গে সঙ্গে কোনো তথ্য মুছে যাবে না। অনুরোধ জমা দেওয়ার ১৪ দিন পর এবং " +
        "পরিচয় ও মালিকানা যাচাইসহ রিভিউ সম্পন্ন হলে ডিলেট করা হবে। রিভিউয়ে বেশি সময় লাগলে ডিলেট প্রক্রিয়াও " +
        "বিলম্বিত হবে। ডিলেট প্রক্রিয়া শুরু হওয়ার আগে অনুরোধ বাতিল করতে পারবেন।"

data class AccountDeletionRequestUi(
    val referenceNo: String,
    val requestType: String,
    val status: String,
    val createdAt: String?,
    val cancelledAt: String?,
    val earliestEligibleAt: String?,
    val waitingPeriodDays: Int,
)

data class AccountDeletionStatusUi(
    val fullName: String? = null,
    val email: String? = null,
    val mobile: String? = null,
    val companyName: String? = null,
    val canRequestCompanyDeletion: Boolean = false,
    val isOwner: Boolean = false,
    val isSoleOwner: Boolean = false,
    val waitingPeriodDays: Int = 14,
    val request: AccountDeletionRequestUi? = null,
)

data class AccountDeletionUiState(
    val isLoading: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val status: AccountDeletionStatusUi? = null,
    val requestType: String = "personal",
    val reason: String = "",
    val confirm: Boolean = false,
)

class AccountDeletionViewModel(
    private val api: ReportApiService,
    val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountDeletionUiState())
    val uiState: StateFlow<AccountDeletionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = api.get("account-deletion/status", emptyMap())
                    if (response.code() == 401) {
                        return@withContext Resource.Error(
                            "Your session has expired. Please log in again.", isUnauthorized = true,
                        )
                    }
                    val root = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    val success = root?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                    if (success != true) {
                        return@withContext Resource.Error(
                            root?.get("message")?.takeUnless { it.isJsonNull }?.asString
                                ?.ifBlank { null } ?: "Could not load your request.",
                        )
                    }
                    val data = root.getAsJsonObject("data")?.getAsJsonObject("data")
                        ?: return@withContext Resource.Error("Could not load your request.")
                    Resource.Success(data.toStatus())
                } catch (e: java.io.IOException) {
                    Resource.Error("No internet connection. Please check your network and try again.")
                } catch (e: Exception) {
                    Resource.Error("Something went wrong. Please try again.")
                }
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, status = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun setRequestType(type: String) = _uiState.update { it.copy(requestType = type) }
    fun setReason(reason: String) = _uiState.update { it.copy(reason = reason) }
    fun setConfirm(confirm: Boolean) = _uiState.update { it.copy(confirm = confirm) }

    fun submit() {
        val state = _uiState.value
        if (state.submitting || state.status == null) return
        if (!state.confirm) {
            _uiState.update { it.copy(message = "Please confirm the deletion request.") }
            return
        }

        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val body = buildMap {
                        put("request_type", state.requestType)
                        put("confirm", "true")
                        put("full_name", state.status.fullName ?: "")
                        state.reason.takeIf { it.isNotBlank() }?.let { put("reason", it) }
                    }
                    val response = api.post("account-deletion/submit", body)
                    if (response.code() == 401) {
                        return@withContext Resource.Error(
                            "Your session has expired. Please log in again.", isUnauthorized = true,
                        )
                    }
                    val root = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    val success = root?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                    if (success != true) {
                        return@withContext Resource.Error(
                            root?.get("error")?.asJsonObject?.get("message")?.takeUnless { it.isJsonNull }?.asString
                                ?: root?.get("message")?.takeUnless { it.isJsonNull }?.asString
                                ?: "Your request could not be submitted.",
                        )
                    }
                    Resource.Success(root.get("message")?.takeUnless { it.isJsonNull }?.asString ?: "Request received.")
                } catch (e: java.io.IOException) {
                    Resource.Error("No internet connection. Please check your network and try again.")
                } catch (e: Exception) {
                    Resource.Error("Something went wrong. Please try again.")
                }
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(submitting = false, reason = "", confirm = false, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(submitting = false, message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun cancel() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = api.post("account-deletion/cancel", emptyMap())
                    if (response.code() == 401) {
                        return@withContext Resource.Error(
                            "Your session has expired. Please log in again.", isUnauthorized = true,
                        )
                    }
                    val root = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    val success = root?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                    if (success != true) {
                        return@withContext Resource.Error(
                            root?.get("message")?.takeUnless { it.isJsonNull }?.asString
                                ?: "The request could not be cancelled.",
                        )
                    }
                    Resource.Success("Request cancelled.")
                } catch (e: java.io.IOException) {
                    Resource.Error("No internet connection. Please check your network and try again.")
                } catch (e: Exception) {
                    Resource.Error("Something went wrong. Please try again.")
                }
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(submitting = false, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(submitting = false, message = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AccountDeletionViewModel(
                    api = ServiceLocator.provideReportApiService(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}

private fun JsonObject.toStatus(): AccountDeletionStatusUi {
    val req = get("request")?.takeIf { it.isJsonObject }?.asJsonObject
    return AccountDeletionStatusUi(
        fullName = get("full_name")?.takeUnless { it.isJsonNull }?.asString,
        email = get("email")?.takeUnless { it.isJsonNull }?.asString,
        mobile = get("mobile")?.takeUnless { it.isJsonNull }?.asString,
        companyName = get("company_name")?.takeUnless { it.isJsonNull }?.asString,
        canRequestCompanyDeletion = get("can_request_company_deletion")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
        isOwner = get("is_owner")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
        isSoleOwner = get("is_sole_owner")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
        waitingPeriodDays = get("waiting_period_days")?.takeUnless { it.isJsonNull }?.asInt ?: 14,
        request = req?.let {
            AccountDeletionRequestUi(
                referenceNo = it.get("reference_no")?.asString.orEmpty(),
                requestType = it.get("request_type")?.asString.orEmpty(),
                status = it.get("status")?.asString.orEmpty(),
                createdAt = it.get("created_at")?.takeUnless { v -> v.isJsonNull }?.asString,
                cancelledAt = it.get("cancelled_at")?.takeUnless { v -> v.isJsonNull }?.asString,
                earliestEligibleAt = it.get("earliest_eligible_at")?.takeUnless { v -> v.isJsonNull }?.asString,
                waitingPeriodDays = it.get("waiting_period_days")?.takeUnless { v -> v.isJsonNull }?.asInt ?: 14,
            )
        },
    )
}

@Composable
fun AccountDeletionScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountDeletionViewModel = viewModel(
        factory = AccountDeletionViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    AuthenticatedShell(
        title = "Request Account Deletion",
        currentRoute = Routes.PROFILE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isLoading && state.status == null) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            NoticeCard()

            state.status?.request?.let { request ->
                RequestStatusCard(request, submitting = state.submitting, onCancel = viewModel::cancel)
            }

            RequestFormCard(
                status = state.status,
                requestType = state.requestType,
                reason = state.reason,
                confirm = state.confirm,
                submitting = state.submitting,
                onRequestType = viewModel::setRequestType,
                onReason = viewModel::setReason,
                onConfirm = viewModel::setConfirm,
                onSubmit = viewModel::submit,
            )

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun NoticeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Notice",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = NOTICE_EN, style = MaterialTheme.typography.bodySmall)
            Text(
                text = NOTICE_BN,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RequestStatusCard(
    request: AccountDeletionRequestUi,
    submitting: Boolean,
    onCancel: () -> Unit,
) {
    val open = request.status in setOf("submitted", "verification_pending", "under_review", "awaiting_final_confirmation")

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Your request",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Reference: ${request.referenceNo}", style = MaterialTheme.typography.bodySmall)
            Text("Type: ${request.requestType}", style = MaterialTheme.typography.bodySmall)
            Text("Status: ${request.status.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            request.createdAt?.let { Text("Submitted: $it", style = MaterialTheme.typography.bodySmall) }
            request.earliestEligibleAt?.let {
                Text(
                    "Earliest eligible deletion date: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "This is the earliest possible date, not a guaranteed deletion date.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                LinkButton(
                    text = if (submitting) "Cancelling…" else "Cancel request",
                    onClick = onCancel,
                    enabled = !submitting,
                )
            }
        }
    }
}

@Composable
private fun RequestFormCard(
    status: AccountDeletionStatusUi?,
    requestType: String,
    reason: String,
    confirm: Boolean,
    submitting: Boolean,
    onRequestType: (String) -> Unit,
    onReason: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val hasOpenRequest = status?.request?.status in setOf(
        "submitted", "verification_pending", "under_review", "awaiting_final_confirmation",
    )

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (hasOpenRequest) "New request" else "Request account deletion",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (status?.fullName != null) {
                Text(
                    "Signed in as ${status.fullName}" + (status.companyName?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = requestType == "personal", onClick = { onRequestType("personal") })
                Text("Delete my personal account", Modifier.padding(start = 4.dp))
            }
            if (status?.canRequestCompanyDeletion == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = requestType == "company", onClick = { onRequestType("company") })
                    Text("Delete my company and all its data", Modifier.padding(start = 4.dp))
                }
            } else if (status?.isOwner == true) {
                Text(
                    "Company-wide deletion is available only to the verified owner. Transfer ownership first, or ask support.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = reason,
                onValueChange = onReason,
                label = { Text("Reason (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirm, onCheckedChange = onConfirm)
                Text(
                    "I confirm this request. Submitting does not delete anything yet.",
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LinkButton(
                text = if (submitting) "Submitting…" else "Submit request",
                onClick = onSubmit,
                enabled = !submitting,
            )
        }
    }
}
