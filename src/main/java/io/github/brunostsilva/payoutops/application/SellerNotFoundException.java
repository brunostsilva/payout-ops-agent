package io.github.brunostsilva.payoutops.application;

import io.github.brunostsilva.payoutops.domain.SellerId;

public class SellerNotFoundException extends RuntimeException {

    public SellerNotFoundException(SellerId sellerId) {
        super("Seller " + sellerId + " not found");
    }
}
