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
import com.example.cashbookbd.data.repository.AssetBranchOption
import com.example.cashbookbd.data.repository.AssetCare
import com.example.cashbookbd.data.repository.AssetPerson
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a count found, said the way a person says it. */
private val FOUND_NAMES = mapOf(
    "found" to "There",
    "missing" to "Not there",
    "damaged" to "There but damaged",
)

private val KIND_NAMES = mapOf(
    "service" to "Service",
    "repair" to "Repair",
    "inspection" to "Inspection",
)

data class AssetHandDraft(
    /** issued / returned. */
    val action: String = "issued",
    val onDate: String = todayApi(),
    val employeeId: String = "",
    val toBranchId: String = "",
    val conditionNote: String = "",
)

data class AssetCountDraft(
    val countedOn: String = todayApi(),
    val found: String = "found",
    val location: String = "",
    val note: String = "",
)

data class AssetVisitDraft(
    val onDate: String = todayApi(),
    val kind: String = "service",
    val description: String = "",
    val vendor: String = "",
    val cost: String = "",
    val daysDown: String = "",
    val nextDueOn: String = "",
)

data class AssetCareUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val care: AssetCare? = null,
    val people: List<AssetPerson> = emptyList(),
    val branches: List<AssetBranchOption> = emptyList(),
    /** custody / count / upkeep. */
    val section: String = "custody",
    val hand: AssetHandDraft = AssetHandDraft(),
    val count: AssetCountDraft = AssetCountDraft(),
    val visit: AssetVisitDraft = AssetVisitDraft(),
    val sessionExpired: Boolean = false,
)

class AssetCareViewModel(
    private val repository: AssetRepository,
    private val assetId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetCareUiState())
    val uiState: StateFlow<AssetCareUiState> = _uiState.asStateFlow()

    init {
        load()
        // ⚠️ A failure on either of these is not worth a message of its own —
        // the form says what it can offer, and an empty list is one of the
        // things it can say.
        viewModelScope.launch {
            (repository.fetchPeople() as? Resource.Success)?.let { result ->
                _uiState.update { it.copy(people = result.data) }
            }
        }
        viewModelScope.launch {
            (repository.fetchAssetBranches() as? Resource.Success)?.let { result ->
                _uiState.update { it.copy(branches = result.data) }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchHistory(assetId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        care = result.data,
                        // The form follows what the log says, rather than making
                        // somebody read it: handing out a thing already handed
                        // out is the mistake this screen exists to stop.
                        hand = it.hand.copy(
                            action = if (result.data.heldBy != null) "returned" else "issued",
                        ),
                    )
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

    fun onSection(value: String) = _uiState.update { it.copy(section = value) }

    fun onHand(change: (AssetHandDraft) -> AssetHandDraft) =
        _uiState.update { it.copy(hand = change(it.hand), error = null) }

    fun onCount(change: (AssetCountDraft) -> AssetCountDraft) =
        _uiState.update { it.copy(count = change(it.count), error = null) }

    fun onVisit(change: (AssetVisitDraft) -> AssetVisitDraft) =
        _uiState.update { it.copy(visit = change(it.visit), error = null) }

    fun handOver() {
        val hand = _uiState.value.hand
        if (hand.action == "issued" && hand.employeeId.isBlank() && hand.toBranchId.isBlank()) {
            // Said here as well as by the server: a form that has to be
            // submitted to learn what it wants is a form that gets abandoned.
            _uiState.update { it.copy(error = "Say who took it, or which branch it went to.") }
            return
        }
        send {
            repository.saveCustody(
                assetId = assetId,
                action = hand.action,
                onDate = hand.onDate,
                employeeId = hand.employeeId,
                toBranchId = hand.toBranchId,
                conditionNote = hand.conditionNote,
            )
        }
    }

    fun record() {
        val count = _uiState.value.count
        send {
            repository.saveVerification(
                assetId = assetId,
                countedOn = count.countedOn,
                found = count.found,
                location = count.location,
                note = count.note,
            )
        }
    }

    fun logVisit() {
        val visit = _uiState.value.visit
        if (visit.description.isBlank()) {
            _uiState.update { it.copy(error = "What was done to it?") }
            return
        }
        send {
            repository.saveMaintenance(
                assetId = assetId,
                onDate = visit.onDate,
                kind = visit.kind,
                description = visit.description,
                vendor = visit.vendor,
                cost = visit.cost,
                daysDown = visit.daysDown,
                nextDueOn = visit.nextDueOn,
            )
        }
    }

    private fun send(action: suspend () -> Resource<String>) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = result.data,
                            hand = AssetHandDraft(),
                            count = AssetCountDraft(),
                            visit = AssetVisitDraft(),
                        )
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, assetId: Long) = viewModelFactory {
            initializer {
                AssetCareViewModel(AssetRepository.get(context.applicationContext), assetId)
            }
        }
    }
}

/**
 * Everything known about one asset that is not money: who is holding it, whether
 * it was there when somebody looked, and what it has cost to keep.
 *
 * ⚠️ NOTHING HERE POSTS. A repair bill is paid through the ordinary expense
 * voucher like any other bill; the cost recorded against a visit is the SERVICE
 * HISTORY, kept so that "is this generator worth keeping" has an answer. The
 * screen says so where the money is typed, because a box labelled "cost" that
 * quietly posts nothing is one somebody will otherwise enter twice.
 *
 * ⚠️ CUSTODY IS A LOG, NOT A FIELD. Who has it now is the last row, not a column
 * on the asset: a column would answer "who has it" and lose "who had it in
 * March", which is the question actually asked when something goes missing.
 */
@Composable
fun AssetCareScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    assetId: Long,
    modifier: Modifier = Modifier,
    viewModel: AssetCareViewModel = viewModel(
        factory = AssetCareViewModel.provideFactory(LocalContext.current, assetId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    // A return taken here changes the handover register that sent us in, so the
    // list behind is told to re-read when this screen is left.
    LaunchedEffect(state.message) {
        state.message?.let { navController.reportAssetSaved(it) }
    }

    val care = state.care
    val asset = care?.asset

    AuthenticatedShell(
        title = asset?.name?.takeIf { it.isNotBlank() } ?: "Asset",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (state.isLoading && care == null) {
            AssetLoading()
            return@AuthenticatedShell
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (asset != null && asset.code.isNotBlank()) {
                Text(
                    text = asset.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }

            state.message?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetNotice(text = it, tone = AssetTone.Success, modifier = Modifier.weight(1f))
                    LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                }
            }
            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            // ⚠️ The one line somebody opened this screen to read, above
            // everything else — where the thing is and who answers for it.
            if (care != null) {
                AssetSummaryBar(
                    parts = buildList {
                        val held = care.heldBy
                        add(
                            if (held != null) {
                                AssetSummaryPart(
                                    "With ${held.who}" +
                                        (if (held.location.isNotBlank() && held.who != held.location) {
                                            " at ${held.location}"
                                        } else {
                                            ""
                                        }) +
                                        " since ${onTheDay(held.since)}",
                                    strong = true,
                                )
                            } else {
                                AssetSummaryPart(
                                    "Not issued to anybody — the register says it is at " +
                                        (asset?.location?.takeIf { it.isNotBlank() }
                                            ?: "no stated place"),
                                    AssetTone.Muted,
                                )
                            },
                        )
                        val lastCount = care.verifications.firstOrNull()
                        add(
                            AssetSummaryPart(
                                if (lastCount != null) {
                                    "Last counted ${onTheDay(lastCount.countedOn)} · " +
                                        (FOUND_NAMES[lastCount.found] ?: lastCount.found)
                                } else {
                                    "Never counted"
                                },
                                AssetTone.Muted,
                            ),
                        )
                        if (care.maintenanceTotal > 0) {
                            add(
                                AssetSummaryPart(
                                    "Kept for ${AmountFormat.format(care.maintenanceTotal)} over " +
                                        "${care.maintenance.size} visit(s)",
                                    AssetTone.Muted,
                                ),
                            )
                        }
                    },
                )
            }

            AssetChoiceRow {
                AssetChoice(
                    label = "Who has it",
                    selected = state.section == "custody",
                    onClick = { viewModel.onSection("custody") },
                )
                AssetChoice(
                    label = "Was it there",
                    selected = state.section == "count",
                    onClick = { viewModel.onSection("count") },
                )
                AssetChoice(
                    label = "Upkeep",
                    selected = state.section == "upkeep",
                    onClick = { viewModel.onSection("upkeep") },
                )
            }

            when (state.section) {
                "custody" -> CustodySection(state, viewModel)
                "count" -> CountSection(state, viewModel)
                else -> UpkeepSection(state, viewModel)
            }
        }
    }
}

@Composable
private fun CustodySection(state: AssetCareUiState, viewModel: AssetCareViewModel) {
    val actionOptions = remember {
        listOf(
            SelectorOption("issued", "Handing it out"),
            SelectorOption("returned", "Taking it back"),
        )
    }
    val peopleOptions = remember(state.people) {
        listOf(SelectorOption("", "Not to a person")) +
            state.people.map { SelectorOption(it.id.toString(), it.name) }
    }
    val branchOptions = remember(state.branches) {
        listOf(SelectorOption("", "Not to a branch")) +
            state.branches.map { SelectorOption(it.id.toString(), it.name) }
    }
    val issuing = state.hand.action == "issued"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppSelectDropdown(
            label = "What is happening",
            options = actionOptions,
            selected = actionOptions.firstOrNull { it.id == state.hand.action }
                ?: actionOptions.first(),
            onSelected = { picked -> viewModel.onHand { it.copy(action = picked.id) } },
        )
        AssetDateField(
            label = "On",
            value = state.hand.onDate,
            onPicked = { value -> viewModel.onHand { it.copy(onDate = value) } },
            modifier = Modifier.fillMaxWidth(),
        )

        if (issuing) {
            // ⚠️ Both boxes are CHOSEN FROM A LIST, never typed. Free text let
            // one man be "Rafiq", "Rafiq, driver" and "rafique" across three
            // handovers, and the asset he was holding could not be found under
            // any of them. An empty list says so rather than offering an empty
            // box that cannot be filled.
            if (state.people.isNotEmpty()) {
                AppSelectDropdown(
                    label = "To whom",
                    options = peopleOptions,
                    selected = peopleOptions.firstOrNull { it.id == state.hand.employeeId }
                        ?: peopleOptions.first(),
                    onSelected = { picked -> viewModel.onHand { it.copy(employeeId = picked.id) } },
                )
            } else {
                CareHint("To whom: nobody on the staff list yet. Add employees first, or send it to a branch instead.")
            }

            if (state.branches.isNotEmpty()) {
                AppSelectDropdown(
                    label = "Or which branch",
                    options = branchOptions,
                    selected = branchOptions.firstOrNull { it.id == state.hand.toBranchId }
                        ?: branchOptions.first(),
                    onSelected = { picked -> viewModel.onHand { it.copy(toBranchId = picked.id) } },
                )
            } else {
                CareHint("Or which branch: no branch you can issue to.")
            }

            AppTextField(
                value = state.hand.conditionNote,
                onValueChange = { value -> viewModel.onHand { it.copy(conditionNote = value) } },
                label = "Two scratches on the near-side door",
                caption = "What it looks like now",
                modifier = Modifier.fillMaxWidth(),
            )
            // The only defence when it comes back broken: what it looked like
            // when it left.
            CareHint("Written down at the handover, not after the argument.")
        } else {
            AppTextField(
                value = state.hand.conditionNote,
                onValueChange = { value -> viewModel.onHand { it.copy(conditionNote = value) } },
                label = "Wing mirror broken",
                caption = "What it came back like",
                modifier = Modifier.fillMaxWidth(),
            )
            CareHint("Worth a line even when nothing is wrong.")
        }

        PrimaryButton(
            text = if (issuing) "Hand it out" else "Take it back",
            onClick = viewModel::handOver,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        AssetPanel(title = "Handovers") {
            val custody = state.care?.custody.orEmpty()
            if (custody.isEmpty()) {
                CareHint(
                    "Never handed out. Everything issued and returned shows here, oldest at " +
                        "the bottom.",
                )
            } else {
                custody.forEach { row ->
                    AssetLine(
                        label = onTheDay(row.onDate) + " · " +
                            (if (row.action == "issued") "out to " else "back from ") + row.holder,
                        sublabel = row.conditionNote,
                        value = if (row.action == "issued") "Issued" else "Returned",
                    )
                }
            }
        }
    }
}

@Composable
private fun CountSection(state: AssetCareUiState, viewModel: AssetCareViewModel) {
    val foundOptions = remember {
        listOf(
            SelectorOption("found", "There"),
            SelectorOption("missing", "Not there"),
            SelectorOption("damaged", "There but damaged"),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AssetDateField(
            label = "Counted on",
            value = state.count.countedOn,
            onPicked = { value -> viewModel.onCount { it.copy(countedOn = value) } },
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "What the count found",
            options = foundOptions,
            selected = foundOptions.firstOrNull { it.id == state.count.found }
                ?: foundOptions.first(),
            onSelected = { picked -> viewModel.onCount { it.copy(found = picked.id) } },
        )
        AppTextField(
            value = state.count.location,
            onValueChange = { value -> viewModel.onCount { it.copy(location = value) } },
            label = state.care?.asset?.location?.takeIf { it.isNotBlank() } ?: "Second floor store",
            caption = "Where it actually was",
            modifier = Modifier.fillMaxWidth(),
        )
        CareHint("Often not where the register says.")
        AppTextField(
            value = state.count.note,
            onValueChange = { value -> viewModel.onCount { it.copy(note = value) } },
            label = "Note",
            caption = "Note",
            modifier = Modifier.fillMaxWidth(),
        )

        // ⚠️ Said plainly, because ticking "not there" looks like it should do
        // something and must not.
        if (state.count.found != "found") {
            AssetNotice(
                text = "This records what the count found and nothing else. It does not write " +
                    "the asset off — somebody decides that, and it is done from the register " +
                    "with its own entries.",
                tone = AssetTone.Warning,
            )
        }

        PrimaryButton(
            text = "Record it",
            onClick = viewModel::record,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        AssetPanel(title = "Counts") {
            val counts = state.care?.verifications.orEmpty()
            if (counts.isEmpty()) {
                CareHint(
                    "Never counted. One row per round — counting it twice in one round corrects " +
                        "the first answer rather than adding a second.",
                )
            } else {
                counts.forEach { row ->
                    AssetLine(
                        label = onTheDay(row.countedOn) +
                            (if (row.location.isNotBlank()) " · seen at ${row.location}" else ""),
                        sublabel = row.note,
                        value = FOUND_NAMES[row.found] ?: row.found,
                        valueTone = if (row.found == "found") AssetTone.Success else AssetTone.Danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpkeepSection(state: AssetCareUiState, viewModel: AssetCareViewModel) {
    val kindOptions = remember {
        listOf(
            SelectorOption("service", "Service"),
            SelectorOption("repair", "Repair"),
            SelectorOption("inspection", "Inspection"),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AssetDateField(
            label = "On",
            value = state.visit.onDate,
            onPicked = { value -> viewModel.onVisit { it.copy(onDate = value) } },
            modifier = Modifier.fillMaxWidth(),
        )
        AppSelectDropdown(
            label = "What kind",
            options = kindOptions,
            selected = kindOptions.firstOrNull { it.id == state.visit.kind } ?: kindOptions.first(),
            onSelected = { picked -> viewModel.onVisit { it.copy(kind = picked.id) } },
        )
        AppTextField(
            value = state.visit.description,
            onValueChange = { value -> viewModel.onVisit { it.copy(description = value) } },
            label = "Clutch plate replaced",
            caption = "What was done",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.visit.vendor,
            onValueChange = { value -> viewModel.onVisit { it.copy(vendor = value) } },
            label = "Karim Motors",
            caption = "By whom",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.visit.cost,
                onValueChange = { value -> viewModel.onVisit { it.copy(cost = value) } },
                label = "0",
                caption = "What it cost",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.visit.daysDown,
                onValueChange = { value -> viewModel.onVisit { it.copy(daysDown = value) } },
                label = "0",
                caption = "Days out of use",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        CareHint("Kept as history. Not posted.")
        AssetDateField(
            label = "Due again",
            value = state.visit.nextDueOn,
            onPicked = { value -> viewModel.onVisit { it.copy(nextDueOn = value) } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Nothing chases it",
        )
        // Promising a reminder that cannot arrive would be worse than showing
        // the date: nothing on these servers runs on a clock.
        CareHint("Shown here and on the list. Nothing chases it.")

        // The sentence that keeps the same money out of the books twice.
        AssetNotice(
            text = "Nothing here is posted. The bill itself goes through an ordinary expense " +
                "voucher, as it always has. What this keeps is the service history — so that " +
                "\"is this worth keeping\" has an answer beside the asset.",
            tone = AssetTone.Info,
        )

        PrimaryButton(
            text = "Write it down",
            onClick = viewModel::logVisit,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        AssetPanel(title = "Upkeep") {
            val visits = state.care?.maintenance.orEmpty()
            if (visits.isEmpty()) {
                CareHint("Nothing written down yet.")
            } else {
                visits.forEach { row ->
                    AssetLine(
                        label = onTheDay(row.onDate) + " · " +
                            (KIND_NAMES[row.kind] ?: row.kind) + " · " + row.description,
                        sublabel = buildString {
                            if (row.vendor.isNotBlank()) append(row.vendor)
                            if (row.daysDown > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("${row.daysDown} day(s) down")
                            }
                            if (row.nextDueOn.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("due again ${onTheDay(row.nextDueOn)}")
                            }
                        },
                        value = if (row.cost > 0) AmountFormat.format(row.cost) else "—",
                    )
                }
                // Against the cost, which is the comparison the history is for.
                AssetLine(
                    label = "Kept for — against a cost of " +
                        AmountFormat.format(state.care?.asset?.cost ?: 0.0),
                    value = AmountFormat.format(state.care?.maintenanceTotal ?: 0.0),
                    strong = true,
                    divider = false,
                )
            }
        }
    }
}

@Composable
private fun CareHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
    )
}
