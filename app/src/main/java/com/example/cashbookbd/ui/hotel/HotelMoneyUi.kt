package com.example.cashbookbd.ui.hotel

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.HotelFolioLine
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.asDivider
import com.example.cashbookbd.ui.theme.asTint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * The small vocabulary every money-side hotel screen speaks: how an amount is
 * written, how a date that may have arrived as a UTC instant becomes the
 * calendar day it was, how a room's nights fold into one line, the status
 * chip, the hold countdown. Shared so the folio, the check-out screen and the
 * bookings list cannot drift into three readings of the same fact.
 */

// ---------------------------------------------------------------------------
//  Money
// ---------------------------------------------------------------------------

/** Null or absent is "—", never a zero pretending to be an answer. */
internal fun hotelMoney(value: Double?): String = if (value == null) "—" else AmountFormat.format(value)

/** A rate as people say it: "15", "7.5" — never "15.00%". */
internal fun hotelRate(rate: Double): String =
    if (rate == Math.rint(rate)) rate.toLong().toString() else rate.toString().trimEnd('0').trimEnd('.')

// ---------------------------------------------------------------------------
//  Dates
// ---------------------------------------------------------------------------

private val INSTANT = Regex("""^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")
private val ISO_DAY = Regex("""(\d{4})-(\d{2})-(\d{2})""")

/**
 * The calendar day of a date or an instant, as YYYY-MM-DD.
 *
 * A stay_date can arrive as `2026-08-26T18:00:00.000000Z` — midnight in Dhaka
 * said in UTC — and reading its first ten characters would put the night on
 * the 26th when it was the 27th. Anything carrying a zone is moved to this
 * clock first; a plain date is taken as it is.
 */
internal fun hotelDay(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    val m = INSTANT.find(t)
    val zone = m?.groupValues?.getOrNull(3).orEmpty()
    if (m != null && zone.isNotEmpty()) {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = if (zone == "Z") TimeZone.getTimeZone("UTC") else TimeZone.getTimeZone("GMT$zone")
        }
        val parsed = runCatching { parser.parse("${m.groupValues[1]}T${m.groupValues[2]}") }.getOrNull()
        if (parsed != null) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(parsed)
        }
    }
    return t.take(10)
}

/** dd/MM/yyyy of a date or instant; blank stays blank, junk stays as it was. */
internal fun hotelDate(raw: String): String {
    val day = hotelDay(raw)
    val p = day.split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else day
}

/** Every YYYY-MM-DD inside a sentence, rewritten the way it is read out. */
internal fun asRead(text: String): String =
    ISO_DAY.replace(text) { m -> "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}" }

/** A YYYY-MM-DD as a [SimpleDate], or null when it is not one. */
internal fun simpleDateOf(iso: String): SimpleDate? {
    val p = hotelDay(iso).split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return SimpleDate(y, m, d)
}

/** The day after a YYYY-MM-DD, built from its parts — never by adding 24 hours. */
private fun nextDay(day: String): String = simpleDateOf(day)?.plusDays(1)?.toApi() ?: day

/** The shared date dialog, seeded from what is there or from today. */
internal fun pickMoneyDate(context: Context, initial: SimpleDate?, onPicked: (SimpleDate) -> Unit) {
    val start = initial ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}

// ---------------------------------------------------------------------------
//  Folding a room's nights into one line
// ---------------------------------------------------------------------------

/**
 * "30/08/2026 — 01/09/2026, 05/09/2026": consecutive nights as a range and the
 * odd one after the gap on its own. The GAP is the point — read as one range,
 * 30/08 to 05/09 would charge two nights the guest did not have.
 */
internal fun foldNights(dates: List<String>): String {
    val days = dates.map { hotelDay(it) }.filter { it.isNotBlank() }.toSortedSet().toList()
    if (days.isEmpty()) return ""
    val runs = mutableListOf<MutableList<String>>()
    days.forEach { day ->
        val run = runs.lastOrNull()
        if (run != null && nextDay(run.last()) == day) run.add(day) else runs.add(mutableListOf(day))
    }
    return runs.joinToString(", ") { run ->
        if (run.size > 1) "${asRead(run.first())} — ${asRead(run.last())}" else asRead(run.first())
    }
}

/**
 * The bill with a room's nights on one line instead of one line each.
 *
 * What merges is deliberately narrow — the same room, room rent, the same
 * rates. A stay that crosses a tariff change has two rates on it and one row
 * cannot honestly carry two figures. Halls and meals never fold: three
 * sittings of one hall are three sales, and the guest reads them as three.
 * Quantities and amounts are summed; voucher numbers are listed where a run
 * was posted in more than one batch.
 */
internal fun foldBill(lines: List<HotelFolioLine>): List<HotelFolioLine> {
    class Row(var line: HotelFolioLine, val nights: MutableList<String>, val vouchers: MutableList<String>)

    val out = mutableListOf<Row>()
    val at = HashMap<String, Int>()
    lines.forEach { line ->
        val foldable = line.resourceId != null && line.chargeType == "room_rent"
        val key = if (foldable) {
            "${line.resourceId}|${line.chargeType}|${line.unitRate}|${line.serviceChargeRate}|${line.vatRate}"
        } else {
            null
        }
        val index = key?.let { at[it] }
        if (index == null) {
            if (key != null) at[key] = out.size
            out.add(
                Row(
                    line = line,
                    nights = if (foldable && line.stayDate.isNotBlank()) mutableListOf(line.stayDate) else mutableListOf(),
                    vouchers = mutableListOf(line.vrNo),
                )
            )
            return@forEach
        }
        val row = out[index]
        row.line = row.line.copy(
            quantity = row.line.quantity + line.quantity,
            baseAmount = row.line.baseAmount + line.baseAmount,
            serviceChargeAmount = row.line.serviceChargeAmount + line.serviceChargeAmount,
            vatAmount = row.line.vatAmount + line.vatAmount,
            lineTotal = row.line.lineTotal + line.lineTotal,
        )
        if (line.stayDate.isNotBlank()) row.nights.add(line.stayDate)
        row.vouchers.add(line.vrNo)
    }
    return out.mapIndexed { index, row ->
        row.line.copy(
            // Numbered as drawn: the folio's own numbers would print 1, 4, 7.
            lineNo = index + 1,
            description = if (row.nights.isNotEmpty()) {
                "${row.line.description.substringBefore(" — ")} — ${foldNights(row.nights)}"
            } else {
                asRead(row.line.description)
            },
            vrNo = row.vouchers.filter { it.isNotBlank() }.distinct().joinToString(", "),
        )
    }
}

// ---------------------------------------------------------------------------
//  Status
// ---------------------------------------------------------------------------

internal fun hotelStatusLabel(status: String): String = when (status) {
    "hold" -> "Held"
    "confirmed" -> "Confirmed"
    "checked_in" -> "Checked in"
    "checked_out" -> "Checked out"
    "cancelled" -> "Cancelled"
    "expired" -> "Hold expired"
    else -> status.replace('_', ' ').ifBlank { "—" }
}

/** One colour per state, from the palette — a held booking is amber everywhere. */
@Composable
internal fun hotelStatusColor(status: String): Color = when (status) {
    "hold" -> MaterialTheme.appColors.warning
    "confirmed" -> MaterialTheme.colorScheme.primary
    "checked_in" -> MaterialTheme.appColors.success
    "checked_out" -> MaterialTheme.appColors.info
    else -> MaterialTheme.appColors.textMuted
}

/** A small tinted pill — the status chip, a payment's purpose, "not posted". */
@Composable
internal fun HotelPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = AppFontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(color.asTint(), RoundedCornerShape(999.dp))
            .border(1.dp, color.asDivider(), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The status chip, with "(N)" only once somebody has been written down. */
@Composable
internal fun HotelStatusChip(status: String, guestsCount: Int = 0, modifier: Modifier = Modifier) {
    val label = hotelStatusLabel(status) + if (guestsCount > 0) " ($guestsCount)" else ""
    HotelPill(text = label, color = hotelStatusColor(status), modifier = modifier)
}

// ---------------------------------------------------------------------------
//  Hold countdown
// ---------------------------------------------------------------------------

internal enum class HoldTone { NONE, CALM, URGENT, LAPSED }

internal data class HoldWords(val text: String, val tone: HoldTone)

/**
 * How long a hold has left, and how loudly to say it.
 *
 * HOW LONG IS LEFT, not when it ends: the property sets its hold in hours, so
 * the hour is the number somebody is looking for, and a clock time makes them
 * work it out from two figures neither of which is on screen. The stored
 * `YYYY-MM-DD HH:MM` carries no zone and is read on this clock by hand, the
 * way the web does — handed to a parser it means whatever the parser decides.
 */
internal fun holdCountdown(holdUntil: String, now: Long = System.currentTimeMillis()): HoldWords {
    if (holdUntil.isBlank()) return HoldWords("no deadline", HoldTone.NONE)
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})""").find(holdUntil)
        ?: return HoldWords(holdUntil, HoldTone.NONE)
    val at = Calendar.getInstance().apply {
        clear()
        set(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt() - 1,
            m.groupValues[3].toInt(),
            m.groupValues[4].toInt(),
            m.groupValues[5].toInt(),
        )
    }.timeInMillis
    val minutes = Math.round((at - now) / 60000.0).toInt()
    if (minutes < 0) {
        val gone = -minutes
        val text = when {
            gone < 1 -> "lapsed just now"
            gone < 60 -> "lapsed $gone min ago"
            gone < 1440 -> "lapsed ${Math.round(gone / 60.0)} hr ago"
            else -> "lapsed ${Math.round(gone / 1440.0)} days ago"
        }
        return HoldWords(text, HoldTone.LAPSED)
    }
    val text = when {
        minutes < 60 -> "${maxOf(1, minutes)} min left"
        minutes < 1440 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            // "1 hr 5 min", but plain "3 hr" on the hour.
            if (rest > 0) "$hours hr $rest min left" else "$hours hr left"
        }
        else -> {
            val days = minutes / 1440
            "$days day${if (days == 1) "" else "s"} left"
        }
    }
    return HoldWords(text, if (minutes <= 180) HoldTone.URGENT else HoldTone.CALM)
}

// ---------------------------------------------------------------------------
//  Small shared pieces
// ---------------------------------------------------------------------------

/** A sentence that needs to be seen — the chart is not ready, the bill is somebody else's. */
@Composable
internal fun HotelBanner(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.asTint(), RoundedCornerShape(8.dp))
            .border(1.dp, color.asDivider(), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** One of the header figures — Charged, Paid, Owed. */
@Composable
internal fun HotelMoneyTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textMuted,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.Bold,
            color = valueColor ?: MaterialTheme.appColors.text,
            maxLines = 1,
        )
    }
}

/** A label over its value, for the fact rows on the check-out and cancel screens. */
@Composable
internal fun HotelFact(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = AppFontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A section heading in the body of a screen. */
@Composable
internal fun HotelSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = AppFontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}
