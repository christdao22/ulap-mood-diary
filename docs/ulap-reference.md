# Ulap: Repo Reference (reuse plan from macro-tracker)

Prepared: November 4, 2026 · Status: Draft. Decisions in §13 are **proposed** until you accept them.
Source: `REPO-REFERENCE.md` of **macro-tracker**, verified at commit `7569423` (2026-11-04). Section numbers like "MT §6" point to that file.

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · [Technical plan](technical-plan.md) · [API reference](api-spec.md) · [Design spec](design-spec.md) · **Repo reference**
>
> **This document owns:** what Ulap reuses from macro-tracker (setup, conventions, CI, infra, Claude Code tooling), the Claude Code setup for Ulap, and the decision log for changes that reuse would bring.
>
> **Precedence:** until a decision in [§13](#13-decision-log) is marked **Accepted**, the other docs win. When one is accepted, the docs listed in its row are updated in the same pull request, so the set never disagrees.

Legend (same as macro-tracker): **[reuse]** copy as-is or nearly · **[adapt]** copy, then rename or retune · **[pattern]** reimplement the idea in Kotlin/Ktor · **[skip]** not for Ulap

---

## Contents

1. [Summary](#1-summary)
2. [Stack comparison](#2-stack-comparison)
3. [Target repo layout](#3-target-repo-layout)
4. [Android build setup](#4-android-build-setup)
5. [Android architecture rules](#5-android-architecture-rules)
6. [Server: Hono patterns ported to Ktor](#6-server-hono-patterns-ported-to-ktor)
7. [Sync: what to borrow](#7-sync-what-to-borrow)
8. [Infra, backup and deploy](#8-infra-backup-and-deploy)
9. [CI/CD](#9-cicd)
10. [Conventions](#10-conventions)
11. [Claude Code setup for Ulap](#11-claude-code-setup-for-ulap)
12. [Docs to add](#12-docs-to-add)
13. [Decision log](#13-decision-log)
14. [Setup checklist](#14-setup-checklist)
15. [Do not copy](#15-do-not-copy)
16. [Lessons from macro-tracker's doc drift](#16-lessons-from-macro-trackers-doc-drift)

---

## 1. Summary

macro-tracker and Ulap share a lot: a native Android app in Kotlin and Compose, offline-first with Room as the source of truth, WorkManager sync through `/v1/sync/push` and `/v1/sync/pull`, Credential Manager Google sign-in, Postgres, Docker Compose, a Philippine audience (RA 10173), and the same owner.

The big difference is the server. macro-tracker runs **Hono on Node (TypeScript)** in a pnpm + Turborepo monorepo and shares types with Android through a generated OpenAPI client. Ulap runs **Ktor (Kotlin)** in **one Gradle build** with a Kotlin Multiplatform `shared` module, so the app and server share the actual Kotlin classes and no code generation is needed ([Technical plan §3](technical-plan.md#3-architecture)).

| Area | Verdict | Why |
|---|---|---|
| Repo hygiene (`.editorconfig`, `.gitattributes`, `.gitignore` patterns) | **[reuse]** | Language-neutral |
| Android Gradle setup (convention plugins, version catalog, Spotless, detekt, Lint) | **[adapt]** | Same platform. Rename `macrotracker.*` → `ulap.*`. |
| Android module rules (`core:*` / `feature:*`) | **[adapt]**, D1 accepted | Proven boundaries |
| Room + SQLCipher + Tink, DataStore, WorkManager | **[adapt]**, see D3 | Same needs (sensitive journal) |
| Network client | **[skip]** Retrofit + OpenAPI codegen → keep **Ktor Client** | Ktor Client works in KMP (iOS later). Shared DTOs replace codegen. |
| Server code (Hono, Drizzle, Better Auth, BullMQ, Redis) | **[pattern]** | Different language. Keep the layering, error shape, middleware order, jobs table. |
| Sync engine ideas (cursor, tombstones, reset, retries) | **[pattern]**, see D6–D8 | Fills real gaps in Ulap's design |
| Infra (compose split, hardening, backup with age + rclone, restore drill, Cloudflare Tunnel) | **[adapt]** | Same VPS style |
| CI (`ci-android`, server CI, `release-android`) | **[adapt]** | Swap pnpm steps for Gradle |
| Claude Code (agents, `phase-work`, `reference-coding`, hooks, memory) | **[adapt]** | Point them at Ulap's docs and PHASES.md |

---

## 2. Stack comparison

Versions are macro-tracker's pinned versions on 2026-11-04 (MT §3). Use them as Ulap's **starting point**, then confirm each is still the latest stable when you create the catalog.

| Layer | macro-tracker | Ulap | Note |
|---|---|---|---|
| Gradle / daemon JDK | 9.6.0 / JDK 25 (foojay) | Same | [reuse] `gradle-daemon-jvm.properties` |
| AGP | 9.4.1 | Same | Must be ≤ your Android Studio's supported AGP |
| Kotlin / KSP | 2.4.21 / 2.3.12 | Same | Ulap also applies the KMP plugin to `shared` |
| SDK compile / min / target | 37 / 26 / 36 | Same | Matches the docs' API 26 floor |
| Java / JVM target | 17 | 17 | |
| Compose BOM | 2026.09.00 | Same | |
| Navigation Compose | 2.10.2, type-safe `@Serializable` routes | Same | |
| Lifecycle | 2.11.0 | Same | |
| Charts | **Vico 3.3.1** | Vico (D9 accepted) | Stress trend chart. Horizontal bars stay plain Compose. |
| DI | **Hilt 2.60.1** | Hilt in Android, none in `:shared` | D2 accepted |
| Room / SQLCipher / androidx.sqlite | 2.8.5 / 4.20.0 / 2.7.1 | Room (docs) + column encryption | D3 (accepted): full SQLCipher |
| Crypto | Tink 1.24.0 | Keystore (docs) | Tink wraps keys with Keystore |
| DataStore | 1.3.0 (JSON serializer, not Proto) | DataStore | Same pattern |
| WorkManager | 2.12.0 | Same | |
| HTTP client | Retrofit 3.0.0 / OkHttp 5.5.1 + openapi-generator | **Ktor Client** | Keep Ktor (KMP) |
| Serialization / coroutines | 1.12.0 / 1.11.0 | Same | |
| Google sign-in | Credentials 1.6.0 / googleid 1.2.1 | Same | `GetGoogleIdOption` |
| Crash reporting | Sentry Android 8.62.0 | Sentry, Android and server (D11 accepted) | PII off, bodies and `note` scrubbed ([Phases 8](../PHASES.md#phase-8-privacy-safety-and-polish)) |
| Startup | Baseline Profile module | After v1 (D12 accepted) | [Phases 11](../PHASES.md#phase-11-after-v1-backlog) |
| Tests | JUnit 4, Robolectric 4.17, MockWebServer, room-testing, work-testing, Compose UI test | JUnit, Room, Compose UI test, Testcontainers | Ktor has `MockEngine` instead of MockWebServer |
| Lint | Spotless 8.10.4 + ktlint 1.8.0 + compose-rules 0.6.8, detekt 1.23.8, Android Lint | Same | D10 accepted |
| Server runtime | Node 24, Hono 4.13 | **Ktor** (JVM 17+) | Pick the latest stable Ktor 3.x |
| ORM / migrations | Drizzle 0.45 / drizzle-kit | **Exposed + Flyway** | |
| Auth | Better Auth (60-day sliding session, email OTP) | Own JWT + rotating refresh ([API §3](api-spec.md#3-authentication-model)) | Keep Ulap's. No Better Auth for Kotlin. |
| Queue / cache | BullMQ + Redis | None in v1 | §6.6 |
| Postgres | `postgres:17.11-alpine3.24` | Same tag | Exact tags (MT §5) |
| Dev mail | Mailpit v1.27 | Mailpit | Different ports (D13) |
| Ingress | Cloudflare Tunnel | Same, own hostnames (D14 accepted) | [Deploy runbook](runbooks/deploy.md) |
| Backup | pg_dump + age + rclone → R2, nightly | "Daily encrypted backups, expire within 30 days" | Same tooling, 30-day lifecycle |

---

## 3. Target repo layout

This is the target layout (D1, D10 and D13 accepted on 2026-11-05). [README §5](../README.md#5-folder-structure) owns the folder structure and carries the detail.

```
ulap/
├── CLAUDE.md                        # NEW: short rules for Claude Code (§11.1). Committed.
├── README.md · PHASES.md · LICENSE · CONTRIBUTING.md
├── .editorconfig                    # [reuse] LF, 2 spaces; 4 for .kt/.kts; .md keeps trailing spaces
├── .gitattributes                   # [reuse] minus the graphify merge-driver line
├── .gitignore                       # [adapt] .env*/!.env.example, *.jks, build dirs, .claude/local bits, graphify-out/
├── .claude/                         # agents, skills, hooks (see §11 for what's committed)
├── .github/workflows/               # ci-android.yml, ci-server.yml, release-android.yml
├── settings.gradle.kts              # includeBuild("build-logic"), modules, typesafe accessors, FAIL_ON_PROJECT_REPOS
├── build.gradle.kts                 # plugins apply false; Spotless + detekt for every module
├── gradle.properties                # caching, config cache, parallel. NO OAuth client ids.
├── gradle/
│   ├── libs.versions.toml           # the ONLY place versions live
│   └── gradle-daemon-jvm.properties
├── config/detekt/detekt.yml         # overrides only, each with a reason
├── build-logic/convention/          # ulap.* convention plugins
├── shared/                          # KMP: model, catalog, rules, validation, api DTOs (no DI, no Android)
├── androidApp/                      # ":app" equivalent: Application, MainActivity, NavHost, build types
├── core/                            # Android library modules (D1)
│   ├── common/  model/  domain/     # model and domain may simply re-export or wrap :shared
│   ├── designsystem/                # theme tokens, Ulap composable, components
│   ├── ui/                          # shared screen pieces
│   ├── database/                    # Room + SQLCipher, schemas/ committed
│   ├── datastore/                   # settings file
│   ├── network/                     # Ktor client + plugins (bearer, app headers, 426)
│   ├── auth/                        # Credential Manager, session store (Tink)
│   ├── data/                        # repositories
│   ├── sync/                        # SyncEngine, SyncWorker, SyncTriggers
│   └── testing/                     # fakes, MainDispatcherRule
├── feature/
│   ├── onboarding/  checkin/  logs/  reports/  account/
├── server/                          # Ktor: Application.kt, modules/<m>/…, jobs/, db/migration/
├── infra/
│   ├── compose.yaml                 # base: postgres, migrate, server, backup
│   ├── compose.dev.yaml             # ports, mailpit
│   ├── compose.prod.yaml            # registry images, limits, log rotation, cloudflared
│   ├── compose.drill.yaml           # restore drill
│   ├── .env*.example
│   └── backup/                      # Dockerfile, backup.sh, restore.sh, entrypoint.sh
└── docs/
    ├── technical-plan.md · api-spec.md · design-spec.md · ulap-reference.md
    ├── local-services.md            # NEW (§12)
    ├── runbooks/                    # NEW: deploy, backup, restore-drill, release-android, google-sign-in
    └── release/play-console.md      # NEW: Data safety + health declaration answers
```

---

## 4. Android build setup

### 4.1 Convention plugins **[adapt]**
Copy macro-tracker's `build-logic/convention/` and rename (MT §11 E):
- Plugin ids `macrotracker.*` → `ulap.*` (in `libs.versions.toml` `[plugins]` and `build-logic/convention/build.gradle.kts`)
- Package `ph.macrotracker.buildlogic` → `app.ulap.buildlogic`
- Plugins to keep: `ulap.android.application`, `ulap.android.library`, `ulap.android.feature` (enforces allowed dependencies), `ulap.android.room` (schema export + MigrationTestHelper assets), `ulap.hilt` (D2), `ulap.jvm.library`
- Update `Sdk.kt` (compile 37, min 26, target 36) and `ALLOWED_FEATURE_DEPENDENCIES`
- Add one new plugin, **`ulap.kmp.library`**, for `shared` (targets `jvm()` and `androidTarget()`, later `iosArm64()` / `iosSimulatorArm64()`)

Keep the attribution comments macro-tracker has on code adapted from `android/nowinandroid` (Apache-2.0).

### 4.2 Version catalog **[adapt]**
`gradle/libs.versions.toml` is the only place versions live. Start from macro-tracker's catalog, then:
- Remove: Retrofit, OkHttp, openapi-generator, play-services code scanner, Health Connect, anything for food search or barcodes
- Add: Ktor Client (core, okhttp engine, content-negotiation, auth, logging off in release), Ktor Server bundle, Exposed, Flyway, HikariCP, Postgres JDBC, Argon2 (`argon2-jvm`), Google API Client (ID token verification), Testcontainers, `ktor-server-test-host`, Ktor `MockEngine`
- Add Turbine and MockK **only if you actually use them**. macro-tracker's docs named them, but they were never in its catalog (MT §13 item 7).

### 4.3 Build types and Gradle properties
- Build types **debug**, **staging** (`initWith(release)`, `.staging` suffix, `matchingFallbacks += "release"`), **release** (R8 + shrink). No flavors. (MT §5, D4)
- Per-machine and CI values come from `~/.gradle/gradle.properties` (never the repo), read with `providers.gradleProperty`, all prefixed **`ulap.`**:

| Property | Example | Replaces (current docs) |
|---|---|---|
| `ulap.apiUrl.debug` | `http://10.0.2.2:8080/v1` | `ulap.apiBaseUrl` in `local.properties` |
| `ulap.apiUrl.staging` / `ulap.apiUrl.release` | `https://staging-api.ulap.app/v1` / `https://api.ulap.app/v1` | — |
| `ulap.googleWebClientId` | Web client ID | same key, moves to `~/.gradle/gradle.properties` |
| `ulap.sentryDsn` | Sentry DSN (D11). Empty: the app reports nothing. | — |
| `ulap.versionCode` | set by CI | — |
| `ulap.upload.*` | keystore path, passwords, alias | `ULAP_KEYSTORE_*` in README §7.2 |

- Debug allows cleartext through `network_security_config`. Staging and release never do.
- Real phone over USB: `adb reverse tcp:8080 tcp:8080`, then debug can keep using `localhost`-style URLs.

### 4.4 Lint and format (D10)

| Tool | Setting (from macro-tracker) |
|---|---|
| Spotless + ktlint | `android_studio` style, max line 120, trailing commas, `@Composable` exempt from function naming |
| compose-rules | Only allowed CompositionLocal: **`LocalUlapColors`** |
| detekt | `buildUponDefaultConfig`. Every override carries a one-line reason. |
| Android Lint | `checkDependencies`, `warningsAsErrors`, `abortOnError`. Version-nag checks off. |
| Kotlin warnings | Errors when `-PwarningsAsErrors=true` (CI always passes it) |

One command for everything CI checks:
```bash
./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true
```

---

## 5. Android architecture rules

Adopted from macro-tracker (MT §4–§5). D1 (multi-module) is accepted.

| Rule | Ulap wording |
|---|---|
| Layer boundaries | Ktor DTOs never leave `core:network`, and Room entities never leave `core:database`. Repositories map both to domain types from `:shared`. |
| Feature isolation (D1) | `feature:*` may depend on `core:common`, `core:model`, `core:domain`, `core:designsystem`, `core:ui`, and `:shared`. Never on another feature, `core:database` or `core:network`. Enforced by `ulap.android.feature`. |
| Pure modules | `:shared` (commonMain), `core:model`, `core:domain` and `core:testing` have no Android dependencies |
| Repositories | `OfflineFirstCheckInRepository` in `core:data`, bound to a domain interface |
| Writes | One Room transaction per user action. It sets `sync_state = PENDING` and `updated_at` (see §7 for whether to add an outbox row too). |
| Sync trigger | Repositories **never** start sync. `SyncTriggers` observes pending rows and schedules `SyncWorker` (D8). |
| Kotlin style | No `!!` outside tests. Injected dispatchers. No `GlobalScope`. `stateIn(WhileSubscribed(5_000))`. Never swallow `CancellationException`. No Arrow, custom DSLs or context receivers. |
| UI state | `collectAsStateWithLifecycle`. Stateless composables with previews ([README §12](../README.md#12-contributing-guidelines)). |
| Room schema | Every schema change: a migration, a `MigrationTestHelper` test, and the exported schema JSON committed. CI fails if a build changes `core/database/schemas`. |
| Session storage | Tokens in a DataStore file encrypted with a **Tink AEAD keyset wrapped by Android Keystore** (macro-tracker's `SessionStore` pattern) |
| Network plugins | Ktor Client plugins replacing macro-tracker's OkHttp interceptors: bearer token + single-flight refresh, `X-Device-Id` / `X-App-Version` headers, a 426 handler that shows "Please update Ulap" ([API §2](api-spec.md#2-conventions)) |
| Tests | ViewModels with fakes + `MainDispatcherRule`. Room on Robolectric in-memory. Compose with `createComposeRule`. Network with Ktor `MockEngine`. Workers with work-testing. |

---

## 6. Server: Hono patterns ported to Ktor

### 6.1 Layering **[pattern]**
macro-tracker: `modules/<m>/{routes, controller, service, dal}.ts` + `index.ts` (MT §4). Ulap in Ktor:

```
server/src/main/kotlin/app/ulap/server/
├── Application.kt            # fun Application.module(deps: AppDeps)
├── AppDeps.kt                # config, db, clock, services: injectable for tests
├── lib/                      # ApiException, error mapping, rate limits, app-version gate, request id
├── modules/
│   ├── health/  HealthRoutes.kt
│   ├── meta/    MetaRoutes.kt
│   ├── auth/    AuthRoutes.kt · AuthService.kt · AuthDal.kt · GoogleVerifier.kt · Tokens.kt
│   ├── users/   UserRoutes.kt · UserService.kt · UserDal.kt
│   └── sync/    SyncRoutes.kt · SyncService.kt · SyncDal.kt · SyncRules.kt
└── jobs/                     # Schedule.kt table + job bodies
```

| Layer | Rule (from macro-tracker) |
|---|---|
| Routes | Parse, validate (with `:shared` validators), call the service, respond. No try/catch. |
| Service | Business rules. Throws `ApiException(code, message, status)`. |
| DAL | Exposed queries only. Every function takes an optional transaction. |
| Deps | `AppDeps` is passed into `module()`, so `testApplication` tests pass fakes (macro-tracker's `AppDeps` pattern). |

### 6.2 Plugin order **[pattern]**
macro-tracker's middleware order (MT §4), mapped to Ktor `install` order:

1. `CallId` (`X-Request-Id`) → 2. `Compression` → 3. app-version gate (426) → 4. rate limits for auth routes → 5. `Authentication` (JWT) → 6. per-route rate limits → 7. routes

### 6.3 Errors **[pattern]**
- Keep Ulap's shape `{ error: { code, message, details, requestId } }` ([API §4](api-spec.md#4-errors)). macro-tracker also returns `status`. Not needed, since the HTTP status already says it.
- Adopt the rule **"Only ever add error codes; never rename or remove one."**
- Validation failures → `400 VALIDATION_FAILED` with `details[]`, the same as macro-tracker.

### 6.4 Config **[pattern]**
macro-tracker validates env with zod and fails on startup. Ulap's `AppConfig` does the same ([README §8](../README.md#8-configuration-reference)).

### 6.5 Health and readiness (D5)
macro-tracker has `/health` (liveness) **and** `/ready` (checks Postgres). Accepted (D5): Ulap adds `GET /v1/ready`, which checks the database pool ([API §9.2](api-spec.md#92-get-ready)). The deploy runbook uses it (`up -d --wait`, then check `/ready`).

### 6.6 Jobs without Redis **[pattern]**
macro-tracker runs BullMQ jobs from a second entry point (`scheduler.ts`) using the same image, defined as a table (name, cron, idempotent `run()`, `tz: "Asia/Manila"`, concurrency 1). Ulap's jobs are few:

| Job | When (Asia/Manila) | What |
|---|---|---|
| `purge-tombstones` | 03:00 daily | Delete `check_ins` tombstones older than 90 days |
| `purge-tokens` | 03:15 daily | Delete expired or revoked refresh and email tokens |
| (backup runs in its own container) | 04:30 daily | See §8 |

Recommendation: a `Schedule.kt` table in the server and a separate `main` (`app.ulap.server.SchedulerKt`) run as a `scheduler` Compose service from the **same image**, so the jobs never run twice when the API scales. No Redis is needed for v1. The Ktor `RateLimit` plugin is in-memory, which is fine for **one** API instance. Add a shared store only if you run more than one.

### 6.7 Mail
Dev: Mailpit (same as macro-tracker). Prod: an SMTP URL in env (`SMTP_URL` style is simpler than separate host, port, user and password, but keep [README §8](../README.md#8-configuration-reference) until D13 is decided).

---

## 7. Sync: what to borrow

Ulap's sync ([API §8](api-spec.md#8-sync-endpoints-)) already matches macro-tracker's core: client-made UUIDs, a server-wide sequence (`serverSeq` ≈ macro-tracker's `change_seq`), tombstones, ≤ 200 changes per push, push then pull. Gaps that macro-tracker solved:

| Gap in Ulap | macro-tracker solution | Proposal |
|---|---|---|
| A phone offline for **more than 90 days** pulls with an old `since`, but those tombstones are already purged, so it never learns about the deletes | `resetRequired` in the pull response triggers a full resync | **D6:** pull returns `resetRequired: true` when `since` is older than the purge horizon. The phone then pulls from 0, records the IDs it receives in `pull_seen`, and removes `SYNCED` rows the server didn't return ([Technical plan §8](technical-plan.md#full-resync-resetrequired)). |
| Random UUID v4 ids scatter index inserts | **UUIDv7** (time-ordered) ids from `shared/uuid.ts` | **D7:** generate UUIDv7 in `:shared`. The format is still a UUID, so the API doesn't change. |
| A change that always fails with `INTERNAL` retries forever | After 10 `INTERNAL` failures the op becomes `FAILED` and is reported to Sentry | **D8:** after 10 failed attempts set `sync_error = "FAILED"`, stop retrying that row, and show "1 check-in couldn't back up yet" in Account ([Design spec §7.8](design-spec.md#78-account)) |
| Repositories schedule the worker directly ([README §10](../README.md#10-workflow-example)) | `SyncTriggers` watches the outbox; repositories never trigger sync | **D8:** same, `SyncTriggers` observes `sync_state = PENDING` |
| Retried pushes | `(device_id, client_op_id)` receipts make each op idempotent | **Not needed.** Ulap's push is an upsert by `id` with "newer `updatedAt` wins," so a retry is already harmless. |
| Several entity types | Outbox table + per-entity appliers | **Not needed** in v1 (one synced table). Revisit if more synced data is added. |

Also adopt: WorkManager exponential backoff starting at **30 seconds** (Ulap's API reference says 2 s for HTTP retries, which still applies inside one run).

---

## 8. Infra, backup and deploy

### 8.1 Compose split **[adapt]**

| File | Ulap services | Notes |
|---|---|---|
| `infra/compose.yaml` | `postgres`, `migrate` (one-off Flyway, gate with `service_completed_successfully`), `server`, `scheduler`, `backup` | Exact image tags. `x-hardening`: `read_only`, `cap_drop: [ALL]`, `no-new-privileges`. |
| `infra/compose.dev.yaml` | + `mailpit`; host ports | |
| `infra/compose.prod.yaml` | registry images (`build: !reset null`), CPU and memory limits, rotated logs, loopback-only Postgres, + `cloudflared` | Size the JVM: e.g. `mem_limit: 768m` + `-XX:MaxRAMPercentage=75` |
| `infra/compose.drill.yaml` | tmpfs `postgres`, `restore`, `migrate` | Monthly restore drill |

Each environment's `infra/.env` sets `COMPOSE_PROJECT_NAME` and `COMPOSE_FILE`, so plain `docker compose` targets the right stack (macro-tracker pattern). Use `COMPOSE_PROJECT_NAME=ulap-staging` / `ulap-prod` so it can't collide with macro-tracker's stacks on the same VPS.

### 8.2 Migrations as their own step (D4)
macro-tracker's rule: **"Run the migrations as their own step first"** on deploy, and migrations only go forward (add in one release, drop or rename in a later one). Accepted: auto-migrate runs **in dev only**. In staging and production run `docker compose run --rm migrate` **before** `up -d --wait`.

### 8.3 Backup **[adapt]**
- Image: Alpine + `postgresql17-client`, `age`, `rclone`, `supercronic`, `tzdata`
- Nightly **04:30 Asia/Manila** (after the purge jobs): `pg_dump | age (public key) | rclone` → `incoming/`, then moved to `postgres/<UTC time>.dump.age` on success. Optional heartbeat URL.
- Bucket lifecycle: **30 days** (Ulap's privacy promise in [README §7.1](../README.md#71-server). macro-tracker used 35.)
- The VPS holds only the age **public** key. The private key is in a password manager plus an offline copy.
- Restore drill monthly and before risky migrations. Record the result in `docs/runbooks/restore-drill.md`.

### 8.4 Hosting (D14, accepted)
macro-tracker runs on a VPS behind a token-managed **Cloudflare Tunnel** (no open ports), with images in **GHCR** tagged by the 12-character commit SHA, and rollback by pointing the image variable at the previous tag. Accepted: run Ulap on the **same VPS** as a separate Compose project, with its own tunnel hostname (`api.ulap.app`, `staging-api.ulap.app`), Postgres container and backup bucket. Steps: [Deploy runbook](runbooks/deploy.md).

### 8.5 Local ports (D13)
macro-tracker and another local project already use **5432, 5433, 6379, 6380, 3000, 8025, 9000, 9010, 9011, 45432, 46379**. Ulap's ports (accepted) avoid them:

| Service | Ulap dev port |
|---|---|
| Server | **8080** |
| Postgres | **5434** |
| Mailpit UI / SMTP | **8026 / 1026** |
| Test Postgres | Testcontainers (random port, no fixed one needed) |

---

## 9. CI/CD

Adapted from macro-tracker's three workflows (MT §9). All three keep `concurrency` (CI: one group per ref, a newer push cancels; release: one fixed group, never cancelled), `permissions: contents: read` (release raises it to `write`), and `timeout-minutes`.

| Workflow | Triggers | Steps |
|---|---|---|
| `ci-android.yml` | push to `main` / PR touching `androidApp/**`, `core/**`, `feature/**`, `shared/**`, `build-logic/**`, `gradle/**`, itself | JDK 25 → `setup-gradle` → `./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true` → fail if `core/database/schemas` changed (uncommitted Room schema) → upload reports on failure |
| `ci-server.yml` | push / PR touching `server/**`, `shared/**`, `build-logic/**`, `gradle/**`, itself | JDK 25 → `./gradlew :shared:allTests :server:test spotlessCheck detekt` (Testcontainers needs Docker, which `ubuntu-24.04` runners have) |
| `release-android.yml` | tag `android-v*` | Environment `production` → check `MAJOR.MINOR.PATCH` → `versionCode = 1000 + run_number` → decode upload keystore → `bundleRelease` with `-Pulap.*` → R8 mapping to Sentry (D11) → upload AAB to the Play **internal** track → GitHub release → delete the key |

Contract safety without OpenAPI: because the DTOs live in `:shared`, a breaking change fails to compile in both the app and the server. Add `SyncContractTest` in `:server` that decodes the JSON examples from [API §8](api-spec.md#8-sync-endpoints-) with the shared DTOs, playing the role of macro-tracker's `SpecExamplesTest`.

**Release secrets (names only):** `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `SENTRY_DSN_ANDROID`, `SENTRY_AUTH_TOKEN`. **Variables:** `API_URL_RELEASE`, `SENTRY_ORG`, `SENTRY_PROJECT_ANDROID`.

**Pin every third-party action to a commit SHA**, including the Play upload and GitHub release actions. macro-tracker pinned only some of them (MT §13 item 15).

**Android release flow** (macro-tracker's runbook, adapted): server first → `./gradlew` check green and a phone smoke test → `git tag android-vX.Y.Z` and push → approve the environment → smoke-test the internal build → first time: closed test (Play currently requires **12 testers for 14 days** for new personal developer accounts, as macro-tracker's runbook records. Re-check Play's current rule) → staged rollout 10% → 50% → 100% while watching crash-free sessions → only then raise `MIN_ANDROID_VERSION_CODE`.

---

## 10. Conventions

| Convention | Ulap rule | Source |
|---|---|---|
| Files | UTF-8, LF, final newline, trim trailing whitespace; 2 spaces, `.kt/.kts` 4; `.md` keeps trailing spaces | macro-tracker `.editorconfig` |
| Git attributes | `* text=auto eol=lf`, `*.bat` CRLF, jar/png/webp binary | macro-tracker `.gitattributes` |
| Namespaces | `app.ulap.<core\|feature>.<name>`. App id `app.ulap.android` (`.debug`, `.staging` suffixes). | Adapted |
| Gradle | Typesafe project accessors (`projects.core.model`). `FAIL_ON_PROJECT_REPOS`. Plugin ids in the catalog without versions. | macro-tracker |
| Exact versions | Image tags are exact versions ("dev runs what the VPS runs"). Actions pinned to SHAs. | macro-tracker |
| Migrations | Forward only. Add in one release, drop or rename in a later one. Never edit an applied Flyway file. | macro-tracker deploy runbook, README §15 |
| API changes | `/v1` is additive only. Breaking changes go to `/v2`. | macro-tracker, [API §2](api-spec.md#2-conventions) |
| Error codes | Only add. Never rename or remove. | macro-tracker |
| Code comments cite docs | e.g. `// Sync merge rule (api-spec.md §8.2)` or `(technical-plan.md §8)` | macro-tracker cites ARCHITECTURE sections everywhere |
| Attribution | Near-verbatim adapted code gets `// Adapted from <repo> <file> (<licence>).` Apache-2.0 / MIT only, never GPL or unlicensed. | macro-tracker `reference-coding` skill |
| Mismatch rule | If code and docs disagree, point out the mismatch. Don't silently pick one. | macro-tracker agents |
| Secrets | Real values only in gitignored `infra/.env` and `~/.gradle/gradle.properties`. Never in docs, memory or `gradle.properties`. | macro-tracker |
| Commit messages | Conventional Commits, lower-case imperative subject phrased as a user-visible outcome. Body bullets `- area: what and why`, often ending with a docs bullet. Claude-authored commits end with a `Co-Authored-By:` trailer. | macro-tracker log + `phase-work` |
| Commit scopes (proposed for [README §12](../README.md#12-contributing-guidelines)) | Layer: `android`, `server`, `shared`, `db`, `sync`, `auth`, `infra`, `ci`, `docs`. Feature: `checkin`, `logs`, `reports`, `onboarding`, `account`, `ulap` (mascot). Comma-join when needed: `feat(server,shared)`. One subject per commit. | Adapted |
| Who commits | **The user runs the commit.** Claude doesn't commit unless asked. | macro-tracker `phase-work` |
| Pre-commit hook | macro-tracker used husky + lint-staged (Node). Ulap has no Node, so use a plain `.githooks/pre-commit` running `./gradlew spotlessApply` on staged Kotlin files, enabled with `git config core.hooksPath .githooks`. This also fixes macro-tracker's gap where Kotlin was never formatted on commit (MT §5). | Adapted |

---

## 11. Claude Code setup for Ulap

macro-tracker gitignored all of `.claude/`, so a fresh clone had no agents or skills, while `.mcp.json` was committed with a machine-specific path (MT §13 item 16). Ulap fixes both:

| Path | Commit it? | Why |
|---|---|---|
| `CLAUDE.md` | ✅ | Project rules for every session |
| `.claude/agents/*.md` | ✅ | Shared, no secrets |
| `.claude/skills/*/SKILL.md` | ✅ | Shared, no secrets |
| `.claude/settings.json` | ✅ | Shared permissions (§11.6) |
| `.claude/settings.local.json` | ❌ (gitignore) | Personal hooks and paths |
| `.claude/hooks/*.ps1` | ❌ (gitignore) | Windows paths and the XERJ install location |
| `.mcp.json` | ❌ (gitignore), commit `.mcp.example.json` instead | It contains an absolute `C:\Users\…` path |

### 11.1 `CLAUDE.md` (new; macro-tracker had none)

```markdown
# Ulap: Mood Diary

Offline-first mood diary. Android (Kotlin, Compose) + Ktor server + Kotlin Multiplatform `shared`.

## Read first
- What to build next: PHASES.md (tick boxes only when the Definition of Done is met)
- How it works: docs/technical-plan.md · API: docs/api-spec.md · UI and tone: docs/design-spec.md
- Repo tooling and decisions: docs/ulap-reference.md
- Each topic has one owner doc (README §17). If code and docs disagree, say so. Don't silently pick one.

## Rules
- Business rules live in `:shared` (MoodCatalog, ResponseRules, CheckInValidator), never in ViewModels or routes.
- Never log, print or send journal notes (`note`, `triggerOther`), tokens or passwords.
- Every user-facing string goes in all three strings.xml files (values, values-fil, values-b+ceb).
- App text follows the voice guide: validate → offer. No guilt, no streaks, no judgment words.
- The Hopeless flow and the NCMH 1553 hotline must keep working offline. Changes there need two reviewers.
- Room schema change → migration + MigrationTestHelper test + committed schema JSON.
- Server: routes → service → DAL. Flyway migrations are forward-only.
- Cite the doc section in comments for non-obvious rules, e.g. `(api-spec.md §8.2)`.

## Commands
- Full check: `./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true`
- Format: `./gradlew spotlessApply`
- Server: `docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d` then `./gradlew :server:run`
- Ports and logins: docs/local-services.md

## Git
- Conventional Commits (README §12 scopes). The user runs the commit. Don't commit unless asked.
```

The committed [CLAUDE.md](../CLAUDE.md) is the live version. It grew from this draft and wins where they differ.

### 11.2 Agents **[adapt]**

| Agent | From | What changes for Ulap |
|---|---|---|
| `kotlin-specialist` | macro-tracker, same name | Default agent for **Android and `:shared`** code: ViewModels, Room + migrations, DataStore, SyncEngine/SyncWorker, Ktor Client, Hilt (D2), coroutines, convention plugins, tests. Replace the doc references with Ulap's (`technical-plan.md` §4–§8). Keep the Kotlin rules from §5. Remove Retrofit and OpenAPI codegen. Keep "the owner is new to Kotlin" only if that's still true for you. |
| `ktor-backend` | **new** (macro-tracker's server was TypeScript) | Ktor routes → service → DAL, Exposed, Flyway, JWT and refresh rotation, the sync merge, rate limits, `testApplication` + Testcontainers. Reads `api-spec.md` first. Refuses to log request bodies. |
| `mobile-app-developer` | macro-tracker, same name | Keep startup, size, battery, accessibility (WCAG 2.2 AA, 200% font), permissions UX, Play Console, Data safety. **Remove** barcode, Health Connect and calorie text. **Add** the health-app declaration, the three languages, reduced motion, and the hotline flow. |
| `wellbeing-reviewer` | **new** | Reviews app text and flows against [Design spec §10](design-spec.md#10-voice-and-tone) and [§1](design-spec.md#1-design-principles): validate → offer, no guilt, the stress ≥ 8 simplification, Hopeless handling, same tone in all three languages. Read-only tools (Read, Grep, Glob). |

All agents: read the owning doc section first, keep changes small, run the full check and report failures with output, and name another agent when it fits better.

### 11.3 Skills **[adapt]**

**`phase-work`**, retargeted from ARCHITECTURE §17 to **PHASES.md**:
1. Pick the next unchecked box in [PHASES.md](../PHASES.md) (or the one the user names) and say which. Split an oversized task into 2a, 2b… and do only the first.
2. Fold in a closely related task only if it touches the same files, and say so.
3. Build it following the phase's **Spec:** links. Use `reference-coding` for non-trivial code. Run the full check. Say what can't be verified here (e.g. needs a real phone).
4. Stop. Output a paste-ready Conventional Commit message and a short recap: What changed (with links), Why (doc section), New concepts, Try it yourself, Left open, Next.
5. Tick the box and update the phase's count and status in PHASES.md **only when the Definition of Done is met**. The user commits.
6. Overwrite the `phase-work-style` memory (Status / Next / Open, under ~1.5 KB), then suggest `/clear`.

**`reference-coding`**: search permissively licensed reference repos with XERJ before writing non-trivial code, then fit the result to Ulap's docs (Ulap's docs win).
- Corpora: `android-refs` = `android/nowinandroid`, `android/architecture-samples` (Apache-2.0, same as macro-tracker). `server-refs` = a Ktor sample repo such as `ktorio/ktor-samples` (**check its licence before indexing**). Drop `api-refs` (Hono starter).
- Prefix `ulap` (e.g. `xerj search --prefix ulap "SyncEngine"`).
- Cite the `file:line` of reference passages used. Attribution comment for near-verbatim blocks. Apache-2.0/MIT only.
- Never run `xerj feedback --submit` or `xerj_field_report` unless asked.

### 11.4 Hooks (optional, Windows only)
Copy `start-xerj.ps1`, `refresh-indexes.ps1` and `refresh-indexes-worker.ps1` only if XERJ and graphify are installed on the machine. Change the cache dir to `~/.cache/ulap-index` and the XERJ prefix to `ulap`. Wire them in `.claude/settings.local.json` under `hooks.SessionStart` (`shell: powershell`, `timeout: 30`). Don't copy macro-tracker's graphify git hooks. Run `graphify hook install` in the new repo instead.

### 11.5 MCP
`.mcp.example.json` (committed):
```json
{
  "mcpServers": {
    "xerj": {
      "command": "xerj",
      "args": ["mcp", "--disable-feedback"],
      "env": { "XERJ_URL": "http://localhost:9200", "XERJ_DISABLE_FEEDBACK": "true" }
    }
  }
}
```
Copy it to `.mcp.json` (gitignored) and use the full path to `xerj.exe` if it isn't on `PATH`.

### 11.6 Permissions
macro-tracker had no permissions block anywhere (MT §6). Ulap commits a small [`.claude/settings.json`](../.claude/settings.json) that allows the routine commands and asks for the rest:
```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew *)", "PowerShell(./gradlew *)", "PowerShell(.\\gradlew *)",
      "Bash(docker compose *)", "PowerShell(docker compose *)"
    ],
    "ask": [
      "Bash(git commit *)", "Bash(git push *)",
      "PowerShell(git commit *)", "PowerShell(git push *)"
    ],
    "deny": [
      "Read(.env)", "Read(.env.*)", "Read(!.env.example)", "Read(!.env.*.example)",
      "Read(*.jks)", "Read(*.keystore)"
    ]
  }
}
```
- Rules use the space form (`Bash(cmd *)`, which also matches the bare command). On Windows, Claude Code has a PowerShell tool next to Bash, so each command gets both forms.
- Read-only git commands (`status`, `diff`, `log`) are built into Claude Code's read-only set, so they need no rule.
- A bare filename like `Read(.env)` matches at any depth. The `!` rules carve the committed `.example` templates back out, the same as `.gitignore`.
- Deny rules block Claude's file tools, not every shell command, so they're a guard rail, not a security boundary. Real secrets stay out of the repo (§10).

### 11.7 Memory
Memory is keyed by project path, so start fresh for Ulap:

| File | Type | Content |
|---|---|---|
| `phase-work-style.md` | project | Current PHASES.md position only: Status / Next / Open. Overwritten per task. Under 1.5 KB. |
| `local-services.md` | reference | "Dev links and logins are in docs/local-services.md. Secrets only in infra/.env." |
| `xerj-reference-coding.md` | project | Only if XERJ is used: data dir, corpora, prefix `ulap` |
| `use-graphify-and-xerj.md` | feedback | Only if used: cheapest lookup first (Grep/Read → XERJ → graphify). No `/graphify . --update` unless asked. |

---

## 12. Docs to add

In this order, following macro-tracker's advice (MT §10):

| Doc | When | Based on |
|---|---|---|
| `docs/local-services.md` (added) | The day the first container runs (Phase 1) | macro-tracker's: every URL, port, dev login, where each secret lives, ports to leave alone |
| `docs/runbooks/google-sign-in.md` | Phase 6 | Web client + one Android client per build type (debug, staging, release), with SHA-1s and troubleshooting |
| `docs/runbooks/deploy.md` (added as a plan, D14) | Before the first staging deploy | VPS, tunnel, GHCR images by SHA, migrate → up → `/ready`, rollback |
| `docs/runbooks/backup.md` | Before any real user | age keys, bucket, lifecycle 30 days, heartbeat, key rotation |
| `docs/runbooks/restore-drill.md` | Before any real user | Monthly drill, results log |
| `docs/runbooks/release-android.md` | Before the first Play upload | Upload key, tag release, closed test, staged rollout |
| `docs/release/play-console.md` | Phase 10 | Data safety, health declaration, permissions, checked against the merged manifest |

Each new doc gets the same 📚 nav line and an entry in [README §17](../README.md#17-documentation).

---

## 13. Decision log

Status: **Proposed** (the current docs win) · **Accepted** (update the listed docs in the same PR) · **Rejected**.

| # | Decision | Recommendation | Docs to update if accepted | Status |
|---|---|---|---|---|
| D1 | Android modules: single `androidApp` (current) **or** macro-tracker's `core:*` + `feature:*` | **Multi-module.** You already know it from macro-tracker, and the feature rule keeps database and network code out of screens. | README §5, Technical plan §4 | **Accepted** (2026-11-05) |
| D2 | DI: Koin (current) **or** Hilt | **Hilt in Android, no DI in `:shared`.** Reuses macro-tracker's convention plugin and `@HiltWorker`. `:shared` stays plain constructor injection, so iOS isn't blocked. | Technical plan §3, README §3 | **Accepted** (2026-11-05) |
| D3 | Local encryption: `note` column only (current) **or** full SQLCipher database with a Tink-wrapped passphrase | **SQLCipher**, as macro-tracker already runs it with Room 2.8.5. It protects moods and triggers too, not only notes. The iOS driver question stays open for later. | Technical plan §6, README §14, Phases 2 | **Accepted** (2026-11-05) |
| D4 | Migrations: auto on server startup (current) **or** a separate `migrate` step in staging and prod | **Separate step** (macro-tracker's deploy rule). Auto-migrate stays in dev. | README §6.4, §7.1 | **Accepted** (2026-11-05) |
| D5 | Add `GET /v1/ready` (database check) | **Yes** | API §9 and §14, Technical plan §9 | **Accepted** (2026-11-05) |
| D6 | Add `resetRequired` to `/sync/pull` for cursors older than the 90-day tombstone purge | **Yes.** Without it, a long-offline phone keeps deleted check-ins forever. | API §8.3, Technical plan §8, Phases 7 | **Accepted** (2026-11-05) |
| D7 | UUIDv7 ids from `:shared` | **Yes** (same UUID format, better index locality) | Technical plan §6, API §5.3 | **Accepted** (2026-11-05) |
| D8 | `SyncTriggers` observes pending rows (repositories never schedule sync). Rows become `FAILED` after 10 internal errors. Backoff starts at 30 s. | **Yes** | Technical plan §8, README §10, Phases 7 | **Accepted** (2026-11-05) |
| D9 | Charts: Compose Canvas (current) **or** Vico | **Vico** (used in macro-tracker). Check that it supports the band colors and TalkBack summaries in Design spec §6.15. | Design spec §6.15, Phases 4 | **Accepted** (2026-11-05) |
| D10 | Lint: ktlint tasks (current) **or** Spotless + ktlint + compose-rules + detekt + Android Lint with `warningsAsErrors` | **macro-tracker's set.** `ktlintCheck/Format` become `spotlessCheck/Apply`, plus `detekt`. | README §6.7, §12, Phases 1 | **Accepted** (2026-11-05) |
| D11 | Crash reporting: Sentry (Android + server) with PII off and body scrubbing | **Yes**, with `sendDefaultPii = false` and a `beforeSend` that drops request bodies and any field named `note` | Phases 8, README §14 | **Accepted** (2026-11-05) |
| D12 | Baseline Profile module | **Later** (after v1), when startup is measured | Phases 11 | **Accepted** (2026-11-05) |
| D13 | Dev config: ports 8080 / 5434 / 8026 / 1026, `ulap.apiUrl.<buildType>` in `~/.gradle/gradle.properties`, add a `staging` build type, `.githooks/pre-commit` | **Yes**, to run beside macro-tracker without clashes | README §6, §7.2, §8, §15, Phases 1 | **Accepted** (2026-11-05) |
| D14 | Hosting: same VPS as macro-tracker, separate Compose project, own Cloudflare Tunnel hostnames, images in GHCR by commit SHA | **Yes** | README §7.1, new `docs/runbooks/deploy.md` | **Accepted** (2026-11-05) |

All fourteen decisions were accepted on 2026-11-05. A new decision gets the next number (D15) and starts as **Proposed**.

---

## 14. Setup checklist

Maps onto [PHASES.md, Phase 1](../PHASES.md#phase-1-project-setup). Times are macro-tracker's estimates.

**A. Repo skeleton (≈1 hour)**
1. `git init`. Copy `.editorconfig` and `.gitattributes` (drop the graphify merge-driver line).
2. Write `.gitignore`: `.env`/`.env.*` with `!.env.example`, `*.jks`, `**/build/`, `.gradle/`, `.kotlin/`, `local.properties`, `.idea/`, `.claude/settings.local.json`, `.claude/hooks/`, `.mcp.json`, `graphify-out/`.
3. Add `README.md`, `PHASES.md`, `docs/`, `CLAUDE.md` (§11.1).

**B. Claude Code (≈1 hour)**
4. Copy and adapt the agents and skills (§11.2–§11.3). Commit `.claude/settings.json` and `.mcp.example.json`.
5. Create the `phase-work-style` memory with "Status: Phase 1 · Next: Gradle and modules · Open: decisions D1–D14".

**C. Gradle (≈half a day)**
6. Copy macro-tracker's root Gradle files, `gradle/` (wrapper, catalog, daemon JVM), `config/detekt/detekt.yml` and `build-logic/`. Rename everything `macrotracker` → `ulap` (§4.1). **Remove** any OAuth client id from `gradle.properties`.
7. Create `:shared` (KMP), `:androidApp`, `:server`, and the `core:*` / `feature:*` modules (D1), each with an empty class and a passing test.
8. Run the full check locally (§4.4).

**D. Server and infra (≈half a day)**
9. Ktor skeleton (§6): `AppDeps`, `AppConfig`, plugin order, `GET /v1/health` and `GET /v1/ready` (D5), error mapping.
10. `infra/compose*.yaml` with exact tags and hardening. `migrate` service with Flyway `V1__init.sql`.
11. `docs/local-services.md` with the chosen ports.

**E. CI (≈30 minutes)**
12. Copy and adapt the three workflows (§9). Pin actions to SHAs. Add the secret and variable names.
13. Push. Confirm both CI workflows are green.

---

## 15. Do not copy

| Item from macro-tracker | Reason |
|---|---|
| Everything nutrition-specific: `modules/foods`, `sync/apply/*`, food/goal/log/weight/water schemas, FNRI/PhilFCT importer, `data/`, fixtures | Different domain. PhilFCT data "is not ours to publish." |
| `packages/*`, `pnpm-workspace.yaml`, `turbo.json`, `biome.json`, `.node-version`, husky, `scripts/gradle.mjs` | Ulap has no Node toolchain. Everything is Gradle. |
| Hono, Better Auth, Drizzle, BullMQ, Redis code | Reimplemented as patterns in Ktor (§6) |
| `openapi.json`, `core:network` codegen setup | Shared KMP DTOs replace it |
| Migrations, Room schemas (`schemas/1..7.json`) | Ulap starts fresh at version 1 |
| `docs/DESIGN.md` tokens ("Kinetic Volt"), design-system font and icon licences | Ulap has its own [Design spec](design-spec.md) |
| Privacy page copy, Play Console answers | Legal text and declarations for a different app |
| Google OAuth client ids, `ph.macrotracker.app`, Sentry org/projects, domains, GHCR URLs | Accounts and identities of macro-tracker |
| `.claude/settings.local.json`, memory files, graphify output, git hooks with pinned Python paths | Machine- and project-specific |
| Secrets: `infra/.env*`, `*.jks`, age private key, `~/.gradle/gradle.properties` entries | Never copied, never committed |
| Port numbers | Pick Ulap's own (D13) |

---

## 16. Lessons from macro-tracker's doc drift

macro-tracker's reference lists 16 places where its docs and code drifted apart (MT §13). Rules for Ulap so the same doesn't happen:

1. **One owner per topic** ([README §17](../README.md#17-documentation)). Other docs summarize and link, never restate details.
2. **Update docs in the same PR as the code.** The PR checklist already asks for it.
3. **Don't list planned files as if they exist.** Mark them "(planned)" in layouts (macro-tracker's §15 listed modules and docs that were never created).
4. **Name a library only when it's in the catalog** (Turbine and MockK were named but never added).
5. **Keep one status line per doc** and update it when the doc changes (macro-tracker's ARCHITECTURE stayed "draft v0.1").
6. **The workflow name in a doc must exist in `.github/workflows/`** (`deploy-server.yml` and `nightly.yml` were documented but missing).
7. **Commit the shared Claude Code files, gitignore the personal ones** (§11), so a clone matches the docs.
8. **Run the doc link check** before merging doc changes (every internal link and heading must resolve). It can run as a CI step.
