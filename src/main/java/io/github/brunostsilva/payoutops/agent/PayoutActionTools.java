package io.github.brunostsilva.payoutops.agent;

import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutHoldResponse;
import io.github.brunostsilva.payoutops.application.HoldProposal;
import io.github.brunostsilva.payoutops.application.PayoutHoldService;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
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
    static final String APPROVED_NOTE =
            "An analyst already APPROVED a hold for this seller and week, so this payout is held. "
                    + "No new proposal was created.";
    static final String EXISTING_PENDING_NOTE =
            "A proposal for this seller and week was already PENDING (reason: %s). This request did NOT create a "
                    + "new proposal and its reason was not recorded. Nothing has been held yet: an analyst must "
                    + "approve or reject the existing proposal.";

    private final PayoutHoldService holdService;

    public PayoutActionTools(PayoutHoldService holdService) {
        this.holdService = holdService;
    }

    @Tool(description = """
            Proposes holding a seller's payout for the Monday-to-Sunday week containing weekOf. It does NOT hold
            anything: it records a PENDING proposal that an analyst must approve or reject. If that seller and week
            already have a pending or approved hold, that one is returned instead of creating a duplicate; read the
            returned status and note before answering. Only use it when the user explicitly asks to hold or block
            a payout.""")
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
        HoldProposal proposal = holdService.propose(new SellerId(sellerId), period, reason, PROPOSED_BY);
        PayoutHold hold = proposal.hold();
        String note;
        if (!hold.isPending()) {
            note = APPROVED_NOTE;
        } else if (proposal.created()) {
            note = PENDING_NOTE;
        } else {
            note = EXISTING_PENDING_NOTE.formatted(hold.reason());
        }
        return new ProposedHold(PayoutHoldResponse.from(hold), proposal.created(), note);
    }

    /**
     * Tool result: the hold, whether this call created it, and an explicit note for the model on what
     * has (not) been executed.
     */
    public record ProposedHold(PayoutHoldResponse hold, boolean created, String note) {
    }
}
