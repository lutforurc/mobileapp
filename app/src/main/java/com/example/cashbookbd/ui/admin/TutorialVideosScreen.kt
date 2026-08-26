package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One walkthrough-video row (`admin/tutorial-videos`). The operator's job is
 * the two URL boxes and the switch; title/group/key describe the screen and
 * are corrected through the row's own Edit dialog instead.
 */
data class TutorialVideoRow(
    val id: Long,
    val screenKey: String,
    val groupName: String,
    val title: String,
    /** Draft state of the two URL boxes — posted whole by Save All. */
    val webUrl: String,
    val mobileUrl: String,
    val active: Boolean,
)

data class TutorialVideosUiState(
    val isLoading: Boolean = false,
    val rows: List<TutorialVideoRow> = emptyList(),
    val listError: String? = null,

    val isSaving: Boolean = false,

    /** The Add Screen / row Edit dialog, when open. id == null means Add. */
    val dialogRowId: Long? = null,
    val showDialog: Boolean = false,
    val dialogKey: String = "",
    val dialogTitle: String = "",
    val dialogGroup: String = "",

    val confirmDelete: TutorialVideoRow? = null,

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Editing the walkthrough video list — the platform operator's own screen,
 * mirroring the web's Tutorial Videos admin page (the server additionally
 * restricts every route to the platform admin). This app is one of the two
 * platforms those URLs serve: the Mobile URL column is what the tutorial
 * buttons across this app open.
 */
class TutorialVideosViewModel(
    private val api: ReportApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorialVideosUiState())
    val uiState: StateFlow<TutorialVideosUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, listError = null) }
        viewModelScope.launch {
            val result = call { api.get("admin/tutorial-videos", emptyMap()) }
            when (result) {
                is Resource.Success -> {
                    val rows = result.data
                        ?.get("tutorial_videos")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            TutorialVideoRow(
                                id = o.long("id") ?: return@mapNotNull null,
                                screenKey = o.text("screen_key"),
                                groupName = o.text("group_name").ifBlank { "other" },
                                title = o.text("title"),
                                webUrl = o.text("web_url"),
                                mobileUrl = o.text("mobile_url"),
                                active = o.long("status") != 0L,
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

    // ---- The URL boxes and the switch (saved whole, like the web) ----------

    fun onWebUrl(id: Long, value: String) = editRow(id) { it.copy(webUrl = value) }
    fun onMobileUrl(id: Long, value: String) = editRow(id) { it.copy(mobileUrl = value) }
    fun onActive(id: Long, value: Boolean) = editRow(id) { it.copy(active = value) }

    private fun editRow(id: Long, transform: (TutorialVideoRow) -> TutorialVideoRow) =
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.id == id) transform(it) else it })
        }

    /**
     * Saves every row's URL pair and switch in one go (`PUT /`), the way the
     * web's table posts its whole list back. A cleared box retires the video —
     * the server stores it as NULL and the settings payload stops offering it.
     */
    fun saveAll() {
        val rows = _uiState.value.rows
        if (rows.isEmpty() || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val videos = rows.map { row ->
                mutableMapOf<String, Any>(
                    "id" to row.id,
                    "status" to if (row.active) 1 else 0,
                ).apply {
                    row.webUrl.trim().takeIf { it.isNotEmpty() }?.let { put("web_url", it) }
                    row.mobileUrl.trim().takeIf { it.isNotEmpty() }?.let { put("mobile_url", it) }
                }
            }
            val result = call { api.put("admin/tutorial-videos", mapOf("videos" to videos)) }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = "Tutorial videos saved.") }
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

    // ---- Add Screen / correct a row's own details --------------------------

    fun openAdd() = _uiState.update {
        it.copy(showDialog = true, dialogRowId = null, dialogKey = "", dialogTitle = "", dialogGroup = "")
    }

    fun openEdit(row: TutorialVideoRow) = _uiState.update {
        it.copy(
            showDialog = true,
            dialogRowId = row.id,
            dialogKey = row.screenKey,
            dialogTitle = row.title,
            dialogGroup = row.groupName,
        )
    }

    fun onDialogKey(value: String) = _uiState.update { it.copy(dialogKey = value) }
    fun onDialogTitle(value: String) = _uiState.update { it.copy(dialogTitle = value) }
    fun onDialogGroup(value: String) = _uiState.update { it.copy(dialogGroup = value) }
    fun closeDialog() = _uiState.update { it.copy(showDialog = false) }

    fun submitDialog() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val body = mutableMapOf<String, Any>(
                "screen_key" to state.dialogKey.trim(),
                "title" to state.dialogTitle.trim(),
            ).apply {
                state.dialogGroup.trim().takeIf { it.isNotEmpty() }?.let { put("group_name", it) }
            }
            val result = call {
                val id = state.dialogRowId
                if (id == null) {
                    api.postAny("admin/tutorial-videos", body)
                } else {
                    // update() keeps the URLs it is sent; the boxes on screen
                    // are the current draft, so they ride along unchanged.
                    val row = state.rows.firstOrNull { it.id == id }
                    row?.webUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { body["web_url"] = it }
                    row?.mobileUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { body["mobile_url"] = it }
                    api.putAny("admin/tutorial-videos/$id", body)
                }
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showDialog = false,
                            message = if (state.dialogRowId == null) "Screen added." else "Screen updated.",
                        )
                    }
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

    // ---- Delete ------------------------------------------------------------

    fun requestDelete(row: TutorialVideoRow) = _uiState.update { it.copy(confirmDelete = row) }
    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, isSaving = true) }
        viewModelScope.launch {
            val result = call { api.delete("admin/tutorial-videos/${row.id}") }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = "Screen removed.") }
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /**
     * Shared transport: runs the request, unwraps the foundData envelope
     * (`data.data` double-wrap) and turns refusals into readable errors.
     * Success carries the payload object (or null when the answer has none).
     */
    private suspend fun call(
        request: suspend () -> retrofit2.Response<com.google.gson.JsonElement>,
    ): Resource<JsonObject?> = withContext(Dispatchers.IO) {
        try {
            val response = request()
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("Only the platform administrator can manage tutorial videos.")
            }
            val body = (response.body() ?: response.errorBody()?.let {
                runCatching { com.google.gson.JsonParser.parseString(it.string()) }.getOrNull()
            })?.takeIf { it.isJsonObject }?.asJsonObject
            val success = body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = body?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
            when {
                success == false -> Resource.Error(message ?: "The request was refused.")
                !response.isSuccessful && response.code() != 201 ->
                    Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")
                else -> {
                    val data = body?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    val inner = data?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    Resource.Success(inner ?: data)
                }
            }
        } catch (e: java.io.IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                TutorialVideosViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                )
            }
        }
    }
}

/**
 * The walkthrough list: rows grouped the way the admin screen lays them out,
 * each with its two URL boxes and its switch, saved whole by the one button —
 * plus the row's own Edit (name/group/key) and Delete, and Add Screen for the
 * handful of screens that can never register themselves.
 */
@Composable
fun TutorialVideosScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TutorialVideosViewModel = viewModel(
        factory = TutorialVideosViewModel.provideFactory(LocalContext.current),
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
        title = "Tutorial Videos",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton(
                        text = "Save All",
                        onClick = viewModel::saveAll,
                        isLoading = state.isSaving,
                        enabled = state.rows.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Add Screen",
                        onClick = viewModel::openAdd,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.listError != null -> Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.listError!!, color = MaterialTheme.colorScheme.onBackground)
                        LinkButton(text = "Retry", onClick = viewModel::load)
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val grouped = state.rows.groupBy { it.groupName }
                        grouped.forEach { (group, rows) ->
                            item(key = "group-$group") {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = AppFontWeight.Bold,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(rows.size, key = { rows[it].id }) { index ->
                                TutorialVideoCard(
                                    row = rows[index],
                                    onWebUrl = viewModel::onWebUrl,
                                    onMobileUrl = viewModel::onMobileUrl,
                                    onActive = viewModel::onActive,
                                    onEdit = viewModel::openEdit,
                                    onDelete = viewModel::requestDelete,
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.showDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeDialog,
            title = { Text(if (state.dialogRowId == null) "Add Screen" else "Edit Screen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The key is what the screen asks for: its route with any " +
                            "record id written as :id, or the explicit screen key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    AppTextField(
                        value = state.dialogKey,
                        onValueChange = viewModel::onDialogKey,
                        label = "Screen Key",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.dialogTitle,
                        onValueChange = viewModel::onDialogTitle,
                        label = "Title",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.dialogGroup,
                        onValueChange = viewModel::onDialogGroup,
                        label = "Group (blank = from the key)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::submitDialog,
                    enabled = !state.isSaving &&
                        state.dialogKey.isNotBlank() && state.dialogTitle.isNotBlank(),
                ) { Text(if (state.isSaving) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeDialog, enabled = !state.isSaving) { Text("Cancel") }
            },
        )
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Remove screen?") },
            text = {
                Text(
                    "Remove \"${row.title}\" from the list? A screen that still " +
                        "announces itself comes back on its next visit.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TutorialVideoCard(
    row: TutorialVideoRow,
    onWebUrl: (Long, String) -> Unit,
    onMobileUrl: (Long, String) -> Unit,
    onActive: (Long, Boolean) -> Unit,
    onEdit: (TutorialVideoRow) -> Unit,
    onDelete: (TutorialVideoRow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.title.ifBlank { row.screenKey },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    Text(
                        text = row.screenKey,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
                Switch(checked = row.active, onCheckedChange = { onActive(row.id, it) })
            }
            AppTextField(
                value = row.webUrl,
                onValueChange = { onWebUrl(row.id, it) },
                label = "Web URL",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = row.mobileUrl,
                onValueChange = { onMobileUrl(row.id, it) },
                label = "Mobile URL",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LinkButton(text = "Edit", onClick = { onEdit(row) })
                LinkButton(text = "Delete", onClick = { onDelete(row) })
            }
        }
    }
}
