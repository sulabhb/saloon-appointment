-- Staff sign-in sessions. One row per signed-in browser or Android device.
--
-- A platform table: sessions are looked up by token before any business is known, so the table
-- has no row-level security. The chosen business is therefore called selected_business_id, not
-- business_id (business_id marks business-owned tables, which must have the tenant policy).

CREATE TABLE auth_session (
    id                   uuid PRIMARY KEY,
    user_id              uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    selected_business_id uuid REFERENCES business (id),
    -- SHA-256 of the token; the token itself is never stored.
    token_hash           bytea       NOT NULL UNIQUE,
    client               text        NOT NULL CHECK (client IN ('web', 'android')),
    device_name          text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    last_seen_at         timestamptz NOT NULL DEFAULT now(),
    idle_expires_at      timestamptz NOT NULL,
    absolute_expires_at  timestamptz NOT NULL,
    revoked_at           timestamptz,
    CHECK (idle_expires_at <= absolute_expires_at)
);

CREATE INDEX auth_session_user_active_idx ON auth_session (user_id) WHERE revoked_at IS NULL;
