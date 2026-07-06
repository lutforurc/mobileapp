package com.example.cashbookbd.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.dashboard.model.ReceivedFromHo
import com.example.cashbookbd.ui.dashboard.model.TopPurchase
import com.example.cashbookbd.ui.dashboard.model.previewDashboard
import com.example.cashbookbd.ui.theme.CashBookbdTheme
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
        CircularProgressIndicator()
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
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    isRefreshing: Boolean,
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

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Today Received",
                    value = formatAmount(dashboard.todayReceived),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Today Payment",
                    value = formatAmount(dashboard.todayPayment),
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            StatTile(
                label = "Balance",
                value = formatAmount(dashboard.balance),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SectionCard(title = "Top Purchases (${dashboard.topPurchaseDays} days)") {
                if (dashboard.topPurchases.isEmpty()) {
                    EmptyRow("No purchases in this period.")
                } else {
                    dashboard.topPurchases.forEachIndexed { index, product ->
                        TopPurchaseRow(product)
                        if (index != dashboard.topPurchases.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = dashboard.receiveDetailsTitle) {
                if (dashboard.receivedFromHeadOffice.isEmpty()) {
                    EmptyRow("No records for this month.")
                }
            }
        }

        items(dashboard.receivedFromHeadOffice) { row ->
            ReceivedFromHoCard(row)
        }
    }
}

@Composable
private fun LinearRefreshHint() {
    Text(
        text = "Refreshing…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SummaryCard(dashboard: Dashboard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = dashboard.branchName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            LabeledValue("Transaction date", dashboard.transactionDate)
            Spacer(Modifier.height(2.dp))
            LabeledValue("Last updated", dashboard.lastUpdate)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(0.dp))
        Text(
            text = "  $value",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TopPurchaseRow(product: TopPurchase) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Qty: ${formatAmount(product.quantity)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReceivedFromHoCard(row: ReceivedFromHo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.voucherNo.ifBlank { "Voucher —" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = row.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatAmount(row.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (row.remarks.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyRow(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private val amountFormat = DecimalFormat("#,##0.##")

private fun formatAmount(value: Double): String = amountFormat.format(value)

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    CashBookbdTheme {
        DashboardContent(dashboard = previewDashboard, isRefreshing = false)
    }
}