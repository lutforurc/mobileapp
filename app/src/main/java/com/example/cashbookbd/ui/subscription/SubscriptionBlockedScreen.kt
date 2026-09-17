package com.example.cashbookbd.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.DateFormat
import com.example.cashbookbd.data.remote.SubscriptionBlock
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.theme.muted

/**
 * What a lapsed company sees instead of the app.
 *
 * Covers everything rather than sitting on one screen, because the server now
 * refuses every call: without this the user would wander a working-looking app
 * meeting "Server error (403)" on each screen in turn.
 *
 * ⚠️ Two ways out and no third. Retry is for the case that matters most --
 * the payment has just been approved and the company is paid up again, so one
 * successful call should give the app straight back rather than making someone
 * force-quit it. Log out is for handing the phone to a different company.
 * There is deliberately no "continue anyway": there is nothing to continue to.
 */
@Composable
fun SubscriptionBlockedScreen(
    block: SubscriptionBlock,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    isRetrying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = block.planName?.let { "$it — subscription expired" } ?: "Subscription expired",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // The server's own sentence. Keeping the wording there means the
            // reason for a block -- lapsed, stopped by hand, never subscribed --
            // can be reworded without shipping a new APK.
            Text(
                text = block.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.muted(),
                textAlign = TextAlign.Center,
            )

            if (block.endDate != null || block.gracePeriodEndAt != null) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        block.endDate?.let {
                            Text(
                                text = "Plan ended: ${DateFormat.dayMonthYear(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.muted(),
                            )
                        }
                        block.gracePeriodEndAt?.let {
                            Text(
                                text = "Grace period ended: ${DateFormat.dayMonthYear(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.muted(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Your data is untouched. Everything comes back the moment the plan is renewed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.muted(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                text = "I have renewed — check again",
                onClick = onRetry,
                isLoading = isRetrying,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            SecondaryButton(
                text = "Log out",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
