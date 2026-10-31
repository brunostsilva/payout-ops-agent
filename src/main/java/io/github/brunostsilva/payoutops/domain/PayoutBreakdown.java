package io.github.brunostsilva.payoutops.domain;

/**
 * How a seller's net payout for a period is composed. Commissions, refunds and chargebacks are
 * positive magnitudes that reduce the payout; adjustments are signed.
 */
public record PayoutBreakdown(
        SellerId sellerId,
        PayoutPeriod period,
        Money grossSales,
        Money commissions,
        Money refunds,
        Money chargebacks,
        Money adjustments,
        int entryCount) {

    public Money net() {
        return grossSales.minus(commissions).minus(refunds).minus(chargebacks).plus(adjustments);
    }

    /** Contribution of each component to {@link #net()}, signed. */
    public Money effectOnNet(PayoutComponent component) {
        return switch (component) {
            case SALES -> grossSales;
            case COMMISSIONS -> commissions.negate();
            case REFUNDS -> refunds.negate();
            case CHARGEBACKS -> chargebacks.negate();
            case ADJUSTMENTS -> adjustments;
        };
    }
}
