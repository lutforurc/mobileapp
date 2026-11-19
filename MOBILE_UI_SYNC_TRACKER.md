# Mobile UI sync tracker

Read this file first in any future session. It records the web revision the
Android app was last compared against, which screens were ported, and the exact
place to resume.

## Checkpoint

| | |
|---|---|
| **Web UI reviewed** | `cashbookbd_react` branch `Reseller-ui` @ `475c6ce5` (2026-11-19). Previous checkpoint was `9698fc1b` (2026-11-17) |
| **API reviewed** | `cashbook_api` branch `Reseller` @ `851b9686` (2026-11-10). Not re-read — that repo is not present on this machine |
| **Android baseline** | working tree on `Lutfor-Rahman` (1.0.8) plus the 2026-11-19 pass: Tiles purchase charges, Product Stock/Stock Details/Closing Stock strict Product filter, Purchase Ledger bill total, Ledger Details footer opening row, Attendance Approve Range, Customer list page size + Enter-to-search, Hotel folio "Generate Bill", Advance Payment wording |
| **Pass date** | 2026-11-19 |
| **Last verified on a device** | Nothing from the 2026-11-19 or 2026-11-17 passes has been tapped through. The last device checks remain the 2026-11-14 rows 16–19 (Tiles labels, product search, account menu, My Devices dates) |
| **Work in progress** | **None half-finished.** All 20 web commits in `9698fc1b..475c6ce5` that carry mobile behaviour are ported; the rest are print-designer or web-layout only (see rows 36–40) |
| **Next step** | Device pass: Tiles **Purchase** invoice (the three charge boxes and the counter's auto payment), Product Stock / Stock Details / Closing Stock (no Branch box, Product narrows), Purchase Ledger (a charged bill reads a nought balance), Ledger Details footer, Manual Attendance → Approve Range, Customers → Rows: All |

Both repos above are the **live demo branches and are to be treated as
read-only**; no file in either was modified by the 2026-11-10 pass or by this
one.

### Verification done this pass

```
2026-11-14 pass:
  cashbookbd_react @ 47d24903  (28 commits past 91062fef, static review)
  mobileapp        @ f2eff00 + uncommitted Invoice Wise Profit, Production
                     Register/Costing and Tiles date-label work
  compileDebugKotlin, testDebugUnitTest and lintDebug all ran (see below).
  Emulator booted and the APK installed; login blocked (see below).
```

The only tap-through was the Tiles invoice label check (row 16), which passed.
No report was verified: the signed-in role cannot see Invoice Wise Profit. The
app is installed on the running emulator, left on that invoice form.

### Device verification attempt (2026-11-14)

- **Emulator:** `Pixel_Stable` AVD, Android 16 / API 36.1 (Google APIs Play
  Store), 1080×1920 at 480 dpi. The AVD's configured skin `pixel_10_pro` is
  missing; it boots with an explicit `-skin 1280x2856` override.
- **APK:** `:app:assembleDebug` built and installed cleanly
  (`com.cashbookbd.app`); an older copy with a different signing key had to be
  uninstalled first.
- **Login:** a fresh install opened **Login** with the username prefilled and
  the password empty; no `dev.login.*` password is present in
  `local.properties`. A later reinstall kept the app data, and a persisted
  session reached the dashboard (Tiles and Sanitary branch, transaction date
  13/11/2026).
- **Blocker (report entry):** opened **Reports** and walked the full list. It
  contains the other statements (Profit Loss etc.) and ends at Closing Stock,
  but **Invoice Wise Profit is absent** — the signed-in role does not hold
  `invoice.wise.profit`. The web agrees: that key is granted to nobody by
  default. Granting it is a permission/settings write on the live demo account,
  so it was **not** done.
- **Not checked on device:** report entry/permission, branch/date filters,
  invoice search, 10 rows per page, newest-first order, Customer/Received
  columns, full-set totals across pages, Edit navigation, amount comparison
  with React.

To finish the P11 device pass: grant **Invoice Wise Profit** (`invoice.wise.profit`)
to the role on a safe/staging account (or use an account that already holds it),
sign in, open **Reports → Financial Statements → Invoice Wise Profit**, and
compare against React at the same branch/date filters. A fresh emulator install
also needs `dev.login.password` in `local.properties`.

```
2026-11-10 (not re-run):
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL (45 tests, incl. 4 new Cash Flow / General Ledger cases)
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
```

```
2026-11-14 (Invoice Wise Profit, P11 / row 13):
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
```

```
2026-11-14 (Production Register/Costing, P2 / row 15; Tiles labels, row 16):
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
```

```
2026-11-14 (product search labels, row 17):
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
```

```
2026-11-14 (account menu order, row 18; My Devices dates, row 19):
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
```

```
2026-11-17 (rows 20-30: Production suite, Advance for Purchase, Branch steps,
Hotel booking advance, Ledger footer, Monthly Report):
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
```

```
2026-11-19 (rows 31-40: Tiles purchase charges, strict Product filter on the
three stock reports, Purchase Ledger bill total, Ledger Details footer opening
row, Attendance Approve Range, Customer list page size, Enter-to-search,
Generate Bill, Advance Payment):
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL (52 tests, 0
                                            failures, incl. 3 new cases: strict
                                            product narrowing, purchase charges
                                            on the bill, the attendance range's
                                            day walk)
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
```

Machine note: `local.properties` was pointing at a stale SDK path
(`C:\Users\lutfo\AppData\Local\Android\Sdk`, missing on this machine). It now
points at the installed SDK, `D:\Local\Android\Sdk`, so Gradle can build.

## Status key

- **Complete** — the Android code implements the React behaviour; confirmed by
  reading the current source at `f2eff00`. Unless it also says **Verified**, it
  has not been tapped through on a device.
- **Partial** — part of it is implemented; the exact gap and file paths are in
  the notes.
- **Pending** — identified, not started.
- **Blocked** — cannot proceed; the blocker is named in the notes.
- **Web-only** — deliberately not ported, with the reason.
- **Verified** — checked on a running build (device/emulator) by a human or
  agent. Do not use this status without an actual tap-through.

The 2026-11-10 pass used "Implemented" for code-complete work; those rows are
now **Complete** (still not device-verified). The old section is kept below so
its history survives.

## Implemented in the 2026-11-10 pass (re-checked in code 2026-11-14)

| # | Web change (repo @ rev) | Screen / workflow | Status | Verification notes |
|---|---|---|---|---|
| 1 | `a62592b3` Cash & Bank Book: one row per head | Reports → Cash & Bank Book | Complete | Sends `split_heads=1`; rows keyed by `row_key ?? mtm_id` so a voucher spanning heads does not repeat its key. Files: `CashBankBookRepository.kt`, `CashBankBookScreen.kt`. Code present at `f2eff00`; build/test green in the 2026-11-10 pass. |
| 2 | `c28dc0e1` + `333f6a6e` + `d3d4eaff` + `c38e7899` Ledger Details | Reports → Customer Supplier Statement ("Ledger Details") | Complete | Transaction-type selector offers **All Transactions / Received / Payment / Sales / Purchase** (3 and 4 included). Cash labels shorten to Received/Payment. New **Inv. Amt** column between Sal. Total and Debit, computed per the web's `getInvoiceAmountValue` (purchase cash negative, sale cash positive); Debit/Credit blank on invoice rows; footer totals Debit, Credit and Inv. Amt. The arithmetic is a pure helper (`report/LedgerMoney.kt`) with four unit cases in `ParitySmokeTest`; the repository only maps JSON and lays out cells. Files: `report/ReportConfig.kt`, `report/LedgerMoney.kt`, `data/repository/GenericReportRepository.kt`. Build, tests and lint green in the 2026-11-10 pass. |
| 3 | `50e5a4da` Sales & Purchase Ledger: rule under every product line | Reports → Sales Ledger / Purchase Ledger (and the return ledgers) | Complete | A hairline rule under each stacked product line in Product & Details, Quantity, Rate and Total; the last line keeps its rule when a note follows. File: `ui/reports/TradeLedgerScreen.kt`. |
| 4 | `c705bb46` + `b9b85802` Sold Units: installment money | Real Estate → Sold Units | Complete | `installment_received` parsed on sale/customer/totals; the Received cell prints an extra "Installment" line when non-zero; the floored grand/report totals add booking receipts **and** installment money. Files: `data/repository/RealEstateSalesRepository.kt`, `ui/realestate/SoldUnitsScreen.kt`. The web's per-receipt date list inside Due is still unported. |
| 5 | `dd0b10ad` / `74eb8d57` Installment Create due dates | Real Estate → Installment Create | Complete | Due Date prints `dd/MM/yy` in the preview and saved rows; the stored/edited value stays `dd/MM/yyyy`. Android already parsed day-first, so no parsing change was needed. File: `ui/realestate/InstallmentCreateScreen.kt`. |
| 6 | `67e6364f` Branch Issue: issuing branch's stock | Branch Transfer → Branch Issue | Complete | Calls `warehouse/transfer/available-stock?branch_id=&product_id=` when a product is picked (or the from-branch changes) and shows the figure inside the Quantity box; stale answers are dropped. Files: `InventoryMovementRepository.kt`, `InventoryMovementUiState.kt`, `InventoryMovementViewModel.kt`, `InventoryMovementScreen.kt`. |
| 7 | `cee049a7` Hotel bill: discount before VAT, VAT once | Hotel → a booking's folio (bill strip) | Complete | A discounted bill reads: Charges (VAT incl.) `gross` → less discount → net → = rent (excl. VAT) + each VAT band. Undiscounted bills keep the old strip. No arithmetic is done locally; the server's totals are printed. File: `ui/hotel/HotelFolioScreen.kt` (`WorkingLine`). |
| 8 | `fd4c4e3d` Hotel reports: VAT collection by rate | Hotel → Reports | Complete | New **VAT by rate** tab: From/To inclusive, a tile with "VAT collected · N rate(s)", a table (VAT Rate / VAT Collected), a Grand Total footer and the server's note. Reads `hotel-setup/reports/vat-summary`. The tab strip is `PrimaryScrollableTabRow` so five labels fit a phone. Files: `HotelOpsRepository.kt`, `ui/hotel/HotelReportsScreen.kt`. |
| 9 | `fb6c2836` Real Estate: keep project/building after save | Real Estate → Project Expense / Project Income | Complete | Picking an account no longer clears the project/building; both survive a saved line and a saved voucher. The project box stays open until an account is picked; building follows the project. A line that is not an expense/income head still saves untracked. Files: `ProjectExpenseViewModel.kt`, `ProjectExpenseScreen.kt`, `ProjectIncomeViewModel.kt`, `ProjectIncomeScreen.kt`. |
| 10 | `609fa5f6` Sales Summary: total every filtered record | Real Estate → Sales Summary | Complete (no code change needed) | The Android screen already prints the server's `totals` block rather than re-adding visible rows. The server includes installment receipts, so Received/Due agree with Sold Units. `ui/realestate/SalesSummaryScreen.kt`. |
| 11 | `08ca7cf4` Cash Flow Statement (indirect method) | Reports → Financial Statements → Cash Flow Statement | Complete | New bespoke screen under the two Trial Balances, gated on `cash.flow.statement`. Filter: branch + date range, opened year-to-date from the branch's business date. Reads `reports/cash-flow-statement` (GET, `branch_id`/`start_date`/`end_date`, `yyyy-MM-dd`). Row wording and order ported rule-for-rule from `cashFlowStatementRows.ts` (`ui/reports/CashFlowRows.kt`): operating → non-cash → working capital → subtotals, investing, financing, optional "Other movements not classified", net increase/decrease, opening/closing cash, and the two ledger-difference rows when the server says it does not reconcile. Four summary tiles and the web's basis sentences. Files: `data/repository/CashFlowStatementRepository.kt`, `ui/reports/model/CashFlowStatement.kt`, `ui/reports/CashFlowRows.kt`, `CashFlowStatementUiState.kt`, `CashFlowStatementViewModel.kt`, `CashFlowStatementScreen.kt`. No print/Excel export — print stays web-only by the existing policy. |
| 12 | `08ca7cf4` General Ledger | Reports → Financial Statements → General Ledger | Complete | New bespoke screen gated on `general.ledger`. Filter: branch, optional searchable **Accounts Head** (`reports/general-ledger/accounts?search=`; blank = All Accounts), start/end dates opened year-to-date. Reads `reports/general-ledger` (GET) page by Accounts Head at the web's 25 per page; four total tiles summed over the full filtered set; one card per head with its group trail and code, a leading Opening Balance row, every posted entry, the server's per-row running balance, a Total footer and a Closing Balance footer; Previous/Next pager. The web's Voucher-cell link is carried as an ACTION pencil through the existing `VoucherEdit` permissions. Files: `data/repository/GeneralLedgerRepository.kt`, `ui/reports/model/GeneralLedger.kt`, `GeneralLedgerUiState.kt`, `GeneralLedgerViewModel.kt`, `GeneralLedgerScreen.kt`. |
| 13 | `f0028a3b` + `c59b2cfb` Invoice Wise Profit | Reports → Financial Statements → Invoice Wise Profit | Complete | New bespoke screen gated on `invoice.wise.profit`. Filter: branch, **Search Invoice** (invoice number; blank = all), start/end dates opened on the branch's month so far (`trx date` day 1 → trx date). Reads `POST reports/invoice-wise-profit` with `branch_id`, `startDate`, `endDate`, `search`, `page`, `per_page` (`yyyy-MM-dd`). Columns: #, Inv. No., Date, Customer, Inv. Amount, Received, Discount, Profit; footer totals from the server's whole-filtered-set `summary`, not the page; Previous/Next pager. Amounts follow the web: zero prints "-" except **Received**, which prints "0.00" at a minimum of two decimals; negative profit is red (the web's `formatReceivedAmount` and `thousandSeparator`). The row's Edit pencil uses the existing `VoucherEdit` permissions, and paging re-asks with the applied filters. Print stays web-only by policy. Files: `data/repository/InvoiceWiseProfitRepository.kt`, `ui/reports/model/InvoiceWiseProfit.kt`, `InvoiceWiseProfitUiState.kt`, `InvoiceWiseProfitViewModel.kt`, `InvoiceWiseProfitScreen.kt`, plus registry/routes in `report/ReportConfig.kt`, `report/ReportEndpoints.kt`, `navigation/MenuGroups.kt`, `navigation/ParityGraph.kt`, `ui/reports/ReportsHomeScreen.kt`, and `di/ServiceLocator.kt`. Compile, unit tests (incl. 1 new formatting case) and lint green 2026-11-14; not device-verified. |

## Implemented 2026-11-14

| # | Web change | Screen / workflow | Status | Verification notes |
|---|---|---|---|---|
| 13 | `f0028a3b` + `c59b2cfb` Invoice Wise Profit | Reports → Financial Statements → Invoice Wise Profit | Complete | Implemented this session; see the row 13 note above for the full description and file list. `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` and `assembleDebug` all BUILD SUCCESSFUL on 2026-11-14. Device pass attempted: emulator booted, APK installed, signed-in role's Reports list does not contain the report because `invoice.wise.profit` is not granted, so **not Verified** — see "Device verification attempt". |
| 15 | `9f3c02bd` + `ae19ca1e` + `47d24903` Production Register and Batch Costing | Production → Production Register / Batch Costing | Partial | First P2 slice. New `production/ProductionMenu.kt` (menu requires a `production.*` key **and** `manufactur_control`), `ui/production/ProductionHomeScreen.kt`, `ProductionRegisterScreen.kt` (From/To month-to-date, four summary tiles: Net sales, Cost with allocated, True gross profit, Abnormal loss; 14-column batch table with multi-output lines, expiry, loss/abnormal, recipe, variance, source-changed flag and Detail link), `ProductionCostingScreen.kt` (master facts; recipe variance; Inputs; consumed purchase layers; allocated expense lines; output layers), plus `data/repository/ProductionRepository.kt`, models, UI state/VM and route gates. Reads `production/report/register?from_date&to_date` and `production/report/costing/{id}`. Print stays web-only. Remaining P2 screens: Entry, Recipe, Sales, Sales Return, Purchase, Scheme, Free Goods, Near Expiry. Files also touched: `session/Settings.kt`, `data/remote/dto/SettingsResponse.kt`, `data/repository/SettingsRepository.kt`, `navigation/{AuthenticatedShell,ParityGraph,DrawerMenus,MenuGroups}.kt`, `di/ServiceLocator.kt`. Compile/test/lint green; not device-verified — the signed-in branch's drawer showed no Production entry, consistent with `manufactur_control` being off (and/or no `production.*` grant), so the screens were not opened. |
| 16 | Mobile-only fix (owner report, 2026-11-14) | Invoice → Tiles Sales/Purchase → Memo/Challan rows | Verified | All four labels are now **persistent** inside their boxes: a small label line above the value in each field, never a placeholder that vanishes on typing. Both date boxes also carry `dd/mm/yyyy` until a date is picked. The shared 52dp frame needed a fixed 16dp label line so the input could not squeeze it away. File: `ui/invoice/TilesInvoiceScreen.kt` (`InlineLabelTextField`, `InlineLabelDateField`). Verified on `Pixel_Stable`: opened Invoice → Sales, all four labels visible; typed `123` into Memo No. and its label stayed; cleared the test value. Test/lint/assemble green. |
| 17 | Mobile-only fix (owner report, 2026-11-14) | Invoice → Sales / Purchase (standard and Tiles) → product search | Complete | Results and the selected value now read **"Product Code — Product Name"**; a product with no code shows only its name. Added `InvoiceProduct.dropdownLabel` and `TilesProduct.dropdownLabel`, used by `InvoiceFormViewModel.searchProducts`, `InvoiceFormScreen.ProductEntry`, `TilesInvoiceScreen.searchProducts` and its selected-product field. Selection stays keyed on the product **id** (`productCache[option.id]`), so two products sharing a name (with different codes) remain distinct. Unit test covers the label and distinct ids. Device: on `Pixel_Stable` Sales Invoice, searched and selected a result; the line added was `GVSTRM-26602 - 10.31" X 24"- Homogenious Stair` at 50 Pcs × 210, i.e. the right product. The popup's own label text could not be captured by UIAutomator (separate window); it is unit-tested. Test/lint/assemble green; form reset afterwards. |
| 18 | Mobile-only fix (owner report, 2026-11-14) | Account menu order | Verified | Reordered to the owner's numbered sequence: Dashboard, Profile, My Tasks, (Switch Branch when offered), My Devices, Subscription, Light, Full Screen, Change Password, Request Account Deletion, then Log Out. The two security/destructive actions carry `afterToggles = true` so they render below the theme/full-screen switches instead of above them. File: `ui/components/AccountMenu.kt` (`AccountMenuItem.afterToggles`, `AccountMenuEntry`, `accountMenuItems`). Verified on `Pixel_Stable` with the menu open; test/assemble green (lint not re-run for this UI-only order change). |
| 19 | Mobile-only fix (owner report, 2026-11-14) | My Devices → Last used / Signed in | Verified | Timestamps now read `dd/mm/yy HH:mm:ss` instead of the raw `yyyy-mm-dd HH:mm:ss`. Added `DateFormat.dayMonthYearShort` (compact year, keeps the clock; normalises a `T`/`Z` timestamp); `MyDevicesScreen` uses it for both rows. Unit test covers ISO, `T…Z`, day-first and blank inputs. Verified on `Pixel_Stable`: `14/11/26 10:39:05`, `14/11/26 08:44:56`, `13/11/26 12:06:55`, `13/11/26 11:10:30`. Test/assemble green. |

## Implemented 2026-11-17 (React `47d24903..9698fc1b`)

| # | Web change | Screen / workflow | Status | Verification notes |
|---|---|---|---|---|
| 20 | Production module (web `ProductionEntry.tsx`, `ProductionSales.tsx`, `ProductionPurchase.tsx`, `ProductionSalesReturn.tsx`, `ProductionScheme.tsx`, `Recipe.tsx`) | Production menu | Complete | `production/ProductionMenu.kt` now lists Entry, Sales, Purchase, Sales Return, Scheme, Recipe, Register, Free Goods and Near Expiry; every screen is gated on its own permission and the drawer still requires `manufactur_control`. Routes live in `navigation/ParityGraph.kt`. |
| 21 | `ProductionEntry.tsx` | Production → Production Entry | Complete | Four steps (Materials in / Products out / Waste & Labour / Recent batches). Recipe fill and re-fill at a make quantity, stock-on-the-date check, outputs with sales price + typed share (auto share by sales value), loss tolerance/basis/reason, allocatable expense lines, draft save/update, Post / Reverse / Delete, Edit draft. Files: `data/repository/ProductionRepository.kt` (`production/list`, `allocatable-expenses`, `available-stock`, `details/{id}`, `store|update`, `post|reverse|destroy/{id}`), `ui/production/ProductionEntryViewModel.kt`, `ProductionEntryScreen.kt`, models in `ui/production/model/Production.kt`. Deviation: no warehouse picker (godown sent null) and no package-unit entry; both noted under P2. |
| 22 | `ProductionSales.tsx`, `ProductionPurchase.tsx`, `ProductionSalesReturn.tsx` | Production → Sales / Purchase / Sales Return | Complete | One shared form (`ProductionInvoiceScreen.kt` + `ProductionInvoiceViewModel.kt`): find a voucher by Vr. No., party search (`chart_of_accounts/ddl/l4-list`, acType 3), bill no/date, note, discount, paid/received/refund with the cash party (17) locked, free line at price 0, scheme free row added automatically on a sale (`production/schemes/match`), stock-shortage question with Continue Save on a sale. Payloads match the web: purchase `paymentAmt`, sale `receivedAmt`, return `supplier_id`/`sales_invoice_number`/`sales_invoice_date`/`netpayment`. Edit endpoints are POST `{invoiceNo}`. Printing stays web-only. |
| 23 | `ProductionScheme.tsx` | Production → Scheme | Complete | Buy-X-get-Y form with same-product switch, threshold/free qty, from/to, repeatable (Bengali wording), list with edit and retire (retire never deletes). Files: `ProductionSchemeScreen.kt`, `ProductionRepository.fetchSchemes/saveScheme/retireScheme`. |
| 24 | `Recipe.tsx` | Production → Recipe | Complete | Recipe tab + Recipe List tab, header Save/New version/Clear, materials editor (kind material/packaging, note), version copy with a confirmation, retire/remove. Files: `RecipeRepository.kt`, `RecipeViewModel.kt`, `RecipeScreen.kt`, `model/Recipe.kt`. |
| 25 | `FreeGoodsReport.tsx`, `ExpiryReport.tsx` | Production → Free Goods / Near Expiry | Complete | Free Goods: from/to, per-row qty × unit cost = value, Total qty/cost and the Bangla note. Near Expiry: as-of + days, days left (expired in red), batch made vs product on hand, row opens Batch Costing. Files: `ProductionReports.kt`, `ProductionRepository.fetchFreeGoods/fetchExpiry`. |
| 26 | `purchase-advance/*` (new module) | Transaction → Advance for Purchase | Complete | List with In-hands / Owed / Entries-dated tiles, head-vs-rows integrity banner, Give Payment / Advance Settlement (employee, amount, cash/bank, bank account, note), Show-everyone switch + Refresh, and the employee ledger dialog with From/To, opening, goods under each bill, totals and closing. Files: `data/repository/PurchaseAdvanceRepository.kt`, `ui/purchaseadvance/PurchaseAdvanceViewModel.kt`, `PurchaseAdvanceScreen.kt`. Menu/permission `purchase.advance.view` (+ `.create` for entry), Transaction group, `Route.PURCHASE_ADVANCE`. Print and the bill-paper viewer stay web-only. |
| 27 | `AddBranch.tsx` (2026-11-14 + `booking_sms`) | Branch → Dropdown / Hotel Setup steps | Complete | Two new wizard steps: **Dropdown** (party/product search line counts and the show-code/mobile/address/father, category/brand/group/name/prices/unit/balance switches) and **Hotel Setup** (check-in/out times, hold hours, VAT/Due/Income modes with the web's wording, registration-card terms). Feature Controls gained **Hotel Booking SMS**; `sms_service` is now owner-only, as on the web. Files: `ui/branch/BranchForm.kt`, `AddBranchUiState.kt`, `AddBranchViewModel.kt`. |
| 28 | `BookingFormScreen.tsx` (booking advance) | Hotel → New Booking | Complete | An **Advance (optional)** block on a new paid booking: amount, How (Cash/Bank/Card/Mobile), the till filtered by side, Reference, and the advance-receipt note. Sent as `advance_amount`/`advance_method`/`advance_coa4_id`/`advance_reference` inside the booking's own store call; tills come from `hotel-setup/bookings/folio/tills`. Files: `HotelNewBookingScreen.kt`, `HotelRepository.storeBooking`. |
| 29 | `ledgerWithProductUtils.ts` / `LedgerWithProduct.tsx` (footer) | Reports → Ledger Details footer | Complete | The footer is now one right-aligned line: Opening · Pur. Qty · Sal. Qty · Pur. Amt · Sal. Amt · Inv. Amt · Debit · Credit · Closing, with `Closing = Opening + Sal. Amt − Pur. Amt − Inv. Amt + Debit − Credit` and the opening row excluded. Files: `ReportConfig.ledgerFooter`, `GenericReportScreen.ledgerFooterLine`. |
| 30 | `MonthlyReport.tsx` | Reports → Monthly Report | Complete | The five money columns now total in a **Grand Total** row (the web computes its tfoot from the same rows). File: `report/ReportConfig.kt`. |

## Implemented 2026-11-19 (React `9698fc1b..475c6ce5`, 20 commits)

| # | Web change | Screen / workflow | Status | Verification notes |
|---|---|---|---|---|
| 31 | `5bfe8ab8` + `05e57518` Purchase Invoice charges | Invoice → Purchase (Tiles) | Complete | A Tiles purchase bill now carries the same three heads the sale carries on the way out: **Carrying Inward** (coa4 197, or 198 on an older chart), the tax head the branch names **TDS/VAT** (41) and **Service Charge** (42). All three are folded into Current Invoice, into the cash counter's auto payment, and posted in the save body. Each box is named by the account it posts to — `settings/service-list` is now loaded for a purchase too, as the sale already did. Reopening a bill reads the three back off the voucher's own ledger legs, either side of the book (`findCoaAmount`: debit then credit, across every leg). Files: `ui/invoice/TilesInvoiceScreen.kt` (`carryingInwardAmt`, `onCarryingInward`, `billAmount`, `invoicePayload`), `data/repository/InvoiceEdit.kt` (`InvoiceEdit.carryingInwardAmt`, `amountOf`). Unit test added for the bill total and the return's exemption. |
| 32 | `475c6ce5` Product Stock, Stock Details, Closing Stock: Branch out, Product in | Reports → Product Stock / Stock Details / Closing Stock | Complete | The Branch box is gone from all three — each follows the **login's own branch** (`GenericReportViewModel.ownBranchId`). A matching **Product** box sits beside Category and narrows **strictly**: the request carries `product_id`, and the answer is narrowed again **before grouping**, so the Brand/Group/Category headings, group totals and Grand Total all follow the picked product. The id decides whenever a row carries one; the picked name is the fallback for a closing-stock answer that arrives without ids (`report/ProductNarrowing.kt`, unit-tested). The narrowing reads the product the rows were **asked for**, so picking another without Apply leaves the table standing. Product Stock now also posts its Search box as `product_name` (`ReportConfig.searchParam`), as the web does. Files: `report/ReportConfig.kt` (`hideBranchField`, `strictProductNarrowing`, `searchParam`), `GenericReportUiState.kt`, `GenericReportViewModel.kt`, `GenericReportRepository.kt`, `GenericReportScreen.kt`. |
| 33 | `b8321400` Purchase Ledger: the bill's total carries its charges | Reports → Purchase Ledger | Complete | The Total column and the footer now read `purchase_master.total` (which holds the bill's carriage, tax and service charge) instead of summing the goods lines, and the Balance is worked from it — so a bill paid in full reads a nought balance rather than −1,800. Falls back to the detail sum for a payload with no master total or a goods-only bill; Sales is untouched. Files: `data/repository/TradeLedgerRepository.kt` (`TradeLedgerRow.billTotal`, balance, `totals()`), `ui/reports/TradeLedgerScreen.kt`. |
| 34 | `a45d4862` Ledger Details: the footer's Debit and Credit are the column totals | Reports → Customer Supplier Statement (Ledger Details) | Complete | The opening row is a row of the table, so its figure now counts inside the Debit (or Credit) column total, and the closing is worked from those same totals. A statement filtered by product carries no opening row, so there the opening is still added by hand — otherwise the closing would lose it. File: `ui/reports/GenericReportScreen.kt` (`ledgerFooterLine`). |
| 35 | `d236bd59` Attendance list: Approve Range | HRM → Manual Attendance | Complete | An **Approve Range** button beside Load takes the Fill From/To days, reads each day one at a time (the path the daily list already uses), approves what came back still pending in ten-entry groups, and then **reads the touched days back** so the message reports what actually changed rather than what the server accepted. Leave days and already-approved rows are skipped; the range is capped at 31 days. Files: `ui/hrm/ManualAttendanceScreen.kt` (`approveRange`, `attendanceRangeDays` — unit-tested), `ui/hrm/model/HrmModels.kt` (`isLeaveDay`), `data/repository/HrmRepository.kt`. |
| 36 | `ecc4602d` Customer list: All turns the page size into every row | Customers → list | Complete | A **Rows** selector with the web's choices (10/20/30/50/100/All). "All" is `per_page=0`, which the API answers with every matched row; it is a real choice, not the ten-row default, so the select shows it and the row numbering starts at 1. Files: `ui/customer/CustomerListUiState.kt` (`CUSTOMER_PAGE_SIZES`, `perPageValue`), `CustomerListViewModel.kt` (`onPageSize`), `CustomerListScreen.kt`. |
| 37 | `e543ab32` Search runs on Enter | Customers → search box; Sales/Purchase Return Ledger → search box | Complete | Enter in the search box now runs that screen's own Search/Apply — the same handler, filters and page reset a button click uses. Files: `ui/customer/CustomerListScreen.kt`, `ui/reports/TradeLedgerScreen.kt` (`imeAction = Search`, `KeyboardActions`). The web's Add-Customer Ctrl+S has no equivalent on a touch device. |
| 38 | `b57c609b` Hotel folio: the bill button says Generate Bill | Hotel → a booking's folio | Complete | The bill action, its confirmation dialog and the charge panel's hint now say **Generate Bill** / **Bill Generated**, as the web does. Files: `ui/hotel/HotelFolioScreen.kt`. |
| 39 | `ec1d961c` Purchase Advance: "Advance Payment" | Transaction → Advance for Purchase | Complete | The first segment and its submit button now read **Advance Payment**, the web's wording. Files: `ui/purchaseadvance/PurchaseAdvanceScreen.kt`, `PurchaseAdvanceViewModel.kt` (`submitLabel`). |
| 40 | `72f1cf52`, `c68be285`, `1cb2f321`, `cbe6651d`, `1e30e6bf`, `ca894d5b`, `837e27e5`, `58c46257`, `cbc35c43` | Print designer, report filter layout, sidebar icon | Web-only | The amount/due-in-words template fields, the branch Money Format wording in print, the Ledger Details print sheet and its totals band, the Business Summary / Product Profit Summary / Trial Balance filter-line layout, and the Hotel sidebar icon are all web surfaces this app does not have (print stays web-only by policy; the financial statements in those layout commits — Business Summary, Product Profit Summary — are still unported on Android, see P1). |

## Pending and partial (verified against React 475c6ce5)

| # | Web change | What is owed on Android | Status | Notes / next step |
|---|---|---|---|---|
| P1 | `ac5af26f` (Monthly Statement), `649f9f81` (P&L Date Wise), `74921537` (Business Summary), `6d3edbbb` (Product Profit Summary), `87782eb7` (Product Statement), `b3d99a2e` (Customer Sales Report), `eaac018d` (Tiles Purchase), plus `f0028a3b` / `c59b2cfb` (Invoice Wise Profit) | The rest of the **Financial Statement** report group | Partial | **Done:** Cash Flow and General Ledger (rows 11–12); **Customer Sales** is native (`ui/reports/CustomerSalesScreen.kt`, `report/ReportConfig.kt:1199`, `reports/customer-sales`); **Product Statement** exists but under the wrong group/permission — Android route `reports/product-statement` → `ui/products/ProductStockStatementScreen.kt`, gated `product.tracking.report.view`, while React now serves `/product/product-statement` under Products on `product.statement.view`. **Done since this row was written:** Invoice Wise Profit (row 13 / P11) — `POST reports/invoice-wise-profit`, `invoice.wise.profit`. **Missing entirely** from `report/ReportConfig.kt` and `report/ReportEndpoints.kt`: Monthly Statement (`reports/monthly-statement`), P&L Date Wise (`reports/profit-loss-date-wise`), Business Summary + detail (`reports/business-summary`, `reports/business-summary-detail`), Product Profit Summary (`reports/product-profit-summary`), Tiles Purchase (`/reports/tiles-purchase`, `tiles.purchase.report`). The Financial Statements group in `navigation/MenuGroups.kt` also lacks these entries, and React's `financial_statement` permission list (Sidebar `menuPermissions.ts`) now has a separate key per statement. |
| P2 | `9f3c02bd`, `bf4a31d7`, `e88c85f0`, `41895d37`, `a40a8003`, `615f5b80`, `ae19ca1e`, `8a607df7`, `d323b45f`, `47d24903`, `c12fe267` (Production / Manufacturing) | A Production / Manufacturing module | Partial | **Done (row 15):** Production menu + branch-switch/permission gate, Production Register and Batch Costing, with repository/models/routes. **Remaining:** Production Entry, Recipe, Production Sales, Production Sales Return, Production Purchase, Scheme, Free Goods, Near Expiry. React now has **nine screens** — Production Entry, Production Sales, Production Purchase, Production Sales Return, Scheme, Recipe, Production Register, Free Goods, Near Expiry — plus Batch Costing (`/production/costing/:id`). Routes in `cashbookbd_react/src/components/services/appRoutes.tsx:103-114`; API endpoints in `apiRoutes.tsx:344-402` (`production/list`, `voucher-types`, `details`, `allocatable-expenses`, `available-stock`, `input-units`, `store|update|post|reverse|destroy`, `report/register`, `report/costing`, `report/free-goods`, `report/expiry`, `sales/*`, `sales-return/*`, `purchase/*`, `schemes*`, `recipes*`). Phases 1–4 are complete on the web (`docs/Manufacturing/v4-manufacturing-production-plan.md`): free rows at price 0, old voucher numbers (`3-/10-/4-/9-/13-`) with `main_trx_master.invoice_workflow = production`, optional expiry per output, package-unit conversion, and `denyUnlessVoucherEditable()` refusing edits from the old forms. Menu/help changes (`c12fe267`, "menu stays open") are **web-only**. Also needs `production.*` permissions **and** `manufactur_control`; Accountant is read-only (`production.view`, `production.report.view`). **2026-11-17 update:** every listed screen is now implemented on Android (rows 20–25): Entry, Sales, Purchase, Sales Return, Scheme, Recipe, Register, Costing, Free Goods, Near Expiry. What is deliberately not carried: the web's validation/error wording (the server's message is shown instead), batch-field validation for `production/voucher-types`, and the print papers. Still owed as layout work: a warehouse picker on Entry (godown travels as null), package-unit entry (`production/input-units`; quantities are typed in the stock unit), and lifting the shared Production voucher print. |
| P3 | `2acca618` Hotel Setup: Income Setup | Branch Hotel Setup step | Complete | **2026-11-17:** the Hotel Setup step now exists on Android (`BranchForm.kt`) with check-in/out times, hold/max hours, VAT mode, due mode, income mode and registration-card terms, plus the Booking SMS switch under Feature Controls (row 27 in the 2026-11-17 table). Forms Setup remains the one wizard step still not carried — see P16. |
| P4 | `5e2a1d28` Hotel booking: two-column screens, folding floors, Enter walks the form | Hotel → New Booking / booking screens | Pending | A desktop-layout change. Android is one-column (`ui/hotel/HotelNewBookingScreen.kt`) and groups rooms by building/floor in a `LazyColumn` (`:451`) rather than folding them. The one-column layout is already the right phone form; check keyboard order (`imeAction`) and floor grouping against the React change before doing anything. Low priority, layout adaptation only. |
| P5 | `efeb7fb5` Hotel Layout: full-width buildings, seven rooms to a line | Hotel → Layout | Pending | Web drawing change. `ui/hotel/HotelLayoutScreen.kt` draws its own mobile grid; decide whether the room-per-line count needs adapting to phone width rather than copying seven. |
| P6 | `fb6c2836` DdlMultiline relation label | Party dropdown labels | Web-only | Android's party/account dropdowns do not print a relation label at all. `label_5` exists in Android but is the product **unit** in the product dropdown (`data/repository/InvoiceRepository.kt:575,614`; `ui/invoice/TilesInvoiceScreen.kt:106,377`), not a party relation. Nothing to port unless the multiline party row is added later. |
| P7 | `66c1f7e4` / `3ec373d9` Real Estate customer-wise installment statement | Print sheet | Blocked | The print-designer / bespoke print engine is web-only in this app (documented policy). The screen-side data is already on Installment Create. |
| P8 | `823ca96d` No negative numbers: one rule for every numeric field (2026-10-30) | Every numeric entry field | Pending | Audited this pass. React has one rule in `cashbookbd_react/src/components/utils/utils-functions/nonNegative.ts` (`isNegativeNumeric`, `NEGATIVE_VALUE_MESSAGE`, `findNegativePath`) and enforces it in `utils/fields/InputElement.tsx:117,210`, with an explicit `allowNegative` exception for party/money opening balances. Android's shared field `ui/components/AppTextField.kt` has **no negative guard**, and amount fields use `KeyboardType.Decimal` (which accepts `-`), e.g. `ui/invoice/InvoiceFormScreen.kt:174-459`. Add one shared guard to `AppTextField`/`FieldTextInput` (and a payload check before submit), with the opening-balance exception. Also audit `ui/imports/OpeningImportScreen.kt` and `ui/realestate` amount boxes. |
| P9 | `a62fc7df`, `164b152a`, `ceedbfef`, `6efdff2c`, `8430b545`, `2d0d2762`, `cb489067` (sidebar/account-menu furniture) | Drawer / account menu | Web-only | These restyle the web sidebar and account dropdown to match the mobile app's own icons and grouping; the Android drawer is the source those changes were copied from. No port. |
| P10 | `8b3a8934` Help topic browser, `44a3f869` hotel help | Help | Web-only | The help JSON browser is a web surface this app does not have. |
| P11 | `f0028a3b` + `c59b2cfb` Invoice Wise Profit | Reports → Financial Statements → Invoice Wise Profit | Complete | Implemented 2026-11-14 — see row 13. Registry entry, endpoint, menu group, route and screen all wired; `invoice.wise.profit` gates the menu and the deep link. Compile, unit tests and lint green; not device-verified. |
| P12 | `380ec256` Fixed Assets: the register posts its own journal | Assets → Register → add/edit asset | Pending | React (`AssetRegisterForm.tsx`) adds a "Post this to the books" toggle (unposted rows only), a counter-account searchable dropdown whose `acType` excludes income/expense/manufacturing (`['3','4','5']`), a journal preview (`Dr <category> cost · Cr Accumulated Depreciation opening · Cr <counter> net`), and re-posts on edit until a year is charged ("old voucher withdrawn, new number issued"). Verified absent on Android: `data/repository/AssetRepository.kt:137-154` (`saveAsset`) sends only branch/category/code/name/serial/location/purchase_date/cost/opening_accum_dep/opening_as_on/notes — no `post_to_books`, no `counter_coa4_id`; `ui/asset/AssetRegisterFormScreen.kt:235-404` still states "Nothing is posted from this box". Needs API confirmation (`asset/register/store` fields) before wiring the form. |
| P13 | `380ec256` + `b5c70985` Fixed Assets: Setup (chart) tab and tab order | Assets → Setup / chart of accounts | Pending | React `AssetChartSetupTab.tsx` reads `GET asset/setup` and posts `asset/setup/create`, listing the four group heads, the two shared heads and every category's gaps, and making only what is missing. Android has **no** `asset/setup` call, screen, route or menu entry (`asset/AssetMenu.kt:100-113` lists Categories, Items, Register, Under construction, Depreciation, Schedule, Handovers, Verification). Add a Setup screen plus `AssetMenu` item. React's tab order (b5c70985) is Items → Register → Handovers → Verification → Categories → Depreciation → Schedule → Under construction → Setup; Android's list order differs. The "kept-mounted / never blank tab" part is a web tab-mounting fix and does **not** apply to Android's one-screen-per-tab navigation. |
| P14 | `380ec256` Fixed Assets: category heads made on save | Assets → Categories → new/edit | Partial | React `AssetCategoryForm.tsx` hides the four head pickers on a **new** category (server creates them), labels the empty option "Made on save", and shows the button as "Update" on an edit. Android `ui/asset/AssetCategoryFormScreen.kt` always shows the four head boxes (`:315-340`) with the old "Not chosen yet" semantics and the button reads "Save" (`:365`). Change the form to hide heads when `form.id` is null, update the help text, and switch the label for an existing category. |
| P15 | `380ec256` Fixed Assets: Accounts Transaction moves from Hotel into Assets | Assets → Accounts Transaction (asset purchase) | Partial | The screen exists but in the wrong place and on the wrong permission. Android: `ui/hotel/HotelAssetPurchaseScreen.kt`, menu key `hotelAssetPurchase` under Hotel Operations (`hotel/HotelMenu.kt:35,81`), `navigation/MenuGroups.kt:163`, gated on the hotel parent list (`hotel.building/floor/room.type/resource/booking.view`). React: the entry is under the **Assets** menu, labelled "Accounts Transaction", gated on its own key `hotel.accounts.transaction.create` (the id/permission keep the `hotel_` spelling on purpose). Move the menu entry and change the gate; the route and screen can stay. |
| P16 | `7a72b0f0` Order Management and Tiles & Sanitary invoices in one branch | Invoice → Sales / Purchase, and report Edit links | Partial | Android can render both forms (`ui/invoice/TilesInvoiceScreen.kt`, `ui/invoice/InvoiceFormScreen.kt`) but chooses one **solely from the branch flag**: `ui/invoice/TilesCommon.kt` (`TilesBranch.isTiles`), `ui/invoice/InvoiceHomeScreen.kt:61,111-114`, `navigation/VoucherEdit.kt:103-128`, `ui/components/VoucherEditAction.kt:53`. React adds: a "Both — choose per invoice" option in Branch Setup → Forms Setup (`AddBranch.tsx:323-345`), a switch on the invoice screen (`InvoiceWorkflowSwitch.tsx` + `invoiceWorkflow.ts`), storage of the writing form in `main_trx_master.invoice_workflow` (`order` \| `tiles`, and now `production`), `?workflow=` handling with an `auto` pin for legacy invoices, and report Edit links that reopen the stored form. Android's branch form (`ui/branch/BranchForm.kt:180-237`) has **no Forms Setup step or invoice-variant selector at all**, and no code reads `invoice_workflow`. Port the branch selection first, then the switch, then the stored-workflow routing in `VoucherEdit`. |
| P17 | `21100e30` One permission per menu; `a4f04bbc` Admin Notifications; `c1d9c2ab` Inventory Systems / Business Types; `588f649c` own permissions for five screens | Menu, route and screen permission keys | Partial | React now gives every menu entry its own key (Sidebar `menuPermissions.ts`, "one rule" comment). Android still reuses older/broader keys in several places. Confirmed mismatches: **Admin Notifications** — React `admin.notifications.view` vs Android `reseller.view`/`subscription.view`/`all.user.view` (`admin/AdminMenu.kt:63-66`). **Inventory Systems** — `inventory.systems.view` vs the same trio (`AdminMenu.kt:78-81`). **Business Types** — `business.create` vs the same trio (`AdminMenu.kt:74-77`). **Cash & Bank Book** — `bankbook.view` vs `cashbook.view` (`report/ReportConfig.kt:598-605`). **Daily Account Book** — `daily.accounts.book` vs `cashbook.view` (`report/ReportConfig.kt` `dailyAccountBook`). **Received/Payment Entry** — `received.entry.create` / `payment.entry.create` vs the hotel parent list (`hotel/HotelMenu.kt:82-83`). **Trading/Tiles combined invoice** — `trading.combined.create` / `tiles.combined.create` vs the old `purchase.create` + `sales.create` pair (`invoice/InvoiceMenu.kt`). **HRM** — React `hrm.attendance.view`, `hrm.leave.view`, `festival.bonus.generate`, `employee.loan.balance.view`, `festival.bonus.view`, etc. vs Android `attendance.view`, `leave.view`, `salary.generate` (`hrm/HrmMenu.kt`). **Real Estate** — React `real.estate.area.view` and `real.estate.installment.create` vs Android `real.estate.view` (`realestate/RealEstateMenu.kt`). **Hotel** — React `hotel.calendar.view`, `hotel.amenity.issue.view`, `hotel.booking.due.view` vs `hotel.booking.view`/`hotel.housekeeping.view` (`hotel/HotelMenu.kt`). **Products** — `product.statement.view` (see P1). **Product Tracking summary** — `product.tracking.summary.view`. **Delivery** — split delivery keys. Before changing any key, read `cashbook_api` `config/route_permissions.php`: the API repo was not available this pass, and the server may still create the old names. |
| P18 | `21100e30` Login History shows how a session ended | Admin → Login History | Partial | Android renders Login History from the generic list (`applist/AppLists.kt:398-435`) with `result`, `reason`, `login_input`, `company`, `branch`, `in_time`, `out_time`, `duration_minutes`, `ip_address`, `computer_name`, plus the Result filter. It does **not** use `out_reason` (React shows "Last active · logged in again" / "Last active · device removed" under the out time) and prints duration as raw minutes where React writes "7h 25m". Add an `out_reason`-aware cell and word the duration. |
| P19 | `588f649c` Hotel Bookings: hall rooms and seats in the Holds column | Hotel → Bookings list | Partial | The booking forms and repository already support seats and hall sittings (`ui/hotel/HotelNewBookingScreen.kt`, `HotelHallBookingScreen.kt`, `HotelRoomFormScreen.kt`, `data/repository/HotelRepository.kt`). The **list** does not show them: `ui/hotel/HotelBookingsScreen.kt:507-530` prints only "N rooms, G guests" in the PARTY column. React's Holds cell prints "N Hall Room" plus `hall_seats` Seats, and guests only under rooms. Add sittings/seats to the list row (and hide guests for a hall-only booking). |

## Deliberately not re-audited this pass

The previous parity document (`WEB_PARITY_2026_08_01.md`) records the app as
matched through react `1e7644af` / api `f4f87e2f`, and the Android history shows
parity passes on 2026-10-26 and 2026-10-30 (design-system refresh, ledger edit,
account deletion). The 2026-11-10 pass read the gap forward from the
**2026-11-05** icon commit (`587361b`) — the 2026-11-05 → 2026-11-10 web window
plus the immediately preceding report/production additions — and then ported
`08ca7cf4`'s two statements (rows 11–12).

This pass (2026-11-14) read `cashbookbd_react` `91062fef..47d24903` (28 commits)
against the Android tree at `f2eff00` and: re-checked rows 1–12, re-opened
P1–P10, and added P11–P19 for the new React work (Invoice Wise Profit; Fixed
Assets register journal, Setup tab and category/Accounts-Transaction movement;
invoice workflow "Both"; the one-key-per-menu permission pass; Login History
session-end; hall rooms/seats on Bookings). It then implemented **Invoice Wise
Profit** (row 13 / P11), the first **Production** slice — menu, Register and
Batch Costing (row 15) — and the Tiles memo/challan date-label fix (row 16) on
Android. It did **not** read `cashbook_api` (not present locally); the API
revision above is therefore still the 2026-11-10 one. Older 2026-10 items (delivery, product in/out, tiles, daily account book,
customer opening import, product catalogue download) were **not** re-opened;
treat `WEB_PARITY_2026_08_01.md` as their record until a dedicated audit says
otherwise.

This pass (2026-11-19) read `cashbookbd_react` `9698fc1b..475c6ce5` (20 commits)
against the working tree and ported every commit that carries behaviour a phone
can show: the Tiles **purchase** charges (rows 31), the **strict Product** box
and no Branch box on Product Stock / Stock Details / Closing Stock (row 32), the
Purchase Ledger's bill total (row 33), the Ledger Details footer's opening row
(row 34), **Approve Range** on Manual Attendance (row 35), the Customers page-
size selector (row 36), Search-on-Enter (row 37), and the two wording changes
(rows 38–39). The other nine commits are print-designer or web-layout only
(row 40). P1–P19 were **not** re-opened; only the rows this pass touched were
re-read. `cashbook_api` is still not present locally, so the API revision above
is still the 2026-11-10 one — in particular `reports/product-stock` and
`reports/closing-stock` now need to accept `product_id`, and
`trading/purchase/api-store` / `api-update` need to accept `carryingInwardAmt`,
`tdsAmount` and `serviceCharge`, before rows 31–32 can be device-verified.

One unresolved question for the API: `475c6ce5` adds `product_id` to both
`productStockData()` and `apiClosingStockItems()` in the web's `temp/` mirror of
the controller, i.e. it assumes a matching `cashbook_api` change. If the
deployed API does not yet read it, the Product box still narrows correctly on
device — the narrowing happens over the answer before grouping — but the request
would carry a parameter the server ignores.

One thing to re-check when the API repo is available: `TradeLedgerRepository`
now trusts `purchase_master.total` for a purchase bill (row 33). For a bill that
stores goods only, that field should be the detail sum; if some older payload
stores a stale or nought `total` while carrying goods, the fallback
(`masterTotal == 0`) keeps the old reading.

## How to resume

1. Read the Checkpoint table and the "Pending and partial" table above.
2. For anything newer than this pass, run
   `git -C F:\All_Database\cashbookbd_react log --oneline 475c6ce5..HEAD`
   (branch `Reseller-ui`) and the same in `cashbook_api` (branch `Reseller`)
   once that repo is available; add rows for whatever is new. **`cashbook_api`
   is now the bigger unknown**: rows 31–32 depend on its acceptance of the new
   fields noted above.
3. Pick the first item to implement. **P2 Production is implemented** — the
   next work there is layout/print (warehouse picker, package-unit entry) and
   the device pass. The next screen-level gaps are **P16 Forms Setup**, the
   remaining **P1** statements, and **P13 Fixed Assets Setup** (the smallest
   unrelated item; P12 needs the API contract confirmed).
4. Device pass for this session's rows: Tiles Purchase (three charge boxes),
   the three stock reports (no Branch box, Product narrows), Purchase Ledger
   (charged bill → nought balance), Ledger Details footer, Manual Attendance →
   Approve Range, Customers → Rows: All.
5. To finish the P11 device pass, add `dev.login.password` to
   `local.properties`, rebuild/reinstall and sign in.
6. Build and test the touched code:
   `.\gradlew.bat :app:compileDebugKotlin` then
   `.\gradlew.bat :app:testDebugUnitTest` (and `:app:lintDebug` for report or
   permission changes).
7. Only change a row to **Verified** after tapping through the built app.
8. Before stopping, update the Checkpoint's "Last verified on a device",
   "Work in progress" and "Next step", and move completed rows to the
   implemented table with the web revision that introduced them.
