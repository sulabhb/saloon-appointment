-- Password reset by email (DSH-1). A platform table: accounts are not business-owned, so no
-- row-level security. Only a SHA-256 hash of each single-use token is stored.

CREATE TABLE password_reset (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash bytea       NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_user_idx ON password_reset (user_id) WHERE used_at IS NULL;
