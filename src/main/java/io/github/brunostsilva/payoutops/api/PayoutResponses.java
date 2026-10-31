package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.domain.CommissionRule;
import io.github.brunostsilva.payoutops.domain.LedgerEntry;
import io.github.brunostsilva.payoutops.domain.Money;
import io.github.brunostsilva.payoutops.domain.PayoutBreakdown;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * JSON representations of payout data, decoupled from the domain model. Shared by the REST API and
 * the agent tools, so the LLM sees exactly the same shapes as API clients.
 */
public final class PayoutResponses {

    private PayoutResponses() {
    }

    public record MoneyResponse(BigDecimal amount, String currency) {
        public static MoneyResponse from(Money money) {
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

        public static PayoutResponse from(PayoutBreakdown payout) {
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
        public static DriverResponse from(PayoutComparison.Driver driver) {
            return new DriverResponse(driver.component().name(), MoneyResponse.from(driver.effectOnNet()));
        }
    }

    public record PayoutComparisonResponse(
            PayoutResponse previous,
            PayoutResponse current,
            MoneyResponse netChange,
            BigDecimal netChangePercent,
            List<DriverResponse> drivers) {

        public static PayoutComparisonResponse from(PayoutComparison comparison) {
            return new PayoutComparisonResponse(
                    PayoutResponse.from(comparison.previous()),
                    PayoutResponse.from(comparison.current()),
                    MoneyResponse.from(comparison.netChange()),
                    comparison.netChangePercent().orElse(null),
                    comparison.drivers().stream().map(DriverResponse::from).toList());
        }
    }

    public record CommissionRuleResponse(BigDecimal rate, LocalDate validFrom, String reason) {
        public static CommissionRuleResponse from(CommissionRule rule) {
            return new CommissionRuleResponse(rule.rate(), rule.validFrom(), rule.reason());
        }
    }

    /** A ledger entry; {@code note} carries the chargeback reason or the adjustment description. */
    public record LedgerEntryResponse(
            String id, String type, LocalDate occurredOn, MoneyResponse amount, String orderId, String note) {

        public static LedgerEntryResponse from(LedgerEntry entry) {
            MoneyResponse amount = MoneyResponse.from(entry.amount());
            return switch (entry) {
                case LedgerEntry.Sale sale ->
                        new LedgerEntryResponse(sale.id(), "SALE", sale.occurredOn(), amount, sale.orderId(), null);
                case LedgerEntry.Commission commission -> new LedgerEntryResponse(
                        commission.id(), "COMMISSION", commission.occurredOn(), amount, commission.orderId(), null);
                case LedgerEntry.Refund refund ->
                        new LedgerEntryResponse(refund.id(), "REFUND", refund.occurredOn(), amount, refund.orderId(), null);
                case LedgerEntry.Chargeback chargeback -> new LedgerEntryResponse(
                        chargeback.id(), "CHARGEBACK", chargeback.occurredOn(), amount, chargeback.orderId(),
                        chargeback.reason());
                case LedgerEntry.Adjustment adjustment -> new LedgerEntryResponse(
                        adjustment.id(), "ADJUSTMENT", adjustment.occurredOn(), amount, null, adjustment.description());
            };
        }
    }
}
