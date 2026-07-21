package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.data.remote.dto.BranchOptionDto
import com.example.cashbookbd.data.remote.dto.CashBookRowDto
import java.util.Calendar
import java.util.Locale

/** A branch entry for the "Select Branch" dropdown. */
data class BranchOption(
    val id: Long,
    val name: String,
)

/**
 * The branch dropdown data plus the backend's current transaction (business)
 * date, which the screen uses as the default Start/End date. [transactionDate]
 * is null when the backend didn't send a parseable date.
 */
data class BranchList(
    val branches: List<BranchOption>,
    val transactionDate: SimpleDate?,
)

/**
 * Calendar-based date value (java.time isn't safe at minSdk 24 without
 * desugaring). Displays as dd/MM/yyyy; serializes to yyyy-MM-dd for the API.
 * [month] is 1-based.
 */
data class SimpleDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** Format the backend expects. */
    fun toApi(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    /** Format shown to the user. */
    fun toDisplay(): String = String.format(Locale.US, "%02d/%02d/%04d", day, month, year)

    /** Compact dd/MM/yy (two-digit year), for tight table cells. */
    fun toShortDisplay(): String = String.format(Locale.US, "%02d/%02d/%02d", day, month, year % 100)

    /** This date shifted by [days] (normalising month/year via Calendar). */
    fun plusDays(days: Int): SimpleDate {
        val c = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
            add(Calendar.DAY_OF_MONTH, days)
        }
        return SimpleDate(
            year = c.get(Calendar.YEAR),
            month = c.get(Calendar.MONTH) + 1,
            day = c.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        fun today(): SimpleDate {
            val c = Calendar.getInstance()
            return SimpleDate(
                year = c.get(Calendar.YEAR),
                month = c.get(Calendar.MONTH) + 1, // Calendar months are 0-based.
                day = c.get(Calendar.DAY_OF_MONTH),
            )
        }

        /**
         * Parses the backend's dd/MM/yyyy business date (e.g. "03/07/2026").
         * Returns null for a blank/malformed value so callers can fall back.
         */
        fun fromDisplay(value: String?): SimpleDate? {
            val parts = value?.trim()?.split("/") ?: return null
            if (parts.size != 3) return null
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = parts[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31 || year < 1900) return null
            return SimpleDate(year = year, month = month, day = day)
        }

        /**
         * Parses the backend's yyyy-MM-dd wire date (e.g. "2026-07-21"), the
         * `attendance_date` an attendance row carries. Null when blank/malformed.
         */
        fun fromApi(value: String?): SimpleDate? {
            val parts = value?.trim()?.split("-") ?: return null
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31 || year < 1900) return null
            return SimpleDate(year = year, month = month, day = day)
        }
    }
}

/** One line of the cash book. */
data class CashBookRow(
    val date: String,
    val particulars: String,
    val voucherNo: String,
    val debit: Double,
    val credit: Double,
    val balance: Double?,
    /** True for the trailing Total / Balance summary rows (styled distinctly). */
    val isSummary: Boolean,
)

data class CashBookReport(
    val rows: List<CashBookRow>,
)

private fun String?.toAmount(): Double = this?.trim()?.toDoubleOrNull() ?: 0.0

private val htmlTag = Regex("<[^>]*>")
private val whitespace = Regex("\\s+")

/** Strips the HTML the backend embeds in `nam` (e.g. `<span>Name</span></br>Account`). */
private fun String?.stripHtml(): String {
    if (this.isNullOrBlank()) return ""
    return this
        .replace("</br>", " — ", ignoreCase = true)
        .replace("<br>", " — ", ignoreCase = true)
        .replace("<br/>", " — ", ignoreCase = true)
        .replace(htmlTag, "")
        .replace("&nbsp;", " ")
        .replace(whitespace, " ")
        .trim()
}

fun BranchOptionDto.toBranchOption(): BranchOption? {
    val id = id ?: return null
    return BranchOption(id = id, name = name?.trim().orEmpty().ifBlank { "Branch $id" })
}

fun CashBookRowDto.toCashBookRow(): CashBookRow {
    val name = particulars.stripHtml()
    // Summary rows are pushed with no voucher and a name ending in Total / Balance.
    val isSummary = vrNo.isNullOrBlank() &&
        (name.endsWith("Total", ignoreCase = true) || name.equals("Balance", ignoreCase = true))

    return CashBookRow(
        date = vrDate?.trim().orEmpty(),
        particulars = name,
        voucherNo = vrNo?.trim().orEmpty(),
        debit = debit.toAmount(),
        credit = credit.toAmount(),
        balance = balance?.trim()?.toDoubleOrNull(),
        isSummary = isSummary,
    )
}
