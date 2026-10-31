package io.github.brunostsilva.payoutops.infrastructure.persistence;

import io.github.brunostsilva.payoutops.domain.CommissionRule;
import io.github.brunostsilva.payoutops.domain.CommissionRuleRepository;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCommissionRuleRepository implements CommissionRuleRepository {

    private final JdbcClient jdbc;

    JdbcCommissionRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CommissionRule> findBySeller(SellerId sellerId) {
        return jdbc.sql("""
                        SELECT seller_id, rate, valid_from, reason
                        FROM commission_rules
                        WHERE seller_id = :sellerId
                        ORDER BY valid_from
                        """)
                .param("sellerId", sellerId.value())
                .query((rs, rowNum) -> new CommissionRule(
                        new SellerId(rs.getString("seller_id")),
                        rs.getBigDecimal("rate"),
                        rs.getObject("valid_from", LocalDate.class),
                        rs.getString("reason")))
                .list();
    }
}
