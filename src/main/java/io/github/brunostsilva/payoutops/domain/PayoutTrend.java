package io.github.brunostsilva.payoutops.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A seller's payouts over consecutive periods, oldest first, and how the net payout moved across
 * them: tells a one-off change apart from a steady trend.
 */
public record PayoutTrend(List<PayoutBreakdown> periods) {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PayoutTrend {
        periods = List.copyOf(Objects.requireNonNull(periods, "periods"));
        if (periods.size() < 2) {
            throw new IllegalArgumentException("A trend needs at least 2 periods, got " + periods.size());
        }
        for (int i = 1; i < periods.size(); i++) {
            PayoutBreakdown previous = periods.get(i - 1);
            PayoutBreakdown current = periods.get(i);
            if (!current.sellerId().equals(previous.sellerId())) {
                throw new IllegalArgumentException("Cannot build a trend from payouts of different sellers");
            }
            if (!current.period().previous().equals(previous.period())) {
                throw new IllegalArgumentException("Periods must be consecutive, oldest first: "
                        + previous.period() + " is not right before " + current.period());
            }
        }
    }

    public PayoutBreakdown first() {
        return periods.getFirst();
    }

    public PayoutBreakdown last() {
        return periods.getLast();
    }

    /** Change of the net payout from the first period to the last, in percent; empty when the first was zero. */
    public Optional<BigDecimal> totalChangePercent() {
        BigDecimal base = first().net().amount();
        if (base.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(last().net().minus(first().net()).amount()
                .multiply(HUNDRED)
                .divide(base.abs(), 1, RoundingMode.HALF_EVEN));
    }

    /**
     * Average growth of the net payout per period, compounded, in percent: the constant rate that takes
     * the first net payout to the last one, {@code (last / first)^(1 / (n - 1)) - 1} over n periods. Like
     * any compound rate, it depends only on the first and last periods.
     *
     * <p>The n-th root is taken with {@link BigDecimal#rootn(int, MathContext)} (new in Java 27), so the
     * whole computation stays in decimal arithmetic: no detour through {@code double} and
     * {@code Math.pow}, whose binary rounding would leak into amounts that are exact decimals.
     *
     * <p>Empty when the first or last net payout isn't positive: a growth rate from or to a zero or
     * negative payout isn't defined.
     */
    public Optional<BigDecimal> averageGrowthPercent() {
        BigDecimal first = first().net().amount();
        BigDecimal last = last().net().amount();
        if (first.signum() <= 0 || last.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal growthFactor = last.divide(first, PRECISION).rootn(periods.size() - 1, PRECISION);
        return Optional.of(growthFactor.subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_EVEN));
    }

    /**
     * The largest change of the net payout from one period to the next, in percent, and the period it
     * led to; the earliest one on a tie. Unlike {@link #averageGrowthPercent()}, it follows the path
     * between the first and last periods: close to the average, the change was spread over the
     * periods (a trend); much larger, it was concentrated in one period (a one-off). Changes from a
     * zero net payout have no percentage and are skipped; empty when all of them are.
     */
    public Optional<PeriodChange> largestPeriodChange() {
        PeriodChange largest = null;
        for (int i = 1; i < periods.size(); i++) {
            BigDecimal before = periods.get(i - 1).net().amount();
            if (before.signum() == 0) {
                continue;
            }
            BigDecimal percent = periods.get(i).net().amount().subtract(before)
                    .multiply(HUNDRED)
                    .divide(before.abs(), 1, RoundingMode.HALF_EVEN);
            if (largest == null || percent.abs().compareTo(largest.percent().abs()) > 0) {
                largest = new PeriodChange(periods.get(i).period(), percent);
            }
        }
        return Optional.ofNullable(largest);
    }

    /** A change of the net payout into {@code period}, from the period before, in percent. */
    public record PeriodChange(PayoutPeriod period, BigDecimal percent) {
    }
}
