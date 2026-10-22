package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.ui.accounts.pickAccountsDate
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.appColors

/*
 * The pieces every report and form of the 2026-10 parity pass starts with:
 * the branch the user is in, the branch's business (transaction) date the
 * web opens its date boxes on, and the small effects each screen repeats.
 */

/** The branch box's state: what can be picked, what is, and the business date. */
data class BranchPick(
    val loading: Boolean = true,
    val branches: List<BranchOption> = emptyList(),
    val selected: BranchOption? = null,
    /** The branch's transaction date — where the web's date boxes open. */
    val trxDate: SimpleDate? = null,
) {
    val selectedId: Long? get() = selected?.id

    fun pick(id: String): BranchPick =
        copy(selected = branches.firstOrNull { it.id.toString() == id } ?: selected)
}

/**
 * Loads the branch list with the user's own branch selected — the web's
 * `setBranchId(user.branch_id)` — and the business date beside it.
 */
suspend fun ReportRepository.loadBranchPick(ownBranchId: Long?): Resource<BranchPick> =
    when (val result = getBranches()) {
        is Resource.Success -> Resource.Success(
            BranchPick(
                loading = false,
                branches = result.data.branches,
                selected = result.data.branches.firstOrNull { it.id == ownBranchId }
                    ?: result.data.branches.firstOrNull(),
                trxDate = result.data.transactionDate,
            )
        )
        is Resource.Error -> result
        Resource.Loading -> Resource.Loading
    }

/** Bounces to login once when the session lapses. */
@Composable
fun LogoutOnExpiry(expired: Boolean, onHandled: () -> Unit, onLogout: () -> Unit) {
    LaunchedEffect(expired) {
        if (expired) {
            onHandled()
            onLogout()
        }
    }
}

/** Shows [message] once in [host], then clears it. */
@Composable
fun SnackOnMessage(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        host.showSnackbar(text)
        onShown()
    }
}

@Composable
fun BranchField(
    pick: BranchPick,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Select Branch",
) {
    AppSelectDropdown(
        label = label,
        options = pick.branches.map { SelectorOption(it.id.toString(), it.name) },
        selected = pick.selected?.let { SelectorOption(it.id.toString(), it.name) },
        onSelected = { onSelected(it.id) },
        placeholder = if (pick.loading) "Loading…" else "Pick a branch",
        modifier = modifier.fillMaxWidth(),
    )
}

/** One date box opening the shared picker. */
@Composable
fun DateField(
    label: String,
    date: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val context = LocalContext.current
    PickerField(
        label = label,
        value = date?.toDisplay().orEmpty(),
        trailingIcon = Icons.Filled.DateRange,
        placeholder = placeholder,
        modifier = modifier,
        onClick = { pickAccountsDate(context, date, onPicked) },
    )
}

/** Start and end side by side, the web's two InputDatePickers. */
@Composable
fun DateRangeFields(
    start: SimpleDate?,
    end: SimpleDate?,
    onStart: (SimpleDate) -> Unit,
    onEnd: (SimpleDate) -> Unit,
    startLabel: String = "Start Date",
    endLabel: String = "End Date",
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(startLabel, start, onStart, Modifier.weight(1f))
        DateField(endLabel, end, onEnd, Modifier.weight(1f))
    }
}

/** A quiet centred line: "choose filters", "nothing found". */
@Composable
fun ReportNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.appColors.textOnScreenMuted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ReportError(text: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) LinkButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
fun ReportLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** The web's thousandSeparator with a zero written as a dash. */
fun kitMoneyDash(value: Double): String = if (value == 0.0) "-" else AmountFormat.format(value)

/** The web's thousandSeparator, a zero written as a nought. */
fun kitMoney(value: Double): String = AmountFormat.format(value)
