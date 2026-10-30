-- Service catalogue, staff profiles and their links to branches (SVC-1..3, SVC-5..7, STF-1..3, STF-8).
-- Stylist levels and level prices (SVC-4) and schedules (STF-4..6) come in later migrations.

CREATE TABLE service_category (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id),
    name        text        NOT NULL CHECK (length(trim(name)) > 0),
    sort_order  integer     NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id)
);

CREATE TABLE service (
    id                   uuid PRIMARY KEY,
    business_id          uuid        NOT NULL REFERENCES business (id),
    category_id          uuid        NOT NULL,
    name                 text        NOT NULL CHECK (length(trim(name)) > 0),
    description          text,
    default_duration_min integer     NOT NULL CHECK (default_duration_min BETWEEN 5 AND 600),
    buffer_after_min     integer     NOT NULL DEFAULT 0 CHECK (buffer_after_min BETWEEN 0 AND 120),
    -- Minor units of the business currency, GST-inclusive.
    default_price        bigint      NOT NULL CHECK (default_price >= 0),
    price_is_from        boolean     NOT NULL DEFAULT false,
    customer_notice      text,
    active               boolean     NOT NULL DEFAULT true,
    sort_order           integer     NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id),
    FOREIGN KEY (business_id, category_id) REFERENCES service_category (business_id, id)
);
CREATE INDEX service_category_idx ON service (business_id, category_id);

-- A service offered at a branch, with optional branch price and duration.
CREATE TABLE location_service (
    business_id  uuid        NOT NULL,
    location_id  uuid        NOT NULL,
    service_id   uuid        NOT NULL,
    price        bigint CHECK (price >= 0),
    duration_min integer CHECK (duration_min BETWEEN 5 AND 600),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (location_id, service_id),
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, service_id) REFERENCES service (business_id, id) ON DELETE CASCADE
);

CREATE TABLE staff (
    id              uuid PRIMARY KEY,
    business_id     uuid        NOT NULL REFERENCES business (id),
    display_name    text        NOT NULL CHECK (length(trim(display_name)) > 0),
    bio             text,
    bookable_online boolean     NOT NULL DEFAULT true,
    active          boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id)
);

CREATE TABLE staff_location (
    business_id uuid NOT NULL,
    staff_id    uuid NOT NULL,
    location_id uuid NOT NULL,
    PRIMARY KEY (staff_id, location_id),
    FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE
);

CREATE TABLE staff_service (
    business_id uuid NOT NULL,
    staff_id    uuid NOT NULL,
    service_id  uuid NOT NULL,
    PRIMARY KEY (staff_id, service_id),
    FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, service_id) REFERENCES service (business_id, id) ON DELETE CASCADE
);

-- A sign-in can be linked to the staff profile of the person (stylists, and owners who also cut hair).
ALTER TABLE membership ADD COLUMN staff_id uuid;
ALTER TABLE membership ADD FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id);
CREATE UNIQUE INDEX membership_staff_idx ON membership (business_id, staff_id) WHERE staff_id IS NOT NULL;

-- Invites can carry the staff profile and branch access the person gets on accepting.
ALTER TABLE invite ADD COLUMN staff_id uuid;
ALTER TABLE invite ADD FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id);

CREATE TABLE invite_location (
    business_id uuid NOT NULL,
    invite_id   uuid NOT NULL,
    location_id uuid NOT NULL,
    PRIMARY KEY (invite_id, location_id),
    FOREIGN KEY (business_id, invite_id) REFERENCES invite (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id) ON DELETE CASCADE
);

SELECT enable_tenant_rls('service_category');
SELECT enable_tenant_rls('service');
SELECT enable_tenant_rls('location_service');
SELECT enable_tenant_rls('staff');
SELECT enable_tenant_rls('staff_location');
SELECT enable_tenant_rls('staff_service');
SELECT enable_tenant_rls('invite_location');
