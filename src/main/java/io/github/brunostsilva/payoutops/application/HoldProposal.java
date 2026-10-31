package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.PayoutHold;

/**
 * Outcome of a hold proposal: {@code created} is {@code false} when the seller and period already had
 * an active hold, which is returned instead (the new request and its reason are not recorded).
 */
public record HoldProposal(PayoutHold hold, boolean created) {
}
