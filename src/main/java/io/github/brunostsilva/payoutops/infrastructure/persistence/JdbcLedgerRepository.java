package io.github.brunostsilva.payoutops.infrastructure.persistence;

import io.github.brunostsilva.payoutops.domain.LedgerEntry;
import io.github.brunostsilva.payoutops.domain.LedgerRepository;
import io.github.brunostsilva.payoutops.domain.Money;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerRepository implements LedgerRepository {

    private final JdbcClient jdbc;

    JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LedgerEntry> findBySellerAndPeriod(SellerId sellerId, PayoutPeriod period) {
        return jdbc.sql("""
                        SELECT id, seller_id, entry_type, amount, currency, occurred_on, order_id, description
                        FROM ledger_entries
                        WHERE seller_id = :sellerId
                          AND occurred_on BETWEEN :start AND :end
                        ORDER BY occurred_on, id
                        """)
                .param("sellerId", sellerId.value())
                .param("start", period.start())
                .param("end", period.end())
                .query(JdbcLedgerRepository::mapRow)
                .list();
    }

    private static LedgerEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        SellerId sellerId = new SellerId(rs.getString("seller_id"));
        LocalDate occurredOn = rs.getObject("occurred_on", LocalDate.class);
        Money amount = new Money(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency")));
        String orderId = rs.getString("order_id");
        String description = rs.getString("description");
        String type = rs.getString("entry_type");

        return switch (type) {
            case "SALE" -> new LedgerEntry.Sale(id, sellerId, occurredOn, amount, orderId);
            case "COMMISSION" -> new LedgerEntry.Commission(id, sellerId, occurredOn, amount, orderId);
            case "REFUND" -> new LedgerEntry.Refund(id, sellerId, occurredOn, amount, orderId);
            case "CHARGEBACK" -> new LedgerEntry.Chargeback(id, sellerId, occurredOn, amount, orderId, description);
            case "ADJUSTMENT" -> new LedgerEntry.Adjustment(id, sellerId, occurredOn, amount, description);
            default -> throw new IllegalStateException("Unknown ledger entry type " + type + " for entry " + id);
        };
    }
}
