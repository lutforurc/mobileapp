# CashBookBD — Delete your account and data (DRAFT page)

> **Status: DRAFT — not published, not legal advice.**
> This is the content of the *public web page* that Google Play requires of any
> app that lets users create an account, entered in Play Console →
> **Data safety → Data deletion**. It must load **without a login**, at a stable
> public URL, and stay live.
>
> Written from what the Android app actually does (inspected in code). Every
> **«CONFIRM»** is a fact that lives in the CashBookBD backend or the business,
> not in this repo, and must be filled in before publishing. Do not publish with
> the placeholders still in it.
>
> **The in-app half is not built yet.** Google's requirement has two halves:
> a way to delete the account *in the app*, and this web page. The app has no
> delete-account screen and the client has no endpoint for it — see
> `play-listing.md` §6, item 1. This page alone does not satisfy the policy.

---

**Suggested URL:** `https://my.cashbookbd.com/delete-account` «CONFIRM — publish
it here, or wherever your website already lives, and use the final URL in the
Console»

## Delete your CashBookBD account and data

CashBookBD is a business management service: a company signs up, and its staff
record the day-to-day work in the app. Account holders are the business's own
administrators.

### Ask us to delete the account

Send the request from the email address «CONFIRM — the address the account is
registered with», or from the administrator's own address, to:

**«CONFIRM — support email, e.g. support@cashbookbd.com»**

Please include:

- the **company name** and the **subdomain** you sign in at
  (CashBookBD is run per-company, so a company name alone may not be enough);
- the **login** (email or mobile number) of the account to be removed;
- whether you want the **whole company account** closed, or only one **user**
  removed. «CONFIRM — whether removing a single user is a self-service action
  the administrator can already do inside the app.»

If the request comes from someone other than the registered administrator, we
may ask for evidence that they are authorised to act for the company.
«CONFIRM the verification you will actually do.»

### What gets deleted

«CONFIRM each line against what your backend really does. Do not promise more
than it delivers.»

- The account and its login, and the sessions/tokens that go with it.
- The business records in it — vouchers, invoices, products, stock movements,
  ledgers, HRM/payroll entries, customer and supplier records, hotel bookings
  and guest records, and the photos and scanned documents stored with them.
- The device records used for the subscription's per-install device limit.

### What we have to keep, and for how long

«CONFIRM — this is the part that varies by business and jurisdiction.»

- Records a company is legally required to keep (for example accounting and tax
  records) are retained for «CONFIRM period» and then deleted. «CONFIRM whether
  this applies, and for how long, for your business.»
- Backups: data may remain in backups for up to «CONFIRM period» before those
  backups expire. «CONFIRM whether backups exist and for how long.»

### How long it takes

We aim to confirm the request within «CONFIRM — e.g. 7 days» and to complete the
deletion within «CONFIRM — e.g. 30 days».

### Before you ask

Deleting a company account removes everything in it, for every user of that
company, and cannot be undone. If you only want to stop using CashBookBD, sign
out or ask us to cancel the subscription instead. «CONFIRM that cancelling a
subscription is a separate action from deleting the account, and how either is
done.»

---

## Facts to confirm before publishing this page

1. The URL it will live at, and that it loads without a login.
2. The support email and inbox that will actually receive these requests.
3. Whether the customer contract / DPA already describes deletion, and in whose
   role (controller or processor) the deletion happens.
4. Whether a single user can be removed by the administrator in-app, or whether
   every removal is a support request.
5. The verification done before acting on a request.
6. Retention periods: legal/accounting records, and backups.
7. The acknowledgement and completion timeframes.
8. Whether the in-app deletion path will be built (it is required, and it is not
   built today).
