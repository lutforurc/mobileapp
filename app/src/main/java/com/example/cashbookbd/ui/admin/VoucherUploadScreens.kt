package com.example.cashbookbd.ui.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.cashbookbd.core.UriParts
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.VoucherAttachmentsCell
import com.example.cashbookbd.ui.components.VoucherImageViewerDialog
import com.example.cashbookbd.ui.reports.BranchField
import com.example.cashbookbd.ui.reports.BranchPick
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.loadBranchPick
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
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

/** The web's ImageVoucherType and Attachment lists (DataConstant.ts). */
private val VOUCHER_TYPES = listOf(
    SelectorOption("", "Select Voucher Type"),
    SelectorOption("1", "Received Voucher"),
    SelectorOption("2", "Payment Voucher"),
    SelectorOption("5", "Journal Voucher"),
)
private val ATTACHMENT_TYPES = listOf(
    SelectorOption("", "Select Attachment Type"),
    SelectorOption("1", "With Image"),
    SelectorOption("2", "No Image"),
)

// ---- Voucher Upload ------------------------------------------------------------------

data class UploadVoucher(
    val id: Long,
    val serial: String,
    val vrNo: String,
    val vrDate: String,
    val details: String,
    val debit: Double,
    val credit: Double,
    val image: String,
    val branchPad: String,
)

data class VoucherUploadUiState(
    val canDelete: Boolean = false,
    val branch: BranchPick = BranchPick(),
    val voucherType: SelectorOption = VOUCHER_TYPES.first(),
    val attachmentType: SelectorOption = ATTACHMENT_TYPES.first(),
    val start: SimpleDate? = null,
    val end: SimpleDate? = null,
    val isLoading: Boolean = false,
    val rows: List<UploadVoucher>? = null,
    /** voucher id → files picked for it, not yet sent */
    val picked: Map<Long, List<Uri>> = emptyMap(),
    val uploadingId: Long? = null,
    val removing: Pair<Long, String>? = null,
    val askRemove: Pair<UploadVoucher, String>? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Upload Voucher Image (MultipleImageUpload.tsx): find the vouchers of a
 * kind and a range, then attach images, PDFs or sheets to each one; an
 * attachment already on a voucher can be taken off by whoever holds
 * voucher.photo.delete.
 */
class VoucherUploadViewModel(
    private val api: WebApi,
    private val reports: ReportRepository,
    private val appContext: Context,
    ownBranchId: Long?,
    canDelete: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoucherUploadUiState(canDelete = canDelete))
    val uiState: StateFlow<VoucherUploadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = reports.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update { it.copy(branch = r.data, start = r.data.trxDate, end = r.data.trxDate) }
                is Resource.Error -> _uiState.update { it.copy(branch = it.branch.copy(loading = false), sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onVoucherType(o: SelectorOption) = _uiState.update { it.copy(voucherType = o) }
    fun onAttachmentType(o: SelectorOption) = _uiState.update { it.copy(attachmentType = o) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(start = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(end = d) }

    fun pick(id: Long, uris: List<Uri>) = _uiState.update { it.copy(picked = it.picked + (id to (it.picked[id].orEmpty() + uris))) }
    fun clearPicked(id: Long) = _uiState.update { it.copy(picked = it.picked - id) }

    fun run() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.post("admin/voucher-list/for-image-upload", body(s))) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, rows = parse(r.data)) }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, rows = emptyList(), message = r.message, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun body(s: VoucherUploadUiState) = JsonObject().apply {
        addProperty("branch_id", s.branch.selectedId?.toString().orEmpty())
        addProperty("voucher_type", s.voucherType.id)
        addProperty("image_type", s.attachmentType.id)
        addProperty("start_date", s.start?.toApi().orEmpty())
        addProperty("end_date", s.end?.toApi().orEmpty())
    }

    private fun parse(o: JsonObject): List<UploadVoucher> = o.jRows().map { r ->
        UploadVoucher(
            id = r.jLong("id") ?: r.jLong("mtm_id") ?: 0L,
            serial = r.jStr("serial_no"), vrNo = r.jStr("vr_no"), vrDate = r.jStr("vr_date"),
            details = r.jStr("nam"), debit = r.jNum("debit"), credit = r.jNum("credit"),
            image = r.jStr("voucher_image"), branchPad = r.jStr("branchPad"),
        )
    }

    private suspend fun refresh() {
        (api.post("admin/voucher-list/for-image-upload", body(_uiState.value)) as? Resource.Success)?.let { r ->
            _uiState.update { it.copy(rows = parse(r.data)) }
        }
    }

    fun upload(id: Long) {
        val uris = _uiState.value.picked[id].orEmpty()
        if (uris.isEmpty()) {
            _uiState.update { it.copy(message = "Please select files first!") }
            return
        }
        _uiState.update { it.copy(uploadingId = id) }
        viewModelScope.launch {
            val parts = withContext(Dispatchers.IO) { uris.mapNotNull { UriParts.part(appContext, it, "images[]") } }
            when (val r = api.multipart("admin/voucher/upload/$id", mapOf("voucher_id" to UriParts.text(id.toString())), parts)) {
                is Resource.Success -> {
                    refresh()
                    _uiState.update { it.copy(uploadingId = null, picked = it.picked - id, message = "Files uploaded successfully!") }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(uploadingId = null, message = r.message.ifBlank { "Failed to upload files" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askRemove(row: UploadVoucher, image: String) = _uiState.update { it.copy(askRemove = row to image) }
    fun dismissRemove() = _uiState.update { it.copy(askRemove = null) }

    fun confirmRemove() {
        val (row, image) = _uiState.value.askRemove ?: return
        _uiState.update { it.copy(askRemove = null, removing = row.id to image) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("image_name", image)
                addProperty("branch_pad", row.branchPad)
            }
            when (val r = api.post("admin/voucher/image/delete/${row.id}", body)) {
                is Resource.Success -> {
                    refresh()
                    _uiState.update { it.copy(removing = null, message = "Uploaded attachment removed successfully!") }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(removing = null, message = r.message.ifBlank { "Failed to remove uploaded attachment." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val settings = ServiceLocator.provideSessionManager(app).state.value.settings
                VoucherUploadViewModel(
                    ServiceLocator.provideWebApi(app), ServiceLocator.provideReportRepository(app), app,
                    settings?.branchId, Permissions.has(settings?.permissions, "voucher.photo.delete"),
                )
            }
        }
    }
}

@Composable
fun VoucherUploadScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: VoucherUploadViewModel = viewModel(factory = VoucherUploadViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isLocal = remember { ServiceLocator.provideSessionManager(context).state.value.settings?.isLocalEnv == true }
    var pickingFor by remember { mutableStateOf<Long?>(null) }
    var viewing by remember { mutableStateOf<Pair<UploadVoucher, VoucherAttachment>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pickingFor?.let { id -> if (uris.isNotEmpty()) viewModel.pick(id, uris) }
        pickingFor = null
    }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = "Upload Voucher Image", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { BranchField(state.branch, viewModel::onBranch) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Voucher Type", VOUCHER_TYPES, state.voucherType, viewModel::onVoucherType, Modifier.weight(1f))
                        AppSelectDropdown("Attachment Type", ATTACHMENT_TYPES, state.attachmentType, viewModel::onAttachmentType, Modifier.weight(1f))
                    }
                }
                item { DateRangeFields(state.start, state.end, viewModel::onStart, viewModel::onEnd) }
                item { PrimaryButton(text = "Run", onClick = viewModel::run, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }
                val rows = state.rows
                when {
                    state.isLoading && rows == null -> item { ReportLoading() }
                    rows == null -> Unit
                    rows.isEmpty() -> item { ReportNote("No data found") }
                    else -> items(rows, key = { it.id }) { row ->
                        val picked = state.picked[row.id].orEmpty()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row.serial}. ${row.vrNo}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(kitMoney(if (row.debit > 0) row.debit else row.credit), fontWeight = AppFontWeight.SemiBold)
                                }
                                Text(SimpleDate.fromApi(row.vrDate.take(10))?.toDisplay() ?: row.vrDate, style = MaterialTheme.typography.labelSmall, color = muted)
                                Text(row.details, style = MaterialTheme.typography.labelSmall)
                                val attachments = VoucherImages.attachments(row.image, row.branchPad.ifBlank { null })
                                if (attachments.isEmpty()) {
                                    Text("No image uploaded yet", style = MaterialTheme.typography.labelSmall, color = muted)
                                } else {
                                    VoucherAttachmentsCell(attachments, isLocal) { viewing = row to it }
                                }
                                if (picked.isNotEmpty()) {
                                    Text(
                                        "Selected ${picked.size} file${if (picked.size > 1) "s" else ""}: " +
                                            picked.joinToString(", ") { UriParts.displayName(context, it) },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SecondaryButton(
                                        text = "Choose files", compact = true,
                                        onClick = { pickingFor = row.id; picker.launch("*/*") },
                                    )
                                    PrimaryButton(
                                        text = if (state.uploadingId == row.id) "Uploading..." else if (row.image.isNotBlank()) "Add More" else "Upload",
                                        compact = true, isLoading = state.uploadingId == row.id,
                                        enabled = state.uploadingId == null,
                                        onClick = { viewModel.upload(row.id) },
                                    )
                                    if (picked.isNotEmpty()) LinkButton(text = "Cancel", onClick = { viewModel.clearPicked(row.id) })
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    viewing?.let { (row, attachment) ->
        if (state.canDelete) {
            AlertDialog(
                onDismissRequest = { viewing = null },
                title = { Text(row.vrNo) },
                text = { Text("Open this attachment, or take it off the voucher?") },
                confirmButton = {
                    LinkButton(text = "Remove", color = MaterialTheme.colorScheme.error, onClick = {
                        viewing = null
                        viewModel.askRemove(row, attachment.fileName)
                    })
                },
                dismissButton = {
                    LinkButton(text = "Open", onClick = {
                        viewing = null
                        com.example.cashbookbd.ui.components.openVoucherAttachment(context, attachment, isLocal)
                    })
                },
            )
        } else {
            VoucherImageViewerDialog(attachment, isLocal) { viewing = null }
        }
    }
    state.askRemove?.let { (_, image) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemove,
            title = { Text("Remove attachment?") },
            text = { Text("Remove $image from this voucher?") },
            confirmButton = { LinkButton(text = "Remove", onClick = viewModel::confirmRemove, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissRemove) },
        )
    }
}

// ---- Bulk Upload ---------------------------------------------------------------------

data class BulkUploadUiState(
    val branch: BranchPick = BranchPick(),
    val voucherType: SelectorOption = VOUCHER_TYPES.first(),
    val attachmentType: SelectorOption = ATTACHMENT_TYPES.first(),
    val start: SimpleDate = SimpleDate.today(),
    val end: SimpleDate = SimpleDate.today(),
    val files: List<Uri> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/** Bulk Voucher Upload (BulkImageUpload.tsx): many files at once, matched by the server. */
class BulkUploadViewModel(
    private val api: WebApi,
    private val reports: ReportRepository,
    private val appContext: Context,
    ownBranchId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BulkUploadUiState())
    val uiState: StateFlow<BulkUploadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = reports.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(branch = r.data, start = r.data.trxDate ?: it.start, end = r.data.trxDate ?: it.end)
                }
                is Resource.Error -> _uiState.update { it.copy(branch = it.branch.copy(loading = false), sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onVoucherType(o: SelectorOption) = _uiState.update { it.copy(voucherType = o) }
    fun onAttachmentType(o: SelectorOption) = _uiState.update { it.copy(attachmentType = o) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(start = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(end = d) }
    fun remove(index: Int) = _uiState.update { it.copy(files = it.files.filterIndexed { i, _ -> i != index }) }

    /** Files over 5 MB are left out, and said so. */
    fun onFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val kept = withContext(Dispatchers.IO) {
                uris.filter { uri ->
                    val size = runCatching {
                        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    }.getOrNull() ?: 0L
                    size <= 5L * 1024 * 1024
                }
            }
            _uiState.update {
                it.copy(files = kept, message = if (kept.size != uris.size) "Some files are too large and were not added." else null)
            }
        }
    }

    fun upload() {
        val s = _uiState.value
        val error = when {
            s.files.isEmpty() -> "Please select files first!"
            s.branch.selectedId == null -> "Please select branch!"
            s.voucherType.id.isBlank() -> "Please select Voucher Type!"
            s.attachmentType.id.isBlank() -> "Please select Attachment Type!"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val parts = withContext(Dispatchers.IO) { s.files.mapNotNull { UriParts.part(appContext, it, "images[]") } }
            val fields = mapOf(
                "branch_id" to UriParts.text(s.branch.selectedId.toString()),
                "voucher_type" to UriParts.text(s.voucherType.id),
                "image_type" to UriParts.text(s.attachmentType.id),
                "start_date" to UriParts.text(s.start.toApi()),
                "end_date" to UriParts.text(s.end.toApi()),
            )
            when (val r = api.multipart("admin/bulk-image/upload", fields, parts)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, files = emptyList(), message = r.data.jStr("message").ifBlank { "Uploaded." })
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Failed to upload data" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                BulkUploadViewModel(
                    ServiceLocator.provideWebApi(app), ServiceLocator.provideReportRepository(app), app,
                    ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

@Composable
fun BulkUploadScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: BulkUploadViewModel = viewModel(factory = BulkUploadViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { viewModel.onFiles(it) }
    var askRemove by remember { mutableStateOf<Int?>(null) }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = "Bulk Voucher Upload", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { BranchField(state.branch, viewModel::onBranch) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Voucher Type", VOUCHER_TYPES, state.voucherType, viewModel::onVoucherType, Modifier.weight(1f))
                        AppSelectDropdown("Attachment Type", ATTACHMENT_TYPES, state.attachmentType, viewModel::onAttachmentType, Modifier.weight(1f))
                    }
                }
                item { DateRangeFields(state.start, state.end, viewModel::onStart, viewModel::onEnd) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton(text = "Choose Voucher", onClick = { picker.launch("image/*") })
                        if (state.files.isNotEmpty()) {
                            Text("${state.files.size} file(s) selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreen)
                        }
                    }
                }
                item { PrimaryButton(text = "Update", onClick = viewModel::upload, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }
                itemsIndexed(state.files) { i, uri ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(UriParts.displayName(context, uri), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            LinkButton(text = "Remove", onClick = { askRemove = i }, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    askRemove?.let { index ->
        AlertDialog(
            onDismissRequest = { askRemove = null },
            title = { Text("Remove file?") },
            text = { Text("Are you sure you want to remove this file?") },
            confirmButton = { LinkButton(text = "Remove", onClick = { viewModel.remove(index); askRemove = null }) },
            dismissButton = { LinkButton(text = "Cancel", onClick = { askRemove = null }) },
        )
    }
}
