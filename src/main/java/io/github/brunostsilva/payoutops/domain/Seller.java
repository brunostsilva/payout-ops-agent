package io.github.brunostsilva.payoutops.domain;

import java.util.Currency;
import java.util.Objects;

public record Seller(SellerId id, String name, Currency currency) {

    public Seller {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
    }
}
