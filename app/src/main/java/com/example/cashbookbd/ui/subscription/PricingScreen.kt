package com.example.cashbookbd.ui.subscription

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.subscription.model.SubscriptionPlan
import java.text.DecimalFormat

private val amountFormat = DecimalFormat("#,##0.##")

/** Pricing: the purchasable plans as cards (price, trial, quotas, features). */
@Composable
fun PricingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = viewModel(
        factory = SubscriptionViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadPlans() }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Pricing",
        currentRoute = Routes.SUBSCRIPTION,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        when {
            state.isLoading -> Center { CircularProgressIndicator() }
            state.error != null -> Center {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(text = "Retry", onClick = viewModel::loadPlans)
                }
            }
            state.plans.isEmpty() -> Center {
                Text("No plans available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.plans) { PlanCard(it) }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: SubscriptionPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (plan.billingInterval.isNotBlank()) Pill(plan.billingInterval.replace('_', ' '))
            }
            Text(
                text = "${plan.currency} ${amountFormat.format(plan.price)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (plan.trialDays > 0) {
                Text("Trial: ${plan.trialDays} days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan.description.isNotBlank()) {
                Text(plan.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))

            QuotaRow("Employees", plan.maxEmployees)
            QuotaRow("Customers", plan.maxCustomers)
            QuotaRow("Products", plan.maxProducts)
            QuotaRow("Users", plan.maxUsers)
            QuotaRow("Branches", plan.maxBranches)
            QuotaRow("Transactions / month", plan.maxTransactionsPerMonth)
            if (plan.supportTime.isNotBlank()) {
                QuotaText("Support Time", plan.supportTime)
            }

            val features = plan.features.filter { it.enabled }
            if (features.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Included Features", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                features.forEach {
                    Text("• ${it.name}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A quota line; a null limit reads as "Unlimited" (matching the web). */
@Composable
private fun QuotaRow(label: String, value: Int?) = QuotaText(label, value?.toString() ?: "Unlimited")

@Composable
private fun QuotaText(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
