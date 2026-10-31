package io.github.brunostsilva.payoutops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Commission rate charged to a seller from {@code validFrom} until the next rule takes effect. */
public record CommissionRule(SellerId sellerId, BigDecimal rate, LocalDate validFrom, String reason) {

    public CommissionRule {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(validFrom, "validFrom");
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Commission rate must be between 0 and 1, got " + rate);
        }
    }
}
