package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.HoldAlreadyDecidedException;
import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
import io.github.brunostsilva.payoutops.domain.PayoutHoldRepository;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.time.Clock;
import java.util.List;
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
 */
@Service
public class PayoutHoldService {

    static final int MAX_PROPOSAL_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(PayoutHoldService.class);

    private final SellerRepository sellers;
    private final PayoutHoldRepository holds;
    private final Clock clock;

    public PayoutHoldService(SellerRepository sellers, PayoutHoldRepository holds, Clock clock) {
        this.sellers = sellers;
        this.holds = holds;
        this.clock = clock;
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
        PayoutHold decided = decision.apply(current);
        if (!holds.recordDecision(decided)) {
            HoldStatus winner = holds.findById(holdId).map(PayoutHold::status).orElse(current.status());
            log.atWarn()
                    .setMessage("Decision {} on hold {} by {} lost to a concurrent decision ({})")
                    .addArgument(decided.status())
                    .addArgument(holdId)
                    .addArgument(analyst)
                    .addArgument(winner)
                    .addKeyValue("hold.id", holdId)
                    .addKeyValue("hold.decided_by", analyst)
                    .log();
            throw new HoldAlreadyDecidedException(holdId, winner);
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
        return decided;
    }
}
