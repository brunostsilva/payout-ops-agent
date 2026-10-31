CREATE TABLE sellers (
    id       VARCHAR(64)  PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    currency VARCHAR(3)   NOT NULL
);

CREATE TABLE commission_rules (
    seller_id  VARCHAR(64)   NOT NULL REFERENCES sellers (id),
    rate       NUMERIC(5, 4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    valid_from DATE          NOT NULL,
    reason     TEXT,
    PRIMARY KEY (seller_id, valid_from)
);

CREATE TABLE ledger_entries (
    id          VARCHAR(64)    PRIMARY KEY,
    seller_id   VARCHAR(64)    NOT NULL REFERENCES sellers (id),
    entry_type  VARCHAR(16)    NOT NULL
        CHECK (entry_type IN ('SALE', 'COMMISSION', 'REFUND', 'CHARGEBACK', 'ADJUSTMENT')),
    amount      NUMERIC(19, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    occurred_on DATE           NOT NULL,
    order_id    VARCHAR(64),
    description TEXT,
    CONSTRAINT ledger_entries_signed_only_for_adjustments
        CHECK (entry_type = 'ADJUSTMENT' OR amount >= 0)
);

CREATE INDEX idx_ledger_entries_seller_date ON ledger_entries (seller_id, occurred_on);
