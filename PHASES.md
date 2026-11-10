# Ulap: Mood Diary · Project Phases

> 📚 **Ulap docs:** [README](README.md) · **Phases** · [Technical plan](docs/technical-plan.md) · [API reference](docs/api-spec.md) · [Design spec](docs/design-spec.md) · [Repo reference](docs/ulap-reference.md)
>
> **This document owns:** build order and progress. Each phase links to the document that defines its tasks.

Track progress by ticking boxes: change `- [ ]` to `- [x]` when a task is **done** (it meets the Definition of Done below).

**Legend:** `- [x]` complete · `- [ ]` not started or in progress · 🔒 needs two-reviewer approval (auth, sync, encryption, safety)

Last updated: November 7, 2026

---

## Progress overview

| Phase | Name | Status | Done |
|---|---|---|---|
| 0 | Research and planning | ✅ Complete | 19 / 19 |
| 1 | Project setup | ✅ Complete | 18 / 18 |
| 2 | Offline check-in | 🟡 In progress | 23 / 25 |
| 3 | Logs | ✅ Complete | 13 / 13 |
| 4 | Reports | ✅ Complete | 17 / 17 |
| 5 | Languages and onboarding | 🟡 In progress | 12 / 15 |
| 6 | Server and accounts | ⬜ Not started | 0 / 26 |
| 7 | Sync | ⬜ Not started | 0 / 25 |
| 8 | Privacy, safety and polish | ⬜ Not started | 0 / 23 |
| 9 | Testing and beta | ⬜ Not started | 0 / 15 |
| 10 | Release v1.0 | ⬜ Not started | 0 / 15 |
| 11 | After v1 (backlog) | ⬜ Planned | 0 / 13 |

Update the **Done** column and **Status** (⬜ Not started · 🟡 In progress · ✅ Complete) whenever you tick boxes.

---

## Definition of Done

A task is complete only when:
- [ ] The code is merged into `main` through a reviewed pull request. While Ulap has one maintainer, the owner's own read of the diff plus green CI counts as the review for tasks without 🔒. 🔒 tasks still need another person ([README §12](README.md#12-contributing-guidelines))
- [ ] Tests are written and pass in CI (`./gradlew build`)
- [ ] User-facing text exists in **English, Filipino and Bisaya** (`strings.xml`)
- [ ] It follows the design spec (tokens, 48dp touch targets, TalkBack labels)
- [ ] It follows the voice guide (validate → offer, no judgment words)
- [ ] No journal notes, tokens or passwords appear in any log
- [ ] Docs are updated if behavior or the API changed, following [README §17](README.md#17-documentation)

**Phase 1 exception:** until the CI tasks are ticked, a setup task is done when it works locally and is committed to `main`. Skip rules that don't apply to it (e.g. strings, voice guide).

---

## Phase 0: Research and planning ✅

**Goal:** know what to build, why, and how.

### Research
- [x] Research the link between feelings and stress (2021–2026 studies)
- [x] Define 25 moods with default energy and stress levels
- [x] Group moods into 4 zones (Red, Blue, Yellow, Green) ranked by stress
- [x] Research the benefits of mood logging and journaling
- [x] Research gamification risks (streaks, guilt) and mascot approach
- [x] Define the sympathetic response approach (validate → offer → check again)

### Product decisions
- [x] Platform: Android first (Kotlin), iPhone later
- [x] Backend: Ktor + PostgreSQL, Kotlin Multiplatform shared module
- [x] Data: offline-first with two-way sync
- [x] Users: public app, many users. Guest mode first, then Google or email sign-in.
- [x] One mood per check-in
- [x] Languages: English, Filipino, Bisaya (chosen by the user)
- [x] Mascot: Ulap the cloud (name check still pending, see Phase 10)

### Design and documentation
- [x] Desktop mockups (Log mood, Reports)
- [x] Mobile app mockup with the full sympathetic flow
- [x] [Technical plan](docs/technical-plan.md)
- [x] [Design spec](docs/design-spec.md)
- [x] [API reference](docs/api-spec.md) and [README](README.md)
- [x] [Repo reference](docs/ulap-reference.md): reuse plan from macro-tracker (decisions D1–D14, accepted in Phase 1)

---

## Phase 1: Project setup ✅ (Nov 2026)

**Goal:** an empty project that builds, runs and tests locally and in CI.
Spec: [README §5–§8](README.md#5-folder-structure) · [Technical plan §4](docs/technical-plan.md#4-project-structure) · [Repo reference §4](docs/ulap-reference.md#4-android-build-setup)

### Repository
- [x] Create the Git repository with `main` as the protected branch
- [x] Add `README.md`, `PHASES.md`, `docs/`, `.editorconfig`, `.gitignore`
- [x] Add the Claude Code setup: `CLAUDE.md`, agents and skills in `.claude/`, `.claude/settings.json` ([Repo reference §11](docs/ulap-reference.md#11-claude-code-setup-for-ulap))
- [x] Add the PR template and a short `CONTRIBUTING.md` that links to [README §12](README.md#12-contributing-guidelines)
- [x] Choose a license (or keep "All rights reserved" for now)
- [x] Decide the proposed changes D1–D14 in [Repo reference §13](docs/ulap-reference.md#13-decision-log), then update the listed docs

### Gradle and modules
- [x] Create the Kotlin Multiplatform project: `shared`, `androidApp`, `server`, the `core:*` and `feature:*` modules, and the `ulap.*` convention plugins in `build-logic/`. Build types `debug`, `staging`, `release`.
- [x] Set up the version catalog (`gradle/libs.versions.toml`) with the latest stable versions
- [x] Set up Spotless (ktlint + compose-rules), detekt and Android Lint, plus the `.githooks/pre-commit` hook
- [x] `androidApp` runs an empty Compose screen on the emulator
- [x] `server` runs with `GET /v1/health` → `{"status":"ok"}`

### Local infrastructure
- [x] `infra/compose.yaml` + `infra/compose.dev.yaml` with PostgreSQL (port 5434), Mailpit (8026 / 1026) and a one-off `migrate` service
- [x] `.env.example` with every server variable
- [x] Flyway set up, with an empty `V1__init.sql` migration that runs on startup in dev and through the `migrate` service in staging and production
- [x] The build reads `ulap.apiUrl.<buildType>` and `ulap.googleWebClientId` from `~/.gradle/gradle.properties`

### CI
- [x] GitHub Actions (`ci-android.yml`, `ci-server.yml`): Spotless, detekt, Android Lint and unit tests on every pull request
- [x] Server tests run with Testcontainers in CI
- [x] Build status badge in the README

**Phase 1 complete when:** a new developer can clone the repo, follow the README, and see the app and the server running in under 30 minutes.

---

## Phase 2: Offline check-in

**Goal:** a person can log a mood and get Ulap's response with no internet and no account.
Spec: [Technical plan §5–§6](docs/technical-plan.md#5-shared-domain-model) · [Design spec §6–§8](docs/design-spec.md#6-components)

### Shared module
- [x] `Zone`, `Mood`, `Trigger`, `SupportType` models
- [x] `MoodCatalog` with all 25 moods, zones, and default energy and stress
- [x] `StressLabels` and `EnergyLabels` (very low … very high)
- [x] `ResponseRules` (zone actions, Hopeless, stress ≥ 8 → one button, trigger lines)
- [x] `CheckInValidator` (ranges, lengths, good moment only for Yellow and Green)
- [x] `Ids.newId()`: UUIDv7 (RFC 9562), lowercase, time-ordered, with tests ([Technical plan §3](docs/technical-plan.md#recommended-libraries))
- [x] Unit tests for the catalog, rules and validator

### Local data
- [x] Room `CheckInEntity` with every column from the technical plan
- [x] `CheckInDao` (insert, update, soft delete, observe by day)
- [x] `CheckInRepository` (save sets `PENDING` and `updatedAt`)
- [ ] Database encryption: SQLCipher on Room 2.8, with a random passphrase wrapped by Tink and the Android Keystore, kept in `noBackupFilesDir`. Recovers from a lost key. Includes an instrumented test that the file can't be opened without the passphrase ([Technical plan §6](docs/technical-plan.md#encryption-of-the-phone-database)) 🔒
- [x] Turn off Android cloud backup of the database
- [x] DataStore for settings (language, mascot on/off, device ID)

### Theme and components
- [x] Theme: `Color.kt`, `Type.kt`, `Shape.kt` from the design spec
- [x] Bundle the fonts (Bricolage Grotesque, Figtree) with their license files
- [x] `Ulap` composable with 6 expressions plus the float animation (respects reduced motion)
- [x] Components: MoodTile, ZoneSection, SpeechBubble, Slider row, TriggerChip, buttons

### Screens
- [x] Pick mood: Ulap greeting plus 4 zone sections in a 3-column grid
- [x] Details: mood header, energy and stress sliders with defaults, Reset to default, triggers, journal with zone prompt, Save
- [x] Response: zone tint background, Ulap, message, trigger line, actions
- [x] Support cards: breathing circle (4-2-6), small helps list, saved to good moments
- [x] "How stressed do you feel now?" check-in (Red and Blue only)
- [x] After screen: before → now, with the right message (lower, same, higher)
- [ ] Hopeless: quiet Ulap plus the NCMH 1553 card (dialer intent) 🔒
- [x] Bottom tab bar (Check in, Logs, Reports, Account)

**Phase 2 complete when:** in airplane mode, you can log all 25 moods and each shows the right response, and the data survives an app restart.

---

## Phase 3: Logs ✅ (Nov 2026)

**Goal:** people can review, search, edit and delete their check-ins.
Spec: [Design spec §7.6](docs/design-spec.md#76-logs) · [Design spec §6.13](docs/design-spec.md#613-log-row)

- [x] Logs list grouped by local day (Today, Yesterday, dates)
- [x] Day header with check-in count and average stress
- [x] Log row: zone stripe, mood, time, stress, trigger, 2-line note
- [x] Search across mood name, trigger and note (decrypted in memory only)
- [x] Zone filter chips (All, Red, Blue, Yellow, Green)
- [x] Overflow menu: Edit, Delete
- [x] Edit opens Details filled in, and saving updates `updatedAt`
- [x] Delete confirmation ("This can't be undone"), then soft delete
- [x] Empty state: Ulap plus "Your check-ins will show up here."
- [x] Empty search result state
- [x] Paging or lazy loading for long histories (1,000+ entries)
- [x] TalkBack labels for rows and the menu
- [x] Tests: DAO day grouping across midnight and time zones

**Phase 3 complete when:** 1,000 sample check-ins scroll smoothly, and search and filter return correct results.

---

## Phase 4: Reports ✅ (Nov 2026)

**Goal:** people see patterns in their feelings without feeling judged.
Spec: [Technical plan §10](docs/technical-plan.md#10-reports-calculated-on-the-phone) · [Design spec §7.7](docs/design-spec.md#77-reports)

### Calculations (on the phone)
- [x] Period selection: Daily, Weekly (7 days), Monthly (calendar month)
- [x] Summary tiles: check-ins, average stress, most common mood, top trigger, change vs. previous period
- [x] Stress trend data (per check-in for Daily, per-day average for Weekly and Monthly)
- [x] Zone mix counts and percentages
- [x] Mood frequency (top 5)
- [x] Top triggers with count and average stress
- [x] Time-of-day averages (Morning, Afternoon, Evening, Night)
- [x] What helped most (average of `stress − stressAfter` by support type)
- [x] "Ulap noticed" insight rules (wording without judgment, in all 3 languages)
- [x] Unit tests for every calculation

### UI
- [x] Segmented control and a period label with previous/next arrows
- [x] Stat tiles (2×2 grid)
- [x] Stress trend bar chart with gridlines, band colors and a legend, built with Vico (D9; [Design spec §6.15](docs/design-spec.md#615-charts))
- [x] Horizontal bar lists (moods, triggers, what helped)
- [x] Ulap insight bubble
- [x] Not-enough-data state (fewer than 3 check-ins)
- [x] Chart summaries for TalkBack

**Phase 4 complete when:** the reports match numbers calculated by hand for a test data set.

---

## Phase 5: Languages and onboarding

**Goal:** first-time users feel welcome in their own language.
Spec: [Technical plan §11 and Appendix A–B](docs/technical-plan.md#11-languages-english-filipino-bisaya) · [Design spec §7.1 and §10.5](docs/design-spec.md#71-onboarding-3-steps)

### Languages
- [x] Every English string in `values/strings.xml` (no hard-coded text)
- [x] Filipino strings in `values-fil/strings.xml`
- [x] Bisaya strings in `values-b+ceb/strings.xml`
- [ ] Native-speaker review of the Filipino strings
- [ ] Native-speaker review of the Bisaya strings (owner)
- [x] Fix duplicate mood words (e.g. Frustrated and Disappointed both "Nasagmuyo")
- [x] Per-app language switch (`AppCompatDelegate.setApplicationLocales`)
- [ ] Layouts tested with text 40% longer and at 200% font size

### Onboarding
- [x] Step 1: choose a language (English, Filipino, Bisaya)
- [x] Step 2: meet Ulap
- [x] Step 3: privacy note, disclaimer, consent → "Start my first check-in"
- [x] Privacy notice screen (also linked from Account)
- [x] Onboarding shown only once (DataStore flag)
- [x] Account: change language later
- [x] Account: mascot on/off switch (messages still show when off)

**Phase 5 complete when:** a new user can complete onboarding and a check-in fully in Bisaya without seeing any English.

---

## Phase 6: Server and accounts

**Goal:** people can create an account and sign in safely.
Spec: [API reference §3–§7 and §10–§12](docs/api-spec.md#3-authentication-model) · [Technical plan §7](docs/technical-plan.md#7-server-database-postgresql)

### Database
- [ ] Flyway `V1__init.sql`: `users`, `auth_identities`, `refresh_tokens`, `email_tokens`, `check_ins`, `check_in_seq`, `sync_purge`
- [ ] Exposed table definitions plus a transaction helper
- [ ] Encryption at rest: AES-256-GCM for `note` and `triggerOther`, with `key_id` 🔒

### Server foundations
- [ ] `AppConfig` reads every env var and fails fast if one is missing
- [ ] Plugins: ContentNegotiation, CallId (`X-Request-Id`), StatusPages, RateLimit
- [ ] The standard error format and every error code from the API spec
- [ ] `logback.xml` with no request bodies, notes or tokens 🔒
- [ ] `GET /v1/ready`: `SELECT 1` with a 2-second timeout → 200, or 503 `UNAVAILABLE` ([API reference §9.2](docs/api-spec.md#92-get-ready))
- [ ] `GET /v1/meta` (minimum app version, hotline)
- [ ] `426 APP_UPDATE_REQUIRED` check from `X-App-Version`

### Auth (server) 🔒
- [ ] JWT access tokens (15 minutes) with `sub` and `did`
- [ ] Refresh tokens (60 days), stored hashed, rotating, with reuse detection
- [ ] `POST /auth/google` (verify signature, `aud`, `iss`, `exp`)
- [ ] `POST /auth/email/register` with password rules plus Argon2id
- [ ] `POST /auth/email/login`
- [ ] Email verification (`/verify`, `/resend-verification`) with a 24-hour single-use token
- [ ] Password reset (`/forgot`, `/reset`) with a 30-minute single-use token, revoking all sessions
- [ ] `POST /auth/refresh`, `/logout`, `/logout-all`
- [ ] `POST /auth/link/google` and `/auth/link/email`
- [ ] Rate limits on every auth route

### User routes
- [ ] `GET /me`, `PATCH /me`
- [ ] `DELETE /me` (hard delete of everything, in one transaction) 🔒

### Auth (Android) 🔒
- [ ] Credential Manager "Continue with Google"
- [ ] Email sign-up, sign-in and forgot-password forms
- [ ] Secure token storage (Keystore-backed) plus automatic refresh with a mutex
- [ ] Sign-in bottom sheet and the Account screen's "Back up and sync" card

**Phase 6 complete when:** a user can sign up with Google and with email, verify the email, sign out and in again, and delete the account. All auth tests pass.

---

## Phase 7: Sync

**Goal:** check-ins move safely between the phone and the server, online or offline.
Spec: [API reference §8](docs/api-spec.md#8-sync-endpoints-) · [Technical plan §8](docs/technical-plan.md#8-sync-design)

### Server 🔒
- [ ] `POST /sync/push` (max 200 changes, merge by `updatedAt`, APPLIED/STALE/rejected)
- [ ] Clock guard (future `updatedAt` → server time)
- [ ] Tombstones: clear `note` and `triggerOther` when deleted
- [ ] `GET /sync/pull` (since, limit, in order, `hasMore`)
- [ ] `resetRequired` on pull: cursor below `purged_through_seq`, or above the latest `serverSeq` ([API reference §8.3](docs/api-spec.md#83-get-syncpull))
- [ ] `GET /sync/status`
- [ ] Block sync until the email is verified (`403 EMAIL_NOT_VERIFIED`)
- [ ] Scheduled job to purge tombstones older than 90 days. It raises `sync_purge.purged_through_seq` in the same transaction.

### Android 🔒
- [ ] `SyncApi` with the shared DTOs
- [ ] `SyncEngine`: push PENDING in batches → pull until `hasMore = false` → save `lastPullSeq`
- [ ] Merge pulled changes (local PENDING and newer → keep local)
- [ ] Full resync on `resetRequired`: `pull_seen` table, `sync_resetting` flag, remove `SYNCED` rows the server didn't send ([Technical plan §8](docs/technical-plan.md#full-resync-resetrequired))
- [ ] Handle rejected changes and internal errors: `sync_error = reason` right away, `sync_attempts`, then `FAILED` after 10 × `500 INTERNAL`. No endless retry ([Technical plan §8](docs/technical-plan.md#when-a-change-cant-upload)).
- [ ] `SyncTriggers`: watches pending rows (repositories never schedule sync), syncs 5 seconds after a write and on app open, keeps the 6-hour job while signed in, cancels it all on sign-out ([Technical plan §8](docs/technical-plan.md#when-sync-runs))
- [ ] `SyncWorker`: one unique job, network required, exponential backoff starting at 30 seconds
- [ ] Account: "Sync now" plus last-synced time
- [ ] Account: "%d check-ins couldn't back up yet. They're still saved on this phone." + Try again ([Design spec §7.8](docs/design-spec.md#78-account))
- [ ] Guest → new account: upload everything
- [ ] Guest → existing account: the "Add this phone's check-ins?" prompt
- [ ] Offline banner text: "Saved on this phone. Will back up when you're online."

### Tests
- [ ] Push: insert, newer, older (STALE), delete, edit after delete, another user's ID (FORBIDDEN), 201 → 413
- [ ] Pull: paging from `since=0`, tombstones, order
- [ ] Reset: an old cursor → `resetRequired`. The phone's full resync removes purged rows, keeps `PENDING` rows, and carries on after the app is killed.
- [ ] Two devices edit the same entry offline → both end with the newest version
- [ ] Network drops halfway through a sync → retry completes without duplicates

**Phase 7 complete when:** two phones signed into the same account show the same check-ins after editing offline and reconnecting.

---

## Phase 8: Privacy, safety and polish

**Goal:** the app is safe, accessible and pleasant to use.
Spec: [Technical plan §13](docs/technical-plan.md#13-safety-privacy-and-store-requirements) · [Design spec §11](docs/design-spec.md#11-accessibility) · [README §14](README.md#14-privacy-safety-and-data-handling)

### Privacy and legal
- [ ] Written privacy notice (what is collected, why, how long, deletion, contact)
- [ ] Consent recorded at onboarding (with a timestamp)
- [ ] Check National Privacy Commission (NPC) registration requirements
- [ ] Backup retention policy (encrypted backups expire within 30 days)
- [ ] Account deletion also documented on a web page (for Google Play)
- [ ] Security contact (`security@…`) and a responsible-disclosure note

### Safety 🔒
- [ ] Hotline card for Hopeless, tested on a real phone (opens the dialer with 1553)
- [ ] Account: "Need to talk to someone? NCMH 1553" row
- [ ] Hotline works fully offline (hard-coded, `/meta` only updates it)
- [ ] In-app disclaimer: "Ulap is a self-care diary, not a medical or emergency service."
- [ ] Review every message against the voice guide (no guilt, no judgment)

### Accessibility
- [ ] Every touch target ≥ 48dp
- [ ] Contrast check of every color pair (≥ 4.5:1)
- [ ] Full TalkBack pass through every screen
- [ ] Font scaling at 200% without clipping
- [ ] Reduced motion: Ulap float and the breathing animation turned off

### Polish
- [ ] Loading, error and empty states on every screen
- [ ] Screen transitions (fade plus slide, slower for Red and Blue)
- [ ] App icon (Ulap) tested at 48dp
- [ ] Polished Ulap illustration set for onboarding and the store
- [ ] Performance: cold start < 2 seconds on a mid-range phone, smooth scrolling in Logs
- [ ] Details shows an edited check-in's date and time on the clock it was logged on (`tz_offset_min`), as Logs rows do, so after a trip the two never disagree
- [ ] Crash reporting with Sentry on Android and the server (D11): `sendDefaultPii = false`, a `beforeSend` that drops request bodies and any field named `note` or `triggerOther`, no emails

**Phase 8 complete when:** an accessibility and safety review passes with no blocking issues.

---

## Phase 9: Testing and beta

**Goal:** real people try it, and we fix what hurts.
Spec: [Technical plan §15](docs/technical-plan.md#15-testing-checklist) · [API reference §15](docs/api-spec.md#15-testing-checklist) · [README §11](README.md#11-testing)

- [ ] Unit test coverage for `shared` ≥ 80%
- [ ] Server route tests for every endpoint in the API spec
- [ ] Compose UI tests for the check-in flow, logs, reports and account
- [ ] Manual test on at least 3 phones (small screen, mid-range, Android 8 / API 26)
- [ ] Manual test in all 3 languages
- [ ] Load test: 1,000 users syncing at once on the staging server
- [ ] Staging server deployed (HTTPS, managed PostgreSQL, backups)
- [ ] Internal testing track on Google Play
- [ ] Closed beta with 10–20 testers (friends, family, colleagues)
- [ ] Short feedback form (what felt kind, what felt off, bugs)
- [ ] Fix all crash and data-loss bugs found in beta
- [ ] Fix the top 5 usability issues from feedback
- [ ] Review any message testers found unkind or confusing
- [ ] Beta testers confirm their sync works across reinstalls
- [ ] Sign-off: no open P0 or P1 bugs

**Phase 9 complete when:** the beta runs for 2 weeks with no data-loss bugs, and the testers' feedback is addressed.

---

## Phase 10: Release v1.0

**Goal:** Ulap is live on Google Play.
Spec: [README §7](README.md#7-running-in-production) · [Design spec §14](docs/design-spec.md#14-open-design-items)

### Brand and store
- [ ] Name check: search "Ulap" on the Google Play Store
- [ ] Name check: IPOPHL trademark database search
- [ ] Final store name (e.g. "Ulap: Mood Diary")
- [ ] Store listing text in English (plus Filipino if possible)
- [ ] 5 store screenshots (Pick mood, Red response, After check-in, Reports, Logs)
- [ ] Feature graphic and app icon in final sizes
- [ ] Google Play **Data safety** form
- [ ] Health app declaration (if Play asks for it)
- [ ] Content rating questionnaire

### Production
- [ ] Production server deployed (HTTPS, HSTS, secrets in a secret manager)
- [ ] Production database with encrypted daily backups and a tested restore
- [ ] Monitoring and alerts (error rate, latency, database connections)
- [ ] Release keystore stored safely, Play App Signing turned on, release SHA-1 registered with Google OAuth
- [ ] Signed release bundle (`bundleRelease`) uploaded and a staged rollout started (e.g. 20%)
- [ ] Watch crashes for 48 hours, then roll out to 100%

**Phase 10 complete when:** v1.0 is available to everyone on Google Play. 🎉

---

## Phase 11: After v1 (backlog)

**Goal:** grow carefully, keeping it gentle.
Spec: [Technical plan §16](docs/technical-plan.md#16-plans-for-later-versions)

- [ ] Gentle reminders: push notifications (FCM) with quiet hours, never counting missed days
- [ ] Export: CSV
- [ ] Export: monthly PDF report
- [ ] Dark mode (second token palette)
- [ ] Good moments resurfacing on harder days (not Hopeless, not stress 9–10, can be dismissed)
- [ ] Passkeys through Credential Manager
- [ ] Home-screen widget for one-tap check-ins
- [ ] iPhone: move Room and the sync engine into `shared`
- [ ] iPhone: UI (Compose Multiplatform or SwiftUI)
- [ ] iPhone: Sign in with Apple (`POST /auth/apple`)
- [ ] More languages (e.g. Hiligaynon, Ilocano)
- [ ] Accessibility study with real TalkBack users
- [ ] Baseline Profile module, once cold start is measured on a mid-range phone (D12)

---

## How to update this file

1. Tick the box: `- [ ]` → `- [x]`.
2. Update that phase's **Done** count and **Status** in the progress overview.
3. When every box in a phase is ticked, change its status to ✅ and add the completion date to the phase heading, e.g. `## Phase 1: Project setup ✅ (Dec 2026)`.
4. Commit with: `docs(phases): complete <task or phase>`.
