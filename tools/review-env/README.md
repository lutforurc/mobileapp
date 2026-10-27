# Isolated review/development environment

Three release concerns that are easy to conflate, and where each actually lives:

| Concern | Where it belongs | State |
|---|---|---|
| Reviewer access for the Play submission | The install the submitted build already points at (`my.cashbookbd.com`) — a synthetic *company* inside it | Proposed, not created (needs approval) |
| Isolated development/testing (write tests, deletion flow) | This directory: its own checkout, its own empty database | Built, and blocked at the schema step (below) |
| Public account-deletion page + in-app request flow | Design in `../../playstore/deletion-request-design.md` | Design only |

Nothing here is the Play review environment. Google's reviewers cannot reach a
`.test` host, and a separate build for them is explicitly not acceptable — the
submitted build keeps its production `BASE_URL`.

## What these scripts build

| File | Role |
|---|---|
| `setup-review-env.ps1` | Independent checkout, its own `.env`, a NEW empty database, migrations |
| `_review_db.php` | Creates the empty database. Refuses anything not named `cashbookbd_review*` |
| `seed-synthetic-company.ps1` | Runs `_review_seed.php` in the review checkout |
| `_review_seed.php` | Creates a fictional company by driving the product's own `POST /api/register/verify-otp` provisioning path |

Run order:

```powershell
.\setup-review-env.ps1                 # add -WhatIf to preview
.\seed-synthetic-company.ps1
```

## Guards — why this cannot hit a business database

* The target database must start with `cashbookbd_review`, and a deny-list refuses
  `krishibitandatabase`, `myappsaasdatabase` and the other existing names outright.
* The database must not already exist; the PHP helper refuses to touch one that does.
* The source checkout is only ever **read** — its `.env` is copied, never edited,
  and `DB_DATABASE` in it is never repointed. The target is a different name, so a
  stray migration cannot land on it.
* Migrations run only after the review checkout's own connection has been asked
  what database it is actually on, and the answer is checked against the target —
  the same guard shape the repository already uses in `_site_patch_run.php`.
* `SMS_ENABLED=false` in the review `.env`, so a synthetic run can never send a real
  message.
* No password is printed, logged or echoed. The copied `.env` carries the source's
  credentials untouched.

## What has actually been done

* Checkout: `F:\All_Database\www\cashbook_review` (independent clone of the backend
  at branch `Reseller`, commit `52d0c2bc`, with `vendor/` copied and the runtime
  directories git does not track recreated).
* Database: `cashbookbd_review` — **new and empty**, created by `_review_db.php`.
* Migrations: ran to `2026_08_18_120000_add_stock_lookup_indexes` (79 applied) and
  **stopped there**. The application cannot run yet, so the seed has not been
  executed.

## The blocker: a fresh schema cannot be built from this repository

```
2026_08_18_120000_add_stock_lookup_indexes  FAIL
SQLSTATE[42000]: 1072 Key column 'branch_id' doesn't exist in table
  ALTER TABLE `inventory_details`
    ADD INDEX `idx_inventory_details_branch_product_master`
    (`branch_id`, `product_id`, `inv_mstr_id`, `stock_in`, `stock_out`)
```

Why it happens:

* `2018_10_20_160752_create_inventory_details_table.php` creates
  `inventory_details` as a **stub — only `id` and `timestamps`**. Every other
  column was added later.
* `branch_id` is never added by any migration, and `database/sql/` has no patch for
  it either.
* The supported deploy patch fails too, on the same column:
  `php artisan patch:add-unit-type` →
  `Failed: 1072 Key column 'branch_id' doesn't exist in table`.

So the repository has **no reproducible path from an empty database to a working
schema**. The deployed databases carry columns that are in neither the migrations
nor the patch command — they were added to live schemas by hand, or by an earlier
generation of patches that is not in the tree.

Two ways to unblock it, both needing your decision:

1. **Structure-only snapshot.** Produce the schema, without any rows, from a
   deployed instance (`mysqldump --no-data`, which copies structure only — no
   customer records, no business data) and load that into `cashbookbd_review`
   instead of migrating. Fast and faithful, but it needs someone with database
   access to run the dump; I will not take it from production.
2. **Fix the chain in the repository.** Add the missing `branch_id` (and check for
   the same class of gap in the other 49 patch files) so `php artisan migrate`
   completes on a fresh database. Better long-term — a product that cannot stand up
   a new install from its own tree is a problem beyond this release — but it is a
   change to the backend, and it is yours to authorise.

Either way, once the schema builds, `.\seed-synthetic-company.ps1` is ready: it
inserts the OTP row the registration flow would have written and then dispatches
the real `verify-otp` route, so the synthetic company is provisioned by the
product's own code — company, branch, inter-branch head, day-close, chart of
accounts, roles and admin user. Every value in it is invented; no real record is
read, copied or referenced anywhere in these scripts.
