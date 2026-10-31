package io.github.brunostsilva.payoutops.domain;

import java.time.LocalDate;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicInteger;

public final class LedgerFixtures {

    public static final SellerId SELLER = new SellerId("seller-1");
    public static final Currency BRL = Currency.getInstance("BRL");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private LedgerFixtures() {
    }

    public static LedgerEntry sale(String amount, LocalDate on) {
        return new LedgerEntry.Sale(nextId(), SELLER, on, Money.of(amount, "BRL"), "o-" + SEQUENCE.get());
    }

    public static LedgerEntry commission(String amount, LocalDate on) {
        return new LedgerEntry.Commission(nextId(), SELLER, on, Money.of(amount, "BRL"), null);
    }

    public static LedgerEntry refund(String amount, LocalDate on) {
        return new LedgerEntry.Refund(nextId(), SELLER, on, Money.of(amount, "BRL"), null);
    }

    public static LedgerEntry chargeback(String amount, LocalDate on) {
        return new LedgerEntry.Chargeback(nextId(), SELLER, on, Money.of(amount, "BRL"), null, "disputed");
    }

    public static LedgerEntry adjustment(String amount, LocalDate on) {
        return new LedgerEntry.Adjustment(nextId(), SELLER, on, Money.of(amount, "BRL"), "manual");
    }

    private static String nextId() {
        return "le-" + SEQUENCE.incrementAndGet();
    }
}
