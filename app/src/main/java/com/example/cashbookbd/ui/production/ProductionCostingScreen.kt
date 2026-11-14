package com.example.cashbookbd.ui.production

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.production.model.ProductionCosting
import com.example.cashbookbd.ui.production.model.productionAmountText
import com.example.cashbookbd.ui.production.model.productionQtyText
import com.example.cashbookbd.ui.production.model.productionVarianceText
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportError
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Batch Costing (web `BatchCosting.tsx`): every input and the purchase layers
 * it came from, the allocated expense lines, the output layers and the recipe
 * variance — the breakdown behind one Production Register row.
 */
@Composable
fun ProductionCostingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    batchId: Long,
    modifier: Modifier = Modifier,
    viewModel: ProductionCostingViewModel = viewModel(
        factory = ProductionCostingViewModel.provideFactory(LocalContext.current, batchId)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)

    AuthenticatedShell(
        title = "Batch Costing",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SecondaryButton(
                    text = "Back to Register",
                    onClick = { navController.popBackStack() },
                    compact = true,
                )
            }

            when {
                state.error != null -> ReportError(state.error!!, viewModel::load)
                state.isLoading || state.detail == null -> ReportLoading()
                else -> CostingContent(state.detail!!)
            }
        }
    }
}

@Composable
private fun CostingContent(detail: ProductionCosting) {
    val master = detail.master
    val danger = MaterialTheme.appColors.danger

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        master?.flagMessage?.let { message ->
            Text(
                text = "Source changed: $message",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = danger,
            )
        }
        if (master?.status == "reversed") {
            Text(
                text = "Reversed. The stock movement has been taken back off and the allocated " +
                    "expense released; the batch and its detail are kept as history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.warning,
            )
        }
        detail.variance?.takeIf { it.over }?.let { variance ->
            Text(
                text = "Above the recipe: ${productionVarianceText(variance)}.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = danger,
            )
        }

        if (master != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("Batch", master.batchLabel, Modifier.weight(1f))
                KpiTile("Date", master.date.ifBlank { "-" }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("Status", master.status.ifBlank { "-" }, Modifier.weight(1f))
                if (!detail.severalOutputs) {
                    KpiTile("Output Qty", productionQtyText(master.outputQty), Modifier.weight(1f))
                } else {
                    KpiTile("Outputs", detail.outputs.size.toString(), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("Material Cost", productionAmountText(master.materialCost), Modifier.weight(1f))
                KpiTile("Allocated Cost", productionAmountText(master.allocatedCost), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("True Cost", productionAmountText(master.trueCost), Modifier.weight(1f))
                if (!detail.severalOutputs) {
                    KpiTile("Unit Cost", productionAmountText(master.unitCost), Modifier.weight(1f))
                } else {
                    KpiTile("Total Cost", productionAmountText(master.totalCost), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("Normal Loss", productionQtyText(master.lossNormal), Modifier.weight(1f))
                KpiTile("Abnormal Loss", productionQtyText(master.lossAbnormal), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiTile("Abnormal Cost", productionAmountText(master.abnormalLossCost), Modifier.weight(1f))
                KpiTile("Total Cost", productionAmountText(master.totalCost), Modifier.weight(1f))
            }
        }

        detail.recipe?.let { recipe ->
            SectionTitle(
                "Recipe — ${recipe.name}" + (recipe.version?.let { " v$it" } ?: "")
            )
            Text(
                text = "Saved against this batch on ${recipe.effectiveDate.ifBlank { "-" }}; " +
                    "filled for ${productionQtyText(recipe.targetQty)}." +
                    detail.variance?.let { variance ->
                        " The batch made ${productionQtyText(variance.outputActual)}" +
                            if (variance.outputShort) " — short of the recipe" else ""
                    }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReportTable(
                columns = listOf(
                    ReportColumn("MATERIAL", ReportColWidth.Fixed(180.dp)) { row, _ ->
                        val suffix = buildString {
                            if (row.lineType == "packaging") append(" (packaging)")
                            if (row.notInRecipe) append(" (not in recipe)")
                        }
                        cellText(row.productName.ifBlank { "-" } + suffix, maxLines = 2)
                    },
                    ReportColumn("RECIPE SAYS", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
                        cellText("${productionQtyText(row.expectedQty)} ${row.unit}".trim())
                    },
                    ReportColumn("USED", ReportColWidth.Fixed(100.dp), TextAlign.End) { row, _ ->
                        cellText("${productionQtyText(row.actualQty)} ${row.unit}".trim())
                    },
                    ReportColumn("DIFFERENCE", ReportColWidth.Fixed(120.dp), TextAlign.End) { row, _ ->
                        val text = when {
                            row.difference == 0.0 -> "0"
                            else -> {
                                val sign = if (row.difference > 0) "+" else ""
                                "$sign${productionQtyText(row.difference)} ${row.unit}".trim()
                            }
                        }
                        cellText(
                            text + if (row.over) " over" else "",
                            color = if (row.over) danger else Color.Unspecified,
                            bold = row.over,
                        )
                    },
                ),
                data = detail.recipeLines,
                noDataMessage = "No recipe lines",
                scrollable = false,
            )
        }

        SectionTitle("Inputs")
        ReportTable(
            columns = listOf(
                ReportColumn("PRODUCT", ReportColWidth.Fixed(200.dp)) { row, _ ->
                    cellText(row.productName.ifBlank { "-" }, maxLines = 2)
                },
                ReportColumn("BASE QTY", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
                    cellText(productionQtyText(row.baseQty))
                },
                ReportColumn("FIFO COST", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
                    cellText(productionAmountText(row.fifoCost))
                },
            ),
            data = detail.inputs,
            noDataMessage = "No inputs",
            scrollable = false,
        )

        SectionTitle("Consumed purchase layers (the batch's trace)")
        ReportTable(
            columns = listOf(
                ReportColumn("PRODUCT", ReportColWidth.Fixed(170.dp)) { row, _ ->
                    cellText(row.productName.ifBlank { "-" }, maxLines = 2)
                },
                ReportColumn("VOUCHER", ReportColWidth.Fixed(110.dp)) { row, _ ->
                    cellText(row.sourceVoucherNo.ifBlank { "-" })
                },
                ReportColumn("DATE", ReportColWidth.Fixed(90.dp)) { row, _ ->
                    cellText(row.layerDate.ifBlank { "-" })
                },
                ReportColumn("QTY", ReportColWidth.Fixed(90.dp), TextAlign.End) { row, _ ->
                    cellText(productionQtyText(row.layerQty))
                },
                ReportColumn("RATE", ReportColWidth.Fixed(90.dp), TextAlign.End) { row, _ ->
                    cellText(productionAmountText(row.layerRate))
                },
            ),
            data = detail.layers,
            noDataMessage = "No purchase layers",
            scrollable = false,
        )

        SectionTitle("Allocated expense lines")
        ReportTable(
            columns = listOf(
                ReportColumn("VOUCHER", ReportColWidth.Fixed(110.dp)) { row, _ ->
                    cellText(row.sourceVoucherNo.ifBlank { "-" })
                },
                ReportColumn("HEAD", ReportColWidth.Fixed(160.dp)) { row, _ ->
                    cellText(row.headName.ifBlank { "-" }, maxLines = 2)
                },
                ReportColumn("DATE", ReportColWidth.Fixed(90.dp)) { row, _ ->
                    cellText(row.sourceDate.ifBlank { "-" })
                },
                ReportColumn("AMOUNT", ReportColWidth.Fixed(100.dp), TextAlign.End) { row, _ ->
                    cellText(productionAmountText(row.allocatedAmount))
                },
            ),
            data = detail.allocations,
            noDataMessage = "No allocated expense lines",
            scrollable = false,
        )

        SectionTitle("Output layers")
        ReportTable(
            columns = listOf(
                ReportColumn("PRODUCT", ReportColWidth.Fixed(170.dp)) { row, _ ->
                    cellText(row.productName.ifBlank { "-" }, maxLines = 2)
                },
                ReportColumn("SHARE %", ReportColWidth.Fixed(80.dp), TextAlign.End) { row, _ ->
                    cellText(row.costSharePct?.let { String.format(java.util.Locale.US, "%.2f", it) }.orEmpty())
                },
                ReportColumn("QTY", ReportColWidth.Fixed(80.dp), TextAlign.End) { row, _ ->
                    cellText(productionQtyText(row.qty))
                },
                ReportColumn("UNIT COST", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
                    cellText(productionAmountText(row.unitCost))
                },
                ReportColumn("BATCH", ReportColWidth.Fixed(110.dp)) { row, _ ->
                    cellText(row.batchNo.ifBlank { "-" })
                },
                ReportColumn("EXPIRY", ReportColWidth.Fixed(88.dp)) { row, _ ->
                    cellText(row.expiryDate.ifBlank { "-" })
                },
                ReportColumn("ROUNDING", ReportColWidth.Fixed(84.dp)) { row, _ ->
                    cellText(if (row.isResidual) "yes" else "")
                },
            ),
            data = detail.outputs,
            noDataMessage = "No output layers",
            scrollable = false,
        )

        Text(
            text = "Basis: every figure is the server's snapshot of the batch. A red note means " +
                "a source voucher has been edited or removed since the batch was posted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
