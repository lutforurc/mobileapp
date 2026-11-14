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
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.production.model.ProductionRegisterRow
import com.example.cashbookbd.ui.production.model.productionAmountText
import com.example.cashbookbd.ui.production.model.productionQtyText
import com.example.cashbookbd.ui.production.model.productionVarianceText
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportError
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Production Register (web `ProductionRegister.tsx`): one row per batch with
 * its material and allocated cost, the true gross profit summary the P&L cannot
 * show together, and a red flag when a source voucher has since moved.
 */
@Composable
fun ProductionRegisterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductionRegisterViewModel = viewModel(
        factory = ProductionRegisterViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)

    AuthenticatedShell(
        title = "Production Register",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateRangeFields(
                    start = state.fromDate,
                    end = state.toDate,
                    onStart = viewModel::onFrom,
                    onEnd = viewModel::onTo,
                    startLabel = "From",
                    endLabel = "To",
                )
                FilterActions(
                    onApply = viewModel::run,
                    onReset = null,
                    canApply = state.canRun,
                    isLoading = state.isLoading,
                    applyText = "Search",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.error != null -> ReportError(state.error!!, viewModel::run)
                state.isLoading -> ReportLoading()
                state.report == null -> ReportNote("Enter a date range and tap Search.")
                state.isEmptyResult -> ReportNote(
                    "No batch in this period (${state.appliedRange ?: "the selected range"})."
                )
                else -> RegisterContent(state, viewModel, navController)
            }
        }
    }
}

@Composable
private fun RegisterContent(
    state: ProductionRegisterUiState,
    viewModel: ProductionRegisterViewModel,
    navController: NavHostController,
) {
    val report = state.report ?: return
    val summary = report.summary
    val danger = MaterialTheme.appColors.danger
    val flagBg = danger.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Net Sales", productionAmountText(summary.netSales), Modifier.weight(1f))
            KpiTile(
                "Cost (with allocated)",
                productionAmountText(summary.costOfGoods),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(
                "True Gross Profit",
                productionAmountText(summary.trueGrossProfit),
                Modifier.weight(1f),
                accent = if (summary.trueGrossProfit < 0) danger else null,
            )
            KpiTile(
                "Abnormal Loss",
                productionAmountText(summary.abnormalLossTotal),
                Modifier.weight(1f),
            )
        }

        Text(
            text = "Batches · ${state.appliedRange.orEmpty()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.SemiBold,
        )

        ReportTable(
            columns = registerColumns(danger, navController),
            data = report.rows,
            noDataMessage = "No batch in this period",
            scrollable = false,
            rowBackground = { row, _ ->
                if (row.flagMessage != null || row.variance?.over == true) flagBg else null
            },
        )

        report.rows.filter { it.flagMessage != null }.takeIf { it.isNotEmpty() }?.let { flagged ->
            Text(
                text = "Some batches no longer match their source:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = danger,
            )
            flagged.forEach { row ->
                Text(
                    text = "${row.batchLabel}: ${row.flagMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = danger,
                )
            }
        }

        Text(
            text = "Basis: the true gross profit adds each batch's allocated expense to its " +
                "material cost. A red row has a source voucher that has since been edited or " +
                "removed. Printing stays on the web by policy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun registerColumns(
    danger: Color,
    navController: NavHostController,
): List<ReportColumn<ProductionRegisterRow>> = listOf(
    ReportColumn("BATCH", ReportColWidth.Fixed(130.dp)) { row, _ ->
        ReportTableCell.Slot {
            Column {
                Text(row.batchLabel, style = MaterialTheme.typography.bodySmall)
                if (row.voucherSuffix.isNotBlank()) {
                    Text(
                        "(${row.voucherSuffix})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    },
    ReportColumn("DATE", ReportColWidth.Fixed(84.dp)) { row, _ -> cellText(row.date.ifBlank { "-" }) },
    ReportColumn("OUTPUT", ReportColWidth.Fixed(150.dp)) { row, _ -> outputCell(row) },
    ReportColumn("QTY", ReportColWidth.Fixed(76.dp), TextAlign.End) { row, _ -> qtyCell(row) },
    ReportColumn("EXPIRY", ReportColWidth.Fixed(84.dp)) { row, _ ->
        if (row.outputs.size > 1) {
            ReportTableCell.Slot {
                Column {
                    row.outputs.forEach { output ->
                        Text(
                            output.expiryDate.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            cellText(row.outputs.firstOrNull()?.expiryDate.orEmpty().ifBlank { "-" })
        }
    },
    ReportColumn("MATERIAL", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
        cellText(productionAmountText(row.materialCost))
    },
    ReportColumn("ALLOCATED", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
        cellText(productionAmountText(row.allocatedCost))
    },
    ReportColumn("TRUE COST", ReportColWidth.Fixed(96.dp), TextAlign.End) { row, _ ->
        cellText(productionAmountText(row.trueCost), bold = true)
    },
    ReportColumn("UNIT COST", ReportColWidth.Fixed(92.dp), TextAlign.End) { row, _ ->
        if (row.outputs.size > 1) {
            ReportTableCell.Slot {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    row.outputs.forEach { output ->
                        Text(productionAmountText(output.unitCost), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            cellText(productionAmountText(row.unitCost))
        }
    },
    ReportColumn("LOSS", ReportColWidth.Fixed(92.dp)) { row, _ ->
        ReportTableCell.Slot {
            Column {
                Text(productionAmountText(row.lossActual), style = MaterialTheme.typography.bodySmall)
                if (row.lossAbnormal > 0) {
                    Text(
                        "(${productionAmountText(row.lossAbnormal)} abnormal)",
                        style = MaterialTheme.typography.labelSmall,
                        color = danger,
                    )
                }
            }
        }
    },
    ReportColumn("RECIPE", ReportColWidth.Fixed(110.dp)) { row, _ -> cellText(row.recipeLabel, maxLines = 2) },
    ReportColumn("VARIANCE", ReportColWidth.Fixed(132.dp)) { row, _ ->
        ReportTableCell.Slot {
            Column {
                Text(
                    productionVarianceText(row.variance),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (row.variance?.over == true) AppFontWeight.SemiBold else AppFontWeight.Normal,
                    color = if (row.variance?.over == true) danger else Color.Unspecified,
                    maxLines = 2,
                )
                if (row.variance?.lossOver == true) {
                    Text(
                        "loss ${productionAmountText(row.variance.lossActual)} over " +
                            productionAmountText(row.variance.lossAllowed),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
    },
    ReportColumn("STATUS", ReportColWidth.Fixed(100.dp)) { row, _ ->
        cellText(
            text = if (row.flagMessage != null) "source changed" else row.status.ifBlank { "-" },
            color = if (row.flagMessage != null) danger else Color.Unspecified,
            bold = row.flagMessage != null,
        )
    },
    ReportColumn("DETAIL", ReportColWidth.Fixed(72.dp), TextAlign.Center) { row, _ ->
        ReportTableCell.Slot {
            LinkButton(
                text = "Open",
                onClick = { navController.navigate(ProductionMenu.costing(row.id)) },
            )
        }
    },
)

private fun outputCell(row: ProductionRegisterRow): ReportTableCell =
    if (row.outputs.size > 1) {
        ReportTableCell.Slot {
            Column {
                row.outputs.forEach { output ->
                    Text(output.productName, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    } else {
        cellText(row.outputProduct.ifBlank { row.outputs.firstOrNull()?.productName.orEmpty().ifBlank { "-" } }, maxLines = 2)
    }

private fun qtyCell(row: ProductionRegisterRow): ReportTableCell =
    if (row.outputs.size > 1) {
        ReportTableCell.Slot {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                row.outputs.forEach { output ->
                    Text(productionQtyText(output.qty), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        cellText(productionQtyText(row.outputQty))
    }
