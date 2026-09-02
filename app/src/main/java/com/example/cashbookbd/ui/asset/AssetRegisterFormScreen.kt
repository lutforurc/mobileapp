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
import com.example.cashbookbd.data.repository.AssetCategoryOption
import com.example.cashbookbd.data.repository.AssetInput
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDraft(
    val id: Long? = null,
    val categoryId: String = "",
    val code: String = "",
    val name: String = "",
    val serialNo: String = "",
    val location: String = "",
    val purchaseDate: String = todayApi(),
    val cost: String = "",
    val openingAccumDep: String = "",
    val openingAsOn: String = "",
    val notes: String = "",
    /**
     * The SERVER'S answer, not this form's guess: cost and the brought-forward
     * figures are frozen once a year has been charged, because a schedule
     * already footed on them would stop footing.
     */
    val locked: Boolean = false,
)

data class AssetFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val draft: AssetDraft = AssetDraft(),
    val categories: List<AssetCategoryOption> = emptyList(),
    val notFound: Boolean = false,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
}

class AssetFormViewModel(
    private val repository: AssetRepository,
    private val assetId: Long?,
    private val branchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetFormUiState())
    val uiState: StateFlow<AssetFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // The register read carries the category list beside the rows, so
            // the form's dropdown and the list's filter cannot drift apart.
            when (val listing = repository.fetchRegister(branchId, null, "", "", 1, perPage = 1)) {
                is Resource.Success -> _uiState.update { it.copy(categories = listing.data.categories) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        error = listing.message,
                        sessionExpired = it.sessionExpired || listing.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }

            if (assetId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            when (val result = repository.fetchAsset(assetId)) {
                is Resource.Success -> {
                    val asset = result.data.asset
                    if (asset == null) {
                        _uiState.update { it.copy(isLoading = false, notFound = true) }
                        return@launch
                    }
                    // `locked` is the same sum the list makes — charged here
                    // above nought — rather than a second rule.
                    val charged = result.data.depreciations.sumOf { it.amount }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            draft = AssetDraft(
                                id = asset.id,
                                categoryId = asset.categoryId?.toString().orEmpty(),
                                code = asset.code,
                                name = asset.name,
                                serialNo = asset.serialNo,
                                location = asset.location,
                                purchaseDate = asset.purchaseDate,
                                cost = trimNumber(asset.cost),
                                openingAccumDep = trimNumber(asset.openingAccumDep),
                                openingAsOn = asset.openingAsOn,
                                notes = asset.notes,
                                locked = charged > 0.0 || asset.locked,
                            ),
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

    fun onDraft(change: (AssetDraft) -> AssetDraft) =
        _uiState.update { it.copy(draft = change(it.draft), error = null) }

    fun save() {
        val draft = _uiState.value.draft
        if (draft.code.isBlank() || draft.name.isBlank()) {
            _uiState.update { it.copy(error = "An asset needs a code and a name.") }
            return
        }
        val categoryId = draft.categoryId.toLongOrNull()
        if (categoryId == null) {
            _uiState.update { it.copy(error = "Which category is it? The rate comes from there.") }
            return
        }
        if (draft.cost.isBlank()) {
            _uiState.update { it.copy(error = "What did it cost?") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.saveAsset(
                AssetInput(
                    id = draft.id,
                    branchId = branchId,
                    categoryId = categoryId,
                    code = draft.code,
                    name = draft.name,
                    serialNo = draft.serialNo,
                    location = draft.location,
                    purchaseDate = draft.purchaseDate,
                    cost = draft.cost,
                    openingAccumDep = draft.openingAccumDep,
                    openingAsOn = draft.openingAsOn,
                    notes = draft.notes,
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
        if (value == 0.0) "" else AmountText.plain(value)

    companion object {
        fun provideFactory(context: Context, assetId: Long?, branchId: Long?) = viewModelFactory {
            initializer {
                AssetFormViewModel(
                    AssetRepository.get(context.applicationContext),
                    assetId,
                    branchId,
                )
            }
        }
    }
}

/** A figure as it goes back into a text box: no grouping, no trailing noughts. */
internal object AmountText {
    fun plain(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/**
 * An asset being typed: what it is, what it cost, and what the old books had
 * already charged against it.
 *
 * ⚠️ AN OLD ASSET BRINGS ITS DEPRECIATION AS A MEMORY. Something carried over
 * is already in the ledger — its cost in the asset head, its accumulated
 * depreciation in the depreciation head, put there by whoever wrote the opening
 * entries. The two boxes at the foot record it and post NOTHING; posting again
 * would double both sides of the balance sheet. The form says so, because a box
 * that quietly does nothing is one somebody will fill in twice.
 */
@Composable
fun AssetRegisterFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    assetId: Long?,
    branchId: Long?,
    modifier: Modifier = Modifier,
    viewModel: AssetFormViewModel = viewModel(
        factory = AssetFormViewModel.provideFactory(LocalContext.current, assetId, branchId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            navController.reportAssetSaved(it)
            navController.popBackStack()
        }
    }

    val categoryOptions = remember(state.categories) {
        listOf(SelectorOption("", "Choose one")) +
            state.categories.map { SelectorOption(it.id.toString(), it.label) }
    }

    AuthenticatedShell(
        title = if (state.isNew) "New asset" else "Editing ${state.draft.name}",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        when {
            state.isLoading -> AssetLoading()
            state.notFound -> AssetNotice(
                text = "That asset could not be found — it may have been removed. " +
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
                state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

                AppTextField(
                    value = state.draft.code,
                    onValueChange = { value -> viewModel.onDraft { it.copy(code = value) } },
                    label = "VEH-001",
                    caption = "Code",
                    modifier = Modifier.fillMaxWidth(),
                )
                FormNote("What goes on the sticker.")

                AppTextField(
                    value = state.draft.name,
                    onValueChange = { value -> viewModel.onDraft { it.copy(name = value) } },
                    label = "Toyota Hiace, Dhaka Metro Ga 11-2233",
                    caption = "Asset",
                    modifier = Modifier.fillMaxWidth(),
                )

                AppSelectDropdown(
                    label = "Category",
                    options = categoryOptions,
                    selected = categoryOptions.firstOrNull { it.id == state.draft.categoryId }
                        ?: categoryOptions.first(),
                    onSelected = { picked -> viewModel.onDraft { it.copy(categoryId = picked.id) } },
                )
                FormNote("The rate comes from here.")

                AssetDateField(
                    label = "Bought on",
                    value = state.draft.purchaseDate,
                    onPicked = { value -> viewModel.onDraft { it.copy(purchaseDate = value) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                AppTextField(
                    value = state.draft.cost,
                    onValueChange = { value -> viewModel.onDraft { it.copy(cost = value) } },
                    label = "0",
                    caption = "Cost",
                    enabled = !state.draft.locked,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormNote(
                    if (state.draft.locked) {
                        "Frozen — a year has been charged against it."
                    } else {
                        "What it cost. This never changes afterwards."
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = state.draft.serialNo,
                        onValueChange = { value -> viewModel.onDraft { it.copy(serialNo = value) } },
                        label = "Serial no",
                        caption = "Serial no",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.draft.location,
                        onValueChange = { value -> viewModel.onDraft { it.copy(location = value) } },
                        label = "Head office, second floor",
                        caption = "Where it is",
                        modifier = Modifier.weight(1f),
                    )
                }

                // ⚠️ THE HALF THAT POSTS NOTHING, and it says so.
                Text(
                    text = "Brought forward from the old books",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = AppFontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                AppTextField(
                    value = state.draft.openingAccumDep,
                    onValueChange = { value ->
                        viewModel.onDraft { it.copy(openingAccumDep = value) }
                    },
                    label = "0",
                    caption = "Depreciation so far",
                    enabled = !state.draft.locked,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormNote(
                    if (state.draft.locked) {
                        "Frozen — a year has been charged against it."
                    } else {
                        "What has already been charged against it."
                    },
                )
                AssetDateField(
                    label = "As on",
                    value = state.draft.openingAsOn,
                    onPicked = { value -> viewModel.onDraft { it.copy(openingAsOn = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "The day that figure was true",
                )
                FormNote(
                    "Leave both empty for something bought new. For an asset carried over, " +
                        "enter what it originally cost above and what has been charged against " +
                        "it here — not what it is worth now. Nothing is posted from this box: " +
                        "those figures are already in the ledger from the old books' opening " +
                        "entries, and posting them again would double both the asset and the " +
                        "depreciation.",
                )

                AppTextField(
                    value = state.draft.notes,
                    onValueChange = { value -> viewModel.onDraft { it.copy(notes = value) } },
                    label = "Optional",
                    caption = "Note",
                    modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun FormNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
        modifier = Modifier.padding(top = 2.dp),
    )
}
