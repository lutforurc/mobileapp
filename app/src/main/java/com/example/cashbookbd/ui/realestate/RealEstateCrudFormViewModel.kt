package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.RealEstateCrudRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.realestate.ReCrudField
import com.example.cashbookbd.realestate.ReCrudFieldKind
import com.example.cashbookbd.realestate.ReCrudSpec
import com.example.cashbookbd.realestate.ReDdlSource
import com.example.cashbookbd.realestate.RealEstateCrudForms
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Drives the config-driven Real Estate add/edit form: loads options and the
 * edit record for whichever [ReCrudSpec] the route names, holds the field
 * values, and posts the store/update/upsert body.
 */
class RealEstateCrudFormViewModel(
    private val crudKey: String,
    private val crudId: String?,
    private val repository: RealEstateCrudRepository,
    private val reportRepository: ReportRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val spec: ReCrudSpec? = RealEstateCrudForms.byKey(crudKey)

    private val _uiState = MutableStateFlow(
        RealEstateCrudFormUiState(
            spec = spec,
            crudId = crudId,
            fields = spec?.fields.orEmpty().map { ReCrudFieldState(it, it.default) },
        ),
    )
    val uiState: StateFlow<RealEstateCrudFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val currentSpec = spec ?: run {
            _uiState.update { it.copy(isLoading = false, loadError = "Unknown form.") }
            return
        }
        viewModelScope.launch {
            if (currentSpec.fields.any { it.kind == ReCrudFieldKind.BRANCH }) {
                (reportRepository.getBranches() as? Resource.Success)?.let { result ->
                    val options = result.data.branches.map {
                        SelectorOption(it.id.toString(), it.name)
                    }
                    _uiState.update { state ->
                        state.copy(
                            branches = options,
                            // The web form preselects the first protected branch.
                            fields = state.fields.map { fieldState ->
                                if (
                                    fieldState.field.kind == ReCrudFieldKind.BRANCH &&
                                    fieldState.value.isBlank() &&
                                    crudId == null
                                ) {
                                    fieldState.copy(value = options.firstOrNull()?.id.orEmpty())
                                } else {
                                    fieldState
                                }
                            },
                        )
                    }
                }
            }

            if (crudId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            when (val record = repository.fetchEditRecord(currentSpec.editPath, crudId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        fields = state.fields.map { fieldState ->
                            val raw = record.data.get(fieldState.field.key)
                                ?.takeUnless { it.isJsonNull }
                                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            fieldState.copy(
                                value = normalize(fieldState.field.kind, raw),
                                display = displayFor(fieldState.field, record.data),
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = record.message,
                        sessionExpired = it.sessionExpired || record.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Server value → form value ("2026-01-05T…"→"2026-01-05", true→"1", "15.0"→"15"). */
    private fun normalize(kind: ReCrudFieldKind, raw: String): String {
        val trimmed = raw.trim()
        return when (kind) {
            ReCrudFieldKind.DATE ->
                Regex("""^(\d{4}-\d{2}-\d{2})""").find(trimmed)?.groupValues?.get(1) ?: trimmed
            ReCrudFieldKind.NUMBER -> trimmed.toDoubleOrNull()
                ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else trimmed }
                ?: trimmed
            // Booleans arrive for the charge types' is_active; a select's ids
            // are "1"/"0", so both shapes normalize to the same value.
            else -> when (trimmed.lowercase(Locale.US)) {
                "true" -> "1"
                "false" -> "0"
                else -> trimmed
            }
        }
    }

    /**
     * An async picker's prefilled label, resolved from the edit record via the
     * field's dotted [ReCrudField.labelPath] — a loaded relation ("area.name")
     * or a computed top-level column ("customer_name").
     */
    private fun displayFor(field: ReCrudField, record: JsonObject): String {
        if (field.kind != ReCrudFieldKind.ASYNC_PICKER) return ""
        val path = field.labelPath ?: return ""
        var current: JsonElement = record
        for (part in path.split('.')) {
            current = current.takeIf { it.isJsonObject }?.asJsonObject
                ?.get(part)?.takeUnless { it.isJsonNull }
                ?: return ""
        }
        return current.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }

    fun onValueChanged(key: String, value: String, display: String = "") {
        _uiState.update { state ->
            state.copy(
                fields = state.fields.map { fieldState ->
                    if (fieldState.field.key == key) {
                        val limited = fieldState.field.maxLength
                            ?.let { value.take(it) } ?: value
                        fieldState.copy(value = limited, display = display)
                    } else {
                        fieldState
                    }
                },
            )
        }
    }

    /**
     * Runs one async picker's typeahead: the field's real-estate DDL, or the
     * shared chart-of-accounts ledger search for the land-owner picker.
     */
    suspend fun searchOptions(field: ReCrudField, query: String): Resource<List<SelectorOption>> {
        val source = field.source ?: return Resource.Success(emptyList())
        val path = source.path
            ?: return when (val outcome = ledgerRepository.searchLedgers(query, acType = "")) {
                is Resource.Success -> Resource.Success(
                    outcome.data.map {
                        SelectorOption(it.id.toString(), it.name, it.mobile?.ifBlank { null })
                    },
                )
                is Resource.Error -> outcome
                Resource.Loading -> Resource.Loading
            }
        return repository.searchDdl(path, query)
    }

    fun save() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            // Only the documented keys go on the wire — one property per field,
            // plus the upsert endpoints' `id` on update.
            val body = JsonObject().apply {
                state.fields.forEach { fieldState -> put(fieldState.field.key, fieldState.value) }
                if (crudId != null && currentSpec.updatePath == null) {
                    addProperty("id", crudId.toLongOrNull() ?: 0L)
                }
            }
            val path = when {
                crudId == null -> currentSpec.storePath
                currentSpec.updatePath != null -> "${currentSpec.updatePath}/$crudId"
                else -> currentSpec.storePath
            }
            val result = repository.save(
                path = path,
                body = body,
                fallback = "${currentSpec.title} saved successfully",
            )
            when (result) {
                is Resource.Success ->
                    if (crudId == null && currentSpec.stayAfterCreate) {
                        stayAndReset(currentSpec, result.data)
                    } else {
                        _uiState.update { it.copy(isSaving = false, savedMessage = result.data) }
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

    /**
     * The stay-in-place create (units): keep the fields listed in
     * [ReCrudSpec.keepOnCreate] (the chosen floor), reset the rest to their
     * defaults, and raise the in-place snackbar instead of popping back.
     */
    private fun stayAndReset(currentSpec: ReCrudSpec, message: String) {
        _uiState.update { state ->
            state.copy(
                isSaving = false,
                stayMessage = message,
                fields = state.fields.map { fieldState ->
                    if (fieldState.field.key in currentSpec.keepOnCreate) {
                        fieldState
                    } else {
                        ReCrudFieldState(fieldState.field, fieldState.field.default)
                    }
                },
            )
        }
    }

    /** Blank → null; integer text → number; everything else → string (server casts). */
    private fun JsonObject.put(key: String, value: String) {
        val trimmed = value.trim()
        when {
            trimmed.isBlank() -> add(key, JsonNull.INSTANCE)
            trimmed.toLongOrNull() != null -> addProperty(key, trimmed.toLong())
            else -> addProperty(key, trimmed)
        }
    }

    fun onStayMessageShown() = _uiState.update { it.copy(stayMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, crudKey: String, crudId: String?) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                RealEstateCrudFormViewModel(
                    crudKey = crudKey,
                    crudId = crudId,
                    repository = ServiceLocator.provideRealEstateCrudRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}
