package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.appColors
import java.util.Calendar
import java.util.Locale

/**
 * What the hotel's operations screens — the board, the calendar, the reports
 * and the dashboard — share, so a figure tile or a branch picker is decided
 * once and cannot drift between four screens that are opened in one morning.
 *
 * Nothing here is a screen; each piece is small on purpose. The larger shared
 * components (buttons, fields, tables) stay in `ui/components` and
 * `ui/reports`, and these wrap them rather than replace them.
 */

// ---------------------------------------------------------------------------
//  Branches
// ---------------------------------------------------------------------------

/** The branch list and which one to start on. */
internal data class HotelOpsBranches(
    val branches: List<BranchOption>,
    val selected: BranchOption?,
    val unauthorized: Boolean,
)

/**
 * The branch list with the user's OWN branch chosen — the property they are
 * standing in, which is the one every question here is asked about. Falls
 * back to the first when the session does not say. A failed read is not an
 * error for these screens: with no branch sent the server answers for the
 * user's own anyway.
 */
internal suspend fun hotelOpsLoadBranches(
    reportRepository: ReportRepository,
    ownBranchId: Long?,
): HotelOpsBranches = when (val result = reportRepository.getBranches()) {
    is Resource.Success -> {
        val list = result.data.branches
        HotelOpsBranches(
            branches = list,
            selected = list.firstOrNull { it.id == ownBranchId } ?: list.firstOrNull(),
            unauthorized = false,
        )
    }
    is Resource.Error -> HotelOpsBranches(emptyList(), null, result.isUnauthorized)
    Resource.Loading -> HotelOpsBranches(emptyList(), null, false)
}

/**
 * The property picker — shown only when there is a choice. A single-branch
 * company would otherwise see a field that can never change, and every screen
 * in this module would start with a dead control.
 */
@Composable
internal fun HotelOpsBranchPicker(
    branches: List<BranchOption>,
    selected: BranchOption?,
    onSelected: (BranchOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (branches.size <= 1) return
    AppSelectDropdown(
        label = "Property",
        options = branches.map { SelectorOption(it.id.toString(), it.name) },
        selected = selected?.let { SelectorOption(it.id.toString(), it.name) },
        onSelected = { option -> branches.firstOrNull { it.id.toString() == option.id }?.let(onSelected) },
        modifier = modifier,
        placeholder = "Property",
    )
}

// ---------------------------------------------------------------------------
//  Figures
// ---------------------------------------------------------------------------

/**
 * One headline figure with its name under it and, where there is one, the
 * sum that produced it. The working is on the tile deliberately: these
 * figures are quoted at meetings by people who did not run the report, and
 * "62.5% — 30 of 48 room-nights" can be argued with where a bare 62.5% can
 * only be believed.
 *
 * [lead] rings the tile in the primary colour — RevPAR, the one figure that
 * cannot be gamed by discounting or by selling three rooms at a high rate.
 */
@Composable
internal fun HotelOpsFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    working: String? = null,
    tone: Color? = null,
    lead: Boolean = false,
) {
    val ring = if (lead) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, AppShape)
    } else {
        Modifier
    }
    SummaryTile(modifier = modifier.then(ring)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.Bold,
            color = tone ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!working.isNullOrBlank()) {
            Text(
                text = working,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A grid of equal-width tiles laid out [columns] to a row. A short final row
 * is padded so its tiles keep the same width as the rows above rather than
 * stretching to fill the line.
 */
@Composable
internal fun <T> HotelOpsGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable RowScope.(T) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                row.forEach { content(it) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A thin horizontal bar filled to [fraction] — occupancy beside its number.
 * The number is always printed next to it: the bar is a shape to scan by,
 * never the figure itself.
 */
@Composable
internal fun HotelOpsBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(AppShape)
            .background(MaterialTheme.appColors.cardMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(fill),
        )
    }
}

/** A titled card: a header line, a rule, then whatever the section holds. */
@Composable
internal fun HotelOpsSection(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        HorizontalDivider(color = MaterialTheme.appColors.divider)
        content()
        if (!footer.isNullOrBlank()) {
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** A quiet paragraph under a grid or a table — a footnote, a caveat. */
@Composable
internal fun HotelOpsNote(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color ?: MaterialTheme.appColors.textOnScreenMuted,
        modifier = modifier.fillMaxWidth(),
    )
}

/** An error or refusal, centred, with a way back. */
@Composable
internal fun HotelOpsProblem(text: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) LinkButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
internal fun HotelOpsLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

// ---------------------------------------------------------------------------
//  Dates and figures as text
// ---------------------------------------------------------------------------

internal object HotelOpsDates {
    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun monthStart(date: SimpleDate): SimpleDate = SimpleDate(date.year, date.month, 1)

    /** The first of the month [by] months away — the calendar's ‹ › arrows. */
    fun shiftMonth(date: SimpleDate, by: Int): SimpleDate {
        var year = date.year
        var month = date.month + by
        while (month < 1) { month += 12; year -= 1 }
        while (month > 12) { month -= 12; year += 1 }
        return SimpleDate(year, month, 1)
    }

    fun monthTitle(date: SimpleDate): String = "${MONTHS[date.month - 1]} ${date.year}"

    /** A yyyy-MM-dd from the server as dd/MM/yyyy; anything else passes through. */
    fun display(api: String): String =
        SimpleDate.fromApi(api.take(10))?.toDisplay() ?: api

    /** 0 = Sunday … 6 = Saturday. Bangladesh's week starts on Sunday. */
    fun sundayFirstIndex(date: SimpleDate): Int {
        val c = Calendar.getInstance().apply {
            clear()
            set(date.year, date.month - 1, date.day)
        }
        return c.get(Calendar.DAY_OF_WEEK) - 1
    }

    /** The day-of-month digits of a yyyy-MM-dd. */
    fun dayOf(api: String): String = api.takeLast(2).trimStart('0').ifBlank { "0" }
}

/** Money as the branch formats it, or a dash where there is no figure. */
internal fun hotelOpsMoney(value: Double?): String =
    if (value == null) "—" else AmountFormat.format(value)

/** A percentage with [decimals] places — "62.5%". */
internal fun hotelOpsPercent(value: Double?, decimals: Int = 1): String =
    if (value == null) "—" else String.format(Locale.US, "%.${decimals}f%%", value)

/** The shared date-picker dialog, seeded with what is already chosen. */
internal fun hotelOpsPickDate(
    context: Context,
    initial: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
) {
    val start = initial ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}
