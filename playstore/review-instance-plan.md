# Reviewer access and the review environment — corrected plan

Supersedes the earlier revision of this file, which wrongly proposed a separate
review deployment and claimed the repository had no usable seeding mechanism.
What follows is what the code actually supports.

## 1. The submitted build keeps its production BASE_URL

`app/build.gradle.kts` has one switch, `baseUrl`, which is compiled into
`BASE_URL` **and** selects the per-tenant branding key (the subdomain). The public
release stays on `https://my.cashbookbd.com/api/`. A review-server-only build is
not a solution for the public release, so the reviewer has to reach the app
through the same install normal customers use.

## 2. What that install supports (verified in the backend)

* Tables are `com_`-prefixed (`com_companies`, `com_branches`); `config/database.php`
  sets no prefix, so those are the real names.
* **17 migrations carry a `company_id`** — branches, all four COA levels, units,
  roles. `com_companies` holds `branch_quata` / `user_quata` / `status`. So one
  install holds **many companies**, each with its own quotas.
* Company self-registration provisions a complete company through the product's own
  code: `com_companies` → `com_branches` → `InterBranchAccountHead::ensureForBranch()`
  → day-close → chart of accounts, roles, admin user. Its own comment: *"a
  self-registered company is set up the same as one opened by hand."*

## 3. The proposal: a synthetic company, not a new deployment

Create a dedicated review company **inside the install the release already points
at**, through the same provisioning path a customer uses, with invented records and
a dedicated reviewer user carrying a least-privilege role (the permission system is
real: `spatie/laravel-permission` plus `config/route_permissions.php`).

That gives:

* the submitted AAB/APK works **unchanged** — no second build, no rebuild, no
  `BASE_URL` change;
* the reviewer sees only that company's synthetic data, because the application
  scopes data by company;
* **no reviewer-only behaviour, backdoor or hidden switch**;
* normal customers are untouched.

One open question it rests on: whether `my.cashbookbd.com` is the shared install or
a single company's own install. The evidence above says the application supports
many companies per install; only your hosting and DNS records settle which
subdomain is which. If `my` turns out to be one company's own install, the review
company belongs on whichever install is shared instead — still the same build.

**Creating anything in production needs your approval.**

## 4. Alternatives (assessed, not assumed)

| Option | Same submitted build? | Reviewer writes blocked? | Real data exposed? |
|---|---|---|---|
| Synthetic company in the same install (proposed) | Yes | Yes, with a least-privilege role | No |
| Separate public staging install | No — needs its own build | Yes | No |
| Dedicated least-privilege user in a live company | Yes | Yes | **Yes** |
| Local/staging on this machine | n/a | Yes | No — but reviewers cannot reach it, so it cannot satisfy App access |
| Reviewer-only behaviour in the app | — | — | **Excluded** |

## 5. The three tasks are separate concerns

| Task | Where it lands | State |
|---|---|---|
| Reviewer access using the submitted build | The synthetic company above | Proposed; needs approval |
| Isolated development and testing | `tools/review-env/` — its own checkout, its own empty database | Built; blocked at the schema step |
| Public deletion page + in-app request flow | Designed in `deletion-request-design.md` | Design only |

They do not need one environment between them: only the first touches production,
and only behind an approval.

## 6. Seeding mechanisms that do exist

Correcting the earlier "no seeders" claim — absence of `database/seeders` is not
absence of mechanism:

* **`patch:` artisan commands.** `patch:add-unit-type` runs on every deploy and is
  the broad schema-and-data patch (`AddUnitTypeToBuildingUnits`), alongside 50 SQL
  files in `database/sql/`.
* **The registration provisioning path** (section 2) — the supported way to create
  a company, and what `tools/review-env/_review_seed.php` reuses.
* `database/factories/UserFactory.php`.
* `app/Console/Commands`: `InstallSiteSchema`, `SeedWebsiteTemplates`,
  `DraftRoutePermissionMap`, the legacy importers and data-fix commands.

## 7. Account deletion

The endpoint does not exist, verified at the source: `AuthController` exposes
`login`, `logout`, `devices`, `revokeDevice`, `revokeDeviceAtLogin`, `me`,
`profileCover`, `profilePhoto`, and a recursive search of 616 PHP files across
`app/`, `routes/`, `src/`, `modules/` and `backend/` for the deletion keywords
returned one unrelated hit (`deleteAccountTransactions()`, which removes a
purchase's ledger lines). Caveat: branch `Reseller`, commit `52d0c2bc` — repeat the
search on the revision production tracks.

The proposed **user-level** request workflow is in `deletion-request-design.md`.
Deleting a user is not deleting the company's books, and the design keeps them.
