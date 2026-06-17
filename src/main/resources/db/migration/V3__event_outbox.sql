CREATE TABLE IF NOT EXISTS multistate.event_outbox (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  TEXT         NOT NULL,
    topic         TEXT         NOT NULL,
    payload       JSONB        NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_unpublished
    ON multistate.event_outbox (occurred_at)
    WHERE published_at IS NULL;
