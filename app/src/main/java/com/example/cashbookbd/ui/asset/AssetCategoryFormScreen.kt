package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.data.repository.AssetCategoryInput
import com.example.cashbookbd.data.repository.AssetHead
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The draft being typed. Everything is text, because the boxes are text. */
data class AssetCategoryDraft(
    val id: Long? = null,
    val name: String = "",
    val code: String = "",
    val rate: String = "",
    val residualValue: String = "1",
    val assetCoa4Id: String = "",
    val accumDepCoa4Id: String = "",
    val depExpenseCoa4Id: String = "",
    val disposalCoa4Id: String = "",
    val notes: String = "",
    val sortOrder: Int? = null,
)

data class AssetCategoryFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val draft: AssetCategoryDraft = AssetCategoryDraft(),
    val balanceSheetHeads: List<AssetHead> = emptyList(),
    val expenseHeads: List<AssetHead> = emptyList(),
    val note: String = "",
    /** An id that is not in the list: a mislaid link, said plainly. */
    val notFound: Boolean = false,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
}

class AssetCategoryFormViewModel(
    private val repository: AssetRepository,
    private val categoryId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetCategoryFormUiState())
    val uiState: StateFlow<AssetCategoryFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * There is no read for one category — the list endpoint carries the rows and
     * both head lists together, so the form asks for that and picks its row out
     * of it. One read rather than two, and the heads cannot disagree with the
     * list the row came from.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchCategories()) {
                is Resource.Success -> {
                    val row = categoryId?.let { id -> result.data.rows.firstOrNull { it.id == id } }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            balanceSheetHeads = result.data.balanceSheetHeads,
                            expenseHeads = result.data.expenseHeads,
                            note = result.data.note,
                            notFound = categoryId != null && row == null,
                            draft = row?.let { one ->
                                AssetCategoryDraft(
                                    id = one.id,
                                    name = one.name,
                                    code = one.code,
                                    rate = trimNumber(one.rate),
                                    residualValue = trimNumber(one.residualValue),
                                    assetCoa4Id = one.assetCoa4Id,
                                    accumDepCoa4Id = one.accumDepCoa4Id,
                                    depExpenseCoa4Id = one.depExpenseCoa4Id,
                                    disposalCoa4Id = one.disposalCoa4Id,
                                    notes = one.notes,
                                    sortOrder = one.sortOrder,
                                )
                            } ?: it.draft,
                        )
                    }
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

    fun onDraft(change: (AssetCategoryDraft) -> AssetCategoryDraft) =
        _uiState.update { it.copy(draft = change(it.draft), error = null) }

    /**
     * ⚠️ Said here as well as by the server: a form that has to be submitted to
     * learn what it wants is a form that gets abandoned.
     */
    fun save() {
        val draft = _uiState.value.draft
        if (draft.name.isBlank()) {
            _uiState.update { it.copy(error = "The category needs a name.") }
            return
        }
        if (draft.rate.isBlank()) {
            _uiState.update {
                it.copy(error = "Give the rate. Nought is an answer, but it has to be typed.")
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.saveCategory(
                AssetCategoryInput(
                    id = draft.id,
                    name = draft.name,
                    code = draft.code,
                    rate = draft.rate,
                    residualValue = draft.residualValue,
                    assetCoa4Id = draft.assetCoa4Id,
                    accumDepCoa4Id = draft.accumDepCoa4Id,
                    depExpenseCoa4Id = draft.depExpenseCoa4Id,
                    disposalCoa4Id = draft.disposalCoa4Id,
                    notes = draft.notes,
                    sortOrder = draft.sortOrder,
                ),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    companion object {
        fun provideFactory(context: Context, categoryId: Long?) = viewModelFactory {
            initializer {
                AssetCategoryFormViewModel(
                    AssetRepository.get(context.applicationContext),
                    categoryId,
                )
            }
        }
    }
}

/**
 * A category being typed: its rate, and the four heads its money moves through.
 *
 * ⚠️ THE HEAD LISTS ARE NOT INTERCHANGEABLE. Cost and accumulated depreciation
 * belong to the balance sheet, the yearly charge and the gain or loss on sale to
 * the profit and loss. "Depreciation" is a word that appears in both halves, and
 * two heads pointed the wrong way round would run the books backwards with
 * nothing on screen looking odd — so each box is filled from its own list.
 */
@Composable
fun AssetCategoryFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    categoryId: Long?,
    modifier: Modifier = Modifier,
    viewModel: AssetCategoryFormViewModel = viewModel(
        factory = AssetCategoryFormViewModel.provideFactory(LocalContext.current, categoryId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // Back to the list, carrying the server's sentence: where years have already
    // been charged it answers in a paragraph, and that paragraph is the answer.
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            navController.reportAssetSaved(it)
            navController.popBackStack()
        }
    }

    AuthenticatedShell(
        title = if (state.isNew) "New category" else "Editing ${state.draft.name}",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        when {
            state.isLoading -> AssetLoading()
            state.notFound -> AssetNotice(
                text = "That category is not in the list — it may have been removed. " +
                    "Go back and pick one.",
                tone = AssetTone.Warning,
                modifier = Modifier.padding(16.dp),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.note.isNotBlank()) {
                    AssetNotice(text = state.note, tone = AssetTone.Info)
                }
                state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

                AppTextField(
                    value = state.draft.name,
                    onValueChange = { value -> viewModel.onDraft { it.copy(name = value) } },
                    label = "Vehicles",
                    caption = "Category",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.draft.code,
                    onValueChange = { value -> viewModel.onDraft { it.copy(code = value) } },
                    label = "VEH",
                    caption = "Code",
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldHint("Optional. Handy on a sticker.")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.draft.rate,
                        onValueChange = { value -> viewModel.onDraft { it.copy(rate = value) } },
                        label = "20",
                        caption = "Rate %",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.draft.residualValue,
                        onValueChange = { value ->
                            viewModel.onDraft { it.copy(residualValue = value) }
                        },
                        label = "1",
                        caption = "Stops at",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldHint(
                    "A year, of what the asset is still worth. " +
                        "It stops at one taka, so the asset never vanishes off the books.",
                )

                HeadDropdown(
                    label = "Asset head",
                    hint = "Where what it cost sits. Balance sheet.",
                    heads = state.balanceSheetHeads,
                    value = state.draft.assetCoa4Id,
                    onPicked = { value -> viewModel.onDraft { it.copy(assetCoa4Id = value) } },
                )
                HeadDropdown(
                    label = "Accumulated depreciation",
                    hint = "Grows underneath the asset. Balance sheet.",
                    heads = state.balanceSheetHeads,
                    value = state.draft.accumDepCoa4Id,
                    onPicked = { value -> viewModel.onDraft { it.copy(accumDepCoa4Id = value) } },
                )
                HeadDropdown(
                    label = "Depreciation charge",
                    hint = "This year's expense. Profit and loss.",
                    heads = state.expenseHeads,
                    value = state.draft.depExpenseCoa4Id,
                    onPicked = { value -> viewModel.onDraft { it.copy(depExpenseCoa4Id = value) } },
                )
                // The fourth head is what SELLING needs. A category can be
                // perfectly able to depreciate and unable to dispose.
                HeadDropdown(
                    label = "Gain or loss on sale",
                    hint = "Only needed to sell or write one off. Profit and loss.",
                    heads = state.expenseHeads,
                    value = state.draft.disposalCoa4Id,
                    onPicked = { value -> viewModel.onDraft { it.copy(disposalCoa4Id = value) } },
                )

                AppTextField(
                    value = state.draft.notes,
                    onValueChange = { value -> viewModel.onDraft { it.copy(notes = value) } },
                    label = "Optional — where the rate came from",
                    caption = "Note",
                    modifier = Modifier.fillMaxWidth(),
                )

                // The two things somebody typing a rate needs told, and neither
                // is obvious from the form: the charge falls every year rather
                // than staying flat, and a rate changed later does not reach back.
                FieldHint(
                    "Reducing balance: 20% of 100,000 is 20,000 in the first full year and " +
                        "16,000 in the next, because the second year is charged on 80,000. " +
                        "Changing a rate here reaches the next run — every year already " +
                        "charged keeps the rate it was charged at.",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        text = "Save",
                        onClick = viewModel::save,
                        isLoading = state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Back to the list",
                        onClick = { navController.popBackStack() },
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A head box.
 *
 * ⚠️ The first line is NAMED, not blank. "Not chosen yet" is a real state — a
 * category saves perfectly well without its heads and simply cannot be
 * depreciated — so the box says which state it is in rather than looking unfilled.
 */
@Composable
private fun HeadDropdown(
    label: String,
    hint: String,
    heads: List<AssetHead>,
    value: String,
    onPicked: (String) -> Unit,
) {
    val options = remember(heads) {
        listOf(SelectorOption("", "Not chosen yet")) +
            heads.map { SelectorOption(it.id.toString(), it.label) }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSelectDropdown(
            label = label,
            options = options,
            selected = options.firstOrNull { it.id == value } ?: options.first(),
            onSelected = { onPicked(it.id) },
        )
        FieldHint(hint)
    }
}

/** The small grey sentence under a box. */
@Composable
private fun FieldHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
        modifier = Modifier.padding(top = 2.dp),
    )
}
