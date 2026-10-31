CREATE TABLE payout_holds (
    id            UUID         PRIMARY KEY,
    seller_id     VARCHAR(64)  NOT NULL REFERENCES sellers (id),
    period_start  DATE         NOT NULL,
    period_end    DATE         NOT NULL,
    reason        TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    proposed_by   VARCHAR(100) NOT NULL,
    proposed_at   TIMESTAMPTZ  NOT NULL,
    decided_by    VARCHAR(100),
    decided_at    TIMESTAMPTZ,
    decision_note TEXT,
    CONSTRAINT payout_holds_decision_matches_status CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

-- At most one pending hold per seller and payout period, even under concurrent proposals.
CREATE UNIQUE INDEX payout_holds_one_pending_per_period
    ON payout_holds (seller_id, period_start) WHERE status = 'PENDING';

CREATE INDEX idx_payout_holds_status ON payout_holds (status, proposed_at);
