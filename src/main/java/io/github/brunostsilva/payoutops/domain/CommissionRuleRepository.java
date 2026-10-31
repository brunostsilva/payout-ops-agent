package io.github.brunostsilva.payoutops.domain;

import java.util.List;

public interface CommissionRuleRepository {

    /** All rules for the seller, oldest first. */
    List<CommissionRule> findBySeller(SellerId sellerId);
}
