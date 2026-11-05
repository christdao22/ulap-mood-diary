# Deploy runbook: staging and production on the VPS

> 📚 **Ulap docs:** [README](../../README.md) · [Phases](../../PHASES.md) · [Technical plan](../technical-plan.md) · [API reference](../api-spec.md) · [Design spec](../design-spec.md) · [Repo reference](../ulap-reference.md)
>
> **This document owns:** how the server gets onto the VPS, how a new version is deployed, and how it is rolled back (decision D14 in [ulap-reference §13](../ulap-reference.md#13-decision-log)).

Status: **plan, not yet run.** The first staging deploy is a [Phase 9](../../PHASES.md#phase-9-testing-and-beta) task. Three pieces this runbook needs don't exist yet and arrive before that deploy: `infra/compose.prod.yaml` (registry images, limits, loopback-only Postgres, `cloudflared`), `infra/.env.prod.example`, and the backup service with its own runbook. Until they exist, steps 3–6 are the intended procedure, not a tested one.

## How it fits together

```
phone ──HTTPS──▶ Cloudflare edge ◀──outbound tunnel── cloudflared ──▶ server:8080 ──▶ postgres
                 (TLS, DNS)                           └──── one Compose project on the VPS ────┘
```

- Ulap runs on the **same VPS as macro-tracker**, as its **own Compose project**, so containers, networks and the database volume stay separate from macro-tracker's.
- The VPS opens **no** inbound ports except SSH. `cloudflared` dials out to Cloudflare, and Cloudflare sends requests for the environment's hostname back down that connection.
- Each environment has its own checkout and its own `infra/.env`. That file sets `COMPOSE_PROJECT_NAME` and `COMPOSE_FILE`, so plain `docker compose` from that checkout's `infra/` folder always targets the right stack ([ulap-reference §8.1](../ulap-reference.md#81-compose-split-adapt)).

| | Staging | Production |
|---|---|---|
| Directory | `/srv/ulap/staging` | `/srv/ulap/production` |
| `COMPOSE_PROJECT_NAME` | `ulap-staging` | `ulap-prod` |
| Hostname | `staging-api.ulap.app` (placeholder domain) | `api.ulap.app` (placeholder domain) |
| `APP_ENV` | `production` | `production` |
| Cloudflare tunnel | `ulap-staging` | `ulap-prod` |
| Deployed from | any commit on `main` | tagged releases `server-v*` that ran on staging first |

## 1. Prepare the VPS (once)

macro-tracker's deploy runbook (`docs/runbooks/deploy.md` in that repo) already set up the VPS (SSH-key-only `deploy` user, `ufw` allowing only SSH, Docker Engine with the Compose plugin, `docker login ghcr.io` with a `read:packages` token). Ulap reuses that machine and needs only:

```sh
sudo mkdir -p /srv/ulap && sudo chown deploy: /srv/ulap
```

Check that the machine has room for one more JVM and Postgres: `free -h` and `df -h`. Ulap's server is sized at `mem_limit: 768m` with `-XX:MaxRAMPercentage=75` ([ulap-reference §8.1](../ulap-reference.md#81-compose-split-adapt)).

## 2. Create the Cloudflare tunnel (once per environment)

1. **Zero Trust → Networks → Tunnels → Create a tunnel → Cloudflared.** Name it `ulap-staging`.
2. Choose Docker and copy the token after `--token`. It becomes `TUNNEL_TOKEN` in `infra/.env`. Compose runs `cloudflared`, so skip the install command.
3. Add a **public hostname**: subdomain `staging-api`, the domain, service `HTTP`, URL `server:8080`.
4. Once per domain: *Always Use HTTPS* on, *Minimum TLS Version* 1.2, *Bot Fight Mode* **off** (the app's HTTP client can't solve a challenge). Turn on HSTS for production only, after production works ([README §7.1](../../README.md#71-server)).

## 3. Build and push the image

On your machine (PowerShell), from a clean checkout of a commit that is already pushed:

```powershell
git status                                   # must be clean
$tag = git rev-parse --short=12 HEAD
./gradlew :server:buildFatJar
docker build -f server/Dockerfile --label "org.opencontainers.image.source=https://github.com/christdao22/ulap-mood-diary" -t ghcr.io/christdao22/ulap-server:$tag .
docker push ghcr.io/christdao22/ulap-server:$tag
```

`SERVER_IMAGE` in `infra/.env` names this tag. The `migrate` service runs the **same image** with a different main class, so the schema and the code always come from one commit.

## 4. First staging deploy

```sh
git clone https://github.com/christdao22/ulap-mood-diary.git /srv/ulap/staging
cd /srv/ulap/staging && git checkout <sha>
cp infra/.env.prod.example infra/.env && chmod 600 infra/.env
nano infra/.env                              # every value; openssl rand -base64 64 for JWT_SECRET
cd infra
docker compose config --quiet                # names any missing variable
docker compose pull
docker compose run --rm migrate              # Flyway, as its own step (D4)
docker compose up -d --wait
curl -fsS https://staging-api.ulap.app/v1/ready   # {"status":"ready"}
```

## 5. Routine deploy

```sh
cd /srv/ulap/staging
git fetch && git checkout <sha>
sed -i "s|^SERVER_IMAGE=.*|SERVER_IMAGE=ghcr.io/christdao22/ulap-server:<sha>|" infra/.env
cd infra
docker compose pull
docker compose run --rm migrate              # while the old server keeps serving
docker compose up -d --wait                  # swap in the new server
curl -fsS https://staging-api.ulap.app/v1/ready
```

Migrations run **first and on their own**. If one fails, the deploy stops and the old server is still up. The server never migrates on startup outside development ([README §7.1](../../README.md#71-server)).

## 6. Roll back

Point `SERVER_IMAGE` at the previous tag and run `docker compose up -d --wait`. Migrations only go forward, so a rollback works only if the old image still runs on the new schema. That's why columns are added in one release and dropped in a later one ([ulap-reference §10](../ulap-reference.md#10-conventions)).

## 7. Production

Steps 2, 4 and 5 with the production column. Before the first real user:

- Backups run nightly with a heartbeat, and a restore drill passed against them (backup and restore-drill runbooks, planned in [ulap-reference §12](../ulap-reference.md#12-docs-to-add)).
- An external uptime check on `https://api.ulap.app/v1/ready`.
- HSTS on.

## Everyday commands

From the environment's `infra/` folder.

| Task | Command |
|---|---|
| Status and health | `docker compose ps` |
| Follow server logs | `docker compose logs -f server` (no bodies, notes or tokens are logged) |
| Tunnel status | `docker compose logs cloudflared`: look for "Registered tunnel connection" |
| SQL shell | `docker compose exec postgres psql -U ulap ulap` |
| Restart the server | `docker compose restart server` |

**Never run `docker compose down -v`.** The `-v` deletes the Postgres volume, which is the database.
