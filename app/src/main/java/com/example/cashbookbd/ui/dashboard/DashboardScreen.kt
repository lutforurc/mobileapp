package com.example.cashbookbd.ui.dashboard

import com.example.cashbookbd.ui.theme.PillShape
import com.example.cashbookbd.ui.theme.asTint
import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.R
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.DashboardKpi
import com.example.cashbookbd.data.repository.DashboardSummary
import com.example.cashbookbd.data.repository.DueAging
import com.example.cashbookbd.data.repository.LowStock
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.Sparkline
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.dashboard.model.ReceivedBranchGroup
import com.example.cashbookbd.ui.dashboard.model.ReceivedFromHo
import com.example.cashbookbd.ui.dashboard.model.TopProduct
import com.example.cashbookbd.ui.dashboard.model.previewDashboard
import com.example.cashbookbd.ui.theme.CashBookbdTheme
import com.example.cashbookbd.ui.theme.accents
import java.text.DecimalFormat

@Composable
fun DashboardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.provideFactory(LocalContext.current)
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbar) {
        val snackbar = uiState.snackbar
        if (snackbar != null) {
            snackbarHostState.showSnackbar(snackbar.message)
            viewModel.onSnackbarShown()
        }
    }

    // The user's dashboard layout (order/hidden/density), live like the web's.
    val widgetPrefs = viewModel.widgetPrefs?.collectAsStateWithLifecycle()?.value
        ?: com.example.cashbookbd.data.repository.DashboardPrefs()
    var showCustomize by remember { androidx.compose.runtime.mutableStateOf(false) }

    AuthenticatedShell(
        title = "Dashboard",
        currentRoute = Routes.HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = {
            // The web's Customize panel, as a dialog.
            IconButton(onClick = { showCustomize = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Customize dashboard")
            }
            IconButton(
                onClick = viewModel::refresh,
                enabled = !uiState.isLoading && !uiState.isRefreshing,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        if (showCustomize) {
            CustomizeDialog(
                widgets = viewModel.declaredWidgets(),
                prefs = widgetPrefs,
                onToggle = viewModel::toggleWidget,
                onMove = viewModel::moveWidget,
                onDensity = viewModel::setDensity,
                onReset = viewModel::resetLayout,
                onDismiss = { showCustomize = false },
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // First load with nothing to show yet.
                uiState.isLoading && uiState.dashboard == null -> LoadingState()

                // Hard error with no cached content.
                uiState.dashboard == null && uiState.errorMessage != null ->
                    ErrorState(message = uiState.errorMessage!!, onRetry = viewModel::load)

                uiState.dashboard != null ->
                    DashboardContent(
                        dashboard = uiState.dashboard!!,
                        summary = uiState.summary,
                        prefs = widgetPrefs,
                        isRefreshing = uiState.isRefreshing,
                        isConstruction = uiState.isConstruction,
                        rowActions = uiState.rowActions,
                        onReceive = viewModel::onReceive,
                    )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
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
private fun DashboardContent(
    dashboard: Dashboard,
    summary: DashboardSummary?,
    prefs: com.example.cashbookbd.data.repository.DashboardPrefs,
    isRefreshing: Boolean,
    isConstruction: Boolean,
    rowActions: Map<Int, RowActionState>,
    onReceive: (ReceivedFromHo) -> Unit,
) {
    // The user's saved order over the widgets this dashboard has; the web's
    // Compact density tightens the gaps.
    val declared = if (isConstruction) {
        listOf("summary", "top-purchase", "receive-details")
    } else {
        listOf("summary", "due-aging", "low-stock", "top-sales", "top-purchase")
    }
    val ordered = com.example.cashbookbd.data.repository.applyMenuOrder(
        declared, prefs.order.filter { it != "kpi-row" },
    )
    val compact = prefs.density == com.example.cashbookbd.data.repository.DashboardPrefs.DENSITY_COMPACT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        if (isRefreshing) {
            item {
                LinearRefreshHint()
            }
        }

        // The web's KPI row sits above the widget grid — only its hidden flag
        // counts, its saved order position is ignored.
        if (!isConstruction && summary != null && "kpi-row" !in prefs.hidden) {
            item { KpiRow(summary) }
        }

        ordered.forEach { id ->
            if (id in prefs.hidden) return@forEach
            when (id) {
                "summary" -> item { SummaryCard(dashboard, summary) }

                "due-aging" -> summary?.dueAging?.let { item { DueAgingCard(it) } }

                "low-stock" -> summary?.lowStock?.let { item { LowStockCard(it) } }

                "top-sales" -> if (dashboard.topSales.isNotEmpty()) {
                    item {
                        TopProductsCard(
                            title = "Top Sales",
                            products = dashboard.topSales,
                            days = dashboard.topPurchaseDays,
                            accent = MaterialTheme.appColors.success,
                            showTotal = true,
                            periodPrefix = "",
                        )
                    }
                }

                "top-purchase" -> if (dashboard.topPurchases.isNotEmpty()) {
                    item {
                        TopProductsCard(
                            title = "Top Purchase",
                            products = dashboard.topPurchases,
                            days = dashboard.topPurchaseDays,
                            accent = MaterialTheme.accents.amber,
                            // Construction shows it without a Total, "Last N days".
                            showTotal = !isConstruction,
                            periodPrefix = if (isConstruction) "Last " else "",
                        )
                    }
                }

                "receive-details" -> if (dashboard.receivedGroups.any { it.rows.isNotEmpty() }) {
                    item {
                        ReceivedFromHoPanel(
                            title = dashboard.receiveDetailsTitle,
                            total = dashboard.receivedTotal,
                            groups = dashboard.receivedGroups,
                            rowActions = rowActions,
                            onReceive = onReceive,
                        )
                    }
                }
            }
        }
    }
}

/** The web's widget titles, id for id. */
private fun widgetTitle(id: String): String = when (id) {
    "kpi-row" -> "Today at a Glance"
    "summary" -> "Balance Summary"
    "due-aging" -> "Receivable Ageing"
    "low-stock" -> "Low Stock"
    "top-sales" -> "Top Sales Products"
    "top-purchase" -> "Top Purchase Products"
    "receive-details" -> "Receive Details"
    else -> id
}

/**
 * The web's Customize panel as a dialog: per widget an eye toggle and
 * up/down movers, the Expanded/Compact density pair, and Reset Layout.
 * Every change saves as it is made — there is nothing else to press.
 */
@Composable
private fun CustomizeDialog(
    widgets: List<String>,
    prefs: com.example.cashbookbd.data.repository.DashboardPrefs,
    onToggle: (String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onDensity: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = com.example.cashbookbd.data.repository.applyMenuOrder(widgets, prefs.order)
    val compact = prefs.density == com.example.cashbookbd.data.repository.DashboardPrefs.DENSITY_COMPACT
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Density",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    com.example.cashbookbd.ui.components.SecondaryButton(
                        text = if (compact) "Compact ✓" else "Compact",
                        onClick = { onDensity(!compact) },
                        compact = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ordered.forEachIndexed { index, id ->
                    val hidden = id in prefs.hidden
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = widgetTitle(id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hidden) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMove(id, true) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMove(id, false) },
                            enabled = index < ordered.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        androidx.compose.material3.TextButton(onClick = { onToggle(id) }) {
                            Text(if (hidden) "Show" else "Hide")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                com.example.cashbookbd.ui.components.SecondaryButton(
                    text = "Reset Layout",
                    onClick = onReset,
                    compact = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun LinearRefreshHint() {
    Text(
        text = "Refreshing…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.appColors.textOnScreenMuted,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/**
 * Branch summary card: header with the branch name, then a stat row per metric
 * (each with a tinted icon chip), and a "last updated" footer strip.
 */
@Composable
private fun SummaryCard(dashboard: Dashboard, summary: DashboardSummary? = null) {
    val accents = MaterialTheme.accents
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dashboard.branchName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = AppFontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconChip(icon = Icons.Filled.Place, tint = accents.blue)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SummaryStatRow(
                iconRes = R.drawable.ic_stat_trx_date,
                tint = accents.blue,
                label = "TRANSACTION DATE",
                value = dashboard.transactionDate,
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryStatRow(
                iconRes = R.drawable.ic_stat_received,
                tint = MaterialTheme.appColors.success,
                label = "TODAY RECEIVED",
                value = formatMoney(dashboard.todayReceived),
                valueColor = MaterialTheme.colorScheme.onSurface,
                spark = summary?.kpis?.get("received")?.spark,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryStatRow(
                iconRes = R.drawable.ic_stat_payment,
                tint = accents.red,
                label = "TODAY PAYMENT",
                value = formatMoney(dashboard.todayPayment),
                valueColor = accents.red,
                spark = summary?.kpis?.get("payment")?.spark,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryStatRow(
                iconRes = R.drawable.ic_stat_balance,
                tint = accents.purple,
                label = "BALANCE",
                value = formatMoney(dashboard.balance),
                valueColor = accents.blue,
                spark = summary?.kpis?.get("balance")?.spark,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Last updated: ${dashboard.lastUpdate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The web's "Today at a glance" band: four KPI tiles in a 2×2 grid (the web's
 * responsive grid collapses to one column on phones; two-by-two keeps the
 * glance on one screen). Deltas compare with the previous day in muted ink —
 * deliberately no green/red. Tile hues are the web's own chart colours.
 */
@Composable
private fun KpiRow(summary: DashboardSummary) {
    val tiles = listOf(
        KpiTileSpec("Today Sales", "sales", Color(0xFF14B8A6), money = true),
        KpiTileSpec("Today Purchase", "purchase", Color(0xFFF59E0B), money = true),
        KpiTileSpec("New Customers", "newCustomers", Color(0xFF06B6D4), money = false),
        KpiTileSpec("Today Vouchers", "vouchers", Color(0xFF64748B), money = false),
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today at a glance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Trx Date: ${isoToDisplay(summary.trxDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "change vs previous day · sparkline last 14 days",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        tiles.chunked(2).forEachIndexed { rowIndex, pair ->
            if (rowIndex > 0) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { spec ->
                    Box(modifier = Modifier.weight(1f)) {
                        KpiTile(spec = spec, kpi = summary.kpis[spec.key])
                    }
                }
            }
        }
    }
}

private data class KpiTileSpec(
    val label: String,
    val key: String,
    val hue: Color,
    val money: Boolean,
)

@Composable
private fun KpiTile(spec: KpiTileSpec, kpi: DashboardKpi?) {
    kpi ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = kpiValue(kpi.value, spec.money),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
            )
            // The web's delta: previous == 0 answers "—", never ∞; the glyph
            // says the direction, the ink stays muted either way.
            val delta = if (kpi.previous == 0.0) null
            else (kpi.value - kpi.previous) / kotlin.math.abs(kpi.previous) * 100
            Text(
                text = when {
                    delta == null -> "—"
                    delta > 0 -> "▲ ${kotlin.math.abs(delta).toInt()}%"
                    delta < 0 -> "▼ ${kotlin.math.abs(delta).toInt()}%"
                    else -> "— 0%"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (kpi.spark.size > 1) {
                Spacer(Modifier.height(6.dp))
                Sparkline(
                    values = kpi.spark,
                    color = spec.hue,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
            }
        }
    }
}

/** KPI figures round first; a rounded zero prints "0" (not the dash). */
private fun kpiValue(value: Double, money: Boolean): String {
    val rounded = Math.round(value)
    return when {
        rounded == 0L -> "0"
        money -> formatMoney(rounded.toDouble())
        else -> rounded.toString()
    }
}

/** Ageing/stock money: rounded, lakh-grouped, zero as "-" (the web's rule). */
private fun dashMoney(value: Double): String {
    val rounded = Math.round(value)
    return if (rounded == 0L) "-" else formatMoney(rounded.toDouble())
}

/** "2026-08-05" → "05/08/2026". A malformed date passes through unchanged. */
private fun isoToDisplay(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

/** The web's Receivable Ageing card: outstanding, four buckets, advance strip. */
@Composable
private fun DueAgingCard(aging: DueAging) {
    // The web's single-hue amber ramp — older money, darker bar.
    val ramp = listOf(Color(0xFFFCD34D), Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFB45309))
    val maxBucket = aging.buckets.maxOfOrNull { it.amount } ?: 0.0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Receivable Ageing",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Outstanding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dashMoney(aging.total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = AppFontWeight.Bold,
            )
            Text(
                text = "across ${aging.parties} ${if (aging.parties == 1) "party" else "parties"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            aging.buckets.forEachIndexed { index, bucket ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${bucket.label} days",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(72.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dashMoney(bucket.amount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = AppFontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        // Scaled to the largest bucket; a non-zero bucket keeps
                        // a 2% floor so it never disappears.
                        val fraction = when {
                            maxBucket <= 0.0 || bucket.amount <= 0.0 -> 0f
                            else -> (bucket.amount / maxBucket).toFloat().coerceAtLeast(0.02f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape),
                        ) {
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(6.dp)
                                        .background(ramp[index % ramp.size], AppShape),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${bucket.parties} ${if (bucket.parties == 1) "party" else "parties"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
            if (aging.advance > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Advance received",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = dashMoney(aging.advance),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = AppFontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The web's low-stock card: reorder candidates, or the lowest stock instead. */
@Composable
private fun LowStockCard(lowStock: LowStock) {
    val orderLevelMode = lowStock.mode == "order_level"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (orderLevelMode) "Reorder Now" else "Lowest Stock",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (lowStock.items.isEmpty()) {
                Text(
                    text = "Nothing below its reorder level",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                lowStock.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.stock < 0) {
                            // Issued more than received — check entries.
                            Text(
                                text = "⚠",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.accents.red,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        Text(
                            text = dashMoney(item.stock),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = AppFontWeight.SemiBold,
                            color = if (item.stock < 0) MaterialTheme.accents.red else Color.Unspecified,
                        )
                        if (orderLevelMode) {
                            Text(
                                text = " / ${formatAmount(item.orderLevel)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!orderLevelMode) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "No reorder levels set — showing lowest stock instead",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A small tinted rounded-square icon badge. */
@Composable
private fun IconChip(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(tint.asTint(), AppShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * One metric row: a tinted icon chip, an uppercase label and its value.
 *
 * The icon is a drawable rather than a Material [ImageVector] because these
 * four are a drawn set (`res/drawable/ic_stat_*`) — Material's stock arrows and
 * a text glyph never matched each other's weight.
 */
@Composable
private fun SummaryStatRow(
    @DrawableRes iconRes: Int,
    tint: Color,
    label: String,
    value: String,
    valueColor: Color,
    /** The row's last-14-days series; drawn beside the figure when present. */
    spark: List<Double>? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                // A neutral chip with the accent kept on the glyph: a translucent
                // wash of [tint] only works over a light card, and goes muddy on
                // the dark one.
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = if (spark != null && spark.size > 1) Modifier else Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
                color = valueColor,
            )
        }
        if (spark != null && spark.size > 1) {
            Spacer(Modifier.width(12.dp))
            // The web gives the curve the leftover width at 64px tall, in the
            // row's own colour.
            Sparkline(
                values = spark,
                color = tint,
                modifier = Modifier.weight(1f).height(64.dp),
            )
        }
    }
}

/**
 * Top Purchase card: header with a period badge, then a numbered row per product
 * (blue serial, name, amber quantity).
 *
 * Callers only render this when [products] has something in it — an empty card
 * is just a heading over a blank space — so there is no empty state here.
 */
@Composable
private fun TopProductsCard(
    title: String,
    products: List<TopProduct>,
    days: Int,
    accent: Color,
    /** Web's ComputerAccessories lists end with a Total row; Construction's doesn't. */
    showTotal: Boolean,
    /** "7 Days" on the normal dashboard, "Last 7 Days" on Construction. */
    periodPrefix: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = AppFontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val periodLabel = if (days <= 1) "Today" else "$periodPrefix$days Days"
                Box(
                    modifier = Modifier
                        .background(accent.asTint(), PillShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = accent,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            products.forEachIndexed { index, product ->
                TopProductRow(serial = index + 1, product = product, accent = accent)
                if (index != products.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (showTotal) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.Bold)
                    Text(
                        text = formatBdAmount(products.sumOf { it.quantity }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.Bold,
                        color = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopProductRow(serial: Int, product: TopProduct, accent: Color) {
    val accents = MaterialTheme.accents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = serial.toString().padStart(2, '0'),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = AppFontWeight.Bold,
            color = accents.blue,
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatAmount(product.quantity),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = AppFontWeight.Bold,
            color = accent,
        )
    }
}

/**
 * The "Received Details from H/O" panel: a single card with a total badge in the
 * header, then every branch's rows listed under it, with a confirmation check
 * per row. Mirrors the web `card-received-ho` panel.
 */
@Composable
private fun ReceivedFromHoPanel(
    title: String,
    total: Double,
    groups: List<ReceivedBranchGroup>,
    rowActions: Map<Int, RowActionState>,
    onReceive: (ReceivedFromHo) -> Unit,
) {
    val accents = MaterialTheme.accents
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.Bold,
                    color = accents.rose,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = accents.blue.asTint(),
                            shape = PillShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Tk. ${formatMoney(total)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = AppFontWeight.Bold,
                        color = accents.blue,
                    )
                }
            }

            // The panel is only rendered when it has rows (see DashboardContent).
            groups.forEach { group ->
                // No per-branch subtotal strip — the panel header already carries the total.
                group.rows.forEachIndexed { index, row ->
                    val action = rowActions[row.mtmId] ?: RowActionState()
                    ReceivedRow(
                        serial = index + 1,
                        row = row,
                        // Effective status = server's initial flag OR confirmed this session.
                        processed = row.confirmed || action.processedLocally,
                        inFlight = action.inFlight,
                        onReceive = { onReceive(row) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ReceivedRow(
    serial: Int,
    row: ReceivedFromHo,
    processed: Boolean,
    inFlight: Boolean,
    onReceive: () -> Unit,
) {
    val accents = MaterialTheme.accents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = serial.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.voucherNo.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
            )
            if (row.date.isNotBlank()) {
                Text(
                    text = row.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = formatMoney(row.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = AppFontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp),
        )
        // Trailing action slot: processed = check, in-flight = spinner, else = receive button.
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                processed -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Received",
                    tint = MaterialTheme.appColors.success,
                    modifier = Modifier.size(22.dp),
                )

                inFlight -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accents.red,
                )

                // Red framed arrow — the whole box is the tap target (mirrors the
                // web's "receive remittance" submit action).
                else -> Box(
                    modifier = Modifier
                        .clip(AppShape)
                        .border(1.dp, accents.red, AppShape)
                        .clickable(onClick = onReceive)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Receive this remittance",
                        tint = accents.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// Quantity, not money — a product count keeps up to two decimals if present and
// never picks up the branch's money decimal places (a count of "3" must not read
// as "3.00").
private val amountFormat = DecimalFormat("#,##0.##")

private fun formatAmount(value: Double): String = amountFormat.format(value)

/**
 * A money figure in the dashboard's lakh style. Keeps the Bangladeshi grouping
 * the panel is designed around, but takes the fraction from the branch's
 * decimal_places via [AmountFormat] — so it agrees with every other transaction
 * amount in the app. e.g. decimal_places=2 -> "2,38,807.00", =0 -> "2,38,807".
 */
private fun formatMoney(value: Double): String {
    val western = AmountFormat.format(value)          // truncated + grouped, western
    val negative = western.startsWith("-")
    val body = western.removePrefix("-")
    val dot = body.indexOf('.')
    val intPart = (if (dot >= 0) body.substring(0, dot) else body).replace(",", "")
    val fraction = if (dot >= 0) body.substring(dot) else ""   // keeps the "."
    val grouped = groupLakh(intPart) + fraction
    return if (negative) "-$grouped" else grouped
}

/**
 * Bangladeshi/Indian lakh grouping (e.g. 1540400 -> "15,40,400"): the rightmost
 * three digits, then groups of two. Whole numbers, for the top-products count.
 */
private fun formatBdAmount(value: Double): String {
    val whole = Math.round(Math.abs(value)).toString()
    val grouped = groupLakh(whole)
    return if (value < 0) "-$grouped" else grouped
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

@Preview(showBackground = true, name = "Dashboard · Light")
@Composable
private fun DashboardContentPreview() {
    CashBookbdTheme(darkTheme = false) {
        DashboardContent(
            dashboard = previewDashboard,
            summary = null,
            prefs = com.example.cashbookbd.data.repository.DashboardPrefs(),
            isRefreshing = false,
            isConstruction = false,
            rowActions = emptyMap(),
            onReceive = {},
        )
    }
}

@Preview(
    showBackground = true,
    name = "Dashboard · Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DashboardContentDarkPreview() {
    CashBookbdTheme(darkTheme = true) {
        DashboardContent(
            dashboard = previewDashboard,
            summary = null,
            prefs = com.example.cashbookbd.data.repository.DashboardPrefs(),
            isRefreshing = false,
            isConstruction = false,
            rowActions = emptyMap(),
            onReceive = {},
        )
    }
}