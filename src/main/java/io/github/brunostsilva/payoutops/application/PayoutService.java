package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.CommissionRule;
import io.github.brunostsilva.payoutops.domain.CommissionRuleRepository;
import io.github.brunostsilva.payoutops.domain.LedgerEntry;
import io.github.brunostsilva.payoutops.domain.LedgerRepository;
import io.github.brunostsilva.payoutops.domain.PayoutBreakdown;
import io.github.brunostsilva.payoutops.domain.PayoutCalculator;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.PayoutTrend;
import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class PayoutService {

    /** Bounds of {@link #trend}: at least two periods to have a change, at most half a year of weeks. */
    public static final int MIN_TREND_PERIODS = 2;
    public static final int MAX_TREND_PERIODS = 26;

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

    /** The payouts of the {@code count} consecutive periods ending with {@code last}, oldest first. */
    public PayoutTrend trend(SellerId sellerId, PayoutPeriod last, int count) {
        if (count < MIN_TREND_PERIODS || count > MAX_TREND_PERIODS) {
            throw new IllegalArgumentException(
                    "A trend covers " + MIN_TREND_PERIODS + " to " + MAX_TREND_PERIODS + " periods, got " + count);
        }
        Seller seller = requireSeller(sellerId);
        List<PayoutPeriod> periods = Stream.iterate(last, PayoutPeriod::previous).limit(count).toList().reversed();
        return new PayoutTrend(periods.stream().map(period -> payoutFor(seller, period)).toList());
    }

    public List<LedgerEntry> ledgerEntries(SellerId sellerId, PayoutPeriod period) {
        requireSeller(sellerId);
        return ledger.findBySellerAndPeriod(sellerId, period);
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
