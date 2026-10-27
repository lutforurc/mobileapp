# Reviewer access — CashBookBD (`com.cashbookbd.app`)

> ## ⛔ Do not submit an account for this app yet
>
> The only working login today is the account named below, and it signs in to a
> **live branch holding real business and customer data**. It must not be handed
> to Google, pasted into the Console, or used as the App access account, and its
> password is deliberately not recorded in this file.
>
> **Leave Play Console → App content → App access unfilled until a dedicated
> reviewer account exists on an isolated review instance.** What that account has
> to be is set out in *What the reviewer account must be* below; how to stand the
> instance up is in `review-instance-plan.md`.

## Why access is needed

The whole application sits behind a sign-in. Without a working login a reviewer
reaches only the login screen, and the release is rejected. A reviewer account
*is* required — it just cannot be the live one.

## Status

| Item | State |
|---|---|
| Reviewer account for the Console | ❌ Not available — do not submit one |
| Isolated review instance with synthetic data | ❌ Not built — see `review-instance-plan.md` |
| Live account for this build | ⚠️ Exists and works, but **must not be used for review** |

## Internal reference — the live account (do not submit)

Kept here only because it is the account the current build is verified against.

- **It is a live production branch, not a sandbox.** The company
  `Talukder Tiles and Sanitary Corner` holds real records — product catalogue,
  vouchers, ledgers and customer names — and the Dashboard shows real figures.
- **Every write is real.** There is no dry-run and no undo, and other people work
  in the same books. Treat the account as strictly read-only: navigate, open
  forms and read reports. Never create, edit, delete or import a record; never
  submit a payment, invoice, voucher, product or subscription; never change
  branch settings, users, permissions or notifications.
- **Its credentials are not stored here.** Ask the account owner.
- Two facts that will also apply to any future account: the app has no
  tenant/subdomain picker (the build points at exactly one instance), and a
  subscription caps simultaneous devices — if that panel appears, sign a listed
  device out and continue.

## What the reviewer account must be

1. **Dedicated.** Created for review only — never the live account, never a real
   customer's login.
2. **On an isolated instance.** Its own deployment and its own database, seeded
   with synthetic data, publicly reachable over HTTPS. A `.test` host or a
   staging box on a developer machine is not reachable by Google's reviewers, so
   it cannot satisfy this requirement.
3. **Synthetic data only.** An invented company, products, customers and
   vouchers. No copy, export or subset of any real customer's data.
4. **The same app, same configuration.** The released build must reach the review
   instance through the supported base-URL setting in `app/build.gradle.kts`.
   No reviewer-only behaviour, backdoor or hidden switch is added — the app must
   behave for reviewers exactly as it behaves for a customer.
5. **Least privilege.** Ideally a read-only role: the app has real role-based
   permissions, so a reviewer should not be able to alter anything even by
   accident.
6. **Recorded here when it exists.** Identifier, password and the sign-in steps
   below, in this file — which is the copy that gets pasted into the Console.

## Sign-in steps (for the future account)

1. Install and open CashBookBD (`com.cashbookbd.app`).
2. On the sign-in screen, enter the identifier and password.
   "Remember me" is optional and changes nothing for the review.
3. Tap **Log in**. No OTP, no SMS, no email confirmation and no administrator
   approval is involved — the account signs in directly.
4. The Dashboard opens. Everything else is in the hamburger menu at the top left.

Once an account exists, the reviewer can reach every module from that menu:
Dashboard, Reseller Dashboard, Transaction, Invoice, Branch Transfer, Reports,
Product Tracking, Company Scheme, Requisition, Hotel, Assets, Products, Labour
Items, Old Software, Admin and VR Settings. Menus, forms, filters and reports all
open, and read-only browsing is enough to review the app.

## If the login fails

Treat it as a real problem rather than a reviewer error: reset the password in
the admin area and update this file and the Console field together. **Do not
substitute the live account as a workaround.**
