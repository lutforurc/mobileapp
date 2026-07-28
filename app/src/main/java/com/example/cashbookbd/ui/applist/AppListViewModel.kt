package com.example.cashbookbd.ui.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.applist.AppListColumn
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AppListRepository
import com.example.cashbookbd.data.repository.AppListRow
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.session.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads and holds a read-only list screen (resolved from [listKey]). */
class AppListViewModel(
    listKey: String,
    private val repository: AppListRepository,
    private val productRepository: ProductRepository,
    private val settings: Settings?,
) : ViewModel() {

    /**
     * True when this list shows the per-row opening stock entry: the spec
     * declares it and the branch is still "Opening ongoing" — the web's
     * `branch.is_opening == 1` gate for the Product List's inline columns.
     */
    private val openingEnabled =
        AppLists.byKey(listKey)?.openingStock == true && settings?.openingOngoing == true

    /**
     * The spec as this branch sees it. With the opening entry active the columns
     * mirror the web's opening layout: Category is hidden (its inline inputs
     * take the room) and the current Opening qty shows after Unit.
     */
    private val spec = AppLists.byKey(listKey)?.let { base ->
        if (!openingEnabled) base
        else base.copy(
            columns = buildList {
                base.columns.forEach { col ->
                    if (col.key == "category") return@forEach
                    add(col)
                    if (col.key == "unit") add(AppListColumn("openingbalance", "Opening", numeric = true))
                }
            },
        )
    }

    private val _uiState = MutableStateFlow(
        AppListUiState(
            title = spec?.title ?: "List",
            isSupported = spec != null,
            columns = spec?.columns.orEmpty(),
            isPaginated = spec?.paginated == true,
            perPage = spec?.perPage ?: 25,
            hasStatusToggle = spec?.statusToggle != null,
            addAction = spec?.addAction,
            editAction = spec?.editAction,
            deleteAction = spec?.deleteAction,
            openingEnabled = openingEnabled,
        )
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        if (spec != null) load(1)
    }

    /**
     * Loads [page]. A [silent] load skips the spinner so the table stays on
     * screen — used to reconcile rows after a status toggle.
     */
    fun load(page: Int = _uiState.value.currentPage, silent: Boolean = false) {
        val currentSpec = spec ?: return
        if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetch(currentSpec, page, _uiState.value.perPage)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                        error = null,
                    )
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

    /** Changes the page size and reloads from page 1, as the web selector does. */
    fun onPerPageChange(perPage: Int) {
        if (perPage == _uiState.value.perPage) return
        _uiState.update { it.copy(perPage = perPage) }
        load(page = 1)
    }

    fun nextPage() {
        val s = _uiState.value
        if (s.canNext) load(s.currentPage + 1)
    }

    fun prevPage() {
        val s = _uiState.value
        if (s.canPrev) load(s.currentPage - 1)
    }

    /**
     * Flips [row]'s status. The switch moves immediately; on success the page is
     * silently reloaded so the rows settle on the server's own state, and on
     * failure the switch snaps back and the reason is surfaced.
     */
    fun onToggleStatus(row: AppListRow, on: Boolean) {
        val currentSpec = spec ?: return
        val id = row.id ?: return
        if (id in _uiState.value.togglingIds) return

        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.id == id) it.copy(statusOn = on) else it },
                togglingIds = state.togglingIds + id,
            )
        }
        viewModelScope.launch {
            when (val result = repository.setStatus(currentSpec, id, on)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(togglingIds = it.togglingIds - id) }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        rows = state.rows.map { if (it.id == id) it.copy(statusOn = !on) else it },
                        togglingIds = state.togglingIds - id,
                        actionMessage = result.message,
                        sessionExpired = state.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Row delete (Real Estate master lists) ----

    /** Opens the confirm dialog for [row]. */
    fun requestDelete(row: AppListRow) {
        if (row.deleteId == null) return
        _uiState.update { it.copy(pendingDelete = row) }
    }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    /**
     * Deletes the pending row. The server may refuse (dependent rows) — its
     * message is shown and the list is left untouched.
     */
    fun confirmDelete() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        val id = state.pendingDelete?.deleteId ?: return
        if (state.isDeleting) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.delete(currentSpec, id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isDeleting = false, pendingDelete = null, actionMessage = "Deleted.")
                    }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Opening stock entry (Product List, "Opening ongoing" branches) ----

    /** Opens the dialog pre-filled the way the web's inline inputs are. */
    fun startOpeningEdit(row: AppListRow) {
        val opening = row.opening ?: return
        _uiState.update {
            it.copy(
                openingEdit = row,
                openingSerial = "",
                // Zero means "not set yet" — leave the field empty, like the web
                // rendering 0 as an empty input.
                openingQty = opening.qty.takeIf { q -> (q.toDoubleOrNull() ?: 0.0) != 0.0 }.orEmpty(),
                openingRate = opening.rate.takeIf { r -> (r.toDoubleOrNull() ?: 0.0) != 0.0 }.orEmpty(),
            )
        }
    }

    /**
     * IMEI/serial lines drive the qty (the web's serial-blur recount): one unit
     * per non-blank line, comma also accepted as a separator like the server.
     */
    fun onOpeningSerial(value: String) = _uiState.update {
        val count = serialLines(value).size
        it.copy(
            openingSerial = value,
            openingQty = if (count > 0) count.toString() else it.openingQty,
        )
    }

    fun onOpeningQty(value: String) = _uiState.update { it.copy(openingQty = value) }
    fun onOpeningRate(value: String) = _uiState.update { it.copy(openingRate = value) }
    fun cancelOpeningEdit() = _uiState.update { it.copy(openingEdit = null, openingSaving = false) }

    /**
     * Posts the opening stock — a REAL opening purchase voucher, the same
     * `product/update-qty-rate` call as the web's inline Save.
     */
    fun saveOpeningStock() {
        val state = _uiState.value
        val opening = state.openingEdit?.opening ?: return
        if (state.openingSaving) return

        val branchId = settings?.branchId
        if (branchId == null) {
            _uiState.update { it.copy(actionMessage = "Branch not resolved — please re-login and try again.") }
            return
        }
        val serials = serialLines(state.openingSerial)
        // With serials the server counts them; otherwise qty must be entered.
        val qty = if (serials.isNotEmpty()) serials.size else state.openingQty.trim().toDoubleOrNull()?.toInt() ?: 0
        if (qty <= 0) {
            _uiState.update { it.copy(actionMessage = "Enter a quantity (or IMEI/serial numbers) first.") }
            return
        }
        // The web falls back to the row's purchase price when rate is untouched.
        val rate = state.openingRate.trim().ifEmpty { opening.rate }.ifEmpty { "0" }

        _uiState.update { it.copy(openingSaving = true) }
        viewModelScope.launch {
            val result = productRepository.updateOpeningStock(
                productId = opening.productId,
                branchId = branchId.toString(),
                qty = qty.toString(),
                rate = rate,
                serialNo = serials.joinToString("\n"),
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(openingSaving = false, openingEdit = null, actionMessage = result.data)
                    }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        openingSaving = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Non-blank IMEI/serial entries — newline or comma separated, like the server splits. */
    private fun serialLines(value: String): List<String> =
        value.split('\n', '\r', ',').map(String::trim).filter(String::isNotEmpty)

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, listKey: String) = viewModelFactory {
            initializer {
                AppListViewModel(
                    listKey = listKey,
                    repository = ServiceLocator.provideAppListRepository(context.applicationContext),
                    productRepository = ServiceLocator.provideProductRepository(context.applicationContext),
                    settings = ServiceLocator.provideSessionManager(context.applicationContext)
                        .state.value.settings,
                )
            }
        }
    }
}
