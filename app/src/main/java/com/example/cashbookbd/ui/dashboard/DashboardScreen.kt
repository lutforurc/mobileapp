package com.example.cashbookbd.ui.dashboard

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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.R
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.ui.components.PrimaryButton
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

    AuthenticatedShell(
        title = "Dashboard",
        currentRoute = Routes.HOME,
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
                // First load with nothing to show yet.
                uiState.isLoading && uiState.dashboard == null -> LoadingState()

                // Hard error with no cached content.
                uiState.dashboard == null && uiState.errorMessage != null ->
                    ErrorState(message = uiState.errorMessage!!, onRetry = viewModel::load)

                uiState.dashboard != null ->
                    DashboardContent(
                        dashboard = uiState.dashboard!!,
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
    isRefreshing: Boolean,
    isConstruction: Boolean,
    rowActions: Map<Int, RowActionState>,
    onReceive: (ReceivedFromHo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isRefreshing) {
            item {
                LinearRefreshHint()
            }
        }

        item { SummaryCard(dashboard) }

        if (isConstruction) {
            // Construction: Top Purchase only, no Total row — then the
            // head-office receive panel.
            if (dashboard.topPurchases.isNotEmpty()) {
                item {
                    TopProductsCard(
                        title = "Top Purchase",
                        products = dashboard.topPurchases,
                        days = dashboard.topPurchaseDays,
                        accent = MaterialTheme.accents.amber,
                        showTotal = false,
                        periodPrefix = "Last ",
                    )
                }
            }
            // Hide the whole H/O panel when there's nothing to receive.
            if (dashboard.receivedGroups.any { it.rows.isNotEmpty() }) {
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
        } else {
            // Everything else: Top Sales + Top Purchase, each with a Total row.
            if (dashboard.topSales.isNotEmpty()) {
                item {
                    TopProductsCard(
                        title = "Top Sales",
                        products = dashboard.topSales,
                        days = dashboard.topPurchaseDays,
                        accent = MaterialTheme.accents.green,
                        showTotal = true,
                        periodPrefix = "",
                    )
                }
            }
            if (dashboard.topPurchases.isNotEmpty()) {
                item {
                    TopProductsCard(
                        title = "Top Purchase",
                        products = dashboard.topPurchases,
                        days = dashboard.topPurchaseDays,
                        accent = MaterialTheme.accents.amber,
                        showTotal = true,
                        periodPrefix = "",
                    )
                }
            }
        }
    }
}

@Composable
private fun LinearRefreshHint() {
    Text(
        text = "Refreshing…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/**
 * Branch summary card: header with the branch name, then a stat row per metric
 * (each with a tinted icon chip), and a "last updated" footer strip.
 */
@Composable
private fun SummaryCard(dashboard: Dashboard) {
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
                    fontWeight = FontWeight.Bold,
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
                tint = accents.green,
                label = "TODAY RECEIVED",
                value = formatMoney(dashboard.todayReceived),
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryStatRow(
                iconRes = R.drawable.ic_stat_payment,
                tint = accents.red,
                label = "TODAY PAYMENT",
                value = formatMoney(dashboard.todayPayment),
                valueColor = accents.red,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryStatRow(
                iconRes = R.drawable.ic_stat_balance,
                tint = accents.purple,
                label = "BALANCE",
                value = formatMoney(dashboard.balance),
                valueColor = accents.blue,
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

/** A small tinted rounded-square icon badge. */
@Composable
private fun IconChip(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
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
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
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
        Column(modifier = Modifier.weight(1f)) {
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
                fontWeight = FontWeight.Bold,
                color = valueColor,
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val periodLabel = if (days <= 1) "Today" else "$periodPrefix$days Days"
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
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
                    Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatBdAmount(products.sumOf { it.quantity }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
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
            fontWeight = FontWeight.Bold,
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
            fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.Bold,
                    color = accents.rose,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = accents.blue.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Tk. ${formatMoney(total)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
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
                fontWeight = FontWeight.SemiBold,
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
            fontWeight = FontWeight.Bold,
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
                    tint = accents.green,
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
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, accents.red, RoundedCornerShape(6.dp))
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
            isRefreshing = false,
            isConstruction = false,
            rowActions = emptyMap(),
            onReceive = {},
        )
    }
}