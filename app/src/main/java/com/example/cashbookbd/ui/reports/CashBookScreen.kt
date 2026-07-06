package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.CashBookRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import java.text.DecimalFormat

@Composable
fun CashBookScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashBookViewModel = viewModel(
        factory = CashBookViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Expired/rejected token -> clear session and return to login.
    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Cash Book",
        currentRoute = Routes.CASHBOOK,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
            )

            Box(modifier = Modifier.weight(1f)) {
                CashBookResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

@Composable
private fun FilterCard(
    state: CashBookUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

    // No card background — the form sits directly on the screen. The outer
    // padding preserves the previous spacing (12.dp margin + 16.dp inner).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
    ) {
        BranchDropdown(
            branches = state.branches,
            selected = state.selectedBranch,
            isLoading = state.isBranchesLoading,
            onSelected = onBranchSelected,
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                label = "Start Date",
                value = state.startDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.startDate, onStartDate) },
            )
            PickerField(
                label = "End Date",
                value = state.endDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.endDate, onEndDate) },
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onApply,
            enabled = state.canApply,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (state.isReportLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun BranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PickerField(
            label = "Select Branch",
            value = selected?.name ?: if (isLoading) "Loading branches…" else "",
            placeholder = "Select Branch",
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        onSelected(branch)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A read-only text field for the filter form. It's not directly editable — a
 * transparent overlay forwards taps to [onClick] (dropdown / date picker).
 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onClick: () -> Unit,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(trailingIcon, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
        )
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
private fun CashBookResults(state: CashBookUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox { CircularProgressIndicator() }

        state.reportError != null -> CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.reportError,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }

        state.report == null -> CenterBox {
            Text(
                text = "Choose a branch and date range, then tap Apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No transactions found for this period.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> CashBookTable(rows = state.report.rows)
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// Column widths for the horizontally-scrollable table.
// Order: Date | VR No | Description | Received (credit) | Payment (debit)
private val COL_DATE = 96.dp
private val COL_VR = 100.dp
private val COL_DESCRIPTION = 210.dp
private val COL_RECEIVED = 116.dp
private val COL_PAYMENT = 116.dp

@Composable
private fun CashBookTable(rows: List<CashBookRow>) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScroll),
    ) {
        TableHeader()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            rows.forEach { row -> TableRow(row) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 10.dp),
    ) {
        HeaderCell("Date", COL_DATE)
        HeaderCell("VR No", COL_VR)
        HeaderCell("Description", COL_DESCRIPTION)
        HeaderCell("Received", COL_RECEIVED, TextAlign.End)
        HeaderCell("Payment", COL_PAYMENT, TextAlign.End)
    }
}

@Composable
private fun TableRow(row: CashBookRow) {
    val bg = if (row.isSummary) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val weight = if (row.isSummary) FontWeight.Bold else FontWeight.Normal

    Column {
        Row(
            modifier = Modifier
                .background(bg)
                .padding(vertical = 10.dp),
        ) {
            BodyCell(row.date, COL_DATE, fontWeight = weight)
            BodyCell(row.voucherNo, COL_VR, fontWeight = weight)
            BodyCell(row.particulars, COL_DESCRIPTION, fontWeight = weight, maxLines = 3)
            BodyCell(amountOrBlank(row.credit), COL_RECEIVED, TextAlign.End, weight)
            BodyCell(amountOrBlank(row.debit), COL_PAYMENT, TextAlign.End, weight)
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    align: TextAlign = TextAlign.Start,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
    )
}

private val amountFormat = DecimalFormat("#,##0")

/** Received/Payment cells read cleaner when zeros are blank. */
private fun amountOrBlank(value: Double): String =
    if (value == 0.0) "" else amountFormat.format(value)

private fun showDatePicker(
    context: Context,
    initial: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            // DatePickerDialog months are 0-based; SimpleDate is 1-based.
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
