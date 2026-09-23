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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.example.cashbookbd.data.repository.LegacyLedgerRow
import com.example.cashbookbd.data.repository.LegacyPartyLedger
import com.example.cashbookbd.data.repository.LegacyRecordRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen 2 — one party's whole ledger from an archived old ERP, oldest to newest. */

data class LegacyLedgerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ledger: LegacyPartyLedger? = null,
    val sessionExpired: Boolean = false,
)

class LegacyPartyLedgerViewModel(
    private val archive: LegacyArchive,
    private val legacyId: String,
    private val partyType: String,
    private val source: String,
    private val repository: LegacyRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegacyLedgerUiState())
    val uiState: StateFlow<LegacyLedgerUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchPartyLedger(archive, legacyId, partyType, source)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, ledger = result.data) }
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(
            context: Context,
            archive: LegacyArchive,
            legacyId: String,
            partyType: String,
            source: String,
        ) = viewModelFactory {
            initializer {
                LegacyPartyLedgerViewModel(
                    archive = archive,
                    legacyId = legacyId,
                    partyType = partyType,
                    source = source,
                    repository = ServiceLocator.provideLegacyRecordRepository(context.applicationContext),
                )
            }
        }
    }
}

@Composable
fun LegacyPartyLedgerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    archive: LegacyArchive,
    legacyId: String,
    partyType: String,
    source: String,
    modifier: Modifier = Modifier,
    viewModel: LegacyPartyLedgerViewModel = viewModel(
        key = "${archive.endpointPrefix}:$partyType:$source:$legacyId",
        factory = LegacyPartyLedgerViewModel.provideFactory(
            LocalContext.current, archive, legacyId, partyType, source,
        ),
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
        title = "Party Ledger",
        currentRoute = Routes.LEGACY_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::load, compact = true)
                    }
                }

                else -> state.ledger?.let { ledger ->
                    Column(Modifier.fillMaxSize()) {
                        LedgerHeader(ledger)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.weight(1f)) {
                            ReportTable(
                                columns = ledgerColumns { row ->
                                    navController.navigate(Routes.legacyInvoice(archive, row.id))
                                },
                                data = ledger.rows,
                                noDataMessage = "No entries recorded for this party.",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerHeader(ledger: LegacyPartyLedger) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = ledger.party.name.ifBlank { "-" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (ledger.party.address.isNotBlank() || ledger.party.phone.isNotBlank()) {
            Text(
                text = listOf(ledger.party.address, ledger.party.phone).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        if (ledger.party.legacyId.isNotBlank()) {
            Text(
                text = "Old system id: ${ledger.party.legacyId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        Text(
            text = "An old system's own record — nothing here posts to the books.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        if (ledger.summary.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(ledger.summary) { (label, value) ->
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

private fun ledgerColumns(onOpenBill: (LegacyLedgerRow) -> Unit): List<ReportColumn<LegacyLedgerRow>> = listOf(
    ReportColumn("DATE", ReportColWidth.Fixed(100.dp)) { row, _ ->
        cellText(row.docDate.ifBlank { "-" }, maxLines = 1)
    },
    ReportColumn("PARTICULARS", ReportColWidth.Fixed(220.dp)) { row, _ ->
        ReportTableCell.Slot {
            Row(
                modifier = Modifier
                    .let { if (row.hasBill) it.clickable(onClick = { onOpenBill(row) }) else it }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.particulars.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.hasBill) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.hasBill) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open bill",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    },
    ReportColumn("DEBIT", ReportColWidth.Fixed(100.dp)) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.debit), align = TextAlign.End)
    },
    ReportColumn("CREDIT", ReportColWidth.Fixed(100.dp)) { row, _ ->
        cellText(AmountFormat.formatOrDash(row.credit), align = TextAlign.End)
    },
    ReportColumn("BALANCE", ReportColWidth.Fixed(110.dp)) { row, _ ->
        cellText(AmountFormat.format(row.balance), align = TextAlign.End)
    },
)
