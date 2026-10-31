package io.github.brunostsilva.payoutops.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to hold a seller's payout for a period. It starts {@link HoldStatus#PENDING} and only a
 * human decision moves it to {@link HoldStatus#APPROVED} or {@link HoldStatus#REJECTED}, exactly once.
 */
public record PayoutHold(
        UUID id,
        SellerId sellerId,
        PayoutPeriod period,
        String reason,
        HoldStatus status,
        String proposedBy,
        Instant proposedAt,
        Decision decision) {

    public PayoutHold {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(proposedAt, "proposedAt");
        requireText(reason, "reason");
        requireText(proposedBy, "proposedBy");
        if ((status == HoldStatus.PENDING) != (decision == null)) {
            throw new IllegalArgumentException("A hold has a decision if and only if it is no longer pending");
        }
    }

    public static PayoutHold propose(
            SellerId sellerId, PayoutPeriod period, String reason, String proposedBy, Instant proposedAt) {
        return new PayoutHold(
                UUID.randomUUID(), sellerId, period, reason, HoldStatus.PENDING, proposedBy, proposedAt, null);
    }

    public boolean isPending() {
        return status == HoldStatus.PENDING;
    }

    public PayoutHold approve(String analyst, String note, Instant decidedAt) {
        return decide(HoldStatus.APPROVED, new Decision(analyst, decidedAt, note));
    }

    /** Rejections must be justified, so the note is mandatory. */
    public PayoutHold reject(String analyst, String note, Instant decidedAt) {
        requireText(note, "note");
        return decide(HoldStatus.REJECTED, new Decision(analyst, decidedAt, note));
    }

    private PayoutHold decide(HoldStatus outcome, Decision decision) {
        if (!isPending()) {
            throw new HoldAlreadyDecidedException(id, status);
        }
        return new PayoutHold(id, sellerId, period, reason, outcome, proposedBy, proposedAt, decision);
    }

    public record Decision(String decidedBy, Instant decidedAt, String note) {

        public Decision {
            requireText(decidedBy, "decidedBy");
            Objects.requireNonNull(decidedAt, "decidedAt");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
