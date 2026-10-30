-- Customers, appointments and their management links (BKG, MGT, D-5, D-19).
-- Customers are separate per business; there is no shared customer profile.

CREATE TABLE customer (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id),
    full_name   text        NOT NULL CHECK (length(trim(full_name)) > 0),
    email       citext,
    phone_e164  text CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, id)
);
-- Matching is by exact email, then exact mobile (D-5).
CREATE UNIQUE INDEX customer_email_idx ON customer (business_id, email) WHERE email IS NOT NULL;
CREATE INDEX customer_phone_idx ON customer (business_id, phone_e164);

CREATE TABLE appointment (
    id              uuid PRIMARY KEY,
    business_id     uuid        NOT NULL REFERENCES business (id),
    -- A label for people to quote, not a credential (MGT-7).
    reference       text        NOT NULL CHECK (reference ~ '^[A-Z0-9]{8}$'),
    location_id     uuid        NOT NULL,
    customer_id     uuid        NOT NULL,
    staff_id        uuid        NOT NULL,
    start_at        timestamptz NOT NULL,
    end_at          timestamptz NOT NULL,
    -- end_at plus the clean-up time after the last service; the stylist is busy until then.
    blocked_until   timestamptz NOT NULL,
    status          text        NOT NULL DEFAULT 'confirmed'
        CHECK (status IN ('confirmed', 'checked_in', 'completed', 'cancelled', 'no_show')),
    source          text        NOT NULL CHECK (source IN ('web', 'android', 'staff', 'walk_in')),
    customer_note   text CHECK (length(customer_note) <= 500),
    idempotency_key uuid,
    cancelled_at    timestamptz,
    cancelled_by    text CHECK (cancelled_by IN ('customer', 'staff')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (start_at < end_at AND end_at <= blocked_until),
    UNIQUE (business_id, id),
    UNIQUE (business_id, reference),
    UNIQUE (business_id, idempotency_key),
    FOREIGN KEY (business_id, location_id) REFERENCES location (business_id, id),
    FOREIGN KEY (business_id, customer_id) REFERENCES customer (business_id, id),
    FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id)
);

-- A stylist is never double booked (NFR-REL-1). Cancelled and no-show appointments free the time;
-- '[)' allows back-to-back appointments. Checked across all rows regardless of row-level security.
ALTER TABLE appointment ADD CONSTRAINT appointment_staff_no_overlap
    EXCLUDE USING gist (staff_id WITH =, tstzrange(start_at, blocked_until, '[)') WITH &&)
    WHERE (status IN ('confirmed', 'checked_in', 'completed'));

CREATE INDEX appointment_location_time_idx ON appointment (business_id, location_id, start_at);
CREATE INDEX appointment_staff_time_idx ON appointment (business_id, staff_id, start_at);
CREATE INDEX appointment_customer_idx ON appointment (business_id, customer_id, start_at);

-- What was booked, as it was at booking time (BKG-12): later service edits never change it.
CREATE TABLE appointment_service (
    business_id    uuid    NOT NULL,
    appointment_id uuid    NOT NULL,
    position       integer NOT NULL CHECK (position >= 0),
    service_id     uuid    NOT NULL,
    name           text    NOT NULL,
    duration_min   integer NOT NULL CHECK (duration_min > 0),
    price          bigint  NOT NULL CHECK (price >= 0),
    price_is_from  boolean NOT NULL,
    PRIMARY KEY (appointment_id, position),
    FOREIGN KEY (business_id, appointment_id) REFERENCES appointment (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, service_id) REFERENCES service (business_id, id)
);

-- Private management links (MGT-1): only a SHA-256 hash of the token is stored.
CREATE TABLE booking_token (
    id             uuid PRIMARY KEY,
    business_id    uuid        NOT NULL,
    appointment_id uuid        NOT NULL,
    token_hash     bytea       NOT NULL UNIQUE,
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (business_id, appointment_id) REFERENCES appointment (business_id, id) ON DELETE CASCADE
);

SELECT enable_tenant_rls('customer');
SELECT enable_tenant_rls('appointment');
SELECT enable_tenant_rls('appointment_service');
SELECT enable_tenant_rls('booking_token');
