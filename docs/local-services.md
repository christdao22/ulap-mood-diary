# Local services

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · [Technical plan](technical-plan.md) · [API reference](api-spec.md) · [Design spec](design-spec.md) · [Repo reference](ulap-reference.md)
>
> **This document owns:** every local URL, port and dev login, and where each local setting lives. [README §6](../README.md#6-getting-started-development) owns the setup steps.

Real secrets are never written here. Everything below is a development value for your own computer.

## Start and stop

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres mailpit   # start
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml ps                       # both "healthy"
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml stop                     # stop, keep data
./gradlew :server:run                                                                   # the server, reads .env
```

The Compose project is called `ulap`, so its containers (`ulap-postgres-1`, `ulap-mailpit-1`) never collide with macro-tracker's.

## Services

| Service | Address | Login |
|---|---|---|
| Server | http://localhost:8080/v1 | — |
| Liveness / readiness | http://localhost:8080/v1/health, http://localhost:8080/v1/ready | — |
| PostgreSQL | `localhost:5434`, database `ulap` | user `ulap`, password `ulap` |
| Mailpit inbox (every email the server sends) | http://localhost:8026 | none |
| Mailpit SMTP | `localhost:1026` | none |
| Server tests' PostgreSQL | Testcontainers, a random port per run | made by the test |

From the Android emulator, the server is `http://10.0.2.2:8080/v1` (the emulator's name for your computer). From a phone on USB, run `adb reverse tcp:8080 tcp:8080` and use `http://localhost:8080/v1` ([README §6.5](../README.md#65-run-the-android-app)).

SQL client connection: `jdbc:postgresql://localhost:5434/ulap` (user `ulap`, password `ulap`), or
`docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec postgres psql -U ulap ulap`.

## Ports to leave alone

Other local projects (macro-tracker and one more) use **5432, 5433, 6379, 6380, 3000, 8025, 9000, 9010, 9011, 45432, 46379**. Ulap's ports were picked to avoid them (decision D13). To move one, set `POSTGRES_HOST_PORT`, `MAILPIT_UI_PORT`, `MAILPIT_SMTP_PORT` or `SERVER_HOST_PORT` in your shell before `docker compose`, and change the matching value in `.env`.

## Where each setting lives

| Setting | File | Committed? |
|---|---|---|
| Server variables for `./gradlew :server:run` | `.env` (copy of `.env.example`) | No |
| Android per-machine values (`ulap.apiUrl.debug`, `ulap.googleWebClientId`, signing) | `~/.gradle/gradle.properties` | No (outside the repo) |
| Android SDK path | `local.properties` (Android Studio writes it) | No |
| Staging and production server variables | `infra/.env` on the VPS ([deploy runbook](runbooks/deploy.md)) | No |

## Migrations

- `./gradlew :server:run` applies new Flyway migrations on startup (development only).
- The one-off `migrate` service does the same from the server image, the way staging and production do it (D4):
  ```bash
  ./gradlew :server:buildFatJar
  docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm migrate
  ```

## Reset the local database

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml down -v
```

`-v` deletes the `ulap_postgres-data` volume, so every local row is gone. Do this **only** on your own computer, never on the VPS.
