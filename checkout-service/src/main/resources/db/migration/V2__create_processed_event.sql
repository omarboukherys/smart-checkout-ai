-- Inbox: ids of the incoming events already applied (risk decisions, payment outcomes).
-- Inserted in the same transaction as the state change: a duplicate delivery is detected and ignored.
CREATE TABLE processed_event (
    event_id      UUID         PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL
);
