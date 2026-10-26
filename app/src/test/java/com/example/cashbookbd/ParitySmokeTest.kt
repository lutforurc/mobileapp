package com.example.cashbookbd

import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.invoice.TilesBranch
import com.example.cashbookbd.ui.invoice.TilesInvoiceKind
import com.example.cashbookbd.ui.invoice.TilesInvoiceUiState
import com.example.cashbookbd.ui.invoice.TilesLine
import com.example.cashbookbd.ui.invoice.TilesProduct
import com.example.cashbookbd.ui.products.AddBrandUiState
import com.example.cashbookbd.ui.products.AddProductUiState
import com.example.cashbookbd.ui.products.MeasurementFields
import com.example.cashbookbd.ui.reports.PlIncomeGroup
import com.example.cashbookbd.ui.reports.ProfitLossFigures
import com.example.cashbookbd.ui.reports.balanceSheetNoteIndex
import com.example.cashbookbd.ui.reports.buildBalanceSheetNotes
import com.example.cashbookbd.ui.reports.buildProfitLossNotes
import com.example.cashbookbd.ui.reports.buildTrialBalanceNotes
import com.example.cashbookbd.ui.reports.noteRef
import com.example.cashbookbd.ui.reports.profitLossNoteIndex
import com.example.cashbookbd.ui.reports.reportGroupLabel
import com.example.cashbookbd.ui.reports.reportSectionLabel
import com.example.cashbookbd.ui.reports.stockMovementNote
import com.example.cashbookbd.ui.reports.model.BalanceSheetColumns
import com.example.cashbookbd.ui.reports.model.BalanceSheetGroup
import com.example.cashbookbd.ui.reports.model.BalanceSheetReport
import com.example.cashbookbd.ui.reports.model.BalanceSheetSection
import com.example.cashbookbd.ui.reports.model.BalanceSheetSubsection
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.vrsettings.validateVoucherRange
import com.example.cashbookbd.session.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A smoke test of the logic the 2026-10 web catch-up brought over: each rule
 * checked against the web source it was ported from. No device needed.
 */
class ParitySmokeTest {

    @Before
    fun twoDecimals() = AmountFormat.setDecimalPlaces(2)

    // ---- Voucher Date Change (web voucherNumber.ts) ----

    @Test
    fun `voucher range - a valid pair passes, identical start and end too`() {
        assertNull(validateVoucherRange("10-261000001", "10-261000010"))
        assertNull(validateVoucherRange("10-261000005", "10-261000005"))
    }

    @Test
    fun `voucher range - every refusal says what is wrong`() {
        assertEquals("Start voucher number is not a valid voucher number.", validateVoucherRange("abc", "10-261000010"))
        assertEquals("End voucher number is not a valid voucher number.", validateVoucherRange("10-261000001", "10-2613"))
        assertEquals("Start voucher number is not a valid voucher number.", validateVoucherRange("10-261300001", "10-261300002"))
        assertEquals("Start and End voucher numbers must have the same voucher type.", validateVoucherRange("10-261000001", "2-261000002"))
        assertEquals("Start and End voucher numbers must belong to the same month and year.", validateVoucherRange("10-261000001", "10-261100002"))
        assertEquals("End voucher number cannot be earlier than the Start voucher number.", validateVoucherRange("10-261000009", "10-261000002"))
    }

    // ---- Product measurement (web bcf31f8f) ----

    @Test
    fun `measurement keeps three decimals, package size two`() {
        assertNull(MeasurementFields("0.667", "3", "10", "4").error)
        assertNull(MeasurementFields(".67", "3").error)
        assertEquals("Measurement must be a number with at most three decimals.", MeasurementFields("0.6667", "3").error)
        assertEquals("Package size must be a number with at most two decimals.", MeasurementFields(packageSize = "1.234", packageUnitId = "4").error)
    }

    @Test
    fun `measurement pairs go together, and a leading dot gains its nought`() {
        assertEquals("Measurement and measurement unit must be given together.", MeasurementFields("1").error)
        assertEquals("Package size and package type must be given together.", MeasurementFields(packageUnitId = "4").error)
        assertNull(MeasurementFields().error)
        assertEquals("0.67", MeasurementFields(".67", "3").measurementForApi())
        assertEquals("", MeasurementFields().packageSizeForApi())
    }

    // ---- Brand and Product forms ----

    @Test
    fun `brand needs only a name, and a filled email must read`() {
        assertTrue(AddBrandUiState(name = "RAK").canSave)
        assertFalse(AddBrandUiState(name = "").canSave)
        assertFalse(AddBrandUiState(name = "RAK", email = "not-an-email").canSave)
        assertTrue(AddBrandUiState(name = "RAK", email = "a@b.co").canSave)
    }

    @Test
    fun `product needs a description and a sound measurement`() {
        val o = SelectorOption("1", "x")
        val ok = AddProductUiState(
            category = o, productType = o, unit = o, name = "Tile", description = "Floor",
            purchasePrice = "10", salesPrice = "12", isLoadingOptions = false,
        )
        assertTrue(ok.canSave)
        assertFalse(ok.copy(description = "").canSave)
        assertFalse(ok.copy(measurement = MeasurementFields("1")).canSave)
    }

    // ---- Tiles invoices (web TilesBusinessSales / Purchase / returns) ----

    private val tile = TilesProduct("5", "Floor Tile", "FT-1", "box", 100.0, 120.0, "", "", "", "")
    private val line = TilesLine(tile, qty = 2.0, price = 120.0, warehouseId = "1", warehouseName = "Main")
    private val party = LedgerDropdownItem(55, "Rahim", null)

    @Test
    fun `tiles sale total = previous + bill (lines and charges) - discount - received`() {
        val s = TilesInvoiceUiState(
            kind = TilesInvoiceKind.SALES, party = party, previousBalance = 1000.0, lines = listOf(line),
            serviceCharge = "10", tdsAmount = "5", transportationAmt = "25", discount = "20", amount = "100",
        )
        assertEquals(280.0, s.billAmount, 0.001)
        assertEquals(1000.0 + 280 - 20 - 100, s.totalTk, 0.001)
        assertEquals("260", s.payable)
        assertEquals("FT-1 - Floor Tile", line.label)
    }

    @Test
    fun `tiles purchase and sales return run the ledger the other way`() {
        val purchase = TilesInvoiceUiState(
            kind = TilesInvoiceKind.PURCHASE, party = party, previousBalance = -500.0,
            lines = listOf(line), discount = "10", amount = "50",
            // A purchase has no service charge, TDS or transport.
            serviceCharge = "99",
        )
        assertEquals(240.0, purchase.billAmount, 0.001)
        assertEquals(-500.0 - 240 + 10 + 50, purchase.totalTk, 0.001)

        val salesReturn = purchase.copy(kind = TilesInvoiceKind.SALES_RETURN)
        assertEquals(-500.0 - 240 + 10 + 50, salesReturn.totalTk, 0.001)
        val purchaseReturn = purchase.copy(kind = TilesInvoiceKind.PURCHASE_RETURN)
        assertEquals(-500.0 + 240 - 10 - 50, purchaseReturn.totalTk, 0.001)
    }

    @Test
    fun `cash head is recognised, and a tiles branch without warehouses keeps one`() {
        val cash = TilesInvoiceUiState(party = LedgerDropdownItem(17, "Cash", null))
        assertTrue(cash.isCash)
        assertFalse(TilesInvoiceUiState(party = party).isCash)
    }

    @Test
    fun `a tiles branch is recognised by its flag or by the trade's name`() {
        // The trade, however the server spells it.
        assertTrue(TilesBranch.matches("Tiles and Sanitary"))
        assertTrue(TilesBranch.matches("Tiles & Sanitary"))
        assertTrue(TilesBranch.matches("Krishi Bitan Tiles and Sanitary Corner"))
        assertFalse(TilesBranch.matches("Trading"))
        assertFalse(TilesBranch.matches("Construction"))
        assertFalse(TilesBranch.matches(null))

        // The flag alone is enough, with no name to read.
        assertTrue(TilesBranch.isTiles(Settings(isTilesAndSanitary = true), null))
        // The trade's name (get-settings) alone is enough, with no flag and no call.
        assertTrue(TilesBranch.isTiles(Settings(branchBusinessType = "Tiles and Sanitary"), null))
        // A non-tiles branch is untouched.
        assertFalse(TilesBranch.isTiles(Settings(branchBusinessType = "Trading"), null))
        assertFalse(TilesBranch.isTiles(Settings(), null))
    }

    // ---- Report notes (web ReportNotes / *Notes.ts) ----

    @Test
    fun `stock note - none, either direction, or both, never netted`() {
        assertNull(stockMovementNote(0.0, 0.0, "x"))
        assertEquals("Inventory loss — damaged or lost goods", stockMovementNote(10.0, 0.0, "x")!!.first)
        assertEquals("Goods received without a purchase", stockMovementNote(0.0, 5.0, "x")!!.first)
        val both = stockMovementNote(50_000.0, 50_000.0, "end.")!!
        assertEquals("Stock moved without a purchase or a sale", both.first)
        assertTrue(both.second.contains("50,000.00") && both.second.endsWith("end."))
    }

    private fun figures(incomes: List<PlIncomeGroup> = emptyList(), out: Double = 0.0, `in`: Double = 0.0) = ProfitLossFigures(
        cogsOpening = 100.0, cogsNetPurchase = 500.0, cogsDirectAcquisitionCosts = 0.0, cogsClosing = 150.0,
        cogsTotal = 450.0, grossProfit = 50.0, totalIncome = incomes.sumOf { it.credit }, incomes = incomes,
        purchaseDiscountCredit = 0.0, productOut = out, productIn = `in`,
    )

    @Test
    fun `P&L notes are numbered 1 to n with nothing skipped`() {
        val plain = profitLossNoteIndex(figures())
        assertNull(plain.income)
        assertEquals(4, plain.stock)
        assertNull(plain.stockMovement)
        assertEquals(listOf(1, 2, 3, 4), buildProfitLossNotes(figures(), "01/01/2026", "22/10/2026").map { it.n })

        val full = figures(listOf(PlIncomeGroup("Other Income", 300.0, listOf("Company Comission (Income)" to 300.0))), out = 40.0)
        val idx = profitLossNoteIndex(full)
        assertEquals(4, idx.income)
        assertEquals(5, idx.stock)
        assertEquals(6, idx.stockMovement)
        val notes = buildProfitLossNotes(full, null, null)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), notes.map { it.n })
        // The misspelt chart head still draws the commission paragraph.
        assertTrue(notes.first { it.n == 4 }.body.contains("Company Commission was settled in cash"))
        assertTrue(notes.first { it.n == 1 }.body.contains("the period selected"))
    }

    @Test
    fun `trial balance notes - fixed numbers, note 6 only when stock moved`() {
        assertEquals(listOf(1, 2, 3, 4, 5), buildTrialBalanceNotes("Main", "01/01/2026", "22/10/2026", 0.0, 0.0).map { it.n })
        val withStock = buildTrialBalanceNotes("Main", null, null, 10.0, 0.0)
        assertEquals(6, withStock.last().n)
        assertTrue(withStock.first().body.contains("the selected period for Main"))
    }

    private fun group(name: String, closing: Double, movement: Double = 0.0) =
        BalanceSheetGroup(name, emptyList(), 0.0, movement, closing)

    @Test
    fun `balance sheet notes follow the groups the sheet carries`() {
        val subs = mapOf(
            "Assets" to listOf(
                BalanceSheetSubsection(
                    "Current Assets",
                    listOf(group("Supplier Advances", 200.0), group("Closing Stock", 900.0, movement = 300.0)),
                    BalanceSheetColumns.ZERO,
                ),
            ),
            "Equity" to listOf(
                BalanceSheetSubsection("Retained Earnings", listOf(group("Current Period Loss", -50.0)), BalanceSheetColumns.ZERO),
            ),
        )
        val report = BalanceSheetReport(sections = listOf(BalanceSheetSection("Assets", emptyList(), 0.0, 0.0, 0.0)), summary = emptyList(), sectioned = subs)

        val noStock = balanceSheetNoteIndex(report, hasStockNote = false)
        assertEquals(mapOf("Supplier Advances" to 2, "Current Period Profit" to 3, "Current Period Loss" to 3), noStock)
        assertEquals(4, balanceSheetNoteIndex(report, hasStockNote = true)["Closing Stock"])

        val notes = buildBalanceSheetNotes(report, "22/10/2026", productOut = null, productIn = 25.0)
        assertEquals(listOf(1, 2, 3, 4), notes.map { it.n })
        // A loss arrives negative and is one figure: -50, ledger result -350.
        assertTrue(notes.first { it.n == 3 }.body.contains("leave -350.00"))
        assertEquals("Supplier Advances [2]", reportGroupLabel("Supplier Advances", noStock))
        assertEquals("Current Liabilities", reportSectionLabel("Current Liability"))
        assertEquals("", noteRef(null))
    }
}
