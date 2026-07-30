package com.example.cashbookbd.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.cashbookbd.ui.theme.AppFontWeight

/**
 * The app's four kinds of text as ready-made composables — the writing-side of
 * [AppFontWeight]. Pick the kind, not a weight:
 *
 *  - [BoldText] — screen titles, table headers, totals.
 *  - [SemiBoldText] — sub-headings, field labels, emphasised values.
 *  - [NormalText] — body text.
 *  - [MutedText] — secondary grey text (normal weight, muted colour).
 *
 * Each takes the size from [style] (defaulting to the surrounding text style),
 * so the same kind scales from a caption to a headline without new variants.
 */
@Composable
fun BoldText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text = text,
    modifier = modifier,
    style = style,
    fontWeight = AppFontWeight.Bold,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
)

@Composable
fun SemiBoldText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text = text,
    modifier = modifier,
    style = style,
    fontWeight = AppFontWeight.SemiBold,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
)

@Composable
fun NormalText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text = text,
    modifier = modifier,
    style = style,
    fontWeight = AppFontWeight.Normal,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
)

/** Grey secondary text — the fourth kind: normal weight, muted colour. */
@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text = text,
    modifier = modifier,
    style = style,
    fontWeight = AppFontWeight.Normal,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
)
