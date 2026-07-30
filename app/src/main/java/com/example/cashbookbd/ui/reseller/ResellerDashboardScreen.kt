package com.example.cashbookbd.ui.reseller

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reseller.model.ResellerClient
import com.example.cashbookbd.ui.reseller.model.ResellerCommissionRow
import com.example.cashbookbd.ui.reseller.model.ResellerDashboard
import com.example.cashbookbd.ui.reseller.model.ResellerOverview
import com.example.cashbookbd.ui.reseller.model.ResellerPaymentRow
import com.example.cashbookbd.ui.reseller.model.previewResellerDashboard
import com.example.cashbookbd.ui.theme.CashBookbdTheme
import com.example.cashbookbd.ui.theme.accents

@Composable
fun ResellerDashboardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResellerDashboardViewModel = viewModel(
        factory = ResellerDashboardViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Expired/rejected token -> back to login (consume the one-shot flag once).
    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onSessionExpired()
        }
    }

    AuthenticatedShell(
        title = "Reseller Dashboard",
        currentRoute = Routes.RESELLER_DASHBOARD,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = viewModel::refresh,
                enabled = !uiState.isLoading && !uiState.isRefreshing,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.dashboard == null -> LoadingState()

                uiState.dashboard == null && uiState.errorMessage != null ->
                    ErrorState(message = uiState.errorMessage!!, onRetry = viewModel::load)

                uiState.dashboard != null ->
                    ResellerDashboardContent(
                        dashboard = uiState.dashboard!!,
                        isRefreshing = uiState.isRefreshing,
                    )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun ResellerDashboardContent(
    dashboard: ResellerDashboard,
    isRefreshing: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isRefreshing) {
            item {
                Text(
                    text = "Refreshing…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item { KpiGrid(dashboard.overview) }

        item {
            DataTableCard(
                title = "My Clients",
                count = dashboard.clients.size,
                columns = listOf(
                    TableCol("Company", 2.2f),
                    TableCol("Plan", 1.6f),
                    TableCol("Subscription", 1.5f),
                    TableCol("Access", 1.2f),
                ),
                rows = dashboard.clients.map {
                    listOf(it.company, it.plan, it.subscription, it.access)
                },
                emptyText = "No client assigned yet.",
            )
        }

        item {
            DataTableCard(
                title = "Commission History",
                count = dashboard.commissionHistory.size,
                columns = listOf(
                    TableCol("Company", 2f),
                    TableCol("Payment", 1.3f),
                    TableCol("Amount", 1.5f, alignEnd = true),
                    TableCol("Commission", 1.6f, alignEnd = true, emphasize = true),
                ),
                rows = dashboard.commissionHistory.map {
                    listOf(it.company, it.payment, money(it.amount, it.currency), money(it.commission, it.currency))
                },
                emptyText = "No payment commission found.",
            )
        }

        item {
            PaymentDetailsCard(rows = dashboard.paymentDetails)
        }
    }
}

/* --------------------------------- KPI tiles -------------------------------- */

@Composable
private fun KpiGrid(overview: ResellerOverview) {
    val accents = MaterialTheme.accents
    val tiles = listOf(
        KpiTileData("Clients", overview.clients.toString(), accents.blue),
        KpiTileData("Approved Payments", overview.approvedPayments.toString(), accents.purple),
        KpiTileData("Approved Amount", formatMoney(overview.approvedAmount), accents.green),
        KpiTileData("Commission", formatMoney(overview.commission), accents.amber),
        KpiTileData("Paid", formatMoney(overview.paid), accents.rose),
        KpiTileData("Payable", formatMoney(overview.payable), accents.red),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile ->
                    KpiTile(tile = tile, modifier = Modifier.weight(1f))
                }
                // Keep a lone tile half-width so the grid stays aligned.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class KpiTileData(val label: String, val value: String, val accent: Color)

@Composable
private fun KpiTile(tile: KpiTileData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = tile.accent.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = tile.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tile.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------- Generic table ------------------------------ */

private data class TableCol(
    val title: String,
    val weight: Float,
    val alignEnd: Boolean = false,
    /** Emphasised cells (bold + accent) — used for the commission column. */
    val emphasize: Boolean = false,
)

@Composable
private fun DataTableCard(
    title: String,
    count: Int,
    columns: List<TableCol>,
    rows: List<List<String>>,
    emptyText: String,
) {
    val accent = MaterialTheme.accents.green
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader(title = title, count = count)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Column header strip.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                columns.forEach { col ->
                    Text(
                        text = col.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(col.weight),
                        textAlign = if (col.alignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (rows.isEmpty()) {
                EmptyRow(emptyText)
            } else {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        columns.forEachIndexed { ci, col ->
                            Text(
                                text = row.getOrElse(ci) { "" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (col.emphasize) FontWeight.Bold else FontWeight.Normal,
                                color = if (col.emphasize) accent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(col.weight),
                                textAlign = if (col.alignEnd) TextAlign.End else TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/* --------------------------- Payment Details (stacked) ---------------------- */

@Composable
private fun PaymentDetailsCard(rows: List<ResellerPaymentRow>) {
    val accents = MaterialTheme.accents
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader(title = "Payment Details", count = rows.size)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (rows.isEmpty()) {
                EmptyRow("No reseller payment found.")
            } else {
                rows.forEachIndexed { index, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.method,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = money(row.amount, row.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = accents.green,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = row.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.account != "—") DetailLine("Account", row.account)
                        if (row.reference != "—") DetailLine("Reference", row.reference)
                        if (row.notes.isNotBlank()) DetailLine("Notes", row.notes)
                    }
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Spacer(Modifier.height(2.dp))
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* --------------------------------- Shared bits ------------------------------ */

@Composable
private fun SectionHeader(title: String, count: Int) {
    val accent = MaterialTheme.accents.blue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    )
}

/* --------------------------------- Formatting ------------------------------- */

/** A money figure with a currency suffix when it isn't the default BDT. */
private fun money(value: Double, currency: String): String {
    val amount = formatMoney(value)
    return if (currency.isBlank() || currency.equals("BDT", ignoreCase = true)) amount else "$amount $currency"
}

/**
 * A money figure in the Bangladeshi lakh style — the rightmost three digits, then
 * groups of two — taking its fraction from the branch's decimal places via
 * [AmountFormat] so it agrees with every other amount in the app.
 */
private fun formatMoney(value: Double): String {
    val western = AmountFormat.format(value)
    val negative = western.startsWith("-")
    val body = western.removePrefix("-")
    val dot = body.indexOf('.')
    val intPart = (if (dot >= 0) body.substring(0, dot) else body).replace(",", "")
    val fraction = if (dot >= 0) body.substring(dot) else ""
    val grouped = groupLakh(intPart) + fraction
    return if (negative) "-$grouped" else grouped
}

private fun groupLakh(digits: String): String {
    if (digits.length <= 3) return digits
    val last3 = digits.substring(digits.length - 3)
    var rest = digits.substring(0, digits.length - 3)
    val sb = StringBuilder()
    while (rest.length > 2) {
        sb.insert(0, "," + rest.substring(rest.length - 2))
        rest = rest.substring(0, rest.length - 2)
    }
    if (rest.isNotEmpty()) sb.insert(0, rest)
    return "$sb,$last3"
}

/* --------------------------------- Previews --------------------------------- */

@Preview(showBackground = true, name = "Reseller · Light")
@Composable
private fun ResellerDashboardPreview() {
    CashBookbdTheme(darkTheme = false) {
        ResellerDashboardContent(dashboard = previewResellerDashboard, isRefreshing = false)
    }
}

@Preview(showBackground = true, name = "Reseller · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResellerDashboardDarkPreview() {
    CashBookbdTheme(darkTheme = true) {
        ResellerDashboardContent(dashboard = previewResellerDashboard, isRefreshing = false)
    }
}
