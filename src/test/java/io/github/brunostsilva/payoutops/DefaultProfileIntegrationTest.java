package io.github.brunostsilva.payoutops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Without the {@code demo} profile, only the schema is migrated: no synthetic data reaches the database. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DefaultProfileIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void demoSeedIsNotLoadedByDefault() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM sellers").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ledger_entries").query(Long.class).single()).isZero();
    }
}
