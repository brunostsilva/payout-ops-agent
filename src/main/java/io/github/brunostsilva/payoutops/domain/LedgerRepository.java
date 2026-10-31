package io.github.brunostsilva.payoutops.domain;

import java.util.List;

public interface LedgerRepository {

    List<LedgerEntry> findBySellerAndPeriod(SellerId sellerId, PayoutPeriod period);
}
