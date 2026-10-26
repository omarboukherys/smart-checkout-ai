-- Checkout aggregate -------------------------------------------------------
CREATE TABLE checkout (
    id               UUID         PRIMARY KEY,
    idempotency_key  VARCHAR(128) NOT NULL,
    store_id         VARCHAR(64)  NOT NULL,
    channel          VARCHAR(16)  NOT NULL,
    customer_id      VARCHAR(64),
    currency         VARCHAR(3)   NOT NULL,
    total_minor      BIGINT       NOT NULL CHECK (total_minor > 0),
    payment_method   VARCHAR(16)  NOT NULL,
    payment_token    VARCHAR(128) NOT NULL,
    card_last4       VARCHAR(4),
    card_country     VARCHAR(2),
    ip_country       VARCHAR(2),
    device_id        VARCHAR(128),
    status           VARCHAR(24)  NOT NULL,
    status_reason    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL,
    -- Last line of defence of idempotency: two concurrent requests with the same key cannot both insert.
    CONSTRAINT uk_checkout_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_checkout_created_at ON checkout (created_at DESC);
CREATE INDEX idx_checkout_status ON checkout (status);

CREATE TABLE checkout_line (
    checkout_id      UUID         NOT NULL REFERENCES checkout (id) ON DELETE CASCADE,
    line_no          INTEGER      NOT NULL,
    sku              VARCHAR(64)  NOT NULL,
    label            VARCHAR(255) NOT NULL,
    quantity         INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT       NOT NULL CHECK (unit_price_minor > 0),
    PRIMARY KEY (checkout_id, line_no)
);

-- Transactional outbox -----------------------------------------------------
-- Rows are written in the same transaction as the checkout, then relayed to Kafka.
CREATE TABLE outbox_event (
    seq           BIGINT       GENERATED ALWAYS AS IDENTITY,
    id            UUID         PRIMARY KEY,
    aggregate_id  VARCHAR(64)  NOT NULL,
    topic         VARCHAR(128) NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);

-- Partial index: the relay only ever reads pending rows, in insertion order.
CREATE INDEX idx_outbox_pending ON outbox_event (seq) WHERE published_at IS NULL;
