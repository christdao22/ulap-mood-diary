-- V2: the first real schema (technical-plan.md §7). V1 is the empty starting point and has already
-- run on dev databases, so it stays untouched: migrations only go forward (ulap-reference.md §10).
-- Server-made ids (users, identities, tokens) are UUIDv7 from Ids.newId() in :shared (§3).

CREATE TABLE users (
  id             UUID PRIMARY KEY,
  display_name   TEXT CHECK (char_length(display_name) BETWEEN 1 AND 50),  -- api-spec.md §5.1
  language       TEXT NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'fil', 'ceb')),
  mascot_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at     TIMESTAMPTZ
);

-- One user can have many sign-in methods (Google now, email, Apple later).
CREATE TABLE auth_identities (
  id               UUID PRIMARY KEY,
  user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  provider         TEXT NOT NULL CHECK (provider IN ('GOOGLE', 'EMAIL', 'APPLE')),
  provider_subject TEXT NOT NULL,           -- Google "sub", Apple "sub", or the lower-cased email
  email            TEXT,
  email_verified   BOOLEAN NOT NULL DEFAULT FALSE,
  password_hash    TEXT,                    -- Argon2id, EMAIL provider only
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_subject),
  CHECK (provider = 'EMAIL' OR password_hash IS NULL)
);
CREATE INDEX auth_identities_user ON auth_identities (user_id);

-- Rotating refresh tokens (api-spec.md §3, §6.9). Only a hash is stored, never the token itself.
-- A revoked token that comes back is a reuse: every token for that user and device is revoked.
CREATE TABLE refresh_tokens (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  device_id  UUID NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,          -- the lookup on every /auth/refresh
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_device ON refresh_tokens (user_id, device_id);

-- Email verification (24 hours) and password reset (30 minutes), single use.
CREATE TABLE email_tokens (
  id          UUID PRIMARY KEY,
  identity_id UUID NOT NULL REFERENCES auth_identities (id) ON DELETE CASCADE,
  purpose     TEXT NOT NULL CHECK (purpose IN ('verify', 'reset')),
  token_hash  TEXT NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  used_at     TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX email_tokens_identity ON email_tokens (identity_id);

CREATE SEQUENCE check_in_seq;

CREATE TABLE check_ins (
  id                UUID PRIMARY KEY,       -- the same UUID the phone created
  user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  mood_code         TEXT NOT NULL,
  energy            SMALLINT NOT NULL CHECK (energy BETWEEN 1 AND 10),
  stress            SMALLINT NOT NULL CHECK (stress BETWEEN 1 AND 10),
  stress_after      SMALLINT CHECK (stress_after BETWEEN 1 AND 10),
  trigger           TEXT,
  -- note and trigger_other are AES-256-GCM ciphertext (api-spec.md §11): a 12-byte nonce, then
  -- the ciphertext and its 16-byte tag. key_id names the server key that sealed both of them.
  trigger_other     BYTEA,
  note              BYTEA,
  key_id            TEXT,
  support_used      TEXT NOT NULL DEFAULT 'NONE',
  is_good_moment    BOOLEAN NOT NULL DEFAULT FALSE,
  logged_at         TIMESTAMPTZ NOT NULL,
  tz_offset_min     SMALLINT NOT NULL,
  client_updated_at TIMESTAMPTZ NOT NULL,   -- the phone's updated_at
  deleted_at        TIMESTAMPTZ,
  server_seq        BIGINT NOT NULL DEFAULT nextval('check_in_seq'),
  server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((note IS NULL AND trigger_other IS NULL) OR key_id IS NOT NULL)
);
ALTER SEQUENCE check_in_seq OWNED BY check_ins.server_seq;

CREATE INDEX check_ins_user_seq ON check_ins (user_id, server_seq);
-- The tombstone purge job (technical-plan.md §8) finds old deletes without a full scan.
CREATE INDEX check_ins_deleted ON check_ins (deleted_at) WHERE deleted_at IS NOT NULL;

-- How far tombstones have been purged. One row (id = 1). The purge job raises
-- purged_through_seq to the highest server_seq it deleted, in the same transaction.
-- A pull with 0 < since < purged_through_seq may have missed a delete → resetRequired (§8).
CREATE TABLE sync_purge (
  id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  purged_through_seq BIGINT NOT NULL DEFAULT 0,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO sync_purge (id) VALUES (1);
