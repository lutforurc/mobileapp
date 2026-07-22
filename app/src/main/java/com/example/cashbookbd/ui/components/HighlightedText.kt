package com.example.cashbookbd.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.HighlightRule
import com.example.cashbookbd.ui.theme.BrandPalette
import com.example.cashbookbd.ui.theme.brand

/**
 * Highlight-rules UI support, shared by every report screen: load the cached
 * rules with [rememberHighlightRules], match a row's notes/remarks with
 * [com.example.cashbookbd.report.matchHighlightRule], then draw the text with
 * [HighlightedText] — the one place the "phrase → coloured box" style lives.
 */

/**
 * The company's active highlight rules, fetched once per process and shared by
 * every screen that calls this (the repository caches; re-entering a report
 * doesn't refetch). Empty until loaded or when the fetch fails.
 */
@Composable
fun rememberHighlightRules(): List<HighlightRule> {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.provideHighlightRuleRepository(context) }
    LaunchedEffect(repository) { repository.ensureLoaded() }
    val rules by repository.rules.collectAsStateWithLifecycle()
    return rules
}

/**
 * The border colour for a matched rule in this theme, or null for no rule.
 * Unknown/blank palette keys fall back to red, per the agreed behaviour.
 */
fun BrandPalette.highlightColor(rule: HighlightRule?): Color? =
    rule?.let { highlight[it.color.lowercase()] ?: highlight["red"] }

/** [highlightColor] against the current theme's palette. */
@Composable
@ReadOnlyComposable
fun highlightBorderColor(rule: HighlightRule?): Color? =
    MaterialTheme.brand.highlightColor(rule)

/**
 * A text that, when [borderColor] is set, is wrapped in the highlight box: a
 * 2dp rounded border hugging the text (not the full cell width), the text
 * colour itself unchanged.
 */
@Composable
fun HighlightedText(
    text: String,
    borderColor: Color?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = if (borderColor != null) {
            modifier
                .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        } else {
            modifier
        },
    )
}
