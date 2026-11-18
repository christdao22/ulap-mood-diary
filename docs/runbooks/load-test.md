# Load test runbook: 1,000 phones syncing at once

Owner of: how the sync load test works, how to run it locally and on staging, and its pass criteria. Tracked as the "Load test" box in [PHASES.md Phase 9](../../PHASES.md#phase-9-testing-and-beta).

Status: **tooling done and run locally; the staging run waits for the first staging deploy** ([deploy.md §4](deploy.md#4-first-staging-deploy)). The staging steps below haven't been run yet, like deploy.md's own steps 3–6.

**Never run this against production.** [SECURITY.md](../../SECURITY.md) promises no automated load testing against `api.ulap.app`, and the seeding tool refuses a production `JWT_ISSUER`.

## What it tests

Each k6 virtual user is one phone signed in as its own account. It does what `SyncEngine` does ([api-spec.md §8.6](../api-spec.md#86-when-the-app-syncs)):

1. **First sync** (a sign-in from guest mode, [api-spec.md §8.5](../api-spec.md#85-first-sign-in-from-guest-mode)): push a backlog of `BACKLOG` check-ins, then a full pull from `since = 0`.
2. **Every `THINK_MIN`–`THINK_MAX` seconds** after that: add one check-in, sometimes edit (20%) or delete (5%) an older one, push everything pending in batches of 200, then pull from the last cursor until `hasMore` is false.

About half the check-ins have a note, so the server's note encryption is part of the load. The pace is far faster than a real phone's (every 10–30 seconds instead of every few hours), so 1,000 test phones stand in for a much bigger real user base.

| Piece | File |
|---|---|
| k6 script | [server/loadtest/sync.js](../../server/loadtest/sync.js) |
| Account seeder (test source set, never in the server image) | [LoadTestMain.kt](../../server/src/test/kotlin/app/ulap/server/loadtest/LoadTestMain.kt), [LoadTestUsers.kt](../../server/src/test/kotlin/app/ulap/server/loadtest/LoadTestUsers.kt) |
| Gradle task | `:server:loadTestUsers` in [server/build.gradle.kts](../../server/build.gradle.kts) |

### Why the accounts are seeded, not signed in

Every route without an access token shares one limit of 120 requests per minute per IP ([api-spec.md §10](../api-spec.md#10-rate-limits)), so 1,000 sign-ins from one test machine would take more than 8 minutes. The seeder writes the accounts straight into the database (a user plus one verified GOOGLE identity whose subject starts with `loadtest-`) and signs their access tokens with the server's own `JWT_SECRET`. Sync limits are per user (60 pushes and 120 pulls per minute), so they don't get in the way.

`server/build/loadtest/users.json` holds live access tokens for the seeded accounts. It's under `build/`, so git ignores it. Delete it after the run, and never paste it anywhere.

## Run it locally

Docker must be running. k6 runs from its Docker image, so there's nothing to install.

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres mailpit
./gradlew :server:run                                      # terminal 1, wait for /v1/ready
./gradlew :server:loadTestUsers --args="seed --users 1000"  # writes server/build/loadtest/users.json

# Git Bash on Windows: MSYS_NO_PATHCONV and pwd -W keep the volume path as C:/…
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)/server:/work" grafana/k6:1.0.0 \
  run --summary-export /work/build/loadtest/summary.json /work/loadtest/sync.js
# macOS / Linux: docker run --rm -v "$PWD/server:/work" --add-host=host.docker.internal:host-gateway grafana/k6:1.0.0 run …

./gradlew :server:loadTestUsers --args="cleanup"            # removes every load-test account and its check-ins
```

A quick check of the script: add `-e VUS=20 -e RAMP=10s -e HOLD=30s -e THINK_MIN=2 -e THINK_MAX=5`.

### Settings (`-e NAME=value`)

| Name | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://host.docker.internal:8080/v1` | The server under test |
| `USERS_FILE` | `/work/build/loadtest/users.json` | Seeded accounts |
| `VUS` | number of accounts | Phones at the peak |
| `RAMP` / `HOLD` | `1m` / `5m` | Time to reach the peak, then time held there. 30 seconds of ramp-down follow. |
| `THINK_MIN` / `THINK_MAX` | `10` / `30` | Seconds between one phone's sync runs |
| `BACKLOG` | `30` | Check-ins each phone uploads on its first sync |

Seed tokens last 120 minutes (`--ttl-min`, up to 720). Seed again if a run starts getting `401 TOKEN_EXPIRED`.

## Run it on staging

Run it from a dev machine, at a quiet time. The VPS also hosts macro-tracker, so watch its health during the run too.

1. Put staging's database and JWT values in a gitignored env file at the repo root, e.g. `.env.loadtest-staging` (`.env.*` is ignored). It needs `APP_ENV`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER` and `JWT_AUDIENCE`, copied from staging's `infra/.env`. Point `DATABASE_URL` at the SSH tunnel from step 2.
2. Open a tunnel to staging's PostgreSQL, which listens on loopback only: `ssh -N -L 5435:127.0.0.1:<postgres port> <vps>`.
3. Seed. Outside development the tool wants the issuer repeated, so a shell pointed at the wrong env file can't seed or wipe anything:
   ```bash
   ./gradlew :server:loadTestUsers -PloadTestEnv=.env.loadtest-staging \
     --args="seed --users 1000 --confirm-issuer https://staging-api.ulap.app"
   ```
4. In Cloudflare, make sure Bot Fight Mode or a WAF rule won't challenge 1,000 sessions from one IP (a skip rule for the tester's IP, for the run only).
5. Run k6 with `-e BASE_URL=https://staging-api.ulap.app/v1`. While it runs, watch the server's CPU and memory (`docker stats`), the database connections, and the server log for `500`s.
6. Clean up, then delete `users.json` and the env file:
   ```bash
   ./gradlew :server:loadTestUsers -PloadTestEnv=.env.loadtest-staging \
     --args="cleanup --confirm-issuer https://staging-api.ulap.app"
   ```
7. Record the results below and tick the PHASES.md box.

## Pass criteria (proposed)

These are the script's k6 thresholds. No doc set a latency target before this, so the owner confirms or changes them.

| Measure | Target |
|---|---|
| Failed requests (`http_req_failed`) | under 1% |
| Push and pull latency | p95 under 500 ms, p99 under 1.5 s |
| Checks (status 200, every change accepted) | over 99% |
| `rejected_changes` | 0. Any rejection means the script or the server's validation is wrong. |
| `reset_required` | 0 |
| Server | no `500`s, no restarts, and memory flat by the end of the hold |

k6 exits with a non-zero code when a threshold fails.

## Results

| Date | Where | Phones | Requests | Failed | Push p95 / p99 | Pull p95 / p99 | Notes |
|---|---|---|---|---|---|---|---|
| 2026-11-18 | Local (dev laptop, `DATABASE_POOL_SIZE=10`) | 1,000 | 35,511 (≈ 90/s) | 0% | 46 / 65 ms | 12 / 17 ms | Default settings. All thresholds passed, no server errors. A baseline, not the staging run. |
| 2026-11-18 | Local, same | 1,000 | 116,783 (≈ 810/s) | 0% | 188 / 373 ms | 181 / 370 ms | Stress pass: `THINK_MIN=1 THINK_MAX=3`, `RAMP=30s HOLD=90s`. Passed; latency climbs as the 10-connection pool queues. |
| 2026-11-18 | Local, same (owner's run, web dashboard) | 1,000 | 35,599 (≈ 85/s) | 0% | all requests: 13 / 46 ms | (not split in the dashboard) | Default settings. Checks 100%, 0 rejected. |
