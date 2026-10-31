package io.github.brunostsilva.payoutops.agent;

import io.github.brunostsilva.payoutops.api.PayoutResponses.CommissionRuleResponse;
import io.github.brunostsilva.payoutops.api.PayoutResponses.LedgerEntryResponse;
import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutComparisonResponse;
import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutResponse;
import io.github.brunostsilva.payoutops.application.PayoutService;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The only way the agent can read data. Every tool is read-only and delegates to the tested
 * {@link PayoutService}, so all numbers in an answer come from deterministic code.
 */
@Component
public class PayoutTools {

    private final PayoutService payoutService;

    public PayoutTools(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Tool(description = """
            Compares a seller's net payout for the Monday-to-Sunday week containing weekOf with the previous week.
            Returns both weekly breakdowns, the net change, the percentage change and the drivers: the effect of
            each component (sales, commissions, refunds, chargebacks, adjustments) on the net change, largest
            impact first. The drivers always add up to the net change.""")
    public PayoutComparisonResponse comparePayoutWithPreviousWeek(
            @ToolParam(description = "Seller id, e.g. seller-123") String sellerId,
            @ToolParam(description = "Any date inside the week to analyse, formatted YYYY-MM-DD") String weekOf) {
        return PayoutComparisonResponse.from(payoutService.compareWithPreviousPeriod(seller(sellerId), week(weekOf)));
    }

    @Tool(description = """
            Returns a seller's payout breakdown (gross sales, commissions, refunds, chargebacks, adjustments and net)
            for the Monday-to-Sunday week containing weekOf.""")
    public PayoutResponse getWeeklyPayout(
            @ToolParam(description = "Seller id, e.g. seller-123") String sellerId,
            @ToolParam(description = "Any date inside the week, formatted YYYY-MM-DD") String weekOf) {
        return PayoutResponse.from(payoutService.payoutFor(seller(sellerId), week(weekOf)));
    }

    @Tool(description = """
            Lists the individual ledger entries of a seller in the Monday-to-Sunday week containing weekOf: sales,
            commissions, refunds, chargebacks (with the dispute reason) and adjustments (with their description).
            Use it to find which orders or events caused a change.""")
    public List<LedgerEntryResponse> getLedgerEntries(
            @ToolParam(description = "Seller id, e.g. seller-123") String sellerId,
            @ToolParam(description = "Any date inside the week, formatted YYYY-MM-DD") String weekOf) {
        return payoutService.ledgerEntries(seller(sellerId), week(weekOf)).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    @Tool(description = "Returns a seller's commission rate history, oldest first, with the reason for each change.")
    public List<CommissionRuleResponse> getCommissionRules(
            @ToolParam(description = "Seller id, e.g. seller-123") String sellerId) {
        return payoutService.commissionRules(seller(sellerId)).stream()
                .map(CommissionRuleResponse::from)
                .toList();
    }

    private static SellerId seller(String sellerId) {
        return new SellerId(sellerId);
    }

    private static PayoutPeriod week(String weekOf) {
        try {
            return PayoutPeriod.weekOf(LocalDate.parse(weekOf));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("weekOf must be a date formatted YYYY-MM-DD, got: " + weekOf);
        }
    }
}
