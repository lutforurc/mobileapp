package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.core.AmountFormat
import kotlin.math.abs

/** One posted entry inside an Accounts Head, as `reports/general-ledger` sends it. */
data class GeneralLedgerRow(
    val atdId: Long,
    val voucherNo: String,
    val voucherDate: String,
    val narration: String,
    val debit: Double,
    val credit: Double,
    val balance: Double,
    val balanceType: String,
    val isApproved: Boolean = false,
    val isBankVoucher: Boolean = false,
)

/** One Accounts Head block: its opening, its entries, and the closing balance. */
data class GeneralLedgerAccount(
    val coa4Id: Long,
    val code: String,
    val name: String,
    val typeName: String,
    val group2Name: String,
    val groupName: String,
    val openingBalance: Double,
    val openingDebit: Double,
    val openingCredit: Double,
    val debit: Double,
    val credit: Double,
    val closingBalance: Double,
    val rows: List<GeneralLedgerRow>,
) {
    /** "Assets / Current Assets / Cash" — the web's group trail under the name. */
    val groupTrail: String
        get() = listOf(typeName, group2Name, groupName)
            .filter { it.isNotBlank() }
            .joinToString(" / ")

    val codeLabel: String
        get() = if (code.isBlank()) "" else "Code $code"
}

/** The web's four summary boxes, summed over the full filtered set. */
data class GeneralLedgerTotals(
    val openingDebit: Double = 0.0,
    val openingCredit: Double = 0.0,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val closingDebit: Double = 0.0,
    val closingCredit: Double = 0.0,
)

/** The server pages by Accounts Head; 25 to a page, as the web asks. */
data class GeneralLedgerPagination(
    val page: Int = 1,
    val perPage: Int = 25,
    val totalAccounts: Int = 0,
    val lastPage: Int = 1,
)

data class GeneralLedgerReport(
    val branchName: String,
    val startDate: String,
    val endDate: String,
    val accountsHeadName: String,
    val accounts: List<GeneralLedgerAccount>,
    val totals: GeneralLedgerTotals,
    val pagination: GeneralLedgerPagination,
) {
    val isEmpty: Boolean get() = accounts.isEmpty()
}

/** A row as the table draws it, with the synthetic Opening line first. */
data class GeneralLedgerTableRow(
    val key: String,
    val dateText: String,
    val voucherNo: String,
    val narration: String,
    val debit: Double,
    val credit: Double,
    val balance: Double,
    val balanceType: String,
    val isOpening: Boolean,
    val isApproved: Boolean = false,
    val isBankVoucher: Boolean = false,
)

/**
 * The web's tableRows: an Opening Balance row carrying the account's opening
 * Dr/Cr and balance, followed by every posted entry. The date is the wire
 * `yyyy-MM-dd` rendered `dd/MM/yyyy`; anything unparseable passes through.
 */
fun generalLedgerTableRows(account: GeneralLedgerAccount): List<GeneralLedgerTableRow> {
    val opening = GeneralLedgerTableRow(
        key = "opening-${account.coa4Id}",
        dateText = "-",
        voucherNo = "-",
        narration = "Opening Balance",
        debit = account.openingDebit,
        credit = account.openingCredit,
        balance = account.openingBalance,
        balanceType = if (account.openingBalance >= 0) "Dr" else "Cr",
        isOpening = true,
    )
    val details = account.rows.map { row ->
        GeneralLedgerTableRow(
            key = "${account.coa4Id}-${row.atdId}",
            dateText = SimpleDate.fromApi(row.voucherDate.take(10))?.toDisplay()
                ?: row.voucherDate.ifBlank { "-" },
            voucherNo = row.voucherNo.ifBlank { "-" },
            narration = row.narration.ifBlank { "-" },
            debit = row.debit,
            credit = row.credit,
            balance = row.balance,
            balanceType = row.balanceType,
            isOpening = false,
            isApproved = row.isApproved,
            isBankVoucher = row.isBankVoucher,
        )
    }
    return listOf(opening) + details
}

/** "1,234.00 Dr" — the web's balanceText; the sign decides the side when unset. */
fun generalLedgerBalanceText(value: Double, type: String? = null): String {
    val side = type?.takeIf { it.isNotBlank() } ?: if (value >= 0) "Dr" else "Cr"
    return "${AmountFormat.format(abs(value))} $side"
}
