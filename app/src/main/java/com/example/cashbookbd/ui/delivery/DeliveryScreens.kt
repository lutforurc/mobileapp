package com.example.cashbookbd.ui.delivery

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPath
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.StockShortageWarning
import com.example.cashbookbd.data.repository.parseStockShortage
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.ParityRoutes
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.StockShortageDialog
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/*
 * Delivery for Tiles and Sanitary (web a6e09291 … 4912e5bd): the voucher that
 * takes the stock out when the shop sends the goods, rather than when it
 * bills; the report of which invoices have gone out; and the dashboard a
 * dispatcher works from. The delivery slip's paper stays on the web.
 */

// ---- Shared ---------------------------------------------------------------

/** One invoice of `delivery/report`. */
data class DeliveryInvoiceRow(
    val mtmId: String,
    val vrNo: String,
    val vrDate: String,
    val customer: String,
    val ordered: Double,
    val delivered: Double,
    val pending: Double,
    val status: String,
)

/** The words the web's print and screen share. */
fun deliveryStatusLabel(status: String): String = when (status) {
    "complete" -> "Complete"
    "partial" -> "Partial"
    "none" -> "Not delivered"
    else -> "Invoice (stock taken)"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "complete" -> MaterialTheme.appColors.success
    "partial" -> MaterialTheme.appColors.warning
    "none" -> MaterialTheme.appColors.danger
    else -> MaterialTheme.appColors.textMuted
}

/** The invoice date as the Cash Book shows one, dd/MM/yyyy. */
private fun deliveryDate(value: String): String =
    SimpleDate.fromApi(value.take(10))?.toDisplay() ?: value.ifBlank { "-" }

/** A quantity, not money. */
private fun qty(v: Double): String = kitMoney(v)

private suspend fun WebApi.deliveryReport(from: String?, to: String?, status: String?, mode: String): Resource<List<DeliveryInvoiceRow>> {
    val body = JsonObject().apply {
        from?.takeIf { it.isNotBlank() }?.let { addProperty("from", it) }
        to?.takeIf { it.isNotBlank() }?.let { addProperty("to", it) }
        status?.takeIf { it.isNotBlank() }?.let { addProperty("status", it) }
        addProperty("mode", mode)
    }
    return when (val r = post("delivery/report", body)) {
        is Resource.Success -> Resource.Success(
            r.data.jRows().map { o ->
                DeliveryInvoiceRow(
                    mtmId = o.jStr("mtm_id"),
                    vrNo = o.jStr("vr_no"),
                    vrDate = o.jStr("vr_date"),
                    customer = o.jStr("customer_name"),
                    ordered = o.jNum("ordered"),
                    delivered = o.jNum("delivered"),
                    pending = o.jNum("pending"),
                    status = o.jStr("status"),
                )
            },
        )
        is Resource.Error -> r
        Resource.Loading -> Resource.Loading
    }
}

@Composable
private fun DeliveryInvoiceTable(
    rows: List<DeliveryInvoiceRow>,
    emptyText: String,
    footerLabel: String?,
    onOpenInvoice: ((DeliveryInvoiceRow) -> Unit)? = null,
    pendingOnly: Boolean = false,
    pendingColor: Color? = null,
) {
    val columns = listOf(
        ReportColumn<DeliveryInvoiceRow>("SL. NO", ReportColWidth.Fixed(52.dp), TextAlign.Center) { _, i -> cellText((i + 1).toString()) },
        ReportColumn<DeliveryInvoiceRow>("INVOICE NO", ReportColWidth.Fixed(120.dp), TextAlign.Center) { r, _ ->
            if (onOpenInvoice != null && r.vrNo.isNotBlank()) {
                ReportTableCell.Slot {
                    Text(
                        r.vrNo,
                        modifier = Modifier.clickable { onOpenInvoice(r) }.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.appColors.textLink,
                    )
                }
            } else cellText(r.vrNo)
        },
        ReportColumn<DeliveryInvoiceRow>("DATE", ReportColWidth.Fixed(96.dp), TextAlign.Center) { r, _ -> cellText(deliveryDate(r.vrDate)) },
        ReportColumn<DeliveryInvoiceRow>("CUSTOMER", ReportColWidth.Fixed(170.dp)) { r, _ -> cellText(r.customer.ifBlank { "-" }, maxLines = 2) },
        ReportColumn<DeliveryInvoiceRow>("ORDERED", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ -> cellText(qty(r.ordered)) },
        ReportColumn<DeliveryInvoiceRow>("DELIVERED", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ -> cellText(qty(r.delivered)) },
        ReportColumn<DeliveryInvoiceRow>("PENDING", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ ->
            cellText(qty(r.pending), bold = pendingColor != null, color = pendingColor ?: Color.Unspecified)
        },
        ReportColumn<DeliveryInvoiceRow>("STATUS", ReportColWidth.Fixed(140.dp), TextAlign.Center) { r, _ ->
            ReportTableCell.Slot {
                Text(
                    deliveryStatusLabel(r.status),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = statusColor(r.status),
                )
            }
        },
    )
    val footer = when {
        rows.isEmpty() || footerLabel == null -> emptyList()
        pendingOnly -> listOf(
            listOf(
                ReportFooterCell(cellText(footerLabel, align = TextAlign.End), colSpan = 6),
                ReportFooterCell(cellText(qty(rows.sumOf { it.pending }), bold = true, color = pendingColor ?: Color.Unspecified)),
                ReportFooterCell(ReportTableCell.Empty),
            ),
        )
        else -> listOf(
            listOf(
                ReportFooterCell(cellText(footerLabel, align = TextAlign.End, bold = true), colSpan = 4),
                ReportFooterCell(cellText(qty(rows.sumOf { it.ordered }), bold = true)),
                ReportFooterCell(cellText(qty(rows.sumOf { it.delivered }), bold = true)),
                ReportFooterCell(cellText(qty(rows.sumOf { it.pending }), bold = true)),
                ReportFooterCell(ReportTableCell.Empty),
            ),
        )
    }
    ReportTable(columns = columns, data = rows, scrollable = false, footerRows = footer, noDataMessage = emptyText)
}

// ---- Delivery Report --------------------------------------------------------

private val STATUS_OPTIONS = listOf(
    SelectorOption("", "All"),
    SelectorOption("complete", "Complete"),
    SelectorOption("partial", "Partial"),
    SelectorOption("none", "Not delivered"),
)
private val MODE_OPTIONS = listOf(
    SelectorOption("delivery", "Delivery invoices"),
    SelectorOption("all", "All invoices"),
)

data class DeliveryReportUiState(
    val from: SimpleDate? = null,
    val to: SimpleDate? = null,
    val status: SelectorOption = STATUS_OPTIONS.first(),
    val mode: SelectorOption = MODE_OPTIONS.first(),
    val isLoading: Boolean = false,
    val rows: List<DeliveryInvoiceRow> = emptyList(),
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Which invoices are fully delivered, which are partial, and which have not
 * gone out — one row per invoice. Only invoices that actually deliver are
 * listed by default: one that took its own stock can never be "delivered".
 */
class DeliveryReportViewModel(private val api: WebApi, from: String?, to: String?) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DeliveryReportUiState(from = SimpleDate.fromApi(from), to = SimpleDate.fromApi(to)),
    )
    val uiState: StateFlow<DeliveryReportUiState> = _uiState.asStateFlow()

    init { search() }

    fun onFrom(d: SimpleDate) = _uiState.update { it.copy(from = d) }
    fun onTo(d: SimpleDate) = _uiState.update { it.copy(to = d) }
    fun onStatus(o: SelectorOption) = _uiState.update { it.copy(status = o) }
    fun onMode(o: SelectorOption) = _uiState.update { it.copy(mode = o) }

    fun reset() {
        _uiState.update { it.copy(from = null, to = null, status = STATUS_OPTIONS.first(), mode = MODE_OPTIONS.first()) }
        search()
    }

    fun search() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.deliveryReport(s.from?.toApi(), s.to?.toApi(), s.status.id, s.mode.id)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, rows = r.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, rows = emptyList(), message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, from: String?, to: String?) = viewModelFactory {
            initializer { DeliveryReportViewModel(ServiceLocator.provideWebApi(context.applicationContext), from, to) }
        }
    }
}

@Composable
fun DeliveryReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    from: String? = null,
    to: String? = null,
    viewModel: DeliveryReportViewModel = viewModel(factory = DeliveryReportViewModel.provideFactory(LocalContext.current, from, to)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = "Delivery Report", currentRoute = Routes.REPORTS, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DateRangeFields(state.from, state.to, viewModel::onFrom, viewModel::onTo, "From", "To") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Status", STATUS_OPTIONS, state.status, viewModel::onStatus, Modifier.weight(1f))
                        AppSelectDropdown("Invoices", MODE_OPTIONS, state.mode, viewModel::onMode, Modifier.weight(1f))
                    }
                }
                item {
                    FilterActions(
                        onApply = viewModel::search, onReset = viewModel::reset, isLoading = state.isLoading,
                        applyText = "Search", modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { StatusCounts(state.rows) }
                item {
                    DeliveryInvoiceTable(
                        rows = state.rows,
                        emptyText = if (state.isLoading) "Loading..." else "No invoices found.",
                        footerLabel = "Total",
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun StatusCounts(rows: List<DeliveryInvoiceRow>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Complete: ${rows.count { it.status == "complete" }}", color = MaterialTheme.appColors.success, style = MaterialTheme.typography.bodySmall)
        Text("Partial: ${rows.count { it.status == "partial" }}", color = MaterialTheme.appColors.warning, style = MaterialTheme.typography.bodySmall)
        Text("Not delivered: ${rows.count { it.status == "none" }}", color = MaterialTheme.appColors.danger, style = MaterialTheme.typography.bodySmall)
    }
}

// ---- Delivery dashboard -------------------------------------------------------

enum class DashRange(val label: String) { THIS_MONTH("This Month"), LAST_MONTH("Last Month"), CUSTOM("Custom") }

data class DeliveryDashboardUiState(
    val range: DashRange = DashRange.THIS_MONTH,
    val customFrom: SimpleDate? = null,
    val customTo: SimpleDate? = null,
    val isLoading: Boolean = false,
    val settled: Boolean = false,
    val rows: List<DeliveryInvoiceRow> = emptyList(),
    val refreshedAt: String = "",
    val sessionExpired: Boolean = false,
) {
    /** The window the page reads: this month so far, last month, or the boxes. */
    val window: Pair<SimpleDate, SimpleDate>
        get() {
            val today = SimpleDate.today()
            val monthStart = SimpleDate(today.year, today.month, 1)
            return when {
                range == DashRange.LAST_MONTH -> {
                    val lastDay = monthStart.plusDays(-1)
                    SimpleDate(lastDay.year, lastDay.month, 1) to lastDay
                }
                range == DashRange.CUSTOM && customFrom != null && customTo != null -> customFrom to customTo
                // Custom with a box still empty keeps reading this month.
                else -> monthStart to today
            }
        }
}

/**
 * The delivery system at a glance (DeliveryDashboard.tsx): one read of the
 * delivery report, the counts, and the two lists a dispatcher works from —
 * Partial (the next trip is planned against them) and Due for Delivery
 * (nothing has gone out yet). Re-reads every five minutes and on demand.
 */
class DeliveryDashboardViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(DeliveryDashboardUiState())
    val uiState: StateFlow<DeliveryDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(5 * 60 * 1000L)
            }
        }
    }

    fun onRange(o: SelectorOption) {
        _uiState.update { it.copy(range = DashRange.valueOf(o.id)) }
        refresh()
    }

    fun onFrom(d: SimpleDate) { _uiState.update { it.copy(customFrom = d) }; refresh() }
    fun onTo(d: SimpleDate) { _uiState.update { it.copy(customTo = d) }; refresh() }

    fun refresh() {
        val (from, to) = _uiState.value.window
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.deliveryReport(from.toApi(), to.toApi(), null, "delivery")) {
                is Resource.Success -> _uiState.update {
                    val now = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
                    it.copy(isLoading = false, settled = true, rows = r.data, refreshedAt = now)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, settled = true, rows = emptyList(), sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { DeliveryDashboardViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryDashboardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: DeliveryDashboardViewModel = viewModel(factory = DeliveryDashboardViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    val rows = state.rows
    val (from, to) = state.window
    // The longest wait first.
    val partial = rows.filter { it.delivered > 0 && it.pending > 0 }.sortedByDescending { it.pending }
    val due = rows.filter { it.delivered <= 0 && it.pending > 0 }.sortedByDescending { it.pending }
    val openInvoice: (DeliveryInvoiceRow) -> Unit = { navController.navigate(ParityRoutes.delivery(it.vrNo)) }

    AuthenticatedShell(title = "Delivery System", currentRoute = Routes.HOME, navController = navController, onLogout = onLogout) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "${from.toDisplay()} – ${to.toDisplay()}" + if (state.refreshedAt.isNotBlank()) " · updated ${state.refreshedAt}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    AppSelectDropdown(
                        "Range",
                        DashRange.entries.map { SelectorOption(it.name, it.label) },
                        SelectorOption(state.range.name, state.range.label),
                        viewModel::onRange,
                        Modifier.weight(1f),
                    )
                    SecondaryButton(text = "Refresh", onClick = viewModel::refresh, isLoading = state.isLoading)
                }
            }
            if (state.range == DashRange.CUSTOM) {
                item { DateRangeFields(state.customFrom, state.customTo, viewModel::onFrom, viewModel::onTo, "From", "To") }
            }
            item {
                PrimaryButton(
                    text = "Open Delivery",
                    onClick = { navController.navigate(ParityRoutes.delivery(null)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val dim = if (state.settled) 1f else 0.6f
                    StatTile("Invoices", rows.size.toString(), "delivery invoices in this window", dim = dim)
                    StatTile("Complete", rows.count { it.status == "complete" }.toString(), "everything ordered has gone out", MaterialTheme.appColors.success, dim)
                    StatTile("Partial", rows.count { it.status == "partial" }.toString(), "some lines still to send", MaterialTheme.appColors.warning, dim)
                    StatTile("Not delivered", rows.count { it.status == "none" }.toString(), "nothing has gone out yet", MaterialTheme.appColors.danger, dim)
                    StatTile("Ordered", qty(rows.sumOf { it.ordered }), "quantity billed across these invoices", dim = dim)
                    StatTile("Delivered", qty(rows.sumOf { it.delivered }), "quantity already dispatched", MaterialTheme.colorScheme.primary, dim)
                    val pending = rows.sumOf { it.pending }
                    StatTile("Pending", qty(pending), "quantity still to load", if (pending > 0) MaterialTheme.appColors.warning else null, dim)
                }
            }
            item {
                DashListHeader("Partial Deliveries", partial.size) {
                    navController.navigate(ParityRoutes.deliveryReport(from.toApi(), to.toApi()))
                }
            }
            item {
                DeliveryInvoiceTable(
                    rows = partial,
                    emptyText = if (state.isLoading) "Loading…" else "No partially delivered invoices in this window.",
                    footerLabel = "Still to send across these invoices",
                    onOpenInvoice = openInvoice,
                    pendingOnly = true,
                    pendingColor = MaterialTheme.appColors.warning,
                )
            }
            item {
                DashListHeader("Due for Delivery", due.size) {
                    navController.navigate(ParityRoutes.deliveryReport(from.toApi(), to.toApi()))
                }
            }
            item {
                DeliveryInvoiceTable(
                    rows = due,
                    emptyText = if (state.isLoading) "Loading…" else "Nothing due — every invoice in this window has gone out.",
                    footerLabel = "Still to send across these invoices",
                    onOpenInvoice = openInvoice,
                    pendingOnly = true,
                    pendingColor = MaterialTheme.appColors.danger,
                )
            }
        }
    }
}

@Composable
private fun DashListHeader(title: String, count: Int, onOpenReport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.clickable(onClick = onOpenReport),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.appColors.textOnScreen,
        )
        Text("$count invoice${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
    }
}

/** A dashboard tile: label, figure, and what the figure means. */
@Composable
fun StatTile(label: String, value: String, working: String, tone: Color? = null, dim: Float = 1f) {
    Card(Modifier.width(160.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted.copy(alpha = dim))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
                color = (tone ?: MaterialTheme.appColors.text).copy(alpha = dim),
            )
            Text(working, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted.copy(alpha = dim))
        }
    }
}

// ---- Delivery (the voucher) -------------------------------------------------

data class DeliveryLine(
    val uid: String,
    val label: String,
    val ordered: Double,
    val delivered: Double,
    val pending: Double,
    val qty: String,
)

data class DeliveryListRow(
    val idHashed: String,
    val vrNo: String,
    val date: String,
    val customer: String,
    val challanNo: String,
    val vehicleNo: String,
    val active: Boolean,
)

data class DeliveryHeader(
    val challanNo: String = "",
    val vehicleNo: String = "",
    val driverName: String = "",
    val driverMobile: String = "",
    val receiverName: String = "",
    val receiverMobile: String = "",
    val notes: String = "",
)

data class DeliveryUiState(
    val canCreate: Boolean = false,
    val canCancel: Boolean = false,
    val trxDate: String = "",
    val searchInvoice: String = "",
    val isLoadingInvoice: Boolean = false,
    val invoiceId: String = "",
    val lines: List<DeliveryLine> = emptyList(),
    val returned: Double = 0.0,
    /** 1 = the invoice took its own stock; nothing to deliver. Null until loaded. */
    val deductsOnInvoice: Int? = null,
    val header: DeliveryHeader = DeliveryHeader(),
    val idempotencyKey: String = "",
    val isSaving: Boolean = false,
    val stockWarning: StockShortageWarning? = null,
    val pendingPayload: JsonObject? = null,
    val list: List<DeliveryListRow> = emptyList(),
    val isLoadingList: Boolean = false,
    val confirmCancel: DeliveryListRow? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val totalGoingOut: Double get() = lines.sumOf { it.qty.toDoubleOrNull() ?: 0.0 }
}

/**
 * Load an invoice by its number, see what is still pending, enter what goes
 * out on this trip, save; underneath, the deliveries already made, each
 * cancellable. A delivery is always dated on the branch's transaction date —
 * shown, not chosen (the server ignores any date sent).
 */
class DeliveryViewModel(
    private val api: WebApi,
    canCreate: Boolean,
    canCancel: Boolean,
    trxDate: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeliveryUiState(canCreate = canCreate, canCancel = canCancel, trxDate = trxDate))
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    private var autoLoaded = ""

    init { loadList() }

    /** Opened from the dashboard with an invoice: load it, once. */
    fun autoLoad(invoiceNo: String?) {
        val no = invoiceNo?.trim().orEmpty()
        if (no.isEmpty() || autoLoaded == no) return
        autoLoaded = no
        _uiState.update { it.copy(searchInvoice = no) }
        loadInvoice(no)
    }

    fun onSearch(v: String) = _uiState.update { it.copy(searchInvoice = v) }
    fun onHeader(h: DeliveryHeader) = _uiState.update { it.copy(header = h) }
    fun onQty(uid: String, v: String) = _uiState.update { s ->
        s.copy(lines = s.lines.map { if (it.uid == uid) it.copy(qty = v.filter { c -> c.isDigit() || c == '.' }) else it })
    }

    fun load() {
        val no = _uiState.value.searchInvoice.trim()
        if (no.isEmpty()) {
            _uiState.update { it.copy(message = "Enter an invoice number") }
            return
        }
        loadInvoice(no)
    }

    private fun loadInvoice(no: String) {
        _uiState.update { it.copy(isLoadingInvoice = true) }
        viewModelScope.launch {
            when (val r = api.post("delivery/pending", JsonObject().apply { addProperty("invoice_no", no) })) {
                is Resource.Success -> {
                    val d = r.data.jPayload()
                    _uiState.update {
                        it.copy(
                            isLoadingInvoice = false,
                            invoiceId = d.jStr("mtmId"),
                            returned = d.jNum("returned"),
                            deductsOnInvoice = d.jStr("deducts_on_invoice").toDoubleOrNull()?.toInt(),
                            lines = d.jObjs("lines").map { l ->
                                val pending = l.jNum("pending")
                                DeliveryLine(
                                    uid = l.jStr("sales_line_uid"),
                                    label = productLabel(l),
                                    ordered = l.jNum("ordered"),
                                    delivered = l.jNum("delivered"),
                                    pending = pending,
                                    qty = if (pending > 0) trimZero(pending) else "0",
                                )
                            },
                            idempotencyKey = UUID.randomUUID().toString(),
                            message = "Invoice loaded",
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingInvoice = false, lines = emptyList(), message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** "code - name", the code only when there is one. */
    private fun productLabel(l: JsonObject): String {
        val code = l.jStr("product_code").ifBlank { l.jPath("product.code") }.ifBlank { l.jStr("code") }
        val name = l.jStr("product_name").ifBlank { l.jPath("product.name") }
        return listOf(code, name).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { l.jStr("product_id").ifBlank { "-" } }
    }

    private fun trimZero(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun save() {
        val s = _uiState.value
        if (s.invoiceId.isBlank()) {
            _uiState.update { it.copy(message = "Load an invoice first") }
            return
        }
        val lines = JsonArray()
        s.lines.filter { (it.qty.toDoubleOrNull() ?: 0.0) > 0 }.forEach { l ->
            lines.add(JsonObject().apply {
                addProperty("sales_line_uid", l.uid)
                addProperty("quantity", l.qty.toDouble())
            })
        }
        if (lines.size() == 0) {
            _uiState.update { it.copy(message = "Enter a quantity to deliver") }
            return
        }
        val h = s.header
        val payload = JsonObject().apply {
            addProperty("mtmId", s.invoiceId)
            addProperty("idempotency_key", s.idempotencyKey)
            addProperty("challan_no", h.challanNo)
            addProperty("vehicle_no", h.vehicleNo)
            addProperty("driver_name", h.driverName)
            addProperty("driver_mobile", h.driverMobile)
            addProperty("receiver_name", h.receiverName)
            addProperty("receiver_mobile", h.receiverMobile)
            addProperty("notes", h.notes)
            add("lines", lines)
        }
        send(payload, allowNegative = false)
    }

    /** Sent again, with allow_negative, once the dispatcher says to go on. */
    fun continueDespiteShortage() {
        val payload = _uiState.value.pendingPayload ?: return
        _uiState.update { it.copy(stockWarning = null) }
        send(payload, allowNegative = true)
    }

    fun dismissShortage() = _uiState.update { it.copy(stockWarning = null, pendingPayload = null) }

    private fun send(payload: JsonObject, allowNegative: Boolean) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        val body = payload.deepCopy().apply { if (allowNegative) addProperty("allow_negative", true) }
        viewModelScope.launch {
            when (val r = api.postEnvelope("delivery/store", body)) {
                is Resource.Success -> {
                    val obj = r.data
                    val shortage = parseStockShortage(obj)
                    val ok = obj.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                    when {
                        // Not enough stock on the book: nothing saved, nothing wrong.
                        shortage != null -> _uiState.update {
                            it.copy(isSaving = false, stockWarning = shortage, pendingPayload = payload)
                        }
                        // Judged by what came back, not by whether a message did.
                        !ok -> _uiState.update {
                            it.copy(isSaving = false, message = WebApi.messageOf(obj).ifBlank { "Failed to save the delivery" })
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    message = obj.jStr("message").ifBlank { "Delivery saved" },
                                    searchInvoice = "", invoiceId = "", lines = emptyList(), returned = 0.0,
                                    deductsOnInvoice = null, header = DeliveryHeader(), idempotencyKey = "",
                                    pendingPayload = null,
                                )
                            }
                            loadList()
                        }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun loadList() {
        _uiState.update { it.copy(isLoadingList = true) }
        viewModelScope.launch {
            when (val r = api.post("delivery/list")) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoadingList = false,
                        list = r.data.jRows().map { o ->
                            DeliveryListRow(
                                idHashed = o.jStr("id_hashed"),
                                vrNo = o.jPath("main_transaction.vr_no"),
                                date = o.jStr("delivery_date"),
                                customer = o.jPath("customer.name"),
                                challanNo = o.jStr("challan_no"),
                                vehicleNo = o.jStr("vehicle_no"),
                                active = o.jStr("status") == "1",
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingList = false, message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askCancel(row: DeliveryListRow) = _uiState.update { it.copy(confirmCancel = row) }
    fun dismissCancel() = _uiState.update { it.copy(confirmCancel = null) }

    fun confirmCancel() {
        val row = _uiState.value.confirmCancel ?: return
        _uiState.update { it.copy(confirmCancel = null) }
        viewModelScope.launch {
            when (val r = api.post("delivery/cancel", JsonObject().apply { addProperty("id", row.idHashed) })) {
                is Resource.Success -> {
                    _uiState.update { it.copy(message = "Delivery cancelled") }
                    loadList()
                }
                is Resource.Error -> _uiState.update { it.copy(message = r.message, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val settings = ServiceLocator.provideSessionManager(app).state.value.settings
                DeliveryViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    canCreate = Permissions.has(settings?.permissions, "delivery.create"),
                    canCancel = Permissions.has(settings?.permissions, "delivery.cancel"),
                    trxDate = settings?.transactionDate.orEmpty(),
                )
            }
        }
    }
}

@Composable
fun DeliveryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    invoiceNo: String? = null,
    viewModel: DeliveryViewModel = viewModel(factory = DeliveryViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    LaunchedEffect(invoiceNo) { viewModel.autoLoad(invoiceNo) }

    AuthenticatedShell(title = "Delivery", currentRoute = Routes.INVOICES, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canCreate) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            AppTextField(
                                value = state.searchInvoice,
                                onValueChange = viewModel::onSearch,
                                label = "Search invoice number",
                                caption = "Invoice No.",
                                imeAction = ImeAction.Search,
                                keyboardActions = KeyboardActions(onSearch = { viewModel.load() }),
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryButton(text = "Load", onClick = viewModel::load, isLoading = state.isLoadingInvoice)
                        }
                    }
                    if (state.lines.isNotEmpty() && state.deductsOnInvoice == 1) {
                        item {
                            Text(
                                "This invoice already took its stock on the invoice date, so there is nothing to deliver. " +
                                    "Write the invoice with Deduct stock on delivery turned on to send it by delivery instead.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.appColors.warning, MaterialTheme.shapes.small)
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.warning,
                            )
                        }
                    }
                    if (state.lines.isNotEmpty() && state.deductsOnInvoice != 1) {
                        items(state.lines, key = { it.uid }) { line -> PendingLineCard(line, state.lines.indexOf(line) + 1, viewModel::onQty) }
                        if (state.returned > 0) {
                            item {
                                Text(
                                    "Returned against this invoice: ${kitMoney(state.returned)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                            }
                        }
                        item { DeliveryHeaderFields(state.trxDate, state.header, viewModel::onHeader) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total going out: ${kitMoney(state.totalGoingOut)}", fontWeight = AppFontWeight.SemiBold)
                                PrimaryButton(
                                    text = if (state.isSaving) "Saving..." else "Save Delivery",
                                    onClick = viewModel::save,
                                    isLoading = state.isSaving,
                                    enabled = !state.isSaving,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "RECENT DELIVERIES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                when {
                    state.isLoadingList && state.list.isEmpty() -> item { ReportLoading() }
                    state.list.isEmpty() -> item { ReportNote("No deliveries yet.") }
                    else -> items(state.list, key = { it.idHashed.ifBlank { it.vrNo } }) { row ->
                        DeliveryListCard(row, state.canCancel, viewModel::askCancel)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.stockWarning?.let { warning ->
        StockShortageDialog(
            warning = warning,
            action = "deliver",
            blockedAction = "delivering",
            onCancel = viewModel::dismissShortage,
            onContinue = viewModel::continueDespiteShortage,
        )
    }
    state.confirmCancel?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Cancel delivery?") },
            text = { Text("Cancel this delivery? The stock will go back to the godown.") },
            confirmButton = { LinkButton(text = "Cancel Delivery", onClick = viewModel::confirmCancel, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Keep", onClick = viewModel::dismissCancel) },
        )
    }
}

@Composable
private fun PendingLineCard(line: DeliveryLine, sl: Int, onQty: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$sl. ${line.label}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ordered: ${kitMoney(line.ordered)}", style = MaterialTheme.typography.labelSmall)
                Text("Delivered: ${kitMoney(line.delivered)}", style = MaterialTheme.typography.labelSmall)
                Text("Pending: ${kitMoney(line.pending)}", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold)
            }
            AppTextField(
                value = line.qty,
                onValueChange = { onQty(line.uid, it) },
                label = "0",
                caption = "Deliver Now",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeliveryHeaderFields(trxDate: String, h: DeliveryHeader, onChange: (DeliveryHeader) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Shown, not chosen: the server dates a delivery itself.
        AppTextField(value = trxDate, onValueChange = {}, label = "", caption = "Delivery Date", enabled = false, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(h.challanNo, { onChange(h.copy(challanNo = it)) }, label = "Challan No", caption = "Challan No", modifier = Modifier.weight(1f))
            AppTextField(h.vehicleNo, { onChange(h.copy(vehicleNo = it)) }, label = "Vehicle No", caption = "Vehicle No", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(h.driverName, { onChange(h.copy(driverName = it)) }, label = "Driver Name", caption = "Driver Name", modifier = Modifier.weight(1f))
            AppTextField(h.driverMobile, { onChange(h.copy(driverMobile = it)) }, label = "Driver Mobile", caption = "Driver Mobile", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(h.receiverName, { onChange(h.copy(receiverName = it)) }, label = "Receiver Name", caption = "Receiver Name", modifier = Modifier.weight(1f))
            AppTextField(h.receiverMobile, { onChange(h.copy(receiverMobile = it)) }, label = "Receiver Mobile", caption = "Receiver Mobile", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        AppTextField(h.notes, { onChange(h.copy(notes = it)) }, label = "Notes", caption = "Notes", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeliveryListCard(row: DeliveryListRow, canCancel: Boolean, onCancel: (DeliveryListRow) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.vrNo.ifBlank { "-" }, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text(
                    listOf(row.date, row.customer).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textMuted,
                )
                Text(
                    "Challan: ${row.challanNo.ifBlank { "-" }} · Vehicle: ${row.vehicleNo.ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textMuted,
                )
                Text(
                    if (row.active) "Active" else "Cancelled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.active) MaterialTheme.appColors.success else MaterialTheme.appColors.danger,
                )
            }
            if (canCancel && row.active) {
                LinkButton(text = "Cancel", onClick = { onCancel(row) }, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
