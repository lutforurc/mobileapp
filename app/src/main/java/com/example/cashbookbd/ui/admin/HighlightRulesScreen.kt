package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.dto.HighlightRuleWriteRequest
import com.example.cashbookbd.data.repository.HighlightRuleRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.HighlightRuleRow
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.HighlightedText
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.theme.brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Admin management of the highlight rules ("phrase → coloured border"), a port
 * of the web's HighlightRules page: the add/edit form on top, the company's
 * full rule list beneath it. Every successful write also refreshes the cached
 * active rules the report screens render with.
 */

/** Palette keys the form offers — keep in step with [com.example.cashbookbd.ui.theme.BrandPalette.highlight]. */
private val COLOR_OPTIONS = listOf(
    SelectorOption("red", "Red"),
    SelectorOption("amber", "Amber"),
    SelectorOption("green", "Green"),
    SelectorOption("blue", "Blue"),
    SelectorOption("purple", "Purple"),
    SelectorOption("pink", "Pink"),
    SelectorOption("gray", "Gray"),
)

data class HighlightRulesUiState(
    // List
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val rows: List<HighlightRuleRow> = emptyList(),

    // Form ("" priority is allowed while typing; saved as 0)
    val editingId: Long? = null,
    val phrase: String = "",
    val color: String = "red",
    val priority: String = "0",
    val active: Boolean = true,
    val description: String = "",

    val isSaving: Boolean = false,
    /** Row awaiting its server delete (spinner replaces the bin icon). */
    val deletingId: Long? = null,
    /** Row the confirmation dialog is asking about; null = no dialog. */
    val confirmDelete: HighlightRuleRow? = null,
    /** One-shot snackbar text (save/delete outcome). */
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = editingId != null
    val canSave: Boolean get() = phrase.isNotBlank() && !isSaving
}

class HighlightRulesViewModel(
    private val repository: HighlightRuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HighlightRulesUiState())
    val uiState: StateFlow<HighlightRulesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchAll()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onPhrase(value: String) = _uiState.update { it.copy(phrase = value.take(255)) }

    fun onColor(option: SelectorOption) = _uiState.update { it.copy(color = option.id) }

    fun onPriority(value: String) = _uiState.update { state ->
        // An optional leading minus, then digits — the server allows -9999..9999.
        val cleaned = value.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) }.take(5)
        state.copy(priority = cleaned)
    }

    fun onActive(value: Boolean) = _uiState.update { it.copy(active = value) }

    fun onDescription(value: String) = _uiState.update { it.copy(description = value.take(255)) }

    fun startEdit(row: HighlightRuleRow) = _uiState.update {
        it.copy(
            editingId = row.id,
            phrase = row.phrase,
            color = row.color,
            priority = row.priority.toString(),
            active = row.active,
            description = row.description,
        )
    }

    fun cancelEdit() = _uiState.update { clearForm(it) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val form = HighlightRuleWriteRequest(
            phrase = state.phrase.trim(),
            color = state.color,
            priority = state.priority.toIntOrNull()?.coerceIn(-9999, 9999) ?: 0,
            status = if (state.active) 1 else 0,
            description = state.description.trim().ifBlank { null },
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = state.editingId
            val result = if (id != null) repository.update(id, form) else repository.create(form)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { clearForm(it).copy(isSaving = false, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun requestDelete(row: HighlightRuleRow) = _uiState.update { it.copy(confirmDelete = row) }

    fun dismissDelete() = _uiState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = row.id) }
        viewModelScope.launch {
            when (val result = repository.delete(row.id)) {
                is Resource.Success -> {
                    _uiState.update { state ->
                        // If the deleted rule was on the form, drop the stale edit.
                        val cleared = if (state.editingId == row.id) clearForm(state) else state
                        cleared.copy(deletingId = null, message = result.data)
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        deletingId = null,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun clearForm(state: HighlightRulesUiState) = state.copy(
        editingId = null,
        phrase = "",
        color = "red",
        priority = "0",
        active = true,
        description = "",
    )

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    HighlightRulesViewModel(ServiceLocator.provideHighlightRuleRepository(appContext))
                }
            }
        }
    }
}

@Composable
fun HighlightRulesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HighlightRulesViewModel = viewModel(
        factory = HighlightRulesViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AuthenticatedShell(
        title = "Highlight Rules",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                RuleForm(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
                RulesList(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    uiState.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete highlight rule?") },
            text = { Text("The rule for \"${row.phrase}\" will be removed and its boxes disappear from every report.") },
            confirmButton = { LinkButton(text = "Delete", onClick = viewModel::confirmDelete) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissDelete) },
        )
    }
}

@Composable
private fun RuleForm(state: HighlightRulesUiState, viewModel: HighlightRulesViewModel) {
    // Everything here sits directly on the screen backdrop (not on a card), so
    // every text uses the background's on-colour — onSurfaceVariant is the
    // card-muted role and disappears against the teal in light mode.
    val onScreen = MaterialTheme.colorScheme.onBackground
    val onScreenMuted = onScreen.copy(alpha = 0.75f)

    Text(
        text = if (state.isEdit) "Edit Highlight Rule" else "Add Highlight Rule",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onScreen,
    )
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.phrase,
        onValueChange = viewModel::onPhrase,
        label = "e.g. Not Yet Reports",
        caption = "Phrase",
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Any line that contains this text (case-insensitive) gets boxed.",
        style = MaterialTheme.typography.labelSmall,
        color = onScreenMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))

    AppSelectDropdown(
        label = "Color",
        options = COLOR_OPTIONS,
        selected = COLOR_OPTIONS.firstOrNull { it.id == state.color } ?: COLOR_OPTIONS.first(),
        onSelected = viewModel::onColor,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.priority,
        onValueChange = viewModel::onPriority,
        label = "0",
        caption = "Priority",
        keyboardType = KeyboardType.Number,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "When a line matches several phrases, the highest priority wins.",
        style = MaterialTheme.typography.labelSmall,
        color = onScreenMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = state.active, onCheckedChange = viewModel::onActive)
        Text(
            text = if (state.active) "Active" else "Inactive",
            style = MaterialTheme.typography.bodyMedium,
            color = onScreen,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.description,
        onValueChange = viewModel::onDescription,
        label = "Optional note about this rule",
        caption = "Description",
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    // Live preview of the box the rule would draw, like the web form's Preview.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Preview:",
            style = MaterialTheme.typography.labelSmall,
            color = onScreenMuted,
            modifier = Modifier.padding(end = 8.dp),
        )
        HighlightedText(
            text = state.phrase.trim().ifBlank { "Sample line" },
            borderColor = ruleBorderColor(state.color),
            style = MaterialTheme.typography.bodySmall,
            color = onScreen,
        )
    }
    Spacer(Modifier.height(16.dp))

    Row {
        PrimaryButton(
            text = if (state.isEdit) "Update" else "Add",
            onClick = viewModel::save,
            enabled = state.canSave,
            isLoading = state.isSaving,
        )
        if (state.isEdit) {
            LinkButton(
                text = "Cancel",
                onClick = viewModel::cancelEdit,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RulesList(state: HighlightRulesUiState, viewModel: HighlightRulesViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Highlight Rules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onScreen,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh list",
                tint = onScreen.copy(alpha = 0.8f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    when {
        state.isLoading && state.rows.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.loadError != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.loadError,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Retry", onClick = viewModel::load)
        }

        else -> ReportTable(
            columns = ruleColumns(state, viewModel),
            data = state.rows,
            noDataMessage = "No highlight rules yet.",
            scrollable = false,
        )
    }
}

/** The list table, mirroring the web's columns: # | Phrase | Color | Priority | Status | Description | Action. */
@Composable
private fun ruleColumns(
    state: HighlightRulesUiState,
    viewModel: HighlightRulesViewModel,
): List<ReportColumn<HighlightRuleRow>> {
    val active = MaterialTheme.accents.green
    // The rows draw straight on the screen backdrop, so cell text carries the
    // background's on-colour explicitly — legible in both themes.
    val onScreen = MaterialTheme.colorScheme.onBackground
    return remember(state.deletingId, active, onScreen) {
        listOf(
            ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
                cellText((index + 1).toString(), align = TextAlign.Center, color = onScreen)
            },
            ReportColumn("Phrase", ReportColWidth.Fixed(160.dp)) { r, _ ->
                ReportTableCell.Slot {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        HighlightedText(
                            text = r.phrase,
                            borderColor = ruleBorderColor(r.color),
                            color = onScreen,
                            maxLines = 2,
                        )
                    }
                }
            },
            ReportColumn("Color", ReportColWidth.Fixed(92.dp)) { r, _ ->
                ReportTableCell.Slot { ColorCell(r.color, onScreen) }
            },
            ReportColumn("Priority", ReportColWidth.Fixed(72.dp), TextAlign.End) { r, _ ->
                cellText(r.priority.toString(), align = TextAlign.End, color = onScreen)
            },
            ReportColumn("Status", ReportColWidth.Fixed(80.dp)) { r, _ ->
                cellText(
                    if (r.active) "Active" else "Inactive",
                    color = if (r.active) active else onScreen.copy(alpha = 0.7f),
                    bold = r.active,
                )
            },
            ReportColumn("Description", ReportColWidth.Fixed(160.dp)) { r, _ ->
                cellText(r.description.ifBlank { "-" }, maxLines = 2, color = onScreen)
            },
            ReportColumn("Action", ReportColWidth.Fixed(96.dp), TextAlign.Center) { r, _ ->
                ReportTableCell.Slot { ActionCell(r, state.deletingId == r.id, viewModel) }
            },
        )
    }
}

@Composable
private fun ColorCell(colorKey: String, textColor: Color) {
    val swatch = ruleBorderColor(colorKey)
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(swatch ?: MaterialTheme.colorScheme.outline)
        )
        Text(
            text = colorKey.replaceFirstChar { it.titlecase(Locale.US) },
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionCell(
    row: HighlightRuleRow,
    isDeleting: Boolean,
    viewModel: HighlightRulesViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.startEdit(row) }) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit ${row.phrase}",
                // primary is the deep blue and sinks into the teal backdrop.
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { viewModel.requestDelete(row) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${row.phrase}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The theme's border colour for a palette key (unknown keys fall back to red). */
@Composable
private fun ruleBorderColor(colorKey: String) =
    MaterialTheme.brand.highlight[colorKey.lowercase()] ?: MaterialTheme.brand.highlight["red"]
