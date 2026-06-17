CREATE TABLE IF NOT EXISTS multistate.event_outbox (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_event_outbox_aggregate_id ON multistate.event_outbox(aggregate_id);
CREATE INDEX idx_event_outbox_published_at ON multistate.event_outbox(published_at) WHERE published_at IS NULL;
