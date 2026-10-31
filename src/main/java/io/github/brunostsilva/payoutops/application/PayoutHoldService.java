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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Proposing a hold never touches the payout; only an analyst's approval does. Proposals are
 * idempotent per seller and period, and each hold can be decided exactly once, even concurrently.
 */
@Service
public class PayoutHoldService {

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
     * creates a pending proposal. A rejected hold doesn't block a new proposal.
     */
    public PayoutHold propose(SellerId sellerId, PayoutPeriod period, String reason, String proposedBy) {
        if (sellers.findById(sellerId).isEmpty()) {
            throw new SellerNotFoundException(sellerId);
        }
        var existing = holds.findActive(sellerId, period);
        if (existing.isPresent()) {
            return existing.get();
        }
        PayoutHold hold = PayoutHold.propose(sellerId, period, reason, proposedBy, clock.instant());
        try {
            holds.insert(hold);
            return hold;
        } catch (DuplicateKeyException concurrentProposal) {
            return holds.findActive(sellerId, period).orElseThrow(() -> concurrentProposal);
        }
    }

    public PayoutHold approve(UUID holdId, String analyst, String note) {
        return decide(holdId, hold -> hold.approve(analyst, note, clock.instant()));
    }

    public PayoutHold reject(UUID holdId, String analyst, String note) {
        return decide(holdId, hold -> hold.reject(analyst, note, clock.instant()));
    }

    public PayoutHold get(UUID holdId) {
        return holds.findById(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    public List<PayoutHold> list(HoldStatus status) {
        return holds.findByStatus(status);
    }

    private PayoutHold decide(UUID holdId, UnaryOperator<PayoutHold> decision) {
        PayoutHold current = get(holdId);
        PayoutHold decided = decision.apply(current);
        if (!holds.recordDecision(decided)) {
            HoldStatus winner = holds.findById(holdId).map(PayoutHold::status).orElse(current.status());
            throw new HoldAlreadyDecidedException(holdId, winner);
        }
        return decided;
    }
}
