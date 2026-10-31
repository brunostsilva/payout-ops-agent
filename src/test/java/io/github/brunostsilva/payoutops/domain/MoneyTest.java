package io.github.brunostsilva.payoutops.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesScaleSoEqualAmountsAreEqual() {
        assertThat(Money.of("10", "BRL")).isEqualTo(Money.of("10.00", "BRL"));
        assertThat(Money.of("10.005", "BRL").amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void usesTheCurrencyMinorUnits() {
        assertThat(Money.of("150", "JPY").amount().scale()).isZero();
    }

    @Test
    void addsAndSubtracts() {
        Money result = Money.of("100.00", "BRL").plus(Money.of("20.50", "BRL")).minus(Money.of("0.50", "BRL"));

        assertThat(result).isEqualTo(Money.of("120.00", "BRL"));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "BRL").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BRL")
                .hasMessageContaining("USD");
    }

    @Test
    void reportsSignAndZero() {
        assertThat(Money.of("-1", "BRL").isNegative()).isTrue();
        assertThat(Money.zero(Currency.getInstance("BRL")).isZero()).isTrue();
        assertThat(Money.of("-3", "BRL").abs()).isEqualTo(Money.of("3", "BRL"));
        assertThat(Money.of("3", "BRL").negate()).isEqualTo(new Money(new BigDecimal("-3"), Currency.getInstance("BRL")));
    }
}
