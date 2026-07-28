package com.example.cashbookbd.ui.requisition

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.RequisitionLine
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlin.math.floor

/**
 * The Requisition create form — a port of the web's `RequisitionForm`: notes, a
 * requisition start/end date range (defaulting to the branch's transaction
 * date), then item lines (product/expense head/labour × day × qty × price)
 * collected into a pending batch. The Requisition Amount is read-only, always
 * Σ(day×qty×price) over the batch; Save posts all lines as one requisition.
 */
@Composable
fun RequisitionFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequisitionFormViewModel = viewModel(
        factory = RequisitionFormViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "New Requisition",
        currentRoute = Routes.REQUISITIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldButton(
                    text = "Start: ${state.startDate.toDisplay()}",
                    onClick = { showDatePicker(context, state.startDate, viewModel::onStartDateChange) },
                    icon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                )
                FieldButton(
                    text = "End: ${state.endDate.toDisplay()}",
                    onClick = { showDatePicker(context, state.endDate, viewModel::onEndDateChange) },
                    icon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                )
            }

            // Read-only, always recomputed from the pending lines (web rule).
            AppTextField(
                value = AmountFormat.format(state.total, 0),
                onValueChange = {},
                label = "Requisition Amount",
                caption = "Requisition Amount",
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )

            ItemEntry(state = state, viewModel = viewModel)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Add New",
                    onClick = viewModel::addLine,
                    enabled = state.canAdd,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = viewModel::submit,
                    enabled = state.canSave,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.lines.isNotEmpty()) {
                RequisitionLinesTable(
                    lines = state.lines,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                    total = state.total,
                )
            }
        }
    }
}

/** The line editor: item picker, remarks, then day × qty × price. */
@Composable
private fun ItemEntry(state: RequisitionFormUiState, viewModel: RequisitionFormViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add Item", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        SearchableSelectDropdown(
            selected = state.selectedItem?.let { SelectorOption(it.id, it.name, it.unit) },
            onSelected = viewModel::onItemSelected,
            search = viewModel::searchItems,
            label = "Select Product",
            placeholder = "Type 3+ chars to search…",
            emptyText = "No item found",
        )

        AppTextField(
            value = state.remarks,
            onValueChange = viewModel::onRemarksChange,
            label = "Remarks (optional)",
            modifier = Modifier.fillMaxWidth(),
        )

        val unit = state.selectedItem?.unit?.takeIf { it.isNotBlank() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = state.day,
                onValueChange = viewModel::onDayChange,
                label = "Day",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.qty,
                onValueChange = viewModel::onQtyChange,
                label = "Qty" + (unit?.let { " ($it)" } ?: ""),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                trailingIcon = unit?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            AppTextField(
                value = state.price,
                onValueChange = viewModel::onPriceChange,
                label = "Price",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }

        // Live day×qty×price preview, like the web's suffix beside Price.
        val lineTotal = (state.day.toDoubleOrNull() ?: 0.0) *
            (state.qty.toDoubleOrNull() ?: 0.0) *
            (state.price.toDoubleOrNull() ?: 0.0)
        if (lineTotal > 0.0) {
            Text(
                text = "Line Total: ${AmountFormat.format(lineTotal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * The pending batch as the web's table: Product | Days | Qty | Rate | Total,
 * with edit/remove per row and the requisition total at the bottom. Per-line
 * Total is floored, matching the web's `Math.floor`.
 */
@Composable
private fun RequisitionLinesTable(
    lines: List<RequisitionLine>,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    total: Double,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                HeaderCell("Product", weight = 1.5f, align = TextAlign.Start)
                HeaderCell("Days", weight = 0.6f)
                HeaderCell("Qty", weight = 0.7f)
                HeaderCell("Rate", weight = 0.8f)
                HeaderCell("Total", weight = 0.9f)
                Spacer(Modifier.size(64.dp, 1.dp)) // The action icons' column.
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(line.item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        if (line.remarks.isNotBlank()) {
                            Text(
                                text = line.remarks,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    ValueCell(AmountFormat.format(line.day.toDoubleOrNull() ?: 0.0, 0), weight = 0.6f)
                    ValueCell(AmountFormat.format(line.qty.toDoubleOrNull() ?: 0.0), weight = 0.7f)
                    ValueCell(AmountFormat.format(line.price.toDoubleOrNull() ?: 0.0), weight = 0.8f)
                    ValueCell(AmountFormat.format(floor(line.amount), 0), weight = 0.9f)
                    IconButton(onClick = { onEdit(index) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit line",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove line",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Requisition Total", fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(total, 0), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.ValueCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

private fun showDatePicker(
    context: Context,
    current: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
