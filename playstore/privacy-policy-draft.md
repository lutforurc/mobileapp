# CashBookBD — Privacy Policy (DRAFT)

> **Status: DRAFT for review — not legal advice, not yet published.**
> Written from what this Android app actually does (inspected in code). Anything
> the app sends to a server that this repo does not control is marked
> **«CONFIRM»** — those facts live in the Laravel backend and must be verified
> before this is published. Do not publish it with the placeholders still in it.
>
> Publish at the URL entered in Play Console. The listing points at
> `https://my.cashbookbd.com/privacy`, which must load **without login**.

---

**Effective date:** «CONFIRM — date it goes live»
**Applies to:** the CashBookBD Android application (`com.cashbookbd.app`) and the
CashBookBD web application.

## 1. Who we are

CashBookBD is a business management ("ERP") service. A business signs up and its
staff use the app to record the day-to-day work — sales, purchases, vouchers,
inventory, customers, employees and so on.

**Data controller / contact:** «CONFIRM legal entity name, address, and a
working contact — e.g. support@cashbookbd.com».

Because most of the information in the app is entered by a business about *its
own* customers, suppliers, employees or guests, that business is the controller
of those records and CashBookBD processes them on its behalf. «CONFIRM this
processing role is reflected in your customer contract / DPA».

## 2. Information the app collects

Taken directly from the app's behaviour:

- **Account details** — company name, user name, contact person, mobile number,
  email address and address when a company is registered; and the identifier
  (email or phone) and password when someone signs in. A one-time password (OTP)
  is sent to the mobile number given at registration.
- **Business records** — everything staff enter in the app: vouchers, invoices,
  products, stock movements, ledgers, HRM/payroll data and the like. This
  includes information about *other people*: customer names, phone numbers,
  emails, addresses, opening balances, and **photos** of customers and nominees;
  hotel **guest names and NID / passport numbers**; employee details.
- **Photos you choose** — customer/nominee photos, a profile photo, a company
  logo, and scanned vouchers/stock slips. These are selected through the system
  photo picker and sent to the server.
- **Device information** — a random identifier generated once per installation,
  plus a device name in the form "Samsung SM-A546E - Android 14". The identifier
  is used to enforce the subscription's per-user device limit and to show your
  devices under "My Devices". It is not used for advertising.
- **Diagnostic/technical data** — «CONFIRM whether the server or any proxy logs
  IP addresses, request metadata or error reports».

The app does **not** collect your location, contacts, SMS or call logs, browsing
history, health data, or the advertising ID, and it contains **no advertising or
third-party analytics SDKs**.

## 3. How the information is used

- To provide the service — store and display the business records you enter.
- To authenticate you and keep you signed in (a session token; "remember me"
  controls whether it survives an app restart).
- To apply the subscription's device limit and permissions.
- To send operational messages such as registration OTPs and, where the business
  has enabled them, customer SMS notifications.
- To support you when you ask for help. «CONFIRM»

## 4. How information is shared

The app itself shares your information with no third parties for advertising or
analytics. It sends your data only to the CashBookBD server at
`my.cashbookbd.com`.

- **Service providers** — «CONFIRM hosting provider, SMS gateway used for OTP /
  customer SMS, email provider, and any backups».
- **Legal** — «CONFIRM whether you disclose data on lawful requests».

We do not sell personal information.

## 5. Security

- All traffic between the app and the server uses **HTTPS/TLS**. The app permits
  cleartext HTTP only to local development hosts, never to production.
- The signed-in session token is stored on the device in an AES-256 encrypted
  preferences file backed by the **Android Keystore**, not in plain text.
- The session token is excluded from Android cloud backup and device transfer,
  so it does not move to a new device — you sign in again.
- «CONFIRM server-side measures: encryption at rest, access control, audit
  logging, staff access, breach-notification process».

## 6. Retention

«CONFIRM how long the server keeps each category: account data, business
records, uploaded photos, device records, SMS logs, and what happens to data
when a business cancels its subscription.»

## 7. Your choices and rights

- You can sign out at any time; a session token stored without "remember me" is
  discarded at the next app start.
- You can remove a photo or correct records from within the relevant screen.
- **Deletion:** to ask that an account and its data be deleted, contact
  «CONFIRM support email». «CONFIRM the process, expected timeframe, and any
  data that must be retained for legal or accounting reasons.»
  ⚠️ The app does **not yet** offer an in-app "delete account" button, and there
  is no public web deletion-request page — both are required by Google Play for
  apps that allow account creation. This policy must describe the method you
  actually provide once it exists.
- Depending on where you live you may have further rights (access, correction,
  portability, objection). «CONFIRM how these requests are handled».

## 8. Children

CashBookBD is a business tool intended for people aged 18 and over. It is not
directed at children and we do not knowingly collect their information.

## 9. Changes

If this policy changes, we will update the effective date above and, where the
change is significant, tell account administrators in the app or by email.
«CONFIRM»

## 10. Contact

«CONFIRM — support email, postal address, and a data-protection contact.»

---

## Facts to confirm before publishing (checklist)

1. Legal entity name, address, and the exact support/contact email and inbox.
2. The URL this policy will live at (ideally `https://my.cashbookbd.com/privacy`,
   loading without login).
3. Retention periods for each category of data.
4. Hosting provider and region; where data is stored.
5. Every third-party processor: SMS gateway (OTP + customer SMS), email, backups,
   error monitoring, CDN.
6. Whether server/proxy logs contain IP addresses or request metadata.
7. Server-side security measures (encryption at rest, staff access, breach process).
8. The account/data deletion route (in-app + web page) and its timeframe.
9. The processing-role/DPA wording for the customer data you process on behalf of
   your business customers.
10. Whether any international data transfers occur and how they are safeguarded.
