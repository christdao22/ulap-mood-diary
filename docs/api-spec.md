# Ulap: Mood Diary · API Reference (v1)

Prepared: November 4, 2026 · Status: Draft for review
Server: Ktor + PostgreSQL · Client: Android (Kotlin), iOS later

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · [Technical plan](technical-plan.md) · **API reference** · [Design spec](design-spec.md) · [Repo reference](ulap-reference.md)
>
> **This document owns:** every endpoint, header, request and response shape, error code, token rule and rate limit. Database schemas and the overall sync design are in the [Technical plan](technical-plan.md) (§6–§8). If the two ever disagree about the API, this document wins, and the plan gets fixed in the same pull request.

---

## 1. Overview

The Ulap API does three things:

1. **Auth:** sign in with Google or email, refresh and revoke tokens.
2. **User:** read and update the profile and settings, delete the account.
3. **Sync:** push local check-in changes and pull changes from other devices.

The app is **offline-first**. Check-ins are created on the phone and sent with `/sync/push`. There is no "create check-in" endpoint, because sync covers create, edit and delete. Reports are calculated on the phone, so there are no report endpoints in v1.

---

## 2. Conventions

### Base URL
```
Production:  https://api.ulap.app/v1        (placeholder domain)
Local dev:   http://10.0.2.2:8080/v1         (Android emulator → host machine)
```

### Format
- JSON only, UTF-8. Send `Content-Type: application/json` and `Accept: application/json`.
- Field names: **camelCase**.
- Timestamps: **ISO 8601 in UTC** with milliseconds, e.g. `"2026-11-04T01:15:00.000Z"`.
- IDs: **UUID** strings, lowercase. Ulap makes **UUIDv7** (time-ordered, RFC 9562) on the phone and the server ([Technical plan §3](technical-plan.md#recommended-libraries)). The server checks the UUID format only, not the version.
- Booleans are `true` / `false`. Never `0` / `1`.
- Missing optional fields are either left out or sent as `null`. The server treats both the same.
- Enums are **UPPER_SNAKE** strings (`WORK`, `BREATHE`). Mood codes are **lower_snake** (`burned_out`).

### Required headers

| Header | When | Example |
|---|---|---|
| `Authorization` | All authenticated routes | `Bearer eyJhbGciOi…` |
| `X-Device-Id` | All routes | `6a1f…` (UUID created on first launch, stored in DataStore) |
| `X-App-Version` | All routes | `android/1.0.0 (12)` |
| `Accept-Language` | Optional | `fil`, `ceb`, `en`. Used only for email text (verification and reset). |
| `Idempotency-Key` | Optional on POST | UUID. Lets a retried request be accepted once. |

### Versioning
- The version is in the path: `/v1`. Breaking changes go to `/v2`, and `/v1` stays supported for at least 6 months.
- Adding new optional fields is **not** a breaking change. Clients must **ignore unknown fields** (`ignoreUnknownKeys = true` in kotlinx.serialization).

### Minimum app version
If the app is too old, any route can return `426 Upgrade Required` with error code `APP_UPDATE_REQUIRED`. The app then shows "Please update Ulap to keep syncing." Local use still works.
- The server reads the build number in brackets from `X-App-Version` (`android/1.0.0 (12)` → `12`) and compares it with `MIN_ANDROID_VERSION_CODE` ([README §8](../README.md#8-configuration-reference)).
- `/health`, `/ready` and `/meta` never return 426, so health checks work without the header and an old app can still read `/meta`.
- A missing or unreadable `X-App-Version` isn't blocked. The check is about compatibility, not security.

---

## 3. Authentication model

| Token | Format | Lifetime | Stored where (Android) |
|---|---|---|---|
| Access token | JWT, HS256 or RS256 | 15 minutes | Memory |
| Refresh token | 256-bit random string, base64url | 60 days, **rotates on every use** | Encrypted storage (Keystore-backed) |

### Access token claims
```json
{
  "iss": "https://api.ulap.app",
  "aud": "ulap-app",
  "sub": "3f0c5b1e-6c1a-4e4e-9d6b-2c1f0f6b8a10",
  "did": "6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b",
  "iat": 1793782800,
  "exp": 1793783700
}
```
`sub` is the user ID and `did` is the device ID.

### Refresh rules
- Each refresh returns a **new** refresh token and invalidates the old one.
- If an already-used refresh token is presented again, the server **revokes every token for that device** (it was probably stolen) and returns `401 REFRESH_TOKEN_REUSED`. The app signs the user out, and local data stays on the phone.
- The server stores only a **SHA-256 hash** of each refresh token.

### Client flow
1. Call an API with the access token.
2. On `401 TOKEN_EXPIRED`, call `/auth/refresh` **once**, then retry the original request.
3. If the refresh fails, mark the session as signed out. Keep local data and show "Sign in again to keep syncing."
4. Only one refresh can run at a time. Use a mutex in the Ktor client's `Auth` plugin.

---

## 4. Errors

All errors use one shape:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "stress must be between 1 and 10",
    "details": [
      { "field": "changes[2].stress", "issue": "OUT_OF_RANGE" }
    ],
    "requestId": "req_01JBX8Q2W7Z5"
  }
}
```

- `code` is stable. The app branches on it.
- `message` is for developers, in English. Don't show it to users. The app shows its own translated text.
- `requestId` also comes back in the `X-Request-Id` header, for support logs.

### Status codes and error codes

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `BAD_REQUEST` | Malformed JSON or missing required field |
| 400 | `VALIDATION_FAILED` | Field values not allowed (see `details`) |
| 401 | `UNAUTHENTICATED` | No token, or a bad token |
| 401 | `TOKEN_EXPIRED` | Access token expired. Refresh and retry. |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| 401 | `INVALID_GOOGLE_TOKEN` | Google ID token failed verification |
| 401 | `REFRESH_TOKEN_INVALID` | Unknown or expired refresh token |
| 401 | `REFRESH_TOKEN_REUSED` | Old refresh token used again. Device sessions revoked. |
| 403 | `EMAIL_NOT_VERIFIED` | Sync is blocked until the email is verified |
| 403 | `ACCOUNT_DELETED` | The account was deleted |
| 404 | `NOT_FOUND` | Route or resource not found |
| 409 | `EMAIL_TAKEN` | Registration with an email that's already in use |
| 409 | `IDENTITY_LINKED_ELSEWHERE` | This Google account belongs to another Ulap user |
| 410 | `TOKEN_USED_OR_EXPIRED` | Verification or reset link already used, or expired |
| 413 | `PAYLOAD_TOO_LARGE` | Body over 1 MB, or more than 200 changes |
| 422 | `WEAK_PASSWORD` | Password doesn't meet the rules (§6.3) |
| 426 | `APP_UPDATE_REQUIRED` | The app version is too old |
| 429 | `RATE_LIMITED` | Too many requests. See `Retry-After`. |
| 500 | `INTERNAL` | Server error. Safe to retry later. |
| 503 | `UNAVAILABLE` | Maintenance or overload. Retry with backoff. |

### Retry policy (client)
- Retry **429, 500, 502, 503 and 504** and network errors with exponential backoff: 2s, 4s, 8s, 16s, max 5 minutes, plus random jitter.
- Never retry 400, 401 (except once after a refresh), 403, 409, 413 or 422.
- WorkManager handles retries for whole sync runs (`BackoffPolicy.EXPONENTIAL`, starting at **30 seconds**). The delays above are for single calls inside one run ([Technical plan §8](technical-plan.md#when-sync-runs)).

---

## 5. Data types

### 5.1 `User`
```json
{
  "id": "3f0c5b1e-6c1a-4e4e-9d6b-2c1f0f6b8a10",
  "displayName": "Christian",
  "language": "en",
  "mascotEnabled": true,
  "identities": [
    { "provider": "GOOGLE", "email": "christian@example.com", "emailVerified": true }
  ],
  "createdAt": "2026-10-01T02:00:00.000Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `displayName` | string \| null | 1–50 chars |
| `language` | `"en"` \| `"fil"` \| `"ceb"` | |
| `mascotEnabled` | boolean | |
| `identities` | array | Sign-in methods. `provider`: `GOOGLE`, `EMAIL`, `APPLE` (later). |
| `createdAt` | timestamp | |

### 5.2 `AuthResponse`
```json
{
  "accessToken": "eyJhbGciOi…",
  "accessTokenExpiresAt": "2026-11-04T10:30:00.000Z",
  "refreshToken": "kq3Vd0…",
  "refreshTokenExpiresAt": "2027-01-03T10:15:00.000Z",
  "user": { "…": "User" },
  "isNewUser": true
}
```
`isNewUser = true` tells the app to upload all guest data (see §8.5).

### 5.3 `CheckIn`

```json
{
  "id": "b7e2c8a4-2f6d-4d0a-9a51-6f3e1c9d7b20",
  "moodCode": "worried",
  "energy": 6,
  "stress": 8,
  "stressAfter": 5,
  "trigger": "WORK",
  "triggerOther": null,
  "note": "Report due tomorrow and the data is not complete yet.",
  "supportUsed": "BREATHE",
  "isGoodMoment": false,
  "loggedAt": "2026-11-04T01:15:00.000Z",
  "tzOffsetMin": 480,
  "updatedAt": "2026-11-04T01:18:42.120Z",
  "deletedAt": null,
  "serverSeq": 1042
}
```

| Field | Type | Required on push | Rules |
|---|---|---|---|
| `id` | UUID | ✅ | Created on the phone (UUIDv7). Never changes. |
| `moodCode` | string | ✅ | One of the 25 codes (§5.4) |
| `energy` | int | ✅ | 1–10 |
| `stress` | int | ✅ | 1–10 |
| `stressAfter` | int \| null | | 1–10 |
| `trigger` | enum \| null | | `WORK` `FAMILY` `MONEY` `HEALTH` `RELATIONSHIPS` `SCHOOL` `OTHER` |
| `triggerOther` | string \| null | | ≤ 100 chars. Only when `trigger = OTHER`, otherwise ignored. |
| `note` | string \| null | | ≤ 5,000 chars. Trimmed. Empty → `null`. |
| `supportUsed` | enum | ✅ | `NONE` `BREATHE` `SMALL_HELPS` `GOOD_MOMENT` |
| `isGoodMoment` | boolean | ✅ | Only allowed for Yellow and Green moods |
| `loggedAt` | timestamp | ✅ | Not more than 5 minutes in the future. Not before 2020-01-01. |
| `tzOffsetMin` | int | ✅ | −720 to +840. Philippines = 480. |
| `updatedAt` | timestamp | ✅ | The phone's last edit time. Used for conflicts. |
| `deletedAt` | timestamp \| null | | Set → this is a delete (tombstone) |
| `serverSeq` | long | ❌ (server only) | Ignored on push. Set on pull. |

Column mapping to the phone and server tables: [Technical plan §6–§7](technical-plan.md#6-phone-database-room).

**Tombstones:** when `deletedAt` is set, the server keeps only `id`, `deletedAt`, `updatedAt` and `serverSeq`. It clears `note` and `triggerOther` right away, then purges the row after 90 days.

### 5.4 Mood codes

| Zone | Codes |
|---|---|
| Red | `panicked` `overwhelmed` `anxious` `angry` `worried` `frustrated` `nervous` `irritated` `restless` |
| Blue | `hopeless` `burned_out` `sad` `lonely` `guilty` `disappointed` `tired` `bored` |
| Yellow | `excited` `motivated` `proud` `happy` |
| Green | `content` `grateful` `relaxed` `calm` |

The list lives in the shared KMP module (`MoodCatalog`), so the app and the server always agree. Default energy and stress per mood: [Technical plan §5](technical-plan.md#5-shared-domain-model). Adding a mood later is not a breaking change. Old apps show unknown codes as "Other feeling."

---

## 6. Auth endpoints

### 6.1 `POST /auth/google`
Sign in or sign up with Google.

The app gets a Google **ID token** from Credential Manager (`GetGoogleIdOption` with your **Web client ID** as `serverClientId`) and sends it.

**Request**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6…",
  "displayName": "Christian"
}
```

**Server checks**
1. Verify the signature with Google's public keys.
2. `aud` equals your Web client ID. `iss` is `accounts.google.com` or `https://accounts.google.com`. `exp` is in the future.
3. Find the identity by `(GOOGLE, sub)`. If it doesn't exist, create a user plus the identity.

**Responses**
- `200` `AuthResponse` (`isNewUser` true or false)
- `401 INVALID_GOOGLE_TOKEN`

---

### 6.2 `POST /auth/email/register`
**Request**
```json
{ "email": "christian@example.com", "password": "Kalma-sa-Ulap-2026", "displayName": "Christian" }
```

**Responses**
- `201` `AuthResponse`. The user can use the app, but `/sync/*` returns `403 EMAIL_NOT_VERIFIED` until the email is verified. A verification email is sent.
- `409 EMAIL_TAKEN`
- `422 WEAK_PASSWORD`

The email is stored lowercased and trimmed.

---

### 6.3 Password rules
- 10–128 characters.
- Not in a common-password list (check against a top-10k list).
- Not the same as the email.
- No forced symbols or capitals. Long passphrases are encouraged.
- Hashed with **Argon2id** (memory 64 MB, iterations 3, parallelism 1).

---

### 6.4 `POST /auth/email/login`
**Request**
```json
{ "email": "christian@example.com", "password": "Kalma-sa-Ulap-2026" }
```
**Responses**
- `200` `AuthResponse`
- `401 INVALID_CREDENTIALS` (the same error whether the email exists or not)

---

### 6.5 `POST /auth/email/verify`
The verification email link opens the app (Android App Link `https://ulap.app/verify?token=…`) or a small web page, which calls this endpoint.

**Request** `{ "token": "Zk3…" }`
**Responses** `204` · `410 TOKEN_USED_OR_EXPIRED` (tokens last 24 hours, one use)

### 6.6 `POST /auth/email/resend-verification` 🔒
**Request** `{}` → `204`. Limited to 3 per hour.

---

### 6.7 `POST /auth/password/forgot`
**Request** `{ "email": "christian@example.com" }`
**Response** always `204`, so it doesn't reveal whether an account exists. Sends a reset link that lasts 30 minutes and works once.

### 6.8 `POST /auth/password/reset`
**Request** `{ "token": "Rr8…", "newPassword": "Bag-ong-Password-2026" }`
**Responses** `204` (all of the user's refresh tokens are revoked) · `410 TOKEN_USED_OR_EXPIRED` · `422 WEAK_PASSWORD`

---

### 6.9 `POST /auth/refresh`
**Request**
```json
{ "refreshToken": "kq3Vd0…" }
```
(`X-Device-Id` must match the device the token was issued to.)

**Responses**
- `200` `AuthResponse` with a **new** refresh token
- `401 REFRESH_TOKEN_INVALID` · `401 REFRESH_TOKEN_REUSED`

---

### 6.10 `POST /auth/logout` 🔒
**Request** `{ "refreshToken": "kq3Vd0…" }` → `204`. Revokes this device's session.

### 6.11 `POST /auth/logout-all` 🔒
**Request** `{}` → `204`. Revokes every session on every device.

---

### 6.12 `POST /auth/link/google` 🔒
Adds Google sign-in to an email account (or the other way round with `/auth/link/email`).

**Request** `{ "idToken": "…" }`
**Responses** `200` `User` · `409 IDENTITY_LINKED_ELSEWHERE`

---

## 7. User endpoints 🔒

### 7.1 `GET /me`
**Response** `200` `User`

### 7.2 `PATCH /me`
Partial update. Send only the fields that changed.

**Request**
```json
{ "displayName": "Ian", "language": "ceb", "mascotEnabled": false }
```
**Responses** `200` `User` · `400 VALIDATION_FAILED`

The phone keeps these settings locally too (DataStore). The server copy lets them follow the user to a new phone.

### 7.3 `DELETE /me`
Permanently deletes the account and **all** check-ins.

**Request**
```json
{ "confirm": "DELETE" }
```

**Server actions** (one transaction):
1. Hard-delete every `check_ins` row for the user.
2. Delete identities, email tokens, and refresh tokens.
3. Delete the user row.
4. Remove the user from future backups according to the retention policy (state this in the privacy notice, e.g. "backups expire within 30 days").

**Responses** `204` · `400 BAD_REQUEST` (missing confirmation)

**App actions after a 204:** wipe the Room database, DataStore and tokens, then go back to onboarding.

---

## 8. Sync endpoints 🔒

Sync needs a verified identity (Google is always verified; email needs verification).

### 8.1 How it works

```
 Phone (Room)                              Server (Postgres)
 ───────────                               ─────────────────
 1. PUSH  pending changes  ───────────────▶  merge by updatedAt
          ◀─────────────── accepted + serverSeq per id
 2. PULL  since = lastPullSeq ────────────▶  rows with serverSeq > since
          ◀─────────────── changes + nextSince (repeat while hasMore)
 3. Save nextSince as lastPullSeq
```

- `serverSeq` is a global number that only goes up. Every write the server accepts gets a new one.
- The phone remembers the last `serverSeq` it pulled and asks only for newer changes.

### 8.2 `POST /sync/push`

**Request**
```json
{
  "changes": [
    {
      "id": "b7e2c8a4-2f6d-4d0a-9a51-6f3e1c9d7b20",
      "moodCode": "worried",
      "energy": 6,
      "stress": 8,
      "stressAfter": 5,
      "trigger": "WORK",
      "triggerOther": null,
      "note": "Report due tomorrow and the data is not complete yet.",
      "supportUsed": "BREATHE",
      "isGoodMoment": false,
      "loggedAt": "2026-11-04T01:15:00.000Z",
      "tzOffsetMin": 480,
      "updatedAt": "2026-11-04T01:18:42.120Z",
      "deletedAt": null
    },
    {
      "id": "0c9d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
      "updatedAt": "2026-11-04T02:00:00.000Z",
      "deletedAt": "2026-11-04T02:00:00.000Z"
    }
  ]
}
```
- At most **200 changes** per request. Send more in batches.
- A delete only needs `id`, `updatedAt` and `deletedAt`.

**Merge rule for each change**

| Server state | Incoming | Result |
|---|---|---|
| No row | any | Insert. New `serverSeq`. → `accepted` |
| Row exists | `updatedAt` **newer** than stored | Update. New `serverSeq`. → `accepted` |
| Row exists | `updatedAt` **older or equal** | Keep stored. → `accepted` with `status: "STALE"` and the current `serverSeq`. The phone will get the newer version on pull. |
| Row is a tombstone | Not a delete, newer `updatedAt` | Un-delete is **not** allowed in v1. → `accepted`, `status: "STALE"` |
| Belongs to another user | any | → `rejected`, `reason: "FORBIDDEN"` |
| Invalid values | any | → `rejected` with a reason |

A clock guard: if an incoming `updatedAt` is more than 5 minutes in the future, the server uses its own current time instead.

**Response `200`**
```json
{
  "accepted": [
    { "id": "b7e2c8a4-2f6d-4d0a-9a51-6f3e1c9d7b20", "serverSeq": 1042, "status": "APPLIED" },
    { "id": "0c9d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f", "serverSeq": 1043, "status": "APPLIED" }
  ],
  "rejected": [
    { "id": "9a8b7c6d-…", "reason": "INVALID_MOOD", "field": "moodCode" }
  ],
  "serverTime": "2026-11-04T02:00:01.250Z"
}
```

`reason` values: `INVALID_MOOD`, `OUT_OF_RANGE`, `TOO_LONG`, `INVALID_ENUM`, `INVALID_TIME`, `GOOD_MOMENT_NOT_ALLOWED`, `FORBIDDEN`.

**Phone actions**
- `APPLIED` or `STALE` → set `sync_state = SYNCED` and store `serverSeq`.
- `rejected` → keep `PENDING` but mark `sync_error = reason`, and don't retry automatically. Log the `reason` and `id` only, never the change itself. These should only come from bugs, because the phone validates with the same shared rules.
- The whole request fails with `500 INTERNAL` → add 1 to `sync_attempts` on each row in the batch. At 10, set `sync_error = "FAILED"` and stop retrying that row. Other failures (network, 429, 502–504) don't count.
- Rows with `sync_error` set are left out of later pushes until the user taps **Try again** in Account or edits the check-in ([Technical plan §8](technical-plan.md#when-a-change-cant-upload)).

**Errors:** `403 EMAIL_NOT_VERIFIED` · `413 PAYLOAD_TOO_LARGE` · `429 RATE_LIMITED`

---

### 8.3 `GET /sync/pull`

**Query**

| Param | Type | Default | Notes |
|---|---|---|---|
| `since` | long | `0` | The last `serverSeq` the phone has. `0` = everything. |
| `limit` | int | `500` | 1–1000 |

**Response `200`**
```json
{
  "changes": [
    { "id": "b7e2…", "moodCode": "worried", "energy": 6, "stress": 8, "stressAfter": 5,
      "trigger": "WORK", "triggerOther": null, "note": "Report due tomorrow…",
      "supportUsed": "BREATHE", "isGoodMoment": false,
      "loggedAt": "2026-11-04T01:15:00.000Z", "tzOffsetMin": 480,
      "updatedAt": "2026-11-04T01:18:42.120Z", "deletedAt": null, "serverSeq": 1042 },
    { "id": "0c9d…", "updatedAt": "2026-11-04T02:00:00.000Z",
      "deletedAt": "2026-11-04T02:00:00.000Z", "serverSeq": 1043 }
  ],
  "nextSince": 1043,
  "hasMore": false,
  "resetRequired": false,
  "serverTime": "2026-11-04T02:00:05.900Z"
}
```
- `changes` are in ascending `serverSeq` order.
- Keep pulling with `since = nextSince` while `hasMore` is true.

**`resetRequired`** (D6 in [ulap-reference §13](ulap-reference.md#13-decision-log))

Tombstones are purged after 90 days, so an old `since` may have missed a delete. The server returns `resetRequired: true` when:
- `0 < since < purged_through_seq`: a tombstone after this cursor was already purged (`sync_purge`, [Technical plan §7](technical-plan.md#7-server-database-postgresql)), or
- `since` is higher than any `serverSeq` the server has given out: the cursor comes from another database (a restore, or a different server).

Then the response is `{ "changes": [], "nextSince": 0, "hasMore": true, "resetRequired": true, "serverTime": … }`. The phone starts a **full resync**: it pulls from `since = 0`, notes every ID it receives, and at the end removes local `SYNCED` rows the server didn't send. `PENDING` rows are kept and pushed. Steps: [Technical plan §8](technical-plan.md#full-resync-resetrequired).

`since = 0` never gets `resetRequired`. The field is always present, and `false` in every other response.

**Phone merge for each pulled change**

| Local state | Action |
|---|---|
| No local row | Insert as `SYNCED` (skip if it's a tombstone) |
| Local `SYNCED` | Overwrite with the server version |
| Local `PENDING` and local `updatedAt` newer | Keep local. It will push next time. |
| Local `PENDING` and server newer | Overwrite with the server version, mark `SYNCED` |
| Server tombstone | Set local `deletedAt` (and remove it from the UI) |

---

### 8.4 `GET /sync/status`
A small check for the Account screen.

**Response `200`**
```json
{ "checkInCount": 128, "latestServerSeq": 1043, "lastPushAt": "2026-11-04T02:00:01.250Z" }
```

---

### 8.5 First sign-in from guest mode

```
Guest uses the app → check-ins saved locally (no account yet)
        │
        ▼
Sign in (Google or email) → AuthResponse
        │
        ├── isNewUser = true  → mark all local rows PENDING → push all → pull since 0
        │
        └── isNewUser = false (account already has data from another phone)
                 │
                 ├── local guest rows exist → ask:
                 │      "Add this phone's check-ins to your account?"
                 │        [Add them]  → push all, then pull since 0
                 │        [Don't add] → delete local guest rows, then pull since 0
                 │
                 └── no local rows → pull since 0
```

### 8.6 When the app syncs
`SyncTriggers` on the phone schedules every run. Repositories never do ([Technical plan §8](technical-plan.md#when-sync-runs)).
- After each save, edit or delete (one-time job, requires network, 5-second debounce)
- When the app opens (if the last sync was more than 15 minutes ago)
- Every 6 hours in the background (periodic job, requires network and battery not low)
- Manually: Account → "Sync now"

Each run: **push all PENDING (in batches of 200) → pull until `hasMore = false`**.

---

## 9. Health and meta

### 9.1 `GET /health`
No auth. `200` `{ "status": "ok" }`. Liveness: the process is up and answering. It doesn't touch the database, so a database outage doesn't get the container restarted.

### 9.2 `GET /ready`
No auth. Readiness: the server can do real work. It runs `SELECT 1` through the connection pool with a 2-second timeout.
- `200` `{ "status": "ready" }`
- `503 UNAVAILABLE` in the standard error format (§4) when the database doesn't answer in time

The deploy runbook checks `/ready` after `migrate` and `up -d --wait`, before it sends traffic to the new version (D5 in [ulap-reference §13](ulap-reference.md#13-decision-log)).

### 9.3 `GET /meta`
No auth. Lets the app check its compatibility.

```json
{
  "minAppVersion": { "android": 1, "ios": null },
  "latestAppVersion": { "android": 1 },
  "moodCatalogVersion": 1,
  "hotline": { "name": "NCMH Crisis Hotline", "number": "1553" }
}
```
The hotline is also hard-coded in the app, so it works offline. `/meta` only allows updating it without an app release.
`minAppVersion.android` and `latestAppVersion.android` come from `MIN_ANDROID_VERSION_CODE` and `LATEST_ANDROID_VERSION_CODE` ([README §8](../README.md#8-configuration-reference)). There's no iOS app yet, so `ios` is left out (the same as `null`, §2).

---

## 10. Rate limits

| Route group | Limit | Key |
|---|---|---|
| `/auth/email/login`, `/auth/email/register` | 10 per minute, 50 per day | IP + email |
| `/auth/password/forgot`, `/auth/email/resend-verification` | 3 per hour | email |
| `/auth/google`, `/auth/refresh` | 30 per minute | IP + device |
| `/sync/push` | 60 per minute | user |
| `/sync/pull` | 120 per minute | user |
| Everything else | 120 per minute | user or IP |

On a limit: `429 RATE_LIMITED` with `Retry-After: <seconds>`.
In Ktor: the `RateLimit` plugin, with named limiters per route group.
"IP" is the `CF-Connecting-IP` header set by the Cloudflare Tunnel, the only way in to production ([ulap-reference §8.4](ulap-reference.md#84-hosting-d14-accepted)). Without it (local runs) it's the socket address. Counters are kept in memory, which is enough for one server.

---

## 11. Security and privacy

- **HTTPS only** (TLS 1.2+). HSTS on the API domain.
- **No sensitive data in logs.** Never log request bodies for `/sync/*` or `/auth/*`, tokens, passwords, or `note` / `triggerOther`. Log the request ID, route, status and time taken.
- **Encryption at rest:** encrypted database disk and backups. `note` and `triggerOther` are also encrypted at the application level (AES-256-GCM, with a key from an environment variable or secret manager, rotatable with a `key_id` column).
- **Authorization:** every sync and user query filters by the `sub` from the access token. Never trust a user ID from the request body.
- **Input limits:** body ≤ 1 MB, ≤ 200 changes per push, the string length limits in §5.3.
- **CORS:** off. This is a mobile API, and there is no browser client in v1.
- **Account deletion** is real deletion (§7.3).
- **Google Play Data safety:** health-related data collected, encrypted in transit, user can request deletion, not shared, not sold.

---

## 12. Ktor implementation notes

```kotlin
fun Application.module() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(CallId) { header("X-Request-Id"); generate { "req_" + ulid() } }
    install(StatusPages) { exception<ApiException> { call, e -> call.respond(e.status, e.toBody()) } }
    install(RateLimit) { /* named limiters per §10 */ }
    install(Authentication) {
        jwt("access") {
            verifier(jwtVerifier)                      // iss, aud, exp checks
            validate { cred -> cred.payload.subject?.let { UserPrincipal(UUID.fromString(it), cred.payload.getClaim("did").asString()) } }
            challenge { _, _ -> throw ApiException.unauthenticated() }
        }
    }
    routing {
        route("/v1") {
            get("/health") { call.respond(mapOf("status" to "ok")) }
            get("/ready") { /* SELECT 1 with a 2 s timeout → 200, or 503 UNAVAILABLE */ }
            get("/meta") { /* … */ }
            authRoutes()                               // /auth/*
            authenticate("access") {
                userRoutes()                           // /me
                syncRoutes()                           // /sync/*
            }
        }
    }
}
```

Shared DTOs in `shared/commonMain/api/`:

```kotlin
@Serializable
data class CheckInDto(
    val id: String,
    val moodCode: String? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val stressAfter: Int? = null,
    val trigger: Trigger? = null,
    val triggerOther: String? = null,
    val note: String? = null,
    val supportUsed: SupportType? = null,
    val isGoodMoment: Boolean? = null,
    val loggedAt: Instant? = null,
    val tzOffsetMin: Int? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val serverSeq: Long? = null,
)

@Serializable data class PushRequest(val changes: List<CheckInDto>)
@Serializable data class PushResult(val id: String, val serverSeq: Long, val status: PushStatus)
@Serializable data class PushRejection(val id: String, val reason: String, val field: String? = null)
@Serializable data class PushResponse(val accepted: List<PushResult>, val rejected: List<PushRejection>, val serverTime: Instant)
@Serializable data class PullResponse(val changes: List<CheckInDto>, val nextSince: Long, val hasMore: Boolean, val resetRequired: Boolean = false, val serverTime: Instant)
@Serializable enum class PushStatus { APPLIED, STALE }
```

Validation (`CheckInValidator`) also lives in `shared`, so the phone and the server reject the same inputs.

### Push merge (SQL sketch, one transaction per batch)
```sql
INSERT INTO check_ins (id, user_id, mood_code, energy, stress, stress_after, trigger, trigger_other,
                       note, support_used, is_good_moment, logged_at, tz_offset_min,
                       client_updated_at, deleted_at, server_seq, server_updated_at)
VALUES (:id, :userId, …, :updatedAt, :deletedAt, nextval('check_in_seq'), now())
ON CONFLICT (id) DO UPDATE SET
  mood_code = EXCLUDED.mood_code, energy = EXCLUDED.energy, stress = EXCLUDED.stress,
  stress_after = EXCLUDED.stress_after, trigger = EXCLUDED.trigger, trigger_other = EXCLUDED.trigger_other,
  note = EXCLUDED.note, support_used = EXCLUDED.support_used, is_good_moment = EXCLUDED.is_good_moment,
  logged_at = EXCLUDED.logged_at, tz_offset_min = EXCLUDED.tz_offset_min,
  client_updated_at = EXCLUDED.client_updated_at, deleted_at = EXCLUDED.deleted_at,
  server_seq = nextval('check_in_seq'), server_updated_at = now()
WHERE check_ins.user_id = :userId
  AND check_ins.deleted_at IS NULL
  AND EXCLUDED.client_updated_at > check_ins.client_updated_at
RETURNING id, server_seq;
```
Rows not returned are either `STALE` (read their current `server_seq`) or `FORBIDDEN` (the row belongs to another user).

---

## 13. Example session (cURL)

```bash
# 1) Sign in with Google
curl -s https://api.ulap.app/v1/auth/google \
  -H 'Content-Type: application/json' -H 'X-Device-Id: 6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b' \
  -H 'X-App-Version: android/1.0.0 (1)' \
  -d '{"idToken":"<google-id-token>"}'

# 2) Push one check-in
curl -s https://api.ulap.app/v1/sync/push \
  -H 'Authorization: Bearer <access>' -H 'Content-Type: application/json' \
  -H 'X-Device-Id: 6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b' -H 'X-App-Version: android/1.0.0 (1)' \
  -d '{"changes":[{"id":"b7e2c8a4-2f6d-4d0a-9a51-6f3e1c9d7b20","moodCode":"worried","energy":6,"stress":8,
       "trigger":"WORK","supportUsed":"NONE","isGoodMoment":false,
       "loggedAt":"2026-11-04T01:15:00.000Z","tzOffsetMin":480,"updatedAt":"2026-11-04T01:15:30.000Z"}]}'

# 3) Pull everything
curl -s 'https://api.ulap.app/v1/sync/pull?since=0&limit=500' \
  -H 'Authorization: Bearer <access>' -H 'X-Device-Id: 6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b' \
  -H 'X-App-Version: android/1.0.0 (1)'

# 4) Refresh tokens
curl -s https://api.ulap.app/v1/auth/refresh \
  -H 'Content-Type: application/json' -H 'X-Device-Id: 6a1f9b2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b' \
  -H 'X-App-Version: android/1.0.0 (1)' \
  -d '{"refreshToken":"<refresh>"}'
```

---

## 14. Endpoint summary

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | – | Liveness check |
| GET | `/ready` | – | Readiness check (database) |
| GET | `/meta` | – | Minimum app version, catalog version, hotline |
| POST | `/auth/google` | – | Sign in or up with Google |
| POST | `/auth/email/register` | – | Create an email account |
| POST | `/auth/email/login` | – | Email sign-in |
| POST | `/auth/email/verify` | – | Verify email |
| POST | `/auth/email/resend-verification` | 🔒 | Resend the verification email |
| POST | `/auth/password/forgot` | – | Send a reset link |
| POST | `/auth/password/reset` | – | Set a new password |
| POST | `/auth/refresh` | – | Rotate tokens |
| POST | `/auth/logout` | 🔒 | Sign out this device |
| POST | `/auth/logout-all` | 🔒 | Sign out all devices |
| POST | `/auth/link/google` | 🔒 | Add Google to an account |
| POST | `/auth/link/email` | 🔒 | Add email and password to an account |
| GET | `/me` | 🔒 | Profile and settings |
| PATCH | `/me` | 🔒 | Update profile and settings |
| DELETE | `/me` | 🔒 | Delete the account and all data |
| POST | `/sync/push` | 🔒 | Send local changes |
| GET | `/sync/pull` | 🔒 | Get changes since a `serverSeq` |
| GET | `/sync/status` | 🔒 | Sync summary |

**Later versions:** `POST /auth/apple` (iPhone), `GET /me/export` (CSV/PDF), `POST /devices/push-token` (notifications).

---

## 15. Testing checklist

Tracked in [PHASES.md](../PHASES.md) (Phases 6, 7 and 9).

- [ ] Google: valid token → 200. Wrong `aud` → 401. Expired → 401.
- [ ] Email: register → 201. Duplicate → 409. Weak password → 422. Sync before verification → 403.
- [ ] Refresh: rotate works. The old token reused → 401 `REFRESH_TOKEN_REUSED`, and the device session is revoked.
- [ ] Push: insert, newer update, older update (`STALE`), delete, edit after a delete (`STALE`), another user's ID (`FORBIDDEN`), 201 changes → 413.
- [ ] Pull: `since=0` with paging. Tombstones are included. Results are in order.
- [ ] Pull reset: a cursor below `purged_through_seq` → `resetRequired: true` with no changes. A cursor above the latest `serverSeq` → `resetRequired: true`. `since=0` → `false`.
- [ ] Full resync on the phone: a `SYNCED` row the server no longer has is removed. A `PENDING` row is kept. Killing the app halfway carries on next run.
- [ ] Push failing with 500 ten times → those rows get `sync_error = FAILED` and stop retrying. Try again sends them again.
- [ ] Ready: database up → 200. Database stopped → 503 `UNAVAILABLE` within about 2 seconds. `/health` stays 200.
- [ ] Two devices edit the same check-in offline → the newest `updatedAt` wins on both after sync.
- [ ] Guest → new account uploads everything. Guest → existing account shows the merge prompt.
- [ ] `DELETE /me` removes every row. The old access token → 401 or 403.
- [ ] Rate limits return 429 with `Retry-After`.
- [ ] Logs contain no notes, tokens or passwords.
