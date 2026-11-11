CREATE TABLE products (
    sku    VARCHAR(64) PRIMARY KEY,
    name   VARCHAR(255)   NOT NULL,
    price  NUMERIC(12, 2) NOT NULL,
    active BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE stock (
    sku       VARCHAR(64) PRIMARY KEY REFERENCES products (sku),
    available INTEGER NOT NULL CHECK (available >= 0)
);
