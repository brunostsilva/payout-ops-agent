package io.github.brunostsilva.payoutops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.brunostsilva.payoutops.TestcontainersConfiguration;
import io.github.brunostsilva.payoutops.application.PayoutService;
import io.github.brunostsilva.payoutops.domain.Money;
import io.github.brunostsilva.payoutops.domain.PayoutComparison;
import io.github.brunostsilva.payoutops.domain.PayoutComponent;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end check of the seeded demo scenario: Flyway schema + seed, JDBC repositories, service and API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class PayoutDemoScenarioIntegrationTest {

    private static final SellerId AURORA = new SellerId("seller-123");
    private static final PayoutPeriod CURRENT_WEEK = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void reapplyingTheSeedRestoresEditedRowsAndRemovesExtraOnes() {
        jdbc.sql("UPDATE ledger_entries SET amount = 1.00 WHERE id = 'le-123-0105'").update();
        jdbc.sql("""
                INSERT INTO ledger_entries (id, seller_id, entry_type, amount, currency, occurred_on)
                VALUES ('le-stray', 'seller-123', 'SALE', 999.00, 'BRL', DATE '2026-10-27')
                """).update();

        new ResourceDatabasePopulator(new ClassPathResource("db/demo/R__demo_seed.sql")).execute(dataSource);

        assertThat(jdbc.sql("SELECT amount FROM ledger_entries WHERE id = 'le-123-0105'")
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("700.00");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ledger_entries WHERE id = 'le-stray'")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void seedReproducesTheDocumentedPayoutDrop() {
        PayoutComparison comparison = payoutService.compareWithPreviousPeriod(AURORA, CURRENT_WEEK);

        assertThat(comparison.previous().net()).isEqualTo(Money.of("8800.00", "BRL"));
        assertThat(comparison.current().net()).isEqualTo(Money.of("6380.00", "BRL"));
        assertThat(comparison.netChange()).isEqualTo(Money.of("-2420.00", "BRL"));
        assertThat(comparison.netChangePercent()).contains(new BigDecimal("-27.5"));
        assertThat(comparison.drivers())
                .extracting(PayoutComparison.Driver::component)
                .containsExactly(
                        PayoutComponent.CHARGEBACKS,
                        PayoutComponent.ADJUSTMENTS,
                        PayoutComponent.COMMISSIONS,
                        PayoutComponent.SALES,
                        PayoutComponent.REFUNDS);
    }

    @Test
    void comparisonEndpointExplainsTheDrop() throws Exception {
        mockMvc.perform(get("/api/sellers/seller-123/payouts/comparison").param("weekOf", "2026-10-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.periodStart").value("2026-10-26"))
                .andExpect(jsonPath("$.current.net.amount").value(6380.00))
                .andExpect(jsonPath("$.netChange.amount").value(-2420.00))
                .andExpect(jsonPath("$.netChangePercent").value(-27.5))
                .andExpect(jsonPath("$.drivers[0].component").value("CHARGEBACKS"))
                .andExpect(jsonPath("$.drivers[0].effectOnNet.amount").value(-1200.00));
    }

    @Test
    void payoutEndpointReturnsTheBreakdown() throws Exception {
        mockMvc.perform(get("/api/sellers/seller-456/payouts").param("weekOf", "2026-10-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales.amount").value(5200.00))
                .andExpect(jsonPath("$.commissions.amount").value(520.00))
                .andExpect(jsonPath("$.net.amount").value(4680.00))
                .andExpect(jsonPath("$.net.currency").value("BRL"));
    }

    @Test
    void commissionRulesEndpointShowsTheRateChange() throws Exception {
        mockMvc.perform(get("/api/sellers/seller-123/commission-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].rate").value(0.15))
                .andExpect(jsonPath("$[1].validFrom").value("2026-10-26"));
    }

    @Test
    void unknownSellerReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/sellers/ghost/payouts").param("weekOf", "2026-10-26"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Seller not found"))
                .andExpect(jsonPath("$.detail").value("Seller ghost not found"));
    }
}
