package com.example.cashbookbd.ui.realestate

import com.example.cashbookbd.ui.theme.asTint
import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.RealEstateSalesRepository
import com.example.cashbookbd.data.repository.SoldUnitCustomer
import com.example.cashbookbd.data.repository.SoldUnitsReport
import com.example.cashbookbd.data.repository.SoldUnitsTotals
import com.example.cashbookbd.ui.theme.brand
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.data.repository.SaleNomineeOption
import com.example.cashbookbd.data.repository.SaleNomineePick
import com.example.cashbookbd.data.repository.SoldUnitSale
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Real Estate → Sold Units — the read-only report grouped per customer: summary
 * tiles up top, then each customer's units (their priced lines), the per-customer
 * Total/Received/Due footer, and a grand total from the backend `totals`.
 * `GET real-estate/unit-sale/sold-units`, filtered by project, building, a free
 * search, an optional date range and the "Only units having due" checkbox.
 */

private val ALL_PROJECTS = SelectorOption(id = "", label = "All Projects")
private val ALL_BUILDINGS = SelectorOption(id = "", label = "All Buildings")

data class SoldUnitsUiState(
    // Filter
    val projects: List<SelectorOption> = listOf(ALL_PROJECTS),
    val buildings: List<SelectorOption> = listOf(ALL_BUILDINGS),
    val isOptionsLoading: Boolean = false,
    val optionsError: String? = null,
    val selectedProject: SelectorOption = ALL_PROJECTS,
    val selectedBuilding: SelectorOption = ALL_BUILDINGS,
    val query: String = "",
    val dateFrom: SimpleDate? = null,
    val dateTo: SimpleDate? = null,
    val dueOnly: Boolean = false,

    // Result
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val hasApplied: Boolean = false,
    val report: SoldUnitsReport? = null,

    // Paper Generate: the sale awaiting confirmation, which paper it is about
    // (letter or booking form — issued the same way, headed the same way, so
    // they share the dialog), and the sale whose save is in flight.
    val pendingGenerate: com.example.cashbookbd.data.repository.SoldUnitSale? = null,
    val generateKind: PaperKind = PaperKind.LETTER,
    val generatingSaleId: String? = null,
    /** What the letter will be headed with — the office's to overwrite. */
    val refNo: String = "",
    val refDate: SimpleDate? = null,
    /** The suggestion this screen offered; a clerk-typed ref survives date changes. */
    val suggestedRefNo: String = "",
    val actionMessage: String? = null,

    /**
     * Withdrawing an issued paper is its own permission per paper
     * (`allotment.letter.delete` / `booking.form.delete`), granted separately
     * from issuing one. Without it the chips print and nothing more.
     */
    val canWithdrawLetter: Boolean = false,
    val canWithdrawBooking: Boolean = false,
    /** The issued copy awaiting the withdraw confirmation. */
    val pendingWithdraw: WithdrawTarget? = null,

    // The sale whose nominees are being named, and the dialog's working state.
    // Done here as well as at booking: most buyers settle on a nominee after
    // the money has been taken.
    val nomineeSale: com.example.cashbookbd.data.repository.SoldUnitSale? = null,
    val nomineeLoading: Boolean = false,
    val nomineeAvailable: List<SaleNomineeOption> = emptyList(),
    /** Nominee id → the pick as typed. Absent = not named on this sale. */
    val nomineePicks: Map<Long, SaleNomineePick> = emptyMap(),
    val nomineeSaving: Boolean = false,

    val sessionExpired: Boolean = false,
)

/** Which issued paper an action is about. The two ride separate series. */
enum class PaperKind { LETTER, BOOKING }

/** One issued copy: the sale, which paper, and its number. */
data class WithdrawTarget(
    val sale: com.example.cashbookbd.data.repository.SoldUnitSale,
    val kind: PaperKind,
    val version: Int,
)

class SoldUnitsViewModel(
    private val repository: RealEstateSalesRepository,
    private val settings: com.example.cashbookbd.session.Settings?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SoldUnitsUiState(
            canWithdrawLetter = com.example.cashbookbd.session.Permissions.has(
                settings?.permissions,
                "allotment.letter.delete",
            ),
            canWithdrawBooking = com.example.cashbookbd.session.Permissions.has(
                settings?.permissions,
                "booking.form.delete",
            ),
        )
    )
    val uiState: StateFlow<SoldUnitsUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isOptionsLoading = true, optionsError = null) }
        viewModelScope.launch {
            val projects = repository.fetchProjects()
            val buildings = repository.fetchBuildings()
            _uiState.update { state ->
                val error = (projects as? Resource.Error)?.message
                    ?: (buildings as? Resource.Error)?.message
                state.copy(
                    isOptionsLoading = false,
                    optionsError = error,
                    projects = listOf(ALL_PROJECTS) + ((projects as? Resource.Success)?.data ?: emptyList()),
                    buildings = listOf(ALL_BUILDINGS) + ((buildings as? Resource.Success)?.data ?: emptyList()),
                    sessionExpired = state.sessionExpired ||
                        (projects as? Resource.Error)?.isUnauthorized == true ||
                        (buildings as? Resource.Error)?.isUnauthorized == true,
                )
            }
        }
    }

    fun onProjectSelected(option: SelectorOption) = _uiState.update { it.copy(selectedProject = option) }

    fun onBuildingSelected(option: SelectorOption) = _uiState.update { it.copy(selectedBuilding = option) }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun onDateFromSelected(date: SimpleDate) = _uiState.update { it.copy(dateFrom = date) }

    fun onDateToSelected(date: SimpleDate) = _uiState.update { it.copy(dateTo = date) }

    fun onDueOnlyToggle(checked: Boolean) = _uiState.update { it.copy(dueOnly = checked) }

    fun search() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val result = repository.fetchSoldUnits(
                projectId = state.selectedProject.id,
                buildingId = state.selectedBuilding.id,
                dateFrom = state.dateFrom?.toApi(),
                dateTo = state.dateTo?.toApi(),
                query = state.query,
                dueOnly = state.dueOnly,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun reset() = _uiState.update {
        it.copy(
            selectedProject = ALL_PROJECTS,
            selectedBuilding = ALL_BUILDINGS,
            query = "",
            dateFrom = null,
            dateTo = null,
            dueOnly = false,
            hasApplied = false,
            report = null,
            loadError = null,
        )
    }

    // ---- Allotment letter Generate (saves an immutable L-{n} snapshot) ----

    /**
     * The reference this screen offers: the branch's prefix, the year of the
     * letter's own date, and the sale's serial padded to 3 — blank when the
     * branch has set no prefix (the server then derives one), like the web.
     */
    private fun suggestRefNo(sale: com.example.cashbookbd.data.repository.SoldUnitSale, on: SimpleDate): String {
        val prefix = settings?.letterRefPrefix?.trimEnd('/') ?: return ""
        val serial = sale.saleId.padStart(3, '0')
        return "$prefix/${on.year}/$serial"
    }

    /** Opens the confirmation with the reference/date the paper will carry. */
    fun requestGenerate(
        sale: com.example.cashbookbd.data.repository.SoldUnitSale,
        kind: PaperKind = PaperKind.LETTER,
    ) {
        // The branch's letter date when it keeps one, else today — an offer the
        // clerk sees and can overwrite, never a silent default.
        val on = settings?.letterRefDate?.let { SimpleDate.fromApi(it) } ?: SimpleDate.today()
        // The letter's prefix is not offered for a booking form: the two are
        // numbered in separate series, and BST/ALLOT on a booking form would be
        // wrong in a way nobody notices until the register is reconciled.
        val suggestion = if (kind == PaperKind.LETTER) suggestRefNo(sale, on) else ""
        _uiState.update {
            it.copy(
                pendingGenerate = sale,
                generateKind = kind,
                refDate = on,
                refNo = suggestion,
                suggestedRefNo = suggestion,
            )
        }
    }

    fun onRefNoChange(value: String) = _uiState.update { it.copy(refNo = value) }

    /** The year belongs to the reference, so it follows the date — unless the
     *  clerk has already written a reference of their own. */
    fun onRefDateSelected(date: SimpleDate) {
        val state = _uiState.value
        val sale = state.pendingGenerate ?: return
        val next = if (state.generateKind == PaperKind.LETTER) suggestRefNo(sale, date) else ""
        _uiState.update {
            it.copy(
                refDate = date,
                refNo = if (it.refNo == it.suggestedRefNo) next else it.refNo,
                suggestedRefNo = next,
            )
        }
    }

    fun cancelGenerate() = _uiState.update { it.copy(pendingGenerate = null) }

    /** Confirms the snapshot save, then silently refreshes so the chips update. */
    fun confirmGenerate() {
        val state = _uiState.value
        val sale = state.pendingGenerate ?: return
        if (state.generatingSaleId != null) return
        val refNo = state.refNo
        val refDate = state.refDate?.toApi()
        val kind = state.generateKind
        _uiState.update { it.copy(pendingGenerate = null, generatingSaleId = sale.saleId) }
        viewModelScope.launch {
            val result = when (kind) {
                PaperKind.LETTER -> repository.generateAllotmentLetter(sale.saleId, refNo, refDate)
                PaperKind.BOOKING -> repository.generateBookingForm(sale.saleId, refNo, refDate)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(actionMessage = result.data) }
                    refreshSilently()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        generatingSaleId = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Withdraw an issued paper -----------------------------------------

    fun requestWithdraw(
        sale: com.example.cashbookbd.data.repository.SoldUnitSale,
        kind: PaperKind,
        version: Int,
    ) = _uiState.update { it.copy(pendingWithdraw = WithdrawTarget(sale, kind, version)) }

    fun cancelWithdraw() = _uiState.update { it.copy(pendingWithdraw = null) }

    /**
     * Withdraws one issued copy. The number is not reused afterwards — the
     * server issues past the highest ever used — so the chips can go from
     * L-1 L-2 L-3 to L-1 L-3, never to two different papers both called L-2.
     */
    fun confirmWithdraw() {
        val state = _uiState.value
        val target = state.pendingWithdraw ?: return
        if (state.generatingSaleId != null) return
        _uiState.update { it.copy(pendingWithdraw = null, generatingSaleId = target.sale.saleId) }
        viewModelScope.launch {
            val result = when (target.kind) {
                PaperKind.LETTER -> repository.withdrawAllotmentLetter(target.sale.saleId, target.version)
                PaperKind.BOOKING -> repository.withdrawBookingForm(target.sale.saleId, target.version)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(actionMessage = result.data) }
                    refreshSilently()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        generatingSaleId = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Sale nominees ----------------------------------------------------

    /** Opens the nominee dialog and loads who is available and already named. */
    fun openNominees(sale: com.example.cashbookbd.data.repository.SoldUnitSale) {
        _uiState.update {
            it.copy(
                nomineeSale = sale,
                nomineeLoading = true,
                nomineeAvailable = emptyList(),
                nomineePicks = emptyMap(),
            )
        }
        viewModelScope.launch {
            when (val result = repository.fetchSaleNominees(sale.saleId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        nomineeLoading = false,
                        nomineeAvailable = result.data.available,
                        nomineePicks = result.data.attached.associateBy { it.nomineeId },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        nomineeSale = null,
                        nomineeLoading = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closeNominees() = _uiState.update { it.copy(nomineeSale = null) }

    fun toggleNominee(option: SaleNomineeOption) = _uiState.update { state ->
        val picks = state.nomineePicks.toMutableMap()
        if (picks.remove(option.id) == null) {
            // The buyer's own default share as the starting point; this
            // property is free to differ, and most of the time it does not.
            picks[option.id] = SaleNomineePick(
                nomineeId = option.id,
                share = option.defaultShare,
                priority = "",
            )
        }
        state.copy(nomineePicks = picks)
    }

    fun onNomineeShare(id: Long, value: String) = _uiState.update { state ->
        state.nomineePicks[id]?.let { pick ->
            state.copy(nomineePicks = state.nomineePicks + (id to pick.copy(share = value)))
        } ?: state
    }

    fun onNomineePriority(id: Long, value: String) = _uiState.update { state ->
        state.nomineePicks[id]?.let { pick ->
            state.copy(nomineePicks = state.nomineePicks + (id to pick.copy(priority = value)))
        } ?: state
    }

    /**
     * The same two rules the server enforces, caught before the round trip:
     * shares may not exceed 100, and a set where EVERY pick carries a share
     * must account for all of it. A blank share is a nomination whose division
     * is left to law, which is allowed.
     */
    fun nomineeShareProblem(): String? {
        val picked = orderedPicks()
        val shared = picked.mapNotNull { it.share.trim().toDoubleOrNull() }
        if (shared.isEmpty()) return null
        val total = shared.sum()
        if (total > 100.0) {
            return "Nominee shares add up to ${AmountFormat.format(total, 0)}%, which is more than the property."
        }
        if (shared.size == picked.size && kotlin.math.abs(total - 100.0) > 0.01) {
            return "Every nominee carries a share, so they must add up to 100% — " +
                "they add up to ${AmountFormat.format(total, 0)}%."
        }
        return null
    }

    /** Picks in the order the available list shows them — the print order. */
    private fun orderedPicks(): List<SaleNomineePick> =
        _uiState.value.nomineeAvailable.mapNotNull { _uiState.value.nomineePicks[it.id] }

    /** Saves the sale's nominations, then refreshes so the count updates. */
    fun saveNominees() {
        val state = _uiState.value
        val sale = state.nomineeSale ?: return
        if (state.nomineeSaving) return
        if (nomineeShareProblem() != null) return
        _uiState.update { it.copy(nomineeSaving = true) }
        viewModelScope.launch {
            when (val result = repository.saveSaleNominees(sale.saleId, orderedPicks())) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(nomineeSaving = false, nomineeSale = null, actionMessage = result.data)
                    }
                    refreshSilently()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        nomineeSaving = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Re-runs the current filters without the full-screen spinner. */
    private suspend fun refreshSilently() {
        val state = _uiState.value
        val result = repository.fetchSoldUnits(
            projectId = state.selectedProject.id,
            buildingId = state.selectedBuilding.id,
            dateFrom = state.dateFrom?.toApi(),
            dateTo = state.dateTo?.toApi(),
            query = state.query,
            dueOnly = state.dueOnly,
        )
        _uiState.update {
            when (result) {
                is Resource.Success -> it.copy(generatingSaleId = null, report = result.data)
                else -> it.copy(generatingSaleId = null)
            }
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SoldUnitsViewModel(
                    repository = ServiceLocator.provideRealEstateSalesRepository(appContext),
                    settings = ServiceLocator.provideSessionManager(appContext).state.value.settings,
                )
            }
        }
    }
}

@Composable
fun SoldUnitsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoldUnitsViewModel = viewModel(
        factory = SoldUnitsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
        title = "Sold Units",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        val onScreen = MaterialTheme.colorScheme.onBackground
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Filters ----------------------------------------------------
            AppSelectDropdown(
                label = "Project",
                options = state.projects,
                selected = state.selectedProject,
                onSelected = viewModel::onProjectSelected,
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (state.isOptionsLoading) "Loading projects…" else "All Projects",
            )
            AppSelectDropdown(
                label = "Building",
                options = state.buildings,
                selected = state.selectedBuilding,
                onSelected = viewModel::onBuildingSelected,
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (state.isOptionsLoading) "Loading buildings…" else "All Buildings",
            )
            state.optionsError?.let {
                Text(it, color = onScreen, style = MaterialTheme.typography.bodySmall)
            }

            AppTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Search customer / unit…",
                caption = "Search",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "From Date",
                    value = state.dateFrom?.toDisplay() ?: "",
                    placeholder = "Any",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showSoldUnitsDatePicker(
                            context,
                            state.dateFrom ?: SimpleDate.today(),
                            viewModel::onDateFromSelected,
                        )
                    },
                )
                PickerField(
                    label = "To Date",
                    value = state.dateTo?.toDisplay() ?: "",
                    placeholder = "Any",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showSoldUnitsDatePicker(
                            context,
                            state.dateTo ?: SimpleDate.today(),
                            viewModel::onDateToSelected,
                        )
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.dueOnly, onCheckedChange = viewModel::onDueOnlyToggle)
                Text(
                    text = "Only units having due",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onScreen,
                )
            }

            FilterActions(
                onApply = viewModel::search,
                onReset = viewModel::reset,
                canApply = !state.isLoading,
                isLoading = state.isLoading,
                applyText = "Search",
            )

            // ---- Results ----------------------------------------------------
            when {
                state.isLoading -> CenteredBox {
                    CircularProgressIndicator(color = onScreen)
                }

                state.loadError != null -> CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.loadError!!, color = onScreen, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::search)
                    }
                }

                !state.hasApplied || state.report == null -> CenteredBox {
                    Text(
                        text = "Choose your filters, then tap Search.",
                        color = onScreen.muted(),
                        textAlign = TextAlign.Center,
                    )
                }

                state.report!!.customers.isEmpty() -> CenteredBox {
                    Text(
                        text = "No sold units found for this selection.",
                        color = onScreen.muted(),
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    val report = state.report!!
                    SoldUnitsSummaryTiles(report)
                    SoldUnitsTable(
                        report = report,
                        generatingSaleId = state.generatingSaleId,
                        onGenerate = viewModel::requestGenerate,
                        canWithdrawLetter = state.canWithdrawLetter,
                        canWithdrawBooking = state.canWithdrawBooking,
                        onWithdraw = viewModel::requestWithdraw,
                        onNominees = viewModel::openNominees,
                    )
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    // The web's Generate confirmation: a snapshot is immutable once saved. The
    // reference and date head the paper — both the office's to overwrite
    // (the register's number, and the day the paper actually goes out).
    state.pendingGenerate?.let { sale ->
        val isLetter = state.generateKind == PaperKind.LETTER
        val paperName = if (isLetter) "allotment letter" else "booking form"
        val nextCopy = if (isLetter) {
            "L-${(sale.letterVersions.maxOrNull() ?: 0) + 1}"
        } else {
            "B-${(sale.bookingFormVersions.maxOrNull() ?: 0) + 1}"
        }
        AlertDialog(
            onDismissRequest = viewModel::cancelGenerate,
            title = { Text("Generate $paperName?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This saves $paperName $nextCopy for receipt " +
                            "${sale.receiptNo.ifBlank { "(no receipt)" }} as a permanent snapshot. " +
                            "Saved versions can be printed from the web.",
                    )
                    AppTextField(
                        value = state.refNo,
                        onValueChange = viewModel::onRefNoChange,
                        label = "Reference No",
                        caption = "Reference No (blank = server derives one)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PickerField(
                        label = "Letter Date",
                        value = state.refDate?.toDisplay() ?: "",
                        placeholder = "Today",
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showSoldUnitsDatePicker(
                                context,
                                state.refDate ?: SimpleDate.today(),
                                viewModel::onRefDateSelected,
                            )
                        },
                    )
                }
            },
            confirmButton = {
                PrimaryButton(text = "Generate", onClick = viewModel::confirmGenerate, compact = true)
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelGenerate) },
        )
    }

    // Withdrawing removes a copy that may already have been handed over, so it
    // asks first, names the paper, and says the number is not reused — the
    // obvious worry on withdrawing L-2 is whether L-3 is about to be renamed.
    state.pendingWithdraw?.let { target ->
        val paperName = if (target.kind == PaperKind.LETTER) "letter" else "booking form"
        val copyName = (if (target.kind == PaperKind.LETTER) "L-" else "B-") + target.version
        AlertDialog(
            onDismissRequest = viewModel::cancelWithdraw,
            title = { Text("Withdraw $paperName $copyName?") },
            text = {
                Text(
                    "This removes the issued copy $copyName for receipt " +
                        "${target.sale.receiptNo.ifBlank { "(no receipt)" }}. It cannot be " +
                        "printed again afterwards, and the number is not reused.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmWithdraw) {
                    Text("Withdraw", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelWithdraw) },
        )
    }

    // Naming who a sold property is left to, after the sale. The people come
    // off the buyer's own nominee list (written on the customer screen); what
    // is decided here is which of them stand against THIS property, in what
    // order and for what share.
    state.nomineeSale?.let { sale ->
        SaleNomineeDialog(
            sale = sale,
            state = state,
            onToggle = viewModel::toggleNominee,
            onShare = viewModel::onNomineeShare,
            onPriority = viewModel::onNomineePriority,
            shareProblem = viewModel.nomineeShareProblem(),
            onSave = viewModel::saveNominees,
            onClose = viewModel::closeNominees,
        )
    }
}

/** The nominee dialog: tick who stands against this sale, order and share. */
@Composable
private fun SaleNomineeDialog(
    sale: SoldUnitSale,
    state: SoldUnitsUiState,
    onToggle: (SaleNomineeOption) -> Unit,
    onShare: (Long, String) -> Unit,
    onPriority: (Long, String) -> Unit,
    shareProblem: String?,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Nominee") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Who receipt ${sale.receiptNo.ifBlank { "(no receipt)" }} is left to. " +
                        "Named against this property only — the buyer's other properties " +
                        "are nominated separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                when {
                    state.nomineeLoading -> Text("Loading…")
                    state.nomineeAvailable.isEmpty() -> Text(
                        "This customer has no nominee on file. Add them on the " +
                            "customer screen, then they can be named here.",
                    )
                    else -> state.nomineeAvailable.forEach { option ->
                        val pick = state.nomineePicks[option.id]
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(option) },
                            ) {
                                Checkbox(checked = pick != null, onCheckedChange = { onToggle(option) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(option.name, style = MaterialTheme.typography.bodyMedium)
                                    val detail = listOf(
                                        titleCaseRelation(option.relation),
                                        option.nationalId,
                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (detail.isNotBlank()) {
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.appColors.textOnScreenMuted,
                                        )
                                    }
                                }
                            }
                            if (pick != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTextField(
                                        value = pick.priority,
                                        onValueChange = { onPriority(option.id, it) },
                                        label = "As listed",
                                        caption = "Order",
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppTextField(
                                        value = pick.share,
                                        onValueChange = { onShare(option.id, it) },
                                        label = "Left to law",
                                        caption = "Share %",
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                shareProblem?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = onSave,
                enabled = !state.nomineeLoading && !state.nomineeSaving && shareProblem == null,
                isLoading = state.nomineeSaving,
                compact = true,
            )
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = onClose) },
    )
}

/**
 * "wife" reads as Wife, "grand father" as Grand Father. Relations are stored
 * as the dropdown's own lowercase values — right for a stored value, wrong on
 * a printed line. Capitalised where shown, never in the column.
 */
private fun titleCaseRelation(value: String): String =
    value.split(" ").filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

// ---------------------------------------------------------------------------
// Summary tiles
// ---------------------------------------------------------------------------

/**
 * Whole-taka display, the web's `wholeTaka`/`money` pair: truncate toward zero
 * (100.99 → 100), "-" for zero. Report totals are re-summed from these floored
 * per-sale figures so the columns always add up to the totals shown.
 */
private fun wholeTaka(value: Double): Long = value.toLong()

/**
 * A negative amount prints as `(-) 36,000`, not `-36,000`. The bare minus is
 * one thin stroke against a column of digits and is missed exactly where it
 * matters — a discount read as a charge turns a rebate into an addition. The
 * parenthesised form is what the office already writes by hand.
 */
private fun money(value: Double): String {
    val whole = wholeTaka(value)
    return when {
        whole == 0L -> "-"
        whole < 0L -> "(-) " + AmountFormat.format(-whole.toDouble(), 0)
        else -> AmountFormat.format(whole.toDouble(), 0)
    }
}

/** The money totals recomputed from the floored per-sale figures. */
private data class FlooredTotals(
    val parkingValue: Long,
    val saleValue: Long,
    val received: Long,
    val due: Long,
)

private fun flooredTotals(report: SoldUnitsReport): FlooredTotals {
    val sales = report.customers.flatMap { it.units }
    return FlooredTotals(
        parkingValue = sales.sumOf { wholeTaka(it.parkingAmount) },
        saleValue = sales.sumOf { wholeTaka(it.totalAmount) },
        received = sales.sumOf { wholeTaka(it.receivedAmount) },
        due = sales.sumOf { wholeTaka(it.dueAmount) },
    )
}

private fun moneyWhole(value: Long): String = when {
    value == 0L -> "-"
    value < 0L -> "(-) " + AmountFormat.format(-value.toDouble(), 0)
    else -> AmountFormat.format(value.toDouble(), 0)
}

/** The web's 7 cards: counts, then Parking/Sale Value, then Received/Due. */
@Composable
private fun SoldUnitsSummaryTiles(report: SoldUnitsReport) {
    val totals = report.totals
    val floored = flooredTotals(report)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure("Customers", countOrDash(totals.customerCount), Modifier.weight(1f))
            SummaryFigure("Sold Units", countOrDash(totals.unitCount), Modifier.weight(1f))
            SummaryFigure("Sold Parking", countOrDash(totals.parkingCount), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure("Parking Value", moneyWhole(floored.parkingValue), Modifier.weight(1f))
            SummaryFigure("Sale Value", moneyWhole(floored.saleValue), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure("Received", moneyWhole(floored.received), Modifier.weight(1f))
            SummaryFigure("Due", moneyWhole(floored.due), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, modifier: Modifier = Modifier) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Customer-wise table (the web's merged-cell report, colour-cycled per buyer)
// ---------------------------------------------------------------------------

// Fixed column widths; the table scrolls horizontally like the other reports.
private val COL_SL = 40.dp
private val COL_CUSTOMER = 170.dp
private val COL_LINE = 190.dp
private val COL_AMOUNT = 100.dp
private val COL_TOTAL = 120.dp
private val COL_RECEIVED = 100.dp
private val COL_DUE = 100.dp

// Five 1dp vertical grid rules sit between the body cells; the header and
// footer rows carry matching spacers so every column lines up.
private val TABLE_WIDTH =
    COL_SL + COL_CUSTOMER + COL_LINE + COL_AMOUNT + COL_TOTAL + COL_RECEIVED + COL_DUE + 5.dp

/**
 * The web report verbatim: a Sl | Customer | Unit / Parking | Amount | Total |
 * Received | Due table where each buyer's rows form one block outlined in the
 * cycling customer colour, the Sl/Customer cells are tinted with it, and each
 * sale's Total/Received/Due merge over that sale's charge lines.
 */
@Composable
private fun SoldUnitsTable(
    report: SoldUnitsReport,
    generatingSaleId: String?,
    onGenerate: (SoldUnitSale, PaperKind) -> Unit,
    canWithdrawLetter: Boolean,
    canWithdrawBooking: Boolean,
    onWithdraw: (SoldUnitSale, PaperKind, Int) -> Unit,
    onNominees: (SoldUnitSale) -> Unit,
) {
    val hScroll = rememberScrollState()
    val gridLine = MaterialTheme.appColors.gridLine
    val cycle = MaterialTheme.brand.customerCycle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll),
    ) {
        SoldUnitsHeaderRow()
        report.customers.forEachIndexed { index, customer ->
            SoldUnitCustomerBlock(
                customer = customer,
                index = index,
                accent = cycle[index % cycle.size],
                gridLine = gridLine,
                generatingSaleId = generatingSaleId,
                onGenerate = onGenerate,
                canWithdrawLetter = canWithdrawLetter,
                canWithdrawBooking = canWithdrawBooking,
                onWithdraw = onWithdraw,
                onNominees = onNominees,
            )
        }
        SoldUnitsGrandTotalRow(report)
    }
}

@Composable
private fun SoldUnitsHeaderRow() {
    Row(
        modifier = Modifier
            .width(TABLE_WIDTH)
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Sl", COL_SL, TextAlign.Center)
        Spacer(Modifier.width(1.dp))
        HeaderCell("Customer", COL_CUSTOMER, TextAlign.Start)
        Spacer(Modifier.width(1.dp))
        HeaderCell("Unit / Parking", COL_LINE, TextAlign.Start)
        HeaderCell("Amount", COL_AMOUNT, TextAlign.End)
        Spacer(Modifier.width(1.dp))
        HeaderCell("Total", COL_TOTAL, TextAlign.End)
        Spacer(Modifier.width(1.dp))
        HeaderCell("Received", COL_RECEIVED, TextAlign.End)
        Spacer(Modifier.width(1.dp))
        HeaderCell("Due", COL_DUE, TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = AppFontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 6.dp),
    )
}

/**
 * One buyer's block: Sl and Customer merge over every line, each sale's
 * Total/Received/Due merge over its own lines, and the whole block wears the
 * customer colour as its outline with a tint behind the merged cells.
 */
@Composable
private fun SoldUnitCustomerBlock(
    customer: SoldUnitCustomer,
    index: Int,
    accent: Color,
    gridLine: Color,
    generatingSaleId: String?,
    onGenerate: (SoldUnitSale, PaperKind) -> Unit,
    canWithdrawLetter: Boolean,
    canWithdrawBooking: Boolean,
    onWithdraw: (SoldUnitSale, PaperKind, Int) -> Unit,
    onNominees: (SoldUnitSale) -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val tint = accent.asTint()

    Row(
        modifier = Modifier
            .width(TABLE_WIDTH)
            .height(IntrinsicSize.Min)
            .border(width = 2.dp, color = accent),
    ) {
        // Sl — merged, tinted, numbered in the block colour.
        Box(
            modifier = Modifier.width(COL_SL).fillMaxHeight().background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.Bold,
                color = accent,
            )
        }
        VLine(gridLine)

        // Customer — merged: name / address / mobile.
        Column(
            modifier = Modifier
                .width(COL_CUSTOMER)
                .fillMaxHeight()
                .background(tint)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = customer.customerName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = onScreen,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (customer.customerAddress.isNotBlank()) {
                Text(
                    text = customer.customerAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.muted(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (customer.customerMobile.isNotBlank()) {
                Text(
                    text = "Cell: ${customer.customerMobile}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.muted(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        VLine(gridLine)

        // One sale per band: its charge lines beside its merged totals.
        Column(modifier = Modifier.fillMaxHeight()) {
            customer.units.forEachIndexed { saleIndex, sale ->
                if (saleIndex > 0) HorizontalDivider(color = gridLine, modifier = Modifier.fillMaxWidth())
                SoldUnitSaleBand(
                    sale = sale,
                    gridLine = gridLine,
                    isGenerating = generatingSaleId == sale.saleId,
                    onGenerate = onGenerate,
                    canWithdrawLetter = canWithdrawLetter,
                    canWithdrawBooking = canWithdrawBooking,
                    onWithdraw = onWithdraw,
                    onNominees = onNominees,
                )
            }
        }
    }
}

@Composable
private fun SoldUnitSaleBand(
    sale: SoldUnitSale,
    gridLine: Color,
    isGenerating: Boolean,
    onGenerate: (SoldUnitSale, PaperKind) -> Unit,
    canWithdrawLetter: Boolean,
    canWithdrawBooking: Boolean,
    onWithdraw: (SoldUnitSale, PaperKind, Int) -> Unit,
    onNominees: (SoldUnitSale) -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Charge lines: caption + place | amount, a faded rule between lines.
        Column(modifier = Modifier.width(COL_LINE + COL_AMOUNT)) {
            sale.lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) HorizontalDivider(color = gridLine)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.width(COL_LINE).padding(horizontal = 6.dp)) {
                        Text(
                            text = line.caption.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            color = onScreen,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (line.place.isNotBlank()) {
                            Text(
                                text = line.place,
                                style = MaterialTheme.typography.labelSmall,
                                color = onScreen.muted(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = money(line.amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = onScreen,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(COL_AMOUNT).padding(horizontal = 6.dp),
                    )
                }
            }
        }
        VLine(gridLine)

        // Total — merged over the sale's lines: amount, sale date | receipt,
        // the saved-letter count, and the snapshot Generate action.
        Column(
            modifier = Modifier.width(COL_TOTAL).fillMaxHeight().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = money(sale.totalAmount),
                style = MaterialTheme.typography.bodySmall,
                color = onScreen,
                textAlign = TextAlign.End,
            )
            val saleInfo = listOf(displayDate(sale.saleDate), sale.receiptNo)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            if (saleInfo.isNotBlank()) {
                Text(
                    text = saleInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.muted(),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // One chip per issued paper, built from the numbers the report
            // sends — after a withdrawal a sale carries L-1 and L-3, and
            // counting 1..n would offer a copy that is gone. The ✕ beside a
            // chip withdraws that copy, and only shows for whoever may do that.
            if (sale.letterVersions.isNotEmpty()) {
                PaperChipsRow(
                    prefix = "L",
                    versions = sale.letterVersions,
                    canWithdraw = canWithdrawLetter && sale.saleId.isNotBlank(),
                    enabled = !isGenerating,
                    gridLine = gridLine,
                    ink = onScreen,
                    onWithdraw = { version -> onWithdraw(sale, PaperKind.LETTER, version) },
                )
            }
            // The booking form rides its own B-n series, because a sale can be
            // on its third form and its first letter.
            if (sale.bookingFormVersions.isNotEmpty()) {
                PaperChipsRow(
                    prefix = "B",
                    versions = sale.bookingFormVersions,
                    canWithdraw = canWithdrawBooking && sale.saleId.isNotBlank(),
                    enabled = !isGenerating,
                    gridLine = gridLine,
                    ink = onScreen,
                    onWithdraw = { version -> onWithdraw(sale, PaperKind.BOOKING, version) },
                )
            }
            if (sale.saleId.isNotBlank()) {
                LinkButton(
                    text = if (isGenerating) "Generating…" else "Generate",
                    onClick = { onGenerate(sale, PaperKind.LETTER) },
                    enabled = !isGenerating,
                )
                LinkButton(
                    text = "Booking Form",
                    onClick = { onGenerate(sale, PaperKind.BOOKING) },
                    enabled = !isGenerating,
                )
                // Who this property is left to. Named at booking when the buyer
                // has decided, and here when they decide later — which is most
                // of the time.
                LinkButton(
                    text = if (sale.nomineeCount > 0) "Nominees (${sale.nomineeCount})" else "+ Nominee",
                    onClick = { onNominees(sale) },
                    enabled = !isGenerating,
                )
            }
        }
        VLine(gridLine)

        Box(
            modifier = Modifier.width(COL_RECEIVED).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = money(sale.receivedAmount),
                style = MaterialTheme.typography.bodySmall,
                color = onScreen,
                textAlign = TextAlign.End,
            )
        }
        VLine(gridLine)

        Box(
            modifier = Modifier.width(COL_DUE).fillMaxHeight().padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = money(sale.dueAmount),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = onScreen,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * The issued copies of one paper as chips: the number prints the stored copy's
 * name, and the ✕ beside it withdraws that copy — two controls in one frame.
 */
@Composable
private fun PaperChipsRow(
    prefix: String,
    versions: List<Int>,
    canWithdraw: Boolean,
    enabled: Boolean,
    gridLine: Color,
    ink: Color,
    onWithdraw: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        versions.forEach { version ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(width = 1.dp, color = gridLine, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "$prefix-$version",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.muted(),
                )
                if (canWithdraw) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable(enabled = enabled) { onWithdraw(version) },
                    )
                }
            }
        }
    }
}

/**
 * The web's tfoot: label spanning the left columns, then the three totals —
 * re-summed from the floored per-sale figures so they match the cells above.
 */
@Composable
private fun SoldUnitsGrandTotalRow(report: SoldUnitsReport) {
    val totals = report.totals
    val floored = flooredTotals(report)
    Row(
        modifier = Modifier
            .width(TABLE_WIDTH)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = MaterialTheme.colorScheme.onSecondaryContainer
        Text(
            text = "Grand Total (${totals.customerCount} customer, ${totals.unitCount} unit, ${totals.parkingCount} parking)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = AppFontWeight.Bold,
            color = ink,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(COL_SL + COL_CUSTOMER + COL_LINE + COL_AMOUNT + 2.dp)
                .padding(horizontal = 6.dp),
        )
        Spacer(Modifier.width(1.dp))
        GrandTotalCell(moneyWhole(floored.saleValue), COL_TOTAL, ink)
        Spacer(Modifier.width(1.dp))
        GrandTotalCell(moneyWhole(floored.received), COL_RECEIVED, ink)
        Spacer(Modifier.width(1.dp))
        GrandTotalCell(moneyWhole(floored.due), COL_DUE, ink)
    }
}

@Composable
private fun GrandTotalCell(text: String, width: Dp, ink: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = AppFontWeight.Bold,
        color = ink,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 6.dp),
    )
}

/** A 1dp vertical grid rule inside an [IntrinsicSize.Min] row. */
@Composable
private fun VLine(color: Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Zero counts read as "-", matching the amount cells. */
private fun countOrDash(value: Int): String = if (value == 0) "-" else value.toString()

/** The backend's yyyy-MM-dd (or already-display) sale date as dd/MM/yyyy. */
private fun displayDate(value: String): String =
    SimpleDate.fromApi(value)?.toDisplay()
        ?: SimpleDate.fromDisplay(value)?.toDisplay()
        ?: value

private fun showSoldUnitsDatePicker(
    context: Context,
    initial: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
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
