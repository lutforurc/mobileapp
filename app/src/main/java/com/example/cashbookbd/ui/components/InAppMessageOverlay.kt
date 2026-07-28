package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.cashbookbd.inappmessage.InAppLayout
import com.example.cashbookbd.inappmessage.InAppMessage

/**
 * Renders one admin-authored pop-up campaign over the current screen.
 *
 * The web renders the same five layouts from the same payload, so a campaign
 * composed once looks the same in both places. Colours are optional: a campaign
 * that sets none simply inherits the app theme.
 */
@Composable
fun InAppMessageOverlay(
    message: InAppMessage,
    onShown: (InAppMessage) -> Unit,
    onPrimary: (InAppMessage) -> Unit,
    onSecondary: (InAppMessage) -> Unit,
    onDismiss: (InAppMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One impression per campaign, recorded the moment it reaches the screen.
    LaunchedEffect(message.id) { onShown(message) }

    // require_ack means the only way out is the campaign's own button.
    val closable = !message.requireAck

    when (message.layout) {
        InAppLayout.BANNER_TOP, InAppLayout.BANNER_BOTTOM ->
            BannerMessage(message, closable, onPrimary, onSecondary, onDismiss, modifier)

        InAppLayout.IMAGE_ONLY ->
            DialogFrame(message, closable, onDismiss) {
                Box {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = message.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPrimary(message) },
                    )
                }
            }

        InAppLayout.CARD ->
            DialogFrame(message, closable, onDismiss) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MessageText(message)
                        Spacer(Modifier.height(12.dp))
                        MessageButtons(message, onPrimary, onSecondary)
                    }
                }
            }

        InAppLayout.MODAL ->
            DialogFrame(message, closable, onDismiss) {
                Column {
                    if (!message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(20.dp)) {
                        MessageText(message)
                        Spacer(Modifier.height(16.dp))
                        MessageButtons(message, onPrimary, onSecondary)
                    }
                }
            }
    }
}

/** Centre pop-up shell shared by MODAL, CARD and IMAGE_ONLY. */
@Composable
private fun DialogFrame(
    message: InAppMessage,
    closable: Boolean,
    onDismiss: (InAppMessage) -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (closable) onDismiss(message) },
        properties = DialogProperties(
            dismissOnBackPress = closable,
            dismissOnClickOutside = closable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = message.bgColor.toColorOr(MaterialTheme.colorScheme.surface),
                    contentColor = message.textColor.toColorOr(MaterialTheme.colorScheme.onSurface),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            }

            if (closable) {
                IconButton(
                    onClick = { onDismiss(message) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close message",
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

/** Strip pinned to the top or bottom of the screen; never blocks the content. */
@Composable
private fun BannerMessage(
    message: InAppMessage,
    closable: Boolean,
    onPrimary: (InAppMessage) -> Unit,
    onSecondary: (InAppMessage) -> Unit,
    onDismiss: (InAppMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment =
        if (message.layout == InAppLayout.BANNER_TOP) Alignment.TopCenter else Alignment.BottomCenter

    Box(modifier = modifier.fillMaxSize()) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = message.bgColor.toColorOr(MaterialTheme.colorScheme.surfaceVariant),
                contentColor = message.textColor.toColorOr(MaterialTheme.colorScheme.onSurfaceVariant),
            ),
            modifier = Modifier
                .align(alignment)
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (!message.body.isNullOrBlank()) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                }
                MessageButtons(message, onPrimary, onSecondary, compact = true)
                if (closable) {
                    IconButton(onClick = { onDismiss(message) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close message")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageText(message: InAppMessage) {
    Text(
        text = message.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    if (!message.body.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageButtons(
    message: InAppMessage,
    onPrimary: (InAppMessage) -> Unit,
    onSecondary: (InAppMessage) -> Unit,
    compact: Boolean = false,
) {
    // A campaign that demands acknowledgement but names no button still needs a
    // way out, so fall back to a plain OK.
    val primaryLabel = message.primaryLabel?.takeIf { it.isNotBlank() }
        ?: "OK".takeIf { message.requireAck }
    val secondaryLabel = message.secondaryLabel?.takeIf { it.isNotBlank() }

    if (primaryLabel == null && secondaryLabel == null) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (primaryLabel != null) {
            Button(
                onClick = { onPrimary(message) },
                colors = message.buttonColor.toColorOrNull()?.let {
                    ButtonDefaults.buttonColors(containerColor = it)
                } ?: ButtonDefaults.buttonColors(),
            ) {
                Text(primaryLabel, maxLines = 1)
            }
        }
        if (secondaryLabel != null && !compact) {
            OutlinedButton(onClick = { onSecondary(message) }) {
                Text(secondaryLabel, maxLines = 1)
            }
        }
    }
    if (secondaryLabel != null && compact) {
        Spacer(Modifier.width(4.dp))
    }
}

/** Parses "#RRGGBB"/"#AARRGGBB"; anything unparseable falls back to the theme. */
private fun String?.toColorOrNull(): Color? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null

    return try {
        Color(android.graphics.Color.parseColor(value))
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String?.toColorOr(fallback: Color): Color = toColorOrNull() ?: fallback
