package io.github.brunostsilva.payoutops.domain;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutTrendTest {

    private static final PayoutPeriod FIRST_WEEK = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 5));

    /** A payout whose net is {@code net}, carried by an adjustment so it can be zero or negative. */
    private static PayoutBreakdown payout(SellerId seller, PayoutPeriod period, String net) {
        Money zero = Money.zero(BRL);
        return new PayoutBreakdown(seller, period, zero, zero, zero, zero, Money.of(net, "BRL"), 1);
    }

    /** Consecutive weeks from FIRST_WEEK with these net payouts. */
    private static PayoutTrend trend(String... nets) {
        List<PayoutBreakdown> weeks = new ArrayList<>();
        PayoutPeriod period = FIRST_WEEK;
        for (String net : nets) {
            weeks.add(payout(SELLER, period, net));
            period = PayoutPeriod.weekOf(period.start().plusWeeks(1));
        }
        return new PayoutTrend(weeks);
    }

    @Test
    void steadyGrowthOfTenPercentAWeek() {
        PayoutTrend trend = trend("1000.00", "1100.00", "1210.00", "1331.00");

        assertThat(trend.averageGrowthPercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("10.0"));
        assertThat(trend.totalChangePercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("33.1"));
    }

    @Test
    void steadyDeclineOfTenPercentAWeek() {
        PayoutTrend trend = trend("1000.00", "900.00", "810.00", "729.00");

        assertThat(trend.averageGrowthPercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("-10.0"));
        assertThat(trend.totalChangePercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("-27.1"));
    }

    @Test
    void theNthRootIsTakenInDecimalArithmetic() {
        // BigDecimal.rootn (Java 27): the cube root of 1.331 is exactly 1.1, with no binary floating point
        // in between. This is the step behind a 10% average over three periods of growth.
        assertThat(new BigDecimal("1.331").rootn(3, MathContext.DECIMAL64)).isEqualByComparingTo("1.1");
        assertThat(new BigDecimal("0.729").rootn(3, MathContext.DECIMAL64)).isEqualByComparingTo("0.9");
    }

    @Test
    void averageGrowthDependsOnlyOnTheFirstAndLastPeriods() {
        assertThat(trend("1000.00", "50.00", "1210.00").averageGrowthPercent())
                .hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("10.0"));
    }

    @Test
    void theLargestWeeklyChangeTellsAOneOffDropFromASteadyDeclineWithTheSameAverage() {
        // Both lose 27.1% over four weeks, so both average -10.0% a week...
        PayoutTrend steady = trend("1000.00", "900.00", "810.00", "729.00");
        PayoutTrend oneOff = trend("1000.00", "1000.00", "1000.00", "729.00");
        assertThat(steady.averageGrowthPercent()).isEqualTo(oneOff.averageGrowthPercent());

        // ...but the steady decline loses about that much every week, the one-off all in its last week.
        assertThat(steady.largestPeriodChange()).hasValueSatisfying(change -> {
            assertThat(change.percent()).isEqualByComparingTo("-10.0");
            assertThat(change.period()).isEqualTo(steady.periods().get(1).period()); // earliest of the ties
        });
        assertThat(oneOff.largestPeriodChange()).hasValueSatisfying(change -> {
            assertThat(change.percent()).isEqualByComparingTo("-27.1");
            assertThat(change.period()).isEqualTo(oneOff.last().period());
        });
    }

    @Test
    void theLargestWeeklyChangeIsTheLargestInEitherDirectionAndSkipsChangesFromZero() {
        assertThat(trend("1000.00", "1500.00", "1200.00").largestPeriodChange())
                .hasValueSatisfying(change -> assertThat(change.percent()).isEqualByComparingTo("50.0"));
        assertThat(trend("1000.00", "1100.00", "550.00").largestPeriodChange())
                .hasValueSatisfying(change -> assertThat(change.percent()).isEqualByComparingTo("-50.0"));
        assertThat(trend("0.00", "500.00", "400.00").largestPeriodChange())
                .hasValueSatisfying(change -> assertThat(change.percent()).isEqualByComparingTo("-20.0"));
        assertThat(trend("0.00", "500.00").largestPeriodChange()).isEmpty();
    }

    @Test
    void overTwoWeeksItIsTheWeekOverWeekChange() {
        PayoutTrend trend = trend("8800.00", "6380.00");
        PayoutComparison comparison = new PayoutComparison(trend.first(), trend.last());

        assertThat(trend.averageGrowthPercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("-27.5"));
        assertThat(trend.totalChangePercent()).isEqualTo(comparison.netChangePercent());
    }

    @Test
    void averageGrowthIsUndefinedFromOrToAPayoutThatIsNotPositive() {
        assertThat(trend("0.00", "100.00").averageGrowthPercent()).isEmpty();
        assertThat(trend("0.00", "100.00").totalChangePercent()).isEmpty();
        assertThat(trend("-100.00", "50.00").averageGrowthPercent()).isEmpty();
        assertThat(trend("100.00", "0.00").averageGrowthPercent()).isEmpty();

        PayoutTrend toNegative = trend("100.00", "-50.00");
        assertThat(toNegative.averageGrowthPercent()).isEmpty();
        assertThat(toNegative.totalChangePercent()).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("-150.0"));
    }

    @Test
    void needsAtLeastTwoConsecutivePeriodsOfTheSameSeller() {
        PayoutPeriod next = PayoutPeriod.weekOf(FIRST_WEEK.start().plusWeeks(1));
        PayoutPeriod skipped = PayoutPeriod.weekOf(FIRST_WEEK.start().plusWeeks(2));

        assertThatThrownBy(() -> new PayoutTrend(List.of(payout(SELLER, FIRST_WEEK, "10.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 periods");
        assertThatThrownBy(() -> new PayoutTrend(List.of(payout(SELLER, FIRST_WEEK, "10.00"), payout(SELLER, skipped, "10.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive");
        assertThatThrownBy(() -> new PayoutTrend(List.of(payout(SELLER, next, "10.00"), payout(SELLER, FIRST_WEEK, "10.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive");
        assertThatThrownBy(() -> new PayoutTrend(List.of(
                        payout(SELLER, FIRST_WEEK, "10.00"), payout(new SellerId("seller-2"), next, "10.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different sellers");
    }
}
