# Play Store Listing — CashBookBD

Everything the Play Console asks for at submission, ready to paste.
Asset files referenced here live in this same `playstore/` folder.

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
manufacturers and real-estate companies. Your team records the day's work on
the phone; the office sees it instantly on the web.

DASHBOARD AT A GLANCE
• Today's sales, purchases, received and payment — with 14-day trend lines
• Live balance and receivable ageing
• Light and dark mode

ACCOUNTING & VOUCHERS
• Cash received, payment, bank and journal vouchers
• Cash book, ledger, trial balance, balance sheet and profit & loss
• Voucher approval workflow with per-user permissions

INVENTORY & PRODUCTS
• Products with brand, category, warranty and multi-warehouse support
• Purchase and sales invoices, orders with contract quantity tracking
• Stock reports — including zero-balance items when you want them

CUSTOMERS & SUPPLIERS
• Customer accounts with due tracking and SMS notifications
• Due list, customer ledger and receivable ageing reports

REAL ESTATE
• Unit sales with pricing builder, parking and custom charges
• Allotment letters, booking forms, nominees and installment schedules
• Project income and expense tracking, sold-unit reports

HRM & PAYROLL
• Employee records, attendance, salary sheets and loans

BUILT FOR TEAMS
• Role-based permissions — every user sees only what they are allowed to
• Per-branch feature controls: enable only what each branch needs
• Works with your existing CashBookBD web account

CashBookBD is a subscription service. Your business's administrator creates
your account — sign in with the same credentials you use on the web.
```

**App icon:** `icon-512.png` (512×512 PNG)

**Feature graphic:** `feature-graphic-1024x500.png`

**Phone screenshots (1080×1920, upload in this order):**
1. `screenshot-1-dashboard-light-1080x1920.png`
2. `screenshot-2-dashboard-dark-1080x1920.png`
3. `screenshot-3-feature-controls-1080x1920.png`
4. `screenshot-4-product-setup-1080x1920.png`

Extra promotional graphics (optional, for campaigns):
`promo-controls-1024x500.png`, `promo-products-1024x500.png`,
`promo-dashboard-1024x500.png`

**Category:** Business
**Tags:** accounting, inventory, invoice, cashbook
**Contact email:** support@cashbookbd.com  ← confirm this inbox exists before submitting
**Website:** https://cashbookbd.com

---

## 2. Privacy policy

**URL to enter in Play Console:**
```
https://my.cashbookbd.com/privacy
```

The page is added to the Laravel app (`routes/web.php` → `resources/views/public/privacy.blade.php`).
⚠️ It must be DEPLOYED to the my.cashbookbd.com server before you submit —
Google's reviewer opens the URL. Verify it loads without login after deploy.

---

## 3. Data safety form (answer sheet)

**Does your app collect or share any of the required user data types?** → Yes

**Is all of the user data collected by your app encrypted in transit?** → Yes

**Do you provide a way for users to request that their data is deleted?** → Yes
(via support email, stated in the privacy policy)

### Data types to declare

| Category | Data type | Collected? | Shared? | Purpose |
|---|---|---|---|---|
| Personal info | Name | Yes | No | Account management, App functionality |
| Personal info | Email address | Yes | No | Account management |
| Personal info | Phone number | Yes | No | Account management, App functionality |
| Personal info | User IDs | Yes | No | Account management |
| Photos and videos | Photos | Yes (customer/nominee photos staff upload) | No | App functionality |
| Financial info | Other financial info | Yes (business transactions the user enters) | No | App functionality |
| Device or other IDs | Device or other IDs | Yes (device id for session/device-limit) | No | App functionality, Account management |

**Declare as NOT collected:** location, contacts, SMS/call logs, browsing
history, health, advertising ID. The app contains no ads SDK and no
third-party analytics.

**Ephemeral processing:** No. **Data collection required?** Required (the app
cannot work without signing in).

### Account section (Play policy)
"Does your app allow users to create an account?" → **No** — accounts are
created by the business's administrator, not inside the app. So no in-app
account-deletion URL is required; the privacy policy's deletion contact covers
data-deletion requests.

---

## 4. Other console declarations

- **App access:** The whole app is behind a login. Provide Google a demo
  account: create a user on a demo tenant (e.g. ABC Traders demo data) and
  enter its credentials under "App access → All or some functionality is
  restricted". Reviewers WILL reject the release if they cannot log in.
- **Ads:** No, the app contains no ads.
- **Content rating questionnaire:** Utility/Business app, no user-generated
  public content, no violence etc. → rating "Everyone".
- **Target audience:** 18 and over (business tool). Not directed at children.
- **News app:** No. **COVID-19 app:** No. **Government app:** No.

---

## 5. Reminders

- If the Play developer account is a **personal** account created after
  Nov 2023: 12 testers for 14 days of closed testing are required before
  production access.
- Keep `cashbookbd-release.keystore` + its passwords backed up in at least
  two safe places. Losing them means the app can never be updated.
- The AAB to upload: `app/build/outputs/bundle/release/app-release.aab`
  (rebuild with `.\gradlew bundleRelease`).
- Before each future upload, bump `versionCode` (and `versionName`) in
  `app/build.gradle.kts`.
