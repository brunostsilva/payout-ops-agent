package io.github.brunostsilva.payoutops.application;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.commission;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.sale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunostsilva.payoutops.domain.CommissionRuleRepository;
import io.github.brunostsilva.payoutops.domain.LedgerEntry;
import io.github.brunostsilva.payoutops.domain.LedgerRepository;
import io.github.brunostsilva.payoutops.domain.Money;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PayoutServiceTest {

    private static final PayoutPeriod CURRENT = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));

    private final List<LedgerEntry> ledger = List.of(
            sale("1000.00", CURRENT.previous().start()),
            commission("100.00", CURRENT.previous().start()),
            sale("800.00", CURRENT.start()),
            commission("80.00", CURRENT.start()));

    private final SellerRepository sellers =
            id -> id.equals(SELLER) ? Optional.of(new Seller(SELLER, "Test Seller", BRL)) : Optional.empty();

    private final LedgerRepository ledgerRepository = (sellerId, period) -> ledger.stream()
            .filter(entry -> entry.sellerId().equals(sellerId) && period.contains(entry.occurredOn()))
            .toList();

    private final CommissionRuleRepository commissionRules = sellerId -> List.of();

    private final PayoutService service = new PayoutService(sellers, ledgerRepository, commissionRules);

    @Test
    void calculatesPayoutForThePeriodOnly() {
        assertThat(service.payoutFor(SELLER, CURRENT).net()).isEqualTo(Money.of("720.00", "BRL"));
    }

    @Test
    void comparesWithThePreviousPeriod() {
        PayoutComparison comparison = service.compareWithPreviousPeriod(SELLER, CURRENT);

        assertThat(comparison.previous().net()).isEqualTo(Money.of("900.00", "BRL"));
        assertThat(comparison.netChange()).isEqualTo(Money.of("-180.00", "BRL"));
    }

    @Test
    void unknownSellerIsReported() {
        SellerId unknown = new SellerId("nope");

        assertThatThrownBy(() -> service.payoutFor(unknown, CURRENT))
                .isInstanceOf(SellerNotFoundException.class)
                .hasMessageContaining("nope");
        assertThatThrownBy(() -> service.commissionRules(unknown)).isInstanceOf(SellerNotFoundException.class);
    }
}
