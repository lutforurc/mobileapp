package com.example.cashbookbd.ui.theme

import androidx.compose.ui.text.font.FontWeight

/**
 * The app's four kinds of text, decided once and used everywhere:
 *
 *  1. **Bold** ([AppFontWeight.Bold]) — screen titles, table headers, totals.
 *  2. **Semi-bold** ([AppFontWeight.SemiBold]) — sub-headings, field labels,
 *     emphasised values.
 *  3. **Normal** ([AppFontWeight.Normal]) — body text.
 *  4. **Muted (grey)** — secondary text. Not a weight but a colour:
 *     `MaterialTheme.colorScheme.onSurfaceVariant`, which the palette keeps
 *     legible on both themes ([BrandPalette.onCardMuted]).
 *
 * Screens never name a [FontWeight] directly — they reference these, so
 * re-deciding what "bold" or "semi-bold" means is a one-line change here.
 * The ready-made composables in `ui/components/AppText.kt` wrap the four kinds
 * for new code.
 */
object AppFontWeight {
    val Bold = FontWeight.Bold
    val SemiBold = FontWeight.SemiBold
    val Normal = FontWeight.Normal
}
