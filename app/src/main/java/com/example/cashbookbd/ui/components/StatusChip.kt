package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.PillShape
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.asDivider
import com.example.cashbookbd.ui.theme.asTint

/**
 * The named status tones a [StatusChip] can carry, resolved against the current
 * theme so a "due" chip is the theme's danger colour everywhere rather than the
 * screen's own guess at a red.
 */
enum class StatusTone { Primary, Success, Danger, Warning, Info, Neutral }

/** The palette colour behind a [StatusChip]'s tone. */
@Composable
fun statusToneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.Primary -> MaterialTheme.colorScheme.primary
    StatusTone.Success -> MaterialTheme.appColors.success
    StatusTone.Danger -> MaterialTheme.appColors.danger
    StatusTone.Warning -> MaterialTheme.appColors.warning
    StatusTone.Info -> MaterialTheme.appColors.info
    StatusTone.Neutral -> MaterialTheme.appColors.textMuted
}

/**
 * The app's status chip — "Confirmed", "Paid", "Overdue", a room state.
 *
 * A soft wash of the tone as the fill with its own ink, and an optional hairline
 * border. Several module-private chips (hotel status pills, dashboard/reseller
 * badges) drew this same shape inline; they resolve here now, so one status
 * looks the same whichever screen it appears on. [color] overrides the tone for
 * a value that arrives with its own colour (a per-customer cycle, say).
 *
 * Uses a tint of the tone rather than a solid fill so it reads as a label, not a
 * button, and stays legible on both a light and a dark card.
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Primary,
    color: Color? = null,
    compact: Boolean = false,
    bordered: Boolean = false,
) {
    val tint = color ?: statusToneColor(tone)
    Text(
        text = text,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
        color = tint,
        fontWeight = AppFontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(tint.asTint(), PillShape)
            .then(if (bordered) Modifier.border(1.dp, tint.asDivider(), PillShape) else Modifier)
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
    )
}
