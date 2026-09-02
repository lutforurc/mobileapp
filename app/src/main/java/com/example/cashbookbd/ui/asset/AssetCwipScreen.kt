package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetCwipBoard
import com.example.cashbookbd.data.repository.AssetCwipFinishInput
import com.example.cashbookbd.data.repository.AssetCwipInput
import com.example.cashbookbd.data.repository.AssetCwipPlan
import com.example.cashbookbd.data.repository.AssetCwipRepository
import com.example.cashbookbd.data.repository.AssetCwipWork
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The finishing panel: the work, what is being typed, and the entry it would write. */
data class AssetCwipFinishing(
    val work: AssetCwipWork,
    val input: AssetCwipFinishInput,
    val plan: AssetCwipPlan? = null,
    val isPlanLoading: Boolean = true,
)

data class AssetCwipUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val board: AssetCwipBoard? = null,
    /** Null while the form is closed — the web toggles the same card. */
    val form: AssetCwipInput? = null,
    val pendingDelete: AssetCwipWork? = null,
    val finishing: AssetCwipFinishing? = null,
    val confirmingFinish: Boolean = false,
    val sessionExpired: Boolean = false,
)

class AssetCwipViewModel(
    private val repository: AssetCwipRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetCwipUiState())
    val uiState: StateFlow<AssetCwipUiState> = _uiState.asStateFlow()

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
        val branchId = _uiState.value.selectedBranch?.id
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchBoard(branchId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, board = result.data)
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
        _uiState.update { it.copy(selectedBranch = branch, form = null, finishing = null) }
        load()
    }

    // ---- The work form ------------------------------------------------------

    /** [work] of null opens a blank one; the toggle closes whatever is open. */
    fun openForm(work: AssetCwipWork?) = _uiState.update { state ->
        state.copy(
            form = AssetCwipInput(
                id = work?.id,
                branchId = state.selectedBranch?.id,
                code = work?.code.orEmpty(),
                name = work?.name.orEmpty(),
                description = work?.description.orEmpty(),
                projectId = work?.projectId.orEmpty(),
                cwipCoa4Id = work?.cwipCoa4Id.orEmpty(),
                categoryId = work?.categoryId.orEmpty(),
                startedOn = work?.startedOn?.ifBlank { todayApi() } ?: todayApi(),
                expectedOn = work?.expectedOn.orEmpty(),
                notes = work?.notes.orEmpty(),
            ),
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun onForm(change: (AssetCwipInput) -> AssetCwipInput) =
        _uiState.update { state -> state.copy(form = state.form?.let(change)) }

    fun save() {
        val form = _uiState.value.form ?: return
        if (form.code.isBlank() || form.name.isBlank()) {
            _uiState.update { it.copy(error = "It needs a code and a name.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.saveWork(form)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, form = null, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        // Usually the refusal: the work has been finished, so it
                        // is part of the books now. The server says exactly that.
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askDelete(work: AssetCwipWork) = _uiState.update { it.copy(pendingDelete = work) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val work = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(isSaving = true, pendingDelete = null) }
        viewModelScope.launch {
            when (val result = repository.deleteWork(work.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Finishing it -------------------------------------------------------

    /**
     * Opens the finishing panel and asks the server what the entry would be.
     *
     * ⚠️ Asked BEFORE anything is typed. Finishing moves a cost that has been
     * sitting in the balance sheet for two years into the asset head and starts
     * depreciation running — neither is something to discover afterwards.
     */
    fun openFinish(work: AssetCwipWork) {
        _uiState.update {
            it.copy(
                finishing = AssetCwipFinishing(
                    work = work,
                    input = AssetCwipFinishInput(
                        capitalisedOn = todayApi(),
                        // Sensible starting points, both editable: the finished
                        // thing usually wants a code of its own, and almost
                        // always keeps the name it was built under.
                        code = "",
                        name = work.name,
                        location = "",
                        categoryId = work.categoryId,
                    ),
                ),
            )
        }
        viewModelScope.launch {
            when (val result = repository.fetchPlan(work.id)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        finishing = state.finishing?.copy(plan = result.data, isPlanLoading = false),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        finishing = null,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closeFinish() = _uiState.update { it.copy(finishing = null, confirmingFinish = false) }

    fun onFinish(change: (AssetCwipFinishInput) -> AssetCwipFinishInput) =
        _uiState.update { state ->
            state.copy(finishing = state.finishing?.let { it.copy(input = change(it.input)) })
        }

    fun askFinish() {
        val finishing = _uiState.value.finishing ?: return
        if (finishing.input.code.isBlank()) {
            _uiState.update {
                it.copy(error = "Give the finished thing a code — it goes on the sticker.")
            }
            return
        }
        _uiState.update { it.copy(confirmingFinish = true, error = null) }
    }

    fun cancelFinish() = _uiState.update { it.copy(confirmingFinish = false) }

    /** The real thing: a voucher and a register row, once somebody has said so. */
    fun finish() {
        val finishing = _uiState.value.finishing ?: return
        _uiState.update { it.copy(isSaving = true, confirmingFinish = false) }
        viewModelScope.launch {
            val result = repository.capitalise(finishing.work.id, finishing.input)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, finishing = null, message = result.data)
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The costs screen came back having written a line down: re-read the heaps. */
    fun onSaved(message: String) {
        _uiState.update { it.copy(message = message) }
        load()
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null, error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetCwipViewModel(
                    repository = AssetCwipRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * What is being built: cost gathered up until there is a thing to depreciate.
 *
 * ⚠️ A HALF-BUILT WAREHOUSE IS NOT AN ASSET YET, so it is not in the register
 * and nothing is charged against it. Its cost gathers in a balance sheet head of
 * its own, and on the day it is finished the whole heap becomes ONE asset whose
 * depreciation starts from that day. Charging earlier would write down a thing
 * nobody has used; leaving it in the heap afterwards would keep a working
 * building out of the schedule for ever.
 *
 * ⚠️ NOTHING ON THIS LIST IS POSTED, and the screen says so at the top. The
 * bills went through ordinary vouchers coded to the work-in-progress head, so
 * the money is in the ledger already.
 *
 * ⚠️ FINISHING IT IS THE ONE ACT THAT WRITES AN ENTRY, so it is shown leg by leg
 * first, sits behind a confirm dialog, and answers to the permission that writes
 * vouchers rather than the one that keeps the list.
 */
@Composable
fun AssetCwipScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetCwipViewModel = viewModel(
        factory = AssetCwipViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    // Finishing a work writes a voucher, so it answers to the permission that
    // writes vouchers rather than the one that reads the list.
    val canPost = Permissions.hasAny(
        sessionState.permissions, listOf(AssetMenu.PERM_DEPRECIATION_RUN),
    )

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    OnAssetSaved(navController) { viewModel.onSaved(it) }

    val board = state.board

    AuthenticatedShell(
        title = "Under construction",
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

            // ⚠️ The server's own paragraph, not a rewritten one: it is where
            // "nothing here is posted" is said, and that is the sentence that
            // stops the same building being paid for twice.
            board?.note?.takeIf { it.isNotBlank() }?.let {
                AssetNotice(text = it, tone = AssetTone.Info)
            }

            state.message?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetNotice(text = it, tone = AssetTone.Success, modifier = Modifier.weight(1f))
                    LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                }
            }
            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            AddButton(
                text = if (state.form == null) "Something new is being built" else "Close",
                onClick = {
                    if (state.form == null) viewModel.openForm(null) else viewModel.closeForm()
                },
                compact = true,
            )

            state.form?.let { form ->
                AssetCwipForm(
                    form = form,
                    board = board,
                    isSaving = state.isSaving,
                    onChange = viewModel::onForm,
                    onSave = viewModel::save,
                    onClose = viewModel::closeForm,
                )
            }

            state.finishing?.let { finishing ->
                AssetCwipFinishPanel(
                    finishing = finishing,
                    board = board,
                    isSaving = state.isSaving,
                    onChange = viewModel::onFinish,
                    onFinish = viewModel::askFinish,
                    onClose = viewModel::closeFinish,
                )
            }

            when {
                state.isLoading && board == null -> AssetLoading()
                board == null -> Unit
                else -> ReportTable(
                    columns = cwipColumns(
                        canPost = canPost,
                        onCosts = { navController.navigate(AssetMenu.cwipCosts(it.id)) },
                        onEdit = viewModel::openForm,
                        onFinish = viewModel::openFinish,
                        onDelete = viewModel::askDelete,
                    ),
                    data = board.rows,
                    noDataMessage = "Nothing being built. Add one when a building, a fit-out or a " +
                        "machine starts costing money before it can be used.",
                    scrollable = false,
                )
            }
        }
    }

    state.pendingDelete?.let { work ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Remove this work?") },
            text = {
                Text(
                    "\"${work.name}\" will be removed. Nothing has been spent on it, so nothing " +
                        "in the ledger moves.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Remove",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isSaving,
                    isLoading = state.isSaving,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancelDelete) },
        )
    }

    val finishing = state.finishing
    if (state.confirmingFinish && finishing != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelFinish,
            title = { Text("Bring it into use?") },
            text = {
                Text(
                    "A voucher will be written: " +
                        AmountFormat.format(finishing.plan?.total ?: 0.0) +
                        " comes out of the work-in-progress head and becomes the cost of one " +
                        "asset, entered as ${finishing.input.code.trim()}. Depreciation starts " +
                        "from ${onTheDay(finishing.input.capitalisedOn)}, and the work is closed.",
                )
            },
            confirmButton = {
                PrimaryButton(text = "Bring it into use", onClick = viewModel::finish, compact = true)
            },
            dismissButton = { LinkButton(text = "Not yet", onClick = viewModel::cancelFinish) },
        )
    }
}

/**
 * A work being described.
 *
 * ⚠️ THE HEAD OFFERED IS A BALANCE SHEET HEAD, and only that — the server sends
 * no others. Pointed at a profit and loss head, the whole cost of a building
 * would disappear into one year's results.
 */
@Composable
private fun AssetCwipForm(
    form: AssetCwipInput,
    board: AssetCwipBoard?,
    isSaving: Boolean,
    onChange: ((AssetCwipInput) -> AssetCwipInput) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val headOptions = remember(board) {
        listOf(SelectorOption("", "Not chosen")) +
            board?.balanceSheetHeads.orEmpty().map { SelectorOption(it.id.toString(), it.label) }
    }
    val categoryOptions = remember(board) {
        listOf(SelectorOption("", "Not chosen")) +
            board?.categories.orEmpty().map { SelectorOption(it.id.toString(), it.label) }
    }
    val projectOptions = remember(board) {
        listOf(SelectorOption("", "None")) +
            board?.projects.orEmpty().map { SelectorOption(it.id.toString(), it.name) }
    }

    AssetPanel(title = if (form.id == null) "Something new is being built" else "Edit this work") {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTextField(
                value = form.code,
                onValueChange = { value -> onChange { it.copy(code = value) } },
                label = "CWIP-WH-01",
                caption = "Code",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = form.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = "Warehouse extension, Savar",
                caption = "What is being built",
                modifier = Modifier.fillMaxWidth(),
            )
            AppSelectDropdown(
                label = "Its cost sits in",
                options = headOptions,
                selected = headOptions.firstOrNull { it.id == form.cwipCoa4Id }
                    ?: headOptions.first(),
                onSelected = { option -> onChange { it.copy(cwipCoa4Id = option.id) } },
            )
            AssetCwipHint("A balance sheet head — not this year's expense.")

            AppSelectDropdown(
                label = "What it becomes",
                options = categoryOptions,
                selected = categoryOptions.firstOrNull { it.id == form.categoryId }
                    ?: categoryOptions.first(),
                onSelected = { option -> onChange { it.copy(categoryId = option.id) } },
            )
            AssetCwipHint("The rate it will then wear out at.")

            AssetDateField(
                label = "Started",
                value = form.startedOn,
                onPicked = { value -> onChange { it.copy(startedOn = value) } },
                modifier = Modifier.fillMaxWidth(),
            )
            AssetDateField(
                label = "Expected to finish",
                value = form.expectedOn,
                onPicked = { value -> onChange { it.copy(expectedOn = value) } },
                modifier = Modifier.fillMaxWidth(),
            )
            AssetCwipHint("Shown, never enforced.")

            // Only where this company keeps projects at all — a link, never a
            // dependency, so an empty list is ordinary rather than a fault.
            if (board?.projects.orEmpty().isNotEmpty()) {
                AppSelectDropdown(
                    label = "On which project",
                    options = projectOptions,
                    selected = projectOptions.firstOrNull { it.id == form.projectId }
                        ?: projectOptions.first(),
                    onSelected = { option -> onChange { it.copy(projectId = option.id) } },
                )
                AssetCwipHint("Optional.")
            }

            AppTextField(
                value = form.description,
                onValueChange = { value -> onChange { it.copy(description = value) } },
                label = "What the work covers",
                caption = "Description",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = form.notes,
                onValueChange = { value -> onChange { it.copy(notes = value) } },
                label = "Anything worth remembering",
                caption = "Note",
                modifier = Modifier.fillMaxWidth(),
            )

            PrimaryButton(
                text = "Save",
                onClick = onSave,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Close",
                onClick = onClose,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The finishing panel.
 *
 * ⚠️ SHOWN LEG BY LEG BEFORE IT IS DONE — the one act on this screen that writes
 * into the books. Somebody signing this off reads legs, not a summary.
 */
@Composable
private fun AssetCwipFinishPanel(
    finishing: AssetCwipFinishing,
    board: AssetCwipBoard?,
    isSaving: Boolean,
    onChange: ((AssetCwipFinishInput) -> AssetCwipFinishInput) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    val categoryOptions = remember(board) {
        listOf(SelectorOption("", "Not chosen")) +
            board?.categories.orEmpty().map { SelectorOption(it.id.toString(), it.label) }
    }
    val plan = finishing.plan

    AssetPanel(title = "Finished — bring ${finishing.work.name} into use") {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssetDateField(
                label = "Finished on",
                value = finishing.input.capitalisedOn,
                onPicked = { value -> onChange { it.copy(capitalisedOn = value) } },
                modifier = Modifier.fillMaxWidth(),
            )
            AssetCwipHint("Depreciation starts from this day.")

            AppTextField(
                value = finishing.input.code,
                onValueChange = { value -> onChange { it.copy(code = value) } },
                label = "BLD-SAVAR-01",
                caption = "The asset's code",
                modifier = Modifier.fillMaxWidth(),
            )
            AssetCwipHint("What goes on the sticker.")

            AppTextField(
                value = finishing.input.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = "Warehouse, Savar",
                caption = "The asset's name",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = finishing.input.location,
                onValueChange = { value -> onChange { it.copy(location = value) } },
                label = "Savar plant",
                caption = "Where it stands",
                modifier = Modifier.fillMaxWidth(),
            )
            AppSelectDropdown(
                label = "What does it become",
                options = categoryOptions,
                selected = categoryOptions.firstOrNull { it.id == finishing.input.categoryId }
                    ?: categoryOptions.first(),
                onSelected = { option -> onChange { it.copy(categoryId = option.id) } },
            )
            AssetCwipHint("Choose the category it will be filed under — the rate comes from there.")

            when {
                finishing.isPlanLoading -> AssetLoading()
                plan != null -> {
                    AssetSummaryBar(
                        parts = listOf(
                            AssetSummaryPart("${plan.lines} line(s) of cost"),
                            AssetSummaryPart(
                                "${AmountFormat.format(plan.total)} becomes the cost of one asset",
                                strong = true,
                            ),
                        ),
                    )
                    plan.legs.forEach { leg ->
                        AssetLine(
                            label = leg.head,
                            sublabel = leg.note,
                            value = if (leg.debit > 0) {
                                "Dr ${AmountFormat.format(leg.debit)}"
                            } else {
                                "Cr ${AmountFormat.format(leg.credit)}"
                            },
                        )
                    }
                    if (!plan.ready) {
                        AssetNotice(
                            text = "Not ready yet — it needs a work-in-progress head, a category " +
                                "to become, and at least one line of cost.",
                            tone = AssetTone.Warning,
                        )
                    }
                }
            }

            PrimaryButton(
                text = "Bring it into use",
                onClick = onFinish,
                enabled = plan?.ready == true,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Close",
                onClick = onClose,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AssetCwipHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textMuted,
    )
}

@Composable
private fun cwipColumns(
    canPost: Boolean,
    onCosts: (AssetCwipWork) -> Unit,
    onEdit: (AssetCwipWork) -> Unit,
    onFinish: (AssetCwipWork) -> Unit,
    onDelete: (AssetCwipWork) -> Unit,
): List<ReportColumn<AssetCwipWork>> {
    val muted = MaterialTheme.appColors.textMuted
    val danger = MaterialTheme.appColors.danger
    val success = MaterialTheme.appColors.success
    val action = MaterialTheme.appColors.action
    return listOf(
        ReportColumn("BEING BUILT", ReportColWidth.Fixed(170.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(
                        text = row.code + if (row.startedOn.isNotBlank()) {
                            " · started ${onTheDay(row.startedOn)}"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 2,
                    )
                }
            }
        },
        // ⚠️ The head is named on the row, and its absence named in the warning
        // colour: a work with nowhere for its cost to sit cannot be finished,
        // and finding that out at the button is finding it out too late.
        ReportColumn("COST SITS IN", ReportColWidth.Fixed(170.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = row.cwipHeadName.ifBlank { "not chosen" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.cwipHeadName.isBlank()) {
                            danger
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                    )
                    Text(
                        text = "becomes ${row.categoryName.ifBlank { "nothing chosen" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 2,
                    )
                }
            }
        },
        ReportColumn("SPENT SO FAR", ReportColWidth.Fixed(130.dp), TextAlign.End) { row, _ ->
            ReportTableCell.Slot {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCosts(row) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = AmountFormat.formatOrDash(row.total),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // What the cost is made of is one tap away rather than a
                    // screen away: this figure is what the question is asked of.
                    Text(
                        text = "${row.lines} line(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = action,
                        maxLines = 1,
                    )
                }
            }
        },
        ReportColumn("STATUS", ReportColWidth.Fixed(120.dp), TextAlign.Center) { row, _ ->
            if (row.isOpen) {
                cellText("Being built", TextAlign.Center, color = muted)
            } else {
                cellText(
                    text = "Finished ${onTheDay(row.capitalisedOn)}",
                    align = TextAlign.Center,
                    color = success,
                    maxLines = 2,
                )
            }
        },
        ReportColumn("ACTION", ReportColWidth.Fixed(180.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { onCosts(row) }) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = "What has gone into it",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // A finished work is a record of something that happened and
                    // its voucher points at it, so nothing below is offered once
                    // it has been brought into use.
                    if (row.isOpen) {
                        IconButton(onClick = { onEdit(row) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit this work",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // ⚠️ Offered only where the server says it is ready: the
                        // entry needs a head, a category and at least one line,
                        // and a button that refuses when pressed teaches nothing.
                        if (canPost && row.ready) {
                            IconButton(onClick = { onFinish(row) }) {
                                Icon(
                                    Icons.Filled.Done,
                                    contentDescription = "Finished — bring it into use",
                                    tint = success,
                                )
                            }
                        }
                        if (row.lines == 0) {
                            IconButton(onClick = { onDelete(row) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription =
                                        "Remove — nothing has been spent on it yet",
                                    tint = danger,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
