package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.data.repository.AssetRound
import com.example.cashbookbd.data.repository.AssetRoundRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
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

/** The three answers a count can give, and how each is meant to read. */
private val COUNT_CHOICES = listOf(
    Triple("found", "There", AssetTone.Success),
    Triple("damaged", "Damaged", AssetTone.Warning),
    Triple("missing", "Not there", AssetTone.Danger),
)

data class AssetVerificationUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val countedOn: String = todayApi(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val round: AssetRound? = null,
    /** Only what is still to be looked at — the way the round is actually walked. */
    val onlyLeft: Boolean = false,
    /** Typed against one row, so the store room's name is not lost on the way. */
    val seenAt: Map<Long, String> = emptyMap(),
    val sessionExpired: Boolean = false,
) {
    val rows: List<AssetRoundRow>
        get() = round?.rows.orEmpty().filter { !onlyLeft || it.found == null }
}

class AssetVerificationViewModel(
    private val repository: AssetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetVerificationUiState())
    val uiState: StateFlow<AssetVerificationUiState> = _uiState.asStateFlow()

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
            val result = repository.fetchVerificationRound(state.selectedBranch?.id, state.countedOn)
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, round = result.data)
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
        _uiState.update { it.copy(selectedBranch = branch, seenAt = emptyMap()) }
        load()
    }

    /** A round is named by its date: change it and this is a different round. */
    fun onCountedOn(value: String) {
        _uiState.update { it.copy(countedOn = value, seenAt = emptyMap()) }
        load()
    }

    fun onOnlyLeft(value: Boolean) = _uiState.update { it.copy(onlyLeft = value) }

    fun onSeenAt(assetId: Long, value: String) =
        _uiState.update { it.copy(seenAt = it.seenAt + (assetId to value)) }

    /**
     * Ticks one asset.
     *
     * ⚠️ The row changes on screen before the server answers, and goes back if
     * it refuses. A count is walked at walking pace — a spinner between every
     * chair and the next would make the screen slower than the paper it replaces.
     *
     * ⚠️ [found] of null takes the tick back, leaving the row not looked at yet:
     * a mis-click must be undoable, because on this screen a wrong "Not there"
     * is the start of a search for something that never went missing.
     */
    fun tick(row: AssetRoundRow, found: String?) {
        val before = _uiState.value.round ?: return
        val typed = _uiState.value.seenAt[row.assetId]
        _uiState.update { state ->
            state.copy(
                round = before.copy(
                    rows = before.rows.map { one ->
                        if (one.assetId == row.assetId) {
                            one.copy(found = found, seenAt = typed ?: one.seenAt)
                        } else {
                            one
                        }
                    },
                ),
            )
        }
        viewModelScope.launch {
            val result = repository.saveVerification(
                assetId = row.assetId,
                countedOn = _uiState.value.countedOn,
                found = found,
                location = typed.orEmpty(),
                note = "",
            )
            when (result) {
                // The summary at the top has to move with the ticks, so it is
                // re-read rather than counted twice in two places.
                is Resource.Success -> load()
                is Resource.Error -> _uiState.update {
                    it.copy(
                        round = before,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetVerificationViewModel(
                    repository = AssetRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The count: walking round the building and ticking off what is actually there.
 *
 * ⚠️ THE COLUMN THAT MATTERS IS THE EMPTY ONE. A count is not finished when
 * everything ticked was found — it is finished when nothing is left unlooked at.
 * So the round lists every asset in the branch and says how many are still
 * untouched at the top, where it cannot be missed.
 *
 * ⚠️ NOTHING HERE WRITES ANYTHING OFF. "Not there" is a finding, not a decision:
 * somebody takes the list of missing things and decides, and writing one off is
 * its own act with its own entries, done from the register.
 */
@Composable
fun AssetVerificationScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetVerificationViewModel = viewModel(
        factory = AssetVerificationViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Verification",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssetBranchField(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranch,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssetDateField(
                    label = "The count of",
                    value = state.countedOn,
                    onPicked = viewModel::onCountedOn,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.onlyLeft, onCheckedChange = viewModel::onOnlyLeft)
                    Text(
                        text = "Only what is left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                state.error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetNotice(text = it, tone = AssetTone.Danger, modifier = Modifier.weight(1f))
                        LinkButton(text = "Dismiss", onClick = viewModel::onErrorShown)
                    }
                }

                // ⚠️ Untouched first and in words, because it is the number that
                // says whether the count is finished.
                state.round?.let { round ->
                    AssetSummaryBar(
                        parts = buildList {
                            add(
                                AssetSummaryPart(
                                    "${round.notLooked} of ${round.total} not looked at yet",
                                    strong = true,
                                ),
                            )
                            add(AssetSummaryPart("There ${round.found}", AssetTone.Success))
                            add(AssetSummaryPart("Damaged ${round.damaged}", AssetTone.Warning))
                            add(AssetSummaryPart("Not there ${round.missing}", AssetTone.Danger))
                            if (round.missing > 0) {
                                add(
                                    AssetSummaryPart(
                                        "Nothing is written off from here — take the missing " +
                                            "ones to whoever decides.",
                                        AssetTone.Muted,
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            when {
                state.isLoading && state.round == null -> AssetLoading()
                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = roundColumns(
                            seenAt = state.seenAt,
                            onSeenAt = viewModel::onSeenAt,
                            onTick = viewModel::tick,
                        ),
                        data = state.rows,
                        noDataMessage = if (state.onlyLeft) {
                            "Nothing left — everything in this branch has been looked at."
                        } else {
                            "Nothing in the register for this branch yet."
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun roundColumns(
    seenAt: Map<Long, String>,
    onSeenAt: (Long, String) -> Unit,
    onTick: (AssetRoundRow, String?) -> Unit,
): List<ReportColumn<AssetRoundRow>> {
    val muted = MaterialTheme.appColors.textMuted
    return listOf(
        ReportColumn("ASSET", ReportColWidth.Fixed(160.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(
                        text = row.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        },
        ReportColumn("REGISTER SAYS", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.registerSays.ifBlank { "—" }, color = muted, maxLines = 2)
        },
        // Filled in only where it differs — which is the finding that quietly
        // matters most, and the one a tick alone loses.
        ReportColumn("ACTUALLY AT", ReportColWidth.Fixed(180.dp)) { row, _ ->
            ReportTableCell.Slot {
                Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    AppTextField(
                        value = seenAt[row.assetId] ?: row.seenAt,
                        onValueChange = { onSeenAt(row.assetId, it) },
                        label = row.registerSays.ifBlank { "Where it was" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        ReportColumn("WHAT THE COUNT FOUND", ReportColWidth.Fixed(230.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                AssetChoiceRow(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    COUNT_CHOICES.forEach { (id, label, tone) ->
                        val picked = row.found == id
                        AssetChoice(
                            label = label,
                            selected = picked,
                            tone = tone,
                            // Tapping the one already chosen takes it back.
                            onClick = { onTick(row, if (picked) null else id) },
                        )
                    }
                }
            }
        },
    )
}
