package com.example.cashbookbd.ui.legacy

import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LegacyArchive
import com.example.cashbookbd.data.repository.LegacyInvoiceItem
import com.example.cashbookbd.data.repository.LegacyInvoiceView
import com.example.cashbookbd.data.repository.LegacyRecordRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen 3 — one bill exactly as the old system printed it: pad letterhead,
 * party/meta block, items, and totals. The older archive's totals arrive as
 * its own verbatim label/value block instead of named fields (see
 * [LegacyRecordRepository]) — this screen renders whichever it gets the same
 * way, so it never needs to know which archive it is looking at.
 */

data class LegacyInvoiceUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val invoice: LegacyInvoiceView? = null,
    val sessionExpired: Boolean = false,
)

class LegacyInvoiceViewModel(
    private val archive: LegacyArchive,
    private val id: Long,
    private val repository: LegacyRecordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegacyInvoiceUiState())
    val uiState: StateFlow<LegacyInvoiceUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchInvoice(archive, id)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, invoice = result.data) }
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
        fun provideFactory(context: Context, archive: LegacyArchive, id: Long) = viewModelFactory {
            initializer {
                LegacyInvoiceViewModel(
                    archive = archive,
                    id = id,
                    repository = ServiceLocator.provideLegacyRecordRepository(context.applicationContext),
                )
            }
        }
    }
}

@Composable
fun LegacyInvoiceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    archive: LegacyArchive,
    id: Long,
    modifier: Modifier = Modifier,
    viewModel: LegacyInvoiceViewModel = viewModel(
        key = "${archive.endpointPrefix}:$id",
        factory = LegacyInvoiceViewModel.provideFactory(LocalContext.current, archive, id),
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
        title = "Bill",
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

                else -> state.invoice?.let { InvoiceBody(it) }
            }
        }
    }
}

@Composable
private fun InvoiceBody(invoice: LegacyInvoiceView) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (invoice.padName.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = invoice.padName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (invoice.padAddress.isNotBlank()) {
                    Text(
                        text = invoice.padAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                if (invoice.padPhone.isNotBlank()) {
                    Text(
                        text = invoice.padPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Text(
            text = "BILL INVOICE",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = invoice.partyName.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                val partyLine = invoice.partyDetails.ifBlank {
                    listOf(invoice.partyAddress, invoice.partyPhone).filter { it.isNotBlank() }.joinToString(" • ")
                }
                if (partyLine.isNotBlank()) {
                    Text(
                        text = partyLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                if (invoice.legacyNo.isNotBlank()) MetaLine("Invoice/Memo No", invoice.legacyNo)
                if (invoice.challan.isNotBlank()) MetaLine("Challan", invoice.challan)
                if (invoice.cashMemo.isNotBlank()) MetaLine("Cash Memo", invoice.cashMemo)
                if (invoice.invoiceDate.isNotBlank()) MetaLine("Date", invoice.invoiceDate)
                if (invoice.invoiceTime.isNotBlank()) MetaLine("Time", invoice.invoiceTime)
                if (invoice.soldBy.isNotBlank()) MetaLine("Sold By", invoice.soldBy)
            }
        }

        ReportTable(
            columns = invoiceItemColumns,
            data = invoice.items,
            noDataMessage = "No line items on this bill.",
            scrollable = false,
        )

        if (invoice.totals.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.fillMaxWidth()) {
                invoice.totals.forEach { (label, value) -> MetaLine(label, value, wide = true) }
            }
        }

        if (invoice.inWord.isNotBlank()) {
            Text(
                text = "In Word: ${invoice.inWord}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Customer Signature & Seal", style = MaterialTheme.typography.labelSmall)
            Text("Signature", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String, wide: Boolean = false) {
    Row(
        modifier = if (wide) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = if (wide) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Text(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

private val invoiceItemColumns: List<ReportColumn<LegacyInvoiceItem>> = listOf(
    ReportColumn("SL", ReportColWidth.Fixed(40.dp)) { row, _ -> cellText(row.sl, align = TextAlign.Center) },
    ReportColumn("CODE", ReportColWidth.Fixed(70.dp)) { row, _ -> cellText(row.productCode.ifBlank { "-" }) },
    ReportColumn("PARTICULARS", ReportColWidth.Fixed(150.dp)) { row, _ -> cellText(row.productName, maxLines = 2) },
    ReportColumn("QTY", ReportColWidth.Fixed(70.dp)) { row, _ -> cellText(row.qty.ifBlank { "-" }, align = TextAlign.Center) },
    ReportColumn("RATE", ReportColWidth.Fixed(70.dp)) { row, _ -> cellText(row.rate.ifBlank { "-" }, align = TextAlign.End) },
    ReportColumn("AMOUNT", ReportColWidth.Fixed(80.dp)) { row, _ -> cellText(row.amount.ifBlank { "-" }, align = TextAlign.End) },
)
