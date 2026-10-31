package io.github.brunostsilva.payoutops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explains how a seller's net payout changed between two periods. */
public record PayoutComparison(PayoutBreakdown previous, PayoutBreakdown current) {

    public PayoutComparison {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (!previous.sellerId().equals(current.sellerId())) {
            throw new IllegalArgumentException("Cannot compare payouts of different sellers");
        }
    }

    public Money netChange() {
        return current.net().minus(previous.net());
    }

    /** Percentage change of the net payout, empty when the previous net payout was zero. */
    public Optional<BigDecimal> netChangePercent() {
        BigDecimal base = previous.net().amount();
        if (base.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(netChange().amount()
                .multiply(BigDecimal.valueOf(100))
                .divide(base.abs(), 1, RoundingMode.HALF_EVEN));
    }

    /**
     * Per-component effect on the net change, largest impact first. Components that did not
     * change are omitted. The effects always add up to {@link #netChange()}.
     */
    public List<Driver> drivers() {
        return Arrays.stream(PayoutComponent.values())
                .map(component -> new Driver(
                        component, current.effectOnNet(component).minus(previous.effectOnNet(component))))
                .filter(driver -> !driver.effectOnNet().isZero())
                .sorted(Comparator.comparing((Driver driver) -> driver.effectOnNet().abs()).reversed())
                .toList();
    }

    public record Driver(PayoutComponent component, Money effectOnNet) {
    }
}
