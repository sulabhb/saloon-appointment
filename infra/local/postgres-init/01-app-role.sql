-- Runtime role used by the backend. Not the table owner and no BYPASSRLS, so tenant row-level
-- security applies to it. Local development password only.
CREATE ROLE salon_app LOGIN PASSWORD 'salon_app' NOSUPERUSER NOBYPASSRLS;
