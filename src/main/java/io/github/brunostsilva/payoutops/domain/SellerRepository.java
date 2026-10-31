package io.github.brunostsilva.payoutops.domain;

import java.util.Optional;

public interface SellerRepository {

    Optional<Seller> findById(SellerId id);
}
