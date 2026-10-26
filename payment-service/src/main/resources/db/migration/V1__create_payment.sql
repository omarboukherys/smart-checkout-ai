-- One row per checkout: the primary key makes a second payment for the same checkout impossible.
CREATE TABLE payment (
    checkout_id      VARCHAR(64)  PRIMARY KEY,
    amount_minor     BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency         VARCHAR(3)   NOT NULL,
    payment_method   VARCHAR(16)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    psp_reference    VARCHAR(64),
    decline_code     VARCHAR(32),
    decline_message  VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL
);

-- Payments stuck in AUTHORIZING (provider outage) are the ones to watch.
CREATE INDEX idx_payment_status ON payment (status);
