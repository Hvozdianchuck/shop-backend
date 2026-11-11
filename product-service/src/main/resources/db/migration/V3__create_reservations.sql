CREATE TABLE reservations (
    order_id     UUID PRIMARY KEY,
    status       VARCHAR(16) NOT NULL,
    missing_skus TEXT[]      NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservation_lines (
    order_id UUID        NOT NULL REFERENCES reservations (order_id),
    sku      VARCHAR(64) NOT NULL,
    quantity INTEGER     NOT NULL
);

CREATE INDEX reservation_lines_order_id_idx ON reservation_lines (order_id);
