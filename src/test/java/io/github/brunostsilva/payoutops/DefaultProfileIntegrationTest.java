package io.github.brunostsilva.payoutops;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunostsilva.payoutops.agent.ScriptedChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Without any profile, only the schema is migrated (no synthetic data reaches the database) and no
 * real AI model is configured, so the app runs without API keys.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DefaultProfileIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ApplicationContext context;

    @Test
    void demoSeedIsNotLoadedByDefault() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM sellers").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ledger_entries").query(Long.class).single()).isZero();
    }

    /**
     * Spring AI starters auto-configure every model type they support unless spring.ai.model.<type> is
     * none. A model type added by a Spring AI upgrade (like the decision model in 2.1) must be turned
     * off in application.yml too, or the app would need provider credentials or a running server.
     */
    @Test
    void noProviderModelIsConfiguredByDefault() {
        assertThat(context.getBeansOfType(Model.class).values())
                .as("AI models in the default profile")
                .singleElement()
                .isInstanceOf(ScriptedChatModel.class);
    }
}
