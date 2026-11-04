---
name: reference-coding
description: Before writing non-trivial code for Ulap (a Room DAO or entity, SyncWorker/sync engine, repository, Compose screen or navigation, Koin module, Ktor route, JWT/auth plugin, Exposed table, Flyway migration, KMP shared module, test setup), search the indexed open-source reference repos with XERJ to see how they solved the same problem, then write code that fits docs/technical-plan.md.
---

# Reference coding with XERJ

A local XERJ node indexes this project and permissively licensed reference repos. Look at how they solved a problem before writing it here.

## Corpora

| Corpus | Repos | Licence | Use for |
|---|---|---|---|
| `kmp-ktor-refs` | `ktorio/ktor-documentation`, `JetBrains/kotlinconf-app`, `touchlab/KaMPKit` | Apache-2.0 | Ktor server (JWT auth, rate limiting, ContentNegotiation, StatusPages, `testApplication`), Exposed, Koin, KMP shared module layout, Ktor client, Compose Multiplatform |
| `android-refs` | `android/nowinandroid`, `android/architecture-samples` | Apache-2.0 | Room, offline-first sync (`SyncWorker`, `Synchronizer`), WorkManager, Compose, ViewModel tests with fakes, convention plugins. Both use Hilt, and Ulap uses Koin. |

This project's own docs are indexed under the prefix `ulap-mood-diary`. The repo has no source code yet, so it holds the README and `docs/*.md` (technical plan, design spec, API spec).

## Workflow

1. **Search first.** Retrieval is lexical, so use identifiers the reference code would contain:
   ```powershell
   xerj code kmp-ktor-refs "install Authentication jwt verifier validate" -k 5
   xerj code android-refs "SyncWorker CoroutineWorker doWork Synchronizer" -k 5
   xerj search --prefix ulap-mood-diary "serverSeq push pull"      # our own docs
   xerj def --prefix ulap-mood-diary <Name>
   ```
   Through MCP, the same calls are `xerj_code_search`, `xerj_search` and `xerj_def`.
2. **Compare with the plan.** `docs/technical-plan.md` and `docs/api-spec.md` win wherever they disagree with a reference. Say which parts you adopted and which you changed. Translate Hilt to Koin where needed.
3. **Cite.** Cite the `file:line` of any reference passage you relied on, in your explanation to the user.
4. **Licences.** Adapting Apache-2.0 or MIT code is allowed. If you copy a substantial block nearly verbatim, keep a short attribution comment, e.g. `// Adapted from ktorio/ktor-documentation (Apache-2.0)`. Never pull code from unlicensed or GPL repos into this project.
5. **No hit, or code you already know well:** say so and write the code normally. Retrieval adds cost for well-known APIs.

## Prerequisites

The node must be running. It isn't started automatically. In a separate terminal, run:
```powershell
xerj --data-dir $HOME\xerj-data --disable-feedback
```
The node requires auth. For CLI calls, run:
```powershell
$env:XERJ_API_KEY = (Get-Content "$HOME\xerj-data\admin.key" -Raw).Trim()
```
Re-index after big changes: `xerj autoindex . --prefix ulap-mood-diary` (project) or `xerj corpus index <name>` (references). Index one corpus at a time on a small machine.

Do not run `xerj feedback --submit` or the `xerj_field_report` tool unless the user asks. Both send data to a third party.
