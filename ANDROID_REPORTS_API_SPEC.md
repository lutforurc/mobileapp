# CashbookBD Reports — Android Build Spec

Everything needed to rebuild the 24 sidebar reports as an Android client against the existing
Laravel API: the real endpoints, parameters and response shapes **as they are today**, plus the
inconsistencies that will break a naive port.

Contract derived from the live API (`routes/api.php`, `Reports/ReportsController.php`,
`SomityReportsController.php`) and the React client's actual request payloads.

---

## 1. Decide these three things first

These are not implementation details — they change *what you build*. Settle them before any code.

### 🔴 Blocker — login kills the web session

The login endpoint deletes every existing token for the user before issuing a new one
(`$user->tokens()->delete()`). **One user = one session, server-wide.** The moment someone signs
into the Android app, their browser session ends — and signing back in on the web ends the app
session. Ping-ponging users is the default outcome.

**Choose one:**
- Allow multiple tokens (remove that call, scope tokens by device name), or
- Accept single-session and tell users.

A mobile app on today's behaviour is not shippable.

### 🟠 The API is not uniform — budget for it

These endpoints were grown over time, not designed as a set. Across 24 reports there are
**4 date conventions, 3 parameter-casing styles, and 2 response envelopes**. Nothing is wrong with
the data — but there is no single rule the Android layer can apply.

**Two options:**
- Normalize the backend first (one envelope, one date format, snake_case everywhere) and write a
  clean client, or
- Mirror the mess per endpoint.

Normalizing is a few days of Laravel work and saves that many times over on mobile, especially if
iOS follows.

### 🔵 No pagination anywhere

Not one report endpoint paginates — no `page`, no `per_page`. Every call returns the entire result
set. A wide date range on Cash Book, Ledger or Due List can return a very large payload. On mobile
that means generous read timeouts, no "infinite scroll" design, and pushing users toward narrow
date ranges in the UI.

---

## 2. Auth & transport

- **Base URL** — all report paths sit under `<host>/api`. The host is per-tenant (the web build
  resolves it from an env var), so make it a build/config field on Android, not a constant.
- **Token** — Laravel Sanctum personal access token. Send `Authorization: Bearer <token>` and
  `Accept: application/json` on every call. Store it in `EncryptedSharedPreferences`.

### Login

```
POST /api/login          (no auth)

Body: { "login": "<username|email|phone>", "password": "<secret>" }
      The identifier key may be "login", "email" or "phone" — resolved in that order.

200 OK (success):
{ "success": true,
  "data": { "user": {...}, "token": "<plainTextToken>",
            "company": 1, "branch": "Head Office" },
  "error": { "code": 0 } }

200 OK (FAILURE — note the status code):
{ "success": false,
  "error": { "code": 10001, "message": "Invalid username or password!" } }
```

### ⚠️ Never branch on the HTTP status

A wrong password returns **HTTP 200**. An empty report returns **HTTP 201** (the `notFound()` helper
passes its error code as the status). Retrofit will treat both as success.
**Always read the `success` boolean** — status codes are not meaningful here.

Also: `/api/logout` calls a Passport method on a Sanctum token and will likely throw. Just delete
the token client-side.

---

## 3. Response envelopes — there are two

Roughly 15 of the 25 endpoints wrap their payload in the standard helper. The rest return raw arrays
or objects with **no `success` flag at all**. Your networking layer needs both paths; the table in
§5 marks which is which per report.

### Wrapped (`foundData`)

```json
{ "success": true,
  "message": "",
  "data": {
    "data": "<PAYLOAD>",
    "transaction_date": ""
  },
  "success_code": { "code": 200 },
  "error": { "code": 0 } }
```

The payload lives at **`data.data`** (double-nested). The web app unwraps with `res.data.data.data`.

### Raw (no envelope)

```json
// e.g. trialbalance-level4
[ { "serial": 1, "NAME": "Cash" } ]

// e.g. product-ledger-data
{ "opening": {}, "details": [] }
```

Top-level array or object. No `success`, no nesting. Deserialize directly.

**Empty result** on a wrapped endpoint returns `success: false` with `data.data: []` and **HTTP 201**.
Treat that as "no rows", not an error.

---

## 4. The traps

### Date formats — four conventions

Sending `yyyy-MM-dd` everywhere works for most endpoints but **silently returns wrong or empty data**
on five of them, and **hard-fails** on one.

| Format | Reports |
|---|---|
| `yyyy-MM-dd` | Cash Book, Ledger, Ledger Details, Purchase Ledger, Sales Ledger, Due List, Labour Ledger, Profit Loss, Product Profit Loss, Cat-wise In/Out, Product Stock, Stock Details, Balance Sheet, Trial Balance (both), Datewise Cash Total |
| `dd/MM/yyyy` | **Product In Out, Date-wise In/Out, Bank Information, Connected Member, Monthly Report** — tolerant parser; the web app sends slashes |
| `dd/MM/yyyy` **(strict)** | **Group Report** — validated as `date_format:d/m/Y`. Sending `yyyy-MM-dd` returns **HTTP 422**. The only endpoint that rejects outright. |
| `MM/yyyy` | Collection Sheet — `month_year`, month + year only (see caveat in §5) |

### Parameter casing — three styles

| Style | Where |
|---|---|
| `branch_id`, `start_date` | most endpoints |
| `branchId`, `startDate`, `endDate` | **Labour Ledger, Balance Sheet** |
| mixed | **Cat-wise In/Out** (`branch_id` + `reportType`), **Profit Loss** / **Product Profit Loss** (`branch_id` + `startDate`) |

### Branch & company scoping

- **Company is server-derived** from the token and cannot be overridden. Never send it.
- **Branch is client-supplied** and mostly unvalidated — but the query's company filter means a
  foreign `branch_id` returns empty rather than leaking.
- **Profit Loss ignores `branch_id` entirely** — it always uses the logged-in user's branch. A branch
  picker on that screen would be a lie; hide it or fix the backend.
- Absent branch means different things per endpoint: "all branches" (Ledger, Date-wise In/Out,
  Product Stock), "user's branch" (Profit Loss, Balance Sheet, Product Profit Loss, Stock Details),
  or a 500 (Trial Balance).

### 🔴 Two backend bugs worth a ticket

- **Labour Ledger has no company filter** — only `branch_id`. A guessed branch id returns another
  tenant's data. This is a real cross-tenant leak and should be fixed before the endpoint is exposed
  to a mobile client.
- **Product Stock opening balance isn't branch-scoped** (the filter is commented out), so opening is
  company-wide while movements are per-branch. Per-branch numbers won't reconcile.

---

## 5. The 24 reports

Paths are relative to `<host>/api`. Names match the sidebar.

| Report | Method | Path | Parameters | Date | Response |
|---|---|---|---|---|---|
| Cash Book | GET | `reports/cashbook` | `branch_id`, `start_date`, `end_date` | `yyyy-MM-dd` | **wrapped** — flat rows; opening/total/balance rows inline, identified by `nam` |
| Profit Loss | POST | `reports/profit-loss` | `branch_id` *(ignored)*, `startDate`, `endDate` | `yyyy-MM-dd` | **wrapped** |
| Product Profit Loss | POST | `reports/product-profit-loss` | `branchId` \| `branch_id`, `startDate`, `endDate` | `yyyy-MM-dd` | **wrapped** `{summary, items[], meta}` |
| Bank Information | GET | `reports/bank-information-data` | `branch_id`, `report_type_id` *(1=balance, 2=loan)*, `enddate` | `dd/MM/yyyy` | **raw** collection · unknown type returns `null` body |
| Connected Member | POST | `reports/connected-member-data` | `branch_id`, `startdate`, `enddate` | `dd/MM/yyyy` | **raw** object keyed by employee name |
| Balance Sheet | POST | `reports/balance-sheet` | **`branchId`, `startDate`, `endDate`** | `yyyy-MM-dd` | **wrapped** |
| Trial Balance Group | GET | `reports/trialbalance-level3` | `branch_id`, `start_date`, `end_date` | `yyyy-MM-dd` | **raw** array |
| Trial Balance Details | GET | `reports/trialbalance-level4` | `branch_id`, `start_date`, `end_date` | `yyyy-MM-dd` | **raw** array · field `NAME` is uppercase |
| Ledger | GET | `reports/api-ledger` | `branch_id`, `ledger_id`, `start_date`, `end_date`, `delay=1` | `yyyy-MM-dd` | **wrapped** `{opening_balance, details[]}` |
| Ledger Details | GET | `reports/ledger-with-product` | `branch_id`, `party_id`, `transaction_type` *(1=sales, 2=purchase)*, `item_id`, `start_date`, `end_date` | `yyyy-MM-dd` | **wrapped** `{party, summary, rows[]}` |
| Product In Out | GET | `reports/product-ledger-data` | `branch_id`, `ledger_id` *(= product id)*, `startdate`, `enddate` | `dd/MM/yyyy` | **raw** `{opening, details[]}` |
| Date-wise In/Out | GET | `reports/in-out/date-wise/data` | `branch_id`, `ledger_id` *(= product id)*, `startdate`, `enddate`, `response_type=json` | `dd/MM/yyyy` | **raw** `{success, data[]}` · **returns HTML without `Accept: application/json`** |
| Labour Ledger | POST | `reports/labour/ledger` | **`branchId`, `ledgerId`, `labourId`, `startDate`, `endDate`** | `yyyy-MM-dd` | **wrapped** nested `{branch: {group: rows[]}}` |
| Due List | GET | `reports/duelist` | `branch_id`, `enddate` *(no start date)* | `yyyy-MM-dd` | **wrapped** · extra `.original` layer |
| Collection Sheet | POST | `somity-report/collection-sheet` | `branch_id`, `somity_id`, `month_year`, `type_id` *(1=opening)* | `MM/yyyy` | **raw** array · **verify separator, see below** |
| Monthly Report | POST | `somity-report/monthly-report/data` | `branch_id`, `startdate`, `enddate` | `dd/MM/yyyy` | **raw** object keyed by date · **not concurrency-safe** |
| Datewise Cash Total | GET | `reports/date-wise-total-data` | `branch_id`, `start_date`, `end_date` | `yyyy-MM-dd` | **wrapped** |
| Product Stock | POST | `reports/product-stock` | `branch_id`, `brand_id`, `category_id`, `product_name`, `startdate`, `enddate` | `yyyy-MM-dd` | **wrapped** — opening/stock_in/stock_out/balance |
| Stock Details | POST | `reports/closing-stock` | `branch_id`, `end_date` *(also accepts `enddate`/`endDate`)* | `yyyy-MM-dd` | **wrapped** object **grouped by brand** · writes closing stock as a side effect |
| IMEI Stock | GET | `reports/stock-imei-data` | `branch_id`, `item_id` *(no dates)* | — | **wrapped** — **object keyed `"1"`,`"2"`… but `[]` when empty** |
| Cat-wise In/Out | POST | `reports/category-wise-in-out` | `branch_id`, **`reportType`** *(1=purchase, else sales)*, `category_id`, `startdate`, `enddate` | `yyyy-MM-dd` | **wrapped** |
| Purchase Ledger | GET | `reports/purchase/ledger` | `branch_id`, `ledger_id`, `item_id`, `startdate`, `enddate`, `search`, `delay=1` | `yyyy-MM-dd` | **wrapped** |
| Sales Ledger | GET | `reports/sales/ledger` | `branch_id`, `ledger_id`, `item_id`, `startdate`, `enddate`, `search`, `delay=1` | `yyyy-MM-dd` | **wrapped** |
| Group Report | POST | `reports/group/report/data` | `branch_id`, `report_group` *(1 or 2 only)*, `startdate`, `enddate` | **`dd/MM/yyyy` strict** | **custom** `{reportType, monthNames, report_data, productsName, purchases_data}` |

### ⚠️ Per-report quirks to hard-code

- **Group Report "All"** — there is no server-side "all groups". The web app fires two parallel calls
  (`report_group=1` and `=2`) and merges client-side. Do the same. Only group `1` fills `report_data`;
  only `2` fills `productsName` + `purchases_data`.
- **IMEI Stock** — the payload is a JSON *object* keyed `"1"`, `"2"`… when populated but an *empty
  array* when not. Kotlin will throw on the type flip; parse defensively.
- **Collection Sheet** — the server prepends `"01/"` to `month_year`, implying `MM/yyyy`, but the web
  client sends `MM$yyyy` with a literal `$`. **These disagree.** Capture the real request from the
  working web app before coding this one.
- **Monthly Report** — each call deletes and rebuilds server-side rows for the user. Two concurrent
  calls by the same user corrupt each other. Never fire it in parallel or from a retry-happy client.
- **Stock Details** — recomputes and writes closing stock before reading. It is a mutation; keep it
  POST and never auto-retry it.
- **Due List / Bank Information** — take an end date only. No date range picker.

---

## 6. Suggested Android architecture

**Stack**
- Kotlin + Jetpack Compose
- Retrofit + OkHttp + kotlinx.serialization (or Moshi)
- Hilt for DI, ViewModel + StateFlow per report
- Paging is *not* applicable — no server pagination

**Networking**
- OkHttp interceptor adds `Authorization` + `Accept: application/json`
- Read timeout ≥ 60s — reports are unpaginated
- Branch on `success:false`, not on status
- `ignoreUnknownKeys = true` — payloads carry many unused fields

**Envelope handling**
- `ApiEnvelope<T>` with nested `data.data` for wrapped endpoints
- Raw endpoints deserialize straight to the model — no envelope type
- One `Result` mapper so screens don't care which shape it was

**Dates**
- Keep `LocalDate` in the domain layer
- Format **at the API boundary, per endpoint**, from the table above
- Never a single global date formatter — that is the bug this API invites

### Build one report end-to-end first

Start with **Cash Book**: wrapped envelope, snake_case, `yyyy-MM-dd`, no quirks. It proves auth, the
envelope, the branch/date filters and the table UI. Then do **Trial Balance Details** to prove the
raw path, and **Group Report** to prove strict dates + the two-call merge. Those three cover every
pattern in the API; the remaining 21 are variations.

---

## 7. Prompt for Android Studio

Give the assistant the **contract, not the goal** — "build a Cash Book report" produces invented
endpoints. Feed it the constraints and one concrete report at a time.

```text
Build an Android report screen in Kotlin + Jetpack Compose, MVVM with Hilt,
Retrofit + OkHttp + kotlinx.serialization.

API CONTRACT — follow exactly, do not invent endpoints or fields.
Base URL: {BASE_URL}/api/
Auth: Authorization: Bearer <token>, Accept: application/json (OkHttp interceptor).

CRITICAL RULES:
1. Success is the JSON field "success", NOT the HTTP status. Login failure returns
   HTTP 200 with success=false. An empty report returns HTTP 201. Never use
   response.isSuccessful to decide success.
2. Responses have TWO shapes. Wrapped endpoints:
      { success, data: { data: PAYLOAD }, error }   -> payload is at data.data
   Raw endpoints return the payload at the top level with no "success" field.
   I will tell you which shape each endpoint uses.
3. There is NO pagination. Every call returns the full result set. Use a 60s read
   timeout and render the whole list.
4. Date formats differ PER ENDPOINT. Use exactly the format I give you for each.
   Do not create a single shared date formatter.
5. Set ignoreUnknownKeys = true; responses contain many fields I don't need.

REPORT TO BUILD: Cash Book
  GET reports/cashbook
  Query: branch_id (Int), start_date (String), end_date (String)
  Date format: yyyy-MM-dd
  Response: WRAPPED. Payload at data.data is a flat JSON array of rows.
  Row fields I need: vr_date, vr_no, nam, remarks, debit, credit
  Note: opening/total/balance rows are mixed INTO the same array — they are
  identified by nam == "Opening Balance" | "Total" | "Balance". Render those as
  summary rows, not normal transactions.

DELIVERABLES:
  - CashBookDto + ApiEnvelope<T> types
  - CashBookApi (Retrofit interface)
  - CashBookRepository returning Result<List<CashBookRow>>
  - CashBookViewModel exposing StateFlow<UiState> (Loading/Empty/Error/Data)
  - Compose screen: branch + date-range filters, a scrollable table,
    and distinct styling for the summary rows.
```

For each further report, swap the **REPORT TO BUILD** block for that report's row from the table in
§5, and add its quirk from the notes. Keep the **CRITICAL RULES** block unchanged every time — it is
the part the assistant will otherwise get wrong.
