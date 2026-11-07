# Ulap: Mood Diary · Technical Plan (v1)

Prepared: November 4, 2026 · Status: Draft for review

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · **Technical plan** · [API reference](api-spec.md) · [Design spec](design-spec.md) · [Repo reference](ulap-reference.md)
>
> **This document owns:** scope, architecture, project structure, domain model and response rules, phone and server database schemas, sync design, reports logic, languages, privacy, and the mood names and message keys (appendices). Endpoint details are in the [API reference](api-spec.md), UI in the [Design spec](design-spec.md), and progress in [Phases](../PHASES.md).

---

## 1. Summary

Ulap is a mood diary for everyone. A person picks **one mood per check-in**, adjusts **energy** and **stress**, picks a **trigger**, and can write a **journal note**. The app then responds with sympathy through **Ulap**, a small cloud mascot: it validates the feeling, offers one small form of help, and asks *"How stressed do you feel now?"*

| Area | Decision |
|---|---|
| Platform | Android first (Kotlin, Jetpack Compose). iPhone later. |
| Code sharing | Kotlin Multiplatform (KMP): shared models and logic for Android, the server, and later iOS |
| Backend | Ktor + PostgreSQL |
| Data | Offline-first on the phone, syncs both ways when online |
| Users | Public app, many users |
| Accounts | No account needed to start. Sign in with Google or email to back up and sync. Sign in with Apple when the iPhone version ships. |
| Languages | English, Filipino, Bisaya (Cebuano). The user chooses in the app. |
| Check-in | Exactly one mood per check-in |
| Good moments | Version 1 has the **save** button only. Resurfacing saved moments on hard days comes later. |
| Later | Push notifications, export, dark mode, iPhone |

---

## 2. Version 1 scope

### In scope
1. **Onboarding:** choose a language, meet Ulap, short privacy note, start logging. No account needed.
2. **Check in:** mood grid (4 zones) → details (energy, stress, trigger, journal) → sympathetic response → optional "How do you feel now?"
3. **Logs:** list grouped by day, search, filter by zone, edit, delete.
4. **Reports:** Daily, Weekly and Monthly views. Summary tiles, stress trend, zone mix, mood frequency, top triggers, time-of-day pattern, and what helped. All calculated on the phone.
5. **Account:** back up and sync (Google or email), language, mascot on/off, sign out, **delete account and all data**.
6. **Safety:** a Hopeless check-in shows a support card with the **NCMH Crisis Hotline 1553**.

### Out of scope for v1
Push notifications, export (CSV/PDF), dark mode, iOS app, resurfacing good moments, social features, sharing with clinicians.

---

## 3. Architecture

```
┌──────────────────────────────┐          HTTPS / JSON          ┌───────────────────────────┐
│ Android app (Compose)        │  ───────────────────────────▶  │ Ktor server               │
│  UI · ViewModels             │                                │  Auth (Google, email)     │
│  Room DB (source of truth)   │  ◀───────────────────────────  │  Sync API (push / pull)   │
│  WorkManager sync worker     │                                │  PostgreSQL               │
└──────────────┬───────────────┘                                └─────────────┬─────────────┘
               │                     shared (KMP module)                      │
               └──────────▶  models · mood catalog · zone rules · DTOs  ◀─────┘
```

**Main principle:** the phone database is the **source of truth for the UI**. Every screen reads from Room. The network only moves changes between Room and the server. That way the app works the same online and offline.

### Recommended libraries

Use the latest stable versions when you set up the project.

**Shared (KMP `commonMain`)**
- `kotlinx.serialization`: JSON for the API
- `kotlinx-datetime`: dates and times across platforms
- `kotlin.uuid`: a small `Ids.newId()` helper in `shared` makes **UUIDv7** IDs (RFC 9562: a millisecond timestamp first, then random bits). They sort by creation time, so database indexes stay compact. The phone uses it for check-ins and the server for its own rows ([ulap-reference §13](ulap-reference.md#13-decision-log), D7).

**Android**
- Jetpack Compose + Material 3
- Navigation Compose
- Room **2.8.x**, encrypted with **SQLCipher** (`net.zetetic:sqlcipher-android`). Stay on Room 2: Room 3 drops the `SupportSQLiteOpenHelper` hook that SQLCipher plugs into. Room supports KMP, but an iOS driver for an encrypted database is still an open question for later.
- Tink (`tink-android`): wraps the database passphrase with an Android Keystore key (§6)
- WorkManager: background sync, started by `SyncTriggers` (§8)
- DataStore: settings (language, mascot on/off, last sync position)
- Credential Manager + Google ID library: Sign in with Google (the legacy Google Sign-In SDK is deprecated)
- Ktor Client: API calls
- Hilt: dependency injection in the Android modules. `shared` uses plain constructor injection and no DI library, so an iOS app can reuse it later ([ulap-reference §13](ulap-reference.md#13-decision-log), D2).
- AppCompat per-app language API: in-app language switching

**Server**
- Ktor Server (Netty), `ktor-server-auth-jwt`, `ktor-server-content-negotiation`, `ktor-server-status-pages`, `ktor-server-rate-limit`
- Exposed (SQL DSL) + HikariCP + PostgreSQL JDBC driver
- Flyway: database migrations
- Google API Client: verify Google ID tokens
- Argon2id (e.g. `argon2-jvm`): password hashing (parameters in [API reference §6.3](api-spec.md#63-password-rules))
- An email service (any transactional SMTP or API provider) for verification and password reset

---

## 4. Project structure

One Gradle build with four kinds of module. [README §5](../README.md#5-folder-structure) has the full folder tree.

| Module | Holds | May depend on |
|---|---|---|
| `shared` (KMP) | Models, `MoodCatalog`, `ResponseRules`, `CheckInValidator`, API DTOs | Nothing Android. No DI library. |
| `core:*` (Android libraries) | Room (`core:database`), Ktor Client (`core:network`), repositories (`core:data`), sync, auth, DataStore, theme and mascot (`core:designsystem`), strings and shared screen pieces (`core:ui`) | `shared` and other `core` modules |
| `feature:*` (Android libraries) | Screens and ViewModels for onboarding, check-in, logs, reports, account | `core:common`, `core:model`, `core:domain`, `core:designsystem`, `core:ui`, `shared`. Never another feature, `core:database` or `core:network`. |
| `androidApp` | Application class, `MainActivity`, navigation, build types (`debug`, `staging`, `release`) | Everything above |
| `server` (Ktor) | Routes, services, data access, Flyway migrations | `shared` |

Why split Android into modules (D1): screens can't reach the database or the network by accident, so "screens read only from Room, through repositories" is enforced by the build instead of by review.

---

## 5. Shared domain model

### Zones and moods

Moods are stored as **stable codes** (e.g. `worried`), never as translated words. Names and messages come from string resources in the user's language.

| Zone | Code | Tier | Moods (code · default energy · default stress) |
|---|---|---|---|
| Red, tense / high energy | `red` | 1 | panicked 9·10, overwhelmed 8·9, anxious 8·9, angry 9·8, worried 6·8, frustrated 7·7, nervous 7·6, irritated 6·6, restless 7·5 |
| Blue, heavy / low energy | `blue` | 2 | hopeless 2·8, burned_out 2·8, sad 3·6, lonely 3·6, guilty 4·6, disappointed 4·5, tired 2·4, bored 3·3 |
| Yellow, bright / high energy | `yellow` | 3 | excited 9·3, motivated 8·2, proud 7·2, happy 7·2 |
| Green, easy / low energy | `green` | 4 | content 4·1, grateful 4·1, relaxed 3·1, calm 3·1 |

```kotlin
enum class Zone(val tier: Int) { RED(1), BLUE(2), YELLOW(3), GREEN(4) }

data class Mood(
    val code: String,
    val zone: Zone,
    val defaultEnergy: Int,   // 1..10
    val defaultStress: Int,   // 1..10
)

enum class Trigger { WORK, FAMILY, MONEY, HEALTH, RELATIONSHIPS, SCHOOL, OTHER }

enum class SupportType { NONE, BREATHE, SMALL_HELPS, GOOD_MOMENT }
```

The catalog lives in `shared` so the app and the server validate against the same list. The server rejects unknown mood codes.

### Response rules (shared)

| Rule | Behavior |
|---|---|
| Red zone | Concerned-but-calm Ulap, breathing exercise offered, check-in slider shown |
| Blue zone | Gentle Ulap, "something small that helps" list, check-in slider shown |
| Yellow / Green | Happy or calm Ulap, "Save to good moments" offered, no check-in slider |
| `hopeless` | Quiet Ulap (no animation), NCMH 1553 card instead of activity buttons, and a secondary **Done** at the bottom. The check-in slider is still shown (Blue zone). The After screen keeps the quiet, still Ulap and shows the 1553 card again, whichever way stress went. |
| Stress ≥ 8 | Only one action button (no choices): no "Maybe later", and in Red and Blue the check-in slider waits until the support has started. Hopeless keeps its slider from the start, since it has no support button to wait for. |
| Unpleasant zone and a trigger is set | Add the trigger line (e.g. "Work can take a lot out of us.") |

Put these rules in `shared/rules/ResponseRules.kt` and unit-test them. How each case looks on screen: [Design spec §7.4](design-spec.md#74-check-in--response-sympathetic).

---

## 6. Phone database (Room)

### `check_ins`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (UUIDv7) | Generated on the device with `Ids.newId()`, so it never changes after sync |
| `mood_code` | TEXT | From MoodCatalog |
| `energy` | INTEGER | 1–10 |
| `stress` | INTEGER | 1–10 |
| `stress_after` | INTEGER NULL | From "How do you feel now?" |
| `trigger` | TEXT NULL | Trigger enum name |
| `trigger_other` | TEXT NULL | Free text when trigger = OTHER |
| `note` | TEXT NULL | Journal |
| `support_used` | TEXT | SupportType |
| `is_good_moment` | INTEGER (bool) | Saved to good moments |
| `logged_at` | INTEGER | Epoch millis (UTC) |
| `tz_offset_min` | INTEGER | Offset when logged, so reports group by the user's local day |
| `created_at` | INTEGER | Epoch millis |
| `updated_at` | INTEGER | Epoch millis, set on every edit |
| `deleted_at` | INTEGER NULL | Soft delete (a tombstone), needed for sync |
| `sync_state` | TEXT | `PENDING` or `SYNCED` |
| `sync_error` | TEXT NULL | Why this row stopped trying to upload: a rejection reason from `/sync/push` ([API reference §8.2](api-spec.md#82-post-syncpush)), or `FAILED` after 10 internal errors (§8). `NULL` while it can still sync. |
| `sync_attempts` | INTEGER | Push requests that failed with `500 INTERNAL` while this row was in the batch. Reset to 0 on every edit and on "Try again". |
| `server_seq` | INTEGER NULL | The server's change number, once known |

Indexes: `(deleted_at, logged_at)`, `(sync_state)`, `(mood_code)`.

### `pull_seen`
One column, `id` (TEXT, primary key). Used only during a full resync (§8): it holds the IDs of every live check-in the server sent, and it's emptied when the resync ends.

### Settings (DataStore, not Room)
`language` (`en` / `fil` / `ceb`), `mascot_enabled`, `onboarding_done`, `device_id` (UUID), `last_pull_seq`, `last_sync_at`, `sync_resetting` (true while a full resync is running, §8).

### Tokens
Store refresh tokens in **EncryptedSharedPreferences**, or in DataStore encrypted with an **Android Keystore** key. Never store them in plain text.

### Encryption of the phone database
Mood data is sensitive, so the **whole Room database is encrypted with SQLCipher**, not only the journal columns. That protects moods, stress and triggers too ([ulap-reference §13](ulap-reference.md#13-decision-log), D3).

- **Passphrase:** 32 random bytes, made once per install with `SecureRandom`.
- **Where it's kept:** encrypted with a Tink AEAD keyset, in a file in `noBackupFilesDir`. The keyset itself is locked by a key in the **Android Keystore**, which never leaves the phone's secure hardware.
- **Opening the database:** Room uses SQLCipher's `SupportOpenHelperFactory` with that passphrase. Reading the Keystore is slow, so build the database off the main thread.
- **If the Keystore key is lost** (app data restored onto another phone, or the lock screen was reset), the old database can't be opened. Delete it, start an empty one and set `last_pull_seq` to 0. A signed-in user gets their synced check-ins back from the server on the next sync. A guest's check-ins, and any that hadn't synced yet, are gone. That's the price of real encryption.
- **Backups:** turn off cloud backup of the database (`android:allowBackup="false"`, or exclude the database and the passphrase file in the backup rules). The server sync is the backup.
- **Tests:** an instrumented test checks that the database file can't be opened without the passphrase.

---

## 7. Server database (PostgreSQL)

```sql
-- V1__init.sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  display_name  TEXT,
  language      TEXT NOT NULL DEFAULT 'en',
  mascot_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);

-- One user can have many sign-in methods (Google now, email, Apple later)
CREATE TABLE auth_identities (
  id               UUID PRIMARY KEY,
  user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider         TEXT NOT NULL CHECK (provider IN ('GOOGLE','EMAIL','APPLE')),
  provider_subject TEXT NOT NULL,          -- Google "sub", Apple "sub", or the email address
  email            TEXT,
  email_verified   BOOLEAN NOT NULL DEFAULT FALSE,
  password_hash    TEXT,                   -- email provider only
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_subject)
);

CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id   UUID NOT NULL,
  token_hash  TEXT NOT NULL,              -- store a hash, never the token itself
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE email_tokens (                -- verification and password reset
  id          UUID PRIMARY KEY,
  identity_id UUID NOT NULL REFERENCES auth_identities(id) ON DELETE CASCADE,
  purpose     TEXT NOT NULL CHECK (purpose IN ('verify','reset')),
  token_hash  TEXT NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  used_at     TIMESTAMPTZ
);

CREATE SEQUENCE check_in_seq;

CREATE TABLE check_ins (
  id                UUID PRIMARY KEY,      -- same UUID the phone created
  user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  mood_code         TEXT NOT NULL,
  energy            SMALLINT NOT NULL CHECK (energy BETWEEN 1 AND 10),
  stress            SMALLINT NOT NULL CHECK (stress BETWEEN 1 AND 10),
  stress_after      SMALLINT CHECK (stress_after BETWEEN 1 AND 10),
  trigger           TEXT,
  trigger_other     TEXT,
  note              TEXT,
  support_used      TEXT NOT NULL DEFAULT 'NONE',
  is_good_moment    BOOLEAN NOT NULL DEFAULT FALSE,
  logged_at         TIMESTAMPTZ NOT NULL,
  tz_offset_min     SMALLINT NOT NULL,
  client_updated_at TIMESTAMPTZ NOT NULL,  -- the phone's updated_at
  deleted_at        TIMESTAMPTZ,
  server_seq        BIGINT NOT NULL DEFAULT nextval('check_in_seq'),
  server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX check_ins_user_seq ON check_ins (user_id, server_seq);

-- How far tombstones have been purged. One row (id = 1). The purge job raises
-- purged_through_seq to the highest server_seq it deleted, in the same transaction.
-- A pull with 0 < since < purged_through_seq may have missed a delete → resetRequired (§8).
CREATE TABLE sync_purge (
  id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  purged_through_seq BIGINT NOT NULL DEFAULT 0,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO sync_purge (id) VALUES (1);
```

Server-made IDs (`users`, tokens) are UUIDv7 from the same `Ids.newId()` in `shared` (§3).

**Encryption on the server:** use an encrypted disk and encrypted backups at minimum. For extra protection, encrypt `note` and `trigger_other` at the application level with a server key (AES-GCM) kept outside the database.

---

## 8. Sync design

### Rules
1. **Every change is written to Room first** with `sync_state = PENDING` and a fresh `updated_at`.
2. **Deletes are soft.** Set `deleted_at` so the delete can sync. Purge tombstones after 90 days.
3. **IDs are UUIDv7s made on the phone** (`Ids.newId()` in `shared`), so the same check-in has the same ID everywhere.
4. **The server gives every accepted change a new `server_seq`,** a number that only goes up. Pulling means asking for everything after the last number you saw.
5. **Conflicts:** last write wins, using `updatedAt` (stored on the server as `client_updated_at`). If they're equal, the stored version is kept. Conflicts are rare, since it's one person on their own devices.

### Push and pull (summary)
- **Push:** the phone sends `PENDING` changes, at most 200 per request. The server applies newer ones and answers `APPLIED`, `STALE` or rejected for each.
- **Pull:** the phone asks for every change after its last `serverSeq` and keeps paging while `hasMore` is true.
- **Merge on the phone:** a local `PENDING` row with a newer `updatedAt` is kept. Otherwise the server version wins.

Exact requests, responses, merge tables and error codes are in the [API reference §8](api-spec.md#8-sync-endpoints-).

### When sync runs
Same schedule as [API reference §8.6](api-spec.md#86-when-the-app-syncs): after each save, edit or delete (5-second debounce, network required), on app open if the last sync was more than 15 minutes ago, every 6 hours in the background, and from Account → "Sync now". Order: **push first, then pull** until `hasMore = false`.

**`SyncTriggers` decides when ([ulap-reference §13](ulap-reference.md#13-decision-log), D8).** Repositories only write to Room. They never schedule sync, so a new write path can't forget to.
- It starts once, from `Application.onCreate`, and does nothing while no one is signed in. Signing out cancels all sync work.
- While someone is signed in, it schedules the 6-hour periodic job and watches a Room `Flow` of the count of rows with `sync_state = PENDING` and `sync_error IS NULL`. When that count is above 0, it requests a sync 5 seconds later, so a burst of quick edits goes up in one push.
- It also requests a sync when the app comes to the foreground (if the last sync was more than 15 minutes ago).
- Requests go to one unique WorkManager job, so two syncs never run at once. Network required.
- **Backoff:** when a run fails (network, 429, 5xx), `SyncWorker` returns `Result.retry()` with `BackoffPolicy.EXPONENTIAL` starting at **30 seconds**. Inside one run, single HTTP calls still use the short retries in [API reference §4](api-spec.md#retry-policy-client).

### When a change can't upload
A row stops trying when `sync_error` is set. It stays on the phone and in every screen. Only uploading stops.
- **Rejected** by `/sync/push` (a bug, since the phone validates with the same shared rules): `sync_error = reason`, straight away.
- **Internal errors:** when a push request fails with `500 INTERNAL`, add 1 to `sync_attempts` on every row in that batch. At **10**, set `sync_error = "FAILED"`. Network errors, 429, 502, 503 and 504 don't count. They're outages, not a problem with the row.
- **Account** shows "%d check-ins couldn't back up yet. They're still saved on this phone." with **Try again** ([Design spec §7.8](design-spec.md#78-account)). Try again clears `sync_error` and `sync_attempts` on those rows, so `SyncTriggers` picks them up. Never show the number of attempts.
- **Editing** the check-in also clears both, because every save sets `PENDING`, `sync_error = NULL` and `sync_attempts = 0`.

### Full resync (`resetRequired`)
Tombstones are purged after 90 days. A phone that was offline longer than that would never hear about those deletes, so the server tells it to start over ([API reference §8.3](api-spec.md#83-get-syncpull), D6).
1. A pull returns `resetRequired: true` with no changes. The server does that when `0 < since < purged_through_seq`, or when `since` is higher than any `server_seq` it has (the database was restored, or it's a different server).
2. The phone, in one transaction: empties `pull_seen`, sets `last_pull_seq = 0` and `sync_resetting = true`.
3. It pulls from `since = 0` with the normal merge. While `sync_resetting` is true, it also adds the ID of every pulled change that isn't a tombstone to `pull_seen`.
4. When the last page arrives (`hasMore = false`), in one transaction: delete local `SYNCED` rows whose ID isn't in `pull_seen`, empty `pull_seen`, and set `sync_resetting = false`.
5. `PENDING` rows are never removed. They push as usual. If the app is killed halfway, `sync_resetting` is still true, so the next run carries on.

### First sign-in (guest → account)
Follows [API reference §8.5](api-spec.md#85-first-sign-in-from-guest-mode):
1. The user signs in, and `isNewUser` comes back in the auth response.
2. **New account:** mark all local check-ins `PENDING`, push everything, then pull from `since = 0`.
3. **Existing account, and this phone has guest check-ins:** ask **"Add this phone's check-ins to your account?"** → **Add them** (push all, then pull) or **Don't add** (delete the local guest check-ins, then pull).

---

## 9. API endpoints

The **[API reference](api-spec.md) is the source of truth** for every endpoint, header, request, response, error code and rate limit. Summary:

| Group | Endpoints (all under `/v1`) |
|---|---|
| Meta | `GET /health` (the process is up), `GET /ready` (the database answers, D5), `GET /meta` |
| Auth | `/auth/google`, `/auth/email/register`, `/auth/email/login`, `/auth/email/verify`, `/auth/email/resend-verification`, `/auth/password/forgot`, `/auth/password/reset`, `/auth/refresh`, `/auth/logout`, `/auth/logout-all`, `/auth/link/google`, `/auth/link/email` |
| User | `GET /me`, `PATCH /me`, `DELETE /me` |
| Sync | `POST /sync/push`, `GET /sync/pull`, `GET /sync/status` |

Key rules (details in the API reference):
- The device ID travels in the **`X-Device-Id` header** on every request, never in request bodies.
- Access token: JWT, 15 minutes. Refresh token: random, 60 days, stored hashed, rotates on every use. Reusing an old one revokes the device's sessions.
- Google flow: Credential Manager ID token → `POST /auth/google` → the server verifies the signature, `aud` (your Web client ID), `iss` and `exp`.
- Sync requires a verified email. Google identities are always verified.

---

## 10. Reports (calculated on the phone)

Screen layout: [Design spec §7.7](design-spec.md#77-reports).

Reports read from Room, so they work offline. "Day" means the user's local day, using `logged_at + tz_offset_min`.

| Report | How it's calculated |
|---|---|
| Summary tiles | Count of check-ins, average stress, average energy, most common mood, top trigger. Compared with the previous period. The screen shows four of these (no average energy tile) and only shows the stress change: a check-in count change would read as a nudge to log more ([Design spec §7.7](design-spec.md#77-reports)). |
| Stress trend | Daily: one bar per check-in. Weekly: average per day (7 bars). Monthly: average per day (28–31 bars). |
| Zone mix | Count per zone ÷ total. Calculated and tested, not shown in v1. |
| Mood frequency | Top 5 moods by count |
| Top triggers | Count and average stress per trigger |
| Time of day | Average stress by block: Morning 5–11, Afternoon 12–17, Evening 18–21, Night 22–4. Not shown as a card in v1; it feeds the "Ulap noticed" time-block rule. |
| What helped | For check-ins with `stress_after`: average of `stress − stress_after`, grouped by `support_used` |
| "Ulap noticed" text | Simple rules: highest-stress weekday, highest time block, top trigger share. No judgment words. |

Wording rule: describe patterns ("Mondays tend to be harder for you"). Never grade the user.

---

## 11. Languages (English, Filipino, Bisaya)

- Store the choice in DataStore and apply it with the **per-app language API** (`AppCompatDelegate.setApplicationLocales`).
- Resource folders: `values/` (English), `values-fil/` (Filipino), `values-b+ceb/` (Cebuano/Bisaya).
- String keys follow the codes: `mood_worried`, `zone_red_name`, `resp_red_title`, `resp_red_body`, `trigger_line_work`, `help_breathe`, …
- Use string formatting for inserted values: `<string name="resp_red_body">Feeling %1$s makes sense…</string>`.
- Keep mood codes and enum names in English in the database and API. Only the display text is translated.
- Language is chosen on the first screen of onboarding and can be changed in Account.

A first-draft mood name table is in **Appendix A**. It needs your review, especially the Bisaya.

---

## 12. Ulap the mascot

Full visual spec (shapes, expressions, sizes, motion): [Design spec §8](design-spec.md#8-ulap-the-mascot).

- Built as a Compose `Canvas` or vector drawable with **6 expressions**: `neutral`, `concerned`, `gentle`, `happy`, `calm`, `quiet`.
- A gentle floating animation, turned off for `quiet` and when the system "Remove animations" setting is on.
- Can be switched off in Account. When it's off, the messages still appear, just without the character.
- Rules: Ulap never cries, panics, or looks sad because the user missed a day. No guilt messages.
- **Name check before publishing:** search "Ulap" in the Play Store, and check the IPOPHL trademark database if you might register the name. Suggested store name: **"Ulap: Mood Diary"**.

---

## 13. Safety, privacy and store requirements

### Safety
- `hopeless` → NCMH Crisis Hotline **1553** card with a call button (`ACTION_DIAL`, which opens the dialer and doesn't place the call automatically).
- The Account screen always shows "Need to talk to someone? NCMH 1553".
- Optional rule for v1.1: three or more Hopeless check-ins within 7 days → gently show the support card on app open.
- Add an in-app note: *Ulap is a self-care diary, not a medical or emergency service.*

### Privacy
- Mood and journal data is health-related personal information. Treat it as **sensitive**.
- The Philippine **Data Privacy Act of 2012 (RA 10173)** applies. Write a clear **privacy notice**, get **consent** at onboarding, and check the **National Privacy Commission** registration requirements for your setup.
- No ads, no selling data, no analytics on journal content.
- Data minimization: only an email or Google ID is needed. A display name is optional.
- **Delete account and all data** works from inside the app (Google Play also requires an account-deletion path for apps that let users create accounts).

### Google Play
- Fill in the **Data safety** form honestly (health-related data collected, encrypted in transit, deletion available).
- Answer the health-app declaration if Play asks for it for your category.
- Target the current required Android API level.

---

## 14. Build plan

The build order, task checklists and progress live in **[PHASES.md](../PHASES.md)**, the single tracker for the project.

| Phase | Name |
|---|---|
| 0 | Research and planning ✅ |
| 1 | Project setup |
| 2 | Offline check-in |
| 3 | Logs |
| 4 | Reports |
| 5 | Languages and onboarding |
| 6 | Server and accounts |
| 7 | Sync |
| 8 | Privacy, safety and polish |
| 9 | Testing and beta |
| 10 | Release v1.0 |
| 11 | After v1 (backlog) |

---

## 15. Testing checklist

Test cases per area. Test tasks and their status are tracked in [PHASES.md](../PHASES.md) (Phases 2–9). Commands are in [README §11](../README.md#11-testing), and API test cases in [API reference §15](api-spec.md#15-testing-checklist).

| Area | Tests |
|---|---|
| Shared rules | Every mood maps to the right zone and defaults. Response rules per zone, Hopeless, stress ≥ 8. |
| Room | Insert, edit, soft delete, report queries per local day across midnight and time zones |
| Sync | Push accepted and rejected changes. Pull pagination. Last write wins. Tombstones. Retry after failure. |
| Auth | Expired access token → refresh. Reused refresh token → refused. Invalid Google token → 401. |
| UI | Check-in flow, language switch, mascot off, TalkBack labels, touch targets ≥ 48dp |
| Privacy | Account deletion removes server data and local data. No notes in logs. |

---

## 16. Plans for later versions

Tracked in [PHASES.md, Phase 11](../PHASES.md#phase-11-after-v1-backlog).

- **Push notifications:** gentle reminders and quiet hours using FCM. Reminders never count missed days.
- **Export:** CSV and a PDF monthly report.
- **Dark mode.**
- **iPhone:** move the Room data layer and the sync engine into `shared`. Build the UI in Compose Multiplatform or SwiftUI. Add **Sign in with Apple**, since Apple's Guideline 4.8 requires an equivalent privacy-friendly login whenever Google sign-in is offered.
- **Good moments resurfacing:** on Red or Blue days (not Hopeless, not stress 9–10), after the validating message, Ulap can show one saved good moment. The user can dismiss it or turn it off.
- **Passkeys** through Credential Manager.

---

## Appendix A: Mood names, first draft (needs review)

> ⚠️ Draft only. Please review the Filipino and especially the **Bisaya** column for natural, everyday wording. Pick words people actually use when they talk about their feelings.

| Code | English | Filipino (draft) | Bisaya (draft) |
|---|---|---|---|
| panicked | Panicked | Natataranta | Nataranta |
| overwhelmed | Overwhelmed | Nabibigatan nang sobra | Nabug-atan kaayo |
| anxious | Anxious | Balisa | Kulbaan |
| angry | Angry | Galit | Nasuko |
| worried | Worried | Nag-aalala | Nabalaka |
| frustrated | Frustrated | Naiinis | Nasagmuyo |
| nervous | Nervous | Kinakabahan | Gikulbaan |
| irritated | Irritated | Iritado | Nalagot |
| restless | Restless | Hindi mapakali | Dili mahimutang |
| hopeless | Hopeless | Nawawalan ng pag-asa | Nawad-an og paglaum |
| burned_out | Burned out | Ubos na ang lakas | Kapoy na kaayo |
| sad | Sad | Malungkot | Masulub-on |
| lonely | Lonely | Nag-iisa | Mingaw |
| guilty | Guilty | Nakokonsensya | Nakonsensya |
| disappointed | Disappointed | Nadismaya | Nasagmuyo |
| tired | Tired | Pagod | Gikapoy |
| bored | Bored | Naiinip | Nalaay |
| excited | Excited | Nasasabik | Naghinamhinam |
| motivated | Motivated | Ganado | Ganado |
| proud | Proud | Ipinagmamalaki ang sarili | Garbo sa kaugalingon |
| happy | Happy | Masaya | Malipayon |
| content | Content | Kuntento | Kontento |
| grateful | Grateful | Nagpapasalamat | Mapasalamaton |
| relaxed | Relaxed | Relaks | Relaks |
| calm | Calm | Kalmado | Kalma |

Notes for review:
- *Frustrated* and *Disappointed* both use **Nasagmuyo** in the draft. Pick different words so the two tiles aren't the same.
- *Proud* should feel positive. Avoid words that suggest arrogance.
- Everyday Taglish or Bislish is fine where it's more natural (e.g. "Stressed," "Excited").

## Appendix B: Response message keys (English source)

This is the source for app text. Tone rules: [Design spec §10](design-spec.md#10-voice-and-tone). Translations go in the three `strings.xml` files ([§11](#11-languages-english-filipino-bisaya)).

| Key | English |
|---|---|
| `pick_greeting` | How are you feeling right now? |
| `pick_sub` | Any answer is okay. |
| `pre_red` | That can feel intense. I'm right here. |
| `pre_blue` | Thanks for telling me. Take your time. |
| `pre_yellow` | Ooh, tell me more! |
| `pre_green` | That sounds peaceful. |
| `resp_red_title` | That's a lot to carry right now. |
| `resp_red_body` | Feeling %1$s makes sense when things pile up. Your body is on high alert, and that's okay. |
| `resp_blue_title` | Thank you for being honest. |
| `resp_blue_body` | Low days happen. You don't have to fix this today. |
| `resp_yellow_title` | Love this! |
| `resp_yellow_body` | What made you feel %1$s? Remembering it can help on harder days. |
| `resp_green_title` | A calm moment. |
| `resp_green_body` | Notice what's helping you feel %1$s, so you can come back to it. |
| `resp_hopeless_title` | I'm really glad you told me. |
| `resp_hopeless_body` | You don't have to go through this alone. Talking to someone can help, even right now. |
| `resp_action_breathe` | Breathe with me · 1 minute |
| `resp_action_small_helps` | Something small that helps |
| `resp_action_good_moment` | Save to good moments |
| `resp_action_later` | Maybe later (the secondary button; it ends the check-in) |
| `resp_action_done` | Done (under the good-moment card; it ends the check-in) |
| `help_breathe_steps` | In for 4 · hold for 2 · out for 6 |
| `help_breathe_sub` | Follow the circle. Five slow breaths is enough. |
| `help_breathe_in` / `_hold` / `_out` | Breathe in · %1$d / Hold · %1$d / Breathe out · %1$d (the counter when animations are off; seconds left) |
| `help_small_title` | Want to try one? |
| `help_small_water` | Drink a glass of water (the list runs easiest first, in this order) |
| `help_small_song` | Play a song you like |
| `help_small_stretch` | Stretch your neck and shoulders |
| `help_small_air` | Open a window or step outside for a minute |
| `help_small_message` | Message someone, even just to say hi |
| `help_small_sub` | Even one is enough. |
| `help_good_saved` | Saved to your good moments |
| `help_good_saved_sub` | Moments like this are worth keeping. |
| `check_ready` | When you're ready (the small line above the after-check question; Red and Blue only) |
| `check_title` | How stressed do you feel now? |
| `check_action` | Check in (the after-check card's button; it saves `stress_after`) |
| `trigger_line_work` | Work can take a lot out of us. |
| `trigger_line_family` | The people closest to us can affect us the most. |
| `trigger_line_money` | Money worries are real, and tiring. |
| `trigger_line_health` | Your body is asking for some care. |
| `trigger_line_school` | Learning under pressure is hard. |
| `after_lower` | Your stress went from %1$d to %2$d. |
| `after_lower_sub` | That's %1$d points lower. Remember what helped, so you can use it again. (Use a plurals resource for point/points.) |
| `after_same` | Still at %1$d, and that's okay. |
| `after_lower_sub_hopeless` | I'm glad something eased a little. Help is still here whenever you need it. (Hopeless only, in place of `after_lower_sub`) |
| `after_same_sub` | Feelings take time to shift. Be gentle with yourself today. |
| `after_higher` | It went up to %1$d. |
| `after_higher_sub` | Thank you for checking honestly. If it keeps rising, reach out to someone you trust or call NCMH at 1553. |
| `after_higher_sub_hopeless` | Thank you for checking honestly. This sounds really heavy. Talking to someone can help, even right now. (Hopeless only, in place of `after_higher_sub`: the hotline card is right below it) |
| `after_before` / `after_now` | Before %1$d / Now %1$d (the two pills at the top of the After screen) |
| `after_action_week` | See my week (the After screen's secondary button; it opens Reports. Its primary button reuses `resp_action_done`) |
| `prompt_red` | What's the worry? What part is in your control? |
| `prompt_blue` | What happened? What do you need right now? |
| `prompt_yellow` | What made this moment good? |
| `prompt_green` | What's helping you feel this way? |
| `hotline_title` | NCMH Crisis Hotline · available 24/7 |
| `hotline_number` | 1553 (not translated, so it's only in `values/strings.xml`) |
| `hotline_call` | Call %1$s (`%1$s` is `hotline_number`) |
| `hotline_helper` | This opens your phone app. You choose when to call. |
| `hotline_no_phone` | This device can't make calls. You can dial %1$s from any phone. (`%1$s` is `hotline_number`; shown when no phone app can open the number) |
| `disclaimer` | Ulap is a self-care diary, not a medical or emergency service. |
