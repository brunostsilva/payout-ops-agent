package io.github.brunostsilva.payoutops.domain;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.chargeback;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.commission;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.sale;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutComparisonTest {

    private static final PayoutPeriod CURRENT = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));
    private static final PayoutPeriod PREVIOUS = CURRENT.previous();

    private final PayoutBreakdown previous = PayoutCalculator.calculate(SELLER, PREVIOUS, BRL, List.of(
            sale("1000.00", PREVIOUS.start()),
            commission("100.00", PREVIOUS.start())));

    private final PayoutBreakdown current = PayoutCalculator.calculate(SELLER, CURRENT, BRL, List.of(
            sale("900.00", CURRENT.start()),
            commission("135.00", CURRENT.start()),
            chargeback("300.00", CURRENT.start())));

    @Test
    void computesNetChangeAndPercent() {
        PayoutComparison comparison = new PayoutComparison(previous, current);

        assertThat(comparison.netChange()).isEqualTo(Money.of("-435.00", "BRL"));
        assertThat(comparison.netChangePercent()).contains(new BigDecimal("-48.3"));
    }

    @Test
    void driversAreSortedByImpactAndAddUpToNetChange() {
        PayoutComparison comparison = new PayoutComparison(previous, current);

        assertThat(comparison.drivers())
                .extracting(PayoutComparison.Driver::component)
                .containsExactly(PayoutComponent.CHARGEBACKS, PayoutComponent.SALES, PayoutComponent.COMMISSIONS);
        Money sum = comparison.drivers().stream()
                .map(PayoutComparison.Driver::effectOnNet)
                .reduce(Money.zero(BRL), Money::plus);
        assertThat(sum).isEqualTo(comparison.netChange());
    }

    @Test
    void percentIsEmptyWhenPreviousNetIsZero() {
        PayoutBreakdown empty = PayoutCalculator.calculate(SELLER, PREVIOUS, BRL, List.of());

        assertThat(new PayoutComparison(empty, current).netChangePercent()).isEmpty();
    }
}
