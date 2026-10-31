package io.github.brunostsilva.payoutops.domain;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayoutHoldTest {

    private static final PayoutPeriod WEEK = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));
    private static final Instant PROPOSED_AT = Instant.parse("2026-10-31T10:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-31T11:00:00Z");

    private final PayoutHold pending = PayoutHold.propose(SELLER, WEEK, "two chargebacks", "agent", PROPOSED_AT);

    @Test
    void proposalsStartPendingWithoutDecision() {
        assertThat(pending.status()).isEqualTo(HoldStatus.PENDING);
        assertThat(pending.isPending()).isTrue();
        assertThat(pending.decision()).isNull();
    }

    @Test
    void approvalRecordsWhoDecidedAndWhen() {
        PayoutHold approved = pending.approve("ana", "confirmed with risk team", DECIDED_AT);

        assertThat(approved.status()).isEqualTo(HoldStatus.APPROVED);
        assertThat(approved.id()).isEqualTo(pending.id());
        assertThat(approved.decision()).isEqualTo(new PayoutHold.Decision("ana", DECIDED_AT, "confirmed with risk team"));
        assertThat(pending.isPending()).as("decisions return a new instance").isTrue();
    }

    @Test
    void approvalNoteIsOptionalButRejectionNoteIsNot() {
        assertThat(pending.approve("ana", null, DECIDED_AT).status()).isEqualTo(HoldStatus.APPROVED);
        assertThatThrownBy(() -> pending.reject("ana", " ", DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note");
    }

    @Test
    void aHoldCanOnlyBeDecidedOnce() {
        PayoutHold rejected = pending.reject("ana", "false positive", DECIDED_AT);

        assertThatThrownBy(() -> rejected.approve("bob", null, DECIDED_AT))
                .isInstanceOf(HoldAlreadyDecidedException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void decisionRequiresAnAnalyst() {
        assertThatThrownBy(() -> pending.approve("", null, DECIDED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionMustMatchStatus() {
        assertThatThrownBy(() -> new PayoutHold(
                        pending.id(), SELLER, WEEK, "reason", HoldStatus.APPROVED, "agent", PROPOSED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
