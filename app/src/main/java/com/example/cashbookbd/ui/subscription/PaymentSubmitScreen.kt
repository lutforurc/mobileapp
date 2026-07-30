package com.example.cashbookbd.ui.subscription

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.SubscriptionAdminRepository
import com.example.cashbookbd.data.repository.SubscriptionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.subscription.model.SubscriptionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Server limits (`subscription/manual-payment` validation). */
private const val MIN_MONTHS = 1
private const val MAX_MONTHS = 24
private const val TRANSACTION_ID_MAX = 120
private const val SENDER_NUMBER_MAX = 30
private const val RECEIVER_ACCOUNT_MAX = 60

/** The selectable payment methods (`in:bkash,nagad,bank,cash`); bKash is the default. */
private val PaymentMethods = listOf(
    SelectorOption("bkash", "bKash"),
    SelectorOption("nagad", "Nagad"),
    SelectorOption("bank", "Bank Transfer"),
    SelectorOption("cash", "Cash"),
)

data class PaymentSubmitUiState(
    val plans: List<SubscriptionPlan> = emptyList(),
    val isLoadingPlans: Boolean = false,
    val plansError: String? = null,
    val selectedPlanId: String? = null,
    val billingMonths: String = "1",
    val paymentMethod: String = "bkash",
    /** yyyy-MM-dd, defaulted to today. */
    val paidAt: String = "",
    val transactionId: String = "",
    val senderNumber: String = "",
    val receiverAccount: String = "",
    val customerNote: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submittedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedPlan: SubscriptionPlan? get() = plans.firstOrNull { it.id == selectedPlanId }

    /** Months as validated by the server: an integer in 1..24. */
    val months: Int? get() = billingMonths.toIntOrNull()?.takeIf { it in MIN_MONTHS..MAX_MONTHS }

    /** The amount is derived, never typed: plan price × billing months. */
    val amount: Double get() = (selectedPlan?.price ?: 0.0) * (months ?: 0)

    val amountText: String
        get() {
            val plan = selectedPlan ?: return ""
            if (months == null) return ""
            return "${plan.currency} ${AmountFormat.format(amount)}".trim()
        }

    val canSubmit: Boolean
        get() = !isSubmitting && submittedMessage == null && selectedPlan != null &&
            months != null && paidAt.isNotBlank() && transactionId.isNotBlank() &&
            senderNumber.isNotBlank()
    // submittedMessage == null closes the double-submit window: the button
    // stays dead while the success snackbar shows and the screen navigates
    // away — a second tap there would post a second REAL payment.
}

class PaymentSubmitViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val adminRepository: SubscriptionAdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PaymentSubmitUiState(
            paidAt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        ),
    )
    val uiState: StateFlow<PaymentSubmitUiState> = _uiState.asStateFlow()

    init {
        loadPlans()
    }

    fun loadPlans() {
        _uiState.update { it.copy(isLoadingPlans = true, plansError = null) }
        viewModelScope.launch {
            when (val result = subscriptionRepository.getPlans()) {
                is Resource.Success -> _uiState.update { it.copy(isLoadingPlans = false, plans = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingPlans = false,
                        plansError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onPlan(planId: String) = _uiState.update { it.copy(selectedPlanId = planId) }

    fun onBillingMonths(value: String) =
        _uiState.update { it.copy(billingMonths = value.filter { c -> c.isDigit() }.take(2)) }

    fun onPaymentMethod(value: String) = _uiState.update { it.copy(paymentMethod = value) }
    fun onPaidAt(value: String) = _uiState.update { it.copy(paidAt = value) }

    fun onTransactionId(value: String) =
        _uiState.update { it.copy(transactionId = value.take(TRANSACTION_ID_MAX)) }

    fun onSenderNumber(value: String) =
        _uiState.update { it.copy(senderNumber = value.take(SENDER_NUMBER_MAX)) }

    fun onReceiverAccount(value: String) =
        _uiState.update { it.copy(receiverAccount = value.take(RECEIVER_ACCOUNT_MAX)) }

    fun onCustomerNote(value: String) = _uiState.update { it.copy(customerNote = value) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val planId = state.selectedPlan?.id?.toIntOrNull()
        val months = state.months
        if (planId == null || months == null) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = adminRepository.submitManualPayment(
                planId = planId,
                amount = state.amount,
                paymentMethod = state.paymentMethod,
                billingMonths = months,
                paidAt = state.paidAt,
                transactionId = state.transactionId,
                senderNumber = state.senderNumber,
                receiverAccount = state.receiverAccount.takeIf { it.isNotBlank() },
                customerNote = state.customerNote.takeIf { it.isNotBlank() },
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isSubmitting = false, submittedMessage = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSubmittedHandled() = _uiState.update { it.copy(submittedMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                PaymentSubmitViewModel(
                    subscriptionRepository = ServiceLocator.provideSubscriptionRepository(appContext),
                    adminRepository = ServiceLocator.provideSubscriptionAdminRepository(appContext),
                )
            }
        }
    }
}

/**
 * Manual subscription payment submit — the web's /subscription/payment-submit.
 * Deliberately reachable without a permission (lapsed tenants must be able to
 * pay their way back in). The amount is derived from plan × months, never typed,
 * and the request waits for a platform admin's approval.
 */
@Composable
fun PaymentSubmitScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentSubmitViewModel = viewModel(
        factory = PaymentSubmitViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }
    // Submitted: confirm, then land on the Billing History list where the new
    // pending request appears.
    LaunchedEffect(state.submittedMessage) {
        val message = state.submittedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onSubmittedHandled()
        navController.navigate(Routes.appListView("subscriptionBilling")) { launchSingleTop = true }
    }

    AuthenticatedShell(
        title = "Payment Submit",
        currentRoute = Routes.SUBSCRIPTION,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Submit a manual payment for your subscription. The request is " +
                        "reviewed by the platform team; access is granted once it is approved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )

                if (state.plansError != null) {
                    Text(
                        text = state.plansError!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinkButton(
                        text = "Retry",
                        onClick = viewModel::loadPlans,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                val planOptions = state.plans.map { plan ->
                    SelectorOption(
                        id = plan.id,
                        label = "${plan.name} - ${plan.currency} ${AmountFormat.format(plan.price)}".trim(),
                    )
                }
                AppSelectDropdown(
                    label = "Plan *",
                    options = planOptions,
                    selected = planOptions.firstOrNull { it.id == state.selectedPlanId },
                    onSelected = { viewModel.onPlan(it.id) },
                    placeholder = if (state.isLoadingPlans) "Loading plans…" else "Select plan",
                )

                AppTextField(
                    value = state.billingMonths,
                    onValueChange = viewModel::onBillingMonths,
                    label = "1-24",
                    caption = "Billing Months *",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Read-only: always plan price × months, like the web form.
                AppTextField(
                    value = state.amountText,
                    onValueChange = {},
                    label = "Select a plan first",
                    caption = "Amount (plan × months)",
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                AppSelectDropdown(
                    label = "Payment Method *",
                    options = PaymentMethods,
                    selected = PaymentMethods.firstOrNull { it.id == state.paymentMethod },
                    onSelected = { viewModel.onPaymentMethod(it.id) },
                )

                DateField(
                    label = "Payment Date *",
                    value = state.paidAt,
                    context = context,
                    onPicked = viewModel::onPaidAt,
                )

                AppTextField(
                    value = state.transactionId,
                    onValueChange = viewModel::onTransactionId,
                    label = "Enter transaction id",
                    caption = "Transaction ID *",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.senderNumber,
                    onValueChange = viewModel::onSenderNumber,
                    label = "Number the payment was sent from",
                    caption = "Sender Number *",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.receiverAccount,
                    onValueChange = viewModel::onReceiverAccount,
                    label = "Account the payment was sent to (optional)",
                    caption = "Receiver Account",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.customerNote,
                    onValueChange = viewModel::onCustomerNote,
                    label = "Anything the reviewer should know (optional)",
                    caption = "Note",
                    multiline = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                PrimaryButton(
                    text = "Submit Payment",
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A `yyyy-MM-dd` field shown as dd/MM/yyyy, picked with the platform dialog. */
@Composable
private fun DateField(
    label: String,
    value: String,
    context: Context,
    onPicked: (String) -> Unit,
) {
    PickerField(
        label = label,
        value = value.toDisplayDate(),
        placeholder = "dd/mm/yyyy",
        trailingIcon = Icons.Filled.DateRange,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val calendar = Calendar.getInstance()
            Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(value)?.let { match ->
                val (year, month, day) = match.destructured
                calendar.set(year.toInt(), month.toInt() - 1, day.toInt())
            }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onPicked(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth),
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
    )
}

/** "2026-01-05" → "05/01/2026" for display; blank stays blank. */
private fun String.toDisplayDate(): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(this) ?: return this
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}
