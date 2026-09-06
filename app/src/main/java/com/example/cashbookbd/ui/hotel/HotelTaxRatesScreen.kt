package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelSetupRepository
import com.example.cashbookbd.data.repository.HotelTaxRateRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.BrandPill
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** "2026-09-06" → "06/09/2026"; anything else → a dash. */
internal fun onTheDay(value: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(value) ?: return "—"
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

private fun percent(value: Double): String = String.format(Locale.US, "%.2f%%", value)

data class HotelTaxRatesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<HotelTaxRateRow> = emptyList(),
    val currentServiceCharge: Double = 0.0,
    val note: String = "",
    val formOpen: Boolean = false,
    val rate: String = "",
    val effectiveFrom: SimpleDate = SimpleDate.today(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val confirmDelete: HotelTaxRateRow? = null,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class HotelTaxRatesViewModel(
    private val repository: HotelSetupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotelTaxRatesUiState())
    val uiState: StateFlow<HotelTaxRatesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.fetchTaxRates()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data.rows,
                        currentServiceCharge = r.data.currentServiceCharge,
                        note = r.data.note,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun toggleForm() = _uiState.update {
        if (it.formOpen) it.copy(formOpen = false)
        else it.copy(formOpen = true, rate = "", effectiveFrom = SimpleDate.today(), notes = "")
    }

    /**
     * "Edit" opens the form carrying the row's figure; it does NOT edit the row
     * in place. A rate in force has bills made under it and never changes —
     * what edit means here is "carry this figure into a new rate from a new
     * day". Only a future rate of this property's own keeps its own date.
     */
    fun editFrom(row: HotelTaxRateRow) = _uiState.update {
        it.copy(
            formOpen = true,
            rate = row.rate.let { r -> if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString() },
            effectiveFrom = (if (row.removable) SimpleDate.fromApi(row.effectiveFrom) else null) ?: SimpleDate.today(),
            notes = if (row.isShipped) "" else row.notes,
        )
    }

    fun onRate(v: String) = _uiState.update { it.copy(rate = v) }
    fun onFrom(v: SimpleDate) = _uiState.update { it.copy(effectiveFrom = v) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }

    fun save() {
        val s = _uiState.value
        // Empty is not zero here: nought is a real answer somebody has to give on purpose.
        if (s.rate.isBlank()) {
            _uiState.update { it.copy(message = "Give the rate. Nought is an answer, but it has to be typed.") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val r = repository.storeTaxRate(s.rate, s.effectiveFrom.toApi(), s.notes)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, formOpen = false, message = r.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askDelete(row: HotelTaxRateRow?) = _uiState.update { it.copy(confirmDelete = row) }

    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val r = repository.deleteTaxRate(row.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isDeleting = false, confirmDelete = null, message = r.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isDeleting = false, confirmDelete = null, message = r.message, sessionExpired = it.sessionExpired || r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { HotelTaxRatesViewModel(repository = HotelSetupRepository.get(context)) }
        }
    }
}

/**
 * The service charge — one figure for the property.
 *
 * VAT is not set here: it belongs to the item and is typed on the Room Types
 * and Charges screens. What is left is the hotel's own takings, which do not
 * vary by what was sold. A HISTORY, not a setting: a new rate is a new row
 * with its own start date, and nothing here reaches a bill already made.
 */
@Composable
fun HotelTaxRatesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelTaxRatesViewModel = viewModel(
        factory = HotelTaxRatesViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val allowed = Permissions.hasAny(sessionState.permissions, listOf("hotel.charge.type.view"))
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    AuthenticatedShell(
        title = "Service Charge",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!allowed) {
            NoAccess("You don't have access to the service charge.")
            return@AuthenticatedShell
        }
        val muted = MaterialTheme.appColors.textMuted
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.rows.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                state.error != null && state.rows.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error.orEmpty(), color = MaterialTheme.appColors.textOnScreen, textAlign = TextAlign.Center)
                            LinkButton(text = "Retry", onClick = viewModel::load)
                        }
                    }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.note.isNotBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(state.note, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                    // What a bill made today would carry, worked out by the
                    // bill's own lookup — never read off the top row.
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("A bill made today:", style = MaterialTheme.typography.labelSmall, color = muted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Service charge", style = MaterialTheme.typography.bodyMedium)
                                    Text(percent(state.currentServiceCharge), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                if (state.currentServiceCharge == 0.0) {
                                    Text(
                                        "At nought — no service charge on a bill. Press Set new rates to set one.",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.warning,
                                    )
                                }
                                Text("VAT is the item's — set it on Room Types and Charges.", style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                    }
                    item {
                        if (state.formOpen) {
                            SecondaryButton(text = "Close", onClick = viewModel::toggleForm, compact = true)
                        } else {
                            PrimaryButton(text = "Set new rates", onClick = viewModel::toggleForm, compact = true)
                        }
                    }
                    if (state.formOpen) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTextField(
                                        value = state.rate,
                                        onValueChange = viewModel::onRate,
                                        label = "0",
                                        caption = "Service charge %",
                                        keyboardType = KeyboardType.Decimal,
                                    )
                                    Text("The hotel's own takings — income, not a tax.", style = MaterialTheme.typography.labelSmall, color = muted)
                                    PickerField(
                                        label = "From",
                                        value = state.effectiveFrom.toDisplay(),
                                        trailingIcon = Icons.Filled.DateRange,
                                        onClick = { pickDay(context, state.effectiveFrom, viewModel::onFrom) },
                                    )
                                    Text("Bills made before this day keep the rates they were made at.", style = MaterialTheme.typography.labelSmall, color = muted)
                                    AppTextField(
                                        value = state.notes,
                                        onValueChange = viewModel::onNotes,
                                        label = "SRO 2026-08, per the consultant",
                                        caption = "Note",
                                    )
                                    Text("Where the figure came from, for whoever reads this next year.", style = MaterialTheme.typography.labelSmall, color = muted)
                                    Text(
                                        "VAT falls on the service charge as well as on the room: 100 of rent with a 5 service charge is taxed on 105. " +
                                            "Each item is taxed at its own rate — 15% on an air-conditioned room, 7.5% without, 5% on food.",
                                        style = MaterialTheme.typography.labelSmall, color = muted,
                                    )
                                    Text(
                                        "Saving writes this property's own rate from the day given. The one that ships at nought stays where it is — " +
                                            "nothing changes for any other property, and no line already billed is touched.",
                                        style = MaterialTheme.typography.labelSmall, color = muted,
                                    )
                                    PrimaryButton(text = "Save", onClick = viewModel::save, isLoading = state.isSaving)
                                }
                            }
                        }
                    }
                    item {
                        RatesTable(
                            rows = state.rows,
                            onEdit = viewModel::editFrom,
                            onDelete = viewModel::askDelete,
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) viewModel.askDelete(null) },
            title = { Text("Remove this rate?") },
            text = { Text("${percent(row.rate)} from ${onTheDay(row.effectiveFrom)} — it has not been used on a bill yet.") },
            confirmButton = { PrimaryButton(text = "Remove", onClick = viewModel::confirmDelete, isLoading = state.isDeleting, compact = true) },
            dismissButton = { LinkButton(text = "Keep it", onClick = { viewModel.askDelete(null) }, enabled = !state.isDeleting) },
        )
    }
}

@Composable
private fun RatesTable(
    rows: List<HotelTaxRateRow>,
    onEdit: (HotelTaxRateRow) -> Unit,
    onDelete: (HotelTaxRateRow) -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    val columns = listOf(
        ReportColumn<HotelTaxRateRow>("Charge", ReportColWidth.Fixed(130.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(if (row.taxType == "service_charge") "Service charge" else row.taxType, style = MaterialTheme.typography.bodySmall)
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 2)
                }
            }
        },
        ReportColumn("Rate", ReportColWidth.Fixed(70.dp), align = TextAlign.End) { row, _ -> cellText(percent(row.rate)) },
        ReportColumn("From", ReportColWidth.Fixed(90.dp), align = TextAlign.Center) { row, _ -> cellText(onTheDay(row.effectiveFrom)) },
        // A rate with no end is the one still running — said in words rather
        // than left as a dash, which in a column of dates reads as missing data.
        ReportColumn("Until", ReportColWidth.Fixed(100.dp), align = TextAlign.Center) { row, _ ->
            if (row.effectiveTo.isBlank()) cellText("still in force", color = muted) else cellText(onTheDay(row.effectiveTo))
        },
        ReportColumn("Today", ReportColWidth.Fixed(70.dp), align = TextAlign.Center) { row, _ ->
            if (row.inForce) {
                ReportTableCell.Slot { Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) { BrandPill("in use", compact = true) } }
            } else {
                cellText("—", color = muted)
            }
        },
        ReportColumn("Set by", ReportColWidth.Fixed(100.dp), align = TextAlign.Center) { row, _ ->
            cellText(if (row.isShipped) "shipped" else "this property", color = muted)
        },
        ReportColumn("Action", ReportColWidth.Fixed(90.dp), align = TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Carry into a new rate", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Only a rate of this property's own that has not started
                    // yet: one in force has had bills made under it.
                    if (row.removable) {
                        IconButton(onClick = { onDelete(row) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.appColors.danger)
                        }
                    }
                }
            }
        },
    )
    ReportTable(
        columns = columns,
        data = rows,
        scrollable = false,
        noDataMessage = "No rate yet — the service charge ships at nought.",
    )
}

private fun pickDay(context: Context, initial: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
