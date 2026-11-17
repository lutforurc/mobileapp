# CashBookBD Android — End-to-End Project Review

| | |
|---|---|
| **Repository** | `mobileapp` (Android, Kotlin/Compose) |
| **Branch / revision** | `Lutfor-Rahman` @ `2d345d7` |
| **Review date** | 2026-11-14 |
| **Working tree** | Clean at review time |
| **Scope** | Build config, dependencies, manifest/security, networking/session, navigation/UI, data layer, tests, release tooling |
| **Mode** | Read-only analysis; no application code changed |

**Evidence base.** Source files were read directly. Build artifacts on disk were used as supporting evidence: 48 unit tests passed on 2026-11-14, and lint reported 0 errors / 55 warnings. No Gradle build, test run, lint run, or device run was performed for this review.

---

## 1. Executive summary

The app is large (572 Kotlin/XML source files, 74 repositories, ~210 `AuthenticatedShell` call sites) and generally well-structured. Token storage, permission gating, dispatcher discipline, theming, and the newer `WebApi` error mapping are solid.

The highest-risk issues are operational rather than architectural:

1. Debug builds point at the live production tenant.
2. A failed boot-time settings fetch silently locks the user out with no retry.
3. The release signing key is tracked in git (an intentional backup choice) and its password sits in an adjacent plaintext file.
4. Debug HTTP logging captures bearer tokens and login passwords.
5. Release signing silently degrades to an unsigned artifact when the keystore is absent.
6. There is no CI/CD, no gated lint/test step, and no Play deployment automation.

---

## 2. Priority summary

| # | Finding | Priority |
|---|---|---|
| 1 | Debug builds authenticate against production | P1 |
| 2 | Boot-time settings failure becomes a permanent lockout | P1 |
| 3 | Release signing silently produces an unsigned build | P1 |
| 4 | Release keystore committed to git; password in working tree | P1 |
| 5 | Debug HTTP logging records tokens and passwords | P1 |
| 6 | No CI/CD, gated checks, or Play deployment automation | P1 |
| 7 | `postEnvelope` treats 403/500 as success | P2 |
| 8 | Logout does not revoke the server token | P2 |
| 9 | Subscription-block detection runs on pre-auth calls | P2 |
| 10 | Global subscription flag is unbounded and non-expiring | P2 |
| 11 | Build config fields declared three times | P2 |
| 12 | Kotlin/Compose version skew; implicit built-in Kotlin | P2 |
| 13 | No localization; 367 hardcoded UI strings | P2 |
| 14 | Report tables render every row eagerly; reports unbounded | P2 |
| 15 | Global drawer data re-fetched on every screen | P2 |
| 16 | Forms lose unsaved input on Back | P2 |
| 17 | Test coverage thin; seams untestable | P2 |
| 18 | Cancellation/timeouts mapped to user-facing errors | P2 |
| 19 | Session expiry implemented ~250 times | P2 |
| 20 | Cleartext-dev network config ships in release | P2 |
| 21 | Tenant branding only half implemented | P2 |
| 22 | `ServiceLocator` double-checked locking misses `@Volatile` | P2 |
| 23 | Play Store readiness blocked (no reviewer account/instance) | P2 |
| 24 | Token lifecycle hardening gaps | P3 |
| 25 | No token expiry/refresh | P3 |
| 26 | Device ID and dashboard cache included in backup | P3 |
| 27 | Login 403/429 mapping incomplete | P3 |
| 28 | `VhostDns` is a latent redirect switch | P3 |
| 29 | No certificate pinning / call timeout | P3 |
| 30 | SDK level / dependency drift / Gradle performance / build hygiene / tracked clutter | P3 |
| 31 | No local database or migrations to review | Informational |

---

## 3. P1 — High priority

### 3.1 Debug builds authenticate against production

- **Module:** `app/build.gradle.kts`
- **Evidence:** The active tenant URL is `baseUrl = "https://my.cashbookbd.com/api/"` (`:46`). The `debug` block sets `BASE_URL` to that same value (`:163`) and `LOCAL_HOST_IP = ""` (`:164`), despite the comment above it describing `10.0.2.2` / `cashbook_api.test` (`:152-162`). `NetworkModule` consumes `BuildConfig.BASE_URL` directly (`NetworkModule.kt:26-28`, `:37`).
- **Impact:** Every debug run — including the dev-login prefill at `build.gradle.kts:169-176` — authenticates against a live branch holding real business data. Any write or destructive test from a debug build mutates real books.
- **Recommendation:** Point `debug` at a staging/local host and move cleartext config to `src/debug/res`. If production is genuinely required, make that explicit and keep it separate from the dev-credentials path. Only allow `LOCAL_HOST_IP` alongside a `*.test` URL.

### 3.2 Failed boot-time settings refresh becomes a permanent lockout

- **Modules:** `navigation/AppNavigation.kt`, `data/repository/SessionRepository.kt`, `ui/common/PermissionGate.kt`
- **Evidence:** Boot calls `sessionRepository.refresh(); booting = false` without inspecting the returned `Resource` (`AppNavigation.kt:623-627`). On failure, `SessionRepository` calls `setLoading(false)` and leaves permissions empty (`SessionRepository.kt:25-37`). `PermissionGate` renders `NoAccessScreen` whenever permissions are empty and loading is false; there is no retry/error branch (`PermissionGate.kt:44-52`). Login has the same pattern (`LoginViewModel.kt:56-65`), with a comment explicitly accepting "no permissions".
- **Impact:** One failed `get-settings` call at cold start (offline blip or 5xx) makes every gated destination show "No Access" for the rest of the process. The only recovery is killing the app. An expired token is also not detected here, so the user lands on HOME rather than LOGIN.
- **Recommendation:** Inspect the `Resource`. On `isUnauthorized`, clear the session and route to LOGIN; otherwise render a blocking retry state wired to `sessionRepository.refresh()` and do the same after login before entering the app.

### 3.3 Release signing silently produces an unsigned build

- **Module:** `app/build.gradle.kts`
- **Evidence:** `hasReleaseSigning` is false when `keystore.properties` or the keystore is absent (`:30-31`). The `release` signing config is then never created (`:110-118`), and the `release` build type assigns `signingConfig` only conditionally (`:179-181`).
- **Impact:** `assembleRelease` succeeds but emits an unsigned APK/AAB. The failure surfaces only at upload/install time, and "which key signed this build" becomes non-deterministic.
- **Recommendation:** Fail fast in release tasks when signing is absent, e.g. `error("keystore.properties missing")`, rather than silently downgrading.

### 3.4 Release keystore committed to git; password in working tree

- **Module:** repository root
- **Evidence:** `git ls-files` and `git log --all -- cashbookbd-release.keystore` confirm the keystore is tracked (commit `3dc6a0e`, "Keep the release keystore in the repo as its backup"). `.gitignore:26-31` deliberately un-ignores it. `keystore.properties` is present untracked with `storePassword` / `keyPassword` in plaintext (values intentionally not reproduced here).
- **Impact:** Anyone with repository or backup access holds the app-signing private key and can sign an update Play will accept for `com.cashbookbd.app`. This is an intentional backup choice, but it is a key-compromise model. If the repository is shared or ever public, treat this as P0 and rotate the upload key.
- **Recommendation:** Move the keystore to an encrypted vault/secret manager, rotate the key password, and purge the keystore from git history if exposure is possible. Keep `keystore.properties` in CI secrets, never on disk.

### 3.5 Debug HTTP logging records tokens and passwords

- **Module:** `data/remote/NetworkModule.kt`
- **Evidence:** Logging is `Level.BODY` in debug (`:65-70`) and is added **after** `AuthInterceptor` (`:83-90`), so it sees the final wire request including `Authorization: Bearer …`. `POST /login` bodies contain the plaintext password.
- **Impact:** Any debug APK captured via `adb logcat` or a bug report leaks a live credential. Release correctly uses `Level.NONE`, so this is debug-only.
- **Recommendation:** Use `redactHeader("Authorization")`, and either drop to `HEADERS`/`BASIC` or gate `BODY` behind an explicit opt-in build flag.

### 3.6 No CI/CD, gated checks, or Play deployment automation

- **Module:** repository-wide
- **Evidence:** `glob **/*.{yml,yaml}` returns zero files. There is no `.github/`, `.gitlab-ci.yml`, `Jenkinsfile`, fastlane, or Gradle Play Publisher configuration. `playstore/` contains only documentation and marketing images. Lint/test outputs exist but nothing wires them into `check`, and there is no `lint {}` block or baseline. On disk: 48 tests pass (`app/build/test-results/testDebugUnitTest/*.xml`, 2026-11-14) and lint is 0 errors / 55 warnings, but neither is enforced.
- **Impact:** Regressions ship silently. Release builds, mapping-file upload, and keystore handling are entirely manual.
- **Recommendation:** Add a CI workflow running `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleRelease` on pull requests, upload `mapping.txt`, and add supply/fastlane for Play uploads.

---

## 4. P2 — Medium priority

### 4.1 `postEnvelope` treats non-401 failures as success

- **Module:** `data/remote/WebApi.kt:52-69`
- **Evidence:** Only `401` short-circuits. For any other status, a JSON error body becomes `Resource.Success(obj)` (`:58-60`). A 403 permission refusal or a 500 with a JSON body on envelope-style saves is therefore reported as a successful save.
- **Impact:** The UI can tell the user a write succeeded when the server refused it.
- **Recommendation:** Return `Resource.Error` for `!response.isSuccessful && code != 422`, or explicitly whitelist accepted envelope statuses.

### 4.2 Logout does not revoke the server token

- **Module:** `data/repository/AuthRepository.kt:69-80`
- **Evidence:** `logout()` clears the local token, caches, session, branch cache, and subscription signal only. `ApiService` exposes `DELETE devices/{tokenId}` (`ApiService.kt:257-261`) but no logout or current-token revoke call.
- **Impact:** A copied token keeps working after logout, and every login consumes another plan device slot — the exact limit the login screen then works around.
- **Recommendation:** Call a server logout/revoke-current-token endpoint before clearing locally, keeping local clearing as the fallback.

### 4.3 Subscription-block detection also runs on pre-auth calls

- **Module:** `data/remote/NetworkModule.kt:89`, `data/remote/SubscriptionBlockInterceptor.kt`
- **Evidence:** The interceptor is added unconditionally. Its comment claims it only sees authenticated responses, but ordering alone does not filter `/login`, `register/*`, or `forgot-password/*`. If login ever answers `403 {error:{code:10031}}`, the global gate (`MainActivity.kt:74-76`) covers the login screen. The gate's logout button clears the signal and returns to the same login, which can re-trigger it.
- **Impact:** A lapsed-company user could be trapped on the login screen with no way in.
- **Recommendation:** Skip detection when the request has no `Authorization` header, or exclude auth paths explicitly.

### 4.4 Global subscription flag is unbounded and non-expiring

- **Module:** `data/remote/SubscriptionBlockSignal.kt:43-55`
- **Evidence:** First raise wins; the flag is cleared only on login, logout, or gate retry. Any 403 anywhere whose body contains `error.code == 10031` blanks the whole app (`MainActivity.kt:74-76`).
- **Impact:** A duplicated or reused error code on any of ~200 endpoints causes an app-wide lockout.
- **Recommendation:** Scope the signal to authenticated requests and consider a short TTL/re-check instead of a process-lifetime latch.

### 4.5 Build config fields are declared three times

- **Module:** `app/build.gradle.kts:135,145,149-150`, `:163-176`, `:189-194`
- **Evidence:** `BASE_URL`, `LOCAL_HOST_IP`, and `DEV_LOGIN_*` are set in `defaultConfig`, then again in both `debug` and `release`. The build-type values win, so editing `defaultConfig` has no effect.
- **Impact:** The next maintainer edits the wrong block; debug and release can drift silently.
- **Recommendation:** Keep one declaration per build type (or rely on `defaultConfig` where identical) and drive the tenant URL from a Gradle property.

### 4.6 Kotlin/Compose version skew and implicit built-in Kotlin

- **Modules:** `gradle/libs.versions.toml:9,48`, `app/build.gradle.kts:3-6`
- **Evidence:** The catalog pins `kotlin = "2.0.21"`, used only by the Compose compiler plugin; no Kotlin Android plugin is applied. The shipped artifact resolves `kotlin-stdlib 2.2.21` (`app/build/outputs/sdk-dependencies/release/sdkDependencies.txt:8`), indicating the compiler comes from AGP 9's built-in Kotlin. Lint flags the declared plugin as behind (`lint-results-debug.txt:66-67`).
- **Impact:** It builds today, but the Compose plugin and compiler are not version-locked. The next AGP bump can fail with a hard Compose-compiler incompatibility, and downgrading AGP removes Kotlin support entirely.
- **Recommendation:** Make the Kotlin toolchain explicit (declare a Kotlin plugin version matching the compiler, or document and pin AGP's built-in Kotlin) and align the Compose plugin version.

### 4.7 No localization; 367 hardcoded UI strings

- **Modules:** `app/src/main/res/values/strings.xml`, all UI
- **Evidence:** `strings.xml` contains exactly one string (`app_name`). `grep stringResource(` returns **0 occurrences**; `grep 'Text\(\s*"'` returns **367 occurrences across 118 files** (e.g. `TradeLedgerScreen.kt` 8, `VoucherRegisterScreen.kt` 7, `HotelTaxesScreen.kt` 10). Strings also live in non-Compose code (`AppNavigation.kt:704-710`, `AuthenticatedShell.kt:352`).
- **Impact:** The app cannot be translated for the Bangladesh market, and copy edits require a rebuild.
- **Recommendation:** Extract strings module by module, starting with shared components (`StateViews`, `MenuListRow`, `FormMessage`, `AppAlertDialog`), then screens.

### 4.8 Report tables render every row eagerly; reports are unbounded

- **Module:** `ui/reports/ReportTable.kt:266-308`
- **Evidence:** The body is `Column(Modifier.verticalScroll(...)) { data.forEachIndexed { ... } }`. Every report uses this table. Report fetches have no `per_page`/pagination (`GenericReportRepository`, `LedgerRepository`, `TradeLedgerRepository`, `TrialBalanceRepository`).
- **Impact:** A ledger with thousands of rows composes and measures all of them in one pass — jank and OOM risk on large tenants.
- **Recommendation:** Switch the body to `LazyColumn` with stable keys (cells are already index-addressed) and add server-side paging for heavy ledgers.

### 4.9 Global drawer data is re-fetched on every screen

- **Module:** `navigation/AuthenticatedShell.kt`
- **Evidence:** `AuthenticatedShell` is composed in ~210 call sites across 196 files. Each instance fires `LaunchedEffect(Unit)` for `user-todos/summary` (`:193-197`), `branch/ddl/all-branch` (`:202-207`), in-app messages (`:137`), notifications (`:128-130`), and menu preferences (`:506`).
- **Impact:** At least three redundant network round-trips per navigation across ~120 screens; the drawer fetches even when never opened.
- **Recommendation:** Hoist these into an app-scoped initializer invoked once from `MainActivity`/`AppNavigation`, and key refreshes to `SessionManager.state` rather than `Unit`.

### 4.10 Forms lose unsaved input on Back

- **Module:** all form screens
- **Evidence:** Only four screens implement `BackHandler` (`SalarySheetScreen.kt:284`, `LegacyAccountsScreen.kt:206`, `AssetScheduleScreen.kt`, `ManualAttendanceScreen.kt`). No form has an unsaved-changes guard (`grep unsaved|discard|Leave without` finds no confirm flow).
- **Impact:** System back silently discards typed invoice, customer, and voucher data.
- **Recommendation:** Add a shared `BackHandler(enabled = isDirty)` plus confirm dialog and adopt it in form screens.

### 4.11 Test coverage is thin and untestable at the seams

- **Modules:** `app/src/test`, `app/src/androidTest`, `di/ServiceLocator.kt`
- **Evidence:** 48 tests pass, but they cover only formatting and pure functions (DateFormat 6, VehicleFormat 4, MenuGroups 3, VoucherEdit 4, ReportMenuPermission 5, ParitySmoke 25) plus one template stub (`ExampleUnitTest.kt` asserts `2+2`). `androidTest` contains only the package-name stub. There are zero tests for 74 repositories, ~120 ViewModels, JSON parsing, money math, or navigation. `ServiceLocator` has ~140 singletons and no `reset()` or injectable override (`grep reset|VisibleForTesting` returns none). No `kotlinx-coroutines-test`, MockWebServer, or MockK dependency is declared.
- **Impact:** The money-handling code is effectively unguarded, and adding tests requires refactoring first.
- **Recommendation:** Add `kotlinx-coroutines-test` and MockWebServer, introduce a test override/reset in `ServiceLocator`, and prioritize `WebApi`, `GenericReportRepository`, auth/session, and amount parsing.

### 4.12 Cancellation and timeouts are mapped to user-facing errors

- **Modules:** `data/remote/WebApi.kt:103-126`, `data/repository/*.kt`
- **Evidence:** `catch (e: InterruptedIOException)` covers both timeouts and OkHttp cancellation. `catch (e: Exception)` (148 occurrences across 67 data-layer files) also catches `CancellationException`.
- **Impact:** Leaving a screen during a request can surface "the server took too long" or "something went wrong", and structured cancellation is swallowed rather than propagated.
- **Recommendation:** Rethrow `CancellationException` or check `currentCoroutineContext().isActive` before mapping, and catch only expected exception types.

### 4.13 Session expiry is implemented ~250 times instead of once

- **Modules:** repositories and 117 UI files
- **Evidence:** There are 195 `isUnauthorized = true` sites across 73 files, and each screen re-plumbs `sessionExpired`. `AppNavigation.kt` wires it explicitly only for HOME. `LaunchedEffect(state.sessionExpired|message|error)` appears in roughly 117 files.
- **Impact:** A screen that forgets the plumbing shows a toast instead of forcing re-login; high maintenance cost.
- **Recommendation:** Add a central `SessionExpiredSignal` (same pattern as `SubscriptionBlockSignal`) emitted from an OkHttp interceptor and handle it once at `MainActivity`/`AppNavigation`.

### 4.14 Cleartext-dev network config ships in the release APK

- **Modules:** `app/src/main/res/xml/network_security_config.xml:14-21`, `AndroidManifest.xml:14`
- **Evidence:** Cleartext is permitted for `10.0.2.2`, `10.0.3.2`, `localhost`, and `cashbook_api.test` in `src/main`, so the release binary carries the exemption.
- **Impact:** A narrow but unnecessary cleartext allowance in production; Play/security review may flag it.
- **Recommendation:** Move the file to `src/debug/res/xml/` and keep the release default (cleartext denied).

### 4.15 Tenant branding is only half implemented

- **Modules:** `app/src/tenants/*`, `app/src/main/res/values/strings.xml:2`
- **Evidence:** Tenant overlays contain only `drawable/` and `mipmap*/`; `app_name` is the shared `CashBookBD` string. The build script comment claims per-tenant branding (`app/build.gradle.kts:49-64`), but `values/` is never tenant-scoped.
- **Impact:** A tenant-specific build shows the wrong launcher/Play label.
- **Recommendation:** Add `src/tenants/<key>/res/values/strings.xml` (and theme/colour overrides where needed) and verify the overlay in `build/tenantRes/<key>`.

### 4.16 `ServiceLocator` double-checked locking misses `@Volatile`

- **Module:** `di/ServiceLocator.kt`
- **Evidence:** Fields at `:176-186` (`productTrackingRepository`, `tradeLedgerRepository`, `hotelRepository`, `menuPreferencesRepository`, …), `:257` (`userTodoRepository`), and `:268` (`deviceRepository`) are used inside `x ?: synchronized(this) { … }` without `@Volatile`.
- **Impact:** Unsafe publication under the JMM. Low practical risk on ART but incorrect and inconsistent with the other ~130 fields.
- **Recommendation:** Annotate every lazily published field `@Volatile`, or migrate to Hilt as the class KDoc already plans.

### 4.17 Play Store readiness is blocked

- **Module:** `playstore/`
- **Evidence:** `playstore/reviewer-access.md:3-27` states the only working login is a live production branch holding real customer data and explicitly says "do not submit an account yet". No isolated review instance exists, and no privacy-policy upload or deployment config is present.
- **Impact:** The app cannot pass Play review in its current state.
- **Recommendation:** Stand up the isolated review instance with synthetic data and a dedicated reviewer account before the next submission.

---

## 5. P3 — Low priority, hygiene, and hardening

| Area | Finding and evidence | Recommendation |
|---|---|---|
| Token lifecycle | `TokenManager` persists the token even when "Remember me" is off; it is dropped only at the next cold start (`TokenManager.kt:35-57`). Registration forces `rememberMe = true` (`RegistrationRepository.kt:64`). | Keep non-remembered tokens in memory only; respect the flag during registration. |
| No refresh/expiry | Validity is "a token exists" (`TokenManager.kt:44`); no TTL or refresh (`LoginResponse` carries only `data.token`). | Add 401-driven logout and, if supported, refresh. |
| Backup scope | Device ID (`DeviceIdManager`) and `DashboardCache` are plain prefs and are backed up; only `cashbook_secure_prefs` is excluded (`backup_rules.xml`, `data_extraction_rules.xml`). | Exclude both, or regenerate the device ID on restore. |
| Login 403 mapping | Any non-10010 403 becomes "Server error (403)" (`AuthRepository.kt:97-103`); no 429 handling anywhere. | Map `10031`/429 to specific messages. |
| `VhostDns` | Rewrites DNS to `LOCAL_HOST_IP` while keeping the production hostname (`NetworkModule.kt:145-156`); currently inert, but a misconfiguration silently redirects credentials/TLS. | Fail the build if `LOCAL_HOST_IP` is set for a real tenant URL. |
| No pinning / call timeout | No `CertificatePinner`; no `callTimeout` (`NetworkModule.kt:74-81`). | Add `callTimeout`; consider pinning the API domain. |
| SDK level | `targetSdk = 36` with `compileSdk 37` (`build.gradle.kts:98-100`, `:128`); lint flags `OldTargetApi` (`lint-results-debug.txt:1-3`). | Bump when API 37 behaviour is tested. |
| Dependency drift | `material-icons-core 1.7.8` vs Compose 1.12.0 (`libs.versions.toml:36`); `security-crypto 1.1.0` is deprecated upstream; `navigation 2.9.8` is behind 2.10.2; AGP/core patches are behind. No dependency locking or verification metadata. | Track the icons migration, plan off `security-crypto`, and add verification metadata. |
| Gradle performance | `org.gradle.jvmargs=-Xmx2048m`; parallel, caching, and configuration cache disabled (`gradle.properties:9-13`). | Raise heap; enable parallel, build cache, and configuration cache. |
| Build hygiene | `versionCode`/`versionName` are hand-maintained (`build.gradle.kts:129-130`); no `applicationIdSuffix` on debug; `namespace` is still `com.example.*` (`:97`). | Automate version bumps; suffix debug; consider moving the namespace. |
| Tracked clutter | `package-lock.json` (empty), `app-screens.pdf`, `.kotlin/errors/errors-*.log`, and `*.idsig` files are tracked; `.gitignore` lacks `*.log`/`*.pdf`. Generated `gradle/gradle-daemon-jvm.properties` is tracked. | Remove and ignore build/log artifacts. |
| Seeded review password | `tools/review-env/_review_seed.php` contains a hardcoded password literal at line 38 and echoes it at line 138 (value intentionally not reproduced here). | Read the password from an environment variable; never commit or print it. |
| Dead affordances | "Coming soon" branches exist (`AdminHomeScreen.kt:128`, `InvoiceHomeScreen.kt:129`), but no registry item sets `supported = false`. | Delete the branch or add the intended entries. |
| Permission scope | Wildcard `"*"` opens every client gate (`session/Permissions.kt:5-8`); parent menus `products`/`admin` are supersets of the web gate (`MenuPermissions.kt:86-125`). | Confirm these are intentional; server still enforces data access. |
| Notification taps | Only two routes map; every other notification tap is inert (`AuthenticatedShell.kt:95-98`, `:300-306`). | Add a notification-list fallback or disable taps. |
| Docs drift | `MOBILE_UI_SYNC_TRACKER.md` records baseline `f2eff00`, but HEAD is `2d345d7` (three commits later). The API repo was not re-read. | Update the checkpoint on the next pass. |
| No local database | No Room, SQLite, or SQLDelight anywhere; persistence is `EncryptedSharedPreferences` plus plain `SharedPreferences`. | No migrations exist to review. If offline caching is added, introduce schema versioning then. |

---

## 6. Verified clean (do not regress)

- Token at rest uses Keystore-backed `EncryptedSharedPreferences` (`TokenManager.kt:15-27`) and is correctly excluded from backup and device transfer.
- No hardcoded credentials or API keys in `app/src`; dev login is sourced from untracked `local.properties` and forced empty in release.
- `WebApi` maps 401/403/422/`success:false`/IO/timeout consistently and flags ambiguous writes; `isAmbiguous` is documented for non-idempotent safety (`Resource.kt:19-25`).
- `SubscriptionBlockInterceptor` reads via `peekBody` and never consumes or alters the response.
- R8 keeps Gson field names and retains annotations; `retryOnConnectionFailure(false)` is deliberate for non-idempotent payments.
- Repository calls consistently run on `Dispatchers.IO`; there is no `GlobalScope` or `runBlocking` in app code; ViewModels are factory-based; state uses `collectAsStateWithLifecycle` (zero bare `collectAsState()`).
- Theme, colour, and shape tokens are centralized (no hardcoded colours outside `ui/theme/`), RTL is structurally correct, and permission gating is applied at the destination for ~180 routes.
- 48 unit tests pass; lint has 0 errors.

---

## 7. Areas not verified

- **No Gradle build, test run, lint run, or device run was performed.** On-disk evidence is from 2026-11-14: 48 tests passing and lint 0 errors / 55 warnings. The release APK/AAB are dated 2026-10-31, while `app/build.gradle.kts` was modified 2026-11-01, so those artifacts do not prove the current build script compiles. `MOBILE_UI_SYNC_TRACKER.md` claims `compileDebugKotlin`, `testDebugUnitTest`, and `lintDebug` ran on 2026-11-14, but that was not reproduced.
- **Server-side behaviour:** whether `/login` can return `error.code 10031`; whether a logout/revoke-current-token endpoint exists; whether `login/release-device` re-verifies credentials and is rate-limited; token TTL; and the exact permissions payload shape.
- **Git history scope:** whether the tracked keystore or `keystore.properties` was ever pushed to a remote beyond this checkout.
- **Runtime behaviour of query-arg routes:** static evidence says the `defaultValue = ""` argument makes bare routes resolve (`AppNavigation.kt:1102-1108`), but the app was not executed.
- **R8 release runtime:** minified Gson/Compose behaviour cannot be validated statically, and no release smoke test exists.
- **Exhaustive registry cross-check:** every menu key was not enumerated against every registered `composable` (~400 route strings × ~180 registrations); sampled families looked consistent.
- **Ambiguous-write handling:** whether every screen honours `Resource.Error.isAmbiguous` before offering a retry was not enumerated.
- **Performance measurements:** the table and shell findings are structural, not profiled.

---

## 8. Suggested next actions

1. Repoint `debug` away from production and gate `LOCAL_HOST_IP` to test URLs.
2. Add a boot/login retry path for `sessionRepository.refresh()` and route expired sessions to LOGIN.
3. Make release signing fail loudly; move the keystore and passwords out of the repository.
4. Redact authorization headers and login bodies in debug logging.
5. Add CI for lint, unit tests, and release assembly, then wire Play upload/mapping upload.
6. Fix `postEnvelope` status handling and add server-side token revocation on logout.
7. Extract shared strings and convert report tables to `LazyColumn`.
8. Add repository/ViewModel test infrastructure and back it with MockWebServer and `kotlinx-coroutines-test`.
