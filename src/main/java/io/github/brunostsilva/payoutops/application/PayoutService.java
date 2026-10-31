package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.CommissionRule;
import io.github.brunostsilva.payoutops.domain.CommissionRuleRepository;
import io.github.brunostsilva.payoutops.domain.LedgerRepository;
import io.github.brunostsilva.payoutops.domain.PayoutBreakdown;
import io.github.brunostsilva.payoutops.domain.PayoutCalculator;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PayoutService {

    private final SellerRepository sellers;
    private final LedgerRepository ledger;
    private final CommissionRuleRepository commissionRules;

    public PayoutService(
            SellerRepository sellers, LedgerRepository ledger, CommissionRuleRepository commissionRules) {
        this.sellers = sellers;
        this.ledger = ledger;
        this.commissionRules = commissionRules;
    }

    public PayoutBreakdown payoutFor(SellerId sellerId, PayoutPeriod period) {
        return payoutFor(requireSeller(sellerId), period);
    }

    public PayoutComparison compareWithPreviousPeriod(SellerId sellerId, PayoutPeriod current) {
        Seller seller = requireSeller(sellerId);
        return new PayoutComparison(payoutFor(seller, current.previous()), payoutFor(seller, current));
    }

    public List<CommissionRule> commissionRules(SellerId sellerId) {
        requireSeller(sellerId);
        return commissionRules.findBySeller(sellerId);
    }

    private PayoutBreakdown payoutFor(Seller seller, PayoutPeriod period) {
        return PayoutCalculator.calculate(
                seller.id(), period, seller.currency(), ledger.findBySellerAndPeriod(seller.id(), period));
    }

    private Seller requireSeller(SellerId sellerId) {
        return sellers.findById(sellerId).orElseThrow(() -> new SellerNotFoundException(sellerId));
    }
}
