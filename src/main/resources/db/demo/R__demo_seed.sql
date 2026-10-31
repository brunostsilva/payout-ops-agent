-- Synthetic demo data. Not real sellers or transactions.
--
-- Scenario: seller-123 (Aurora Cosmetics) sees its weekly payout drop from BRL 8,800.00
-- (week of 2026-10-19) to BRL 6,380.00 (week of 2026-10-26), a 27.5% decrease, driven by:
--   * two chargebacks on orders from the previous week          (-1,200.00)
--   * a logistics fee correction adjustment                     (  -600.00)
--   * commission rate raised from 10% to 15% on 2026-10-26      (  -470.00)
--   * slightly lower sales                                      (  -200.00)
--   * fewer refunds                                             (   +50.00)
-- seller-456 (Nimbus Home) is a stable control seller.
--
-- This file owns all data of the demo sellers: Flyway re-runs it whenever its checksum changes,
-- and it replaces their rules and ledger wholesale, so the database always mirrors this file
-- (edited or removed rows included).

DELETE FROM ledger_entries WHERE seller_id IN ('seller-123', 'seller-456');
DELETE FROM commission_rules WHERE seller_id IN ('seller-123', 'seller-456');

INSERT INTO sellers (id, name, currency) VALUES
    ('seller-123', 'Aurora Cosmetics', 'BRL'),
    ('seller-456', 'Nimbus Home', 'BRL')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, currency = EXCLUDED.currency;

INSERT INTO commission_rules (seller_id, rate, valid_from, reason) VALUES
    ('seller-123', 0.1000, DATE '2025-01-01', 'Standard marketplace rate'),
    ('seller-123', 0.1500, DATE '2026-10-26', 'Category reclassified to Premium Beauty'),
    ('seller-456', 0.1000, DATE '2025-01-01', 'Standard marketplace rate');

INSERT INTO ledger_entries (id, seller_id, entry_type, amount, currency, occurred_on, order_id, description) VALUES
    -- seller-123, week of 2026-10-19
    ('le-123-0001', 'seller-123', 'SALE',       4000.00, 'BRL', DATE '2026-10-20', 'o-1001', NULL),
    ('le-123-0002', 'seller-123', 'COMMISSION',  400.00, 'BRL', DATE '2026-10-20', 'o-1001', NULL),
    ('le-123-0003', 'seller-123', 'SALE',       3500.00, 'BRL', DATE '2026-10-22', 'o-1002', NULL),
    ('le-123-0004', 'seller-123', 'COMMISSION',  350.00, 'BRL', DATE '2026-10-22', 'o-1002', NULL),
    ('le-123-0005', 'seller-123', 'REFUND',      200.00, 'BRL', DATE '2026-10-23', 'o-0999', NULL),
    ('le-123-0006', 'seller-123', 'SALE',       2500.00, 'BRL', DATE '2026-10-24', 'o-1003', NULL),
    ('le-123-0007', 'seller-123', 'COMMISSION',  250.00, 'BRL', DATE '2026-10-24', 'o-1003', NULL),
    -- seller-123, week of 2026-10-26
    ('le-123-0101', 'seller-123', 'SALE',       3800.00, 'BRL', DATE '2026-10-27', 'o-1101', NULL),
    ('le-123-0102', 'seller-123', 'COMMISSION',  570.00, 'BRL', DATE '2026-10-27', 'o-1101', NULL),
    ('le-123-0103', 'seller-123', 'SALE',       3000.00, 'BRL', DATE '2026-10-28', 'o-1102', NULL),
    ('le-123-0104', 'seller-123', 'COMMISSION',  450.00, 'BRL', DATE '2026-10-28', 'o-1102', NULL),
    ('le-123-0105', 'seller-123', 'CHARGEBACK',  700.00, 'BRL', DATE '2026-10-28', 'o-1002', 'Customer disputed charge: item not received'),
    ('le-123-0106', 'seller-123', 'REFUND',      150.00, 'BRL', DATE '2026-10-29', 'o-1050', NULL),
    ('le-123-0107', 'seller-123', 'SALE',       3000.00, 'BRL', DATE '2026-10-30', 'o-1103', NULL),
    ('le-123-0108', 'seller-123', 'COMMISSION',  450.00, 'BRL', DATE '2026-10-30', 'o-1103', NULL),
    ('le-123-0109', 'seller-123', 'ADJUSTMENT', -600.00, 'BRL', DATE '2026-10-30', NULL,     'Logistics fee correction for October shipments'),
    ('le-123-0110', 'seller-123', 'CHARGEBACK',  500.00, 'BRL', DATE '2026-10-31', 'o-1003', 'Card issuer fraud claim'),
    -- seller-456, week of 2026-10-19
    ('le-456-0001', 'seller-456', 'SALE',       5000.00, 'BRL', DATE '2026-10-21', 'o-2001', NULL),
    ('le-456-0002', 'seller-456', 'COMMISSION',  500.00, 'BRL', DATE '2026-10-21', 'o-2001', NULL),
    -- seller-456, week of 2026-10-26
    ('le-456-0101', 'seller-456', 'SALE',       5200.00, 'BRL', DATE '2026-10-28', 'o-2101', NULL),
    ('le-456-0102', 'seller-456', 'COMMISSION',  520.00, 'BRL', DATE '2026-10-28', 'o-2101', NULL);
