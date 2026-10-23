package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.core.AmountFormat

/*
 * Notes to the Profit & Loss Account — the web's profitLossNotes.ts. Every
 * figure a note names is read off the same numbers the statement is drawn
 * from; no amount is written in as text.
 */

/** An income group as the statement lists it, with the heads inside it. */
data class PlIncomeGroup(val name: String, val credit: Double, val children: List<Pair<String, Double>>)

/** The figures the notes read. */
data class ProfitLossFigures(
    val cogsOpening: Double,
    val cogsNetPurchase: Double,
    val cogsDirectAcquisitionCosts: Double,
    val cogsClosing: Double,
    val cogsTotal: Double,
    val grossProfit: Double,
    val totalIncome: Double,
    val incomes: List<PlIncomeGroup>,
    val purchaseDiscountCredit: Double,
    /** Write-offs of the period at cost; carried, never added to a total. */
    val productOut: Double,
    /** Goods received without a purchase; same rule. */
    val productIn: Double,
)

/** The number each note carries — the one place the numbering is decided. */
data class ProfitLossNoteIndex(
    val basis: Int,
    val cogs: Int,
    val acquisition: Int,
    val income: Int?,
    val stock: Int,
    val stockMovement: Int?,
)

fun profitLossNoteIndex(f: ProfitLossFigures): ProfitLossNoteIndex {
    var n = 4
    val income = if (f.incomes.isNotEmpty()) n++ else null
    val stock = n++
    val movement = if (f.productOut > 0 || f.productIn > 0) n++ else null
    return ProfitLossNoteIndex(basis = 1, cogs = 2, acquisition = 3, income = income, stock = stock, stockMovement = movement)
}

private fun money(v: Double) = AmountFormat.format(v)

fun buildProfitLossNotes(f: ProfitLossFigures, startDate: String?, endDate: String?): List<ReportNoteItem> {
    val index = profitLossNoteIndex(f)
    val periodText = if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) "$startDate to $endDate" else "the period selected"

    val children = f.incomes.flatMap { it.children }
    // The misspelt chart name ("Comission") is matched too.
    fun earnedBy(name: Regex) = children.filter { name.containsMatchIn(it.first) }.sumOf { it.second }

    val incomeLines = f.incomes.joinToString("; ") { group ->
        val inner = group.children.joinToString("; ") { "${it.first} ${money(it.second)}" }
        if (inner.isNotEmpty()) "${group.name} ${money(group.credit)} ($inner)" else "${group.name} ${money(group.credit)}"
    }

    val interest = earnedBy(Regex("Personal Interest"))
    val commission = earnedBy(Regex("Co(m+)i?ssion"))

    val interestNote = if (interest != 0.0) {
        " Personal Interest is interest charged on a loan the business carries as" +
            " an asset. It was added to the loan balance rather than received, and on" +
            " the same date the loan account was credited with money received into the" +
            " bank and described as interest and principal together: the collection is" +
            " evidenced, the split between the two is not recorded. The interest is" +
            " charged for a year that ends before these books open."
    } else ""

    val commissionNote = if (commission != 0.0) {
        " Company Commission was settled in cash: the amount was debited against a" +
            " supplier's payable and credited to the bank on the same date, and its" +
            " remark dates it to the year before this period."
    } else ""

    val unapprovedNote = if (interestNote.isNotEmpty() || commissionNote.isNotEmpty()) {
        " Both of those postings are unapproved (is_approved = 0). Neither" +
            " account has been moved, and no historical posting has been changed."
    } else ""

    val asideNote = if (interest != 0.0 || commission != 0.0) {
        " Setting them aside would reduce the result by ${money(interest + commission)} in all" +
            if (interest != 0.0 && commission != 0.0) {
                ", of which ${money(interest)} is the interest and ${money(commission)} the commission."
            } else "."
    } else ""

    val discountCaveat = if (f.purchaseDiscountCredit != 0.0) {
        " Purchase discounts are taken to the trading account in full and are not" +
            " allocated between goods sold and goods still held, while the stock layer" +
            " keeps the gross price on the bill line -- so the stock held is carried" +
            " above its net cost by the share of the discount attributable to it. The" +
            " discounts of this period are ${money(f.purchaseDiscountCredit)} in" +
            " all, and any allocation would have to take the same amount out of net" +
            " purchase for the goods sold, or the discount is deducted twice."
    } else ""

    val stockNote = stockMovementNote(
        f.productOut,
        f.productIn,
        "The written-off quantity has left the stock on hand and the received quantity has joined " +
            "it, so both are already inside the closing stock the cost of goods sold is worked out " +
            "from (note ${index.cogs}) -- the loss has raised that cost and the receipt has lowered it -- and " +
            "neither is adjusted again as a separate expense. Where a product was bought at more " +
            "than one price, the closing-stock valuation (note ${index.stock}) can price the same quantity " +
            "differently, so the effect on the cost of goods sold may differ from the write-off " +
            "amount.",
    )

    return buildList {
        add(
            ReportNoteItem(
                index.basis, "Basis of preparation",
                "The account covers $periodText for the branch selected. Income and " +
                    "expenses come from eligible posted vouchers: posted, not a year-end " +
                    "closing entry, this company and branch, and dated within the period. " +
                    "Net sales is sales less the sales returns and sales discounts of the " +
                    "period, and net purchase is purchases less purchase returns and purchase " +
                    "discounts. Goods transferred to or from another branch are carried at " +
                    "cost as Goods Issued and Goods Received, and are counted once. Each " +
                    "branch carries its own side of a consignment -- the sender credits the " +
                    "cost of the goods it sent, the receiver debits the same cost as a " +
                    "purchase -- and the transfer heads are excluded from the income and " +
                    "expenses below the trading account, so the transfer reaches the result " +
                    "exactly once. This account is always drawn for one branch, so there is no " +
                    "consolidation in which the two sides would meet and have to be eliminated.",
            ),
        )
        add(
            ReportNoteItem(
                index.cogs, "Cost of goods sold",
                "Opening stock ${money(f.cogsOpening)}, plus net purchase ${money(f.cogsNetPurchase)}, " +
                    "plus direct acquisition costs ${money(f.cogsDirectAcquisitionCosts)}, less closing " +
                    "stock ${money(f.cogsClosing)}, gives a cost of goods sold of ${money(f.cogsTotal)}. " +
                    "This states the consequence of figures already in the trading account and is " +
                    "added to no total; it explains the gross profit of ${money(f.grossProfit)} " +
                    "shown beside it.",
            ),
        )
        add(
            ReportNoteItem(
                index.acquisition, "Direct acquisition costs",
                "No freight, unloading or handling cost is carried into the value of the " +
                    "inventory, and none is included in the cost of goods sold above. Stock is " +
                    "valued at the price recorded on each purchase line, and the stock records " +
                    "hold no separate part for such a cost; the accounts that take them book " +
                    "them as expenses of the period they were incurred in. Whether some of " +
                    "that expenditure is directly attributable to bringing goods in, and so " +
                    "belongs in the cost of that inventory, has not been decided and no " +
                    "allocation has been made. Settling it is not a matter of adding an amount " +
                    "here: the same cost would have to come out of the expense it currently " +
                    "sits in, or it is counted once in the stock and again in the expenses. " +
                    "The treatment is therefore a limitation of this account as it stands, and " +
                    "the figures are as the vouchers posted them.",
            ),
        )
        index.income?.let { n ->
            add(
                ReportNoteItem(
                    n, "Income accounts",
                    "The income of the period is ${money(f.totalIncome)}" +
                        (if (incomeLines.isNotEmpty()) ": $incomeLines. " else ". ") +
                        "The accounts shown inside a group are part of that group's own figure and " +
                        "are not added to it a second time." +
                        interestNote + commissionNote + unapprovedNote + asideNote,
                ),
            )
        }
        add(
            ReportNoteItem(
                index.stock, "Stock valuation",
                "Closing stock ${money(f.cogsClosing)}. The layers are the purchase records, " +
                    "and what is left standing is taken from the newest layer backwards until " +
                    "the quantity on hand is met -- last-in, first-out. That is not the method " +
                    "this account has been described with, and the code agrees with the " +
                    "description elsewhere: the routine's own name, and the separate walk that " +
                    "prices stock for the product-wise report, both take the oldest layers " +
                    "first. The two readings have been measured over the period reviewed and " +
                    "differ on a minority of products; the correction is not applied, and the " +
                    "figures on this report are the ones the walk produces today. Three limits " +
                    "of that walk: it caps both the number of layers it reads and the quantity " +
                    "it consumes at the whole-number part of the quantity on hand, so a product " +
                    "held in a fractional quantity can be valued over too few layers with part " +
                    "of its quantity left unpriced; a layer is valued at the price recorded on " +
                    "the purchase line, less any rebate recorded on that line, and carries no " +
                    "acquisition cost (note ${index.acquisition}); and stock is carried at " +
                    "cost, because there is no net realisable value review and no write-down " +
                    "mechanism, so stock that has fallen in value or become unsaleable is not " +
                    "reduced until a stock-out voucher takes it out." +
                    discountCaveat,
            ),
        )
        if (stockNote != null && index.stockMovement != null) {
            add(ReportNoteItem(index.stockMovement, stockNote.first, stockNote.second))
        }
    }
}
