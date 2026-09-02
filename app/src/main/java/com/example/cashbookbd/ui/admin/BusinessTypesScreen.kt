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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
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
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
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

/**
 * One trade a company can say it is in. [branchesCount] is how many branches
 * already stand on this id — the reason the list has a switch and no bin.
 */
data class BusinessTypeRow(
    val id: Long,
    val name: String,
    val status: Int,
    val description: String,
    val branchesCount: Int,
)

data class BusinessTypesUiState(
    val isLoading: Boolean = false,
    val rows: List<BusinessTypeRow> = emptyList(),
    val listError: String? = null,

    // Form
    val editingId: Long? = null,
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false,

    /** Rows whose switch is waiting on the server. */
    val togglingIds: Set<Long> = emptySet(),

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * The Business Types list (web `/admin/business-types`, api b7505a39): the
 * trades a branch or a registering company may pick from, finally maintainable
 * instead of seeded once by SQL.
 *
 * There is deliberately NO delete: `com_branches.business_type_id` stores the
 * number and code branches on it, so a freed id would be inherited by the next
 * trade created. Switching one off takes it out of the Add Branch and
 * registration dropdowns and leaves every existing branch as it was.
 */
class BusinessTypesViewModel(
    private val api: ReportApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusinessTypesUiState())
    val uiState: StateFlow<BusinessTypesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(silent: Boolean = false) {
        if (!silent) _uiState.update { it.copy(isLoading = true, listError = null) }
        viewModelScope.launch {
            val result = call { api.get(PATH, emptyMap()) }
            when (result) {
                is Resource.Success -> {
                    val rows = result.data.getAsJsonObject("data")
                        ?.getAsJsonObject("data")
                        ?.get("business_types")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            BusinessTypeRow(
                                id = o.str("id")?.toLongOrNull() ?: return@mapNotNull null,
                                name = o.str("name").orEmpty(),
                                status = o.str("status")?.toDoubleOrNull()?.toInt() ?: 1,
                                description = o.str("description").orEmpty(),
                                branchesCount = o.str("branches_count")?.toDoubleOrNull()?.toInt() ?: 0,
                            )
                        }
                        .orEmpty()
                    _uiState.update { it.copy(isLoading = false, rows = rows) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        listError = if (silent) it.listError else result.message,
                        message = if (silent) result.message else it.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value.take(128)) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value.take(512)) }

    fun edit(row: BusinessTypeRow) = _uiState.update {
        it.copy(editingId = row.id, name = row.name, description = row.description)
    }

    fun cancelEdit() = _uiState.update { it.copy(editingId = null, name = "", description = "") }

    /** Create or rename. The name is unique server-side; its refusal is shown as sent. */
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
                "description" to state.description.trim(),
            )
            val result = call {
                val id = state.editingId
                if (id == null) api.postAny(PATH, body) else api.putAny("$PATH/$id", body)
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
                    load(silent = true)
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

    /**
     * The web's switch: painted at once, reverted if the save is refused. The
     * status is SENT rather than flipped server-side, so two fast taps settle
     * on what the screen shows, not on how many requests arrived.
     */
    fun toggle(row: BusinessTypeRow, on: Boolean) {
        if (row.id in _uiState.value.togglingIds) return
        _uiState.update { s ->
            s.copy(
                rows = s.rows.map { if (it.id == row.id) it.copy(status = if (on) 1 else 0) else it },
                togglingIds = s.togglingIds + row.id,
            )
        }
        viewModelScope.launch {
            val result = call { api.patchAny("$PATH/${row.id}/toggle", mapOf("status" to if (on) 1 else 0)) }
            _uiState.update { s ->
                when (result) {
                    is Resource.Success -> s.copy(togglingIds = s.togglingIds - row.id)
                    is Resource.Error -> s.copy(
                        rows = s.rows.map { if (it.id == row.id) it.copy(status = if (on) 0 else 1) else it },
                        togglingIds = s.togglingIds - row.id,
                        message = result.message,
                        sessionExpired = s.sessionExpired || result.isUnauthorized,
                    )
                    Resource.Loading -> s
                }
            }
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
            if (success == false || response.code() !in 200..299) {
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
        private const val PATH = "admin/business-types"

        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                BusinessTypesViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                )
            }
        }
    }
}

private fun JsonObject.str(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

/**
 * Business Types — form on top, list under it, the shape the Inventory Systems
 * screen already has. A switch per row and no bin (see the view model).
 */
@Composable
fun BusinessTypesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessTypesViewModel = viewModel(
        factory = BusinessTypesViewModel.provideFactory(LocalContext.current)
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
        title = "Business Types",
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
                                text = if (state.editingId == null) "Add Business Type" else "Edit Business Type",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = AppFontWeight.SemiBold,
                            )
                            AppTextField(
                                value = state.name,
                                onValueChange = viewModel::onName,
                                label = "e.g. Hotel / Motel",
                                caption = "Name *",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = state.description,
                                onValueChange = viewModel::onDescription,
                                label = "Description (optional)",
                                multiline = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Switching a type off takes it out of the Add Branch and " +
                                    "registration lists; branches already on it are left alone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
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
                            LinkButton(text = "Retry", onClick = { viewModel.load() })
                        }
                    }
                    state.rows.isEmpty() -> item {
                        Text(
                            text = "No business types yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    else -> items(state.rows, key = { it.id }) { row ->
                        BusinessTypeRowCard(
                            row = row,
                            isToggling = row.id in state.togglingIds,
                            onEdit = { viewModel.edit(row) },
                            onToggle = { viewModel.toggle(row, it) },
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BusinessTypeRowCard(
    row: BusinessTypeRow,
    isToggling: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
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
                Switch(
                    checked = row.status == 1,
                    onCheckedChange = onToggle,
                    enabled = !isToggling,
                    modifier = Modifier.scale(0.8f),
                )
            }
            Text(
                text = when (row.branchesCount) {
                    0 -> "No branch on it yet"
                    1 -> "1 branch on it"
                    else -> "${row.branchesCount} branches on it"
                },
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
                LinkButton(text = "Edit", onClick = onEdit)
            }
        }
    }
}
