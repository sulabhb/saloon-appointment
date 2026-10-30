-- Invitations to join a business (TEN-2, STF-2). Business-owned, so the tenant policy applies;
-- accepting an invite looks it up by token hash in system scope before the business is known.

CREATE TABLE invite (
    id                 uuid PRIMARY KEY,
    business_id        uuid        NOT NULL REFERENCES business (id),
    email              citext      NOT NULL,
    role               text        NOT NULL CHECK (role IN ('stylist', 'reception', 'manager', 'owner')),
    -- SHA-256 of the token in the invite link; the token itself is never stored.
    token_hash         bytea       NOT NULL UNIQUE,
    invited_by_user_id uuid REFERENCES app_user (id),
    accepted_user_id   uuid REFERENCES app_user (id),
    expires_at         timestamptz NOT NULL,
    accepted_at        timestamptz,
    revoked_at         timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id)
);

CREATE INDEX invite_open_by_email_idx ON invite (business_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

SELECT enable_tenant_rls('invite');
