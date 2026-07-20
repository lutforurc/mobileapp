package com.example.cashbookbd.ui.hrm

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hrm.CrudField
import com.example.cashbookbd.hrm.CrudFieldKind
import com.example.cashbookbd.hrm.CrudEditFetch
import com.example.cashbookbd.hrm.CrudUpdateStyle
import com.example.cashbookbd.hrm.HrmCrudForms
import com.example.cashbookbd.hrm.HrmCrudSpec
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.FieldTextInput
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * One field's live value. [value] is the wire value (id for dropdowns, "HH:mm",
 * "yyyy-MM-dd", …); [display] carries a label the options list can't provide
 * (the employee typeahead's name).
 */
data class CrudFieldState(
    val field: CrudField,
    val value: String,
    val display: String = "",
)

data class HrmCrudUiState(
    val spec: HrmCrudSpec? = null,
    val crudId: String? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,

    val fields: List<CrudFieldState> = emptyList(),
    /** The fetched row (edit only), so unshown keys round-trip on update. */
    val editRow: JsonObject? = null,

    val branches: List<SelectorOption> = emptyList(),
    val shifts: List<SelectorOption> = emptyList(),
    val levels: List<SelectorOption> = emptyList(),

    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedMessage: String? = null,

    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = crudId != null

    val canSave: Boolean
        get() = spec != null && !isSaving &&
            fields.all { !it.field.required || it.value.isNotBlank() }
}

class HrmCrudFormViewModel(
    private val crudKey: String,
    private val crudId: String?,
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val spec: HrmCrudSpec? = HrmCrudForms.byKey(crudKey)

    private val _uiState = MutableStateFlow(
        HrmCrudUiState(
            spec = spec,
            crudId = crudId,
            fields = spec?.fields.orEmpty().map { CrudFieldState(it, it.default) },
        ),
    )
    val uiState: StateFlow<HrmCrudUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val currentSpec = spec ?: run {
            _uiState.update { it.copy(isLoading = false, loadError = "Unknown form.") }
            return
        }
        viewModelScope.launch {
            // Option sources, only for the kinds this form actually uses.
            val kinds = currentSpec.fields.map { it.kind }.toSet()
            if (CrudFieldKind.BRANCH in kinds) {
                (reportRepository.getBranches() as? Resource.Success)?.let { result ->
                    _uiState.update { state ->
                        state.copy(
                            branches = result.data.branches.map {
                                SelectorOption(it.id.toString(), it.name)
                            },
                        )
                    }
                }
            }
            if (CrudFieldKind.SHIFT in kinds) {
                (hrmRepository.getShiftOptions() as? Resource.Success)?.let { result ->
                    _uiState.update { state ->
                        state.copy(shifts = result.data.map { SelectorOption(it.id, it.name) })
                    }
                }
            }
            if (CrudFieldKind.LEVEL in kinds) {
                (hrmRepository.getLevelOptions() as? Resource.Success)?.let { result ->
                    _uiState.update { state ->
                        state.copy(levels = result.data.map { SelectorOption(it.id, it.name) })
                    }
                }
            }

            if (crudId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val row = hrmRepository.fetchCrudRow(
                editPath = currentSpec.editPath.takeIf {
                    currentSpec.editFetch == CrudEditFetch.ENDPOINT
                },
                listPath = currentSpec.listPath,
                id = crudId,
            )
            when (row) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        editRow = row.data,
                        fields = state.fields.map { fieldState ->
                            val raw = row.data.get(fieldState.field.key)
                                ?.takeUnless { it.isJsonNull }
                                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            fieldState.copy(
                                value = normalize(fieldState.field.kind, raw),
                                display = displayFor(fieldState.field, row.data),
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = row.message,
                        sessionExpired = it.sessionExpired || row.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Server value → form value ("09:00:00"→"09:00", true→"1", "15.0"→"15"). */
    private fun normalize(kind: CrudFieldKind, raw: String): String {
        val trimmed = raw.trim()
        return when (kind) {
            CrudFieldKind.TIME -> Regex("""^(\d{2}:\d{2})""").find(trimmed)?.groupValues?.get(1) ?: trimmed
            CrudFieldKind.DATE -> Regex("""^(\d{4}-\d{2}-\d{2})""").find(trimmed)?.groupValues?.get(1) ?: trimmed
            CrudFieldKind.NUMBER -> trimmed.toDoubleOrNull()
                ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else trimmed }
                ?: trimmed
            else -> when (trimmed.lowercase(Locale.US)) {
                "true" -> "1"
                "false" -> "0"
                else -> trimmed
            }
        }
    }

    /** The employee typeahead's label comes from the row (`employee_name`). */
    private fun displayFor(field: CrudField, row: JsonObject): String {
        if (field.kind != CrudFieldKind.EMPLOYEE) return ""
        val nameKey = field.key.removeSuffix("_id") + "_name"
        return row.get(nameKey)?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }

    fun onValueChanged(key: String, value: String, display: String = "") {
        _uiState.update { state ->
            state.copy(
                fields = state.fields.map {
                    if (it.field.key == key) it.copy(value = value, display = display) else it
                },
            )
        }
    }

    suspend fun searchEmployees(query: String): Resource<List<SelectorOption>> =
        selectorRepository.fetch(source = ReportSelectorSource.EMPLOYEE, query = query)

    fun save() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                // Unshown keys the row carries (edit) or the web defaults (add)
                // go first, so a visible field with the same key overwrites.
                if (state.editRow != null) {
                    currentSpec.hiddenDefaults.keys.forEach { key ->
                        state.editRow.get(key)?.takeUnless { it.isJsonNull }?.let { add(key, it) }
                    }
                } else {
                    currentSpec.hiddenDefaults.forEach { (key, value) -> put(key, value) }
                }
                state.fields.forEach { fieldState -> put(fieldState.field.key, fieldState.value) }
                if (currentSpec.updateStyle == CrudUpdateStyle.BODY_ID && crudId != null) {
                    addProperty("id", crudId.toLongOrNull() ?: 0L)
                }
            }
            val path = when {
                crudId == null -> currentSpec.storePath
                currentSpec.updateStyle == CrudUpdateStyle.PATH_ID ->
                    "${currentSpec.updatePath}/$crudId"
                else -> currentSpec.updatePath
            }
            val result = hrmRepository.submitCrud(
                path = path,
                body = body,
                fallback = "${currentSpec.title} saved successfully",
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Blank → null; numeric text → number; everything else → string. */
    private fun JsonObject.put(key: String, value: String) {
        val trimmed = value.trim()
        when {
            trimmed.isBlank() -> add(key, JsonNull.INSTANCE)
            trimmed.toLongOrNull() != null -> addProperty(key, trimmed.toLong())
            else -> addProperty(key, trimmed)
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, crudKey: String, crudId: String?) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                HrmCrudFormViewModel(
                    crudKey = crudKey,
                    crudId = crudId,
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}

/**
 * The config-driven add/edit form behind every HRM list's +Add and pencil —
 * one screen renders whichever [HrmCrudSpec] the route names, exactly as the
 * web's corresponding form does.
 */
@Composable
fun HrmCrudFormScreen(
    crudKey: String,
    crudId: String?,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HrmCrudFormViewModel = viewModel(
        factory = HrmCrudFormViewModel.provideFactory(context, crudKey, crudId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    val title = state.spec?.title ?: "Form"
    AuthenticatedShell(
        title = if (state.isEdit) "Edit $title" else "Add $title",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadError != null -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            ) {
                item {
                    state.fields.forEach { fieldState ->
                        CrudFieldRow(
                            fieldState = fieldState,
                            state = state,
                            context = context,
                            onValueChanged = viewModel::onValueChanged,
                            searchEmployees = viewModel::searchEmployees,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = if (state.isEdit) "Update $title" else "Save $title",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.saveError?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Renders one field by kind, all through the shared components. */
@Composable
private fun CrudFieldRow(
    fieldState: CrudFieldState,
    state: HrmCrudUiState,
    context: Context,
    onValueChanged: (String, String, String) -> Unit,
    searchEmployees: suspend (String) -> Resource<List<SelectorOption>>,
) {
    val field = fieldState.field
    val label = if (field.required) "${field.label} *" else field.label

    when (field.kind) {
        CrudFieldKind.TEXT, CrudFieldKind.NUMBER -> FieldFrame(label = label) {
            FieldTextInput(
                value = fieldState.value,
                onValueChange = { onValueChanged(field.key, it, "") },
                placeholder = if (field.kind == CrudFieldKind.NUMBER) "0" else "",
            )
        }

        CrudFieldKind.TIME -> HrmTimeField(
            label = label,
            value = fieldState.value,
            context = context,
            onSelected = { onValueChanged(field.key, it, "") },
            modifier = Modifier.fillMaxWidth(),
        )

        CrudFieldKind.DATE -> {
            PickerField(
                label = label,
                value = fieldState.value.toDisplayDate(),
                placeholder = "dd/mm/yyyy",
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val calendar = Calendar.getInstance()
                    Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(fieldState.value)?.let { match ->
                        val (year, month, day) = match.destructured
                        calendar.set(year.toInt(), month.toInt() - 1, day.toInt())
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onValueChanged(
                                field.key,
                                String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth),
                                "",
                            )
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
            )
        }

        CrudFieldKind.CHOICE -> AppSelectDropdown(
            label = label,
            options = field.choices,
            selected = field.choices.firstOrNull { it.id == fieldState.value },
            onSelected = { onValueChanged(field.key, it.id, "") },
        )

        CrudFieldKind.BRANCH -> CrudOptionDropdown(
            label = label, options = state.branches, fieldState = fieldState,
            onValueChanged = onValueChanged,
        )

        CrudFieldKind.SHIFT -> CrudOptionDropdown(
            label = label, options = state.shifts, fieldState = fieldState,
            onValueChanged = onValueChanged,
        )

        CrudFieldKind.LEVEL -> CrudOptionDropdown(
            label = label, options = state.levels, fieldState = fieldState,
            onValueChanged = onValueChanged,
        )

        CrudFieldKind.EMPLOYEE -> SearchableSelectDropdown(
            selected = fieldState.value.takeIf { it.isNotBlank() }?.let {
                SelectorOption(it, fieldState.display.ifBlank { "Employee #$it" })
            },
            onSelected = { onValueChanged(field.key, it.id, it.label) },
            search = searchEmployees,
            label = label,
        )
    }
}

/** A remote-options dropdown; optional fields get a blank "—" entry to clear. */
@Composable
private fun CrudOptionDropdown(
    label: String,
    options: List<SelectorOption>,
    fieldState: CrudFieldState,
    onValueChanged: (String, String, String) -> Unit,
) {
    val field = fieldState.field
    val withBlank = if (field.required) options else listOf(SelectorOption("", "—")) + options
    AppSelectDropdown(
        label = label,
        options = withBlank,
        selected = withBlank.firstOrNull { it.id == fieldState.value && it.id.isNotBlank() },
        onSelected = { onValueChanged(field.key, it.id, "") },
    )
}

/** "2026-01-05" → "05/01/2026" for display; blank stays blank. */
private fun String.toDisplayDate(): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(this) ?: return this
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}
