package io.github.brunostsilva.payoutops.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single movement in a seller's marketplace ledger.
 *
 * <p>The hierarchy is sealed so that every consumer switching over entry types is checked for
 * exhaustiveness by the compiler. Amounts are positive magnitudes, except for {@link Adjustment},
 * whose sign carries meaning (credit or debit to the seller).
 */
public sealed interface LedgerEntry {

    String id();

    SellerId sellerId();

    LocalDate occurredOn();

    Money amount();

    record Sale(String id, SellerId sellerId, LocalDate occurredOn, Money amount, String orderId)
            implements LedgerEntry {
        public Sale {
            requireCommonFields(id, sellerId, occurredOn, amount);
            requireNonNegative(amount);
        }
    }

    record Commission(String id, SellerId sellerId, LocalDate occurredOn, Money amount, String orderId)
            implements LedgerEntry {
        public Commission {
            requireCommonFields(id, sellerId, occurredOn, amount);
            requireNonNegative(amount);
        }
    }

    record Refund(String id, SellerId sellerId, LocalDate occurredOn, Money amount, String orderId)
            implements LedgerEntry {
        public Refund {
            requireCommonFields(id, sellerId, occurredOn, amount);
            requireNonNegative(amount);
        }
    }

    record Chargeback(String id, SellerId sellerId, LocalDate occurredOn, Money amount, String orderId, String reason)
            implements LedgerEntry {
        public Chargeback {
            requireCommonFields(id, sellerId, occurredOn, amount);
            requireNonNegative(amount);
        }
    }

    record Adjustment(String id, SellerId sellerId, LocalDate occurredOn, Money amount, String description)
            implements LedgerEntry {
        public Adjustment {
            requireCommonFields(id, sellerId, occurredOn, amount);
        }
    }

    private static void requireCommonFields(String id, SellerId sellerId, LocalDate occurredOn, Money amount) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(amount, "amount");
    }

    private static void requireNonNegative(Money amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Amount must not be negative, got " + amount);
        }
    }
}
