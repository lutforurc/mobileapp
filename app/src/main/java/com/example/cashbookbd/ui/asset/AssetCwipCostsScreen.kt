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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.data.repository.AssetCwipCostInput
import com.example.cashbookbd.data.repository.AssetCwipCostLine
import com.example.cashbookbd.data.repository.AssetCwipRepository
import com.example.cashbookbd.data.repository.AssetCwipWork
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetCwipCostsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val work: AssetCwipWork? = null,
    val lines: List<AssetCwipCostLine> = emptyList(),
    val total: Double = 0.0,
    val line: AssetCwipCostInput = AssetCwipCostInput(
        onDate = todayApi(),
        description = "",
        amount = "",
    ),
    val pendingDelete: AssetCwipCostLine? = null,
    /**
     * Bumped on every write that landed. The list behind this screen is told
     * once, on the way back, so it re-reads its heaps rather than showing a
     * total that no longer matches what is on this screen.
     */
    val changes: Int = 0,
    val sessionExpired: Boolean = false,
)

class AssetCwipCostsViewModel(
    private val repository: AssetCwipRepository,
    private val workId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetCwipCostsUiState())
    val uiState: StateFlow<AssetCwipCostsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchCosts(workId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        work = result.data.work,
                        lines = result.data.lines,
                        total = result.data.total,
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

    fun onLine(change: (AssetCwipCostInput) -> AssetCwipCostInput) =
        _uiState.update { it.copy(line = change(it.line)) }

    /** Writes one more thing down. Posts nothing — the bill had its own voucher. */
    fun addLine() {
        val line = _uiState.value.line
        if (line.description.isBlank() || (line.amount.trim().toDoubleOrNull() ?: 0.0) <= 0.0) {
            _uiState.update { it.copy(error = "What was it, and how much?") }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.addCost(workId, line)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = result.data,
                            changes = it.changes + 1,
                            line = AssetCwipCostInput(
                                onDate = todayApi(),
                                description = "",
                                amount = "",
                            ),
                        )
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        // Usually the refusal: the work has been finished, so its
                        // cost is what the asset was brought in at and cannot
                        // change. The server's sentence says where it belongs now.
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askDelete(line: AssetCwipCostLine) = _uiState.update { it.copy(pendingDelete = line) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val line = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(isSaving = true, pendingDelete = null) }
        viewModelScope.launch {
            when (val result = repository.removeCost(line.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, message = result.data, changes = it.changes + 1)
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

    fun onMessageShown() = _uiState.update { it.copy(message = null, error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, workId: Long) = viewModelFactory {
            initializer {
                AssetCwipCostsViewModel(
                    AssetCwipRepository.get(context.applicationContext),
                    workId,
                )
            }
        }
    }
}

/**
 * What one heap is made of.
 *
 * ⚠️ NOTHING HERE IS POSTED, and it is said where the money is typed rather than
 * only at the top. Every bill went through an ordinary voucher coded to the
 * work-in-progress head, so the money is already in the ledger; writing it again
 * would double the cost of the building. What this buys is a heap somebody can
 * read line by line — and, on the day it is finished, a capitalisation that can
 * be checked instead of taken on trust.
 *
 * ⚠️ A FINISHED WORK IS READ-ONLY. Its total is what the asset was brought in
 * at, and a line added or taken out afterwards would leave the heap disagreeing
 * with the voucher — so the form and the remove buttons are simply not there.
 */
@Composable
fun AssetCwipCostsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    workId: Long,
    modifier: Modifier = Modifier,
    viewModel: AssetCwipCostsViewModel = viewModel(
        factory = AssetCwipCostsViewModel.provideFactory(LocalContext.current, workId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    // Told once per write, not on the way out: the list behind this screen has a
    // total on it, and a stale total is worse than no total.
    LaunchedEffect(state.changes) {
        if (state.changes > 0) {
            navController.reportAssetSaved(state.message.orEmpty().ifBlank { "Saved" })
        }
    }

    val work = state.work
    val isOpen = work?.isOpen != false

    AuthenticatedShell(
        title = work?.name?.takeIf { it.isNotBlank() } ?: "What has gone into it",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (state.isLoading && work == null) {
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
            work?.let { WorkHeader(it) }

            state.message?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetNotice(text = it, tone = AssetTone.Success, modifier = Modifier.weight(1f))
                    LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                }
            }
            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            if (isOpen) {
                AssetPanel(title = "Write one more thing down") {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssetDateField(
                            label = "On",
                            value = state.line.onDate,
                            onPicked = { value -> viewModel.onLine { it.copy(onDate = value) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = state.line.description,
                            onValueChange = { value ->
                                viewModel.onLine { it.copy(description = value) }
                            },
                            label = "Foundation and piling",
                            caption = "What was it",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = state.line.vendor,
                            onValueChange = { value -> viewModel.onLine { it.copy(vendor = value) } },
                            label = "Rahman Construction",
                            caption = "By whom",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = state.line.amount,
                            onValueChange = { value -> viewModel.onLine { it.copy(amount = value) } },
                            label = "0",
                            caption = "How much",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // ⚠️ Said beside the money box, where somebody about to
                        // type a figure can read it — not only in the paragraph
                        // at the top of the previous screen.
                        Text(
                            text = "Recorded, not posted. The bill itself goes through an " +
                                "ordinary voucher coded to the work-in-progress head, as usual.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                        AppTextField(
                            value = state.line.note,
                            onValueChange = { value -> viewModel.onLine { it.copy(note = value) } },
                            label = "Anything worth remembering",
                            caption = "Note",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            text = "Write it down",
                            onClick = viewModel::addLine,
                            isLoading = state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                AssetNotice(
                    text = "This work has been finished, so its cost cannot change. Anything " +
                        "spent on it now belongs to the asset it became — as an upkeep entry, " +
                        "or as a new work if it is an addition.",
                    tone = AssetTone.Warning,
                )
            }

            ReportTable(
                columns = costColumns(canRemove = isOpen, onDelete = viewModel::askDelete),
                data = state.lines,
                footerRows = costFooter(state.lines.size, state.total),
                noDataMessage = "Nothing written down yet.",
                scrollable = false,
            )

            SecondaryButton(
                text = "Back",
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.pendingDelete?.let { line ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Take this line out?") },
            text = {
                Text(
                    "\"${line.description}\" — ${AmountFormat.format(line.amount)} — comes out of " +
                        "the heap. Nothing in the ledger moves: this list was never posted.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Take it out",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isSaving,
                    isLoading = state.isSaving,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancelDelete) },
        )
    }
}

/** What is being built, where its cost sits, and what it will become. */
@Composable
private fun WorkHeader(work: AssetCwipWork) {
    AssetPanel(title = work.name) {
        AssetLine(
            label = "Code",
            value = work.code.ifBlank { "—" },
        )
        AssetLine(
            label = "Its cost sits in",
            value = work.cwipHeadName.ifBlank { "not chosen" },
            valueTone = if (work.cwipHeadName.isBlank()) AssetTone.Danger else AssetTone.Plain,
        )
        AssetLine(
            label = "What it becomes",
            value = work.categoryName.ifBlank { "nothing chosen" },
            valueTone = if (work.categoryName.isBlank()) AssetTone.Danger else AssetTone.Plain,
        )
        AssetLine(
            label = "Started",
            sublabel = if (work.expectedOn.isNotBlank()) {
                "expected ${onTheDay(work.expectedOn)} — shown, never enforced"
            } else {
                ""
            },
            value = onTheDay(work.startedOn),
        )
        AssetLine(
            label = if (work.isOpen) "Being built" else "Finished",
            value = if (work.isOpen) "—" else onTheDay(work.capitalisedOn),
            valueTone = if (work.isOpen) AssetTone.Muted else AssetTone.Success,
            strong = true,
            divider = false,
        )
        if (work.description.isNotBlank() || work.notes.isNotBlank()) {
            Text(
                text = listOf(work.description, work.notes).filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun costColumns(
    canRemove: Boolean,
    onDelete: (AssetCwipCostLine) -> Unit,
): List<ReportColumn<AssetCwipCostLine>> {
    val muted = MaterialTheme.appColors.textMuted
    val danger = MaterialTheme.appColors.danger
    return listOf(
        ReportColumn("ON", ReportColWidth.Fixed(96.dp), TextAlign.Center) { row, _ ->
            cellText(onTheDay(row.onDate), TextAlign.Center)
        },
        ReportColumn("WHAT WAS IT", ReportColWidth.Fixed(200.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    val under = listOfNotNull(
                        row.vendor.takeIf { it.isNotBlank() },
                        row.note.takeIf { it.isNotBlank() },
                        row.mainTrxId.takeIf { it.isNotBlank() }?.let { "voucher $it" },
                    ).joinToString(" · ")
                    if (under.isNotBlank()) {
                        Text(
                            text = under,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        ReportColumn("HOW MUCH", ReportColWidth.Fixed(120.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.formatOrDash(row.amount), TextAlign.End)
        },
        ReportColumn("", ReportColWidth.Fixed(70.dp), TextAlign.Center) { row, _ ->
            if (!canRemove) {
                ReportTableCell.Empty
            } else {
                ReportTableCell.Slot {
                    Row(horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { onDelete(row) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Take this line out",
                                tint = danger,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The heap so far — the figure the whole screen exists to explain. */
@Composable
private fun costFooter(count: Int, total: Double): List<List<ReportFooterCell>> {
    if (count == 0) return emptyList()
    return listOf(
        listOf(
            ReportFooterCell(cellText("The heap so far", bold = true), colSpan = 2),
            ReportFooterCell(cellText(AmountFormat.format(total), TextAlign.End, bold = true)),
            ReportFooterCell(ReportTableCell.Empty),
        ),
    )
}
