# Play Store Listing — CashBookBD

Everything the Play Console asks for at submission, ready to paste.
Asset files referenced here live in this same `playstore/` folder.

> **Corrected against the code on 2026-10-26.** Two answers in the previous
> revision were wrong and are fixed below: the app **does** offer in-app
> account creation (company sign-up with OTP), and the fields the app records
> now include NID/passport for hotel guests. See §3 and §6.
>
> **Re-verified and rebuilt on 2026-10-26.** `targetSdk`/`versionCode`/signing/
> permissions were re-checked against the code and the merged release manifest,
> the release bundle was rebuilt and its signature verified, and the release
> quality gates now pass (§0a). ⚠️ **The live Play requirement pages could not be
> re-read from this machine — it has no outbound network.** The requirement
> statements below are from the earlier research pass and from the API-level
> schedule; confirm them in the Console before you rely on them.

---

## 0. Build facts (verified from the project)

| Fact | Value | Source |
|---|---|---|
| Framework | Native Android — Kotlin + Jetpack Compose, single `MainActivity` | `app/build.gradle.kts`, `MainActivity.kt` |
| Application ID | `com.cashbookbd.app` (permanent) | `app/build.gradle.kts:126` |
| Version name / code | `1.0` / `1` | `app/build.gradle.kts:129-130` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 37 | `app/build.gradle.kts:127-128`, `:100` |
| Release backend | `https://my.cashbookbd.com/api/` (HTTPS) | `app/build.gradle.kts:46,187` |
| Signing | `cashbookbd-release.keystore` via untracked `keystore.properties` | verified present |
| Declared permissions | `INTERNET`, `ACCESS_NETWORK_STATE` only | `AndroidManifest.xml` |
| Ads / analytics SDKs | **None** (SDK list: AndroidX, Kotlin, OkHttp/Retrofit/Gson, Coil, Tink) | `sdkDependencies.txt` |
| Token storage | AES-256 `EncryptedSharedPreferences` (Android Keystore) | `TokenManager.kt` |
| Cleartext HTTP | Local dev hosts only; production is HTTPS-only | `network_security_config.xml` |

### 0a. Release build and quality gates (re-run 2026-10-26)

| Check | Result |
|---|---|
| `.\gradlew bundleRelease` | **BUILD SUCCESSFUL** — AAB rebuilt and re-signed |
| Bundle | `app/build/outputs/bundle/release/app-release.aab` |
| Size | 15,654,735 bytes (14.9 MiB) |
| SHA-256 | `02EE0940534398C996575BEAC272230483F1595247DCB87C86FBDC896E89EAED` |
| Signature | `jarsigner -verify` → **jar verified**; self-signed upload key, expires 2053-12-18 |
| `.\gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `.\gradlew lintRelease` | **0 errors**, 55 warnings (dependency-version hints, unused colours, `OldTargetApi`) |
| Permissions in merged release manifest | `INTERNET`, `ACCESS_NETWORK_STATE` only (plus the AndroidX-internal signature-level receiver permission) |
| API level | `targetSdk 36` satisfies the API-level requirement that has applied to new apps and updates since 31 Aug 2026; `compileSdk` 37 |
| Runtime smoke test | Release build installed and launched on an **API 36** emulator (`Pixel_Stable`): reached the sign-in screen, process stayed up, no crash and no `StrictMode`/cleartext/SSL violations in logcat |
| Demo login (NOT the reviewer account) | ✅ Sign-in verified on that emulator → **Dashboard of `Talukder Tiles and Sanitary Corner`**, its only branch. ⚠️ A **live branch with real data**, so it must **not** be submitted as the reviewer account, and its password is no longer recorded anywhere in this folder. A reviewer account needs an isolated review instance first (`review-instance-plan.md`, §1, §7). |
| Store screenshots | ✅ Candidate final set: five dark-theme captures in `screenshots-dark/`; a light-mode set sits in `screenshots-v2/`, whose three data-bearing captures are held back (§1) |

One lint **error** was found and fixed during this pass: a literal BOM character
(U+FEFF) written directly into two `removePrefix("…")` calls in
`core/SheetReader.kt`. Replaced with the `"\uFEFF"` escape — the same string at
runtime, but no longer an invisible character in source. No other source change
was needed.

---

## 1. Store listing

**App name (30 chars max):**
```
CashBookBD
```

**Short description (80 chars max):**
```
Inventory, accounting & HRM for your business — sales, vouchers, reports.
```
(73 characters)

**Full description (4000 chars max):**
```
CashBookBD is a complete business management system for shops, distributors,
manufacturers, real-estate companies and hotels. Your team records the day's
work on the phone; the office sees it instantly on the web.

DASHBOARD AT A GLANCE
• Today's sales, purchases, received and payment — with 14-day trend lines
• Live balance and receivable ageing
• Light and dark mode

ACCOUNTING & VOUCHERS
• Cash received, cash payment, bank received/payment and journal vouchers
• Cash book, bank book, ledger, trial balance, balance sheet and profit & loss
• Bank reconciliation, cheque register, budget and year closing
• Voucher approval workflow with per-user permissions

INVENTORY & PRODUCTS
• Products with brand, category, unit, warranty and multi-warehouse support
• Purchase and sales invoices, returns, orders with contract-quantity tracking
• Stock reports across warehouses, plus low / negative / slow-moving alerts
• Branch issue and receive transfers, material issue, stock slips

CUSTOMERS & SUPPLIERS
• Customer accounts with due tracking and SMS notifications
• Due list, customer ledger, receivable ageing and statements
• Nominee / guarantor records with photo

REAL ESTATE
• Units, buildings, floors, areas and a flat-layout drawing
• Unit sales with pricing builder, parking and custom charges
• Installment schedules, sold-unit reports, sales summary
• Project purchase, labour, expense and income, with project cost reporting

HOTEL
• Room and hall bookings, a bookings calendar and walk-in check-in
• Allotment with guest ID, registration card and bill paper
• Folio, checkout, no-show and room moves
• Housekeeping, amenity issues and hotel reports

HRM & PAYROLL
• Employee records, designations, attendance and leave
• Salary and bonus generation, salary sheets, loans and loan ledger
• Attendance, overtime and holiday-calendar reports

FIXED ASSETS
• Asset register with categories, handovers and verification
• Straight-line depreciation runs, schedule and disposal
• Work-in-progress (CWIP) tracking and QR label printing

BUILT FOR TEAMS
• Role-based permissions — every user sees only what they are allowed to
• Per-branch feature controls: enable only what each branch needs
• Works with your existing CashBookBD web account

CashBookBD is a subscription service. Your business's administrator creates
your account, or you can register a new company from the sign-in screen.
```

**App icon:** `icon-512.png` (512×512 PNG)
**Feature graphic:** `feature-graphic-1024x500.png` (1024×500, 24-bit PNG)
**Phone screenshots — real captures of the signed release (1080×1920, 9:16
portrait, 24-bit PNG, no alpha).** Candidate final set, dark theme:
`playstore/screenshots-dark/`. Upload these five, in this order:
1. `01-navigation-menu-1080x1920.png` — Navigation drawer (every module)
2. `02-transactions-1080x1920.png` — Transaction (voucher types)
3. `03-reports-1080x1920.png` — Reports
4. `04-add-product-1080x1920.png` — Add Product form
5. `05-sales-invoice-1080x1920.png` — Sales Invoice form

Superseded but kept for reference: the light-mode set in
`playstore/screenshots-v2/`. Its `01-dashboard`, `05-product-list` and
`08-sales-ledger` files belong to neither upload set — they show the tenant's own
data and are parked in `screenshots-v2/held-back/` pending your decision.

Extra promotional graphics (optional, for campaigns):
`promo-controls-1024x500.png`, `promo-products-1024x500.png`,
`promo-dashboard-1024x500.png`

**✅ These are captures of the running app, not mockups.**
The four HTML-mockup screenshots and their generator (`screenshots-source.html`)
were **deleted on 2026-10-26**. Both current sets were shot afterwards from the
**signed release build installed on the `Pixel_Stable` emulator (API 36)** after
signing in with the demo account.

- **9:16 portrait**, 1080×1920, 24-bit RGB, no alpha — inside Play's 320–3,840px
  limit for phone screenshots and under its 2:1 aspect-ratio cap. (The AVD's
  native 1280×2856 is 2.23:1 and would be rejected, so the display is overridden
  to `wm size 1080x1920`. The dark set uses one density throughout, `wm density
  383`; the light set used 380, and 383 for its Sales Invoice so that form fits
  without cutting the next control. Note the earlier revision of this document
  called 1080×1920 "16:9" — it is **9:16**, portrait 16:9 being 1920×1080.)
- **Only the app interface**: no device frames, no added captions, text, graphics
  or backgrounds — Play forbids those, so these are deliberately plain UI captures
  rather than marketing compositions.
- Each capture was opened and inspected for a keyboard, dialog, spinner, error
  message, mid-glyph clipping and personal information, and the bottom edge of
  every one was measured pixel-by-pixel so that no row, field or button is sliced
  at the fold.
- **One theme throughout, and it is dark.** The dark set uses the app's own dark
  theme (enabled from the account menu), so no slot is spent on the same screen
  twice. Measured text contrast is strong — drawer items 14.4:1, app-bar titles
  15.8:1, list-row labels 8.4:1, placeholders 7.1:1 — with one exception: the
  accent-coloured field labels on the two forms measure **3.16:1**, under WCAG AA
  (4.5:1). They are legible but dim, and fixing it means changing the theme's
  label colour in the app, not the capture.

⚠️ **The demo login is `Talukder Tiles and Sanitary Corner` — a live production
branch holding real business data, not a sandbox.** An earlier revision of this
document said the demo company "holds no records" and read `0`; that was wrong
then and is wrong now. Its product catalogue, vouchers, ledgers and customer
names are real, so any capture showing them is a disclosure of real customer
data rather than a demo asset.

That is why three captures are **held out of every upload set**:
`01-dashboard`, `05-product-list`, `08-sales-ledger` (light), parked in
`screenshots-v2/held-back/`. The candidate final set in `screenshots-dark/` shows
only the app's interface — menus and empty forms — and contains no customer
names, no credentials and no business figures.

**Category:** Business
**Tags:** accounting, inventory, invoice, cashbook
**Contact email:** `support@cashbookbd.com` — ✅ printed on the live privacy
policy, so the address is in use (§2)
**Website:** `https://cashbookbd.com` — ✅ returns HTTP 200 (checked 2026-10-26)

**Release notes (v1.0, ≤500 chars):**
```
First release. Dashboard, cash & bank vouchers, invoices, inventory and
stock across branches, customer dues, real-estate sales and installments,
hotel bookings, HRM/payroll, fixed assets and the full report set —
with role-based permissions and per-branch feature controls.
```

---

## 2. Privacy policy

**URL to enter in Play Console:**
```
https://my.cashbookbd.com/privacy
```
A policy draft matching the app's actual data behaviour is in
`playstore/privacy-policy-draft.md`.

**✅ Verified live on 2026-10-26: `https://my.cashbookbd.com/privacy` returns
HTTP 200** with no login required, so the reviewer can open it. It is a real,
published policy (effective date "13 August 2026"), not a placeholder — and it
already answers most of the «CONFIRM» items that were open in the repo draft:

| Open item | What the live policy says |
|---|---|
| Support contact | `support@cashbookbd.com` (printed on the page) |
| Retention | Business records kept while the subscription is active; deletion on request when a subscription ends; requests acted on **within 30 days**, except where law requires accounting records to be preserved |
| Deletion route | "contact us directly to request deletion of your account information", sent to the support address |
| Processors / hosting / region | Still not named on the page — **unchanged, still needs your confirmation** |
| Server logs | "server logs of requests and errors" are acknowledged; contents not described — **still needs your confirmation** |
| Children | "not directed at children under 13" — note this differs from the repo draft's "18 and over" (§4 target audience); the **live page is the published wording** and the two should be reconciled |
| Payment references | Not mentioned — the app's subscription form collects a bKash/bank transaction reference, sender number and receiver account (§4, payments). **Consider adding it** to the policy's "information we collect" |

The repo draft is now a working document only. Treat the live page as the source
of truth, and note that it does **not** mention an in-app deletion path (see §6,
item 1): if that path is ever built, both the policy and this file need updating.

---

## 3. Data safety form (answer sheet)

**Does your app collect or share any of the required user data types?** → Yes

**Is all of the user data collected by your app encrypted in transit?** → Yes
(HTTPS/TLS to `my.cashbookbd.com`; cleartext is permitted only to local dev hosts)

**Do you provide a way for users to request that their data is deleted?** → Yes.
The live privacy policy documents it: request deletion from the registered
address to `support@cashbookbd.com`, actioned **within 30 days** (§2, §6). The
deletion **web link** this form also asks for is a separate requirement and does
not exist yet — see §6, item 1.

### Data types to declare

| Category | Data type | Collected? | Shared? | Purpose |
|---|---|---|---|---|
| Personal info | Name | Yes | No | Account management, App functionality |
| Personal info | Email address | Yes | No | Account management |
| Personal info | Phone number | Yes | No | Account management, App functionality |
| Personal info | Address | Yes | No | App functionality (customer/company records, registration) |
| Personal info | User IDs | Yes | No | Account management |
| Personal info | Other info | Yes (NID / passport entered for hotel guests) | No | App functionality |
| Photos and videos | Photos | Yes (customer/nominee photos, profile photo, company logo, uploaded vouchers/slips staff pick) | No | App functionality |
| Financial info | Other financial info | Yes (the business transactions the user enters) **and** the subscription payment reference — bKash/bank transaction id, sender number, receiver account — collected by the manual-payment form (§4) | No | App functionality, Account management |
| Device or other IDs | Device or other IDs | Yes (random per-install ID for the plan's device limit + device name) | No | App functionality, Account management |

**Declare as NOT collected:** precise/approximate location, contacts, SMS or
call logs, browsing history, health & fitness, advertising ID. The app contains
no ads SDK and no third-party analytics. Photos are chosen through the system
photo picker (`ActivityResultContracts.GetContent`), so the app holds no
storage/media runtime permission.

**Ephemeral processing:** No. **Data collection required?** Required — the app
cannot work without signing in.

> Note: most of the records the app stores are **your customer's** business data
> (their customers, suppliers, employees, hotel guests). Confirm your DPA /
> privacy policy covers you acting as a processor for that data, not just the
> account holder's own data.

### Account section (Play policy)
"Does your app allow users to create an account?" → **Yes.** The sign-in screen
opens a public company sign-up (`RegisterScreen` — company, name, contact,
mobile, email, address, password, then mobile OTP). This makes Play's **account
deletion** requirement apply, and it is **not yet met** — see §6.

---

## 4. Other console declarations

- **App access:** ❌ **Blocked — do not submit an account yet.** The app is behind
  a login, so the Console needs a working reviewer account; the only login that
  works today signs in to a **live branch with real customer data**, so it must not
  be given to Google and its credentials are out of this folder. The fix is a
  dedicated review **company** created inside the install the release already
  points at, through the same registration path a customer uses, with invented
  records — **the submitted build then works unchanged**, with no second build and
  no reviewer-only behaviour (`review-instance-plan.md` §3, `reviewer-access.md`).
  ✅ Verified 2026-10-26: a sign-in works and needs no OTP, payment or
  administrator step (see §0a). There is no tenant/subdomain picker to document:
  the build points at one instance.
- **Ads:** No, the app contains no ads.
- **Content rating questionnaire:** Utility/Business app; no user-generated
  public content, no violence, no gambling, no mature content → rating
  "Everyone". (The app does handle real-money business transactions, but it is
  not a game or a dating/social app.)
- **Target audience:** 18 and over (business tool). Not directed at children.
  ⚠️ The live privacy policy says "not directed at children under 13" instead
  of 18 — pick one and make the Console answer and the published page agree.
- **News app:** No. **COVID-19 app:** No. **Government app:** No.
- **Payments policy:** ❌ **Not compliant as it stands — the highest risk in this
  submission.** The flow, read from the code: `PaymentSubmitScreen` shows the
  plans (`GET subscription/plans`) and a form for a **manual** payment
  (`paymentMethod` defaults to `"bkash"`, carrying `paidAt`, `transactionId`,
  `senderNumber`, `receiverAccount` and a note) which POSTs to
  `subscription/manual-payment` for an administrator to approve. No Play Billing
  library is present (`sdkDependencies.txt`).
  Google's Payments policy requires Play's billing system for **in-app purchases
  of digital goods and services**, and it names **"Subscription services"** and
  **"Cloud software and services (such as data storage services, business
  productivity software, or financial management software)"** among the
  categories that need it — which is exactly what CashBookBD sells. It also says
  that *within* an app, developers may not lead users to another payment method,
  including linking to a page that leads there or wording that encourages buying
  outside the app. The consumption-only ("reader") exemption does **not** apply,
  because this app lets the user submit the payment in-app; neither does the
  exemption for purchases that cannot be consumed in the app. **An earlier
  revision of this document called the manual flow compliant — that was wrong.**
  The subscription therefore needs Play Billing, **or** enrolment in one of the
  supported regional programmes (EEA, India, South Korea, or the US
  external-payments programmes). One genuine unknown to confirm rather than
  assume: the requirement does not apply in countries where Play's billing system
  has not launched, and I did not verify Bangladesh's status. Two knock-ons: the
  reference data this form collects is arguably **financial info** the Data safety
  form should declare (§3), and the privacy policy does not mention it (§2).

---

## 5. Reminders

- If the Play developer account is a **personal** account created after
  13 Nov 2023: **12 testers opted in for 14 continuous days** of closed testing
  are required before you can apply for production access. Account verification
  approval is **not** production access.
- Keep `cashbookbd-release.keystore` + its passwords backed up in at least two
  safe places. Losing them means the app can never be updated. (Note: the
  keystore file is deliberately committed to this repo, while `keystore.properties`
  with the passwords is git-ignored.)
- The AAB to upload: `app/build/outputs/bundle/release/app-release.aab`
  (rebuild with `.\gradlew bundleRelease`) — the build prepared here is
  15,654,735 bytes, SHA-256
  `02EE0940534398C996575BEAC272230483F1595247DCB87C86FBDC896E89EAED`.
  Upload this file itself; it is the upload-key-signed bundle and Play re-signs
  the delivered app with the Play App Signing key.
- Before each future upload, bump `versionCode` (and `versionName`) in
  `app/build.gradle.kts`.

---

## 6. Open items that block or risk submission

| # | Item | Status |
|---|---|---|
| 1 | **Account deletion** — app allows account creation but has no in-app delete path and no web deletion URL | ❌ Still unmet; a **user-level** request workflow is now designed (`deletion-request-design.md`) and needs your retention decisions before it is built |
| 2 | Screenshots were HTML mockups | ✅ Replaced by real captures — candidate final set is the dark-theme `screenshots-dark/` (§1) |
| 3 | Reviewer account (login + tenant) | ❌ **None usable** — the working login is a live branch with real customer data; the fix is a synthetic review company in the same install, so the submitted build works unchanged (`review-instance-plan.md` §3) |
| 4 | Privacy policy URL live and public | ✅ Live, HTTP 200, no login (§2) |
| 5 | Support email / website confirmed | ✅ Both confirmed live (§1, §2) |
| 6 | Subscription payments vs. Play Billing | ❌ **Likely non-compliant** — the in-app manual subscription flow needs Play Billing, or enrolment in a regional programme (§4); a decision is required before upload |
| 7 | Closed-testing 12 testers × 14 days (if personal account) | ⚠️ Still depends on the account type — cannot be checked without Console access |
| 8 | Play requirement pages re-verified live | ✅ Re-read 2026-10-26: API 36 requirement, screenshot rules and account-deletion rule all confirmed (§0a, §1, below) |
| 9 | The release keystore file is committed to this repo | ⚠️ Risk — decide deliberately |

**Item 1 — account deletion.** The requirement, re-read from Google's help page
on 2026-10-26, is verbatim: *"If your app enables account creation, you must:
provide users with an in-app path to delete their app accounts and associated
data; and provide a web link resource where users can request app account
deletion and associated data deletion."* It applies here — the sign-in screen
offers public self-registration ("Register your company",
`ui/login/LoginScreen.kt:239` → `RegisterScreen`, company + OTP).

**Both halves were checked again this pass, frontend and backend, and both are
still missing:**

- *Frontend.* No delete-account screen exists (no `deleteAccount` anywhere in
  `app/src/main`), and the client has no call for one: `ApiService.kt` covers
  `login`, `login/release-device`, `register/request-otp`,
  `register/verify-otp`, `devices`, `devices/{tokenId}` and the module
  endpoints, and nothing touches the account itself.
- *Backend.* Now confirmed at the source rather than by probing (the earlier
  revision of this document inferred it from GET requests returning 404 — weaker
  evidence). `App\Http\Controllers\Auth\AuthController` exposes `login`,
  `logout`, `devices`, `revokeDevice`, `revokeDeviceAtLogin`, `me`,
  `profileCover` and `profilePhoto` — no delete method — and a recursive search
  of 616 PHP files across `app/`, `routes/`, `src/`, `modules/` and `backend/`
  for `deleteAccount|delete-account|account/delete|destroyAccount|eraseAccount|purgeAccount|GDPR`
  returned a single hit: `deleteAccountTransactions()` in
  `TradingCombinedController`, which deletes a purchase's ledger lines —
  bookkeeping, not account deletion. Every `account` route in `routes/api.php` is
  accounting (`chart_of_accounts/*`, `account/opening-balance/*`,
  `accounts/payment/*`). Caveat: inspected on branch `Reseller`, commit
  `52d0c2bc`; repeat the search on whatever revision production tracks.
- *What the live service does instead.* `https://my.cashbookbd.com/privacy`
  documents deletion by email to `support@cashbookbd.com`, actioned **within 30
  days** — a real, working process, and the reason §3 can still answer "yes" to
  "do you provide a way to request deletion". It is not, however, the in-app
  path Play requires, and there is no deletion **web page**: `/delete-account`
  and `/account/delete` both 404 on the website.

**So the remaining work is bounded and known:** (a) a backend endpoint plus the
server-side answer on what is erased and what is retained; (b) one screen and one
route off the account menu; (c) a public deletion-request page —
`account-deletion-request.md` is draft content for it. The proposed **user-level**
workflow — which deliberately does not delete the company's books — is in
`deletion-request-design.md`, and it needs your retention decisions before
anything is built. **Nothing was implemented**, and it can only be built and tested
in the isolated environment — which is itself blocked at the schema step
(`tools/review-env/README.md`). The backend is a separate Laravel application and
it is available locally at `F:\All_Database\www\cashbook_api`.

Note the requirement is judged on apps that let users create accounts *in the
app*. The registration flow above is public self-service, so it applies. If the
business decides company sign-up should happen only on the web, that changes the
analysis — but that is a product decision, not a paperwork one.

**Item 9 — the keystore in the repo.** `cashbookbd-release.keystore` is tracked
deliberately (`.gitignore` has `!cashbookbd-release.keystore`), so the upload
key's private material sits in version control and in every clone and any
backup of it. The passwords are not in the repo. With Play App Signing the
consequences are contained — the app-signing key stays with Google and a
compromised upload key can be reset — but this is still a deliberate risk worth
re-taking a decision on, not something to leave implicit.

**Items 3, 5, 6, 7** are decisions or credentials only you can supply, listed
again in the hand-back at the end of this document.

---

## 7. Hand-back: what was done, and what is yours

### Done on this machine (no Console needed)

- Framework, application ID, version, signing and the **merged release
  manifest** re-read; debug/development leakage re-checked and found clean:
  release `BASE_URL` is production HTTPS, `LOCAL_HOST_IP` and both
  `DEV_LOGIN_*` values are empty in the release build type, and no API keys,
  passwords or tokens are hardcoded anywhere in `app/src/main`.
- Signed release bundle built and verified — see §0a for path, size, SHA-256
  and signature.
- Quality gates green: unit tests pass, `lintRelease` reports 0 errors.
- **Signed in to the signed release build with the demo account** on the API 36
  emulator and reached the Dashboard; then browsed the main screens (Dashboard,
  drawer, Transaction, Reports, Products, Product List, Add Product). No OTP, no
  payment, no administrator step. Autofill was switched off first. The one login
  failure seen was a stray character injected by `adb shell input text` — not the
  app, which was proven by verifying the same credentials against the live API.
- **Five real screenshots** captured from that build, each inspected, and
  installed as 1080×1920 24-bit PNGs; the four HTML mockups and their generator
  were deleted (§1).
- **Play's requirements re-read live** this pass — outbound network was
  available: the Android 16 / API 36 target rule, the preview-asset rules
   (9:16 portrait, 1,080–7,680px, app interface only, no frames or added text)
  and the account-deletion rule. Recorded in §0a, §1 and §6.
- **The live privacy policy was fetched and read** — it is published and
  answers the retention and contact questions that were open (§2).
- **The backend was inspected at the source** — routes and controllers on branch
  `Reseller`, commit `52d0c2bc` — so the deletion answer rests on the code rather
  than on probing (§6 item 1 and `review-instance-plan.md`); the **payment flow
  was read from the code** (§4).
- `reviewer-access.md` restructured: the live account's credentials are out of
  it, and it now states what the reviewer account must be — so the Console's App
  access field stays **unfilled** until that account exists.

### Not done — and why

- **The isolated review/development environment is prepared but not finished.** Its
  own checkout, its own `.env` and a new empty database exist; migrations stop at
  `2026_08_18_120000_add_stock_lookup_indexes` because the repository cannot build a
  fresh schema — `inventory_details.branch_id` is created by neither the migrations
  nor the deploy patch. The exact error and both remedies are in
  `tools/review-env/README.md`.
- **Nothing was uploaded and no Console changes were made.** There is still no
  Play Console session and no API service-account credential on this machine,
  and no signing/publishing pipeline in the project (no `fastlane`, no CI
  workflow, no Play publishing plugin). Console access is the blocker now, not
  the build.
- **Account deletion (a) and (b) are not implemented.** (a) needs a backend
  endpoint that provably does not exist, and deleting a company's books is
  irreversible — the design must be agreed first (§6, item 1). Nothing was
  half-built.
- **No public deletion page exists** — `account-deletion-request.md` is
  unpublished content for one.
- **The keystore and the account-type/tester decisions were left alone** —
  they are yours to make, not mine.

### What is yours to decide or supply

1. **Console access, and the account type** — whether this is a personal
   account (needing 12 testers opted in for 14 continuous days before
   Production) or an organisation one. Only visible in the Console.
2. **Account deletion** — a backend endpoint and the retention design; then the
   in-app screen and the deletion page follow. See §6 item 1 and
   `account-deletion-request.md`.
3. **Processors, hosting/region and server logs** — the live policy does not
   name them (§2). Also decide whether the payment reference data now collected
   by the subscription form should be added to the policy and declared as
   financial info in Data safety (§3, §4).
4. **Target audience wording** — reconcile "18 and over" with the live policy's
   "under 13" (§4).
5. **The keystore in the repo** — re-decide whether the upload key's private
   material stays in version control (§6, item 9).
6. **The payments declaration** — answer the Console question using §4's
   description of the manual bKash flow.
7. **Approval to create the synthetic review company.** The reviewer account goes
   on a new company inside the install the submitted build already points at,
   created by the product's own registration path with invented records — no second
   build and no reviewer-only behaviour (`review-instance-plan.md` §3). That means
   writing to production (one company, its synthetic records, one reviewer user
   with a least-privilege role), so it needs your approval; also confirm which
   subdomain is the shared install. The three captures showing real records
   (`01-dashboard`, `05-product-list`, `08-sales-ledger`, light) stay held out of
   every upload set until a synthetic company exists to re-shoot (§1).

### Console steps that remain (manual)

1. In the Console, open the existing app for `com.cashbookbd.app` **before
   creating anything** — do not create a second app for it.
2. Confirm identity/device verification is complete and check whether the app
   has Production access yet (account approval is not app approval, and account
   verification is not Production access).
3. **Closed testing** (if the account is personal): create the track, add the
   testers you authorise, upload the AAB, and let 12 testers stay opted in for
   14 continuous days.
4. Upload `app/build/outputs/bundle/release/app-release.aab` to the appropriate
   track. Accept Play App Signing when asked.
5. Complete the store listing, Data safety, content rating, target audience,
   ads, and App access (demo account) declarations from §1–§4.
6. Apply for Production access once eligible, then do the staged rollout.

