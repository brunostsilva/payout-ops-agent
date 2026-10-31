package io.github.brunostsilva.payoutops.infrastructure.persistence;

import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerId;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import java.util.Currency;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSellerRepository implements SellerRepository {

    private final JdbcClient jdbc;

    JdbcSellerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Seller> findById(SellerId id) {
        return jdbc.sql("SELECT id, name, currency FROM sellers WHERE id = :id")
                .param("id", id.value())
                .query((rs, rowNum) -> new Seller(
                        new SellerId(rs.getString("id")),
                        rs.getString("name"),
                        Currency.getInstance(rs.getString("currency"))))
                .optional();
    }
}
