CREATE TABLE support_tickets (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16)  NOT NULL,
    text_masked    TEXT         NOT NULL,
    intent         VARCHAR(16),
    order_id       UUID,
    needs_human    BOOLEAN,
    human_reason   VARCHAR(255),
    result         JSONB,
    prompt_version VARCHAR(32),
    model          VARCHAR(64),
    input_tokens   INTEGER,
    output_tokens  INTEGER,
    latency_ms     BIGINT,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

-- The worker only looks at tickets that are still waiting or stuck in processing.
CREATE INDEX support_tickets_queue_idx ON support_tickets (created_at) WHERE status IN ('NEW', 'PROCESSING');
