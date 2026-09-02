package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetCategoryRow
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetCategoriesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<AssetCategoryRow> = emptyList(),
    /** The server's own paragraph about what these heads are for. */
    val note: String = "",
    val message: String? = null,
    val pendingDelete: AssetCategoryRow? = null,
    val isDeleting: Boolean = false,
    val sessionExpired: Boolean = false,
)

class AssetCategoriesViewModel(private val repository: AssetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetCategoriesUiState())
    val uiState: StateFlow<AssetCategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchCategories()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data.rows, note = result.data.note)
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

    fun askDelete(row: AssetCategoryRow) = _uiState.update { it.copy(pendingDelete = row) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.deleteCategory(row.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isDeleting = false, pendingDelete = null, message = result.data)
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** A form came back having saved: show what the server said, then re-read. */
    fun onSaved(message: String) {
        _uiState.update { it.copy(message = message) }
        load()
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                AssetCategoriesViewModel(AssetRepository.get(context.applicationContext))
            }
        }
    }
}

/**
 * What kinds of thing this company owns, how fast each wears out, and where its
 * money lives.
 *
 * ⚠️ THE RATE BELONGS TO THE CLASS, not to the thing: "vehicles at 20%" is one
 * decision applied to every lorry, and typed onto each lorry it would be typed
 * wrongly on one of them.
 *
 * ⚠️ A CATEGORY WITH NO HEADS SAYS SO HERE, rather than leaving it to be found
 * out in June when the run refuses.
 */
@Composable
fun AssetCategoriesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetCategoriesViewModel = viewModel(
        factory = AssetCategoriesViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // Coming back from the form: the list is what says whether the save landed,
    // and the server's own sentence is what it says.
    OnAssetSaved(navController) { viewModel.onSaved(it) }

    AuthenticatedShell(
        title = "Asset Categories",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.note.isNotBlank()) {
                AssetNotice(
                    text = state.note,
                    tone = AssetTone.Info,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            state.message?.let { message ->
                AssetNotice(
                    text = message,
                    tone = AssetTone.Success,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddButton(
                    text = "Add a category",
                    onClick = { navController.navigate(AssetMenu.categoryForm(null)) },
                    compact = true,
                )
                if (state.message != null) {
                    LinkButton(text = "Dismiss", onClick = viewModel::onMessageShown)
                }
            }

            when {
                state.error != null -> AssetError(state.error!!, viewModel::load)
                state.isLoading && state.rows.isEmpty() -> AssetLoading()
                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = categoryColumns(
                            onEdit = { navController.navigate(AssetMenu.categoryForm(it.id)) },
                            onDelete = viewModel::askDelete,
                        ),
                        data = state.rows,
                        noDataMessage =
                            "No categories yet. Add one — furniture, vehicles, computers — and give it a rate.",
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Remove this category?") },
            text = {
                Text(
                    "\"${row.name}\" will be removed. Nothing is filed under it, so nothing else moves.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Remove",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isDeleting,
                    isLoading = state.isDeleting,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancelDelete) },
        )
    }
}

@Composable
private fun categoryColumns(
    onEdit: (AssetCategoryRow) -> Unit,
    onDelete: (AssetCategoryRow) -> Unit,
): List<ReportColumn<AssetCategoryRow>> {
    val muted = MaterialTheme.appColors.textMuted
    val warning = MaterialTheme.appColors.warning
    return listOf(
        ReportColumn("CATEGORY", ReportColWidth.Fixed(150.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (row.notes.isNotBlank()) {
                        Text(
                            text = row.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        // Reducing balance: a percentage of what the thing is still worth, said
        // beside the figure so nobody reads it as a share of the cost.
        ReportColumn("RATE", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
            ReportTableCell.Slot {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.End,
                ) {
                    Text(percentText(row.rate), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "of what it is worth",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 2,
                    )
                }
            }
        },
        ReportColumn("STOPS AT", ReportColWidth.Fixed(80.dp), TextAlign.End) { row, _ ->
            cellText(AmountFormat.format(row.residualValue))
        },
        ReportColumn("HEADS", ReportColWidth.Fixed(190.dp)) { row, _ ->
            ReportTableCell.Slot {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    if (row.readyToPost) {
                        Text(row.assetHeadName, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        Text(
                            text = "less ${row.accumDepHeadName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 2,
                        )
                        Text(
                            text = "charged to ${row.expenseHeadName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 2,
                        )
                        // The fourth head is only needed to SELL, so its absence
                        // is said quietly: a category that cannot be disposed of
                        // is perfectly able to be depreciated.
                        Text(
                            text = if (row.readyToDispose) {
                                "sold through ${row.disposalHeadName}"
                            } else {
                                "no gain-or-loss head — cannot be sold yet"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.readyToDispose) muted else warning,
                            maxLines = 2,
                        )
                    } else {
                        Text(
                            text = "not set — cannot be depreciated",
                            style = MaterialTheme.typography.labelSmall,
                            color = warning,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        ReportColumn("ASSETS", ReportColWidth.Fixed(70.dp), TextAlign.Center) { row, _ ->
            cellText(row.assetCount.toString())
        },
        ReportColumn("ACTION", ReportColWidth.Fixed(96.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { onEdit(row) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit this category",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Only where nothing is filed under it. A category holding
                    // assets is switched off, not removed — the rate its
                    // schedule was worked out from lives here.
                    if (row.assetCount == 0) {
                        IconButton(onClick = { onDelete(row) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove — nothing is filed under it",
                                tint = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                }
            }
        },
    )
}
