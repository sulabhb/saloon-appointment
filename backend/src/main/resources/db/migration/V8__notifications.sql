-- Customer emails as an outbox (NTF-1..6). Rows are written in the same transaction as the booking
-- change, so a notification is queued exactly when the change commits, and sending never blocks
-- or undoes the booking. Content is rendered at send time: management links are created then, and
-- no link or message body is stored.

CREATE TABLE notification (
    id                  uuid PRIMARY KEY,
    business_id         uuid        NOT NULL,
    appointment_id      uuid        NOT NULL,
    type                text        NOT NULL
        CHECK (type IN ('booking_confirmed', 'booking_rescheduled', 'booking_cancelled', 'reminder', 'manage_link')),
    channel             text        NOT NULL DEFAULT 'email' CHECK (channel IN ('email', 'push', 'sms')),
    recipient           text        NOT NULL,
    -- One notification per event; also limits reminders to one per appointment time (NTF-3).
    dedupe_key          text        NOT NULL,
    status              text        NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'sending', 'sent', 'failed', 'skipped')),
    attempts            integer     NOT NULL DEFAULT 0,
    next_attempt_at     timestamptz NOT NULL DEFAULT now(),
    provider_message_id text,
    last_error          text,
    sent_at             timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (business_id, dedupe_key),
    FOREIGN KEY (business_id, appointment_id) REFERENCES appointment (business_id, id) ON DELETE CASCADE
);
CREATE INDEX notification_due_idx ON notification (next_attempt_at) WHERE status = 'queued';
CREATE INDEX notification_appointment_idx ON notification (business_id, appointment_id);

SELECT enable_tenant_rls('notification');
