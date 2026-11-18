// Sync load test (PHASES.md Phase 9, docs/runbooks/load-test.md): many phones syncing at once.
//
// Each k6 virtual user (VU) is one phone signed in as one seeded account (users.json, made by
// `./gradlew :server:loadTestUsers --args="seed"`). It does what the app's SyncEngine does
// (api-spec.md §8.6): first a sign-in upload of a backlog and a full download, then a sync run
// every THINK_MIN–THINK_MAX seconds, each run being push PENDING (batches of 200) → pull until
// hasMore is false. Every run adds a check-in, and sometimes edits or deletes an older one.
//
// Run it with Docker, so k6 needs no install (the runbook has the full command):
//   docker run --rm -v "<repo>/server:/work" -e BASE_URL=... grafana/k6:1.0.0 run /work/loadtest/sync.js
//
// The notes are made up here. Never point this at production (SECURITY.md).

import http from "k6/http";
import { check, fail, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://host.docker.internal:8080/v1").replace(/\/$/, "");
const USERS_FILE = __ENV.USERS_FILE || "/work/build/loadtest/users.json";
const BACKLOG = int("BACKLOG", 30); // check-ins each phone uploads on its first sync
const RAMP = __ENV.RAMP || "1m";
const HOLD = __ENV.HOLD || "5m";
const THINK_MIN = int("THINK_MIN", 10);
const THINK_MAX = int("THINK_MAX", 30);

// api-spec.md §8.2: at most 200 changes per push. §8.3: pages of up to 1000.
const PUSH_BATCH = 200;
const PULL_LIMIT = 500;

const users = new SharedArray("users", () => JSON.parse(open(USERS_FILE)));
const VUS = int("VUS", users.length);

// Proposed targets, not yet in the docs (runbook "Pass criteria"): the owner confirms them.
export const options = {
  scenarios: {
    phones: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: "30s", target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:push}": ["p(95)<500", "p(99)<1500"],
    "http_req_duration{name:pull}": ["p(95)<500", "p(99)<1500"],
    checks: ["rate>0.99"],
    rejected_changes: ["count==0"],
    reset_required: ["count==0"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

const syncRun = new Trend("sync_run_duration", true);
const rejectedChanges = new Counter("rejected_changes");
const resetRequired = new Counter("reset_required");
const rateLimited = new Counter("rate_limited");

const MOODS = {
  red: ["panicked", "overwhelmed", "anxious", "angry", "worried", "frustrated", "nervous", "irritated", "restless"],
  blue: ["hopeless", "burned_out", "sad", "lonely", "guilty", "disappointed", "tired", "bored"],
  yellow: ["excited", "motivated", "proud", "happy"],
  green: ["content", "grateful", "relaxed", "calm"],
};
const TRIGGERS = [null, "WORK", "FAMILY", "MONEY", "HEALTH", "RELATIONSHIPS", "SCHOOL", "OTHER"];
const SUPPORT = ["NONE", "BREATHE", "SMALL_HELPS", "GOOD_MOMENT"];

// One phone's local state. Each VU has its own copy of module globals.
const phone = { started: false, lastPullSeq: 0, live: [], pending: [] };

export function setup() {
  const ready = http.get(`${BASE_URL}/ready`, { tags: { name: "ready" } });
  if (ready.status !== 200) fail(`${BASE_URL}/ready answered ${ready.status}; is the server up?`);
  if (users.length < VUS) console.warn(`${VUS} VUs share ${users.length} accounts; seed more for one phone each.`);
  console.log(`${VUS} phones against ${BASE_URL}, ramp ${RAMP}, hold ${HOLD}`);
}

export default function () {
  const user = users[(__VU - 1) % users.length];
  const started = Date.now();
  if (!phone.started) {
    // First sign-in from guest mode (api-spec.md §8.5): everything local goes up, then a full pull.
    for (let i = 0; i < BACKLOG; i++) phone.pending.push(newCheckIn());
    push(user);
    pullAll(user, true);
    phone.started = true;
  } else {
    phone.pending.push(newCheckIn());
    const roll = Math.random();
    if (roll < 0.2 && phone.live.length > 0) phone.pending.push(edited(pick(phone.live)));
    else if (roll < 0.25 && phone.live.length > 1) phone.pending.push(tombstone(phone.live.shift()));
    push(user);
    pullAll(user, false);
  }
  syncRun.add(Date.now() - started);
  sleep(THINK_MIN + Math.random() * (THINK_MAX - THINK_MIN));
}

function push(user) {
  while (phone.pending.length > 0) {
    const batch = phone.pending.slice(0, PUSH_BATCH);
    const res = http.post(`${BASE_URL}/sync/push`, JSON.stringify({ changes: batch }), {
      headers: headers(user),
      tags: { name: "push" },
    });
    if (!ok(res, "push")) return; // like the app: keep PENDING, try again next run
    const body = res.json();
    rejectedChanges.add(body.rejected.length);
    check(body, { "push: every change accepted": (b) => b.accepted.length === batch.length });
    for (const change of batch) {
      if (change.deletedAt) continue;
      const known = phone.live.findIndex((c) => c.id === change.id);
      if (known >= 0) phone.live[known] = change;
      else phone.live.push(change);
    }
    phone.pending = phone.pending.slice(batch.length);
  }
}

function pullAll(user, full) {
  let since = full ? 0 : phone.lastPullSeq;
  for (;;) {
    const query = `since=${since}&limit=${PULL_LIMIT}` + (full ? "&full=true" : "");
    const res = http.get(`${BASE_URL}/sync/pull?${query}`, { headers: headers(user), tags: { name: "pull" } });
    if (!ok(res, "pull")) return;
    const body = res.json();
    if (body.resetRequired) {
      resetRequired.add(1);
      return;
    }
    since = body.nextSince;
    if (!body.hasMore) break;
  }
  phone.lastPullSeq = since;
}

function ok(res, name) {
  if (res.status === 429) rateLimited.add(1, { name });
  return check(res, { [`${name}: 200`]: (r) => r.status === 200 });
}

function headers(user) {
  return {
    Authorization: `Bearer ${user.accessToken}`,
    "Content-Type": "application/json",
    "X-Device-Id": user.deviceId,
    "X-App-Version": "android/1.0.0 (1)",
  };
}

// A valid check-in by the shared rules (api-spec.md §5.3): Good moment only for Yellow and Green.
function newCheckIn() {
  const zone = pick(Object.keys(MOODS));
  const goodZone = zone === "yellow" || zone === "green";
  const trigger = pick(TRIGGERS);
  const now = new Date();
  const loggedAt = new Date(now.getTime() - Math.floor(Math.random() * 30 * 24 * 3600 * 1000));
  return {
    id: uuidv4(),
    moodCode: pick(MOODS[zone]),
    energy: 1 + Math.floor(Math.random() * 10),
    stress: 1 + Math.floor(Math.random() * 10),
    stressAfter: Math.random() < 0.5 ? 1 + Math.floor(Math.random() * 10) : null,
    trigger,
    triggerOther: trigger === "OTHER" ? "Load test reason" : null,
    // About half have a note, so the server's note encryption is part of the load.
    note: Math.random() < 0.5 ? "Load test note. ".repeat(1 + Math.floor(Math.random() * 20)).trim() : null,
    supportUsed: goodZone ? pick(SUPPORT) : pick(SUPPORT.slice(0, 3)),
    isGoodMoment: goodZone && Math.random() < 0.3,
    loggedAt: loggedAt.toISOString(),
    tzOffsetMin: 480,
    updatedAt: now.toISOString(),
    deletedAt: null,
  };
}

function edited(checkIn) {
  return { ...checkIn, energy: 1 + Math.floor(Math.random() * 10), updatedAt: laterThan(checkIn.updatedAt) };
}

function tombstone(checkIn) {
  const at = laterThan(checkIn.updatedAt);
  return { id: checkIn.id, updatedAt: at, deletedAt: at };
}

// The merge keeps the newer updatedAt (api-spec.md §8.2), so an edit must be strictly later.
function laterThan(iso) {
  return new Date(Math.max(Date.now(), new Date(iso).getTime() + 1)).toISOString();
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function int(name, fallback) {
  const value = parseInt(__ENV[name] || "", 10);
  return Number.isNaN(value) ? fallback : value;
}

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}
