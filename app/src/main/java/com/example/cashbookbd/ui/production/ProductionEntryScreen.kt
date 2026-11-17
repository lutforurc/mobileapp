package com.example.cashbookbd.ui.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.ui.production.model.ProductionAllocatableRow
import com.example.cashbookbd.ui.production.model.ProductionBatchRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import java.util.Locale

private const val TAB_INPUTS = 0
private const val TAB_OUTPUTS = 1
private const val TAB_COSTS = 2
private const val TAB_BATCHES = 3

private val ENTRY_TABS = listOf("Materials in", "Products out", "Waste & Labour", "Recent batches")

@Composable
fun ProductionEntryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductionEntryViewModel = viewModel(
        factory = ProductionEntryViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val invoiceRepository = remember { ServiceLocator.provideInvoiceRepository(context) }

    val searchProducts: suspend (String) -> Resource<List<EntryProduct>> = { query ->
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> Resource.Success(
                result.data.map {
                    EntryProduct(it.id, it.dropdownLabel, it.unit, it.salesPrice)
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.error) {
        val text = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onErrorShown()
    }

    AuthenticatedShell(
        title = "Production Entry",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                EntryHeader(state, viewModel)
                TabRow(selectedTabIndex = state.tab) {
                    ENTRY_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = state.tab == index,
                            onClick = { viewModel.onTab(index) },
                            text = {
                                Text(
                                    text = if (index == TAB_BATCHES) "$title (${state.batches.size})" else title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }

                val contentModifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)

                when (state.tab) {
                    TAB_INPUTS -> InputsPane(state, viewModel, searchProducts, contentModifier)
                    TAB_OUTPUTS -> OutputsPane(state, viewModel, searchProducts, contentModifier)
                    TAB_COSTS -> CostsPane(state, viewModel, contentModifier)
                    else -> BatchesPane(state, viewModel, contentModifier)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.confirmReverseId?.let {
        AlertDialog(
            onDismissRequest = viewModel::onReverseDismissed,
            title = { Text("Reverse this batch?") },
            text = { Text("The inputs go back on the shelf and the output comes off.") },
            confirmButton = {
                LinkButton(
                    text = "Reverse",
                    onClick = viewModel::reverse,
                    color = MaterialTheme.appColors.danger,
                )
            },
            dismissButton = { LinkButton("Cancel", viewModel::onReverseDismissed) },
        )
    }
    state.confirmDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text("Delete this draft?") },
            text = { Text("Nothing has moved yet, so nothing is undone.") },
            confirmButton = {
                LinkButton(
                    text = "Delete",
                    onClick = viewModel::deleteDraft,
                    color = MaterialTheme.appColors.danger,
                )
            },
            dismissButton = { LinkButton("Cancel", viewModel::onDeleteDismissed) },
        )
    }
}

@Composable
private fun EntryHeader(state: ProductionEntryUiState, viewModel: ProductionEntryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (state.editingBatchId != null) {
                            MaterialTheme.appColors.warning.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        },
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.headerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.editingBatchId != null) {
                        Spacer(Modifier.size(8.dp))
                        LinkButton(text = "Cancel", onClick = viewModel::resetForm)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (state.editingBatchId != null) "Update draft" else "Save draft",
                onClick = viewModel::save,
                enabled = state.canWrite,
                isLoading = state.isSaving,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Start over",
                onClick = viewModel::resetForm,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "A draft moves no stock. Post it from Recent batches when the batch is done.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        if (!state.canWrite) {
            Text(
                text = "You can read this screen but not save (production.create is not granted).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }

        AppDateField(
            label = "Production date",
            date = SimpleDate.fromApi(state.date),
            onPicked = viewModel::onDate,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.batchRef,
                onValueChange = viewModel::onBatchRef,
                label = "Batch ref",
                caption = "Batch ref",
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = "Note",
                caption = "Note",
                modifier = Modifier.weight(1f),
            )
        }

        val recipeOptions = listOf(SelectorOption("", "Enter by hand")) + state.recipes.map {
            SelectorOption(
                it.id.toString(),
                it.name + (if (it.version.isNotBlank()) " v${it.version}" else "") +
                    (if (it.outputProduct.isNotBlank()) " — ${it.outputProduct}" else ""),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            AppSelectDropdown(
                label = "Recipe (optional)",
                options = recipeOptions,
                selected = recipeOptions.firstOrNull { it.id == state.recipeId } ?: recipeOptions.first(),
                onSelected = { viewModel.chooseRecipe(it.id) },
                modifier = Modifier.weight(1.4f),
            )
            AppTextField(
                value = state.recipeQty,
                onValueChange = viewModel::onRecipeQty,
                label = "The recipe's own yield",
                caption = "Make",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(text = "Fill", onClick = viewModel::refillRecipe, compact = true)
        }
        if (state.recipeName.isNotBlank()) {
            Text(
                text = "Filled from ${state.recipeName}" +
                    (if (state.recipeVersion.isNotBlank()) " v${state.recipeVersion}" else "") +
                    " — the batch is still yours to change.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        if (state.recipeQtyChanged) {
            Text(
                text = "The make quantity changed. Press Fill to apply it before saving.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.danger,
                fontWeight = AppFontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProductPicker(
    label: String,
    selected: EntryProduct?,
    onSelected: (EntryProduct) -> Unit,
    search: suspend (String) -> Resource<List<EntryProduct>>,
    modifier: Modifier = Modifier,
) {
    // The dropdown answers only the option it was given (id, label, unit), so
    // the products from the last search are kept here to hand back the price
    // that came with them — which the output form needs.
    val cache = remember { mutableMapOf<String, EntryProduct>() }

    SearchableSelectDropdown(
        selected = selected?.let { SelectorOption(it.id, it.label, it.unit.ifBlank { null }) },
        onSelected = { option ->
            onSelected(
                cache[option.id] ?: EntryProduct(
                    id = option.id,
                    label = option.label,
                    unit = option.sublabel.orEmpty(),
                    price = null,
                )
            )
        },
        search = { query ->
            when (val result = search(query)) {
                is Resource.Success -> {
                    result.data.forEach { cache[it.id] = it }
                    Resource.Success(
                        result.data.map {
                            SelectorOption(it.id, it.label, it.unit.ifBlank { null })
                        }
                    )
                }
                is Resource.Error -> result
                Resource.Loading -> Resource.Loading
            }
        },
        label = label,
        placeholder = "Type a product name…",
        modifier = modifier,
    )
}

@Composable
private fun InputsPane(
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
    search: suspend (String) -> Resource<List<EntryProduct>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Raw materials, bulk packs and empty packets this batch uses.", style = MaterialTheme.typography.bodySmall)
        ProductPicker(
            label = "Material",
            selected = state.inputDraft,
            onSelected = viewModel::onInputProduct,
            search = search,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.inputDraftQty,
            onValueChange = viewModel::onInputQty,
            label = "",
            caption = "Quantity" +
                (state.inputDraft?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (state.editingInputId != null) "Update" else "Add",
                onClick = viewModel::addOrUpdateInput,
                modifier = Modifier.weight(1f),
            )
            if (state.editingInputId != null) {
                SecondaryButton(text = "Cancel", onClick = viewModel::resetForm, modifier = Modifier.weight(1f))
            }
        }

        Divider()
        if (state.inputs.isEmpty()) {
            Text("Pick a material above and press Add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted)
        } else {
            state.inputs.forEachIndexed { index, row ->
                val onHand = state.stock[row.productId]
                val short = onHand != null && (row.qty.toDoubleOrNull() ?: 0.0) > onHand
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${row.productName}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${row.qty} ${row.unit} · in stock ${onHand?.let { trimNum(it) } ?: "-"}" +
                                if (short) " · short" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (short) MaterialTheme.appColors.danger else MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    IconButton(onClick = { viewModel.editInput(row.localId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit material", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.removeInput(row.localId) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove material", modifier = Modifier.size(18.dp), tint = MaterialTheme.appColors.danger)
                    }
                }
            }
        }

        state.check?.let { check ->
            Divider()
            Text(
                text = "Recipe needs at ${state.recipeQty.ifBlank { trimNum(check.yieldQty) }} ${check.unit}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.SemiBold,
            )
            check.lines.forEach { line ->
                val required = Math.round(line.baseQty * state.recipeFactor * 100.0) / 100.0
                val onHand = state.stock[line.productId]
                val short = onHand != null && required > onHand
                Row(Modifier.fillMaxWidth()) {
                    Text(line.productName.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${trimNum(required)} ${line.unit}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "stock ${onHand?.let { trimNum(it) } ?: "-"}" + if (short) " short" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (short) MaterialTheme.appColors.danger else MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OutputsPane(
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
    search: suspend (String) -> Resource<List<EntryProduct>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "What the batch made. With more than one product, the cost is shared by sales value; type a share to set it yourself.",
            style = MaterialTheme.typography.bodySmall,
        )
        ProductPicker(
            label = "Product made",
            selected = state.outputDraft,
            onSelected = viewModel::onOutputProduct,
            search = search,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.outputDraftQty,
                onValueChange = viewModel::onOutputQty,
                label = "",
                caption = "Good quantity" +
                    (state.outputDraft?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.outputDraftPrice,
                onValueChange = viewModel::onOutputPrice,
                label = "",
                caption = "Sales price",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            AppDateField(
                label = "Expiry (optional)",
                date = SimpleDate.fromApi(state.outputDraftExpiry),
                onPicked = viewModel::onOutputExpiry,
                modifier = Modifier.weight(1f),
            )
            if (state.outputDraftExpiry.isNotBlank()) {
                LinkButton(text = "Clear", onClick = viewModel::clearOutputExpiry)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (state.editingOutputId != null) "Update" else "Add",
                onClick = viewModel::addOrUpdateOutput,
                modifier = Modifier.weight(1f),
            )
            if (state.editingOutputId != null) {
                SecondaryButton(text = "Cancel", onClick = viewModel::resetForm, modifier = Modifier.weight(1f))
            }
        }

        Divider()
        if (state.outputs.isEmpty()) {
            Text(
                "Pick the product this batch made and press Add. Add more than one if the batch made several.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        } else {
            state.outputs.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${row.productName}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${row.qty} ${row.unit} · price " +
                                (row.price.takeIf { it.isNotBlank() } ?: "not set") +
                                (if (row.expiry.isNotBlank()) " · expiry ${row.expiry}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.price.toDoubleOrNull() == null || row.price.toDoubleOrNull() == 0.0) {
                                MaterialTheme.appColors.danger
                            } else {
                                MaterialTheme.appColors.textOnScreenMuted
                            },
                        )
                    }
                    IconButton(onClick = { viewModel.editOutput(row.localId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit output", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.removeOutput(row.localId) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove output", modifier = Modifier.size(18.dp), tint = MaterialTheme.appColors.danger)
                    }
                }
            }
        }

        if (state.outputs.size > 1) {
            Divider()
            Text(
                text = "Share of cost %",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.SemiBold,
            )
            state.outputs.forEachIndexed { index, row ->
                val auto = state.autoShares.getOrElse(index) { 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.productName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    AppTextField(
                        value = row.share.ifBlank { String.format(Locale.US, "%.2f", auto) },
                        onValueChange = { viewModel.onShare(row.localId, it) },
                        label = "",
                        caption = "%",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
            Text(
                text = "Shares add up to ${String.format(Locale.US, "%.2f", state.shareTotal)}%" +
                    (if (state.sharesTyped) " (typed)" else " (by sales value)"),
                style = MaterialTheme.typography.bodySmall,
                color = if (kotlin.math.abs(state.shareTotal - 100) > 0.01) {
                    MaterialTheme.appColors.danger
                } else {
                    MaterialTheme.appColors.textOnScreenMuted
                },
            )
            if (state.sharesTyped) {
                LinkButton(text = "Share by sales value again", onClick = viewModel::clearShares)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CostsPane(
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Waste", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.lossActual,
                onValueChange = viewModel::onLossActual,
                label = "",
                caption = "Lost in this batch",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.lossBasis,
                onValueChange = viewModel::onLossBasis,
                label = "kg",
                caption = "Measured in",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.lossTolerance,
                onValueChange = viewModel::onLossTolerance,
                label = "",
                caption = "Normal tolerance %",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.lossReason,
                onValueChange = viewModel::onLossReason,
                label = "",
                caption = "Reason, if abnormal",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Loss up to the normal tolerance stays in the product cost. Anything beyond it is abnormal and is left out of the cost.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )

        Divider()
        Text("Labour and factory expense", style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)
        Text(
            "Spend posted Factory Labour, Packing Labour or Factory Transport from this month on this batch.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        if (state.allocatable.isEmpty()) {
            Text(
                "No posted labour or factory expense in this month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        } else {
            state.allocatable.forEach { row -> AllocationRow(row, state, viewModel) }
        }
        Text(
            text = "Spent on this batch  ${AmountFormat.format(state.allocationTotal)}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AllocationRow(
    row: ProductionAllocatableRow,
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.headName.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall)
            Text(
                "${row.voucherNo} · free ${AmountFormat.format(row.remaining)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        AppTextField(
            value = state.allocations[row.sourceAccDetailId].orEmpty(),
            onValueChange = { viewModel.onAllocation(row, it) },
            label = "",
            caption = "Spend",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(0.5f),
        )
    }
}

@Composable
private fun BatchesPane(
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Post a draft to move its stock. A posted batch can be reversed until any of its products is sold or moved.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.batches.isEmpty()) {
            Text("No batch yet. Save a draft and it appears here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted)
        } else {
            state.batches.forEach { row -> BatchCard(row, state, viewModel) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BatchCard(
    row: ProductionBatchRow,
    state: ProductionEntryUiState,
    viewModel: ProductionEntryViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.SemiBold)
                if (row.vrNo.isNotBlank() && row.vrNo != row.batchRef) {
                    Text(row.vrNo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
                }
            }
            Text(
                text = row.status,
                style = MaterialTheme.typography.labelSmall,
                color = when (row.status) {
                    "posted" -> MaterialTheme.appColors.success
                    "reversed" -> MaterialTheme.appColors.danger
                    else -> MaterialTheme.appColors.textOnScreenMuted
                },
                fontWeight = AppFontWeight.SemiBold,
            )
        }
        Text(
            text = if (row.outputs.isNotEmpty()) {
                row.outputs.joinToString(" · ") { "${it.productName} ${trimNum(it.qty)}" }
            } else {
                row.outputProduct
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${row.productionDate} · unit cost ${AmountFormat.format(row.unitCost)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.status == "draft") {
                PrimaryButton(
                    text = "Post",
                    onClick = { viewModel.post(row.id) },
                    compact = true,
                    enabled = state.canWrite,
                )
                LinkButton(text = "Edit", onClick = { viewModel.openBatch(row.id) })
                LinkButton(
                    text = "Delete",
                    onClick = { viewModel.onDeleteRequested(row.id) },
                    color = MaterialTheme.appColors.danger,
                )
            }
            if (row.status == "posted") {
                SecondaryButton(
                    text = "Reverse",
                    onClick = { viewModel.onReverseRequested(row.id) },
                    compact = true,
                )
            }
        }
    }
}

private fun trimNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
