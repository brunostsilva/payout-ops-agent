package io.github.brunostsilva.payoutops.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayoutPeriodTest {

    @Test
    void weekOfReturnsMondayToSunday() {
        PayoutPeriod week = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 29));

        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void previousReturnsThePeriodOfSameLengthRightBefore() {
        PayoutPeriod previous = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26)).previous();

        assertThat(previous).isEqualTo(new PayoutPeriod(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
    }

    @Test
    void containsIsInclusiveOnBothEnds() {
        PayoutPeriod week = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));

        assertThat(week.contains(LocalDate.of(2026, 10, 26))).isTrue();
        assertThat(week.contains(LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(week.contains(LocalDate.of(2026, 11, 2))).isFalse();
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> new PayoutPeriod(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
