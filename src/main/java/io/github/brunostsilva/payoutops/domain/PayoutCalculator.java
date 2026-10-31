package io.github.brunostsilva.payoutops.domain;

import java.util.Currency;
import java.util.List;

public final class PayoutCalculator {

    private PayoutCalculator() {
    }

    public static PayoutBreakdown calculate(
            SellerId sellerId, PayoutPeriod period, Currency currency, List<LedgerEntry> entries) {
        Money sales = Money.zero(currency);
        Money commissions = Money.zero(currency);
        Money refunds = Money.zero(currency);
        Money chargebacks = Money.zero(currency);
        Money adjustments = Money.zero(currency);

        for (LedgerEntry entry : entries) {
            if (!entry.sellerId().equals(sellerId)) {
                throw new IllegalArgumentException(
                        "Entry " + entry.id() + " belongs to seller " + entry.sellerId() + ", not " + sellerId);
            }
            if (!period.contains(entry.occurredOn())) {
                throw new IllegalArgumentException(
                        "Entry " + entry.id() + " on " + entry.occurredOn() + " is outside " + period);
            }
            if (!entry.amount().currency().equals(currency)) {
                throw new IllegalArgumentException("Entry " + entry.id() + " is in "
                        + entry.amount().currency().getCurrencyCode() + " but seller " + sellerId
                        + " settles in " + currency.getCurrencyCode());
            }
            switch (entry) {
                case LedgerEntry.Sale sale -> sales = sales.plus(sale.amount());
                case LedgerEntry.Commission commission -> commissions = commissions.plus(commission.amount());
                case LedgerEntry.Refund refund -> refunds = refunds.plus(refund.amount());
                case LedgerEntry.Chargeback chargeback -> chargebacks = chargebacks.plus(chargeback.amount());
                case LedgerEntry.Adjustment adjustment -> adjustments = adjustments.plus(adjustment.amount());
            }
        }

        return new PayoutBreakdown(
                sellerId, period, sales, commissions, refunds, chargebacks, adjustments, entries.size());
    }
}
