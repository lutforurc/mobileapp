package com.example.cashbookbd.ui.legacy

import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.data.repository.LegacyArchive
import com.example.cashbookbd.data.repository.LegacyPartyRow
import com.example.cashbookbd.data.repository.LegacyRecordRepository
import com.example.cashbookbd.data.repository.LegacySourceOption
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen 1 of "Old Software" — a browse-first list of parties from an archived
 * old ERP install: it loads with an empty query (nothing to type first), and a
 * search narrows the same paginated list. Nothing here posts to the books.
 */

data class LegacySearchUiState(
    val archive: LegacyArchive,
    val sources: List<LegacySourceOption> = emptyList(),
    val selectedSource: String = "",
    val query: String = "",
    val perPage: Int = 20,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val rows: List<LegacyPartyRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canPrev get() = currentPage > 1 && !isLoading
    val canNext get() = currentPage < lastPage && !isLoading
}

class LegacySearchViewModel(
    private val archive: LegacyArchive,
    private val repository: LegacyRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegacySearchUiState(archive = archive))
    val uiState: StateFlow<LegacySearchUiState> = _uiState.asStateFlow()

    init {
        loadSources()
        load(1)
    }

    private fun loadSources() {
        viewModelScope.launch {
            when (val result = repository.fetchSources(archive)) {
                is Resource.Success -> _uiState.update { it.copy(sources = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onQuery(value: String) = _uiState.update { it.copy(query = value) }
    fun onSource(value: String) = _uiState.update { it.copy(selectedSource = value) }

    fun reset() {
        _uiState.update { it.copy(query = "", selectedSource = "") }
        load(1)
    }

    fun load(page: Int = _uiState.value.currentPage) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (
                val result = repository.searchParties(
                    archive = archive,
                    q = state.query,
                    source = state.selectedSource,
                    page = page,
                    perPage = state.perPage,
                )
            ) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
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

    fun nextPage() { if (_uiState.value.canNext) load(_uiState.value.currentPage + 1) }
    fun prevPage() { if (_uiState.value.canPrev) load(_uiState.value.currentPage - 1) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, archive: LegacyArchive) = viewModelFactory {
            initializer {
                LegacySearchViewModel(
                    archive = archive,
                    repository = ServiceLocator.provideLegacyRecordRepository(context.applicationContext),
                )
            }
        }
    }
}

@Composable
fun LegacyRecordSearchScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    archive: LegacyArchive,
    modifier: Modifier = Modifier,
    viewModel: LegacySearchViewModel = viewModel(
        key = archive.endpointPrefix,
        factory = LegacySearchViewModel.provideFactory(LocalContext.current, archive),
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
        title = archive.title,
        currentRoute = Routes.LEGACY_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "An old system's own records — nothing here posts to the books.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    label = "Name or mobile number",
                    caption = "Search",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.sources.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    AppSelectDropdown(
                        label = "Source",
                        options = state.sources.map { SelectorOption(it.source, it.label) },
                        selected = state.sources.firstOrNull { it.source == state.selectedSource }
                            ?.let { SelectorOption(it.source, it.label) },
                        onSelected = { viewModel.onSource(it.id) },
                        placeholder = "All sources",
                    )
                }
                Spacer(Modifier.height(10.dp))
                FilterActions(
                    onApply = { viewModel.load(1) },
                    onReset = viewModel::reset,
                    canApply = !state.isLoading,
                    isLoading = state.isLoading,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                state.isLoading && state.rows.isEmpty() -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

                state.error != null -> Box(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = partyColumns(showSource = state.sources.size > 1) { row ->
                            navController.navigate(
                                Routes.legacyParty(archive, row.partyType, row.source, row.legacyId)
                            )
                        },
                        data = state.rows,
                        noDataMessage = "No parties found in this old system.",
                    )
                }
            }

            if (state.lastPage > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LinkButton(text = "Prev", onClick = viewModel::prevPage, enabled = state.canPrev)
                    Text(
                        text = "Page ${state.currentPage} of ${state.lastPage}  •  ${state.total} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    LinkButton(text = "Next", onClick = viewModel::nextPage, enabled = state.canNext)
                }
            }
        }
    }
}

private fun partyColumns(
    showSource: Boolean,
    onOpen: (LegacyPartyRow) -> Unit,
): List<ReportColumn<LegacyPartyRow>> = buildList {
    add(
        ReportColumn("NAME", ReportColWidth.Fixed(160.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(
                    modifier = Modifier
                        .clickable(onClick = { onOpen(row) })
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = row.name.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
    if (showSource) {
        add(
            ReportColumn("SYSTEM", ReportColWidth.Fixed(100.dp)) { row, _ ->
                cellText(row.source.ifBlank { "-" }, maxLines = 1)
            },
        )
    }
    add(
        ReportColumn("ADDRESS", ReportColWidth.Fixed(160.dp)) { row, _ ->
            cellText(row.address.ifBlank { "-" }, maxLines = 2)
        },
    )
    add(
        ReportColumn("MOBILE", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(row.phone.ifBlank { "-" }, maxLines = 1)
        },
    )
    add(
        ReportColumn("DOCS", ReportColWidth.Fixed(70.dp)) { row, _ ->
            cellText(row.documents.toString(), align = TextAlign.Center)
        },
    )
    add(
        ReportColumn("BALANCE", ReportColWidth.Fixed(110.dp)) { row, _ ->
            cellText(AmountFormat.format(row.balance), align = TextAlign.End)
        },
    )
}
