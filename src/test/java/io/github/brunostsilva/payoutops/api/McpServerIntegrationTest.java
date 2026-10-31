package io.github.brunostsilva.payoutops.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunostsilva.payoutops.TestcontainersConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** A real MCP client talking to the app over Streamable HTTP, as Claude Desktop or MCP Inspector would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class McpServerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void exposesTheSameToolsAsTheAgent() {
        assertThat(client.listTools().tools())
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder(
                        "comparePayoutWithPreviousWeek",
                        "getWeeklyPayout",
                        "getLedgerEntries",
                        "getCommissionRules",
                        "proposePayoutHold");
    }

    @Test
    void readToolsAnswerFromTheLedger() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "comparePayoutWithPreviousWeek", Map.of("sellerId", "seller-123", "weekOf", "2026-10-26")));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("\"netChange\"").contains("-2420").contains("CHARGEBACKS");
    }

    @Test
    void theHoldToolOnlyRecordsAPendingProposal() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "proposePayoutHold",
                Map.of("sellerId", "seller-456", "weekOf", "2026-11-02", "reason", "Requested from an MCP client")));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("\"status\":\"PENDING\"").contains("Nothing has been held");
    }

    @Test
    void toolErrorsAreReportedToTheClient() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "getWeeklyPayout", Map.of("sellerId", "seller-999", "weekOf", "2026-10-26")));

        assertThat(text(result)).contains("seller-999");
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }
}
