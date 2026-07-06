package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.data.remote.dto.ApiLedgerStatementDto
import com.example.cashbookbd.data.remote.dto.LedgerSearchItemDto
import com.example.cashbookbd.ui.components.LedgerDropdownItem

/**
 * A ledger statement returned by `/reports/api-ledger`: an opening balance
 * (carried from before the start date) followed by the transaction rows in the
 * range. No running/closing balance is computed — the report only lists the
 * opening line and the raw debit/credit of each voucher.
 */
data class LedgerStatement(
    val openingDebit: Double,
    val openingCredit: Double,
    val rows: List<LedgerRow>,
    // Report footer. When the backend supplies these (see [toLedgerStatement]),
    // they win; otherwise they're derived from the opening balance + rows.
    val rangeDebit: Double,
    val rangeCredit: Double,
    val totalDebit: Double,
    val totalCredit: Double,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** Net balance: positive => receivable (debit side), negative => payable. */
    val balance: Double get() = totalDebit - totalCredit
}

/** One transaction line of the statement. */
data class LedgerRow(
    val date: String,
    val voucherNo: String,
    val description: String,
    val debit: Double,
    val credit: Double,
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
    val rows = details.orEmpty().map { dto ->
        LedgerRow(
            date = dto.vrDate?.trim().orEmpty(),
            voucherNo = dto.vrNo?.trim().orEmpty(),
            description = dto.remarks?.trim().orEmpty(),
            debit = dto.debit ?: 0.0,
            credit = dto.credit ?: 0.0,
        )
    }

    val openingDebit = openingBalance?.totalDebit ?: 0.0
    val openingCredit = openingBalance?.totalCredit ?: 0.0

    // Prefer backend-supplied footer totals; otherwise derive from the rows.
    val rangeDebit = summary?.rangeDebit ?: rows.sumOf { it.debit }
    val rangeCredit = summary?.rangeCredit ?: rows.sumOf { it.credit }
    val totalDebit = summary?.totalDebit ?: (openingDebit + rangeDebit)
    val totalCredit = summary?.totalCredit ?: (openingCredit + rangeCredit)

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
