CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    seq          BIGSERIAL   NOT NULL,
    aggregate_id TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    topic        TEXT        NOT NULL,
    msg_key      TEXT        NOT NULL,
    payload      TEXT        NOT NULL,
    headers      JSONB       NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- The relay only looks at unsent records, so the index stays small as history grows.
CREATE INDEX outbox_unpublished_idx ON outbox (seq) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
