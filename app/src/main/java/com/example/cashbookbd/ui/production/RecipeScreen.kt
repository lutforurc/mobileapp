package com.example.cashbookbd.ui.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
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
import com.example.cashbookbd.ui.production.model.RecipeLine
import com.example.cashbookbd.ui.production.model.RecipeListRow
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Recipe (web `Recipe.tsx`): the standard materials for one output product.
 *
 * Two tabs, because the screen holds two jobs — writing this version and
 * reading back the versions already saved. A version a batch has saved against
 * is retired rather than deleted, which the server decides.
 */
@Composable
fun RecipeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeViewModel = viewModel(
        factory = RecipeViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val invoiceRepository = remember { ServiceLocator.provideInvoiceRepository(context) }

    val searchProducts: suspend (String) -> Resource<List<SelectorOption>> = { query ->
        when (val result = invoiceRepository.searchProducts(query)) {
            is Resource.Success -> Resource.Success(
                result.data.map { product ->
                    SelectorOption(
                        id = product.id,
                        label = product.dropdownLabel,
                        sublabel = product.unit.ifBlank { null },
                    )
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
        title = "Recipe",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                RecipeHeader(state, viewModel)
                TabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == RecipeTab.RECIPE,
                        onClick = { viewModel.onTab(RecipeTab.RECIPE) },
                        text = { Text("Recipe") },
                    )
                    Tab(
                        selected = state.tab == RecipeTab.LIST,
                        onClick = { viewModel.onTab(RecipeTab.LIST) },
                        text = {
                            Text(
                                if (state.recipes.isEmpty()) "Recipe List"
                                else "Recipe List (${state.recipes.size})"
                            )
                        },
                    )
                }

                if (state.tab == RecipeTab.RECIPE) {
                    RecipeForm(
                        state = state,
                        viewModel = viewModel,
                        searchProducts = searchProducts,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    RecipeListPane(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (state.confirmNewVersion) {
        AlertDialog(
            onDismissRequest = viewModel::onNewVersionDismissed,
            title = { Text("New version") },
            text = {
                Text(
                    "Copy ${state.form.name.ifBlank { "#${state.form.id}" }}" +
                        (if (state.form.version.isNotBlank()) " version ${state.form.version}" else "") +
                        " into a new version, effective from ${state.effectiveDate?.toDisplay() ?: "today"}?\n\n" +
                        "The copy is taken from the saved version, so anything changed on this form " +
                        "but not yet updated is not carried over. Change the new version after it opens."
                )
            },
            confirmButton = {
                LinkButton(
                    text = "New version",
                    onClick = viewModel::createNewVersion,
                    enabled = !state.creatingVersion,
                )
            },
            dismissButton = {
                LinkButton(text = "Cancel", onClick = viewModel::onNewVersionDismissed)
            },
        )
    }

    state.removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::onRemoveDismissed,
            title = { Text("Remove recipe") },
            text = {
                Text(
                    "Remove ${target.name} version ${target.version}?\n\n" +
                        "A version a batch has already saved against is retired, not deleted."
                )
            },
            confirmButton = {
                LinkButton(
                    text = "Remove",
                    onClick = viewModel::removeRecipe,
                    color = MaterialTheme.appColors.danger,
                    enabled = !state.removing,
                )
            },
            dismissButton = {
                LinkButton(text = "Cancel", onClick = viewModel::onRemoveDismissed)
            },
        )
    }
}

@Composable
private fun RecipeHeader(state: RecipeUiState, viewModel: RecipeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Recipe",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = AppFontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.isOpening) {
                Text(
                    text = "Opening…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (state.isEditing) MaterialTheme.appColors.warning.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
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
                    if (state.isEditing) {
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel editing",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.clearForm() },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = state.saveLabel,
                onClick = viewModel::save,
                enabled = state.canWrite,
                isLoading = state.isSaving,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "New version",
                onClick = viewModel::onNewVersionRequested,
                enabled = state.canWrite,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Clear",
                onClick = viewModel::clearForm,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.canWrite) {
            Text(
                text = "You can read recipes but not change them (production.create is not granted).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
    }
}

@Composable
private fun RecipeForm(
    state: RecipeUiState,
    viewModel: RecipeViewModel,
    searchProducts: suspend (String) -> Resource<List<SelectorOption>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Recipe details")

        AppTextField(
            value = state.form.name,
            onValueChange = { viewModel.onField("name", it) },
            label = "e.g. Spice mix 1 kg",
            caption = "Recipe name",
            modifier = Modifier.fillMaxWidth(),
        )

        SearchableSelectDropdown(
            selected = state.form.outputProductId.takeIf { it.isNotBlank() }?.let {
                SelectorOption(id = it, label = state.form.outputProductName)
            },
            onSelected = { option ->
                viewModel.onOutputProduct(option.id, option.label, option.sublabel.orEmpty())
            },
            search = searchProducts,
            label = "Product it makes",
            placeholder = "Type a product name…",
            modifier = Modifier.fillMaxWidth(),
        )

        AppTextField(
            value = state.form.yieldQty,
            onValueChange = { viewModel.onField("yieldQty", it) },
            label = "e.g. 95",
            caption = if (state.form.unit.isBlank()) "Yield" else "Yield (${state.form.unit})",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )

        AppDateField(
            label = "Effective from",
            date = state.effectiveDate,
            onPicked = { viewModel.onEffectiveDate(it.toApi()) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.form.lossTolerancePct,
                onValueChange = { viewModel.onField("lossTolerancePct", it) },
                label = "e.g. 5",
                caption = "Normal loss %",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.form.lossBasis,
                onValueChange = { viewModel.onField("lossBasis", it) },
                label = "kg",
                caption = "Loss measured in",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.form.suggestedCost,
                onValueChange = { viewModel.onField("suggestedCost", it) },
                label = "A reminder only",
                caption = "Suggested cost (optional)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.form.version,
                onValueChange = {},
                label = "",
                caption = "Version",
                enabled = false,
                modifier = Modifier.weight(1f),
            )
        }

        AppTextField(
            value = state.form.note,
            onValueChange = { viewModel.onField("note", it) },
            label = "Anything worth writing down",
            caption = "Note",
            multiline = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
        SectionTitle("Materials")

        SearchableSelectDropdown(
            selected = state.lineDraft.productId.takeIf { it.isNotBlank() }?.let {
                SelectorOption(id = it, label = state.lineDraft.productName)
            },
            onSelected = { option ->
                viewModel.onDraftProduct(option.id, option.label, option.sublabel.orEmpty())
            },
            search = searchProducts,
            label = "Material",
            placeholder = "Type a product name…",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = state.lineDraft.qty,
                onValueChange = { viewModel.onDraftField("qty", it) },
                label = "",
                caption = if (state.lineDraft.unit.isBlank()) "Quantity"
                else "Quantity (${state.lineDraft.unit})",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppSelectDropdown(
                label = "Kind",
                options = listOf(
                    SelectorOption("material", "Material"),
                    SelectorOption("packaging", "Packaging"),
                ),
                selected = SelectorOption(
                    state.lineDraft.lineType,
                    if (state.lineDraft.lineType == "packaging") "Packaging" else "Material",
                ),
                onSelected = { viewModel.onDraftField("lineType", it.id) },
                modifier = Modifier.weight(1f),
            )
        }

        AppTextField(
            value = state.lineDraft.note,
            onValueChange = { viewModel.onDraftField("note", it) },
            label = "Optional",
            caption = "Note",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (state.editingLineIndex == null) "Add" else "Update",
                onClick = viewModel::onSubmitLine,
                modifier = Modifier.weight(1f),
            )
            if (state.editingLineIndex != null) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = viewModel::cancelLineEdit,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.lines.isEmpty()) {
            Text(
                text = "No material yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        } else {
            state.lines.forEachIndexed { index, line ->
                LineRow(
                    line = line,
                    index = index,
                    onEdit = { viewModel.editLine(index) },
                    onRemove = { viewModel.removeLine(index) },
                )
            }
        }

        Text(
            text = "Per batch of the yield above. Empty packets, labels and cartons are " +
                "materials too — mark them as packaging so the count stays visible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LineRow(
    line: RecipeLine,
    index: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.appColors.cardRow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Spacer(Modifier.size(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.productName.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(line.qty)
                    if (line.unit.isNotBlank()) append(" ${line.unit}")
                    append(" · ")
                    append(if (line.lineType == "packaging") "Packaging" else "Material")
                    if (line.note.isNotBlank()) append(" · ${line.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit material", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove material",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.appColors.danger,
            )
        }
    }
}

@Composable
private fun RecipeListPane(
    state: RecipeUiState,
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier,
) {
    when {
        state.listLoading && state.recipes.isEmpty() -> CenterNote("Loading recipes…", modifier)
        state.listError != null -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.listError!!, style = MaterialTheme.typography.bodyMedium)
            LinkButton(text = "Retry", onClick = viewModel::loadRecipes)
        }
        state.recipes.isEmpty() -> CenterNote("No recipe yet", modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.recipes, key = { it.id }) { row ->
                RecipeRow(
                    row = row,
                    canWrite = state.canWrite,
                    onOpen = { viewModel.openRecipe(row.id) },
                    onRemove = { viewModel.onRemoveRequested(row) },
                )
            }
        }
    }
}

@Composable
private fun RecipeRow(
    row: RecipeListRow,
    canWrite: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${row.name.ifBlank { "-" }}  ·  v${row.version}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Makes ${row.makesLabel} · ${row.yieldLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.lossLabel} · ${row.effectiveDate.ifBlank { "-" }} · " +
                    if (row.isRetired) "Retired" else "Active",
                style = MaterialTheme.typography.bodySmall,
                color = if (row.isRetired) MaterialTheme.appColors.warning
                else MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Filled.Edit, contentDescription = "Open recipe", modifier = Modifier.size(18.dp))
        }
        if (canWrite) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove recipe",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.appColors.danger,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = AppFontWeight.SemiBold,
    )
}

@Composable
private fun CenterNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
    }
}
