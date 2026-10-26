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
import com.example.cashbookbd.ui.reports.TableSortChoice
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
    private val changeLogRepository: com.example.cashbookbd.data.repository.ChangeLogRepository,
    private val settings: Settings?,
    /** voucher.delete — what the opening-stock Delete answers to, like the API. */
    private val canDeleteVoucher: Boolean = false,
    /** The session's permissions, for the per-action gates a spec may declare. */
    private val permissions: List<com.example.cashbookbd.session.Permission>? = null,
    /** Fills the spec's selector filters (brand / category / group …). */
    private val selectorRepository: com.example.cashbookbd.data.repository.SelectorRepository? = null,
) : ViewModel() {

    /** An action with an empty anyOf is ungated; otherwise any one suffices. */
    private fun allowed(anyOf: List<String>): Boolean =
        anyOf.isEmpty() || com.example.cashbookbd.session.Permissions.hasAny(permissions, anyOf)

    /**
     * True when this list shows the per-row opening stock entry: the spec
     * declares it and the branch is still "Opening ongoing" — the web's
     * `branch.is_opening == 1` gate for the Product List's inline columns.
     */
    /**
     * Product List with the branch's "Editable Measurement on Product List?"
     * (web f6f7ff4f): Measurement and Package Size stand right after Unit and
     * the rest of the row stands down — prices, the Action column and the
     * opening entry. Each row's measurement opens in a dialog here.
     */
    private val measureEnabled =
        AppLists.byKey(listKey)?.openingStock == true && settings?.productListInlineEdit == true

    private val openingEnabled =
        AppLists.byKey(listKey)?.openingStock == true && settings?.openingOngoing == true && !measureEnabled

    /**
     * The spec as this branch sees it. With the opening entry active the columns
     * mirror the web's opening layout: Category is hidden (its inline inputs
     * take the room) and the current Opening qty shows after Unit.
     */
    private val spec = AppLists.byKey(listKey)?.let { base ->
        if (measureEnabled) {
            return@let base.copy(
                columns = buildList {
                    base.columns.forEach { col ->
                        if (col.key == "purchase" || col.key == "sales") return@forEach
                        add(col)
                        if (col.key == "unit") {
                            add(AppListColumn("measurement", "Measurement"))
                            add(AppListColumn("package_size", "Package Size"))
                        }
                    }
                },
                editAction = null,
                deleteAction = null,
                deepDeleteAction = null,
                historyAction = null,
            )
        }
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
            // Per-action gates, exactly the web's: a list a user can only view
            // shows neither New nor the pencil, bin or switch (labour lists
            // gate them on .edit/.delete while the list itself needs .view).
            hasStatusToggle = spec?.statusToggle?.takeIf { allowed(it.anyOf) } != null,
            addAction = spec?.addAction?.takeIf { allowed(it.anyOf) },
            editAction = spec?.editAction?.takeIf { allowed(it.anyOf) },
            deleteAction = spec?.deleteAction?.takeIf { allowed(it.anyOf) },
            historyAction = spec?.historyAction?.takeIf { allowed(it.anyOf) },
            deepDeleteAction = spec?.deepDeleteAction?.takeIf { deep ->
                allowed(deep.action.anyOf) && when (deep.gate) {
                    com.example.cashbookbd.applist.DeepDeleteGate.DELETE_WITH_OPENING_INVOICE ->
                        settings?.deleteWithOpeningInvoice == true
                }
            },
            deepDeleteCutoff = settings?.deleteWithOpeningInvoiceDate.orEmpty(),
            measureEnabled = measureEnabled,
            openingEnabled = openingEnabled,
            canDeleteVoucher = canDeleteVoucher,
            filterKey = spec?.filterKey,
            filterLabel = spec?.filterLabel.orEmpty(),
            filterOptions = spec?.filterOptions.orEmpty(),
            searchable = spec?.searchable == true,
            selectorFilters = spec?.selectorFilters.orEmpty(),
            selectorOptions = spec?.selectorFilters.orEmpty().associate {
                it.paramKey to listOf(com.example.cashbookbd.ui.reports.model.SelectorOption("", it.allLabel))
            },
        )
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        if (spec != null) load(1)
        spec?.selectorFilters.orEmpty().forEach { filter ->
            viewModelScope.launch {
                val r = selectorRepository?.fetch(filter.source) as? Resource.Success ?: return@launch
                _uiState.update {
                    it.copy(
                        selectorOptions = it.selectorOptions + (
                            filter.paramKey to listOf(com.example.cashbookbd.ui.reports.model.SelectorOption("", filter.allLabel)) + r.data
                            ),
                    )
                }
            }
        }
        if (measureEnabled) {
            viewModelScope.launch {
                // The same authorized unit list the product form's Unit uses.
                (productRepository.loadProductFormOptions() as? Resource.Success)?.let { r ->
                    _uiState.update { it.copy(units = r.data.units, rows = withUnitNames(it.rows, r.data.units)) }
                }
            }
        }
    }

    /** "0.667 kg": the measurement cells read with their unit's name. */
    private fun withUnitNames(
        rows: List<AppListRow>,
        units: List<com.example.cashbookbd.ui.reports.model.SelectorOption> = _uiState.value.units,
    ): List<AppListRow> {
        if (!measureEnabled) return rows
        val cols = spec?.columns.orEmpty()
        val mi = cols.indexOfFirst { it.key == "measurement" }
        val pi = cols.indexOfFirst { it.key == "package_size" }
        fun unit(id: String) = units.firstOrNull { it.id == id }?.label.orEmpty()
        fun text(value: String, unitId: String) =
            if (value.isBlank()) "-" else listOf(value, unit(unitId)).filter { it.isNotBlank() }.joinToString(" ")
        return rows.map { row ->
            val o = row.opening ?: return@map row
            row.copy(
                cells = row.cells.mapIndexed { i, c ->
                    when (i) {
                        mi -> text(o.measurement, o.measurementUnitId)
                        pi -> text(o.packageSize, o.packageUnitId)
                        else -> c
                    }
                },
            )
        }
    }

    fun startMeasureEdit(row: AppListRow) {
        val o = row.opening ?: return
        _uiState.update {
            it.copy(
                measureEdit = row,
                measureFields = com.example.cashbookbd.ui.products.MeasurementFields(
                    measurement = o.measurement,
                    measurementUnitId = o.measurementUnitId.takeIf { u -> u != "0" }.orEmpty(),
                    packageSize = o.packageSize,
                    packageUnitId = o.packageUnitId.takeIf { u -> u != "0" }.orEmpty(),
                ),
            )
        }
    }

    fun onMeasureFields(f: com.example.cashbookbd.ui.products.MeasurementFields) =
        _uiState.update { it.copy(measureFields = f) }

    fun cancelMeasureEdit() = _uiState.update { it.copy(measureEdit = null) }

    /** Validated against the same rules the product forms keep, then saved. */
    fun saveMeasure() {
        val state = _uiState.value
        val o = state.measureEdit?.opening ?: return
        val f = state.measureFields
        f.error?.let { msg ->
            _uiState.update { it.copy(actionMessage = msg) }
            return
        }
        _uiState.update { it.copy(isSavingMeasure = true) }
        viewModelScope.launch {
            val result = productRepository.saveInlineMeasurement(
                productId = o.productId,
                measurement = f.measurementForApi(),
                measurementUnitId = f.measurementUnitId,
                packageSize = f.packageSizeForApi(),
                packageUnitId = f.packageUnitId,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSavingMeasure = false, measureEdit = null, actionMessage = result.data) }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSavingMeasure = false, actionMessage = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Refreshes the current page without the spinner — the on-resume reload. */
    fun reloadCurrent() = load(page = _uiState.value.currentPage, silent = true)

    /**
     * Loads [page]. A [silent] load skips the spinner so the table stays on
     * screen — used to reconcile rows after a status toggle.
     */
    fun load(page: Int = _uiState.value.currentPage, silent: Boolean = false) {
        val baseSpec = spec ?: return
        // The one extra toolbar filter (Login Log's Result), when the spec
        // declares one — merged in like search would be, had this engine one.
        val filtered = baseSpec.filterKey?.let { key ->
            val value = _uiState.value.filterValue
            if (value.isEmpty()) baseSpec else baseSpec.copy(params = baseSpec.params + (key to value))
        } ?: baseSpec
        // The search and the selector filters ride as params too; a blank
        // selector is sent empty, which the server reads as "all".
        val currentSpec = filtered.copy(
            params = filtered.params +
                (if (filtered.searchable) mapOf("search" to _uiState.value.appliedSearch) else emptyMap()) +
                filtered.selectorFilters.associate { it.paramKey to (_uiState.value.selectorValues[it.paramKey] ?: "") } +
                sortParams(filtered),
        )
        // With rows already on screen this is a page turn: the table stays put
        // under a thin progress line instead of vanishing behind the spinner.
        // (A retry after an error goes back through the full spinner — there
        // is no table to keep.)
        val pageTurn = !silent && _uiState.value.rows.isNotEmpty() && _uiState.value.error == null
        if (!silent) {
            _uiState.update {
                if (pageTurn) it.copy(isPageLoading = true)
                else it.copy(isLoading = true, error = null)
            }
        }
        viewModelScope.launch {
            when (val result = repository.fetch(currentSpec, page, _uiState.value.perPage)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPageLoading = false,
                        rows = withUnitNames(result.data.rows),
                        summary = result.data.summary,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                        error = null,
                    )
                }
                is Resource.Error -> _uiState.update {
                    if (pageTurn) {
                        // The page the user was reading is still valid — keep
                        // it and say why the next one didn't come, rather than
                        // swapping the table for a full-screen error.
                        it.copy(
                            isPageLoading = false,
                            actionMessage = result.message,
                            sessionExpired = it.sessionExpired || result.isUnauthorized,
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                            sessionExpired = it.sessionExpired || result.isUnauthorized,
                        )
                    }
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

    /** Changes the toolbar's extra filter (the Login Log's Result) and reloads from page 1. */
    fun onSearchText(value: String) = _uiState.update { it.copy(searchText = value) }

    /**
     * A sortable header's choice: the column's key and direction, or null to
     * clear it back to the list's own order. Runs the report again from page one
     * — the order decides which rows a page holds, so page five of the old order
     * is not page five of the new one (web e07fd750).
     */
    fun onSort(choice: TableSortChoice?) {
        _uiState.update {
            it.copy(
                sortKey = choice?.key.orEmpty(),
                sortDir = when {
                    choice == null -> ""
                    choice.ascending -> "asc"
                    else -> "desc"
                },
            )
        }
        load(page = 1)
    }

    /** The chosen sort, as the params the list's endpoint reads. */
    private fun sortParams(spec: com.example.cashbookbd.applist.AppListSpec): Map<String, String> {
        val param = spec.sortParam ?: return emptyMap()
        val key = _uiState.value.sortKey
        if (key.isBlank()) return emptyMap()
        val dir = if (_uiState.value.sortDir == "desc") "desc" else "asc"
        return mapOf(param to key, (spec.dirParam ?: param) to dir)
    }

    /** Applied on the search key only — typing alone does not reload (web 681144ed). */
    fun applySearch() {
        _uiState.update { it.copy(appliedSearch = it.searchText.trim()) }
        load(page = 1)
    }

    fun onSelectorFilter(paramKey: String, id: String) {
        if (_uiState.value.selectorValues[paramKey].orEmpty() == id) return
        _uiState.update { it.copy(selectorValues = it.selectorValues + (paramKey to id)) }
        load(page = 1)
    }

    fun onFilterChange(value: String) {
        if (value == _uiState.value.filterValue) return
        _uiState.update { it.copy(filterValue = value) }
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

    // ---- The deeper delete (Product List: product + its opening invoice) ----

    fun requestDeepDelete(row: AppListRow) {
        if (row.deleteId == null) return
        _uiState.update { it.copy(pendingDeepDelete = row) }
    }

    fun cancelDeepDelete() = _uiState.update { it.copy(pendingDeepDelete = null) }

    fun confirmDeepDelete() {
        val currentSpec = spec ?: return
        val deep = _uiState.value.deepDeleteAction ?: return
        val id = _uiState.value.pendingDeepDelete?.deleteId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.delete(currentSpec.copy(deleteAction = deep.action), id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isDeleting = false, pendingDeepDelete = null, actionMessage = "Product deleted successfully.")
                    }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingDeepDelete = null,
                        actionMessage = result.message.ifBlank { "Product and its opening invoice could not be deleted." },
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

    // ---- Opening stock delete (the voucher goes to the trash) ----

    fun requestOpeningDelete(row: AppListRow) {
        if (row.opening?.vrNo.isNullOrBlank()) return
        _uiState.update { it.copy(openingDeletePending = row) }
    }

    fun cancelOpeningDelete() = _uiState.update { it.copy(openingDeletePending = null) }

    /**
     * Deletes the pending row's opening stock and its voucher. The server may
     * refuse — approved voucher, another branch, stock already drawn on — with
     * its reason.
     */
    fun confirmOpeningDelete() {
        val state = _uiState.value
        val opening = state.openingDeletePending?.opening ?: return
        if (state.openingDeleting) return

        _uiState.update { it.copy(openingDeleting = true) }
        viewModelScope.launch {
            when (val result = productRepository.deleteProductOpening(opening.productId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            openingDeleting = false,
                            openingDeletePending = null,
                            // A dialog draft would read as though the stock
                            // survived, so any open entry closes with it.
                            openingEdit = null,
                            actionMessage = result.data,
                        )
                    }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        openingDeleting = false,
                        openingDeletePending = null,
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

    fun showHistory(row: AppListRow) {
        val action = _uiState.value.historyAction ?: return
        val id = row.historyId ?: return
        _uiState.update { it.copy(historyRow = row, isHistoryLoading = true, historyError = null, history = null) }
        viewModelScope.launch {
            when (
                val result = changeLogRepository.fetchHistory(
                    path = "${action.endpointBase}/$id",
                    subjectKey = action.subjectKey,
                    subtitleKey = action.subtitleKey,
                )
            ) {
                is Resource.Success -> _uiState.update { it.copy(isHistoryLoading = false, history = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isHistoryLoading = false,
                        historyError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun dismissHistory() = _uiState.update { it.copy(historyRow = null, history = null, historyError = null) }

    companion object {
        fun provideFactory(context: Context, listKey: String) = viewModelFactory {
            initializer {
                val sessionState = ServiceLocator
                    .provideSessionManager(context.applicationContext).state.value
                AppListViewModel(
                    listKey = listKey,
                    repository = ServiceLocator.provideAppListRepository(context.applicationContext),
                    productRepository = ServiceLocator.provideProductRepository(context.applicationContext),
                    changeLogRepository = ServiceLocator.provideChangeLogRepository(context.applicationContext),
                    settings = sessionState.settings,
                    canDeleteVoucher = com.example.cashbookbd.session.Permissions
                        .hasAny(sessionState.permissions, listOf("voucher.delete")),
                    permissions = sessionState.permissions,
                    selectorRepository = ServiceLocator.provideSelectorRepository(context.applicationContext),
                )
            }
        }
    }
}
