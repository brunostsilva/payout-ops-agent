package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
import io.github.brunostsilva.payoutops.domain.PayoutHoldRepository;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

/** Mirrors the guarantees of the database: one active hold per seller and period, decide-once updates. */
public class InMemoryPayoutHoldRepository implements PayoutHoldRepository {

    private final Map<UUID, PayoutHold> holds = new LinkedHashMap<>();

    @Override
    public synchronized void insert(PayoutHold hold) {
        if (findActive(hold.sellerId(), hold.period()).isPresent()) {
            throw new DuplicateKeyException("An active hold already exists for " + hold.sellerId());
        }
        holds.put(hold.id(), hold);
    }

    @Override
    public synchronized boolean recordDecision(PayoutHold decided) {
        PayoutHold stored = holds.get(decided.id());
        if (stored == null || !stored.isPending()) {
            return false;
        }
        holds.put(decided.id(), decided);
        return true;
    }

    @Override
    public synchronized Optional<PayoutHold> findById(UUID id) {
        return Optional.ofNullable(holds.get(id));
    }

    @Override
    public synchronized Optional<PayoutHold> findActive(SellerId sellerId, PayoutPeriod period) {
        return holds.values().stream()
                .filter(hold -> hold.isActive() && hold.sellerId().equals(sellerId) && hold.period().equals(period))
                .findFirst();
    }

    @Override
    public synchronized List<PayoutHold> findByStatus(HoldStatus status) {
        return holds.values().stream()
                .filter(hold -> hold.status() == status)
                .sorted(Comparator.comparing(PayoutHold::proposedAt))
                .toList();
    }
}
