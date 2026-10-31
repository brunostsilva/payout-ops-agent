package io.github.brunostsilva.payoutops.application;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunostsilva.payoutops.domain.HoldAlreadyDecidedException;
import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class PayoutHoldServiceTest {

    private static final PayoutPeriod WEEK = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));

    private final SellerRepository sellers =
            id -> id.equals(SELLER) ? Optional.of(new Seller(SELLER, "Test Seller", BRL)) : Optional.empty();
    private final InMemoryPayoutHoldRepository holds = new InMemoryPayoutHoldRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private final PayoutHoldService service = new PayoutHoldService(sellers, holds, clock);

    @Test
    void proposalIsPendingAndListed() {
        PayoutHold hold = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();

        assertThat(hold.status()).isEqualTo(HoldStatus.PENDING);
        assertThat(hold.proposedAt()).isEqualTo(clock.instant());
        assertThat(service.list(HoldStatus.PENDING)).containsExactly(hold);
    }

    @Test
    void proposingAgainForTheSamePeriodReturnsTheExistingPendingHold() {
        PayoutHold first = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();
        PayoutHold second = service.propose(SELLER, WEEK, "asked again", "agent").hold();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.list(HoldStatus.PENDING)).hasSize(1);
    }

    @Test
    void theResultSaysWhetherTheProposalWasCreatedOrReused() {
        HoldProposal first = service.propose(SELLER, WEEK, "first reason", "agent");
        HoldProposal second = service.propose(SELLER, WEEK, "second reason", "agent");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.hold().id()).isEqualTo(first.hold().id());
        assertThat(second.hold().reason()).as("the first reason is kept").isEqualTo("first reason");
    }

    @Test
    void aNewProposalIsAllowedOnceThePreviousOneWasRejected() {
        PayoutHold first = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();
        service.reject(first.id(), "ana", "false positive");

        PayoutHold second = service.propose(SELLER, WEEK, "new chargeback", "agent").hold();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(HoldStatus.PENDING);
    }

    @Test
    void proposingForAnAlreadyApprovedWeekReturnsTheApprovedHold() {
        PayoutHold first = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();
        service.approve(first.id(), "ana", null);

        PayoutHold again = service.propose(SELLER, WEEK, "asked again", "agent").hold();

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.status()).isEqualTo(HoldStatus.APPROVED);
        assertThat(service.list(HoldStatus.PENDING)).isEmpty();
    }

    @Test
    void approveAndRejectAreFinal() {
        PayoutHold hold = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();

        PayoutHold approved = service.approve(hold.id(), "ana", null);

        assertThat(approved.status()).isEqualTo(HoldStatus.APPROVED);
        assertThat(service.get(hold.id()).status()).isEqualTo(HoldStatus.APPROVED);
        assertThatThrownBy(() -> service.reject(hold.id(), "bob", "changed my mind"))
                .isInstanceOf(HoldAlreadyDecidedException.class);
    }

    @Test
    void aDecisionLostToAConcurrentOneIsRejected() {
        PayoutHold hold = service.propose(SELLER, WEEK, "two chargebacks", "agent").hold();
        // Simulates another analyst deciding between our read and our write.
        holds.recordDecision(hold.reject("bob", "false positive", clock.instant()));

        assertThatThrownBy(() -> service.approve(hold.id(), "ana", null))
                .isInstanceOf(HoldAlreadyDecidedException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void aProposalThatLosesARaceToAHoldRejectedMeanwhileIsRetried() {
        // First insert: a concurrent proposal took the unique index and was rejected before our re-check,
        // so the database says "duplicate" but the lookup finds no active hold.
        InMemoryPayoutHoldRepository racingHolds = new InMemoryPayoutHoldRepository() {
            private boolean lostTheRace;

            @Override
            public synchronized void insert(PayoutHold hold) {
                if (!lostTheRace) {
                    lostTheRace = true;
                    throw new DuplicateKeyException("payout_holds_one_active_per_period");
                }
                super.insert(hold);
            }
        };
        PayoutHoldService racingService = new PayoutHoldService(sellers, racingHolds, clock);

        PayoutHold hold = racingService.propose(SELLER, WEEK, "two chargebacks", "agent").hold();

        assertThat(hold.status()).isEqualTo(HoldStatus.PENDING);
        assertThat(racingHolds.findActive(SELLER, WEEK)).contains(hold);
    }

    @Test
    void unknownSellersAndHoldsAreReported() {
        assertThatThrownBy(() -> service.propose(new SellerId("ghost"), WEEK, "reason", "agent"))
                .isInstanceOf(SellerNotFoundException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(HoldNotFoundException.class);
    }
}
