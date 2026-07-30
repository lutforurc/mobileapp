package com.example.cashbookbd.ui.subscription

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.PaymentRequest
import com.example.cashbookbd.data.repository.SubscriptionAdminRepository
import com.example.cashbookbd.data.repository.SubscriptionOverview
import com.example.cashbookbd.data.repository.TenantSubscriptionRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** The payment-status filter above the request list; "" is All. */
private val StatusFilters = listOf(
    SelectorOption("", "All"),
    SelectorOption("pending", "Pending"),
    SelectorOption("approved", "Approved"),
    SelectorOption("rejected", "Rejected"),
)

/** A confirm-pending approve/reject on one payment request. */
data class PendingDecision(
    val payment: PaymentRequest,
    val approve: Boolean,
)

data class SubscriptionAdminUiState(
    val isLoading: Boolean = true,
    /** Fatal (403 from the platform.admin middleware, initial load failure). */
    val error: String? = null,
    val overview: SubscriptionOverview? = null,
    val payments: List<PaymentRequest> = emptyList(),
    val isLoadingPayments: Boolean = false,
    val tenants: List<TenantSubscriptionRow> = emptyList(),
    /** "" = All; otherwise sent as `payment_status`. */
    val statusFilter: String = "",
    val pendingDecision: PendingDecision? = null,
    val adminNote: String = "",
    val isDeciding: Boolean = false,
    /** Snackbar confirmations / transient failures. */
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class SubscriptionAdminViewModel(
    private val repository: SubscriptionAdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionAdminUiState())
    val uiState: StateFlow<SubscriptionAdminUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** The full initial load. A failure here (e.g. the 403) is fatal for the screen. */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val overview = repository.getOverview()
            if (overview is Resource.Error) {
                fatal(overview)
                return@launch
            }
            val payments = repository.getPaymentRequests(_uiState.value.statusFilter.ifBlank { null })
            if (payments is Resource.Error) {
                fatal(payments)
                return@launch
            }
            val tenants = repository.getTenantSubscriptions()
            if (tenants is Resource.Error) {
                fatal(tenants)
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = (overview as Resource.Success).data,
                    payments = (payments as Resource.Success).data,
                    tenants = (tenants as Resource.Success).data,
                )
            }
        }
    }

    private fun fatal(result: Resource.Error) = _uiState.update {
        it.copy(
            isLoading = false,
            error = result.message,
            sessionExpired = it.sessionExpired || result.isUnauthorized,
        )
    }

    fun onStatusFilter(value: String) {
        _uiState.update { it.copy(statusFilter = value) }
        refreshPayments()
    }

    // One in-flight list fetch at a time — a slow "All" response must not
    // overwrite a newer status-filtered one.
    private var paymentsJob: kotlinx.coroutines.Job? = null

    /** Refetches the request list (and counters) without blanking the screen. */
    private fun refreshPayments(alsoOverview: Boolean = false) {
        _uiState.update { it.copy(isLoadingPayments = true) }
        paymentsJob?.cancel()
        paymentsJob = viewModelScope.launch {
            when (val payments = repository.getPaymentRequests(_uiState.value.statusFilter.ifBlank { null })) {
                is Resource.Success -> _uiState.update { it.copy(isLoadingPayments = false, payments = payments.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingPayments = false,
                        message = payments.message,
                        sessionExpired = it.sessionExpired || payments.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
            if (alsoOverview) {
                (repository.getOverview() as? Resource.Success)?.let { overview ->
                    _uiState.update { it.copy(overview = overview.data) }
                }
                (repository.getTenantSubscriptions() as? Resource.Success)?.let { tenants ->
                    _uiState.update { it.copy(tenants = tenants.data) }
                }
            }
        }
    }

    fun requestDecision(payment: PaymentRequest, approve: Boolean) =
        _uiState.update { it.copy(pendingDecision = PendingDecision(payment, approve), adminNote = "") }

    fun onAdminNote(value: String) = _uiState.update { it.copy(adminNote = value) }

    fun dismissDecision() {
        if (_uiState.value.isDeciding) return
        _uiState.update { it.copy(pendingDecision = null, adminNote = "") }
    }

    /** Confirms the dialog: posts the approve/reject, then refreshes list + counters. */
    fun confirmDecision() {
        val decision = _uiState.value.pendingDecision ?: return
        if (_uiState.value.isDeciding) return
        _uiState.update { it.copy(isDeciding = true) }
        viewModelScope.launch {
            val note = _uiState.value.adminNote.takeIf { it.isNotBlank() }
            val result = if (decision.approve) {
                repository.approvePayment(decision.payment.id, note)
            } else {
                repository.rejectPayment(decision.payment.id, note)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isDeciding = false, pendingDecision = null, adminNote = "", message = result.data)
                    }
                    refreshPayments(alsoOverview = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeciding = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
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
                SubscriptionAdminViewModel(
                    repository = ServiceLocator.provideSubscriptionAdminRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * The platform operator's subscription console — the web's /subscription/admin.
 * Overview counters, the manual-payment requests (approve/reject with a confirm
 * dialog and optional admin note), and every tenant's subscription. The server
 * additionally restricts these endpoints to the platform company; its 403 is
 * shown as a plain message. The web's Assign Subscription form is not ported yet.
 */
@Composable
fun SubscriptionAdminScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionAdminViewModel = viewModel(
        factory = SubscriptionAdminViewModel.provideFactory(LocalContext.current),
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
        title = "Subscription Admin",
        currentRoute = Routes.SUBSCRIPTION,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::load)
                    }
                }

                else -> AdminContent(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.pendingDecision?.let { decision ->
        DecisionDialog(
            decision = decision,
            adminNote = state.adminNote,
            isDeciding = state.isDeciding,
            onNote = viewModel::onAdminNote,
            onConfirm = viewModel::confirmDecision,
            onDismiss = viewModel::dismissDecision,
        )
    }
}

@Composable
private fun AdminContent(state: SubscriptionAdminUiState, viewModel: SubscriptionAdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.overview?.let { overview ->
            item(key = "overview") { OverviewTiles(overview) }
        }

        item(key = "paymentsHeader") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Payment Requests")
                AppSelectDropdown(
                    label = "Status",
                    options = StatusFilters,
                    selected = StatusFilters.firstOrNull { it.id == state.statusFilter },
                    onSelected = { viewModel.onStatusFilter(it.id) },
                )
            }
        }

        if (state.isLoadingPayments) {
            item(key = "paymentsLoading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else if (state.payments.isEmpty()) {
            item(key = "paymentsEmpty") {
                Text(
                    text = "No payment requests found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(state.payments, key = { "payment-${it.id}" }) { payment ->
                PaymentRequestCard(
                    payment = payment,
                    onApprove = { viewModel.requestDecision(payment, approve = true) },
                    onReject = { viewModel.requestDecision(payment, approve = false) },
                )
            }
        }

        item(key = "tenantsHeader") { SectionTitle("Tenant Subscriptions") }
        item(key = "tenantsTable") { TenantTable(state.tenants) }

        item(key = "assignNote") {
            Text(
                text = "Assigning a subscription directly to a company is only available from the web for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 6.dp),
)

/** The four headline counters, as a 2×2 grid of [SummaryTile]s. */
@Composable
private fun OverviewTiles(overview: SubscriptionOverview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterTile("Pending Payments", overview.pendingPayments, Modifier.weight(1f))
            CounterTile("Active", overview.activeSubscriptions, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterTile("Expired", overview.expiredSubscriptions, Modifier.weight(1f))
            CounterTile("On Trial", overview.trialSubscriptions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CounterTile(label: String, count: Int, modifier: Modifier = Modifier) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PaymentRequestCard(
    payment: PaymentRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = payment.companyName.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(payment.paymentStatus)
            }

            Text(
                text = listOf(
                    payment.planName.ifBlank { "—" },
                    payment.paymentMethod.prettyEnum(),
                    payment.transactionId.ifBlank { "—" },
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DetailRow("Amount", "${payment.currency} ${AmountFormat.format(payment.amount)}".trim())
            DetailRow("Months", payment.billingMonths.takeIf { it > 0 }?.toString() ?: "-")
            DetailRow("Sender", payment.senderNumber.ifBlank { "-" })
            DetailRow("Date", payment.createdAt.take(10).ifBlank { "-" })

            // Approving grants the tenant full access; only a pending request
            // still carries the decision buttons, like the web console.
            if (payment.paymentStatus.equals("pending", ignoreCase = true)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Approve",
                        onClick = onApprove,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Reject",
                        onClick = onReject,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** pending / approved / rejected as a small tinted pill. */
@Composable
private fun StatusChip(status: String) {
    val (container, content) = when (status.lowercase(Locale.US)) {
        "approved" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "rejected" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.prettyEnum().ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/** Company | Plan | Status | Access | Ends — the simple table under the requests. */
@Composable
private fun TenantTable(tenants: List<TenantSubscriptionRow>) {
    if (tenants.isEmpty()) {
        Text(
            text = "No tenant subscriptions found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        return
    }
    val gridLine = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            HeaderCell("Company", 1.4f)
            HeaderCell("Plan", 1f)
            HeaderCell("Status", 0.9f)
            HeaderCell("Access", 0.9f)
            HeaderCell("Ends", 1f)
        }
        HorizontalDivider(color = gridLine)
        tenants.forEach { tenant ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                BodyCell(tenant.companyName.ifBlank { "-" }, 1.4f)
                BodyCell(tenant.planName.ifBlank { "-" }, 1f)
                BodyCell(tenant.status.prettyEnum().ifBlank { "-" }, 0.9f)
                BodyCell(tenant.accessStatus.prettyEnum().ifBlank { "-" }, 0.9f)
                BodyCell(tenant.endDate.ifBlank { "-" }, 1f)
            }
            HorizontalDivider(color = gridLine)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) = Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.weight(weight),
)

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(text: String, weight: Float) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.weight(weight),
)

/**
 * The approve/reject confirmation. These decisions grant or deny a tenant's
 * real access, so neither button fires without passing through here.
 */
@Composable
private fun DecisionDialog(
    decision: PendingDecision,
    adminNote: String,
    isDeciding: Boolean,
    onNote: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionLabel = if (decision.approve) "Approve" else "Reject"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$actionLabel payment?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = buildString {
                        append("${decision.payment.companyName.ifBlank { "This company" }} — ")
                        append("${decision.payment.currency} ${AmountFormat.format(decision.payment.amount)}".trim())
                        append(
                            if (decision.approve) {
                                ". Approving activates the subscription and grants full access."
                            } else {
                                ". Rejecting keeps the company on billing-only access."
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppTextField(
                    value = adminNote,
                    onValueChange = onNote,
                    label = "Optional note to the tenant",
                    caption = "Admin Note",
                    multiline = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = actionLabel,
                onClick = onConfirm,
                isLoading = isDeciding,
                compact = true,
            )
        },
        dismissButton = {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                enabled = !isDeciding,
                compact = true,
            )
        },
    )
}

/** "pending_payment" → "Pending Payment". */
private fun String.prettyEnum(): String = split('_', ' ').joinToString(" ") { word ->
    word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
}
