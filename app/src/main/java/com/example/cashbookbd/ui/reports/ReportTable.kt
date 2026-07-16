package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single, shared table used by every report screen — a Compose port of the
 * web app's `<Table columns={...} data={...} />`. A report describes its
 * [columns] (header + how to [ReportColumn.render] each row into a cell) and
 * hands over its [data]; the template draws the colored header, the grid lines,
 * a vertically-scrolling body, and any [footerRows] (totals) below it.
 *
 * Two layouts, chosen automatically from the column widths:
 *  - **Fixed** — every column is a [ReportColWidth.Fixed] width; the table is as
 *    wide as its columns and scrolls horizontally (ledger-style reports:
 *    Cash Book, Ledger, Trial Balance, Due List, Generic).
 *  - **Fluid** — columns use [ReportColWidth.Weight]; the table fills the width
 *    and does not scroll horizontally (statement reports: Profit & Loss,
 *    Balance Sheet).
 *
 * Cells are text or an arbitrary [ReportTableCell.Slot] (e.g. Due List's stacked
 * name/phone/address block). Totals go in [footerRows], whose cells can span
 * several columns via [ReportFooterCell.colSpan].
 */

/** Thickness of every grid line, kept explicit so footer col-spans stay aligned. */
private val GridLine = 1.dp

/** How a column is sized. All-fixed → horizontal scroll; any weight → fills width. */
sealed interface ReportColWidth {
    data class Fixed(val dp: Dp) : ReportColWidth
    data class Weight(val weight: Float) : ReportColWidth
}

/** The content of a single cell (body or footer). */
sealed interface ReportTableCell {
    data class Text(
        val text: String,
        /** Overrides the column alignment when non-null. */
        val align: TextAlign? = null,
        val bold: Boolean = false,
        val color: Color = Color.Unspecified,
        val maxLines: Int = 1,
        /** Extra leading indent, added to the cell's normal start padding. */
        val startPadding: Dp = 0.dp,
    ) : ReportTableCell

    /** An arbitrary composable cell (the template only supplies the column width). */
    data class Slot(val content: @Composable () -> Unit) : ReportTableCell

    /** A blank cell that still occupies its column, keeping the grid aligned. */
    data object Empty : ReportTableCell
}

/**
 * One column: its header text, width, default alignment, and a [render] that
 * turns a data row (with its index) into a [ReportTableCell] — the direct analog
 * of the web table's `render: (row, index) => ReactNode`.
 */
data class ReportColumn<T>(
    val header: String,
    val width: ReportColWidth,
    val align: TextAlign = TextAlign.Start,
    /** Header alignment; defaults to the column [align]. */
    val headerAlign: TextAlign = align,
    val render: (row: T, index: Int) -> ReportTableCell,
)

/** One cell of a footer/total row, optionally spanning [colSpan] columns. */
data class ReportFooterCell(
    val cell: ReportTableCell,
    val colSpan: Int = 1,
)

/**
 * A top-tier header cell for a two-row grouped header, spanning [span] columns
 * (e.g. "OPENING" over its Dr/Cr pair). A blank [label] leaves the cell empty
 * (used above the SL/Description columns). The spans must sum to the column count.
 */
data class ReportHeaderGroup(
    val label: String,
    val span: Int,
)

/** Convenience builder for a text cell. */
fun cellText(
    text: String,
    align: TextAlign? = null,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    startPadding: Dp = 0.dp,
): ReportTableCell.Text = ReportTableCell.Text(text, align, bold, color, maxLines, startPadding)

@Composable
fun <T> ReportTable(
    columns: List<ReportColumn<T>>,
    data: List<T>,
    modifier: Modifier = Modifier,
    footerRows: List<List<ReportFooterCell>> = emptyList(),
    /** Optional top-tier grouped header row (spans must sum to [columns].size). */
    headerGroups: List<ReportHeaderGroup>? = null,
    noDataMessage: String = "No data found",
    /** Per-row background (e.g. summary/opening-balance rows). Null = default. */
    rowBackground: ((row: T, index: Int) -> Color?)? = null,
    /**
     * When true (the default) the table fills its parent and owns the vertical
     * scroll — the layout for a screen where the table is the whole result area.
     * Set false to embed the table inside an existing scroll (Profit & Loss,
     * Balance Sheet stack several tables); it then wraps its content height.
     */
    scrollable: Boolean = true,
) {
    val fixed = columns.all { it.width is ReportColWidth.Fixed }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val root = when {
        fixed && scrollable -> modifier.fillMaxSize().horizontalScroll(hScroll)
        fixed -> modifier.horizontalScroll(hScroll)
        scrollable -> modifier.fillMaxSize()
        else -> modifier
    }

    Column(modifier = root) {
        // Header: a two-tier grouped header when headerGroups is set — leading
        // ungrouped (blank-label) columns merge across both tiers — else a single
        // header row. The body scrolls under it.
        if (headerGroups != null) {
            GroupedHeader(columns = columns, groups = headerGroups, fixed = fixed)
        } else {
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
        }
        HorizontalDivider(color = gridLineColor())

        val bodyModifier = if (scrollable) {
            Modifier.weight(1f).verticalScroll(vScroll)
        } else {
            Modifier
        }
        Column(modifier = bodyModifier) {
            if (data.isEmpty() && footerRows.isEmpty()) {
                Box(
                    modifier = rowWidth(fixed).padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = noDataMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                data.forEachIndexed { index, row ->
                    val bg = rowBackground?.invoke(row, index)
                    var rowMod = rowWidth(fixed)
                    if (bg != null) rowMod = rowMod.background(bg)
                    Row(modifier = rowMod.height(IntrinsicSize.Min)) {
                        columns.forEachIndexed { i, col ->
                            if (i > 0) GridVDivider()
                            RenderCell(col.width, col.align, col.render(row, index))
                        }
                    }
                    HorizontalDivider(color = gridLineColor())
                }

                if (footerRows.isNotEmpty()) {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    val footerBg = MaterialTheme.colorScheme.surfaceVariant
                    footerRows.forEach { footer ->
                        Row(
                            modifier = rowWidth(fixed)
                                .background(footerBg)
                                .height(IntrinsicSize.Min),
                        ) {
                            var colIndex = 0
                            footer.forEachIndexed { i, fc ->
                                if (i > 0) GridVDivider()
                                RenderFooterCell(columns, colIndex, fc, fixed)
                                colIndex += fc.colSpan
                            }
                        }
                        HorizontalDivider(color = gridLineColor())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RowScope.RenderCell(
    width: ReportColWidth,
    columnAlign: TextAlign,
    cell: ReportTableCell,
) {
    val base = colWidth(width)
    when (cell) {
        is ReportTableCell.Slot -> Box(modifier = base) { cell.content() }

        ReportTableCell.Empty ->
            Box(modifier = base.padding(horizontal = 8.dp, vertical = 10.dp)) {}

        is ReportTableCell.Text -> Text(
            text = cell.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            color = cell.color,
            textAlign = cell.align ?: columnAlign,
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

/**
 * Two-tier header. Each [ReportHeaderGroup] with a blank label is a single column
 * that **merges** across both tiers (its own header, vertically centred — e.g.
 * "#", "DESCRIPTION"); a labelled group shows the group name on top with its
 * member columns' sub-headers (e.g. Dr/Cr) beneath. The whole header is one row
 * of [IntrinsicSize.Min] height, so the merged cells stretch to the group height.
 */
@Composable
private fun <T> GroupedHeader(
    columns: List<ReportColumn<T>>,
    groups: List<ReportHeaderGroup>,
    fixed: Boolean,
) {
    Row(
        modifier = rowWidth(fixed)
            .background(MaterialTheme.colorScheme.primary)
            .height(IntrinsicSize.Min),
    ) {
        var start = 0
        groups.forEachIndexed { gi, group ->
            if (gi > 0) GridVDivider(onHeader = true)
            val members = columns.subList(start, start + group.span)
            val sectionMod = headerSectionWidth(members, group.span, fixed)

            if (group.label.isBlank()) {
                // Merged cell: the single column's own header, full header height.
                val col = members.first()
                Box(
                    modifier = sectionMod.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    HeaderText(col.header, col.headerAlign)
                }
            } else {
                // Group label on top, member sub-headers beneath.
                Column(modifier = sectionMod) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HeaderText(group.label, TextAlign.Center)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        members.forEachIndexed { mi, col ->
                            if (mi > 0) GridVDivider(onHeader = true)
                            Box(modifier = colWidth(col.width)) {
                                HeaderText(col.header, col.headerAlign)
                            }
                        }
                    }
                }
            }
            start += group.span
        }
    }
}

/** Bold, on-primary header text used by [GroupedHeader]. */
@Composable
private fun HeaderText(text: String, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** Total width (fixed) or weight (fluid) for a header section of [span] columns. */
private fun <T> RowScope.headerSectionWidth(
    members: List<ReportColumn<T>>,
    span: Int,
    fixed: Boolean,
): Modifier = if (fixed) {
    var total = 0.dp
    members.forEach { total += (it.width as ReportColWidth.Fixed).dp }
    total += GridLine * (span - 1)
    Modifier.width(total)
} else {
    var weight = 0f
    members.forEach { weight += (it.width as ReportColWidth.Weight).weight }
    Modifier.weight(weight)
}

/** A footer cell spans [ReportFooterCell.colSpan] columns starting at [start]. */
@Composable
private fun <T> RowScope.RenderFooterCell(
    columns: List<ReportColumn<T>>,
    start: Int,
    footerCell: ReportFooterCell,
    fixed: Boolean,
) {
    val spanned = columns.subList(start, start + footerCell.colSpan)
    val columnAlign = spanned.first().align
    val base: Modifier = if (fixed) {
        var total = 0.dp
        spanned.forEach { total += (it.width as ReportColWidth.Fixed).dp }
        // Re-absorb the grid lines the spanned columns would have drawn.
        total += GridLine * (footerCell.colSpan - 1)
        Modifier.width(total)
    } else {
        var weight = 0f
        spanned.forEach { weight += (it.width as ReportColWidth.Weight).weight }
        Modifier.weight(weight)
    }

    when (val cell = footerCell.cell) {
        is ReportTableCell.Slot -> Box(modifier = base) { cell.content() }

        ReportTableCell.Empty ->
            Box(modifier = base.padding(horizontal = 8.dp, vertical = 10.dp)) {}

        is ReportTableCell.Text -> Text(
            text = cell.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            color = cell.color,
            textAlign = cell.align ?: columnAlign,
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
private fun RowScope.colWidth(width: ReportColWidth): Modifier = when (width) {
    is ReportColWidth.Fixed -> Modifier.width(width.dp)
    is ReportColWidth.Weight -> Modifier.weight(width.weight)
}

/** Fixed tables size to content (scrollable); fluid tables fill the width. */
@Composable
private fun rowWidth(fixed: Boolean): Modifier =
    if (fixed) Modifier else Modifier.fillMaxWidth()

/**
 * The grid line colour: a fixed, clearly-visible light grey derived from the
 * on-surface colour, so it reads the same regardless of the (possibly dynamic)
 * palette — [MaterialTheme.colorScheme.outlineVariant] can render near-invisible
 * on some devices.
 */
@Composable
private fun gridLineColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

/** Vertical grid line spanning the full height of a table row. */
@Composable
private fun GridVDivider(onHeader: Boolean = false) {
    VerticalDivider(
        thickness = GridLine,
        color = if (onHeader) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        } else {
            gridLineColor()
        },
    )
}