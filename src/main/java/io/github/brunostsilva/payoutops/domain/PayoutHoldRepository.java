package io.github.brunostsilva.payoutops.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutHoldRepository {

    /** Stores a new pending hold. Fails if the seller already has an active hold for the same period. */
    void insert(PayoutHold hold);

    /**
     * Persists the decision of a hold that is still pending in storage.
     *
     * @return {@code false} if the stored hold was no longer pending (someone else decided it first)
     */
    boolean recordDecision(PayoutHold decided);

    Optional<PayoutHold> findById(UUID id);

    /** The seller's pending or approved hold for the period, if any (see {@link PayoutHold#isActive()}). */
    Optional<PayoutHold> findActive(SellerId sellerId, PayoutPeriod period);

    /** Holds with the given status, oldest proposal first. */
    List<PayoutHold> findByStatus(HoldStatus status);
}
