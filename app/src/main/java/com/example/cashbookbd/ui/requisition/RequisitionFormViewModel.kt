package com.example.cashbookbd.ui.requisition

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.RequisitionItemOption
import com.example.cashbookbd.data.repository.RequisitionLine
import com.example.cashbookbd.data.repository.RequisitionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Drives the Requisition create form (the web's `RequisitionForm`): notes and a
 * start/end date range, then a running list of item lines (product/expense
 * head/labour × day × qty × price) submitted as one requisition voucher via
 * [RequisitionRepository].
 */
class RequisitionFormViewModel(
    private val requisitionRepository: RequisitionRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    /** Last item search results, so a picked option maps back to its type/unit/price. */
    private var itemCache: Map<String, RequisitionItemOption> = emptyMap()

    private val _uiState = MutableStateFlow(RequisitionFormUiState())
    val uiState: StateFlow<RequisitionFormUiState> = _uiState.asStateFlow()

    init {
        loadTransactionDate()
    }

    /**
     * Defaults both dates to the branch's transaction (business) date, like the
     * other entry screens; a failed load leaves today's date in place.
     */
    private fun loadTransactionDate() {
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> result.data.transactionDate?.let { date ->
                    _uiState.update { it.copy(startDate = date, endDate = date) }
                }
                is Resource.Error -> if (result.isUnauthorized) {
                    _uiState.update { it.copy(sessionExpired = true) }
                }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Item search for the picker — products, expense heads and labour items in
     * one list. The sublabel names the kind so lookalikes stay tellable apart.
     */
    suspend fun searchItems(query: String): Resource<List<SelectorOption>> =
        when (val result = requisitionRepository.searchItems(query)) {
            is Resource.Success -> {
                itemCache = result.data.associateBy { it.id }
                Resource.Success(
                    result.data.map { item ->
                        SelectorOption(
                            id = item.id,
                            label = item.name,
                            sublabel = listOf(item.typeName(), item.unit)
                                .filter { it.isNotBlank() }
                                .joinToString("  •  ")
                                .ifBlank { null },
                        )
                    }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** A picked item pre-fills the price from its purchase price (web `label_4`). */
    fun onItemSelected(option: SelectorOption) {
        val item = itemCache[option.id] ?: return
        _uiState.update {
            it.copy(
                selectedItem = item,
                price = item.purchasePrice?.toPlainAmount() ?: it.price,
            )
        }
    }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onStartDateChange(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDateChange(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun onRemarksChange(value: String) = _uiState.update { it.copy(remarks = value) }
    fun onDayChange(value: String) = _uiState.update { it.copy(day = value.decimalOnly()) }
    fun onQtyChange(value: String) = _uiState.update { it.copy(qty = value.decimalOnly()) }
    fun onPriceChange(value: String) = _uiState.update { it.copy(price = value.decimalOnly()) }

    /** Adds the entry as a pending line and clears the entry fields. */
    fun addLine() {
        val state = _uiState.value
        val item = state.selectedItem ?: return
        if (!state.canAdd) return
        _uiState.update {
            it.copy(
                lines = it.lines + RequisitionLine(
                    item = item,
                    remarks = it.remarks.trim(),
                    day = it.day.trim(),
                    qty = it.qty.trim(),
                    price = it.price.trim(),
                ),
                selectedItem = null,
                remarks = "",
                day = "",
                qty = "",
                price = "",
            )
        }
    }

    /** Loads a pending line back into the entry (and removes it from the batch). */
    fun editLine(index: Int) {
        _uiState.update { state ->
            val line = state.lines.getOrNull(index) ?: return@update state
            // The picked line's item must map back on re-add.
            itemCache = itemCache + (line.item.id to line.item)
            state.copy(
                selectedItem = line.item,
                remarks = line.remarks,
                day = line.day,
                qty = line.qty,
                price = line.price,
                lines = state.lines.filterIndexed { i, _ -> i != index },
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSave) return
        // The web's guard: a zero/blank requisition amount never posts.
        if (state.total <= 0.0) {
            _uiState.update { it.copy(message = "Please add requisition amount!", isError = true) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = requisitionRepository.submit(
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
                notes = state.notes.trim(),
                // The web sends total.toFixed(0) — a plain 0-decimal string.
                requisitionAmt = state.total.roundToLong().toString(),
                lines = state.lines,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    // Like the web reset: batch and notes clear; the dates stay.
                    it.copy(
                        isSubmitting = false,
                        message = result.data,
                        isError = false,
                        lines = emptyList(),
                        notes = "",
                        selectedItem = null,
                        remarks = "",
                        day = "",
                        qty = "",
                        price = "",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    /** "1200.0" → "1200", "1200.5" stays — for pre-filling the price field. */
    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    /** What kind of pickable this is, from the option's `label_2`. */
    private fun RequisitionItemOption.typeName(): String = when (type) {
        "1" -> "Product"
        "2" -> "Expense Head"
        "3" -> "Labour"
        else -> ""
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                RequisitionFormViewModel(
                    requisitionRepository = ServiceLocator.provideRequisitionRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}
