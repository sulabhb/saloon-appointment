-- Staff schedules (STF-4..6): a weekly pattern per branch, date overrides, leave and one-off breaks.
-- Times are local to the branch the person works at. A daily lunch break is two shifts.

-- Shifts and overrides reference the staff member's link to a branch, so removing someone from a
-- branch also removes their shifts there.
ALTER TABLE staff_location ADD UNIQUE (business_id, staff_id, location_id);

CREATE TABLE staff_shift (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL,
    staff_id    uuid        NOT NULL,
    location_id uuid        NOT NULL,
    weekday     smallint    NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- ISO: 1 = Monday
    starts_at   time        NOT NULL,
    ends_at     time        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (starts_at < ends_at),
    FOREIGN KEY (business_id, staff_id, location_id)
        REFERENCES staff_location (business_id, staff_id, location_id) ON DELETE CASCADE
);
CREATE INDEX staff_shift_staff_idx ON staff_shift (business_id, staff_id, weekday);
CREATE INDEX staff_shift_location_idx ON staff_shift (business_id, location_id, weekday);

-- Replaces the weekly pattern on one date: either one 'off' row, or one or more 'work' rows.
CREATE TABLE staff_shift_exception (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL,
    staff_id    uuid        NOT NULL,
    on_date     date        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('off', 'work')),
    location_id uuid,
    starts_at   time,
    ends_at     time,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'off' AND location_id IS NULL AND starts_at IS NULL AND ends_at IS NULL)
        OR (kind = 'work' AND location_id IS NOT NULL AND starts_at < ends_at)),
    FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id) ON DELETE CASCADE,
    FOREIGN KEY (business_id, staff_id, location_id)
        REFERENCES staff_location (business_id, staff_id, location_id) ON DELETE CASCADE
);
CREATE INDEX staff_shift_exception_staff_idx ON staff_shift_exception (business_id, staff_id, on_date);

-- Leave covers whole days; a break is part of one day. Both block bookings wherever the person works.
CREATE TABLE staff_time_off (
    id          uuid PRIMARY KEY,
    business_id uuid        NOT NULL,
    staff_id    uuid        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('leave', 'break')),
    starts_on   date        NOT NULL,
    ends_on     date        NOT NULL,
    starts_at   time,
    ends_at     time,
    reason      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (starts_on <= ends_on),
    CHECK ((kind = 'leave' AND starts_at IS NULL AND ends_at IS NULL)
        OR (kind = 'break' AND starts_on = ends_on AND starts_at < ends_at)),
    FOREIGN KEY (business_id, staff_id) REFERENCES staff (business_id, id) ON DELETE CASCADE
);
CREATE INDEX staff_time_off_staff_idx ON staff_time_off (business_id, staff_id, ends_on);

SELECT enable_tenant_rls('staff_shift');
SELECT enable_tenant_rls('staff_shift_exception');
SELECT enable_tenant_rls('staff_time_off');
