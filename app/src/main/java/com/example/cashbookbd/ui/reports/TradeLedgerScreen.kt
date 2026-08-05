package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.TradeLedgerKind
import com.example.cashbookbd.data.repository.TradeLedgerReport
import com.example.cashbookbd.data.repository.TradeLedgerRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.report.matchHighlightRule
import com.example.cashbookbd.session.Settings
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.HighlightedText
import com.example.cashbookbd.ui.components.highlightBorderColor
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.VoucherAttachmentsCell
import com.example.cashbookbd.ui.components.VoucherImageViewerDialog
import com.example.cashbookbd.ui.components.rememberHighlightRules
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The web table's columns, sized for a phone's horizontal scroll.
private val COL_SL = 44.dp
private val COL_CHALLAN = 112.dp
private val COL_PRODUCT = 190.dp
private val COL_VEHICLE = 130.dp
private val COL_QTY = 92.dp
private val COL_RATE = 84.dp
private val COL_TOTAL = 92.dp
private val COL_DISCOUNT = 88.dp
private val COL_PAYMENT = 92.dp
private val COL_BALANCE = 92.dp
private val COL_VOUCHER = 96.dp
private val COL_ACTION = 84.dp

data class TradeLedgerUiState(
    val kind: TradeLedgerKind = TradeLedgerKind.PURCHASE,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    /** Optional: blank runs branch-wide, the 2026-07-30 web change. */
    val account: LedgerDropdownItem? = null,
    val product: SelectorOption? = null,
    val search: String = "",
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val report: TradeLedgerReport? = null,

    // The web ACTION column's two real actions, behind their own permissions.
    val canApprove: Boolean = false,
    val canRemoveApproval: Boolean = false,
    /** The row awaiting a confirm: the voucher and which action. */
    val pendingApprove: TradeLedgerRow? = null,
    val pendingRemoveApproval: TradeLedgerRow? = null,
    val busyVoucherId: Long? = null,
    val actionMessage: String? = null,

    val sessionExpired: Boolean = false,
) {
    val showActionColumn: Boolean get() = canApprove || canRemoveApproval

    val title: String
        get() = if (kind == TradeLedgerKind.PURCHASE) "Purchase Ledger" else "Sales Ledger"

    /** The web's one wording difference in the money columns. */
    val paymentHeader: String
        get() = if (kind == TradeLedgerKind.PURCHASE) "PAYMENT" else "RECEIVED"
}

class TradeLedgerViewModel(
    private val kind: TradeLedgerKind,
    private val repository: com.example.cashbookbd.data.repository.TradeLedgerRepository,
    private val reportRepository: ReportRepository,
    private val ledgerRepository: LedgerRepository,
    private val selectorRepository: SelectorRepository,
    val settings: Settings?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TradeLedgerUiState(
            kind = kind,
            canApprove = com.example.cashbookbd.session.Permissions.has(
                settings?.permissions, "cashbook.approved",
            ),
            canRemoveApproval = com.example.cashbookbd.session.Permissions.has(
                settings?.permissions, "remove.approval",
            ),
        )
    )
    val uiState: StateFlow<TradeLedgerUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    val trDate = result.data.transactionDate
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        // The user's own branch first, like the web default.
                        selectedBranch = state.selectedBranch
                            ?: result.data.branches.firstOrNull { it.id == settings?.branchId }
                            ?: result.data.branches.firstOrNull(),
                        startDate = trDate ?: state.startDate,
                        endDate = trDate ?: state.endDate,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(option: SelectorOption) {
        val branch = _uiState.value.branches.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun onAccountSelected(account: LedgerDropdownItem?) = _uiState.update { it.copy(account = account) }
    fun onProductSelected(option: SelectorOption) = _uiState.update { it.copy(product = option) }
    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    /** The web's Reset clears only the two optional pickers — not dates/search. */
    fun reset() = _uiState.update { it.copy(account = null, product = null) }

    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "3")

    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        selectorRepository.fetch(ReportSelectorSource.PRODUCT, query)

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetch(
                kind = kind,
                branchId = branch.id.toString(),
                ledgerId = state.account?.id?.toString(),
                productId = state.product?.id,
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
                search = state.search,
                groupByCategory = settings?.stockReportTypeGrouped == true,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Row actions (the web ACTION column's approve pair) ---------------

    fun requestApprove(row: TradeLedgerRow) = _uiState.update { it.copy(pendingApprove = row) }
    fun requestRemoveApproval(row: TradeLedgerRow) =
        _uiState.update { it.copy(pendingRemoveApproval = row) }

    fun cancelAction() =
        _uiState.update { it.copy(pendingApprove = null, pendingRemoveApproval = null) }

    /** Approval locks the voucher against editing — reached only via confirm. */
    fun confirmApprove() {
        val row = _uiState.value.pendingApprove ?: return
        runRowAction(row) { repository.approveVoucher(row.voucherId) }
    }

    fun confirmRemoveApproval() {
        val row = _uiState.value.pendingRemoveApproval ?: return
        runRowAction(row) { repository.removeApproval(row.voucherId, row.challanNo) }
    }

    private fun runRowAction(
        row: TradeLedgerRow,
        action: suspend () -> Resource<String>,
    ) {
        if (_uiState.value.busyVoucherId != null) return
        _uiState.update {
            it.copy(pendingApprove = null, pendingRemoveApproval = null, busyVoucherId = row.voucherId)
        }
        viewModelScope.launch {
            val result = action()
            _uiState.update {
                it.copy(
                    busyVoucherId = null,
                    actionMessage = when (result) {
                        is Resource.Success -> result.data
                        is Resource.Error -> result.message
                        Resource.Loading -> null
                    },
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
            // Whatever the outcome, re-fetch so the icons say what is now true.
            apply()
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: TradeLedgerKind) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                TradeLedgerViewModel(
                    kind = kind,
                    repository = ServiceLocator.provideTradeLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                    settings = ServiceLocator.provideSessionManager(appContext).state.value.settings,
                )
            }
        }
    }
}

/**
 * The web's Purchase/Sales Ledger verbatim: one row per voucher, the product
 * lines stacked inside it with their quantity/rate/total beside them, the
 * discount/payment/balance once per voucher, and the voucher-image chips at
 * the end. The web's Action icons (approve/print/edit) ride flows this app
 * keeps elsewhere, so the column is not drawn.
 */
@Composable
fun TradeLedgerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: TradeLedgerKind,
    modifier: Modifier = Modifier,
    viewModel: TradeLedgerViewModel = viewModel(
        key = "trade-ledger-$kind",
        factory = TradeLedgerViewModel.provideFactory(LocalContext.current, kind),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val settings = viewModel.settings

    var viewing by remember { mutableStateOf<VoucherAttachment?>(null) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }

    AuthenticatedShell(
        title = state.title,
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSelectDropdown(
                        label = "Select Branch",
                        options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                        selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                        onSelected = viewModel::onBranchSelected,
                        placeholder = if (state.isBranchesLoading) "Loading…" else "",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SearchableLedgerDropdown(
                        selectedLedger = state.account,
                        onLedgerSelected = viewModel::onAccountSelected,
                        searchLedgers = viewModel::searchAccounts,
                        label = "Select Account (optional)",
                    )
                    SearchableSelectDropdown(
                        selected = state.product,
                        onSelected = viewModel::onProductSelected,
                        search = viewModel::searchProducts,
                        label = "Select Product (optional)",
                        emptyText = "No product found",
                    )
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        label = "Search (order no or exact rate)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = "Start Date",
                            value = state.startDate.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickTradeDate(context, state.startDate, viewModel::onStartDate) },
                        )
                        PickerField(
                            label = "End Date",
                            value = state.endDate.toDisplay(),
                            trailingIcon = Icons.Filled.DateRange,
                            modifier = Modifier.weight(1f),
                            onClick = { pickTradeDate(context, state.endDate, viewModel::onEndDate) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = "Apply",
                            onClick = viewModel::apply,
                            isLoading = state.isLoading,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Reset",
                            onClick = viewModel::reset,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                when {
                    state.error != null -> Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                        LinkButton(text = "Retry", onClick = viewModel::apply)
                    }

                    !state.hasApplied -> Box(
                        Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Choose your filters, then tap Apply.",
                            color = MaterialTheme.appColors.textOnScreenMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    state.isLoading && state.report == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.report != null -> TradeLedgerTable(
                        state = state,
                        report = state.report!!,
                        settings = settings,
                        onOpenAttachment = { viewing = it },
                        onApprove = viewModel::requestApprove,
                        onRemoveApproval = viewModel::requestRemoveApproval,
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    viewing?.let { attachment ->
        VoucherImageViewerDialog(
            attachment = attachment,
            isLocalEnv = settings?.isLocalEnv == true,
            onDismiss = { viewing = null },
        )
    }

    // Approval locks the voucher against editing, so both directions ask
    // first and name the voucher, like the web's inline Yes/No.
    state.pendingApprove?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelAction,
            title = { Text("Approve voucher?") },
            text = { Text("Approve ${row.challanNo}? An approved voucher can no longer be edited.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::confirmApprove) { Text("Approve") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::cancelAction) { Text("Cancel") }
            },
        )
    }
    state.pendingRemoveApproval?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelAction,
            title = { Text("Remove approval?") },
            text = { Text("Withdraw the approval on ${row.challanNo}? The voucher becomes editable again.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::confirmRemoveApproval) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::cancelAction) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TradeLedgerTable(
    state: TradeLedgerUiState,
    report: TradeLedgerReport,
    settings: Settings?,
    onOpenAttachment: (VoucherAttachment) -> Unit,
    onApprove: (TradeLedgerRow) -> Unit,
    onRemoveApproval: (TradeLedgerRow) -> Unit,
) {
    val rules = rememberHighlightRules()
    val showVoucher = settings?.showVoucherImage == true
    val isLocalEnv = settings?.isLocalEnv == true
    val green = MaterialTheme.appColors.success
    val red = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val columns = buildList {
        add(ReportColumn<TradeLedgerRow>("SL. NO", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { r, _ ->
            cellText(r.slNumber.toString())
        })
        add(ReportColumn<TradeLedgerRow>("CHAL. NO. & DATE", ReportColWidth.Fixed(COL_CHALLAN)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(r.challanNo, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.challanDate, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                }
            }
        })
        add(ReportColumn<TradeLedgerRow>("PRODUCT & DETAILS", ReportColWidth.Fixed(COL_PRODUCT)) { r, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (r.partyName.isNotBlank()) {
                        Text(
                            text = r.partyName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = AppFontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    r.lines.forEach { line ->
                        Text(
                            text = line.productLabel.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (r.note.isNotBlank()) {
                        // The voucher's note, boxed in a highlight rule's colour
                        // when one matches — as on the web.
                        HighlightedText(
                            text = r.note,
                            borderColor = highlightBorderColor(matchHighlightRule(r.note, rules)),
                            style = MaterialTheme.typography.labelSmall,
                            color = green,
                            maxLines = 2,
                        )
                    }
                }
            }
        })
        add(ReportColumn<TradeLedgerRow>("VEHICLE & ORDER", ReportColWidth.Fixed(COL_VEHICLE)) { r, _ ->
            if (r.vehicleNo.isBlank() && r.orderNumber.isBlank() && r.deliveryLocation.isBlank()) {
                ReportTableCell.Empty
            } else {
                ReportTableCell.Slot {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        if (r.vehicleNo.isNotBlank()) {
                            Text(r.vehicleNo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        if (r.orderNumber.isNotBlank()) {
                            Text(r.orderNumber, style = MaterialTheme.typography.labelSmall, color = green, maxLines = 1)
                        }
                        if (r.deliveryLocation.isNotBlank()) {
                            Text(r.deliveryLocation, style = MaterialTheme.typography.labelSmall, color = green, maxLines = 1)
                        }
                    }
                }
            }
        })
        add(ReportColumn<TradeLedgerRow>("QUANTITY", ReportColWidth.Fixed(COL_QTY), TextAlign.End) { r, _ ->
            stackedCell(r) { line ->
                lineText("${money(line.quantity)} ${line.unitName}".trim())
            }
        })
        add(ReportColumn<TradeLedgerRow>("RATE", ReportColWidth.Fixed(COL_RATE), TextAlign.End) { r, _ ->
            stackedCell(r) { line ->
                // A rate-and-total pair both at zero reads red, as on the web.
                lineText(money(line.rate), color = if (line.rate == 0.0 && line.total == 0.0) red else null)
            }
        })
        add(ReportColumn<TradeLedgerRow>("TOTAL", ReportColWidth.Fixed(COL_TOTAL), TextAlign.End) { r, _ ->
            stackedCell(r) { line ->
                lineText(money(line.total), color = if (line.rate == 0.0 && line.total == 0.0) red else null)
            }
        })
        add(ReportColumn<TradeLedgerRow>("DISCOUNT", ReportColWidth.Fixed(COL_DISCOUNT), TextAlign.End) { r, _ ->
            // Purchase leaves an absent discount blank; Sales dashes a zero.
            when {
                r.discount == null -> ReportTableCell.Empty
                else -> cellText(money(r.discount!!))
            }
        })
        add(ReportColumn<TradeLedgerRow>(state.paymentHeader, ReportColWidth.Fixed(COL_PAYMENT), TextAlign.End) { r, _ ->
            cellText(money(r.payment))
        })
        add(ReportColumn<TradeLedgerRow>("BALANCE", ReportColWidth.Fixed(COL_BALANCE), TextAlign.End) { r, _ ->
            cellText(money(r.balance))
        })
        if (showVoucher) {
            add(ReportColumn<TradeLedgerRow>("VOUCHER", ReportColWidth.Fixed(COL_VOUCHER), TextAlign.Center) { r, _ ->
                val attachments = VoucherImages.attachments(
                    raw = r.voucherImage,
                    branchPad = r.branchId.padStart(4, '0').takeIf { r.branchId.isNotBlank() },
                )
                if (attachments.isEmpty()) {
                    ReportTableCell.Empty
                } else {
                    ReportTableCell.Slot {
                        VoucherAttachmentsCell(
                            attachments = attachments,
                            isLocalEnv = isLocalEnv,
                            onOpen = onOpenAttachment,
                        )
                    }
                }
            })
        }
        if (state.showActionColumn) {
            add(ReportColumn<TradeLedgerRow>("ACTION", ReportColWidth.Fixed(COL_ACTION), TextAlign.Center) { r, _ ->
                if (r.challanNo.isBlank() || r.voucherId <= 0L) {
                    ReportTableCell.Empty
                } else {
                    ReportTableCell.Slot {
                        ActionIcons(
                            row = r,
                            canApprove = state.canApprove,
                            canRemoveApproval = state.canRemoveApproval,
                            isBusy = state.busyVoucherId == r.voucherId,
                            onApprove = onApprove,
                            onRemoveApproval = onRemoveApproval,
                        )
                    }
                }
            })
        }
    }

    val totals = report.totals
    val footer = buildList {
        add(ReportFooterCell(cellText("Total", bold = true), colSpan = 4))
        add(ReportFooterCell(cellText(money(totals.quantity), bold = true, align = TextAlign.End)))
        add(ReportFooterCell(ReportTableCell.Empty))
        add(ReportFooterCell(cellText(money(totals.total), bold = true, align = TextAlign.End)))
        add(ReportFooterCell(cellText(money(totals.discount), bold = true, align = TextAlign.End)))
        add(ReportFooterCell(cellText(money(totals.payment), bold = true, align = TextAlign.End)))
        add(ReportFooterCell(cellText(money(totals.balance), bold = true, align = TextAlign.End)))
        if (showVoucher) add(ReportFooterCell(ReportTableCell.Empty))
        if (state.showActionColumn) add(ReportFooterCell(ReportTableCell.Empty))
    }

    ReportTable(
        columns = columns,
        data = report.rows,
        footerRows = if (report.rows.isEmpty()) emptyList() else listOf(footer),
        noDataMessage = "No data found",
    )
}

/**
 * The web ACTION column's two live actions: approve (red tick, green circle
 * once approved) and remove-approval (amber ✕, only on approved rows). The
 * web's print and edit icons lead into the voucher print/edit flows, which
 * this app does not carry — they stay on the web.
 */
@Composable
private fun ActionIcons(
    row: TradeLedgerRow,
    canApprove: Boolean,
    canRemoveApproval: Boolean,
    isBusy: Boolean,
    onApprove: (TradeLedgerRow) -> Unit,
    onRemoveApproval: (TradeLedgerRow) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            return@Row
        }
        if (canApprove) {
            if (row.isApproved) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Approved",
                    tint = MaterialTheme.appColors.success,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Approve voucher",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onApprove(row) },
                )
            }
        }
        if (canRemoveApproval && row.isApproved) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove approval",
                tint = MaterialTheme.accents.amber,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onRemoveApproval(row) },
            )
        }
    }
}

/** A stacked numeric cell: one line per product detail, end-aligned. */
private fun stackedCell(
    row: TradeLedgerRow,
    line: @Composable (com.example.cashbookbd.data.repository.TradeLedgerLine) -> Unit,
): ReportTableCell = if (row.lines.isEmpty()) {
    ReportTableCell.Empty
} else {
    ReportTableCell.Slot {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            row.lines.forEach { line(it) }
        }
    }
}

@Composable
private fun lineText(text: String, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color ?: androidx.compose.ui.graphics.Color.Unspecified,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The web's thousandSeparator: separators from AmountFormat, zero as "-". */
private fun money(value: Double): String =
    if (value == 0.0) "-" else AmountFormat.format(value)

private fun pickTradeDate(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
