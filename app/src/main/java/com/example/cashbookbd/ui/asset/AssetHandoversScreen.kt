package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetMovementRow
import com.example.cashbookbd.data.repository.AssetMovements
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppTextField
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

data class AssetHandoversUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val asOf: String = todayApi(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val data: AssetMovements? = null,
    /** Typed here and matched here — the list is already on the phone. */
    val search: String = "",
    val sessionExpired: Boolean = false,
) {
    val rows: List<AssetMovementRow>
        get() {
            val needle = search.trim().lowercase()
            val all = data?.out.orEmpty()
            if (needle.isEmpty()) return all
            return all.filter { row ->
                listOf(row.name, row.code, row.with, row.at)
                    .any { it.lowercase().contains(needle) }
            }
        }
}

class AssetHandoversViewModel(
    private val repository: AssetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetHandoversUiState())
    val uiState: StateFlow<AssetHandoversUiState> = _uiState.asStateFlow()

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
            when (val result = repository.fetchMovements(state.selectedBranch?.id, state.asOf)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, data = result.data)
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

    fun onAsOf(value: String) {
        _uiState.update { it.copy(asOf = value) }
        load()
    }

    fun onSearch(value: String) = _uiState.update { it.copy(search = value) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AssetHandoversViewModel(
                    repository = AssetRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The handover register: what is out of the building, and who signed for it.
 *
 * ⚠️ WHAT IS OUT IS WORKED OUT ON THE SERVER, from each asset's latest movement
 * — not counted again here. Two ways of deciding who is holding something is two
 * answers to give an auditor.
 *
 * ⚠️ AND IT IS A RECORD, NOT AN INSTRUCTION. A thing out for two hundred days is
 * shown as out for two hundred days; nothing here chases anybody or decides that
 * a holder has kept it too long. Somebody reads the register and decides.
 *
 * One asset's story is told by that asset's own screen: tapping a row opens the
 * care screen the register already uses, rather than a second history to drift
 * from the first.
 */
@Composable
fun AssetHandoversScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetHandoversViewModel = viewModel(
        factory = AssetHandoversViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    // A return taken on the care screen has to change the list that sent you
    // into it, or the screen would sit there insisting the thing is still out.
    OnAssetSaved(navController) { viewModel.load() }

    AuthenticatedShell(
        title = "Handovers",
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
                    label = "As it stood on",
                    value = state.asOf,
                    onPicked = viewModel::onAsOf,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearch,
                    label = "An asset, a person, a branch",
                    caption = "Find",
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

                // ⚠️ What is out comes first and in words. It is the number the
                // register is opened for; how many movements were recorded is
                // background.
                state.data?.let { data ->
                    AssetSummaryBar(
                        parts = listOf(
                            AssetSummaryPart(
                                "${data.outCount} of ${data.assets} out with somebody",
                                strong = true,
                            ),
                            AssetSummaryPart("In hand ${data.inHand}", AssetTone.Success),
                            AssetSummaryPart("${data.movements} movements recorded", AssetTone.Muted),
                        ),
                    )
                }
            }

            when {
                state.isLoading && state.data == null -> AssetLoading()
                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = handoverColumns { row ->
                            navController.navigate(AssetMenu.care(row.assetId))
                        },
                        data = state.rows,
                        noDataMessage = if (state.search.isBlank()) {
                            "Everything is in hand."
                        } else {
                            "Nothing out matches that."
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun handoverColumns(
    onOpen: (AssetMovementRow) -> Unit,
): List<ReportColumn<AssetMovementRow>> {
    val muted = MaterialTheme.appColors.textMuted
    return listOf(
        ReportColumn("ASSET", ReportColWidth.Fixed(170.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(
                    modifier = Modifier
                        .clickable { onOpen(row) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
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
        ReportColumn("WITH", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.with.ifBlank { "—" }, maxLines = 2)
        },
        ReportColumn("AT", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.at.ifBlank { "—" }, color = muted, maxLines = 2)
        },
        ReportColumn("SINCE", ReportColWidth.Fixed(100.dp), TextAlign.Center) { row, _ ->
            cellText(onTheDay(row.since))
        },
        // Plain, however long it has been. A register that shouted at ninety
        // days would be reporting a rule nobody has set.
        ReportColumn("DAYS OUT", ReportColWidth.Fixed(90.dp), TextAlign.End) { row, _ ->
            cellText(row.daysOut.toString())
        },
        ReportColumn("", ReportColWidth.Fixed(56.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                IconButton(onClick = { onOpen(row) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${row.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
