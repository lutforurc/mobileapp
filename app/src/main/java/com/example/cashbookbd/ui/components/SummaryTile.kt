package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The little figure box the reports put above their tables — the Debit/Credit
 * totals, the Gross Profit strip, the Net Profit/Loss band.
 *
 * Every report screen used to paint this itself, so the fill lived in five
 * places at once. The fill is `surfaceContainerHigh`: one step up from the card
 * the tile sits on, which is what makes it read as a raised block without a
 * border. It is drawn with a [Surface] rather than `Modifier.background` so
 * `LocalContentColor` becomes `onSurface` inside — text in the tile inherits a
 * colour that is guaranteed to be legible on the fill, in both themes.
 *
 * Callers keep their own inner layout and pass spacing through [modifier] /
 * [contentPadding]; the tile only owns the painted container.
 */

/** Shared with the report cards, so the tile reads as part of the same family. */
private val TileShape = RoundedCornerShape(10.dp)

/** The padding the report summary boxes settled on. */
private val TilePadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)

/** A stacked tile — a label over its value. */
@Composable
fun SummaryTile(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = TilePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** The same tile laid out across the width — a label at one end, a figure at the other. */
@Composable
fun SummaryTileRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = TilePadding,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
