package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.cashbookbd.ui.theme.Spacing
import com.example.cashbookbd.ui.theme.appColors

/**
 * The three things a screen shows while it has no content yet — working,
 * empty and failed.
 *
 * Reports, lists and forms each drew these by hand, so the spinner's colour, the
 * empty text's muted token and the retry button's type all varied screen to
 * screen, and six files re-declared the same private `Center`. These are the one
 * implementation; `ReportKit`'s old helpers now delegate here too.
 */

/** A centred spinner — the screen is loading. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** A centred, muted line — nothing to show (yet). */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.appColors.textOnScreenMuted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A centred error, with the shared filled Retry button when [onRetry] is given. */
@Composable
fun ErrorState(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (onRetry != null) {
            PrimaryButton(text = "Retry", onClick = onRetry)
        }
    }
}
