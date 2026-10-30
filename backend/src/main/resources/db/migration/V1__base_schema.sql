-- Base schema: platform, identity and branch tables, with tenant row-level security.
-- Runs as the owner role. The runtime role (${app_role}) must already exist; it is created
-- by infrastructure (Terraform, the local docker init script, or the test setup).

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- Tenant context helpers. The application sets app.business_id and app.system on every
-- connection checkout (TenantAwareDataSource).
-- ---------------------------------------------------------------------------

CREATE FUNCTION current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.business_id', true), '')::uuid $$;

CREATE FUNCTION is_system_context() RETURNS boolean
    LANGUAGE sql STABLE AS
$$ SELECT coalesce(current_setting('app.system', true), '') = 'on' $$;

-- Applies the standard tenant isolation policy to a business-owned table.
CREATE FUNCTION enable_tenant_rls(target regclass) RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', target);
    EXECUTE format(
        'CREATE POLICY tenant_isolation ON %s
             USING (business_id = current_tenant_id() OR is_system_context())
             WITH CHECK (business_id = current_tenant_id() OR is_system_context())',
        target);
END
$$;

-- ---------------------------------------------------------------------------
-- Platform tables (not business-owned, no row-level security)
-- ---------------------------------------------------------------------------

CREATE TABLE business (
    id                              uuid PRIMARY KEY,
    -- At least 3 characters so it can never collide with short booking-host paths such as /m/.
    slug                            text        NOT NULL UNIQUE
        CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
    legal_name                      text        NOT NULL,
    display_name                    text        NOT NULL,
    status                          text        NOT NULL DEFAULT 'onboarding'
        CHECK (status IN ('onboarding', 'live', 'suspended', 'closed')),
    country                         char(2)     NOT NULL DEFAULT 'AU',
    currency                        char(3)     NOT NULL DEFAULT 'AUD',
    locale                          text        NOT NULL DEFAULT 'en-AU',
    default_time_zone               text        NOT NULL,
    accent_color                    text CHECK (accent_color ~ '^#[0-9a-fA-F]{6}$'),
    description                     text,
    contact_email                   citext,
    privacy_policy_url              text,
    slot_interval_min               integer     NOT NULL DEFAULT 15
        CHECK (slot_interval_min IN (5, 10, 15, 20, 30, 60)),
    min_notice_min                  integer     NOT NULL DEFAULT 120 CHECK (min_notice_min >= 0),
    horizon_days                    integer     NOT NULL DEFAULT 60 CHECK (horizon_days BETWEEN 1 AND 365),
    cancel_cutoff_min               integer     NOT NULL DEFAULT 1440 CHECK (cancel_cutoff_min >= 0),
    max_future_bookings_per_contact integer     NOT NULL DEFAULT 3 CHECK (max_future_bookings_per_contact >= 1),
    sms_enabled                     boolean     NOT NULL DEFAULT false,
    max_locations                   integer CHECK (max_locations > 0),
    max_staff                       integer CHECK (max_staff > 0),
    closed_at                       timestamptz,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id                    uuid PRIMARY KEY,
    email                 citext      NOT NULL UNIQUE,
    full_name             text        NOT NULL,
    password_hash         text,
    totp_secret_encrypted text,
    is_platform_admin     boolean     NOT NULL DEFAULT false,
    active                boolean     NOT NULL DEFAULT true,
    failed_logins         integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Business-owned tables. Every one has business_id, a (business_id, id) key for composite
-- foreign keys, and the tenant isolation policy.
-- ---------------------------------------------------------------------------

CREATE TABLE membership (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id),
    user_id     uuid        NOT NULL REFERENCES app_user (id),
    role        text        NOT NULL CHECK (role IN ('stylist', 'reception', 'manager', 'owner')),
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id),
    UNIQUE (business_id, user_id)
);
CREATE INDEX membership_user_idx ON membership (user_id);

CREATE TABLE location (
    id                uuid PRIMARY KEY,
    business_id       uuid        NOT NULL REFERENCES business (id),
    name              text        NOT NULL,
    slug              text        NOT NULL CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,48}[a-z0-9]$'),
    address_line1     text        NOT NULL,
    address_line2     text,
    suburb            text        NOT NULL,
    state             text        NOT NULL,
    postcode          text        NOT NULL,
    country           char(2)     NOT NULL DEFAULT 'AU',
    phone             text,
    email             citext,
    time_zone         text        NOT NULL,
    -- Booking policy overrides; null means "use the business default".
    slot_interval_min integer CHECK (slot_interval_min IN (5, 10, 15, 20, 30, 60)),
    min_notice_min    integer CHECK (min_notice_min >= 0),
    horizon_days      integer CHECK (horizon_days BETWEEN 1 AND 365),
    cancel_cutoff_min integer CHECK (cancel_cutoff_min >= 0),
    active            boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id),
    UNIQUE (business_id, slug)
);

CREATE TABLE location_hours (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL,
    location_id uuid        NOT NULL,
    weekday     smallint    NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- ISO: 1 = Monday
    opens_at    time        NOT NULL,
    closes_at   time        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (opens_at < closes_at),
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE
);
CREATE INDEX location_hours_location_idx ON location_hours (business_id, location_id, weekday);

CREATE TABLE location_closure (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL,
    location_id uuid        NOT NULL,
    starts_on   date        NOT NULL,
    ends_on     date        NOT NULL,
    reason      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (starts_on <= ends_on),
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE
);
CREATE INDEX location_closure_location_idx ON location_closure (business_id, location_id, starts_on);

CREATE TABLE membership_location (
    business_id   uuid NOT NULL,
    membership_id uuid NOT NULL,
    location_id   uuid NOT NULL,
    PRIMARY KEY (membership_id, location_id),
    FOREIGN KEY (business_id, membership_id) REFERENCES membership (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE
);

SELECT enable_tenant_rls('membership');
SELECT enable_tenant_rls('location');
SELECT enable_tenant_rls('location_hours');
SELECT enable_tenant_rls('location_closure');
SELECT enable_tenant_rls('membership_location');

-- ---------------------------------------------------------------------------
-- Runtime role privileges: data access only, never ownership.
-- ---------------------------------------------------------------------------

GRANT USAGE ON SCHEMA public TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_role};
REVOKE ALL ON flyway_schema_history FROM ${app_role};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
