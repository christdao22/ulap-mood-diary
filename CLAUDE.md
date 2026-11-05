# Ulap: Mood Diary

Offline-first mood diary. Android (Kotlin, Compose) + Ktor server + Kotlin Multiplatform `shared`.

## Read first
- What to build next: PHASES.md (tick boxes only when the Definition of Done is met, including its Phase 1 exception)
- How it works: docs/technical-plan.md · API: docs/api-spec.md · UI and tone: docs/design-spec.md
- Repo tooling and decisions: docs/ulap-reference.md. Decisions D1–D14 (§13) are proposed until marked Accepted. Until then, the other docs win.
- Each topic has one owner doc (README §17). If code and docs disagree, say so. Don't silently pick one.

## Rules
- Business rules live in `shared` (MoodCatalog, ResponseRules, CheckInValidator), never in ViewModels or routes.
- Screens read only from Room. The network moves changes between Room and the server and never drives the UI.
- Never log, print or send journal notes (`note`, `triggerOther`), tokens or passwords outside the sync API.
- Every user-facing string goes in all three strings.xml files (values, values-fil, values-b+ceb). No hard-coded text, colors or sizes in screens.
- App text follows the voice guide (design-spec §10): validate → offer. No guilt, no streaks, no judgment words.
- The Hopeless flow and the NCMH 1553 hotline must keep working offline.
- Auth, sync, encryption and safety changes (🔒 in PHASES.md) need two reviewers. Say so when a task touches them.
- Room schema change → migration + MigrationTestHelper test + committed schema JSON.
- Server: routes → service → DAL. Flyway migrations are forward-only. `/v1` changes are additive only.
- Cite the doc section in comments for non-obvious rules, e.g. `(api-spec.md §8.2)`.

## Commands
From README §6 (D4, D10 and D13 are accepted).
- Build and run all unit tests: `./gradlew build`
- Everything CI checks: `./gradlew spotlessCheck detekt :androidApp:lintDebug test assembleDebug -PwarningsAsErrors=true`
- Format: `./gradlew spotlessApply` (the `.githooks/pre-commit` hook runs it once `git config core.hooksPath .githooks` is set)
- Local services: `docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres mailpit` (PostgreSQL on 5434, Mailpit at http://localhost:8026)
- Server: `./gradlew :server:run`, then check http://localhost:8080/v1/health
- Android: `./gradlew :androidApp:installDebug`
- Gradle isn't set up yet. It arrives with the Phase 1 "Gradle and modules" tasks.

## Agents and skills
- `kotlin-specialist`: Android and `shared` code (the default for Kotlin changes)
- `ktor-backend`: the Ktor server, Exposed, Flyway, auth and sync endpoints
- `mobile-app-developer`: app-wide concerns (performance, accessibility, permissions, hotline flow, Play Console)
- `wellbeing-reviewer`: read-only review of app text and flows against the voice guide and safety rules
- `phase-work` skill: "next task" / "go" in PHASES.md · `reference-coding` skill: XERJ lookups before non-trivial code

## Git
- Conventional Commits (README §12; scopes in ulap-reference §10). The user runs the commit. Don't commit unless asked.
