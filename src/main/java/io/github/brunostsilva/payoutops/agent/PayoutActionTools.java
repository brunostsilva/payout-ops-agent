package io.github.brunostsilva.payoutops.agent;

import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutHoldResponse;
import io.github.brunostsilva.payoutops.application.PayoutHoldService;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The agent's only write capability, kept apart from the read-only {@link PayoutTools}: it can
 * propose a payout hold, which stays pending until an analyst approves or rejects it. It can never
 * hold, release or change a payout by itself.
 */
@Component
public class PayoutActionTools {

    static final String PROPOSED_BY = "payout-ops-agent";
    static final String PENDING_NOTE =
            "Proposal recorded as PENDING. Nothing has been held: an analyst must approve or reject it.";

    private final PayoutHoldService holdService;

    public PayoutActionTools(PayoutHoldService holdService) {
        this.holdService = holdService;
    }

    @Tool(description = """
            Proposes holding a seller's payout for the Monday-to-Sunday week containing weekOf. It does NOT hold
            anything: it records a PENDING proposal that an analyst must approve or reject. If a pending proposal
            already exists for that seller and week, that one is returned instead of creating a duplicate.
            Only use it when the user explicitly asks to hold or block a payout.""")
    public ProposedHold proposePayoutHold(
            @ToolParam(description = "Seller id, e.g. seller-123") String sellerId,
            @ToolParam(description = "Any date inside the week whose payout should be held, formatted YYYY-MM-DD")
                    String weekOf,
            @ToolParam(description = "Short factual reason, citing the data that justifies the hold") String reason) {
        PayoutPeriod period;
        try {
            period = PayoutPeriod.weekOf(LocalDate.parse(weekOf));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("weekOf must be a date formatted YYYY-MM-DD, got: " + weekOf);
        }
        var hold = holdService.propose(new SellerId(sellerId), period, reason, PROPOSED_BY);
        return new ProposedHold(PayoutHoldResponse.from(hold), PENDING_NOTE);
    }

    /** Tool result: the proposal plus an explicit reminder for the model that nothing was executed. */
    public record ProposedHold(PayoutHoldResponse hold, String note) {
    }
}
