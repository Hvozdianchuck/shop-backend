CREATE TABLE orders (
    id         UUID PRIMARY KEY,
    status     VARCHAR(16)    NOT NULL,
    total      NUMERIC(12, 2) NOT NULL,
    version    BIGINT         NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE order_lines (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    sku        VARCHAR(64)    NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE INDEX order_lines_order_id_idx ON order_lines (order_id);
