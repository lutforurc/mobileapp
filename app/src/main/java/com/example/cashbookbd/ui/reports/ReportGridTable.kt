package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single, shared table used by every report screen. It renders a colored
 * header, a full grid (vertical column separators + horizontal row lines), and a
 * vertically-scrolling body, in either of two layouts:
 *
 *  - **Fixed** — every column has a fixed [GridColWidth.Fixed] width; the table is
 *    horizontally scrollable and as wide as the sum of its columns. Used by the
 *    ledger-style reports (Cash Book, Ledger, Trial Balance, Due List, Generic).
 *  - **Fluid** — columns use [GridColWidth.Weight]; the table fills the available
 *    width and does not scroll horizontally. Used by the statement reports
 *    (Profit & Loss, Balance Sheet), which also mix in [GridRowSpec.FullWidth]
 *    section titles and totals.
 *
 * Rows are plain data ([GridRowSpec]); cells are text or an arbitrary [GridCellSpec.Slot]
 * (e.g. Due List's stacked name/phone/address block).
 */

/** How a column is sized. All-fixed → horizontal scroll; any weight → fills width. */
sealed interface GridColWidth {
    data class Fixed(val dp: Dp) : GridColWidth
    data class Weight(val weight: Float) : GridColWidth
}

/** A column: its header text, width, and default cell alignment. */
data class GridColumn(
    val header: String,
    val width: GridColWidth,
    val align: TextAlign = TextAlign.Start,
    /** Header alignment; defaults to the column [align]. */
    val headerAlign: TextAlign = align,
)

/** One cell in a data row. */
sealed interface GridCellSpec {
    data class Text(
        val text: String,
        /** Overrides the column alignment when non-null. */
        val align: TextAlign? = null,
        val bold: Boolean = false,
        val color: Color = Color.Unspecified,
        val maxLines: Int = 1,
        /** Extra leading indent, added to the cell's normal start padding. */
        val startPadding: Dp = 0.dp,
    ) : GridCellSpec

    /** An arbitrary composable cell (the template only supplies the column width). */
    data class Slot(val content: @Composable () -> Unit) : GridCellSpec

    /** A blank cell that still occupies its column, keeping the grid aligned. */
    data object Empty : GridCellSpec
}

/** One row in the table body. */
sealed interface GridRowSpec {
    /** A normal row whose cells line up with the columns (with vertical grid lines). */
    data class Data(
        val cells: List<GridCellSpec>,
        val background: Color? = null,
        /** Draws a heavier separator directly above this row (e.g. a totals block). */
        val topDividerThick: Boolean = false,
    ) : GridRowSpec

    /** A row that spans the full width with no vertical grid lines (section titles, net line). */
    data class FullWidth(
        val content: @Composable () -> Unit,
        val background: Color? = null,
    ) : GridRowSpec
}

@Composable
fun ReportGridTable(
    columns: List<GridColumn>,
    rows: List<GridRowSpec>,
    modifier: Modifier = Modifier,
) {
    val fixed = columns.all { it.width is GridColWidth.Fixed }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val root = if (fixed) {
        modifier.fillMaxSize().horizontalScroll(hScroll)
    } else {
        modifier.fillMaxSize()
    }

    Column(modifier = root) {
        // Header stays put; the body scrolls under it.
        Row(
            modifier = rowWidth(fixed)
                .background(MaterialTheme.colorScheme.primary)
                .height(IntrinsicSize.Min),
        ) {
            columns.forEachIndexed { i, col ->
                if (i > 0) GridVDivider(onHeader = true)
                Text(
                    text = col.header,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = col.headerAlign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = colWidth(col.width)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            rows.forEach { row -> RenderRow(columns, row, fixed) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RenderRow(columns: List<GridColumn>, row: GridRowSpec, fixed: Boolean) {
    when (row) {
        is GridRowSpec.FullWidth -> {
            var mod = rowWidth(fixed)
            row.background?.let { mod = mod.background(it) }
            Box(modifier = mod) { row.content() }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        is GridRowSpec.Data -> {
            if (row.topDividerThick) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            }
            var mod = rowWidth(fixed)
            row.background?.let { mod = mod.background(it) }
            Row(modifier = mod.height(IntrinsicSize.Min)) {
                columns.forEachIndexed { i, col ->
                    if (i > 0) GridVDivider()
                    RenderCell(col, row.cells.getOrNull(i) ?: GridCellSpec.Empty)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RowScope.RenderCell(col: GridColumn, cell: GridCellSpec) {
    val base = colWidth(col.width)
    when (cell) {
        is GridCellSpec.Slot -> Box(modifier = base) { cell.content() }

        GridCellSpec.Empty -> Box(modifier = base.padding(horizontal = 8.dp, vertical = 10.dp)) {}

        is GridCellSpec.Text -> Text(
            text = cell.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            color = cell.color,
            textAlign = cell.align ?: col.align,
            maxLines = cell.maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = base.padding(
                start = 8.dp + cell.startPadding,
                end = 8.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        )
    }
}

/** Column sizing inside a [Row]: fixed width or a proportional weight. */
private fun RowScope.colWidth(width: GridColWidth): Modifier = when (width) {
    is GridColWidth.Fixed -> Modifier.width(width.dp)
    is GridColWidth.Weight -> Modifier.weight(width.weight)
}

/** Fixed tables size to content (scrollable); fluid tables fill the width. */
@Composable
private fun rowWidth(fixed: Boolean): Modifier =
    if (fixed) Modifier else Modifier.fillMaxWidth()

/** Vertical grid line spanning the full height of a table row. */
@Composable
private fun GridVDivider(onHeader: Boolean = false) {
    VerticalDivider(
        color = if (onHeader) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )
}
