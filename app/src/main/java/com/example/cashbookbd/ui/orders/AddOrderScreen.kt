package com.example.cashbookbd.ui.orders

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.OrderProductLine
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * Create Order — a port of the web's AddOrder. The header is the same for
 * every branch; the product entry depends on the branch's multi-product
 * setting: the original single-product fields (sent flat), or a line editor
 * collecting a pending batch that posts as `items[]`, like the web's grid.
 *
 * On success the backend's confirmation goes to the Orders list via
 * [Routes.CREATED_MESSAGE] and the form pops back.
 */
@Composable
fun AddOrderScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddOrderViewModel = viewModel(
        factory = AddOrderViewModel.provideFactory(LocalContext.current)
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
    // Saved: hand the confirmation to the Orders list, which shows the toast.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Create Order",
        // The Orders list lives in the Admin section of the drawer.
        currentRoute = Routes.ADMIN,
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
            OrderHeaderFields(state, viewModel, context)

            if (state.isMultiProduct) {
                MultiProductEntry(state, viewModel)
            } else {
                SingleProductFields(state, viewModel)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isMultiProduct) {
                    SecondaryButton(
                        text = "Add Product",
                        onClick = viewModel::addLine,
                        enabled = state.canAddLine,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = "Save",
                    onClick = viewModel::submit,
                    enabled = state.canSave,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }

            state.saveHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
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

            if (state.isMultiProduct && state.lines.isNotEmpty()) {
                OrderLinesList(
                    lines = state.lines,
                    totalQty = state.totalLineQty,
                    totalAmount = state.totalLineAmount,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                )
            }
        }
    }
}

/** The header every order shares, whichever product mode the branch runs. */
@Composable
private fun OrderHeaderFields(
    state: AddOrderUiState,
    viewModel: AddOrderViewModel,
    context: Context,
) {
    AppSelectDropdown(
        label = "Select Branch",
        options = state.branches,
        selected = state.selectedBranch,
        onSelected = viewModel::onBranchSelected,
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (state.isBranchesLoading) "Loading branches…" else "Select Branch",
    )
    state.branchesError?.let {
        Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
    }

    AppSelectDropdown(
        label = "Order Type",
        options = ORDER_TYPE_OPTIONS,
        selected = state.orderType,
        onSelected = viewModel::onOrderTypeSelected,
        modifier = Modifier.fillMaxWidth(),
    )

    SearchableLedgerDropdown(
        selectedLedger = state.orderFor,
        onLedgerSelected = viewModel::onOrderForSelected,
        searchLedgers = viewModel::searchLedgers,
        label = if (state.needsOrderFor) "Order For" else "Order For (optional)",
        placeholder = "Type to search customer/supplier…",
        modifier = Modifier.fillMaxWidth(),
    )

    AppTextField(
        value = state.orderNumber,
        onValueChange = viewModel::onOrderNumberChange,
        label = "Enter order number",
        caption = "Order Number",
        modifier = Modifier.fillMaxWidth(),
    )
    AppTextField(
        value = state.deliveryLocation,
        onValueChange = viewModel::onDeliveryLocationChange,
        label = "Enter delivery location",
        caption = "Delivery Location (optional)",
        modifier = Modifier.fillMaxWidth(),
    )

    FieldButton(
        text = "Order Date: ${state.orderDate.toDisplay()}",
        onClick = { showDatePicker(context, state.orderDate, viewModel::onOrderDateChange) },
        icon = Icons.Filled.DateRange,
        modifier = Modifier.fillMaxWidth(),
    )
    FieldButton(
        text = "Last Delivery Date: ${state.lastDeliveryDate.toDisplay()}",
        onClick = { showDatePicker(context, state.lastDeliveryDate, viewModel::onLastDeliveryDateChange) },
        icon = Icons.Filled.DateRange,
        modifier = Modifier.fillMaxWidth(),
    )

    // Only purchase/sales orders can reference an (opposite-type) order — for
    // a Stock order the picker disappears, like the web disables it.
    if (state.referenceEnabled) {
        SearchableSelectDropdown(
            selected = state.referenceOrder,
            onSelected = viewModel::onReferenceOrderSelected,
            search = viewModel::searchReferenceOrders,
            label = "Reference Order (optional)",
            placeholder = "Type 3+ chars to search…",
            emptyText = "No order found",
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AppTextField(
        value = state.notes,
        onValueChange = viewModel::onNotesChange,
        label = "Enter note",
        caption = "Note (optional)",
        modifier = Modifier.fillMaxWidth(),
    )

    AppSelectDropdown(
        label = "Order Status",
        options = ORDER_STATUS_OPTIONS,
        selected = state.status,
        onSelected = viewModel::onStatusSelected,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Single-product mode: the original flat product fields. */
@Composable
private fun SingleProductFields(
    state: AddOrderUiState,
    viewModel: AddOrderViewModel,
) {
    SearchableSelectDropdown(
        selected = state.product,
        onSelected = viewModel::onProductSelected,
        search = viewModel::searchProducts,
        label = "Select Product",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No product found",
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Zero quantity is allowed: an order may be raised against a contract
        // before the figure is agreed. The keyboard can't type a negative.
        AppTextField(
            value = state.totalOrder,
            onValueChange = viewModel::onTotalOrderChange,
            label = "Total Order Qty",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.orderRate,
            onValueChange = viewModel::onOrderRateChange,
            label = "Order Rate",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
    // The web prints the product beside the rate field; here it rides under
    // the row, in the report money format.
    val contractAuto =
        (state.totalOrder.toDoubleOrNull() ?: 0.0) * (state.orderRate.toDoubleOrNull() ?: 0.0)
    if (contractAuto > 0) {
        Text(
            text = "= ${AmountFormat.format(contractAuto, 2)} (Total Order Qty × Order Rate)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
    }
    AppTextField(
        value = state.contractQty,
        onValueChange = viewModel::onContractQtyChange,
        label = "Contract Order Qty (auto: qty × rate)",
        keyboardType = KeyboardType.Decimal,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Multi-product mode: the line editor feeding the pending batch. */
@Composable
private fun MultiProductEntry(
    state: AddOrderUiState,
    viewModel: AddOrderViewModel,
) {
    Text("Add Product", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)

    SearchableSelectDropdown(
        selected = state.lineProduct,
        onSelected = viewModel::onLineProductSelected,
        search = viewModel::searchProducts,
        label = "Select Product",
        placeholder = "Type 3+ chars to search…",
        emptyText = "No product found",
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(
            value = state.lineQty,
            onValueChange = viewModel::onLineQtyChange,
            label = "Order Qty",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.lineRate,
            onValueChange = viewModel::onLineRateChange,
            label = "Rate (optional)",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
    AppTextField(
        value = state.lineContractQty,
        onValueChange = viewModel::onLineContractQtyChange,
        label = "Contract Qty (optional)",
        keyboardType = KeyboardType.Decimal,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The pending batch — the web grid's Product | Qty | Rate | Contract | Amount
 * columns as stacked rows, with per-row edit/delete and the totals footer.
 */
@Composable
private fun OrderLinesList(
    lines: List<OrderProductLine>,
    totalQty: Double,
    totalAmount: Double,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.productName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        val details = buildList {
                            add("Qty ${AmountFormat.format(line.qtyValue)}")
                            add("Rate ${AmountFormat.format(line.rateValue)}")
                            if (line.contractQty.isNotBlank()) add("Contract ${line.contractQty}")
                        }
                        Text(
                            text = details.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = line.amount.toDashedAmount(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                    IconButton(onClick = { onEdit(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit line", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove line", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total (${lines.size} product${if (lines.size == 1) "" else "s"})", fontWeight = AppFontWeight.Bold)
                Text(
                    text = "Qty ${totalQty.toDashedAmount()}  •  ${totalAmount.toDashedAmount()}",
                    fontWeight = AppFontWeight.Bold,
                )
            }
        }
    }
}

/** Zero shows as "-", like the web grid's Amount column and every report table. */
private fun Double.toDashedAmount(): String =
    if (this == 0.0) "-" else AmountFormat.format(this)

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
