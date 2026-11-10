# Mobile UI sync tracker

Read this file first in any future session. It records the web revision the
Android app was last compared against, which screens were ported, and the exact
place to resume.

## Checkpoint

| | |
|---|---|
| **Web UI reviewed** | `cashbookbd_react` branch `Reseller-ui` @ `91062fef` (2026-11-10); the ported change is `08ca7cf4` |
| **API reviewed** | `cashbook_api` branch `Reseller` @ `851b9686` (2026-11-10) |
| **Android baseline** | `main` @ `587361b` (2026-11-05) |
| **Pass date** | 2026-11-10 |
| **Last verified on a device** | none this pass (no emulator/device session was run) |
| **Work in progress** | none — the pass ended cleanly |
| **Next step** | Port the rest of the **Financial Statement report group** (section "Pending" below): **Monthly Statement**, **Profit & Loss (Date Wise)**, **Business Summary**, **Product Profit Summary**, **Product Statement**, **Customer Sales Report**, **Tiles Purchase**. General Ledger and Cash Flow Statement are done (rows 11–12). |

Both repos above are the **live demo branches and were opened read-only**; no
file in either was modified by this pass.

### Verification done this session

```
.\gradlew.bat :app:compileDebugKotlin   -> BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest    -> BUILD SUCCESSFUL (45 tests, incl. 4 new Cash Flow / General Ledger cases)
.\gradlew.bat :app:assembleDebug        -> BUILD SUCCESSFUL
.\gradlew.bat :app:lintDebug            -> BUILD SUCCESSFUL
```

No Android device or emulator session was run, so nothing is marked **Verified**
on this pass. Every implemented row below means "code compiles, builds, lints
and unit tests pass; still needs a tap-through in the running app".

Machine note: `local.properties` was pointing at a stale SDK path
(`C:\Users\lutfo\AppData\Local\Android\Sdk`, missing on this machine). It now
points at the installed SDK, `D:\Local\Android\Sdk`, so Gradle can build.

## Status key

- **Pending** — identified, not started.
- **In Progress** — started, not complete.
- **Implemented** — code complete and build/test-green, not yet checked on a device.
- **Verified** — checked on a running build (device/emulator) by a human or agent.
  Do not use this status without an actual tap-through.
- **Blocked** — cannot proceed; the blocker is named in the notes.
- **Web-only** — deliberately not ported, with the reason.

## Implemented this pass (2026-11-10)

| # | Web change (repo @ rev) | Screen / workflow | Status | Verification notes |
|---|---|---|---|---|
| 1 | `a62592b3` Cash & Bank Book: one row per head | Reports → Cash & Bank Book | Implemented | Sends `split_heads=1`; rows keyed by `row_key ?? mtm_id` so a voucher spanning heads does not repeat its key. Files: `CashBankBookRepository.kt`, `CashBankBookScreen.kt`. Build + unit tests green. |
| 2 | `c28dc0e1` + `333f6a6e` + `d3d4eaff` + `c38e7899` Ledger Details | Reports → Customer Supplier Statement ("Ledger Details") | Implemented | Transaction-type selector now offers **All Transactions / Received / Payment / Sales / Purchase** (3 and 4 included). Cash labels shorten to Received/Payment. New **Inv. Amt** column between Sal. Total and Debit, computed per the web's `getInvoiceAmountValue` (purchase cash negative, sale cash positive); Debit/Credit blank on invoice rows; footer totals Debit, Credit and Inv. Amt. The arithmetic is a pure helper (`report/LedgerMoney.kt`) with four unit cases in `ParitySmokeTest`; the repository only maps JSON and lays out cells. Files: `report/ReportConfig.kt`, `report/LedgerMoney.kt`, `data/repository/GenericReportRepository.kt`. Build, tests and lint green. |
| 3 | `50e5a4da` Sales & Purchase Ledger: rule under every product line | Reports → Sales Ledger / Purchase Ledger (and the return ledgers) | Implemented | A hairline rule under each stacked product line in Product & Details, Quantity, Rate and Total; the last line keeps its rule when a note follows. File: `ui/reports/TradeLedgerScreen.kt`. Build green. |
| 4 | `c705bb46` + `b9b85802` Sold Units: installment money | Real Estate → Sold Units | Implemented | `installment_received` parsed on sale/customer/totals; the Received cell prints an extra "Installment" line when non-zero; the floored grand/report totals now add booking receipts **and** installment money (the share the web fixed). Files: `data/repository/RealEstateSalesRepository.kt`, `ui/realestate/SoldUnitsScreen.kt`. Build green. Note: the web's per-receipt date list inside Due is not part of this item and remains unported. |
| 5 | `dd0b10ad` / `74eb8d57` Installment Create due dates | Real Estate → Installment Create | Implemented | Due Date prints `dd/MM/yy` in the preview and saved rows; the stored/edited value stays `dd/MM/yyyy`. The web's day/month swap bug did not exist here (Android parses day-first), so no parsing change was needed. File: `ui/realestate/InstallmentCreateScreen.kt`. Build green. |
| 6 | `67e6364f` Branch Issue: issuing branch's stock | Branch Transfer → Branch Issue | Implemented | Calls `warehouse/transfer/available-stock?branch_id=&product_id=` when a product is picked (or the from-branch changes) and shows the figure inside the Quantity box; stale answers are dropped. Files: `InventoryMovementRepository.kt`, `InventoryMovementUiState.kt`, `InventoryMovementViewModel.kt`, `InventoryMovementScreen.kt`. Build green. |
| 7 | `cee049a7` Hotel bill: discount before VAT, VAT once | Hotel → a booking's folio (bill strip) | Implemented | A discounted bill now reads: Charges (VAT incl.) `gross` → less discount → net → = rent (excl. VAT) + each VAT band. Undiscounted bills keep their old strip. No arithmetic is done locally; the server's totals are printed. File: `ui/hotel/HotelFolioScreen.kt` (`WorkingLine`). Build green. |
| 8 | `fd4c4e3d` Hotel reports: VAT collection by rate | Hotel → Reports | Implemented | New **VAT by rate** tab: From/To (both ends inclusive), a tile with "VAT collected · N rate(s)", a table (VAT Rate / VAT Collected), a Grand Total footer and the server's note. Reads `hotel-setup/reports/vat-summary`. Mobile adaptation: the tab strip is now `PrimaryScrollableTabRow` so five labels fit a phone. Files: `HotelOpsRepository.kt`, `ui/hotel/HotelReportsScreen.kt`. Build green. |
| 9 | `fb6c2836` Real Estate: keep project/building after save | Real Estate → Project Expense / Project Income | Implemented | Picking an account no longer clears the project/building; both survive a saved line and a saved voucher. The project box stays open until an account is picked; building follows the project. A line that is not an expense/income head still saves untracked (row drops them). Files: `ProjectExpenseViewModel.kt`, `ProjectExpenseScreen.kt`, `ProjectIncomeViewModel.kt`, `ProjectIncomeScreen.kt`. Build green. |
| 10 | `609fa5f6` Sales Summary: total every filtered record | Real Estate → Sales Summary | Implemented (no code change needed) | The Android screen already prints the server's `totals` block rather than re-adding visible rows, which is exactly what the web moved to. The server now includes installment receipts in those totals, so Received/Due agree with Sold Units with no client change. `ui/realestate/SalesSummaryScreen.kt`. |
| 11 | `08ca7cf4` Cash Flow Statement (indirect method) | Reports → Financial Statements → Cash Flow Statement | Implemented | New bespoke screen under the two Trial Balances, gated on `cash.flow.statement`. Filter: branch + date range, opened year-to-date from the branch's business date (the web's Jan 1 → transaction day). Reads `reports/cash-flow-statement` (GET, `branch_id`/`start_date`/`end_date`, `yyyy-MM-dd`). The statement's row wording and order are a rule-for-rule port of `cashFlowStatementRows.ts` (`ui/reports/CashFlowRows.kt`): operating → non-cash → working capital → subtotals, investing, financing, optional "Other movements not classified", net increase/decrease, opening/closing cash, and the two ledger-difference rows when the server says it does not reconcile. Four summary tiles, section/subtotal/total/note styling, and the web's basis sentences. Files: `data/repository/CashFlowStatementRepository.kt`, `ui/reports/model/CashFlowStatement.kt`, `ui/reports/CashFlowRows.kt`, `CashFlowStatementUiState.kt`, `CashFlowStatementViewModel.kt`, `CashFlowStatementScreen.kt`. No print/Excel export — print stays web-only by the existing policy. Build, unit tests and lint green. |
| 12 | `08ca7cf4` General Ledger | Reports → Financial Statements → General Ledger | Implemented | New bespoke screen gated on `general.ledger`. Filter: branch, optional searchable **Accounts Head** (server `reports/general-ledger/accounts?search=`; blank = All Accounts), start/end dates opened year-to-date. Reads `reports/general-ledger` (GET) page by Accounts Head at the web's 25 per page; four total tiles (Total/Closing Debit & Credit, summed over the full filtered set, not the page); one card per head with its group trail and code, a leading Opening Balance row, every posted entry, the server's per-row running balance, a Total footer and a Closing Balance footer; Previous/Next pager. The web's Voucher-cell link is carried as an ACTION pencil through the existing `VoucherEdit` permissions (hidden on approved vouchers and uneditable kinds). Files: `data/repository/GeneralLedgerRepository.kt`, `ui/reports/model/GeneralLedger.kt`, `GeneralLedgerUiState.kt`, `GeneralLedgerViewModel.kt`, `GeneralLedgerScreen.kt`. Build, unit tests and lint green. |

## Pending (identified, not started)

| # | Web change | What is owed on Android | Status | Notes / next step |
|---|---|---|---|---|
| P1 | `ac5af26f` (Monthly Statement), `649f9f81` (P&L Date Wise), `74921537` (Business Summary), `6d3edbbb` (Product Profit Summary), `87782eb7` (Product Statement), `b3d99a2e` (Customer Sales Report), `eaac018d` (Tiles Purchase) | The rest of the **Financial Statement** report group | Pending | `08ca7cf4`'s **Cash Flow Statement** and **General Ledger** are done (rows 11–12). API still serves: `reports/monthly-statement`, `reports/profit-loss-date-wise`, `reports/business-summary(-detail)`, `reports/product-profit-summary`, `reports/product-statement`, `reports/customer-sales` (already a native screen), and the tiles purchase endpoint. Register each in `report/ReportConfig.kt` + `report/ReportEndpoints.kt`, add it to the web's menu group in `navigation/MenuGroups.kt`, and give any bespoke layout a native screen; the generic engine covers the plain tables. |
| P2 | `9f3c02bd`, `bf4a31d7`, `e88c85f0`, `41895d37`, `a40a8003`, `615f5b80` (Production/Manufacturing), `12dc55ea` (Material Issue retry), `2263efca` (issue print) | A Production / Manufacturing module | Pending | Large new module: production entry (several outputs, expiry), batch list/details, recipe screen, variance, register/costing, sales return, and the existing Material Issue form. API routes live under `production/*` and require both a production permission and the branch's manufacturing switch. Start with the handoff docs in `cashbookbd_react/docs/Manufacturing/` (v4 plan + phase-1 handoff), then `production/voucher-types`, `/list`, `/details/{id}`. The retry-deduction guard (`12dc55ea`) is API-side; the mobile form posts once per save, so no client change is expected. |
| P3 | `2acca618` Hotel Setup: Income Setup | Branch Hotel Setup step | Blocked | The Android branch form has no hotel setup section at all — hotel VAT/due/income modes are set on the web and read from the server (see `WEB_PARITY_2026_08_01.md`, hotel phase 3). The folio already prints whatever totals the server sends, so the setting has no mobile UI to add until the branch form's hotel step is ported. |
| P4 | `5e2a1d28` Hotel booking: two-column screens, folding floors, Enter walks the form | Hotel → New Booking / booking screens | Pending | A desktop-layout change (two columns, Enter key navigation). On a phone the one-column layout is already the right form; the folding floors list and keyboard order should be checked against `HotelNewBookingScreen.kt` before changing anything. Low priority, layout adaptation only. |
| P5 | `efeb7fb5` Hotel Layout: full-width buildings, seven rooms to a line | Hotel → Layout | Pending | Web drawing change. `HotelLayoutScreen.kt` draws its own mobile grid; decide whether the room-per-line count needs adapting to phone width rather than copying seven. |
| P6 | `fb6c2836` DdlMultiline relation label | Party dropdown labels | Web-only | Android's party/account dropdowns do not print `label_5` at all, so there is no "Father" label to correct. Nothing to port unless the multiline party row is added later. |
| P7 | `66c1f7e4` / `3ec373d9` Real Estate customer-wise installment statement | Print sheet | Blocked | The print-designer / bespoke print engine is web-only in this app (documented policy). The screen-side data is already on Installment Create. |
| P8 | `823ca96d` No negative numbers: one rule for every numeric field (2026-10-30) | Every numeric entry field | Pending | Not re-audited this pass. Android's amount fields allow a typed `-` in places. Audit `AppTextField`/numeric field helpers against the web's RejectNegativeNumbers rule and add the shared guard. |
| P9 | `a62fc7df`, `164b152a`, `ceedbfef`, `6efdff2c`, `8430b545`, `2d0d2762`, `cb489067` (sidebar/account-menu furniture) | Drawer / account menu | Web-only | These restyle the web sidebar and account dropdown to match the mobile app's own icons and grouping; the Android drawer is the source those changes were copied from. No port. |
| P10 | `8b3a8934` Help topic browser, `44a3f869` hotel help | Help | Web-only | The help JSON browser is a web surface this app does not have. |

## Deliberately not re-audited this pass

The previous parity document (`WEB_PARITY_2026_08_01.md`) records the app as
matched through react `1e7644af` / api `f4f87e2f`, and the Android history shows
parity passes on 2026-10-26 and 2026-10-30 (design-system refresh, ledger edit,
account deletion). This pass read the gap forward from the **2026-11-05** icon
commit (`587361b`), i.e. the 2026-11-05 → 2026-11-10 web window, plus the
immediately preceding report/production additions where the Android registry had
no entry. A continuation session on the same date then ported `08ca7cf4`'s two
statements (rows 11–12), reading the web screens at `91062fef` and the controllers
at api `851b9686`; the newer api commits after `bb636617` are Hotel-only and were
not part of it. Older 2026-10 items (delivery, product in/out, tiles, daily account
book, customer opening import, product catalogue download) were **not** re-opened
in this pass; treat `WEB_PARITY_2026_08_01.md` as their record until a dedicated
audit says otherwise.

## How to resume

1. Read the Checkpoint table and the Pending table above.
2. `git log --oneline fd4c4e3d..HEAD` in `cashbookbd_react` (branch `Reseller-ui`)
   and `git log --oneline bb636617..HEAD` in `cashbook_api` (branch `Reseller`)
   for anything newer than this pass; add it as rows.
3. Pick the first Pending item, implement it, update its Status to Implemented,
   then build (`:app:compileDebugKotlin`) and test (`:app:testDebugUnitTest`).
4. Only change a row to **Verified** after tapping through the built app.
5. Update the Checkpoint's "Last verified on a device", "Work in progress" and
   "Next step" before stopping.
