package io.github.brunostsilva.payoutops.domain;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.adjustment;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.chargeback;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.commission;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.refund;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.sale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);
    private static final PayoutPeriod WEEK = PayoutPeriod.weekOf(DAY);

    @Test
    void aggregatesEachEntryTypeIntoItsComponent() {
        List<LedgerEntry> entries = List.of(
                sale("1000.00", DAY),
                commission("100.00", DAY),
                refund("50.00", DAY),
                chargeback("200.00", DAY),
                adjustment("-30.00", DAY),
                adjustment("10.00", DAY));

        PayoutBreakdown payout = PayoutCalculator.calculate(SELLER, WEEK, BRL, entries);

        assertThat(payout.grossSales()).isEqualTo(Money.of("1000.00", "BRL"));
        assertThat(payout.commissions()).isEqualTo(Money.of("100.00", "BRL"));
        assertThat(payout.refunds()).isEqualTo(Money.of("50.00", "BRL"));
        assertThat(payout.chargebacks()).isEqualTo(Money.of("200.00", "BRL"));
        assertThat(payout.adjustments()).isEqualTo(Money.of("-20.00", "BRL"));
        assertThat(payout.net()).isEqualTo(Money.of("630.00", "BRL"));
        assertThat(payout.entryCount()).isEqualTo(6);
    }

    @Test
    void emptyLedgerYieldsZeroPayout() {
        PayoutBreakdown payout = PayoutCalculator.calculate(SELLER, WEEK, BRL, List.of());

        assertThat(payout.net()).isEqualTo(Money.zero(BRL));
        assertThat(payout.entryCount()).isZero();
    }

    @Test
    void rejectsEntriesOfAnotherSeller() {
        LedgerEntry foreign = new LedgerEntry.Sale(
                "le-x", new SellerId("other"), DAY, Money.of("10", "BRL"), "o-x");

        assertThatThrownBy(() -> PayoutCalculator.calculate(SELLER, WEEK, BRL, List.of(foreign)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other");
    }

    @Test
    void rejectsEntriesOutsideThePeriod() {
        LedgerEntry late = sale("10.00", WEEK.end().plusDays(1));

        assertThatThrownBy(() -> PayoutCalculator.calculate(SELLER, WEEK, BRL, List.of(late)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void ledgerEntriesRejectNegativeAmountsExceptAdjustments() {
        assertThatThrownBy(() -> sale("-1.00", DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(adjustment("-1.00", DAY).amount().isNegative()).isTrue();
    }
}
