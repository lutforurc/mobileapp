package com.example.cashbookbd.ui.subscription

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
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

private val BILLING_INTERVALS = listOf(
    SelectorOption("monthly", "Monthly"),
    SelectorOption("quarterly", "Quarterly"),
    SelectorOption("half_yearly", "Half Yearly"),
    SelectorOption("yearly", "Yearly"),
)

private val PLAN_STATUS = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
)

/** The fixed feature switches every plan carries — always all five sent. */
private val PLAN_FEATURES = listOf(
    "accounting" to "Accounting Module",
    "hrms" to "HRMS Module",
    "inventory" to "Inventory Module",
    "sms" to "SMS Module",
    "list_customers" to "List Customers",
)

data class PlanFormUiState(
    val planId: Long? = null,
    val isLoadingPlan: Boolean = false,

    val name: String = "",
    val slug: String = "",
    val billingInterval: SelectorOption = BILLING_INTERVALS.first(),
    val isActive: Boolean = true,
    val price: String = "",
    val currency: String = "BDT",
    val trialDays: String = "0",
    val sortOrder: String = "0",
    // Blank = Unlimited on every quota.
    val maxEmployees: String = "",
    val maxCustomers: String = "",
    val maxProducts: String = "",
    val maxUsers: String = "",
    val maxDevicesPerUser: String = "",
    val maxBranches: String = "",
    val maxTransactionsPerMonth: String = "",
    val supportTime: String = "",
    val description: String = "",
    /** feature_key → on. */
    val features: Map<String, Boolean> = PLAN_FEATURES.associate { it.first to false },

    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class PlanFormViewModel(
    private val api: ReportApiService,
    planId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanFormUiState(planId = planId))
    val uiState: StateFlow<PlanFormUiState> = _uiState.asStateFlow()

    init {
        if (planId != null) loadPlan(planId)
    }

    /** Loads the plan for editing. Flat envelope: the plan sits at `data`. */
    private fun loadPlan(id: Long) {
        _uiState.update { it.copy(isLoadingPlan = true) }
        viewModelScope.launch {
            val result = flatCall { api.get("admin/subscription/plans/$id", emptyMap()) }
            when (result) {
                is Resource.Success -> {
                    val plan = result.data.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    _uiState.update { state ->
                        if (plan == null) state.copy(isLoadingPlan = false, message = "Plan not found.")
                        else state.copy(
                            isLoadingPlan = false,
                            name = plan.str("name").orEmpty(),
                            slug = plan.str("slug").orEmpty(),
                            billingInterval = BILLING_INTERVALS.firstOrNull {
                                it.id == plan.str("billing_interval")
                            } ?: BILLING_INTERVALS.first(),
                            isActive = plan.str("is_active")
                                ?.let { it == "true" || it.toDoubleOrNull() == 1.0 } != false,
                            price = plan.num("price"),
                            currency = plan.str("currency").orEmpty().ifBlank { "BDT" },
                            trialDays = plan.num("trial_days").ifBlank { "0" },
                            sortOrder = plan.num("sort_order").ifBlank { "0" },
                            maxEmployees = plan.num("max_employees"),
                            maxCustomers = plan.num("max_customers"),
                            maxProducts = plan.num("max_products"),
                            maxUsers = plan.num("max_users"),
                            maxDevicesPerUser = plan.num("max_devices_per_user"),
                            maxBranches = plan.num("max_branches"),
                            maxTransactionsPerMonth = plan.num("max_transactions_per_month"),
                            supportTime = plan.str("support_time").orEmpty(),
                            description = plan.str("description").orEmpty(),
                            features = PLAN_FEATURES.associate { (key, _) ->
                                key to (plan.get("features")?.takeIf { it.isJsonArray }?.asJsonArray
                                    ?.any { f ->
                                        val o = f.takeIf { it.isJsonObject }?.asJsonObject
                                        o?.str("feature_key") == key && o.str("feature_value") == "1"
                                    } == true)
                            },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingPlan = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(v: String) = _uiState.update { it.copy(name = v) }
    fun onSlug(v: String) = _uiState.update { it.copy(slug = v) }
    fun onBillingInterval(o: SelectorOption) = _uiState.update { it.copy(billingInterval = o) }
    fun onStatus(o: SelectorOption) = _uiState.update { it.copy(isActive = o.id == "1") }
    fun onPrice(v: String) = _uiState.update { it.copy(price = v) }
    fun onCurrency(v: String) = _uiState.update { it.copy(currency = v) }
    fun onTrialDays(v: String) = _uiState.update { it.copy(trialDays = v) }
    fun onSortOrder(v: String) = _uiState.update { it.copy(sortOrder = v) }
    fun onMaxEmployees(v: String) = _uiState.update { it.copy(maxEmployees = v) }
    fun onMaxCustomers(v: String) = _uiState.update { it.copy(maxCustomers = v) }
    fun onMaxProducts(v: String) = _uiState.update { it.copy(maxProducts = v) }
    fun onMaxUsers(v: String) = _uiState.update { it.copy(maxUsers = v) }
    fun onMaxDevicesPerUser(v: String) = _uiState.update { it.copy(maxDevicesPerUser = v) }
    fun onMaxBranches(v: String) = _uiState.update { it.copy(maxBranches = v) }
    fun onMaxTransactionsPerMonth(v: String) = _uiState.update { it.copy(maxTransactionsPerMonth = v) }
    fun onSupportTime(v: String) = _uiState.update { it.copy(supportTime = v) }
    fun onDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun onFeature(key: String, on: Boolean) =
        _uiState.update { it.copy(features = it.features + (key to on)) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "Plan name is required.") }
            return
        }
        if (state.price.isBlank()) {
            _uiState.update { it.copy(message = "Plan price is required.") }
            return
        }
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Blank quotas travel as null = Unlimited; features always all five.
            val body = buildMap<String, Any> {
                put("name", state.name.trim())
                state.slug.trim().takeIf { it.isNotEmpty() }?.let { put("slug", it) }
                put("billing_interval", state.billingInterval.id)
                put("is_active", state.isActive)
                put("price", state.price.toDoubleOrNull() ?: 0.0)
                put("currency", state.currency.trim().ifBlank { "BDT" })
                put("trial_days", state.trialDays.toIntOrNull() ?: 0)
                put("sort_order", state.sortOrder.toIntOrNull() ?: 0)
                state.maxEmployees.toIntOrNull()?.let { put("max_employees", it) }
                state.maxCustomers.toIntOrNull()?.let { put("max_customers", it) }
                state.maxProducts.toIntOrNull()?.let { put("max_products", it) }
                state.maxUsers.toIntOrNull()?.let { put("max_users", it) }
                state.maxDevicesPerUser.toIntOrNull()?.let { put("max_devices_per_user", it) }
                state.maxBranches.toIntOrNull()?.let { put("max_branches", it) }
                state.maxTransactionsPerMonth.toIntOrNull()?.let { put("max_transactions_per_month", it) }
                state.supportTime.trim().takeIf { it.isNotEmpty() }?.let { put("support_time", it) }
                state.description.trim().takeIf { it.isNotEmpty() }?.let { put("description", it) }
                put("features", PLAN_FEATURES.map { (key, name) ->
                    mapOf(
                        "feature_key" to key,
                        "feature_name" to name,
                        "feature_value" to if (state.features[key] == true) "1" else "0",
                    )
                })
            }
            val result = flatCall {
                val id = state.planId
                if (id == null) api.postAny("admin/subscription/plans", body)
                else api.postAny("admin/subscription/plans/$id", body)
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMessage = result.data.str("message")?.ifBlank { null } ?: "Plan saved.",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
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

    /** These endpoints answer FLAT `{success, message, data}` — no foundData. */
    private suspend fun flatCall(
        request: suspend () -> retrofit2.Response<com.google.gson.JsonElement>,
    ): Resource<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val response = request()
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: response.errorBody()?.string()
                    ?.let { runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull() }
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                return@withContext Resource.Error(body.str("message")?.ifBlank { null } ?: "The request was refused.")
            }
            Resource.Success(body)
        } catch (e: java.io.IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    companion object {
        fun provideFactory(context: Context, planId: Long?) = viewModelFactory {
            initializer {
                PlanFormViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                    planId = planId,
                )
            }
        }
    }
}

private fun JsonObject.str(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

/** A numeric field as text-field content: null → "", 500.0 → "500". */
private fun JsonObject.num(key: String): String =
    str(key)?.toDoubleOrNull()
        ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
        .orEmpty()

/** Creates or edits a SaaS plan — every quota blank means Unlimited. */
@Composable
fun PlanFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    planId: Long? = null,
    viewModel: PlanFormViewModel = viewModel(
        key = "plan-${planId ?: 0L}",
        factory = PlanFormViewModel.provideFactory(LocalContext.current, planId),
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
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (state.planId == null) "New Plan" else "Edit Plan",
        currentRoute = Routes.SUBSCRIPTION,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoadingPlan) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppTextField(state.name, viewModel::onName, label = "Plan Name", modifier = Modifier.fillMaxWidth())
                    AppTextField(
                        state.slug, viewModel::onSlug,
                        label = "Slug (blank = from the name)", modifier = Modifier.fillMaxWidth(),
                    )
                    AppSelectDropdown(
                        label = "Billing Interval",
                        options = BILLING_INTERVALS,
                        selected = state.billingInterval,
                        onSelected = viewModel::onBillingInterval,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppSelectDropdown(
                        label = "Status",
                        options = PLAN_STATUS,
                        selected = PLAN_STATUS.first { it.id == if (state.isActive) "1" else "0" },
                        onSelected = viewModel::onStatus,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.price, viewModel::onPrice, label = "Price",
                            keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.currency, viewModel::onCurrency, label = "Currency",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.trialDays, viewModel::onTrialDays, label = "Trial Days",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.sortOrder, viewModel::onSortOrder, label = "Sort Order",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "Limits — blank = Unlimited",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.maxEmployees, viewModel::onMaxEmployees, label = "Max Employees",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.maxCustomers, viewModel::onMaxCustomers, label = "Max Customers",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.maxProducts, viewModel::onMaxProducts, label = "Max Products",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.maxUsers, viewModel::onMaxUsers, label = "Max Users",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.maxDevicesPerUser, viewModel::onMaxDevicesPerUser, label = "Max Devices / User",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.maxBranches, viewModel::onMaxBranches, label = "Max Branches",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                    }
                    AppTextField(
                        state.maxTransactionsPerMonth, viewModel::onMaxTransactionsPerMonth,
                        label = "Max Transactions / Month",
                        keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        state.supportTime, viewModel::onSupportTime,
                        label = "Support Time (e.g. 10 AM - 6 PM)", modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        state.description, viewModel::onDescription,
                        label = "Description (optional)", multiline = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Included Features",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    PLAN_FEATURES.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = state.features[key] == true,
                                onCheckedChange = { viewModel.onFeature(key, it) },
                            )
                        }
                    }
                    Text(
                        text = "Saving also keeps a global role in step with the plan's name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    PrimaryButton(
                        text = if (state.planId == null) "Save" else "Update",
                        onClick = viewModel::save,
                        isLoading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
