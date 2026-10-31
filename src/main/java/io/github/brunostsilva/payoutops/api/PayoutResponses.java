package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.domain.CommissionRule;
import io.github.brunostsilva.payoutops.domain.Money;
import io.github.brunostsilva.payoutops.domain.PayoutBreakdown;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JSON representations returned by the API, decoupled from the domain model. */
public final class PayoutResponses {

    private PayoutResponses() {
    }

    public record MoneyResponse(BigDecimal amount, String currency) {
        static MoneyResponse from(Money money) {
            return new MoneyResponse(money.amount(), money.currency().getCurrencyCode());
        }
    }

    public record PayoutResponse(
            String sellerId,
            LocalDate periodStart,
            LocalDate periodEnd,
            MoneyResponse grossSales,
            MoneyResponse commissions,
            MoneyResponse refunds,
            MoneyResponse chargebacks,
            MoneyResponse adjustments,
            MoneyResponse net,
            int entryCount) {

        static PayoutResponse from(PayoutBreakdown payout) {
            return new PayoutResponse(
                    payout.sellerId().value(),
                    payout.period().start(),
                    payout.period().end(),
                    MoneyResponse.from(payout.grossSales()),
                    MoneyResponse.from(payout.commissions()),
                    MoneyResponse.from(payout.refunds()),
                    MoneyResponse.from(payout.chargebacks()),
                    MoneyResponse.from(payout.adjustments()),
                    MoneyResponse.from(payout.net()),
                    payout.entryCount());
        }
    }

    public record DriverResponse(String component, MoneyResponse effectOnNet) {
        static DriverResponse from(PayoutComparison.Driver driver) {
            return new DriverResponse(driver.component().name(), MoneyResponse.from(driver.effectOnNet()));
        }
    }

    public record PayoutComparisonResponse(
            PayoutResponse previous,
            PayoutResponse current,
            MoneyResponse netChange,
            BigDecimal netChangePercent,
            List<DriverResponse> drivers) {

        static PayoutComparisonResponse from(PayoutComparison comparison) {
            return new PayoutComparisonResponse(
                    PayoutResponse.from(comparison.previous()),
                    PayoutResponse.from(comparison.current()),
                    MoneyResponse.from(comparison.netChange()),
                    comparison.netChangePercent().orElse(null),
                    comparison.drivers().stream().map(DriverResponse::from).toList());
        }
    }

    public record CommissionRuleResponse(BigDecimal rate, LocalDate validFrom, String reason) {
        static CommissionRuleResponse from(CommissionRule rule) {
            return new CommissionRuleResponse(rule.rate(), rule.validFrom(), rule.reason());
        }
    }
}
