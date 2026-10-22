package com.example.cashbookbd.ui.vrsettings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MismatchRow(
    val id: Long,
    val serial: String,
    val type: String,
    val vrNo: String,
    val vrDate: String,
    val detail: String,
    val branch: String,
    val amount: Double,
    val active: Boolean,
)

/** The two kinds, in the owner's words (English only, 2026-10-10). */
private val TYPES = linkedMapOf(
    "company_missing" to ("No Company" to "Voucher belongs to no company — Profit & Loss counts it, Balance Sheet does not"),
    "unbalanced" to ("Unbalanced" to "Debit and credit are not equal — every report is off by this amount"),
)

data class ReportMismatchUiState(
    val isLoading: Boolean = true,
    val rows: List<MismatchRow> = emptyList(),
    val fixingId: Long? = null,
    val confirm: MismatchRow? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Report Mismatch (ReportMismatch.tsx): the vouchers behind a Balance Sheet
 * Difference. "No Company" can be repaired — the voucher's branch already says
 * which company it belongs to, and nothing else changes; "Unbalanced" cannot,
 * because only a person knows which amount on it is wrong.
 */
class ReportMismatchViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportMismatchUiState())
    val uiState: StateFlow<ReportMismatchUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.get("reports/mismatch/list")) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data.jRows().map { o ->
                            MismatchRow(
                                id = o.jLong("id") ?: 0L, serial = o.jStr("serial_number"), type = o.jStr("type"),
                                vrNo = o.jStr("vr_no"), vrDate = o.jStr("vr_date"), detail = o.jStr("detail"),
                                branch = o.jStr("branch_name"), amount = o.jNum("amount"), active = o.jStr("status") == "1",
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Could not load the mismatch list" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun ask(row: MismatchRow) = _uiState.update { it.copy(confirm = row) }
    fun dismiss() = _uiState.update { it.copy(confirm = null) }

    fun repair() {
        val row = _uiState.value.confirm ?: return
        _uiState.update { it.copy(fixingId = row.id) }
        viewModelScope.launch {
            val r = api.post("reports/mismatch/fix", JsonObject().apply { addProperty("id", row.id) })
            _uiState.update {
                it.copy(
                    fixingId = null, confirm = null,
                    message = if (r is Resource.Success) "${row.vrNo} has been fixed" else (r as? Resource.Error)?.message?.ifBlank { null } ?: "Could not be fixed",
                    sessionExpired = (r as? Resource.Error)?.isUnauthorized == true,
                )
            }
            load()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { ReportMismatchViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

private fun dmy(v: String) = SimpleDate.fromApi(v.take(10))?.toDisplay() ?: v

@Composable
fun ReportMismatchScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: ReportMismatchViewModel = viewModel(factory = ReportMismatchViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = "Report Mismatch", currentRoute = Routes.VR_SETTINGS, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Two causes of a Difference in the Balance Sheet. No Company — Profit & Loss counts the voucher, the Balance Sheet does not. " +
                            "Unbalanced — debit and credit are not equal, so every report is off by that amount. Fix is offered for the first kind only, " +
                            "because only that one can be settled without a judgement call; no amount, date, voucher number or posting line is touched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                item { SecondaryButton(text = "Refresh", onClick = viewModel::load, isLoading = state.isLoading) }
                if (state.rows.isNotEmpty()) {
                    item {
                        val counts = state.rows.groupingBy { it.type }.eachCount()
                        Text(
                            "${state.rows.size} voucher${if (state.rows.size == 1) "" else "s"} in total — " +
                                TYPES.entries.joinToString(", ") { (type, meta) -> "${meta.first} ${counts[type] ?: 0}" } + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreen,
                        )
                    }
                }
                when {
                    state.isLoading && state.rows.isEmpty() -> item { ReportLoading() }
                    state.rows.isEmpty() -> item { ReportNote("No mismatch found — no voucher is company-less or unbalanced.") }
                    // A voucher can trip both checks, so the type is in the key.
                    else -> items(state.rows, key = { "${it.type}-${it.id}" }) { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row.serial}. ${row.vrNo}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(TYPES[row.type]?.first ?: row.type, style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold)
                                }
                                Text("${dmy(row.vrDate)} · ${row.branch}", style = MaterialTheme.typography.labelSmall, color = muted)
                                Text(row.detail, style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Amount (Tk) ${kitMoney(row.amount)} · ${if (row.active) "Active" else "Recycled"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (row.type == "company_missing") {
                                        PrimaryButton(
                                            text = "Fix", onClick = { viewModel.ask(row) }, compact = true,
                                            isLoading = state.fixingId == row.id, enabled = state.fixingId == null,
                                        )
                                    } else {
                                        Text("Cannot be fixed here", style = MaterialTheme.typography.labelSmall, color = muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirm?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("This voucher will be fixed") },
            text = {
                Text(
                    "This voucher will be put under the company its branch belongs to:\n\n" +
                        "Voucher: ${row.vrNo}\nDate: ${dmy(row.vrDate)}\nBranch: ${row.branch}\nAmount: ${kitMoney(row.amount)}\n\n" +
                        "No amount, date, voucher number or posting line changes. The voucher simply stops belonging to no company.",
                )
            },
            confirmButton = { LinkButton(text = "Fix", onClick = viewModel::repair, enabled = state.fixingId == null) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismiss) },
        )
    }
}
