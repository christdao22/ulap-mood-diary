# Ulap: Mood Diary ☁️

> A gentle, offline-first mood diary for Android. Check in with how you feel, track your stress, and get a little sympathy from **Ulap**, a small cloud who keeps you company.

> 📚 **Ulap docs:** **README** · [Phases](PHASES.md) · [Technical plan](docs/technical-plan.md) · [API reference](docs/api-spec.md) · [Design spec](docs/design-spec.md) · [Repo reference](docs/ulap-reference.md)

[![ci-android](https://github.com/christdao22/ulap-mood-diary/actions/workflows/ci-android.yml/badge.svg)](https://github.com/christdao22/ulap-mood-diary/actions/workflows/ci-android.yml) [![ci-server](https://github.com/christdao22/ulap-mood-diary/actions/workflows/ci-server.yml/badge.svg)](https://github.com/christdao22/ulap-mood-diary/actions/workflows/ci-server.yml) ![status](https://img.shields.io/badge/status-pre--alpha-orange) ![platform](https://img.shields.io/badge/platform-Android-3DDC84) ![kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF) ![server](https://img.shields.io/badge/server-Ktor-087CFA)

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Key features](#2-key-features)
3. [Tech stack](#3-tech-stack)
4. [Architecture at a glance](#4-architecture-at-a-glance)
5. [Folder structure](#5-folder-structure)
6. [Getting started (development)](#6-getting-started-development)
7. [Running in production](#7-running-in-production)
8. [Configuration reference](#8-configuration-reference)
9. [Usage instructions](#9-usage-instructions)
10. [Workflow example](#10-workflow-example)
11. [Testing](#11-testing)
12. [Contributing guidelines](#12-contributing-guidelines)
13. [Localization (English · Filipino · Bisaya)](#13-localization-english--filipino--bisaya)
14. [Privacy, safety and data handling](#14-privacy-safety-and-data-handling)
15. [Troubleshooting](#15-troubleshooting)
16. [Future improvements](#16-future-improvements)
17. [Documentation](#17-documentation)
18. [License](#18-license)

---

## 1. Project overview

**Ulap** (Filipino for *cloud*) is a mood diary focused on **feelings and stress**. Each check-in records:

- **one mood**, chosen from 25 moods in four zones
- **energy** and **stress** levels (1–10), which start at research-informed defaults for that mood
- an optional **trigger** (Work, Family, Money, Health, Relationships, School, Other)
- an optional **journal note**

After saving, the app responds **sympathetically**: it validates the feeling, offers one small optional action (like a 1-minute breathing exercise), and asks *"How stressed do you feel now?"* Over time, reports show patterns: when stress peaks, which triggers matter most, and what actually helps.

**Who it's for:** anyone. The app works fully **without an account**. Signing in (Google or email) only adds **backup and sync** across devices.

**Design idea:** moods are grouped by the circumplex model of affect (pleasant ↔ unpleasant, high ↔ low energy) and ranked by stress:

| Zone | Feeling | Tier | Example moods |
|---|---|---|---|
| 🟥 Red | tense · high energy | 1 (highest stress) | Anxious, Overwhelmed, Angry, Worried |
| 🟦 Blue | heavy · low energy | 2 | Sad, Tired, Lonely, Burned out |
| 🟨 Yellow | bright · high energy | 3 | Excited, Happy, Proud, Motivated |
| 🟩 Green | easy · low energy | 4 (lowest stress) | Calm, Relaxed, Grateful, Content |

> ⚠️ Ulap is a **self-care diary, not a medical or emergency service.** Logging *Hopeless* always shows the **NCMH Crisis Hotline (1553)**.

---

## 2. Key features

### Version 1
- **Mood check-in:** a 3-column mood grid by zone → energy and stress sliders with defaults → trigger chips → journal with a gentle prompt per zone.
- **Sympathetic responses from Ulap:** zone-specific messages and mascot expressions. Fewer choices when stress is high (≥ 8).
- **Small support actions:** a breathing exercise (Red), "something small that helps" (Blue), **Save to good moments** (Yellow and Green).
- **Before/after check:** "How stressed do you feel now?" shows whether the action helped.
- **Logs:** grouped by day, with search, zone filter, edit and delete.
- **Reports (Daily · Weekly · Monthly):** summary tiles, stress trend, zone mix, mood frequency, top triggers, time-of-day pattern, **what helped most**, and an "Ulap noticed…" insight written without judgment.
- **Offline-first:** everything works without internet. Changes sync when online.
- **Accounts (optional):** start as a guest. Sign in with **Google** or **email** to back up and sync.
- **Three languages:** English, Filipino, Bisaya, chosen in the app.
- **Mascot toggle:** turn Ulap off for a plain diary.
- **Privacy controls:** the whole diary is encrypted on the device, and **delete account and all data** in one tap.
- **Safety:** a crisis hotline card for *Hopeless*, and a hotline row always available in Account.

### Design rules we keep
- Every mood earns the **same** credit. We never reward "good" moods.
- **No streaks that reset, no guilt notifications.** Ulap never looks sad because you missed a day.

---

## 3. Tech stack

| Layer | Technology |
|---|---|
| Language | **Kotlin** (Multiplatform) |
| Shared module | Kotlin Multiplatform: domain models, mood catalog, response rules, validation, API DTOs (`kotlinx.serialization`, `kotlinx-datetime`) |
| Android UI | **Jetpack Compose**, Material 3, Navigation Compose |
| Local database | **Room** 2.8 (offline source of truth), encrypted with **SQLCipher**. Its passphrase is wrapped by **Tink** with an Android Keystore key. |
| Background work | **WorkManager** (sync) |
| Settings | **DataStore** |
| Auth (Android) | **Credential Manager** + Google ID library (Sign in with Google), email and password |
| Networking | **Ktor Client** |
| Dependency injection | **Hilt** in Android. `shared` uses plain constructors (no DI library), so iOS isn't blocked. |
| Backend | **Ktor Server** (Netty), JWT auth, rate limiting |
| Database | **PostgreSQL**, accessed with **Exposed**, migrations with **Flyway**, pooled with **HikariCP** |
| Passwords | **Argon2id** |
| Build | **Gradle** (Kotlin DSL) with a version catalog (`gradle/libs.versions.toml`) and `ulap.*` convention plugins in `build-logic/` |
| Code quality | **Spotless** + ktlint, **compose-rules**, **detekt**, **Android Lint** (warnings fail the build in CI) |
| Local infra | **Docker Compose** (PostgreSQL, Mailpit for test emails) |
| Crash reporting | **Sentry** on Android and the server, with personal data off (decision D11) |
| CI | GitHub Actions (build, lint, tests) |

---

## 4. Architecture at a glance

```
┌──────────────────────────────┐        HTTPS / JSON        ┌───────────────────────────┐
│ Android app (Compose)        │  ──── /v1/sync/push ────▶  │ Ktor server               │
│  UI → ViewModel → Repository │                            │  Auth · Users · Sync      │
│  Room DB  (source of truth)  │  ◀─── /v1/sync/pull ─────  │  PostgreSQL               │
│  WorkManager SyncWorker      │                            │                           │
└──────────────┬───────────────┘                            └─────────────┬─────────────┘
               └────────────▶  shared (KMP): models · rules · DTOs  ◀──────┘
```

- **Screens read only from Room.** The network never drives the UI directly.
- **Sync** pushes pending local changes and then pulls server changes since the last `serverSeq`. Conflicts are resolved **last write wins** by `updatedAt`.
- **Deletes are soft** (tombstones), so they can sync. The server purges them after 90 days.

Details: [`docs/technical-plan.md`](docs/technical-plan.md) · [`docs/api-spec.md`](docs/api-spec.md)

---

## 5. Folder structure

Android is split into `core:*` and `feature:*` modules (decision D1 in [ulap-reference §13](docs/ulap-reference.md#13-decision-log)). Phase 1 created every module below, most of them still empty. Items marked *(planned)* arrive with later phases.

```
ulap/
├── README.md                          # start here
├── PHASES.md                          # progress tracker (checkboxes)
├── CLAUDE.md                          # rules for Claude Code sessions
├── .claude/                           # shared agents, skills, settings.json (docs/ulap-reference.md §11)
├── .mcp.example.json                  # copy to .mcp.json (gitignored) for the XERJ MCP server
├── LICENSE
├── CONTRIBUTING.md                    # short file that links to README §12
├── .editorconfig · .gitattributes · .gitignore
├── .githooks/pre-commit               # runs spotlessApply on staged Kotlin (§6.2 turns it on)
├── .github/
│   ├── workflows/                     # ci-android.yml, ci-server.yml, release-android.yml
│   └── pull_request_template.md
├── .env.example                       # copy to .env for ./gradlew :server:run
├── .dockerignore                      # the server image gets only the fat JAR
├── local.properties                   # Android SDK path, written by Android Studio (gitignored)
├── settings.gradle.kts                # includeBuild("build-logic"), every module, typesafe accessors
├── build.gradle.kts                   # plugins apply false; Spotless + detekt for every module
├── gradle.properties                  # build settings only. No URLs, client IDs or secrets.
├── gradle/
│   ├── libs.versions.toml             # all dependency versions live here, and only here
│   └── gradle-daemon-jvm.properties
├── config/detekt/detekt.yml           # detekt overrides, each with a one-line reason
├── build-logic/convention/            # ulap.android.application, .library, .feature, .room, ulap.hilt, ulap.kmp.library …
│
├── docs/
│   ├── technical-plan.md              # architecture, DB, sync, phase summary
│   ├── design-spec.md                 # colors, type, components, Ulap, tone
│   ├── api-spec.md                    # every endpoint, request and response
│   ├── ulap-reference.md              # reuse plan from macro-tracker, Claude Code setup, decision log
│   ├── local-services.md              # local URLs, ports, dev logins
│   └── runbooks/deploy.md             # VPS, tunnel, migrate → up → /ready, rollback
│
├── shared/                            # Kotlin Multiplatform module (no DI, no Android)
│   └── src/
│       ├── commonMain/kotlin/app/ulap/shared/
│       │   ├── model/                 # CheckIn, Mood, Zone, Trigger, SupportType
│       │   ├── catalog/MoodCatalog.kt # 25 moods + default energy/stress
│       │   ├── rules/                 # ResponseRules, StressLabels, Insights
│       │   ├── validation/            # CheckInValidator (used by app AND server)
│       │   └── api/                   # @Serializable DTOs for auth + sync
│       └── commonTest/                # unit tests for rules + validation
│
├── androidApp/                        # the app shell: wires the modules together
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/app/ulap/android/
│       │   ├── UlapApp.kt             # Application class (@HiltAndroidApp)
│       │   ├── MainActivity.kt
│       │   └── navigation/            # NavHost with every feature's routes
│       └── res/xml/                   # locales_config, network_security_config, backup rules
│
├── core/                              # Android library modules, app.ulap.core.<name>
│   ├── common/                        # dispatchers, Result helpers
│   ├── model/  domain/                # wrap or re-export :shared types for Android modules
│   ├── designsystem/                  # theme (Color, Type, Shape), Ulap mascot, MoodTile, TriggerChip, charts…
│   │   └── src/main/res/font/         # Bricolage Grotesque, Figtree + LICENSES/
│   ├── ui/                            # shared screen pieces
│   │   └── src/main/res/
│   │       ├── values/strings.xml          # English (every user-facing string lives here)
│   │       ├── values-fil/strings.xml      # Filipino
│   │       └── values-b+ceb/strings.xml    # Bisaya (Cebuano)
│   ├── database/                      # Room entities, DAOs, UlapDatabase (SQLCipher); schemas/ committed
│   ├── datastore/                     # settings
│   ├── network/                       # Ktor client, AuthApi, SyncApi, client plugins
│   ├── auth/                          # Credential Manager, token store
│   ├── data/                          # repositories (CheckInRepository, SettingsRepository)
│   ├── sync/                          # SyncEngine, SyncWorker, SyncTriggers
│   └── testing/                       # fakes, MainDispatcherRule
│
├── feature/                           # one module per area, app.ulap.feature.<name>
│   ├── onboarding/
│   ├── checkin/                       # PickMood, Details, Response, AfterCheck
│   ├── logs/
│   ├── reports/
│   └── account/
│
├── infra/
│   ├── compose.yaml                   # postgres, migrate (one-off Flyway), server
│   └── compose.dev.yaml               # dev host ports + mailpit
│
└── server/
    ├── build.gradle.kts
    ├── Dockerfile
    └── src/
        ├── main/
        │   ├── kotlin/app/ulap/server/
        │   │   ├── Application.kt     # main() and the Ktor module: plugins, routing
        │   │   ├── AppDeps.kt         # config + database handed to module(), so tests pass their own
        │   │   ├── config/            # AppConfig (reads env vars, names every missing one)
        │   │   ├── common/            # ApiException, error mapping, request ids (rate limits planned)
        │   │   ├── db/                # HikariCP pool, Flyway; Exposed tables (planned)
        │   │   ├── health/            # GET /v1/health, GET /v1/ready
        │   │   ├── migrate/           # main() for the one-off `migrate` service (D4)
        │   │   ├── auth/              # (planned) Google verify, email/password, JWT, refresh
        │   │   ├── users/             # (planned) /me routes
        │   │   ├── sync/              # (planned) /sync/push, /sync/pull, merge rules
        │   │   └── mail/              # (planned) verification + reset emails
        │   └── resources/
        │       ├── logback.xml        # no request bodies in logs
        │       └── db/migration/      # Flyway: V1__init.sql, V2__…
        └── test/                      # route tests with testApplication + Testcontainers
```

**Module rules** (from [ulap-reference §5](docs/ulap-reference.md#5-android-architecture-rules)):
- A `feature:*` module may use `core:common`, `core:model`, `core:domain`, `core:designsystem`, `core:ui` and `:shared`. It never uses another feature, `core:database` or `core:network`. The `ulap.android.feature` convention plugin enforces this.
- Room entities never leave `core:database`, and network DTOs never leave `core:network`. Repositories in `core:data` turn both into `:shared` domain types.
- Each module keeps its tests beside it (`src/test/` for JVM tests, `src/androidTest/` for Room and Compose tests on an emulator).

---

## 6. Getting started (development)

### 6.1 Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **17+** | Only to start Gradle. Gradle then downloads the JDK 25 it runs on (`gradle/gradle-daemon-jvm.properties`). |
| Android Studio | 2026.2.1 or later | Must support AGP 9.4.1. With the Android SDK (platform 37) and an emulator (API 26+). |
| Docker + Docker Compose | Latest | Runs PostgreSQL and Mailpit locally, and the server tests (Testcontainers) |
| Git | any | |
| (Optional) Google Cloud project | – | Needed only to test Sign in with Google |

### 6.2 Clone and set up

```bash
git clone https://github.com/christdao22/ulap-mood-diary.git
cd ulap-mood-diary
cp .env.example .env          # works as is for local development (see §8)
git config core.hooksPath .githooks   # formats staged Kotlin with spotlessApply before each commit
```

Per-machine Android values go in your **user-level** `~/.gradle/gradle.properties` (on Windows `%USERPROFILE%\.gradle\gradle.properties`), never in the repo. See [§8](#8-configuration-reference) for the keys.

### 6.3 Start local services

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres mailpit
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml ps    # both should be "running"
```

| Service | Address |
|---|---|
| PostgreSQL | `localhost:5434` |
| Mailpit web / SMTP | http://localhost:8026 / `localhost:1026` |

These ports differ from the usual 5432 / 8025 / 1025 on purpose, so Ulap can run beside other local projects (decision D13).

Mailpit catches all outgoing emails (verification and reset). Open **http://localhost:8026** to read them.

### 6.4 Run the server

```bash
./gradlew :server:run
```

- Starts on **http://localhost:8080**.
- In development, Flyway runs migrations automatically on startup. Staging and production run them as a separate `migrate` step first ([§7.1](#71-server)).
- Server tests don't need these containers. Testcontainers starts its own PostgreSQL on a random port.
- Check it: `curl http://localhost:8080/v1/health` → `{"status":"ok"}`

To reload automatically while coding:
```bash
./gradlew :server:run -t      # continuous build (Ktor development mode is on in dev)
```

### 6.5 Run the Android app

1. Open the project root in **Android Studio**.
2. Wait for the Gradle sync to finish.
3. Choose the **`androidApp`** run configuration with the **`debug`** build type. (The build types are `debug`, `staging` and `release`.)
4. Start an emulator and press **Run** ▶.

Or from the terminal:
```bash
./gradlew :androidApp:installDebug
```

The debug build talks to `http://10.0.2.2:8080/v1` (the emulator's address for your computer), set in `~/.gradle/gradle.properties`:
```properties
ulap.apiUrl.debug=http://10.0.2.2:8080/v1
```
For a **real phone** on USB, run `adb reverse tcp:8080 tcp:8080` and set `ulap.apiUrl.debug=http://localhost:8080/v1`. Over Wi-Fi, use your computer's local IP instead (for example `http://192.168.1.20:8080/v1`).
(Cleartext HTTP is allowed **only** in the debug build, through `network_security_config`.)

### 6.6 Set up Google sign-in (optional in dev)

1. In Google Cloud Console, create an **OAuth client ID**:
   - **Web application** → copy the client ID → `GOOGLE_WEB_CLIENT_ID` (server `.env`) **and** `ulap.googleWebClientId` (`~/.gradle/gradle.properties`).
   - **Android**, one client per build type → package `app.ulap.android.debug` (or `.staging`) + that build's SHA-1 (`./gradlew :androidApp:signingReport`).
2. Rebuild the app. Without these, the Google button is hidden in debug builds, and email sign-in still works.

### 6.7 Useful Gradle tasks

| Task | What it does |
|---|---|
| `./gradlew build` | Compile everything and run all unit tests |
| `./gradlew :shared:allTests` | Shared module tests |
| `./gradlew :server:test` | Server tests (needs Docker for Testcontainers) |
| `./gradlew test` | JVM unit tests in every module |
| `./gradlew connectedDebugAndroidTest` | Room and Compose UI tests on an emulator |
| `./gradlew spotlessCheck` / `spotlessApply` | Check / fix formatting (ktlint + compose-rules) |
| `./gradlew detekt` | Static analysis |
| `./gradlew :androidApp:lintDebug` | Android Lint |
| `./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true` | Everything CI checks, in one command |
| `./gradlew :server:buildFatJar` | Build the runnable server JAR |
| `./gradlew :androidApp:bundleRelease` | Build the Play Store bundle (`.aab`) |

### 6.8 Optional: code-search tools (XERJ and graphify)

Two local tools help Claude Code find things. Neither is needed to build or run the app.

- **XERJ** is a local search node. It indexes this repo and some open-source reference repos (see `.claude/skills/reference-coding/SKILL.md`).
- **graphify** builds a knowledge graph of the repo in `graphify-out/`.

**Update XERJ** (Windows PowerShell)

```powershell
xerj --version                                   # what you have now
Stop-Process -Name xerj                          # the installer can't replace a running xerj.exe
irm https://xerj.org/get.ps1 | iex               # installs the latest release and checks its SHA-256
xerj --version                                   # confirm
xerj --data-dir $HOME\xerj-data --disable-feedback   # start the node again, in its own terminal
```

- Pin a version with `$env:XERJ_VERSION = "vX.Y.Z"` before the install line.
- Your indices live in `$HOME\xerj-data` and survive the update. The same node may serve other projects, so check that nothing else is using it before you stop it.
- Refresh this project's index after big changes: `xerj autoindex . --prefix ulap-mood-diary`.
- Refresh a reference corpus: `xerj corpus index kmp-ktor-refs`.
- CLI calls need the node's key: `$env:XERJ_API_KEY = (Get-Content "$HOME\xerj-data\admin.key" -Raw).Trim()`.

**Update graphify** (installed with `uv`)

```powershell
uv tool upgrade graphifyy                        # or: uv tool install --upgrade graphifyy
graphify install                                 # refresh the Claude Code skill to match the new version
graphify update .                                # re-extract code files into graphify-out/ (no LLM needed)
```

- Use `pip install --upgrade graphifyy` instead if you installed it with pip.
- `graphify --help` prints a warning when another tool's copy of the skill is older than the package. Run the `graphify install --platform <name>` command it shows.
- After doc changes, run `/graphify . --update` inside Claude Code, because only that re-reads docs.

---

## 7. Running in production

### 7.1 Server

**Where it runs (decision D14):** the same VPS as macro-tracker, as its own Compose project (`ulap-staging`, `ulap-prod`) behind its own Cloudflare Tunnel hostnames (`staging-api.ulap.app`, `api.ulap.app`). Images go to GHCR, tagged by the 12-character commit SHA. Step by step: [docs/runbooks/deploy.md](docs/runbooks/deploy.md). The commands below show the pieces.

**Build the image**
```bash
./gradlew :server:buildFatJar
docker build -t ulap-server:1.0.0 -f server/Dockerfile .
```

`server/Dockerfile` (multi-stage example):
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY server/build/libs/server-all.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
USER 1000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Run**
```bash
docker run -d --name ulap-server -p 8080:8080 --env-file .env.production ulap-server:1.0.0
```

**Production checklist**
- [ ] `APP_ENV=production` (turns off development mode and cleartext)
- [ ] Managed **PostgreSQL** with encrypted storage and **daily encrypted backups** (backups expire within 30 days)
- [ ] **HTTPS** at the load balancer or reverse proxy (TLS 1.2+, HSTS)
- [ ] Strong random `JWT_SECRET` (≥ 64 bytes) and `NOTE_ENCRYPTION_KEY` in a secret manager, never in Git
- [ ] A real SMTP or email API provider for verification and reset emails
- [ ] Liveness check on `GET /v1/health`, readiness check on `GET /v1/ready` (database)
- [ ] Logs **without request bodies, notes or tokens** (`logback.xml` is set this way. Don't change it.)
- [ ] Rate limits on (default on)
- [ ] Monitoring: error rate, p95 latency, database connections

**Migrations:** in staging and production, run them as their own step **before** starting the new server: `docker compose run --rm migrate`, then `docker compose up -d --wait`. The server doesn't migrate on startup there (only in development). For zero-downtime releases, only ship **backward-compatible** migrations (add columns, then remove them in a later release).

### 7.2 Android release

1. Create a release keystore (keep it safe and **never commit it**).
2. Add the signing config to `~/.gradle/gradle.properties` (CI passes the same keys as `-P` flags from its secrets):
   ```properties
   ulap.upload.storeFile=/path/to/ulap-release.jks
   ulap.upload.storePassword=…
   ulap.upload.keyAlias=ulap
   ulap.upload.keyPassword=…
   ```
3. Set `ulap.apiUrl.release=https://api.ulap.app/v1` (placeholder domain, same as the [API reference](docs/api-spec.md#2-conventions)). The `staging` build type uses `ulap.apiUrl.staging`.
4. Register the **release SHA-1** (and the Play App Signing SHA-1) with your Android OAuth client.
5. Build: `./gradlew :androidApp:bundleRelease` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`
6. Upload to **Google Play Console** → internal or closed testing first.
7. Fill in the **Data safety** form (health-related data, encrypted in transit, deletable, not shared or sold) and the account-deletion URL.

---

## 8. Configuration reference

### Server (`.env`)

| Variable | Example | Required | Description |
|---|---|---|---|
| `APP_ENV` | `development` | ✅ | `development` or `production` |
| `PORT` | `8080` | | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5434/ulap` | ✅ | JDBC URL |
| `DATABASE_USER` | `ulap` | ✅ | |
| `DATABASE_PASSWORD` | `ulap` | ✅ | |
| `DATABASE_POOL_SIZE` | `10` | | HikariCP max pool |
| `JWT_SECRET` | `<64+ random bytes>` | ✅ | Signs access tokens |
| `JWT_ISSUER` | `https://api.ulap.app` | ✅ | `iss` claim |
| `JWT_AUDIENCE` | `ulap-app` | ✅ | `aud` claim |
| `ACCESS_TOKEN_TTL_MIN` | `15` | | Access token lifetime |
| `REFRESH_TOKEN_TTL_DAYS` | `60` | | Refresh token lifetime |
| `GOOGLE_WEB_CLIENT_ID` | `123…apps.googleusercontent.com` | for Google | Audience for Google ID tokens |
| `NOTE_ENCRYPTION_KEY` | `<base64 32 bytes>` | ✅ | AES-256-GCM key for `note` / `triggerOther` |
| `NOTE_ENCRYPTION_KEY_ID` | `k1` | ✅ | Allows key rotation |
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1026` | ✅ | Mailpit in dev |
| `SMTP_USER` / `SMTP_PASSWORD` | | prod | |
| `MAIL_FROM` | `Ulap <hello@ulap.app>` | ✅ | |
| `APP_LINK_BASE` | `https://ulap.app` | ✅ | Base URL for verify and reset links |
| `MIN_ANDROID_VERSION_CODE` | `1` | | Below this → `426 APP_UPDATE_REQUIRED` |
| `SENTRY_DSN` | `https://…@….ingest.sentry.io/…` | | Crash reports (D11). Empty sends nothing. |

### Android (`~/.gradle/gradle.properties`, never committed)

User-level Gradle properties, read with `providers.gradleProperty`. CI passes them as `-P` flags. Full list: [ulap-reference §4.3](docs/ulap-reference.md#43-build-types-and-gradle-properties).

| Key | Example | Description |
|---|---|---|
| `ulap.apiUrl.debug` | `http://10.0.2.2:8080/v1` | API base URL for the `debug` build |
| `ulap.apiUrl.staging` / `ulap.apiUrl.release` | `https://api.ulap.app/v1` | API base URL for `staging` / `release` |
| `ulap.googleWebClientId` | `123…apps.googleusercontent.com` | Web client ID used by Credential Manager |
| `ulap.upload.*` | see [§7.2](#72-android-release) | Release signing (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `ulap.sentryDsn` | `https://…@….ingest.sentry.io/…` | Crash reports (D11). Empty sends nothing. |

---

## 9. Usage instructions

### For users

1. **First launch:** pick a language (English, Filipino or Bisaya) → meet Ulap → read the short privacy note → tap **Start my first check-in**. No account needed.
2. **Check in:**
   - Tap the mood closest to how you feel.
   - Adjust **Energy** and **Stress** if the defaults don't fit (**Reset to default** brings them back).
   - Pick what **triggered** it (optional).
   - Write in the **journal** (optional). The hint helps you start.
   - Tap **Save**.
3. **Ulap responds:** read the message. Try the small action if you like (breathing, small helps, or save a good moment).
4. **Check again:** for Red and Blue moods, move the slider to show how stressed you feel **now**, then tap **Check in**.
5. **Logs:** search, filter by zone, and tap ⋯ to **edit** or **delete** an entry.
6. **Reports:** switch between **Daily, Weekly and Monthly**. Read "Ulap noticed…" for patterns, and see **What helped most**.
7. **Back up and sync (optional):** Account → **Continue with Google** or **Use email instead**. Your existing check-ins upload automatically.
8. **Settings:** turn Ulap on or off, change the language, sign out, or **Delete account and all data**.

### For developers

- Add a mood → `shared/catalog/MoodCatalog.kt` + strings in all three `strings.xml` + a test in `commonTest`. The server picks it up automatically, because it uses the same catalog.
- Change a response message → `strings.xml` keys `resp_<zone>_title` / `resp_<zone>_body` (all three languages).
- Change response behavior (which buttons, when the check-in slider appears) → `shared/rules/ResponseRules.kt` + its tests.
- Add an API field → the DTO in `shared/api/`, a Flyway migration, the Room migration, and an update to `docs/api-spec.md`.

---

## 10. Workflow example

### A. A check-in from start to sync

```
User taps "Worried"
  → Details screen: energy 6, stress 8 (defaults), trigger WORK, note typed
  → Save
      CheckInRepository.save()
        • Room insert: id = UUIDv7, sync_state = PENDING, updated_at = now
          (the whole database is encrypted with SQLCipher)
        • The repository doesn't schedule anything
  → SyncTriggers sees a new PENDING row
        • SyncWorker requested in 5 seconds (network required)
  → Response screen (ResponseRules: zone RED, stress ≥ 8 → one button only)
      "That's a lot to carry right now." + [Breathe with me · 1 minute]
  → User breathes, sets "now" to 5 → Check in
      Room update: stress_after = 5, support_used = BREATHE, PENDING
  → After screen: "Your stress went from 8 to 5."

Network available → SyncWorker runs
  → POST /v1/sync/push   { changes: [ … ] }
  ← { accepted: [{ id, serverSeq: 1042, status: APPLIED }] }
      Room: sync_state = SYNCED, server_seq = 1042
  → GET /v1/sync/pull?since=1037
  ← { changes: [ … from other devices … ], nextSince: 1042, hasMore: false }
      Room merge → DataStore.lastPullSeq = 1042
```

### B. Developer workflow: adding a feature

```bash
# 1. Start from an up-to-date main
git checkout main && git pull
git checkout -b feat/logs-date-filter

# 2. Run the local stack
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres mailpit
./gradlew :server:run            # terminal 1
# run androidApp from Android Studio  # terminal 2

# 3. Code + tests
./gradlew spotlessApply          # the pre-commit hook also does this
./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true

# 4. Commit with Conventional Commits
git add .
git commit -m "feat(logs): filter logs by date range"

# 5. Push and open a PR
git push -u origin feat/logs-date-filter
```

---

## 11. Testing

| Layer | What to test | Tools |
|---|---|---|
| `shared` | Mood catalog, ResponseRules, validation, insight text | `kotlin.test` |
| Android data | Room DAOs, report queries (local day across midnight), SyncEngine merge, full resync, FAILED after 10 errors | JUnit, Room in-memory, Turbine, MockK |
| Android UI | Check-in flow, language switch, mascot off, TalkBack labels | Compose UI Test |
| Server | Auth flows, push/pull merge, rate limits, delete account | Ktor `testApplication`, Testcontainers (Postgres) |
| End-to-end | Two emulators editing the same entry offline, then syncing | Manual checklist (`docs/technical-plan.md` §15) |

**Rule:** every bug fix comes with a test that fails before the fix.

Test tasks and their status are tracked in [PHASES.md](PHASES.md). Full test case lists: [Technical plan §15](docs/technical-plan.md#15-testing-checklist) and [API reference §15](docs/api-spec.md#15-testing-checklist).

---

## 12. Contributing guidelines

*This section is the contributing guide. `CONTRIBUTING.md` only links here.*

Thank you for helping make Ulap kinder and better. 💙

### Ground rules
- Be respectful. This app is about people's feelings, and so is our collaboration.
- **User wellbeing comes first.** No dark patterns: no streak resets, guilt messages, ads, or sharing user data.
- Never log, print, or send journal notes or tokens anywhere outside the sync API.

### How to contribute
1. **Open an issue first** for anything bigger than a small fix. Describe the problem, the proposal, and any screenshots.
2. **Branch** from `main`:
   - `feat/<short-name>` new feature
   - `fix/<short-name>` bug fix
   - `docs/<short-name>` documentation
   - `chore/<short-name>` build, CI, dependencies
3. **Commit** with [Conventional Commits](https://www.conventionalcommits.org/):
   `feat(checkin): add reset-to-default link` · `fix(sync): keep newer local edit on pull`
4. **Open a pull request** that includes:
   - What changed and why
   - Screenshots or a short video for UI changes (in English **and** one other language)
   - Tests added or updated
   - Docs updated (`docs/*.md`) when behavior or the API changes

### Code style
- Kotlin style is enforced by **Spotless** with ktlint and compose-rules (`./gradlew spotlessApply` fixes it, and the `.githooks/pre-commit` hook runs it for you).
- **detekt** and **Android Lint** must pass. CI treats warnings as errors. A detekt override goes in `config/detekt/detekt.yml` with a one-line reason.
- Compose: stateless composables with state passed in. Preview functions for every component.
- No hard-coded user-facing strings. Everything goes in `strings.xml` (all three languages).
- No hard-coded colors or sizes in screens. Use the theme tokens (`docs/design-spec.md`).
- Keep business rules in `shared/`, not in ViewModels or routes.

### Pull request checklist
- [ ] `./gradlew build` passes
- [ ] New strings added to `values/`, `values-fil/` and `values-b+ceb/`
- [ ] TalkBack labels for new interactive elements
- [ ] Touch targets ≥ 48dp
- [ ] Tone follows the voice guide (validate → offer, no judgment words)
- [ ] No sensitive data in logs
- [ ] Docs updated if needed (follow the rules in [§17](#17-documentation))
- [ ] Related task ticked in [`PHASES.md`](PHASES.md)

### Reviews
- At least **one approval** before merging. Use **squash merge** into `main`. It's the only merge type the repository allows.
- Changes to auth, sync, encryption or safety features (the hotline) need **two approvals**. GitHub can't require this per folder, so the PR template's 🔒 box is the check.
- **Rulesets on `main`** (Settings → Rules → Rulesets):
  - `main`: no force pushes and no deleting `main`. Nobody can bypass it, not even the admin.
  - `main + admin bypass`: changes go through a pull request, and the repository admin can bypass it. Required status checks get added here with CI ([PHASES.md, Phase 1](PHASES.md#phase-1-project-setup--nov-2026)).
- **While Ulap has one maintainer:** GitHub never counts your own approval on your own pull request. So the pull-request ruleset requires 0 approvals, and the admin can bypass it. That lets Phase 1 setup work go straight to `main` ([Definition of Done](PHASES.md#definition-of-done)). Raise it to 1 approval when a second reviewer joins.

---

## 13. Localization (English · Filipino · Bisaya)

- Languages: `en` (default), `fil`, `ceb` (Bisaya/Cebuano).
- The user picks the language in onboarding or Account. It's applied with the per-app language API (`AppCompatDelegate.setApplicationLocales`).
- Mood codes and enums stay **English in data and the API** (`worried`, `WORK`). Only the display text is translated.
- **Translate meaning, not word for word.** Keep the same gentle tone. Everyday Taglish or Bislish is fine where it's more natural.
- Every Filipino and Bisaya change needs a **native-speaker review** before merging.
- Layouts must handle text up to **40% longer** than English.
- First-draft mood names: `docs/technical-plan.md`, Appendix A.

---

## 14. Privacy, safety and data handling

- Mood and journal data is **sensitive personal information**. Handle it in line with the **Philippine Data Privacy Act of 2012 (RA 10173)**: privacy notice, consent at onboarding, a clear purpose, and **NPC** registration where required.
- **On the device:** the whole Room database (moods, stress, triggers and notes) is encrypted with **SQLCipher**. Its random passphrase is wrapped by Tink with an Android Keystore key and kept out of backups. Android cloud backup of the database is off. If the Keystore key is lost, the database starts empty and synced check-ins come back from the server ([Technical plan §6](docs/technical-plan.md#encryption-of-the-phone-database)).
- **On the server:** encrypted disk and backups, plus application-level AES-256-GCM for notes. Every query is filtered by the token's user ID.
- **Crash reports** go to Sentry with `sendDefaultPii = false`. A `beforeSend` hook drops request bodies and any `note` or `triggerOther` field, so a crash report never carries journal text, emails or tokens (decision D11).
- **No ads, no analytics on journal content, no selling or sharing data.**
- **Deletion:** Account → *Delete account and all data* deletes everything on the server and wipes the phone.
- **Safety:** *Hopeless* shows the **NCMH Crisis Hotline 1553** (the call button opens the dialer). The hotline is hard-coded so it works offline.
- Report security issues **privately** to `security@<your-domain>`. Please don't open public issues for vulnerabilities.

---

## 15. Troubleshooting

| Problem | Fix |
|---|---|
| App can't reach the server on the emulator | Use `http://10.0.2.2:8080/v1`, not `localhost`. Check that the server is running. |
| App can't reach the server on a real phone | On USB: `adb reverse tcp:8080 tcp:8080`. On Wi-Fi: set `ulap.apiUrl.debug` to your computer's LAN IP, keep both devices on the same network, and allow port 8080 in the firewall. |
| Google button missing | `ulap.googleWebClientId` isn't set in `~/.gradle/gradle.properties` |
| Google sign-in fails with "No credentials" | Register the debug or release **SHA-1** with your Android OAuth client. Sign in to a Google account on the emulator. |
| Server: `INVALID_GOOGLE_TOKEN` | `GOOGLE_WEB_CLIENT_ID` on the server must match the app's `serverClientId` |
| Flyway "checksum mismatch" | Never edit an applied migration. Add a new `V{n}__…sql`. In local dev only: `docker compose -f infra/compose.yaml -f infra/compose.dev.yaml down -v` to reset. |
| Server tests fail to start | Docker must be running (Testcontainers) |
| Verification email never arrives (dev) | Open Mailpit at http://localhost:8026 |
| "Port is already allocated" when starting containers | Another project uses that port. Ulap uses 5434, 8026 and 1026 to avoid the usual ones. Find the clash with `docker ps`. |
| Commit doesn't format Kotlin | Turn on the hook once per clone: `git config core.hooksPath .githooks` |
| Sync stuck as "pending" | Look for `sync_error` on the row: a rejection reason, or `FAILED` after 10 internal errors. Account → Try again sends it again. Check the server logs by `X-Request-Id`. |
| Wrong language after switching | Restart the activity. Make sure the strings exist in that `values-*` folder. |

---

## 16. Future improvements

Tracked in [PHASES.md, Phase 11](PHASES.md#phase-11-after-v1-backlog).

- 🍎 **iPhone app:** move Room and sync into `shared`, a Compose Multiplatform or SwiftUI UI, **Sign in with Apple**
- 🔔 **Gentle reminders:** push notifications with quiet hours, never counting missed days
- 📤 **Export:** CSV and a monthly PDF report
- 🌙 **Dark mode:** a second token palette, already planned in the design spec
- ☀️ **Good moments resurfacing:** on harder days (not *Hopeless*, not stress 9–10), Ulap gently shows a saved good moment
- 🔑 **Passkeys** through Credential Manager
- 📈 **Richer insights:** mood vs. sleep, and day-of-week patterns over months
- 🧩 **Home-screen widget** for one-tap check-ins
- ♿ **Accessibility audit** with real TalkBack users
- 🌏 **More languages** (e.g. Hiligaynon, Ilocano)

---

## 17. Documentation

Each topic has **one owner document**. Other documents summarize it and link to it. If two documents ever disagree, the owner wins, and the other one gets fixed in the same pull request.

| Document | Owns (source of truth for) | Read it when… |
|---|---|---|
| **README.md** (this file) | Setup, commands, configuration, contributing rules | You're starting or running the project |
| [PHASES.md](PHASES.md) | Build order, task checklists, progress | You want to know what's next or what's done |
| [docs/technical-plan.md](docs/technical-plan.md) | Scope, architecture, domain model and response rules, database schemas, sync design, reports logic, languages, privacy, mood names and message keys (appendices) | You're designing or changing how something works |
| [docs/api-spec.md](docs/api-spec.md) | Endpoints, headers, request and response shapes, errors, tokens, rate limits | You're building or calling the server |
| [docs/design-spec.md](docs/design-spec.md) | Colors, type, components, screen layouts, the Ulap mascot, motion, voice and tone, accessibility | You're building UI or writing app text |
| [docs/local-services.md](docs/local-services.md) | Local URLs, ports and dev logins | You need an address or a password for the local stack |
| [docs/runbooks/deploy.md](docs/runbooks/deploy.md) | Getting the server onto the VPS, deploying and rolling back | You're deploying staging or production |
| [docs/ulap-reference.md](docs/ulap-reference.md) | What Ulap reuses from macro-tracker, repo tooling, Claude Code setup, and the decision log (D1–D14, all accepted) | You're setting up the repo or tooling, or deciding a new change |
| Mockup canvas | Clickable visual reference | https://claude.ai/artifact/Ecjc7A7vEuqp2xtL3mbaiy |

**Keeping the docs in sync**
- Change an endpoint → update `api-spec.md` first, then the summary in `technical-plan.md` §9.
- Change a database column → `technical-plan.md` §6–§7, the field table in `api-spec.md` §5.3, and add a migration.
- Change response behavior → `technical-plan.md` §5, then the screen in `design-spec.md` §7.
- Change app text → `technical-plan.md` Appendix B (English keys), then all three `strings.xml` files.
- Accept a decision in `ulap-reference.md` §13 → update the docs listed in its row in the same PR, then mark it **Accepted**.
- Finish a task → tick it in `PHASES.md`.

---

## 18. License

**All rights reserved** © 2026 Christian Daohog. See [LICENSE](LICENSE).

The repository is public so people can read it. Copying, modifying or distributing the code, or using it in another product, needs the owner's written permission. The only exception is the viewing and forking that GitHub's Terms of Service allow.

If the project opens up later, these are the options:
- **MIT** or **Apache-2.0** if you want anyone to reuse the code freely (Apache-2.0 adds patent protection).
- **AGPL-3.0** if you want anyone who runs a modified server to share their changes.

Going from "all rights reserved" to an open license is easy. Taking back an open license you've already granted is not.

The **name "Ulap," the mascot artwork and the app icon** are brand assets. [LICENSE](LICENSE) reserves them separately, so they stay reserved even if the code is open-sourced, and forks can't publish under the same name and character.

Third-party fonts (**Bricolage Grotesque**, **Figtree**) are under the **SIL Open Font License 1.1**. Include their license files in `core/designsystem/src/main/res/font/LICENSES/`.

---

<p align="center">Made with care ☁️ · <em>Any feeling is okay here.</em></p>
