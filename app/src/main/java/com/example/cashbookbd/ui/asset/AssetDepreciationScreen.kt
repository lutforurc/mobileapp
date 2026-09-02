package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetDepreciationPlan
import com.example.cashbookbd.data.repository.AssetPlanRow
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.BrandPill
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDepreciationUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val asAt: String = todayApi(),
    val isLoading: Boolean = true,
    val isPosting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val plan: AssetDepreciationPlan? = null,
    val confirmingCharge: Boolean = false,
    val confirmingUndo: Boolean = false,
    val sessionExpired: Boolean = false,
)

class AssetDepreciationViewModel(
    private val repository: AssetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDepreciationUiState())
    val uiState: StateFlow<AssetDepreciationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isBranchesLoading = true) }
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
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
            load()
        }
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchDepreciationPlan(state.selectedBranch?.id, state.asAt)
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, plan = result.data)
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

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch) }
        load()
    }

    fun onAsAt(value: String) {
        _uiState.update { it.copy(asAt = value) }
        load()
    }

    fun askCharge() = _uiState.update { it.copy(confirmingCharge = true) }

    fun askUndo() = _uiState.update { it.copy(confirmingUndo = true) }

    fun cancel() = _uiState.update { it.copy(confirmingCharge = false, confirmingUndo = false) }

    /** Writes the voucher. Only from the confirm dialog, never on its own. */
    fun charge() {
        val state = _uiState.value
        _uiState.update { it.copy(isPosting = true, confirmingCharge = false) }
        viewModelScope.launch {
            val result = repository.runDepreciation(
                branchId = state.selectedBranch?.id,
                yearEnding = state.plan?.yearEnding ?: state.asAt,
            )
            finish(result)
        }
    }

    /** A second journal reversing the first. The books keep both. */
    fun undo() {
        val runId = _uiState.value.plan?.run?.id ?: return
        _uiState.update { it.copy(isPosting = true, confirmingUndo = false) }
        viewModelScope.launch { finish(repository.reverseDepreciation(runId)) }
    }

    private fun finish(result: Resource<String>) {
        when (result) {
            is Resource.Success -> {
                _uiState.update { it.copy(isPosting = false, message = result.data) }
                load()
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isPosting = false,
                    // Usually the refusal: the voucher has been approved, so it
                    // is somebody's signed decision. The server says exactly that.
                    error = result.message,
                    sessionExpired = it.sessionExpired || result.isUnauthorized,
                )
            }
            Resource.Loading -> Unit
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetDepreciationViewModel(
                    repository = AssetRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The yearly charge — shown first, then posted.
 *
 * ⚠️ SHOWN FIRST, ALWAYS. Charging writes a voucher into the books, so what it
 * would do is put in front of a person before it does it, in the server's own
 * arithmetic. One property, one year, one voucher, with a line per category.
 *
 * ⚠️ A YEAR ALREADY CHARGED IS DRAWN AS IT WAS CHARGED — the rate, the days and
 * the amount that were stored. A rate edited since must not restate a year the
 * accounts have already closed.
 */
@Composable
fun AssetDepreciationScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetDepreciationViewModel = viewModel(
        factory = AssetDepreciationViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    val plan = state.plan
    val chargeable = plan?.chargeable ?: 0.0
    val done = plan?.run != null
    // ⚠️ A DEAD BUTTON HAS TO SAY WHY IT IS DEAD. "Nothing to charge" over a
    // register full of assets reads as "no depreciation is due" — which is the
    // one thing it does not mean when every category is waiting for its heads.
    val stuck = chargeable == 0.0 && (plan?.blocked?.isNotEmpty() == true)

    AuthenticatedShell(
        title = "Depreciation",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssetBranchField(
                branches = state.branches,
                selected = state.selectedBranch,
                isLoading = state.isBranchesLoading,
                onSelected = viewModel::onBranch,
                modifier = Modifier.fillMaxWidth(),
            )
            AssetDateField(
                label = "Year ending on or after",
                value = state.asAt,
                onPicked = viewModel::onAsAt,
                modifier = Modifier.fillMaxWidth(),
            )

            if (plan != null) {
                Text(
                    text = "Charging the year ending ${onTheDay(plan.yearEnding)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            state.message?.let {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AssetNotice(text = it, tone = AssetTone.Success, modifier = Modifier.weight(1f))
                    LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                }
            }
            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            when {
                state.isLoading && plan == null -> AssetLoading()

                plan == null -> Unit

                else -> {
                    // ⚠️ Drawn as done rather than hidden. A year already charged
                    // is the answer to "did we do this?", and a missing button is
                    // not an answer.
                    if (done) {
                        val run = plan.run!!
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            BrandPill(
                                text = "Already charged — ${run.assetCount} asset(s), " +
                                    AmountFormat.format(run.totalAmount),
                                compact = true,
                            )
                        }
                        SecondaryButton(
                            text = "Undo this year",
                            onClick = viewModel::askUndo,
                            isLoading = state.isPosting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PrimaryButton(
                            text = when {
                                chargeable > 0 -> "Charge ${AmountFormat.format(chargeable)}"
                                stuck -> "Waiting for the ledger heads"
                                else -> "Nothing to charge"
                            },
                            onClick = viewModel::askCharge,
                            enabled = chargeable > 0,
                            isLoading = state.isPosting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ⚠️ The blockage is named, with the category that caused it.
                    // A run that quietly skipped those assets would leave a
                    // schedule short by exactly the assets nobody was told about.
                    if (plan.blocked.isNotEmpty()) {
                        AssetNotice(
                            text = "Left out: ${plan.blocked.joinToString(", ")} — these " +
                                "categories have no ledger heads yet, so their assets cannot be " +
                                "charged. Choose them on the Categories screen, then come back.",
                            tone = AssetTone.Warning,
                        )
                    }

                    if (plan.totals.isNotEmpty()) {
                        AssetPanel(title = "What the voucher will say") {
                            plan.totals.forEach { one ->
                                AssetLine(
                                    label = one.categoryName,
                                    sublabel = "${one.assets} asset(s)",
                                    value = AmountFormat.format(one.amount),
                                )
                            }
                            AssetLine(
                                label = "Total",
                                value = AmountFormat.format(chargeable),
                                strong = true,
                                divider = false,
                            )
                            Text(
                                text = "Debit each category's depreciation head, credit its " +
                                    "accumulated depreciation — one journal for this property, " +
                                    "dated ${onTheDay(plan.yearEnding)}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }

                    ReportTable(
                        columns = planColumns(),
                        data = plan.rows,
                        footerRows = planFooter(plan),
                        noDataMessage = "No assets in this property's register for that year.",
                        scrollable = false,
                    )

                    // The years already charged here, so somebody can look one up
                    // rather than guessing which Junes have been done.
                    if (plan.history.isNotEmpty()) {
                        AssetPanel(title = "Years already charged") {
                            plan.history.forEach { one ->
                                AssetLine(
                                    label = onTheDay(one.yearEnding),
                                    sublabel = "${one.assetCount} asset(s)",
                                    value = AmountFormat.format(one.totalAmount),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.confirmingCharge && plan != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text("Charge this year's depreciation?") },
            text = {
                Text(
                    "One journal will be written for this property, dated " +
                        "${onTheDay(plan.yearEnding)}: ${AmountFormat.format(chargeable)} across " +
                        "${plan.totals.size} category(s). It can be undone afterwards, which " +
                        "writes a second journal reversing it.",
                )
            },
            confirmButton = {
                PrimaryButton(text = "Charge it", onClick = viewModel::charge, compact = true)
            },
            dismissButton = { LinkButton(text = "Not yet", onClick = viewModel::cancel) },
        )
    }

    if (state.confirmingUndo && plan?.run != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text("Undo this year's depreciation?") },
            text = {
                Text(
                    "A second journal will be written, reversing every line of the first — the " +
                        "books keep both, so what happened can be shown. The " +
                        "${plan.run!!.assetCount} asset(s) go back to what they were worth " +
                        "before, and the year can be charged again.",
                )
            },
            confirmButton = {
                PrimaryButton(text = "Undo it", onClick = viewModel::undo, compact = true)
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancel) },
        )
    }
}

@Composable
private fun planColumns(): List<ReportColumn<AssetPlanRow>> {
    val muted = MaterialTheme.appColors.textMuted
    val info = MaterialTheme.appColors.info
    return listOf(
        ReportColumn("ASSET", ReportColWidth.Fixed(170.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(
                        text = "${row.code} · ${row.categoryName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 2,
                    )
                }
            }
        },
        ReportColumn("WORTH AT 1 JULY", ReportColWidth.Fixed(120.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.format(row.openingWdv))
        },
        ReportColumn("RATE", ReportColWidth.Fixed(70.dp), TextAlign.End) { row, _ ->
            cellText(percentText(row.rate))
        },
        // ⚠️ Short years are called out. 61 days beside 365 is the difference
        // between an asset bought in May and one owned all year.
        ReportColumn("DAYS", ReportColWidth.Fixed(70.dp), TextAlign.End) { row, _ ->
            cellText(
                text = row.days.toString(),
                color = if (row.days == 365) muted else info,
                bold = row.days != 365,
            )
        },
        ReportColumn("DEPRECIATION", ReportColWidth.Fixed(120.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.amount), bold = row.amount != 0.0)
        },
        ReportColumn("WORTH AFTER", ReportColWidth.Fixed(120.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.format(row.closingWdv))
        },
        ReportColumn("", ReportColWidth.Fixed(84.dp), TextAlign.Center) { row, _ ->
            if (row.charged) {
                ReportTableCell.Slot { BrandPill(text = "charged", compact = true) }
            } else {
                ReportTableCell.Empty
            }
        },
    )
}

/**
 * ⚠️ THE TABLE'S TOTAL IS NOT THE BUTTON'S, and it must not pretend to be. The
 * table lists every asset — including the ones already charged this year and the
 * ones held up by a category with no heads — while the button charges only what
 * is left. Two totals that quietly disagree is the worst thing a page like this
 * can do, so the difference is said in words rather than hidden.
 */
@Composable
private fun planFooter(plan: AssetDepreciationPlan): List<List<ReportFooterCell>> {
    if (plan.rows.isEmpty()) return emptyList()
    val shown = plan.rows.sumOf { it.amount }
    val chargeable = plan.chargeable
    val muted = MaterialTheme.appColors.textMuted
    return listOf(
        listOf(
            ReportFooterCell(cellText("Total — ${plan.rows.size} asset(s)", bold = true)),
            ReportFooterCell(
                cellText(AmountFormat.format(plan.rows.sumOf { it.openingWdv }), TextAlign.End, bold = true),
            ),
            // Rate and days are not things that add up.
            ReportFooterCell(ReportTableCell.Empty),
            ReportFooterCell(ReportTableCell.Empty),
            ReportFooterCell(
                ReportTableCell.Slot {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.End,
                    ) {
                        Text(
                            text = AmountFormat.format(shown),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (kotlin.math.abs(shown - chargeable) > 0.005) {
                            Text(
                                text = "${AmountFormat.format(chargeable)} of it left to charge",
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                maxLines = 2,
                            )
                        }
                    }
                },
            ),
            ReportFooterCell(
                cellText(AmountFormat.format(plan.rows.sumOf { it.closingWdv }), TextAlign.End, bold = true),
            ),
            ReportFooterCell(ReportTableCell.Empty),
        ),
    )
}
