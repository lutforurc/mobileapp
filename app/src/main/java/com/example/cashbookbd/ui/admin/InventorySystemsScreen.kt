package com.example.cashbookbd.ui.admin

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.muted
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One inventory system row. Core slugs are load-bearing — code branches on them. */
data class InventorySystemRow(
    val id: Long,
    val name: String,
    val slug: String,
    val status: Int,
    val description: String,
    val isCore: Boolean,
)

data class InventorySystemsUiState(
    val isLoading: Boolean = false,
    val rows: List<InventorySystemRow> = emptyList(),
    val listError: String? = null,

    // Form
    val editingId: Long? = null,
    val editingIsCore: Boolean = false,
    val name: String = "",
    val slug: String = "",
    val statusActive: Boolean = true,
    val description: String = "",
    val isSaving: Boolean = false,

    val confirmDelete: InventorySystemRow? = null,
    val deletingId: Long? = null,

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class InventorySystemsViewModel(
    private val api: ReportApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventorySystemsUiState())
    val uiState: StateFlow<InventorySystemsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, listError = null) }
        viewModelScope.launch {
            val result = call { api.get("admin/inventory-systems", emptyMap()) }
            when (result) {
                is Resource.Success -> {
                    val rows = result.data.getAsJsonObject("data")
                        ?.getAsJsonObject("data")
                        ?.get("inventory_systems")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            InventorySystemRow(
                                id = o.str("id")?.toLongOrNull() ?: return@mapNotNull null,
                                name = o.str("name").orEmpty(),
                                slug = o.str("slug").orEmpty(),
                                status = o.str("status")?.toDoubleOrNull()?.toInt() ?: 1,
                                description = o.str("description").orEmpty(),
                                isCore = o.str("is_core")
                                    ?.let { it == "true" || it.toDoubleOrNull() == 1.0 } == true,
                            )
                        }
                        .orEmpty()
                    _uiState.update { it.copy(isLoading = false, rows = rows) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        listError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value.take(128)) }
    fun onSlug(value: String) = _uiState.update { it.copy(slug = value.take(64)) }
    fun onStatus(option: SelectorOption) = _uiState.update { it.copy(statusActive = option.id == "1") }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value.take(512)) }

    fun edit(row: InventorySystemRow) = _uiState.update {
        it.copy(
            editingId = row.id,
            editingIsCore = row.isCore,
            name = row.name,
            slug = row.slug,
            statusActive = row.status == 1,
            description = row.description,
        )
    }

    fun cancelEdit() = _uiState.update {
        it.copy(editingId = null, editingIsCore = false, name = "", slug = "", statusActive = true, description = "")
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "Please enter a name.") }
            return
        }
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val body = mapOf<String, Any>(
                "name" to state.name.trim(),
                // Blank derives from the name server-side; a core slug is
                // locked server-side whatever is sent.
                "slug" to state.slug.trim(),
                "status" to if (state.statusActive) 1 else 0,
                "description" to state.description.trim(),
            )
            val result = call {
                val id = state.editingId
                if (id == null) api.postAny("admin/inventory-systems", body)
                else api.putAny("admin/inventory-systems/$id", body)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = result.data.str("message")?.ifBlank { null } ?: "Saved.",
                        )
                    }
                    cancelEdit()
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun requestDelete(row: InventorySystemRow) = _uiState.update { it.copy(confirmDelete = row) }
    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = null) }

    /**
     * The server refuses a core system, and one any branch still uses (409
     * with the reason) — the message is surfaced as-is.
     */
    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = row.id) }
        viewModelScope.launch {
            val result = call { api.delete("admin/inventory-systems/${row.id}") }
            _uiState.update {
                it.copy(
                    deletingId = null,
                    message = when (result) {
                        is Resource.Success -> result.data.str("message")?.ifBlank { null } ?: "Deleted."
                        is Resource.Error -> result.message
                        Resource.Loading -> null
                    },
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
            load()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /** Shared transport guard: 401 → relogin, success flag → verdict. */
    private suspend fun call(
        request: suspend () -> retrofit2.Response<com.google.gson.JsonElement>,
    ): Resource<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val response = request()
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: response.errorBody()?.string()
                    ?.let { runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull() }
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false || (!response.isSuccessful && response.code() !in 200..299)) {
                return@withContext Resource.Error(
                    body.str("message")?.ifBlank { null }
                        ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(body)
        } catch (e: java.io.IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                InventorySystemsViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                )
            }
        }
    }
}

private fun JsonObject.str(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

private val STATUS_OPTIONS = listOf(
    SelectorOption(id = "1", label = "Active"),
    SelectorOption(id = "0", label = "Inactive"),
)

/**
 * The platform's inventory systems (general/electronics/construction/trading +
 * any custom ones). Core systems keep their slug and cannot be deleted — code
 * branches on those slugs.
 */
@Composable
fun InventorySystemsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventorySystemsViewModel = viewModel(
        factory = InventorySystemsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        title = "Inventory Systems",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = if (state.editingId == null) "Add Inventory System" else "Edit Inventory System",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = AppFontWeight.SemiBold,
                            )
                            AppTextField(
                                value = state.name,
                                onValueChange = viewModel::onName,
                                label = "e.g. Trading Inventory",
                                caption = "Name *",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = state.slug,
                                onValueChange = viewModel::onSlug,
                                label = "Leave blank to derive from the name",
                                caption = "Slug",
                                enabled = !state.editingIsCore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = if (state.editingIsCore) {
                                    "Core system — the slug is locked because routing depends on it."
                                } else {
                                    "Code branches on this. Leave blank to derive it from the name."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                            AppSelectDropdown(
                                label = "Status",
                                options = STATUS_OPTIONS,
                                selected = STATUS_OPTIONS.first { it.id == if (state.statusActive) "1" else "0" },
                                onSelected = viewModel::onStatus,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = state.description,
                                onValueChange = viewModel::onDescription,
                                label = "Description (optional)",
                                multiline = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(
                                    text = if (state.editingId == null) "Add" else "Update",
                                    onClick = viewModel::save,
                                    isLoading = state.isSaving,
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.editingId != null) {
                                    SecondaryButton(
                                        text = "Cancel",
                                        onClick = viewModel::cancelEdit,
                                        enabled = !state.isSaving,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.listError != null -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(state.listError!!, color = MaterialTheme.colorScheme.onBackground)
                            LinkButton(text = "Retry", onClick = viewModel::load)
                        }
                    }
                    state.rows.isEmpty() -> item {
                        Text(
                            text = "No inventory systems yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    else -> items(state.rows, key = { it.id }) { row ->
                        SystemRow(
                            row = row,
                            isDeleting = state.deletingId == row.id,
                            onEdit = { viewModel.edit(row) },
                            onDelete = { viewModel.requestDelete(row) },
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete \"${row.name}\"?") },
            text = { Text("A system any branch still uses, and the four core systems, are refused by the server.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SystemRow(
    row: InventorySystemRow,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                    color = onScreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.status == 1) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = if (row.status == 1) MaterialTheme.appColors.success
                    else MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            Text(
                text = row.slug + if (row.isCore) "  •  core" else "",
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            if (row.description.isNotBlank()) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.muted(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                LinkButton(text = "Edit", onClick = onEdit, enabled = !isDeleting)
                if (!row.isCore) {
                    LinkButton(
                        text = if (isDeleting) "Deleting…" else "Delete",
                        onClick = onDelete,
                        enabled = !isDeleting,
                    )
                }
            }
        }
    }
}
