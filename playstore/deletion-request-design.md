# Account deletion — proposed request workflow

Design for approval. **Nothing here is implemented or tested**; the endpoint does
not exist yet (verified at the source — see `review-instance-plan.md` §6), and the
isolated environment this would be built in is currently blocked at the schema
step (`tools/review-env/README.md`).

## The distinction that matters

Deleting a **user** is not deleting the **company's books**. The books belong to
the company and are shared: ledgers, vouchers, stock movements and tax records.
A clerk leaving must not erase them, and accounting law usually requires keeping
them. So this design is deliberately **user-level first**:

* **User account deletion** — the person's own account and personal data. In scope
  here.
* **Company deletion** — the whole tenant and its books. Out of scope for this
  pass; it needs company-admin consent, a "no other active users" rule, and its own
  retention decision. Do not equate the two.

## What Play requires

Verbatim, for an app that allows account creation (this one does — public company
registration with OTP):

> provide users with an in-app path to delete their app accounts and associated
> data; and provide a web link resource where users can request app account
> deletion and associated data deletion.

Two deliverables: **in-app path** and **web resource**.

## Proposed flow

### In-app

1. Account menu → **Delete my account** (next to Profile).
2. A plain screen stating exactly what is destroyed, what is retained, and how
   long it takes. No dark patterns: the destructive action is not the default.
3. Confirmation requires the account password re-entered, so a borrowed unlocked
   phone is not enough.
4. `POST /account/deletion-request` (new) creates a request row and returns the
   retention summary and a reference id.
5. Immediately: the user is signed out and blocked from signing in again — their
   tokens and device registrations are revoked, and the login is disabled. The
   request is then actioned (see retention below).

### Web resource

A public page (marketing site, no login required) with a short form: company,
account identifier, and a verification step. It writes to the same table, so one
admin queue serves both routes. `account-deletion-request.md` already holds draft
content for the page.

### Backend shape

| Piece | Detail |
|---|---|
| Table | `user_deletion_requests` — user id, company id, requested_at, source (`app`/`web`), status, decided_by, decided_at, actioned_at, reason |
| Route | `POST /account/deletion-request` (authenticated, throttled), inside the existing `auth:sanctum` group |
| Admin | A queue listing pending requests, with approve/reject and an audit record. Admin screens already exist (`ApprovalCenterController`) |
| Audit | Every state change written to the existing audit trail |

## Retention — what is destroyed vs kept

Proposed, and **this is the part that needs your decision before anything is
built**:

| Data | Proposal |
|---|---|
| Name, email, phone, address, photo, password hash | **Destroyed** |
| Sessions, tokens, device registrations, notification preferences | **Destroyed** |
| The vouchers, ledgers and stock movements the user created | **Kept** — company property; the user reference is anonymised so the books stay intact |
| Audit-trail entries naming the user | **Kept** (legal), with the same anonymisation |
| Deletion request record | **Kept** as proof of compliance |

Anonymisation, rather than deletion, is what keeps the books reconcilable while
still removing the person. If you would rather destroy the attribution entirely,
that needs a decision on how the audit trail then reads.

## Decisions needed before implementation

1. **Company deletion** — in scope later, or never? If later, who consents?
2. **Retention window** for the request record and the audit anonymisation — the
   live privacy policy promises action within 30 days; confirm that is the
   committed window.
3. **Where the web page lives** — marketing site or tenant app?
4. **Anonymise vs destroy** the user's attribution in the retained books.
5. **Who actions the queue** — the company admin, CashBookBD support, or both?

## Build and test plan

1. Unblock the isolated environment (schema bootstrap — `tools/review-env/README.md`).
2. Migration + route + controller + the two screens + the web page.
3. Test the whole flow **only** in that isolated environment against synthetic
   users: request, revocation, anonymisation, retention of the books, admin queue.
4. Never exercise it against the live account, and never submit a deletion request
   for a real user.
