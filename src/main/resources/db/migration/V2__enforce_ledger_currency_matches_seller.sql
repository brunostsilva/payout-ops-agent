-- A ledger entry must be in its seller's currency. The composite foreign key enforces it at write
-- time, so a mismatched row can never reach the payout calculation.
ALTER TABLE sellers
    ADD CONSTRAINT sellers_id_currency_key UNIQUE (id, currency);

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_seller_currency_fkey
        FOREIGN KEY (seller_id, currency) REFERENCES sellers (id, currency);
