package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.HoldAlreadyDecidedException;
import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
import io.github.brunostsilva.payoutops.domain.PayoutHoldRepository;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Proposing a hold never touches the payout; only an analyst's approval does. Proposals are
 * idempotent per seller and period, and each hold can be decided exactly once, even concurrently.
 *
 * <p>Every proposal and decision is logged as an audit line (who, what, when). Free-text fields
 * (the reason and the decision note) are not logged, since they may contain customer details.
 *
 * <p>Proposals and decisions are also counted ({@value #PROPOSED_METRIC}, {@value #DECIDED_METRIC},
 * {@value #DECISION_CONFLICTS_METRIC}). Tags only take bounded values: never ids or analyst names.
 */
@Service
public class PayoutHoldService {

    static final int MAX_PROPOSAL_ATTEMPTS = 3;
    static final String PROPOSED_METRIC = "payout.holds.proposed";
    static final String DECIDED_METRIC = "payout.holds.decided";
    static final String DECISION_CONFLICTS_METRIC = "payout.holds.decision_conflicts";

    private static final Logger log = LoggerFactory.getLogger(PayoutHoldService.class);

    private final SellerRepository sellers;
    private final PayoutHoldRepository holds;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Counter decisionConflicts;

    public PayoutHoldService(SellerRepository sellers, PayoutHoldRepository holds, Clock clock, MeterRegistry meters) {
        this.sellers = sellers;
        this.holds = holds;
        this.clock = clock;
        this.meters = meters;
        this.decisionConflicts = Counter.builder(DECISION_CONFLICTS_METRIC)
                .description("Decisions refused because the hold had already been approved or rejected")
                .register(meters);
    }

    /**
     * Returns the seller's active (pending or approved) hold for the period if there is one, otherwise
     * creates a pending proposal. A rejected hold doesn't block a new proposal. The result says which
     * of the two happened, so callers never report a reused hold as a new proposal.
     */
    public HoldProposal propose(SellerId sellerId, PayoutPeriod period, String reason, String proposedBy) {
        if (sellers.findById(sellerId).isEmpty()) {
            throw new SellerNotFoundException(sellerId);
        }
        for (int attempt = 1; ; attempt++) {
            var existing = holds.findActive(sellerId, period);
            if (existing.isPresent()) {
                PayoutHold hold = existing.get();
                log.atInfo()
                        .setMessage("Hold proposal by {} for seller {} week {} reused existing {} hold {}")
                        .addArgument(proposedBy)
                        .addArgument(sellerId)
                        .addArgument(period.start())
                        .addArgument(hold.status())
                        .addArgument(hold.id())
                        .addKeyValue("hold.id", hold.id())
                        .addKeyValue("hold.status", hold.status())
                        .addKeyValue("hold.created", false)
                        .addKeyValue("seller.id", sellerId)
                        .addKeyValue("hold.proposed_by", proposedBy)
                        .log();
                countProposal("reused_" + tagValue(hold.status()), proposedBy);
                return new HoldProposal(hold, false);
            }
            PayoutHold hold = PayoutHold.propose(sellerId, period, reason, proposedBy, clock.instant());
            try {
                holds.insert(hold);
                log.atInfo()
                        .setMessage("Hold {} proposed by {} for seller {} week {}")
                        .addArgument(hold.id())
                        .addArgument(proposedBy)
                        .addArgument(sellerId)
                        .addArgument(period.start())
                        .addKeyValue("hold.id", hold.id())
                        .addKeyValue("hold.status", hold.status())
                        .addKeyValue("hold.created", true)
                        .addKeyValue("seller.id", sellerId)
                        .addKeyValue("hold.proposed_by", proposedBy)
                        .log();
                countProposal("created", proposedBy);
                return new HoldProposal(hold, true);
            } catch (DuplicateKeyException concurrentProposal) {
                // A concurrent proposal won the unique index. Look again: usually it is still active and we
                // return it; if it was rejected in the meantime, the period is free and we try once more.
                log.atDebug()
                        .setMessage("Hold proposal for seller {} week {} lost a race (attempt {}), retrying")
                        .addArgument(sellerId)
                        .addArgument(period.start())
                        .addArgument(attempt)
                        .log();
                if (attempt == MAX_PROPOSAL_ATTEMPTS) {
                    throw concurrentProposal;
                }
            }
        }
    }

    public PayoutHold approve(UUID holdId, String analyst, String note) {
        return decide(holdId, analyst, hold -> hold.approve(analyst, note, clock.instant()));
    }

    public PayoutHold reject(UUID holdId, String analyst, String note) {
        return decide(holdId, analyst, hold -> hold.reject(analyst, note, clock.instant()));
    }

    public PayoutHold get(UUID holdId) {
        return holds.findById(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    public List<PayoutHold> list(HoldStatus status) {
        return holds.findByStatus(status);
    }

    private PayoutHold decide(UUID holdId, String analyst, UnaryOperator<PayoutHold> decision) {
        PayoutHold current = get(holdId);
        if (!current.isPending()) {
            throw conflict(holdId, analyst, current.status());
        }
        PayoutHold decided = decision.apply(current);
        if (!holds.recordDecision(decided)) {
            // Another analyst decided it between our read and our write.
            throw conflict(holdId, analyst, holds.findById(holdId).map(PayoutHold::status).orElse(current.status()));
        }
        log.atInfo()
                .setMessage("Hold {} {} by {}")
                .addArgument(holdId)
                .addArgument(decided.status())
                .addArgument(analyst)
                .addKeyValue("hold.id", holdId)
                .addKeyValue("hold.status", decided.status())
                .addKeyValue("hold.decided_by", analyst)
                .addKeyValue("seller.id", decided.sellerId())
                .log();
        Counter.builder(DECIDED_METRIC)
                .description("Holds approved or rejected by an analyst")
                .tag("status", tagValue(decided.status()))
                .register(meters)
                .increment();
        return decided;
    }

    private HoldAlreadyDecidedException conflict(UUID holdId, String analyst, HoldStatus status) {
        decisionConflicts.increment();
        log.atWarn()
                .setMessage("Hold {} was already {} when {} tried to decide it")
                .addArgument(holdId)
                .addArgument(status)
                .addArgument(analyst)
                .addKeyValue("hold.id", holdId)
                .addKeyValue("hold.status", status)
                .addKeyValue("hold.decided_by", analyst)
                .log();
        return new HoldAlreadyDecidedException(holdId, status);
    }

    /** {@code proposedBy} is one of the fixed proposer ids (agent, MCP client), so it is a safe tag. */
    private void countProposal(String outcome, String proposedBy) {
        Counter.builder(PROPOSED_METRIC)
                .description("Hold proposals, by whether a new hold was created or an active one was reused")
                .tag("outcome", outcome)
                .tag("proposed_by", proposedBy)
                .register(meters)
                .increment();
    }

    private static String tagValue(HoldStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
