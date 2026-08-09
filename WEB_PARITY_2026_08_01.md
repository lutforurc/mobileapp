# Web parity — 2026-08-01

> **Matched to (2026-08-09 evening):**
> ```
> cashbookbd_react : 9a0ea46 (2026-08-09 15:31)
> cashbook_api     : e1d2117d (2026-08-09 15:25)
> ```
> The project-and-building cost batch is ported: the Real Estate section
> gained Project Expense (cash payment voucher, each line tagged
> project/building, voucher pulled up by number and rewritten in place, bank
> funding preserved with the amber banner), Project Purchase (the purchase
> invoice with Building for Warehouse — per-line project/building, cash /
> credit / partial shapes decided server-side, price prefilled from the last
> purchase), and the Project Cost Report (Summary / Building Detail /
> Untagged tabs + the amber integrity panel; the untagged rows' Tag button
> deep-links into Project Expense with `?vrNo=`, or shows a lock where
> approval closed the door). One repository (`ProjectCostRepository`) holds
> the whole contract: raw int ids, hashed `mtm_id` only, refusals as
> success:false at HTTP 201, `data.data` double wrap. Design record:
> `docs/project-building-expense-spec.md` (react repo, Bangla).
>
> Skipped as web-only: `ProjectCostReportPrint` (print stream), the ledger
> print-button restyle (9a0ea46 — print is web-only here), rows-per-page and
> font-size controls (the mobile table has no pagination), and the
> `FilterMenuShell` popover (mobile filters are already inline). Server-side
> only, nothing to port: `e1d2117d` (settings permissions now
> `effectivePermissions` — platform admins' lists shrink to what their role
> grants; `remove.approval` enforced on the two remove-approval endpoints)
> and the `acc_coa_level4s.status` INT conversion. Deviations kept: money
> cells follow the app's zero-as-dash and en-US/decimal_places AmountFormat
> (web prints '0.00' and en-IN lakh grouping here); "not recorded"/"no area"
> keep their words. **Server prerequisite per tenant:** the two dimension
> tables — `php artisan patch:add-unit-type` or
> `project_building_expense_dimensions.sql` by hand, then `route:clear`;
> without them every endpoint of the batch fails.
>
> **Previously matched (2026-08-09 early morning):**
> ```
> cashbookbd_react : 4599d7b (2026-08-09 02:03)
> cashbook_api     : 95d881f3 (2026-08-09 02:04)
> ```
> The Bank Opening batch is ported (see **"Catch-up — 2026-08-09"** below for
> the contract): the Bank Opening screen lives in the Customers section beside
> the CoA lists (grouped account sections with subtotals and a Total opening
> line, dialog-based row edit like the customer list's opening entry, delete
> behind the amount-and-voucher confirm, voucher number deep-linking into the
> Ledger, the switched-off notice when `is_opening` is off), behind
> `bank.opening.view`/`bank.opening.edit`; and the branch form's Clear buttons
> now show a `severity: "info"` 403 as an inline notice in the info tone
> instead of a red snackbar (the server's message is no longer flattened to
> the generic 403 line). The Cash/Bank Book journal-voucher change needed no
> port — the numbers correct themselves server-side. Known deviations,
> deliberate: the Delete icon asks for `bank.opening.edit` AND
> `voucher.delete` (the web shows it on `voucher.delete` alone and the API
> then always refuses); mobile has no global search, so no search entry; the
> per-group serial restart matches the web's actual rendering.
>
> **Matched to (2026-08-08 late night):**
> ```
> cashbookbd_react : 979e081  (2026-08-08 — bank-opening write-up, docs only)
> cashbook_api     : 35bf16c7 (2026-08-08 18:39)
> ```
> Mobile-side additions after this note was first written (same web baseline,
> commits `f2d24df..7de533e`): the customer list hides its Opening column,
> field, and Delete when the branch's `is_opening` flag is off; the customer
> list's Action column gained a second edit — the full Edit Customer form —
> beside the quick opening/ledger dialog; the Product List gained Edit Product
> (full form, warranty fields included) and Delete row actions; list serial
> numbers continue across pages instead of restarting at 1; and the opening
> list re-saves a matching opening figure when its voucher is found gone.
> The opening-voucher tracking batch is ported: customer and product opening
> balances are tied to their journal voucher (`opening_vr_no` on both lists),
> the voucher number links into the Ledger (new deep-link route, auto-run
> search), Delete-opening on both lists behind voucher.delete with the
> amount-and-voucher confirm, the customer opening's "already set" gate
> removed (the server now rewrites the voucher in place), and the customer
> list's demo-video link. Server prerequisite: the `main_trx_id` columns from
> `opening_balance_tracking.sql` / the patch command must be applied per
> tenant, or the new fields stay empty and Delete never shows.
>
> **Gap-closure batch (2026-08-08 night, same web baseline as below):** the
> long-standing structural gaps are closed — full Customer Add/Edit forms
> (all gated fields, photo, guarantor/nominee panels, portal password),
> company logo upload (light+dark, multipart), cash voucher
> Search→Edit→Update (all three variants incl. Head Office), dashboard
> widget customization (order/hide/density, per-user-per-branch), Arrange
> Menu's inside-a-menu panel (sidebar-sub), and the three financial report
> layouts: P&L four-column (with the sumByIds and max(0,·) numeric fixes),
> Balance Sheet Opening/Movement/Closing + equity opening-difference
> adjustment + tappable group breakdown + Final Position card, Ledger
> running Balance + netted Opening + "Select All Branch" (per-row branch
> names in All Branch mode). The HRM matrix reports are also closed:
> Overtime's employee × day matrix, the Holiday Calendar month grid (with
> the web's weekly-policy expansion and default-Friday fallback), Branch
> Attendance's per-branch roll-up with its eight tiles, plus Monthly
> Attendance's serial sort, Total column/footer, and Deduction. The web's
> daily-report absent-row synthesis was NOT ported — on the web it is dead
> code in practice (branch filter compares a field the employees API never
> returns; the dedupe key can never match), so mobile matches observable
> behaviour instead.
>
> **Previously matched (2026-08-08 evening):**
> ```
> cashbookbd_react : 08febd9 (2026-08-08 17:02)
> cashbook_api     : 6fe3b31b (2026-08-08 17:01)
> ```
> The 08-08 batch is ported: the dashboard/summary features (KPI band,
> Receivable Ageing, Low Stock, monotone-cubic sparklines in the summary
> card), the user list's sign-in switch, Edit Customer's is_opening gate,
> and the two label fixes. Known deviations: KPI tiles sit 2×2 (web
> collapses to one column on phones); mobile has no dashboard widget
> customization, so the new cards are unconditional; the full Edit Customer
> form port (all fields, locked opening, contact/update endpoint) remains a
> separate pre-existing gap.
>
> **Previously matched (2026-08-08 morning):**
> ```
> cashbookbd_react : a5307aa (2026-08-08 01:48)
> cashbook_api     : 0a92544f (2026-08-07 18:50)
> ```
> The 08-07 batch is ported: Expense Report (native, expense.report), order
> party-by-id (name search now only the fallback), the Trading invoice-level
> tracked product, the Product Tracking drawer group (out of Admin/Reports),
> subscription payment preselect + zero-amount guard, Company User's
> phone-under-email, and the need_demo_tutorial settings key. Skipped as
> web-only: dark-logo upload/columns (mobile has no logo upload), tutorial
> YouTube links, favicon, and the SoftwareInfo/EditCompany restyles.
>
> **Previously matched (2026-08-06):**
> ```
> cashbookbd_react : 549ad6b (2026-08-06 02:01)
> cashbook_api     : af14b5a0 (2026-08-06 02:13)
> ```
> Ported through the 08-05 evening batch: the challan register with its
> issued-vs-received comparison, the sale nominee dialog, the booking form's
> generate/withdraw, the branch form's full web layout (Inventory System, the
> six missing customer switches, and the renamed meta keys the old form was
> silently posting into), and the cash forms' party-scoped Select Product
> (product tracking) field.
>
> Still web-only: the scanned-deed upload (multipart file picking), printing
> the letter/booking-form PDFs (auth'd PDF streams), and — from the product
> tracking module — the Tracking Settings screen, the Product Financial
> Statement and the Tracking Summary reports (`product-tracking/settings`,
> `reports/product-financial-statement`, `reports/product-tracking-summary`).
> `git log 549ad6b..HEAD` in the react repo is the next catch-up list.

## Catch-up — 2026-08-09: the Bank Opening batch (ported same night)

Read off the controllers as they stand at `95d881f3`, not off the messages.
Ported to mobile 2026-08-09: `BankOpeningRepository` + `ui/customer/BankOpening*`
(menu key `bankOpening` in the Customers section, route
`customers/bank-opening`), and the §D notice handling in `BranchRepository` /
the Add Branch screen. The section below stands as the contract record.

### A. Bank Opening — the feature to port

A new screen (web: `AccountOpeningBalance.tsx`, menu item **"Bank Opening"**
in the Chart of Accounts group, global-search entry too) sets opening balances
for the money accounts — Cash, Bank Account, Mobile Banking, i.e. the level-3
groups in `config('accounts.opening_account_groups')` = `[1, 2, 28]`. Kept off
the CoA L4 list deliberately: most of that chart is expense and sales heads,
which open at nothing.

```
POST /api/account/opening-balance/list          bank.opening.view
POST /api/account/opening-balance/update/{id}   bank.opening.edit
POST /api/account/opening-balance/delete/{id}   bank.opening.edit + voucher.delete
```

All three POST (so none collides with the `coal4/{id}` catch-all). `{id}` is
the hashed coa4 id. `foundData()` wraps twice — the list reads from
`response.data.data.data`, with `transaction_date` beside it.

Behaviour is the customer opening's, on the shared `OpeningVoucher` machinery
(extracted from `PartyOpeningBalanceService`, which shrank 289 → 93 lines with
behaviour unchanged):

- No voucher + non-zero figure → journal voucher raised (two lines, contra
  coa4 14), `main_trx_id` saved on `acc_coa_level4s`.
- Voucher exists + figure changed → same voucher rewritten in place, `vr_no`
  kept. Figure 0 → voucher trashed, link nulled. Delete → soft delete.
- Amount is read back from the voucher's own rows (`debit - credit`), not
  from any column — there is no `openingbalance` column here, on purpose.
  Positive = money in the bank (debit); negative = overdraft.
- Guards: approved voucher (on `main_trx_master.is_approved`) blocks edit and
  delete; another branch's voucher blocks; `lockForUpdate` inside the
  transaction; the whole screen and API sit behind the branch's
  `is_opening == 1` — the same switch the customer list reads.
- One opening per account, company-wide — `acc_coa_level4s` has no
  `branch_id`, so there is no per-branch opening to model.
- Inactive accounts are hidden, except one that still holds a live opening
  voucher: it stays listed with an "Inactive" badge, delete-only.
- The voucher number is a link into the Ledger — the deep-link route mobile
  already has from the opening-voucher batch.

**Mobile port:** a drawer entry + screen shaped like the customer list's
opening rows (amount field, Save/Cancel, Delete behind the confirm naming
account, amount and voucher number), grouped by level-3 group.

### B. Permissions — new names, seeded from the old

`branch_opening_permissions.sql` / the patch command create a **Branch
Opening** permission group and seed each new name from the one it replaces,
so nobody loses a screen on ship day:

| New | Seeded from | Enforced today? |
|---|---|---|
| `bank.opening.view` | `coa.l4.view` | yes — the three endpoints above |
| `bank.opening.edit` | `coa.l4.edit` | yes |
| `party.opening.view` / `.edit` | `cs.view` / `cs.edit` | **no — seeded only, no controller checks yet** |
| `product.opening.view` / `.edit` | `products.view` / `products.edit` | **no — seeded only** |

`branch.opening.clear` and the transaction-clear permission are moved into the
same group by `35e06296`. The patch also activates `acc_coa_level4s` rows
above id 240 whose `status` a form had left at `'0'`, hiding those banks from
the very screen that exists to open them.

**Server prerequisite per tenant:** `account_opening_balance_tracking.sql`
(adds `main_trx_id` to `acc_coa_level4s`; MariaDB-only `IF NOT EXISTS`
syntax) and `branch_opening_permissions.sql` or the patch command, then the
permission cache reset. Without the first, every save fails; without the
second, only privileged roles reach the screen.

### C. Cash Book / Bank Book now count journal vouchers — no port, numbers change

Both books only ever counted transaction_type 1 and 2, while an opening
balance arrives as a journal (type 5) — a bank opened with 57,000 showed
nothing in the Bank Book while its ledger counted it. Now the opening line
includes type 5, and a seventh union lists journal rows inside the range
(the entry set holds a bank leg — account 17 for the cash book — and the row
shown is the other side of it; nothing is double-counted against the
cash-to-bank rows, and bank-to-bank transfers still net to no row). Mobile
renders these reports off the API, so the correction arrives by itself —
just don't read changed numbers as a regression.

### D. Clearing transactions is owner-gated — mobile is affected

`POST /api/branch/clear-transaction` now refuses anyone not in
`config('subscription.platform_owner_user_ids')` (env
`PLATFORM_OWNER_USER_IDS`, default `[1]`), **even with the permission** — a
tenant's own administrator holds every permission their company has, and one
click here empties a branch's books. The 403 carries `severity: "info"` and
*"Only the system administrator can clear transactions. Please call the
administrator."*

**Mobile owes two small things** (the branch form already has both Clear
buttons): show that refusal once, and as a notice rather than an error — the
web now marks interceptor-toasted errors (`toastReported`) so handlers stay
quiet, and paints `severity: "info"` refusals in the notice voice, not red.

### E. Web-only, nothing to do

The shared row-button shape (`CustomButtons.tsx`) adopted by Customers and
the new screen — mobile already routes row actions through its shared list
components.

## Full-surface audit — 2026-08-06

Every web sidebar item and route was enumerated and matched against every
mobile registry (menus, ReportConfig, AppLists, Routes). Transaction, Invoice,
Reports, Requisition, Real Estate, Products, Customers, VR Settings, HRM and
Subscription are at parity.

**Closed on 2026-08-06 (commit 1fbabdf):** Product Tracking settings + both
reports, In-App Messages admin (list + form; image by URL), Inventory Systems
admin, Analytics → Comparison (new drawer entry), Forgot Password (3-step
OTP), Profile (photo upload), and Subscription plan entry/edit.

## Report-table audit — 2026-08-06 (commit b5b245f)

All 41 reports were compared against their web components by a five-agent
audit. Config-level mismatches (leaked columns, wrong order, headers) are
fixed across 19 generic reports, and Monthly Report + Closing Stock/Stock
Details — silently broken parsers — now render. Purchase/Sales Ledger are
native rebuilds. Structural gaps that need bespoke screens, largest first:

- ~~Group Report~~ — rebuilt native 2026-08-06: GroupReportScreen +
  GroupReportRepository send report_group (strict d/m/Y dates), render the
  month-pair pivot with two-tier headers and a Grand Total row, and "All
  group" fires both group requests and merges client-side, like the web.
- ~~Connected Member~~ — rebuilt native 2026-08-06: ConnectedMemberScreen +
  ConnectedMemberRepository parse the employee-keyed payload, expandable
  employee rows, computed columns (Pay Member %, DP+Coll, 5% Salary,
  Overview — including the web's own detail-row Overview inconsistency,
  kept for parity), Grand Total tfoot over all rows.
- ~~HRM Overtime matrix, Holiday Calendar grid, Branch Attendance
  aggregation~~ (closed 2026-08-08, native screens). Still open: Employee
  Attendance day-synthesis (server also ignores employee_id — fix belongs
  in the API); the daily report's absent-row synthesis is web dead code
  (see the top note) and deliberately not ported.
- Generic-table niceties the web has and the engine lacks: ~~per-report
  footer Total rows~~ (added 2026-08-06 — ReportConfig.totalColumns /
  totalRowLabel now sum 13 reports' web tfoots), per-group headings
  (closing stock brands, labour branches), cell colour rules,
  running-balance columns (Product In Out; ~~Ledger's Balance~~ closed
  2026-08-08), en-IN lakh/crore grouping, dd/MM/yyyy date reformat outside
  stacked cells, "All Branch" filter option (~~Ledger~~ closed 2026-08-08;
  other reports pending).
- Native-screen deltas: Cash Book/Bank Book Sl+Action columns and somity
  lines, ~~Ledger running Balance + netted Opening~~, ~~P&L 4-column
  layout~~, ~~Balance Sheet Opening/Movement columns + equity adjustment~~
  (all three closed 2026-08-08).

Still web-only, deliberately:

| Gap | Why |
|---|---|
| Resellers screen, Approval Center (+audit) | mobile menu placeholders — port when asked |
| Voucher/Bulk Upload, Purchase/Sales Import | file uploads/imports class |
| Campaign image upload, scanned-deed upload | multipart file picking beyond the profile photo |
| Letter/booking-form PDF prints | auth'd PDF streams |
| Customer portal | different audience |

Deliberate divergences (fine as they are): the web's "Branch Transfer" drawer
group lives inside Invoice + Reports here; Due/Employee Installments are not
business_type_id==4-gated here; the web's absent/late/early-out attendance
presets ride the one Attendance Alerts report here.

Web-side bugs found while auditing (fix in the REACT repo, not here): the
/vr-settings/voucher-activity route guard still checks `voucher.changes`
while the sidebar checks `log.changes` (holder of only log.changes is bounced
to /no-access); sidebar/route permission mismatches on Ledger Details
(ledger.details vs ledger.customer), Bank Book & Cash & Bank Summary
(bank.book/cash.bank.summery vs cashbook.view), and Branch Issue
(branch.issue.create vs the transfer-create set).

What the web gained after this app's last commit, and what it costs to follow.

Baseline for this document:

| Repo | At | When |
|---|---|---|
| `mobileapp` | `7a3302f` | 2026-07-30 23:08 |
| `cashbook_api` | `8da23df1` | 2026-08-01 20:05 |
| `cashbookbd_react` | `0aa6091` | 2026-08-01 20:05 |

Six backend commits and ten frontend ones sit in that gap. Contract below is
read off the controllers as they are today, not off the commit messages.

> **The gap was worked out from timestamps.** Web work from earlier on 07-30 —
> the allotment letter's reference number and date, the password-change
> permission — falls before this app's last commit, so it is left out here. That
> does not prove it was ported. Check those separately.

---

## 1. API contract — miss these and the app is wrong, not just behind

### 1.1 Customer password minimum is 6, was 4

```
POST /api/customer/change-password
  password              required, string, min:6, confirmed
  password_confirmation required
```

The server was accepting four characters while the web screen asked for six —
posting straight to the endpoint set a four-character password. Both say six now.

A second rule was already there and still is: a password equal to the customer's
mobile number is refused with 422, *"Please choose a password different from your
mobile number."* That is the default password, so it is the one people try.

**Client:** raise the local minimum to six, and surface both 422s distinctly —
"too short" and "that is your mobile number" are different problems.

### 1.2 The customer payload carries `photo_url`

```
POST /api/customer/login
GET  (customer me / session restore)

→ data.photo_url : absolute URL string, or null
```

Resolved server-side. Whether the stored path needs an extra `public/` segment is
a deployment detail the server knows; it is built from the host of the request in
flight, so it stays right across tenant domains without depending on `APP_URL`.

**Client:** show it as the dashboard avatar. Fall back to the name's initials when
it is `null` **and** when the image fails to load — the file can go missing while
the path stays, and a broken-image glyph in the header is worse than initials.

### 1.3 Customer creation accepts an opening balance

```
POST /api/contact/store
  openingbalance  optional, numeric        ← new
```

Two effects, and both matter:

| | |
|---|---|
| 1 | `cust_party_infos.openingbalance` is set |
| 2 | a journal voucher is raised: `main_trx_master` (transaction_type 5) → `acc_transaction_master` (`note = "Opening Balance Entry"`, voucher_type_id 3) → **two** `acc_transaction_details` rows |

The two rows are the customer's own `coa4_id` and `coa4_id = 14` as the contra.
A positive opening debits the customer and credits 14; a negative one reverses.

- Not numeric → **the whole customer creation is rolled back**, with *"Opening
  balance must be a numeric value."* No customer is left behind.
- Zero or absent → nothing happens at all, neither column nor journal.
- Offer the field only where `settings.data.branch.is_opening == 1` — the same
  switch the customer list reads before showing its Opening column.

A new party starts at zero, so the list's *"Opening balance already set. It cannot
be changed."* has nothing to guard here. It takes over from the next edit.

### 1.4 Product creation accepts opening stock

```
POST /api/product/store
  opening_qty        optional, number      ← new
  opening_rate       optional, number      ← new
  opening_serial_no  optional, string      ← new
```

- `opening_rate` empty falls back to `purchase_price`.
- `opening_serial_no` is split on **newlines and commas** (`/[\r\n,]+/`), entries
  trimmed, empties dropped.
- **With serials present they decide the quantity** — whatever `opening_qty` says
  is ignored.
- Without serials `opening_qty` is required; zero or empty gives *"Quantity is
  required when serial number is not provided"*.

What it writes: `product_items.openingbalance`, a journal voucher, an
`inventory_purchase_master` with details, the stock in `inventory_master`, and the
accounting behind it. Exactly what the product list writes — the same method is
called, not a second copy of it.

- A refused opening **takes the half-made product with it**. No orphan rows.
- Only while creating, and only where `is_opening == 1`.

### 1.5 New: clear a branch's opening balances

```
POST /api/branch/clear-opening
  branch_id   hashed, the way branch ids always travel

→ 200  data.data = { products: n, parties: n }
→ 403  without the permission
→ 404  branch outside the caller's company
```

Requires **`branch.opening.clear`**.

Sets `product_items.openingbalance = 0` and `cust_party_infos.openingbalance = 0`
for that branch. **Nothing else** — the journals, purchases and stock an opening
raised are left exactly where they are. Unwinding those is a separate, deliberate
correction; a product's opening is recorded as an ordinary inventory purchase and
anything trying to delete it would be guessing which rows to destroy.

Rows already at zero are skipped, so the two counts returned are what actually
changed rather than how many rows the branch holds.

The permission is created by
`database/sql/2026_08_01_add_branch_opening_clear_permission.sql` and is
deliberately granted to **no role** — currently only Super Administrator / DBA /
Administrator reach it, because `CompanyRoleScope` hands privileged roles
everything.

---

## 2. Fixes that change behaviour

### 2.1 Saving a product returned 500 — read this first

`ItemController`'s constructor had:

```php
$this->branch = Branch::find(Auth::user());   // find() wants an id
```

Handed a model, `find()` read it as a *list of ids* — the user's own attribute
values — and returned a Collection of whatever branches matched. Not being null,
that Collection won **every** `$this->branch ?? Branch::find($user->branch_id)`
fallback in the file, and the first property read off it threw.

`POST /api/product/store` raised a 500 for any signed-in user because of it.

Five other methods in that controller share the same `$this->branch`. Worth
walking the product list and stock screens once after picking this up.

### 2.2 A serial-tracked opening recorded a quantity of zero

`openingbalance` was written from the raw `qty` field, which a product entered by
serials leaves at zero. The stock went in; the opening said none.

It now stores the resolved count. **This was true of the product list too**, so
products stocked that way before this fix still carry a zero opening — a data
correction, separate from the code.

### 2.3 Ledger page accepts `#` and `,`

```
was : /^[A-Za-z0-9\s\-\/.]+$/
now : /^[A-Za-z0-9\s\-\/.#,]+$/
```

If the app validates locally, match it — otherwise the app refuses what the
server would accept.

### 2.4 Ledger page with duplicate rows

Previously one row was fetched and deleted; now every row for that party and
branch is cleared before the new one is written, so duplicates left by earlier
saves clean themselves up.

### 2.5 Duplicate customer rows in reports

`ReportsController` joins `cust_party_infos` through the latest row per
`coa4_id` (`MAX(id)`) instead of the raw table. Where one `coa4_id` had more than
one party row the report was multiplying its lines. Any port of the same report
will see its numbers change — to the correct ones.

---

## 3. Screens

### 3.1 Add Customer — Opening

After Ledger Page, shown only where `is_opening == 1`. Note beneath it:
*"Entered once. Afterwards it can only be changed by clearing the branch's
opening."*

### 3.2 Add Product — Opening Stock

Below Order Level, only while creating:

```
IMEI / Serial        ← first, full width, multiline, resizable
   "One per line. The quantity below counts them as you type."

Quantity             Rate
```

- The quantity **counts the serials as they are typed**, split the same way the
  server splits them.
- While there are serials the quantity field is **closed to typing** — left open
  it could be made to disagree with them, and the server would ignore it anyway.
- Clear the serials and the field is handed back, so a product without serials can
  still be given a quantity.
- The IMEI field **must** be multiline. It used to be a single-line input, which
  made entering more than one serial impossible while the server had always split
  them on newlines.

### 3.3 Customer list — Save and Cancel moved

Out of the Action column at the end of the row and in beside the Opening field
they save. On a narrow screen they could not be reached without scrolling.

Action now holds only what belongs to the row rather than to one field:
guarantors, nominees, print, edit, delete.

### 3.4 Branch → Feature Controls

- A rule under every step's heading; heading smaller and closer to its description.
- Four separate grids merged into one, so fifteen toggles fill the columns instead
  of stranding one on a row with two empty cells beside it.
- **Clear Opening** button after "Opening ongoing?", with a confirmation naming
  what is cleared and what is left alone, and a progress bar spanning the row
  beneath it (plus the thin one pinned to the top of the window — one piece of
  state drives both, so they cannot disagree).

### 3.5 The three customer-portal screens

| Screen | What changed |
|---|---|
| Login | leading icons, password reveal, error panel instead of red text, dark mode, numeric keypad and an 11-digit limit on the mobile field |
| Set a New Password | same language as the login it follows, both boxes reveal together, a tick as soon as they match, minimum six |
| Dashboard | full page width, header stacks on a phone so the address is read rather than cut, **Balance leads** (wider and larger), card radius 8px and tiles 6px, the customer's photo |

One rule worth carrying into the dashboard: the Installment Summary counts sit in
ordinary ink and the **coloured chip beside each carries the state** — a number
should never be told apart by its colour alone. Overdue is the exception the rule
allows, and it keeps its red only while there is something to warn about, always
beside an icon and the word.

### 3.6 Two small ones

- Attendance delete uses the app's own confirm dialog.
- Labour invoice save feedback corrected.

---

## 4. Server, before any of this works

```bash
mysql -u root -p <db> < database/sql/2026_08_01_add_branch_opening_clear_permission.sql
php artisan permission:cache-reset
php artisan route:clear
```

Per tenant database. So far only `kbrrealestatedb` has had it.

---

## 5. Order to take them in

| | | Why |
|---|---|---|
| 1 | §2.1 product save 500 | if creating a product is broken, nothing else matters |
| 2 | §1.1, §1.2 password minimum, photo | small, but until then the app states a rule the server does not hold |
| 3 | §1.3, §1.4, §3.1, §3.2 openings | the substantial new feature |
| 4 | §2.3, §2.4, §2.5 validation and reports | these prevent quietly wrong data |
| 5 | §1.5, §3.4 Clear Opening | blocked on the permission seed anyway |
| 6 | §3.5 portal appearance | looks, not behaviour |

---

## 6. Keeping the next one short

The gap above had to be reconstructed from commit times, which cannot tell what
was ported and what merely happened to be older. A line in this repo saying where
the app last matched would settle it:

```
Matched to:
  cashbookbd_react : 0aa6091  (2026-08-01 20:05)
  cashbook_api     : 8da23df1 (2026-08-01 20:05)
```

Then `git log 0aa6091..HEAD` is the whole list, with nothing to infer.
