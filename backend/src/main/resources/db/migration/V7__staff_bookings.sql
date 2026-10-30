-- Staff-made bookings and status changes (DSH-8..16, STS-1..4).

ALTER TABLE appointment
    -- Staff only; never returned by public APIs (DSH-16).
    ADD COLUMN internal_note      text CHECK (length(internal_note) <= 2000),
    ADD COLUMN cancel_reason      text CHECK (length(cancel_reason) <= 500),
    -- Cancelled after the online cut-off (STS-3).
    ADD COLUMN late_cancel        boolean NOT NULL DEFAULT false,
    ADD COLUMN created_by_user_id uuid REFERENCES app_user (id),
    ADD COLUMN status_changed_at  timestamptz;

-- Walk-ins may leave out contact details (D-4); online bookings always have both.
CREATE INDEX customer_name_trgm_idx ON customer USING gin (full_name gin_trgm_ops);
