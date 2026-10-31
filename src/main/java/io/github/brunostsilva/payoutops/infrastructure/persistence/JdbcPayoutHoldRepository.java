package io.github.brunostsilva.payoutops.infrastructure.persistence;

import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.PayoutHold;
import io.github.brunostsilva.payoutops.domain.PayoutHoldRepository;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPayoutHoldRepository implements PayoutHoldRepository {

    private static final String SELECT = """
            SELECT id, seller_id, period_start, period_end, reason, status, proposed_by, proposed_at,
                   decided_by, decided_at, decision_note
            FROM payout_holds
            """;

    private final JdbcClient jdbc;

    JdbcPayoutHoldRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PayoutHold hold) {
        if (!hold.isPending()) {
            throw new IllegalArgumentException("Only pending holds can be inserted");
        }
        jdbc.sql("""
                        INSERT INTO payout_holds
                            (id, seller_id, period_start, period_end, reason, status, proposed_by, proposed_at)
                        VALUES (:id, :sellerId, :periodStart, :periodEnd, :reason, 'PENDING', :proposedBy, :proposedAt)
                        """)
                .param("id", hold.id())
                .param("sellerId", hold.sellerId().value())
                .param("periodStart", hold.period().start())
                .param("periodEnd", hold.period().end())
                .param("reason", hold.reason())
                .param("proposedBy", hold.proposedBy())
                .param("proposedAt", toTimestamp(hold.proposedAt()))
                .update();
    }

    @Override
    public boolean recordDecision(PayoutHold decided) {
        if (decided.isPending()) {
            throw new IllegalArgumentException("Hold " + decided.id() + " has no decision to record");
        }
        int updated = jdbc.sql("""
                        UPDATE payout_holds
                        SET status = :status, decided_by = :decidedBy, decided_at = :decidedAt,
                            decision_note = :note
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("status", decided.status().name())
                .param("decidedBy", decided.decision().decidedBy())
                .param("decidedAt", toTimestamp(decided.decision().decidedAt()))
                .param("note", decided.decision().note(), Types.VARCHAR)
                .param("id", decided.id())
                .update();
        return updated == 1;
    }

    @Override
    public Optional<PayoutHold> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = :id")
                .param("id", id)
                .query(JdbcPayoutHoldRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<PayoutHold> findActive(SellerId sellerId, PayoutPeriod period) {
        return jdbc.sql(SELECT + """
                        WHERE seller_id = :sellerId AND period_start = :periodStart
                          AND status IN ('PENDING', 'APPROVED')
                        """)
                .param("sellerId", sellerId.value())
                .param("periodStart", period.start())
                .query(JdbcPayoutHoldRepository::mapRow)
                .optional();
    }

    @Override
    public List<PayoutHold> findByStatus(HoldStatus status) {
        return jdbc.sql(SELECT + "WHERE status = :status ORDER BY proposed_at, id")
                .param("status", status.name())
                .query(JdbcPayoutHoldRepository::mapRow)
                .list();
    }

    private static PayoutHold mapRow(ResultSet rs, int rowNum) throws SQLException {
        String decidedBy = rs.getString("decided_by");
        PayoutHold.Decision decision = decidedBy == null
                ? null
                : new PayoutHold.Decision(
                        decidedBy,
                        rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                        rs.getString("decision_note"));
        return new PayoutHold(
                rs.getObject("id", UUID.class),
                new SellerId(rs.getString("seller_id")),
                new PayoutPeriod(rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class)),
                rs.getString("reason"),
                HoldStatus.valueOf(rs.getString("status")),
                rs.getString("proposed_by"),
                rs.getObject("proposed_at", OffsetDateTime.class).toInstant(),
                decision);
    }

    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
