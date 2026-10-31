package io.github.brunostsilva.payoutops.domain;

public record SellerId(String value) {

    public SellerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SellerId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
