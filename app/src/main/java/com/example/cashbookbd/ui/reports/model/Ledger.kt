package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages
import com.example.cashbookbd.data.remote.dto.ApiLedgerStatementDto
import com.example.cashbookbd.data.remote.dto.LedgerSearchItemDto
import com.example.cashbookbd.ui.components.LedgerDropdownItem

/**
 * A ledger statement returned by `/reports/api-ledger`: an opening balance
 * (carried from before the start date) followed by the transaction rows in the
 * range. As on the web, the opening is presented **netted** (only one side
 * non-zero) and every row carries a cumulative running balance seeded from it.
 */
data class LedgerStatement(
    val openingDebit: Double,
    val openingCredit: Double,
    val rows: List<LedgerRow>,
    // Report footer. When the backend supplies these (see [toLedgerStatement]),
    // they win; otherwise they're derived from the netted opening + rows.
    val rangeDebit: Double,
    val rangeCredit: Double,
    val totalDebit: Double,
    val totalCredit: Double,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** Net balance: positive => receivable (debit side), negative => payable. */
    val balance: Double get() = totalDebit - totalCredit

    // The web nets the opening before showing it: exactly one side is non-zero.
    val openingNetDebit: Double get() = maxOf(openingDebit - openingCredit, 0.0)
    val openingNetCredit: Double get() = maxOf(openingCredit - openingDebit, 0.0)

    /** The running-balance seed: netted opening, debit positive. */
    val openingRunning: Double get() = openingNetDebit - openingNetCredit
}

/** One transaction line of the statement. */
data class LedgerRow(
    val date: String,
    val voucherNo: String,
    val description: String,
    /** Free-text voucher note ("-" from the API means none); highlight rules match on it. */
    val remarks: String,
    val debit: Double,
    val credit: Double,
    /** Cumulative balance after this row, seeded from the netted opening. */
    val runningBalance: Double = 0.0,
    /** The voucher's branch — shown under the description in All Branch mode. */
    val branchName: String = "",
    /** The voucher's photo/document attachments — the flag-gated Voucher column. */
    val attachments: List<VoucherAttachment> = emptyList(),
)

fun LedgerSearchItemDto.toLedgerDropdownItem(): LedgerDropdownItem? {
    val id = value?.toInt() ?: return null
    val name = label?.trim().orEmpty().ifBlank { "Account $id" }
    // `label_2` carries the party's mobile; blank/space for non-party accounts.
    return LedgerDropdownItem(
        id = id,
        name = name,
        mobile = mobile?.trim()?.ifBlank { null },
    )
}

fun ApiLedgerStatementDto.toLedgerStatement(): LedgerStatement {
    val openingDebit = openingBalance?.totalDebit ?: 0.0
    val openingCredit = openingBalance?.totalCredit ?: 0.0

    // The running balance is seeded from the *netted* opening, then accumulates
    // debit − credit per row — the web's generateTableData loop.
    var running = maxOf(openingDebit - openingCredit, 0.0) -
        maxOf(openingCredit - openingDebit, 0.0)

    val rows = details.orEmpty().map { dto ->
        // "-" is the backend's empty-remarks placeholder.
        val remarks = dto.remarks?.trim().orEmpty().takeUnless { it == "-" }.orEmpty()
        running += (dto.debit ?: 0.0) - (dto.credit ?: 0.0)
        LedgerRow(
            date = dto.vrDate?.trim().orEmpty(),
            voucherNo = dto.vrNo?.trim().orEmpty(),
            // The web ledger's DESCRIPTION column renders `name` — the opposite
            // side of the entry ("Sales, Sales Discount (<items>)") — with the
            // free-text remarks on a second line beneath it.
            description = dto.name?.trim().orEmpty().ifBlank { remarks },
            remarks = remarks,
            debit = dto.debit ?: 0.0,
            credit = dto.credit ?: 0.0,
            runningBalance = running,
            branchName = dto.branchName?.trim().orEmpty(),
            attachments = VoucherImages.attachments(
                dto.voucherImage,
                VoucherImages.branchPad(dto.branchId),
            ),
        )
    }

    // Prefer backend-supplied footer totals; otherwise derive from the rows.
    // The Total row combines the netted opening with the range, as on the web.
    val openingNetDebit = maxOf(openingDebit - openingCredit, 0.0)
    val openingNetCredit = maxOf(openingCredit - openingDebit, 0.0)
    val rangeDebit = summary?.rangeDebit ?: rows.sumOf { it.debit }
    val rangeCredit = summary?.rangeCredit ?: rows.sumOf { it.credit }
    val totalDebit = summary?.totalDebit ?: (openingNetDebit + rangeDebit)
    val totalCredit = summary?.totalCredit ?: (openingNetCredit + rangeCredit)

    return LedgerStatement(
        openingDebit = openingDebit,
        openingCredit = openingCredit,
        rows = rows,
        rangeDebit = rangeDebit,
        rangeCredit = rangeCredit,
        totalDebit = totalDebit,
        totalCredit = totalCredit,
    )
}
