package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.RecipeRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.production.model.RecipeDetail
import com.example.cashbookbd.ui.production.model.RecipeLine
import com.example.cashbookbd.ui.production.model.RecipeLineBody
import com.example.cashbookbd.ui.production.model.RecipeListRow
import com.example.cashbookbd.ui.production.model.RecipeSaveBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Recipe screen (web `Recipe.tsx`). Reading the list needs
 * `production.view`; every write needs `production.create`, which is what
 * [RecipeUiState.canWrite] carries so the screen can keep the buttons visible
 * but shut.
 */
class RecipeViewModel(
    private val repository: RecipeRepository,
    transactionDate: String?,
    canWrite: Boolean,
) : ViewModel() {

    private val initialDate =
        com.example.cashbookbd.ui.reports.model.SimpleDate.fromDisplay(transactionDate)
            ?: com.example.cashbookbd.ui.reports.model.SimpleDate.today()

    private val _uiState = MutableStateFlow(
        RecipeUiState(
            form = RecipeUiState.emptyForm(initialDate.toApi()),
            canWrite = canWrite,
        ),
    )
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    init {
        loadRecipes()
    }

    fun onTab(tab: RecipeTab) = _uiState.update { it.copy(tab = tab) }

    fun loadRecipes() {
        _uiState.update { it.copy(listLoading = true, listError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchRecipes()) {
                is Resource.Success -> _uiState.update {
                    it.copy(listLoading = false, listError = null, recipes = result.data)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        listLoading = false,
                        listError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onField(key: String, value: String) = _uiState.update { state ->
        val form = state.form
        state.copy(
            form = when (key) {
                "name" -> form.copy(name = value)
                "yieldQty" -> form.copy(yieldQty = value)
                "lossBasis" -> form.copy(lossBasis = value)
                "lossTolerancePct" -> form.copy(lossTolerancePct = value)
                "suggestedCost" -> form.copy(suggestedCost = value)
                "note" -> form.copy(note = value)
                else -> form
            }
        )
    }

    /** Picking the product the recipe makes also takes its unit, as the web does. */
    fun onOutputProduct(id: String, name: String, unit: String) = _uiState.update {
        it.copy(
            form = it.form.copy(
                outputProductId = id,
                outputProductName = name,
                unit = unit.ifBlank { it.form.unit },
            )
        )
    }

    fun onEffectiveDate(isoDate: String) = _uiState.update {
        it.copy(form = it.form.copy(effectiveDate = isoDate))
    }

    fun onDraftProduct(id: String, name: String, unit: String) = _uiState.update {
        it.copy(lineDraft = it.lineDraft.copy(productId = id, productName = name, unit = unit))
    }

    fun onDraftField(key: String, value: String) = _uiState.update { state ->
        val draft = state.lineDraft
        state.copy(
            lineDraft = when (key) {
                "qty" -> draft.copy(qty = value)
                "lineType" -> draft.copy(lineType = value)
                "note" -> draft.copy(note = value)
                else -> draft
            }
        )
    }

    fun onSubmitLine() {
        val state = _uiState.value
        val draft = state.lineDraft
        val problem = lineProblem(draft, state.form.outputProductId, state.lines, state.editingLineIndex)
        if (problem != null) {
            _uiState.update { it.copy(error = problem) }
            return
        }
        _uiState.update {
            it.copy(
                lines = if (it.editingLineIndex != null) {
                    it.lines.toMutableList().also { rows -> rows[it.editingLineIndex] = draft }
                } else {
                    it.lines + draft
                },
                lineDraft = RecipeLine("", "", "", ""),
                editingLineIndex = null,
                error = null,
            )
        }
    }

    fun editLine(index: Int) = _uiState.update { state ->
        val line = state.lines.getOrNull(index) ?: return@update state
        state.copy(lineDraft = line, editingLineIndex = index)
    }

    fun removeLine(index: Int) = _uiState.update { state ->
        state.copy(
            lines = state.lines.filterIndexed { i, _ -> i != index },
            lineDraft = if (state.editingLineIndex == index) RecipeLine("", "", "", "") else state.lineDraft,
            editingLineIndex = if (state.editingLineIndex == index) null else state.editingLineIndex?.let {
                if (it > index) it - 1 else it
            },
        )
    }

    fun cancelLineEdit() = _uiState.update {
        it.copy(lineDraft = RecipeLine("", "", "", ""), editingLineIndex = null)
    }

    fun openRecipe(id: Long) {
        if (_uiState.value.isOpening) return
        _uiState.update { it.copy(isOpening = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchRecipe(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isOpening = false,
                        form = result.data.recipe,
                        lines = result.data.lines,
                        lineDraft = RecipeLine("", "", "", ""),
                        editingLineIndex = null,
                        tab = RecipeTab.RECIPE,
                        error = null,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isOpening = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun clearForm() = _uiState.update {
        it.copy(
            form = RecipeUiState.emptyForm(initialDate.toApi()),
            lines = emptyList(),
            lineDraft = RecipeLine("", "", "", ""),
            editingLineIndex = null,
            error = null,
            message = null,
        )
    }

    fun save() {
        val state = _uiState.value
        if (!state.canWrite || state.isSaving) return
        val form = state.form
        val problem = when {
            form.name.isBlank() -> "Give the recipe a name."
            form.outputProductId.toLongOrNull() == null -> "Choose the product this recipe makes."
            form.yieldQty.positiveOrNull() == null -> "The yield must be more than nothing."
            form.effectiveDate.isBlank() -> "Choose the date this version takes effect."
            state.lines.isEmpty() -> "Add at least one material."
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(error = problem) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val body = RecipeSaveBody(
                id = form.id,
                name = form.name.trim(),
                outputProductId = form.outputProductId.toLongOrNull() ?: 0L,
                yieldQty = form.yieldQty.toDoubleOrNull() ?: 0.0,
                lossBasis = form.lossBasis.ifBlank { null },
                lossTolerancePct = form.lossTolerancePct.toDoubleOrNull() ?: 0.0,
                suggestedCost = form.suggestedCost.takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                effectiveDate = form.effectiveDate,
                note = form.note.ifBlank { null },
                lines = state.lines.map {
                    RecipeLineBody(
                        productId = it.productId.toLongOrNull() ?: 0L,
                        baseQty = it.qty.toDoubleOrNull() ?: 0.0,
                        lineType = it.lineType,
                        note = it.note.ifBlank { null },
                    )
                },
            )
            when (val result = repository.save(body)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = result.data,
                            form = RecipeUiState.emptyForm(initialDate.toApi()),
                            lines = emptyList(),
                            lineDraft = RecipeLine("", "", "", ""),
                            editingLineIndex = null,
                        )
                    }
                    loadRecipes()
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

    fun onNewVersionRequested() {
        if (_uiState.value.form.id == null) {
            _uiState.update { it.copy(error = "Open a version first.") }
            return
        }
        _uiState.update { it.copy(confirmNewVersion = true) }
    }

    fun onNewVersionDismissed() = _uiState.update { it.copy(confirmNewVersion = false) }

    fun createNewVersion() {
        val state = _uiState.value
        val id = state.form.id ?: return
        if (state.creatingVersion) return
        _uiState.update { it.copy(creatingVersion = true, confirmNewVersion = false) }
        viewModelScope.launch {
            val date = state.form.effectiveDate.takeIf { it.isNotBlank() }
            when (val result = repository.newVersion(id, date)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(creatingVersion = false, message = "New version saved") }
                    loadRecipes()
                    result.data?.let { newId -> openRecipe(newId) }
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        creatingVersion = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onRemoveRequested(row: RecipeListRow) = _uiState.update { it.copy(removeTarget = row) }

    fun onRemoveDismissed() = _uiState.update { it.copy(removeTarget = null) }

    fun removeRecipe() {
        val state = _uiState.value
        val target = state.removeTarget ?: return
        if (state.removing) return
        _uiState.update { it.copy(removing = true, removeTarget = null) }
        viewModelScope.launch {
            when (val result = repository.destroy(target.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        val form = it.form
                        it.copy(
                            removing = false,
                            message = result.data,
                            form = if (form.id == target.id) RecipeUiState.emptyForm(initialDate.toApi()) else form,
                            lines = if (form.id == target.id) emptyList() else it.lines,
                        )
                    }
                    loadRecipes()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        removing = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /** The web's own refusals, so the form cannot post a line the server would drop. */
    private fun lineProblem(
        line: RecipeLine,
        outputProductId: String,
        lines: List<RecipeLine>,
        editingIndex: Int?,
    ): String? = when {
        line.productId.isBlank() -> "Choose a material."
        line.qty.positiveOrNull() == null -> "Every material needs a quantity."
        line.productId == outputProductId ->
            "The product the recipe makes cannot also be one of its materials."
        lines.withIndex().any { (index, row) ->
            row.productId == line.productId && index != editingIndex
        } -> "That material is already listed."
        else -> null
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val session = ServiceLocator.provideSessionManager(app)
                RecipeViewModel(
                    repository = ServiceLocator.provideRecipeRepository(app),
                    transactionDate = session.state.value.settings?.transactionDate,
                    canWrite = com.example.cashbookbd.session.Permissions.has(
                        session.permissions,
                        "production.create",
                    ),
                )
            }
        }
    }
}
