package com.example.cashbookbd.ui.purchaseadvance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.PurchaseAdvanceData
import com.example.cashbookbd.data.repository.PurchaseAdvanceLedger
import com.example.cashbookbd.data.repository.PurchaseAdvanceRepository
import com.example.cashbookbd.data.repository.PurchaseAdvanceStaff
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Advance for Purchase: money handed to staff to buy goods with.
 *
 * ⚠️ Not a loan. Nothing on this screen reaches the salary run.
 */
data class PurchaseAdvanceUiState(
    val data: PurchaseAdvanceData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val canEnter: Boolean = false,

    val showAll: Boolean = false,

    /** "give" or "settle". */
    val action: String = "give",
    val employeeId: Long? = null,
    val amount: String = "",
    /** Cash or bank — which pocket the money moved through. */
    val bank: Boolean = false,
    val bankAccount: Long? = null,
    val note: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,

    val ledgerTarget: PurchaseAdvanceStaff? = null,
    val ledger: PurchaseAdvanceLedger? = null,
    val ledgerFrom: SimpleDate? = null,
    val ledgerTo: SimpleDate? = null,
    val ledgerLoading: Boolean = false,
    val ledgerError: String? = null,
    val expandedBills: Set<Long> = emptySet(),
) {
    val staff: List<PurchaseAdvanceStaff> get() = data?.staff.orEmpty()

    val chosen: PurchaseAdvanceStaff? get() = staff.firstOrNull { it.id == employeeId }

    val held: Double get() = chosen?.balance ?: 0.0

    val isSettle: Boolean get() = action == "settle"

    /** Everyone holding a balance, unless "Show every employee" is on. */
    val listed: List<PurchaseAdvanceStaff>
        get() = if (showAll) staff else staff.filter { it.balance != 0.0 }

    val amountLabel: String
        get() = when {
            !isSettle -> "Amount (Tk.)"
            held < 0 -> "Pay him (Tk.)"
            else -> "Receive back (Tk.)"
        }

    val submitLabel: String
        get() = when {
            !isSettle -> "Advance Payment"
            held < 0 -> "Pay the difference"
            else -> "Receive back"
        }

    val settleSays: String
        get() {
            val one = chosen ?: return "Choose an employee who holds an advance, or is owed for a bill."
            return when {
                one.balance == 0.0 ->
                    "Choose an employee who holds an advance, or is owed for a bill."
                one.balance > 0 ->
                    "${one.name} holds ${moneyText(one.balance)}. What he hands back is received into " +
                        (if (bank) "the bank." else "cash.")
                else ->
                    "The bills ran ${moneyText(-one.balance)} past the advance. What is paid to " +
                        "${one.name} leaves " + (if (bank) "the bank." else "cash.")
            }
        }

    val daysOpen: (PurchaseAdvanceStaff) -> Long?
        get() = { one ->
            val from = (one.lastAdvance.ifBlank { one.lastMovement }).take(10)
            val to = data?.summary?.date.orEmpty().take(10)
            if (one.balance == 0.0 || from.isBlank() || to.isBlank()) {
                null
            } else {
                runCatching {
                    val f = SimpleDate.fromApi(from)?.let { isoMillis(it) }
                    val t = SimpleDate.fromApi(to)?.let { isoMillis(it) }
                    if (f == null || t == null) null
                    else ((t - f) / 86_400_000L).coerceAtLeast(0)
                }.getOrNull()
            }
        }

    companion object {
        private fun isoMillis(date: SimpleDate): Long = Calendar.getInstance().apply {
            clear()
            set(date.year, date.month - 1, date.day)
        }.timeInMillis
    }
}

class PurchaseAdvanceViewModel(
    private val repository: PurchaseAdvanceRepository,
    canEnter: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseAdvanceUiState(canEnter = canEnter))
    val uiState: StateFlow<PurchaseAdvanceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchList()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, error = null, data = result.data)
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

    fun onShowAll(value: Boolean) = _uiState.update { it.copy(showAll = value) }

    fun onAction(action: String) = _uiState.update { state ->
        val held = state.held
        state.copy(
            action = action,
            amount = if (action == "settle" && held != 0.0) plain(kotlin.math.abs(held)) else "",
        )
    }

    fun onEmployee(id: Long?) = _uiState.update { state ->
        val held = state.staff.firstOrNull { it.id == id }?.balance ?: 0.0
        state.copy(
            employeeId = id,
            // Settling starts at the whole of what stands between them; it may
            // be lowered. Giving starts empty.
            amount = if (state.isSettle && held != 0.0) plain(kotlin.math.abs(held)) else "",
        )
    }

    fun onAmount(value: String) = _uiState.update {
        it.copy(amount = value.filter { c -> c.isDigit() || c == '.' })
    }

    fun onBank(value: Boolean) = _uiState.update { it.copy(bank = value) }

    fun onBankAccount(id: Long?) = _uiState.update { it.copy(bankAccount = id) }

    fun onNote(value: String) = _uiState.update { it.copy(note = value.take(255)) }

    fun submit() {
        val state = _uiState.value
        if (state.isSaving) return
        val id = state.employeeId
        if (id == null) {
            _uiState.update { it.copy(error = "Choose the employee.") }
            return
        }
        val amount = state.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            _uiState.update { it.copy(error = "How much? Enter an amount.") }
            return
        }
        if (state.bank && state.bankAccount == null) {
            _uiState.update { it.copy(error = "Which bank account?") }
            return
        }
        if (state.isSettle) {
            val one = state.chosen
            if (one == null || one.balance == 0.0) {
                _uiState.update { it.copy(error = "He holds nothing and is owed nothing.") }
                return
            }
            if (amount > kotlin.math.abs(one.balance)) {
                _uiState.update {
                    it.copy(
                        error = if (one.balance > 0) {
                            "He holds ${moneyText(one.balance)}; no more than that can be taken back."
                        } else {
                            "He is owed ${moneyText(-one.balance)}; no more than that can be paid."
                        }
                    )
                }
                return
            }
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.submit(
                give = !state.isSettle,
                employeeId = id,
                amount = amount,
                bank = state.bank,
                bankAccount = state.bankAccount,
                note = state.note,
            )) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, message = result.data, amount = "", note = "")
                    }
                    load()
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

    // ---- The ledger -----------------------------------------------------

    fun openLedger(one: PurchaseAdvanceStaff) {
        val today = _uiState.value.data?.summary?.date.orEmpty().take(10)
            .let { SimpleDate.fromApi(it) } ?: SimpleDate.today()
        _uiState.update {
            it.copy(
                ledgerTarget = one,
                ledger = null,
                ledgerError = null,
                expandedBills = emptySet(),
                ledgerTo = today,
                // From = the first of that month, as the web opens it.
                ledgerFrom = today.copy(day = 1),
            )
        }
        loadLedger()
    }

    fun closeLedger() = _uiState.update {
        it.copy(ledgerTarget = null, ledger = null, ledgerError = null, expandedBills = emptySet())
    }

    fun onLedgerFrom(date: SimpleDate) = _uiState.update { it.copy(ledgerFrom = date) }
    fun onLedgerTo(date: SimpleDate) = _uiState.update { it.copy(ledgerTo = date) }

    fun toggleBill(rowId: Long) = _uiState.update { state ->
        state.copy(
            expandedBills = if (rowId in state.expandedBills) {
                state.expandedBills - rowId
            } else {
                state.expandedBills + rowId
            }
        )
    }

    fun loadLedger() {
        val state = _uiState.value
        val one = state.ledgerTarget ?: return
        if (state.ledgerLoading) return
        val from = state.ledgerFrom ?: return
        val to = state.ledgerTo ?: return
        _uiState.update { it.copy(ledgerLoading = true, ledgerError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchStatement(one.id, from.toApi(), to.toApi())) {
                is Resource.Success -> _uiState.update {
                    it.copy(ledgerLoading = false, ledger = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        ledgerLoading = false,
                        ledgerError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                PurchaseAdvanceViewModel(
                    repository = ServiceLocator.providePurchaseAdvanceRepository(app),
                    canEnter = com.example.cashbookbd.session.Permissions.has(
                        ServiceLocator.provideSessionManager(app).permissions,
                        "purchase.advance.create",
                    ),
                )
            }
        }
    }
}

/** The web's thousandSeparator, at the branch's decimal places. */
fun moneyText(value: Double): String = com.example.cashbookbd.core.AmountFormat.format(value)

/** "1,200.00 Dr" / "1,200.00 Cr", or "-" for nothing. */
fun balanceText(value: Double): String = when {
    value == 0.0 -> "-"
    value < 0 -> "${moneyText(-value)} Cr"
    else -> "${moneyText(value)} Dr"
}

/** "2026-11-03" → "03/11/2026"; a blank stays "-". */
fun dayText(value: String): String {
    val date = SimpleDate.fromApi(value) ?: return "-"
    return date.toDisplay()
}

val KIND_LABEL: Map<String, String> = mapOf(
    "advance" to "Advance given",
    "bill" to "Goods bought",
    "refund" to "Returned by him",
    "topup" to "Paid over the advance",
)
