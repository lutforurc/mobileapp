package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.MobileFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.DateField
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val COMMISSION_TYPES = listOf(SelectorOption("percentage", "Percentage"), SelectorOption("fixed", "Fixed"))
private val STATUS = listOf(SelectorOption("1", "Active"), SelectorOption("0", "Inactive"))
private val METHODS = listOf(
    SelectorOption("bkash", "bKash"), SelectorOption("nagad", "Nagad"), SelectorOption("bank", "Bank"),
    SelectorOption("cash", "Cash"), SelectorOption("other", "Other"),
)

private fun currency(v: Double, code: String = "BDT") = if (v == 0.0) "-" else "$code ${kitMoney(v)}"

data class ResellerRow(
    val id: Long, val name: String, val businessName: String, val phone: String, val email: String,
    val companies: Int, val approved: Double, val payable: Double, val active: Boolean,
    val commissionType: String, val commissionValue: String,
)

data class ResellerForm(
    val id: Long? = null, val name: String = "", val businessName: String = "", val email: String = "", val phone: String = "",
    val commissionType: SelectorOption = COMMISSION_TYPES.first(), val commissionValue: String = "0", val password: String = "",
    val status: SelectorOption = STATUS.first(), val notes: String = "",
)

data class PayForm(
    val reseller: SelectorOption? = null, val amount: String = "", val method: SelectorOption = METHODS.first(),
    val account: String = "", val reference: String = "", val date: SimpleDate = SimpleDate.today(), val notes: String = "",
)

data class ResellerAdminUiState(
    val isLoading: Boolean = false,
    val saving: Boolean = false,
    val overview: JsonObject? = null,
    val resellers: List<ResellerRow> = emptyList(),
    val companies: List<JsonObject> = emptyList(),
    val payments: List<JsonObject> = emptyList(),
    val ledgers: List<JsonObject> = emptyList(),
    val ledgerPage: Int = 1,
    val ledgerLastPage: Int = 1,
    val form: ResellerForm = ResellerForm(),
    val pay: PayForm = PayForm(),
    val assignReseller: SelectorOption? = null,
    val assignCompany: SelectorOption? = null,
    val askUnassign: JsonObject? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Reseller Admin (ResellerAdmin.tsx): reseller accounts, which company each
 * one brought, and the commission owed and paid. Platform screen — the
 * server restricts it to company 1.
 */
class ResellerAdminViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(ResellerAdminUiState())
    val uiState: StateFlow<ResellerAdminUiState> = _uiState.asStateFlow()

    init { load() }

    private fun arrayOf(o: JsonObject?): List<JsonObject> {
        val d = o?.get("data")
        d.jAsArr()?.let { return it.jObjects() }
        d.jAsObj()?.get("data").jAsArr()?.let { return it.jObjects() }
        return emptyList()
    }

    private fun objectOf(o: JsonObject?): JsonObject? {
        val d = o?.get("data")
        return d.jAsObj()?.get("data").jAsObj() ?: d.jAsObj()
    }

    fun load(ledgerPage: Int = _uiState.value.ledgerPage) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val overview = async { api.get("admin/resellers/overview") }
            val resellers = async { api.get("admin/resellers") }
            val companies = async { api.get("admin/resellers/companies") }
            val payments = async { api.get("admin/resellers/payments") }
            val ledgers = async {
                api.get("admin/resellers/commission-ledgers", mapOf("ledger_type" to "paid", "page" to ledgerPage.toString(), "per_page" to "10"))
            }
            val results = listOf(overview.await(), resellers.await(), companies.await(), payments.await(), ledgers.await())
            val err = results.filterIsInstance<Resource.Error>().firstOrNull()
            if (err != null) {
                _uiState.update { it.copy(isLoading = false, message = err.message.ifBlank { "Failed to load reseller data." }, sessionExpired = err.isUnauthorized) }
                return@launch
            }
            fun body(r: Resource<JsonObject>) = (r as Resource.Success).data
            val ledgerRoot = body(results[4]).get("data").jAsObj()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = objectOf(body(results[0])),
                    resellers = arrayOf(body(results[1])).map { o ->
                        ResellerRow(
                            id = o.jStr("id").toLongOrNull() ?: 0L, name = o.jStr("name"), businessName = o.jStr("business_name"),
                            phone = o.jStr("phone"), email = o.jStr("email"), companies = o.jInt("company_count") ?: 0,
                            approved = o.jNum("approved_commission"), payable = o.jNum("payable_commission"),
                            active = o.jStr("status") == "1", commissionType = o.jStr("commission_type").ifBlank { "percentage" },
                            commissionValue = o.jStr("commission_value").ifBlank { "0" },
                        )
                    },
                    companies = arrayOf(body(results[2])),
                    payments = arrayOf(body(results[3])),
                    ledgers = arrayOf(body(results[4])),
                    ledgerPage = ledgerRoot?.jInt("current_page") ?: 1,
                    ledgerLastPage = ledgerRoot?.jInt("last_page") ?: 1,
                )
            }
        }
    }

    fun onForm(f: ResellerForm) = _uiState.update { it.copy(form = f) }
    fun onPay(p: PayForm) = _uiState.update { it.copy(pay = p) }
    fun onAssignReseller(o: SelectorOption) = _uiState.update { it.copy(assignReseller = o) }
    fun onAssignCompany(o: SelectorOption) = _uiState.update { it.copy(assignCompany = o) }
    fun resetForm() = _uiState.update { it.copy(form = ResellerForm()) }

    fun edit(r: ResellerRow) = _uiState.update {
        it.copy(
            form = ResellerForm(
                id = r.id, name = r.name, businessName = r.businessName, email = r.email, phone = r.phone,
                commissionType = COMMISSION_TYPES.firstOrNull { c -> c.id == r.commissionType } ?: COMMISSION_TYPES.first(),
                commissionValue = r.commissionValue,
                status = if (r.active) STATUS[0] else STATUS[1],
            ),
        )
    }

    private fun write(path: String, body: JsonObject, onOk: () -> Unit) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = api.post(path, body)) {
                is Resource.Success -> {
                    onOk()
                    _uiState.update { it.copy(saving = false, message = r.data.jStr("message").ifBlank { "Saved." }) }
                    load()
                }
                is Resource.Error -> _uiState.update { it.copy(saving = false, message = r.message, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun JsonObject.putOrNull(key: String, v: String) = apply { if (v.isBlank()) add(key, JsonNull.INSTANCE) else addProperty(key, v) }

    fun saveReseller() {
        val f = _uiState.value.form
        if (f.name.isBlank()) {
            _uiState.update { it.copy(message = "Reseller name is required.") }
            return
        }
        val body = JsonObject().apply {
            addProperty("name", f.name.trim())
            putOrNull("business_name", f.businessName)
            putOrNull("email", f.email)
            putOrNull("phone", f.phone)
            add("address", JsonNull.INSTANCE)
            addProperty("commission_type", f.commissionType.id)
            addProperty("commission_value", f.commissionValue.toDoubleOrNull() ?: 0.0)
            if (f.password.isNotBlank()) addProperty("password", f.password)
            addProperty("status", f.status.id.toInt())
            putOrNull("notes", f.notes)
        }
        write(if (f.id != null) "admin/resellers/${f.id}" else "admin/resellers", body) { resetForm() }
    }

    fun assign() {
        val s = _uiState.value
        when {
            s.assignReseller == null -> _uiState.update { it.copy(message = "Please select a reseller.") }
            s.assignCompany == null -> _uiState.update { it.copy(message = "Please select a company.") }
            else -> write(
                "admin/resellers/assign-company",
                JsonObject().apply {
                    addProperty("reseller_id", s.assignReseller.id.toLong())
                    addProperty("company_id", s.assignCompany.id.toLong())
                    addProperty("status", 1)
                },
            ) { _uiState.update { it.copy(assignCompany = null) } }
        }
    }

    fun askUnassign(company: JsonObject) = _uiState.update { it.copy(askUnassign = company) }
    fun dismissUnassign() = _uiState.update { it.copy(askUnassign = null) }
    fun confirmUnassign() {
        val c = _uiState.value.askUnassign ?: return
        _uiState.update { it.copy(askUnassign = null) }
        write(
            "admin/resellers/assign-company",
            JsonObject().apply {
                addProperty("reseller_id", c.jStr("reseller_id").toLongOrNull() ?: 0L)
                addProperty("company_id", c.jStr("id").toLongOrNull() ?: 0L)
                addProperty("status", 0)
            },
        ) {}
    }

    fun pay() {
        val p = _uiState.value.pay
        when {
            p.reseller == null -> _uiState.update { it.copy(message = "Please select a reseller.") }
            (p.amount.toDoubleOrNull() ?: 0.0) <= 0 -> _uiState.update { it.copy(message = "Payment amount is required.") }
            else -> write(
                "admin/resellers/pay-commission",
                JsonObject().apply {
                    addProperty("reseller_id", p.reseller.id.toLong())
                    addProperty("amount", p.amount.toDouble())
                    addProperty("payment_method", p.method.id)
                    putOrNull("paid_to_account", p.account)
                    putOrNull("payment_reference", p.reference)
                    addProperty("ledger_date", p.date.toApi())
                    putOrNull("notes", p.notes)
                },
            ) { _uiState.update { it.copy(pay = PayForm()) } }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { ResellerAdminViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

private fun JsonElement?.n(): Double = this?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResellerAdminScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: ResellerAdminViewModel = viewModel(factory = ResellerAdminViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val mobileFormat = remember { ServiceLocator.provideSessionManager(context).state.value.settings?.mobileNumberFormat.orEmpty() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted
    val resellerOptions = state.resellers.map { SelectorOption(it.id.toString(), it.name) }
    val f = state.form
    val p = state.pay

    AuthenticatedShell(title = "Reseller Admin", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Manage reseller accounts, client assignment, and commission tracking.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted, modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = if (state.isLoading) "Refreshing..." else "Refresh", onClick = { viewModel.load() }, compact = true, isLoading = state.isLoading)
                    }
                }
                item {
                    val o = state.overview
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Resellers" to (o?.jStr("total_resellers")?.ifBlank { "0" } ?: "0"),
                            "Active" to (o?.jStr("active_resellers")?.ifBlank { "0" } ?: "0"),
                            "Companies" to (o?.jStr("assigned_companies")?.ifBlank { "0" } ?: "0"),
                            "Commission" to currency(o?.get("approved_commission").n()),
                            "Paid" to currency(o?.get("paid_commission").n()),
                            "Payable" to currency(o?.get("payable_commission").n()),
                        ).forEach { (label, value) ->
                            Card(Modifier.width(104.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
                                    Text(value, fontWeight = AppFontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { SectionHeading(if (f.id != null) "Edit Reseller" else "New Reseller") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(f.name, { viewModel.onForm(f.copy(name = it)) }, label = "Name", caption = "Name", modifier = Modifier.fillMaxWidth())
                        AppTextField(f.businessName, { viewModel.onForm(f.copy(businessName = it)) }, label = "Business Name", caption = "Business Name", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(f.phone, { viewModel.onForm(f.copy(phone = it)) }, label = "Phone", caption = "Phone", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
                            AppTextField(f.email, { viewModel.onForm(f.copy(email = it)) }, label = "Email", caption = "Email", keyboardType = KeyboardType.Email, modifier = Modifier.weight(1f))
                        }
                        AppTextField(
                            f.password, { viewModel.onForm(f.copy(password = it)) },
                            label = if (f.id != null) "Keep blank to keep current password" else "Optional reseller login password",
                            caption = "Login Password", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSelectDropdown("Commission", COMMISSION_TYPES, f.commissionType, { viewModel.onForm(f.copy(commissionType = it)) }, Modifier.weight(1f))
                            AppTextField(f.commissionValue, { v -> viewModel.onForm(f.copy(commissionValue = v.filter { c -> c.isDigit() || c == '.' })) }, label = "0", caption = "Value", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                        }
                        AppSelectDropdown("Status", STATUS, f.status, { viewModel.onForm(f.copy(status = it)) })
                        AppTextField(f.notes, { viewModel.onForm(f.copy(notes = it)) }, label = "Notes", caption = "Notes", multiline = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(text = if (state.saving) "Saving..." else "Save", onClick = viewModel::saveReseller, isLoading = state.saving)
                            if (f.id != null) SecondaryButton(text = "Cancel", onClick = viewModel::resetForm)
                        }
                    }
                }
                item { SectionHeading("Reseller List") }
                if (state.resellers.isEmpty()) item { ReportNote("No reseller found.") }
                items(state.resellers, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(r.businessName.ifBlank { "-" }, style = MaterialTheme.typography.labelSmall, color = muted)
                                Text(
                                    "${MobileFormat.format(r.phone, mobileFormat).ifBlank { "-" }} · ${r.email.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                Text(
                                    "Companies: ${r.companies} · Commission: ${currency(r.approved)} · ${if (r.active) "Active" else "Inactive"}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            LinkButton(text = "Edit", onClick = { viewModel.edit(r) })
                        }
                    }
                }
                item { SectionHeading("Assign Company") }
                item {
                    val unassigned = state.companies.filter { it.jStr("reseller_id").isBlank() || it.jStr("reseller_id") == "0" }
                        .map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppSelectDropdown("Reseller", resellerOptions, state.assignReseller, viewModel::onAssignReseller, placeholder = "Select reseller")
                        AppSelectDropdown("Unassigned Company", unassigned, state.assignCompany, viewModel::onAssignCompany, placeholder = "Select company")
                        PrimaryButton(text = "Assign", onClick = viewModel::assign, isLoading = state.saving)
                    }
                }
                item { SectionHeading("Pay Reseller Commission") }
                item {
                    val payable = state.resellers.firstOrNull { it.id.toString() == p.reseller?.id }?.payable
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppSelectDropdown("Reseller", resellerOptions, p.reseller, { viewModel.onPay(p.copy(reseller = it)) }, placeholder = "Select reseller")
                        AppTextField(
                            p.amount, { v -> viewModel.onPay(p.copy(amount = v.filter { c -> c.isDigit() || c == '.' })) },
                            label = "Enter Amount", caption = "Amount" + (payable?.let { " (Payable ${currency(it)})" } ?: ""),
                            keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppSelectDropdown("Payment Method", METHODS, p.method, { viewModel.onPay(p.copy(method = it)) }, Modifier.weight(1f))
                            DateField("Payment Date", p.date, { viewModel.onPay(p.copy(date = it)) }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(p.account, { viewModel.onPay(p.copy(account = it)) }, label = "Paid To Account", caption = "Paid To Account", modifier = Modifier.weight(1f))
                            AppTextField(p.reference, { viewModel.onPay(p.copy(reference = it)) }, label = "Reference", caption = "Reference", modifier = Modifier.weight(1f))
                        }
                        AppTextField(p.notes, { viewModel.onPay(p.copy(notes = it)) }, label = "Notes", caption = "Notes", multiline = true, modifier = Modifier.fillMaxWidth())
                        PrimaryButton(text = if (state.saving) "Paying..." else "Pay", onClick = viewModel::pay, isLoading = state.saving)
                    }
                }
                item { SectionHeading("Assigned Companies") }
                val assigned = state.companies.filter { it.jStr("reseller_id").isNotBlank() && it.jStr("reseller_id") != "0" }
                if (assigned.isEmpty()) item { ReportNote("No company assignment found.") }
                items(assigned) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.jStr("name"), fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Reseller: ${c.jStr("reseller_name").ifBlank { "-" }} · Plan: ${c.jStr("plan_name").ifBlank { "-" }} · ${c.jStr("subscription_status").ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                            }
                            LinkButton(text = "Cancel", enabled = !state.saving, onClick = { viewModel.askUnassign(c) }, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item { SectionHeading("Payment Commissions") }
                if (state.payments.isEmpty()) item { ReportNote("No payment commission found.") }
                items(state.payments) { pay ->
                    val code = pay.jStr("currency").ifBlank { "BDT" }
                    Text(
                        "${pay.jStr("company_name").ifBlank { "-" }} · ${pay.jStr("payment_status").ifBlank { "-" }} · " +
                            "Amount ${currency(pay.get("amount").n(), code)} · Commission ${currency(pay.get("commission_amount").n(), code)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreen,
                    )
                }
                item { SectionHeading("Commission Payments") }
                val paid = state.ledgers.filter { it.jStr("ledger_type") == "paid" }
                if (paid.isEmpty()) item { ReportNote("No reseller payment found.") }
                items(paid) { l ->
                    val date = l.jStr("ledger_date").ifBlank { l.jStr("paid_at") }
                    Text(
                        "${SimpleDate.fromApi(date.take(10))?.toDisplay() ?: "-"} · ${l.jStr("reseller_name").ifBlank { "-" }} · " +
                            "${METHODS.firstOrNull { it.id == l.jStr("payment_method") }?.label ?: l.jStr("payment_method").ifBlank { "-" }} · " +
                            "${l.jStr("paid_to_account").ifBlank { "-" }} · ${l.jStr("payment_reference").ifBlank { l.jStr("reference_no") }.ifBlank { "-" }} · " +
                            currency(l.get("amount").n(), l.jStr("currency").ifBlank { "BDT" }),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreen,
                    )
                }
                if (state.ledgerLastPage > 1) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            LinkButton(text = "Previous", enabled = state.ledgerPage > 1, onClick = { viewModel.load(state.ledgerPage - 1) })
                            Text("Page ${state.ledgerPage} of ${state.ledgerLastPage}", style = MaterialTheme.typography.labelMedium)
                            LinkButton(text = "Next", enabled = state.ledgerPage < state.ledgerLastPage, onClick = { viewModel.load(state.ledgerPage + 1) })
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.askUnassign?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnassign,
            title = { Text("Cancel assignment?") },
            text = { Text("Cancel this company assignment? (${c.jStr("name")})") },
            confirmButton = { LinkButton(text = "Cancel Assignment", onClick = viewModel::confirmUnassign, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Keep", onClick = viewModel::dismissUnassign) },
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen, modifier = Modifier.padding(top = 4.dp))
}
