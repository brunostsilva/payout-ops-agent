package io.github.brunostsilva.payoutops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.brunostsilva.payoutops.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What an operator gets out of the box: audit and agent log lines correlated with the trace, no
 * sensitive text in the logs, business metrics on /actuator/prometheus and health probes.
 */
@SpringBootTest(properties = {
    // Tests may turn metrics export and tracing off; this test is about exactly those, so keep them on.
    "spring.test.metrics.export=true",
    "spring.test.tracing.export=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

    /** A distinctive phrase that only appears in the question, never in ids or metadata. */
    private static final String SECRET_DETAIL = "customer told us about a lawsuit";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void holdLifecycleIsLoggedAndCorrelatedWithoutSensitiveText(CapturedOutput output) throws Exception {
        String holdId = proposeThroughAgent(
                "Please hold seller-456's payout for the week of 2026-10-05, the " + SECRET_DETAIL);
        decide(holdId, "approve", "ana", "Confirmed: " + SECRET_DETAIL);
        mockMvc.perform(post("/api/holds/{id}/reject", holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"bob\", \"note\": \"Too late\"}"))
                .andExpect(status().isConflict());

        String logs = output.getAll();
        assertThat(logs)
                .contains("Hold " + holdId + " proposed by payout-ops-agent for seller seller-456 week 2026-10-05")
                .contains("Hold " + holdId + " APPROVED by ana")
                .contains("Hold " + holdId + " was already APPROVED when bob tried to decide it")
                .contains("Agent answered with 1 tool call(s) [proposePayoutHold]")
                .as("the question, the hold reason and the decision note are never logged")
                .doesNotContain(SECRET_DETAIL);
        assertThat(logs.lines().filter(line -> line.contains("Agent answered with")))
                .as("agent log lines carry the request's trace id")
                .singleElement()
                .asString()
                .containsPattern("[0-9a-f]{32}");
    }

    @Test
    void businessMetricsAreExposedForPrometheus() throws Exception {
        proposeThroughAgent("Please hold seller-123's payout for the week of 2026-10-05");

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("payout_agent_ask_seconds_count")))
                .andExpect(content().string(containsString("payout_holds_proposed_total{")))
                .andExpect(content().string(containsString("outcome=\"created\"")))
                .andExpect(content().string(containsString("proposed_by=\"payout-ops-agent\"")));
    }

    @Test
    void livenessAndReadinessProbesAreUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String proposeThroughAgent(String question) throws Exception {
        String body = mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].tool").value("proposePayoutHold"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String toolResult = JsonPath.read(body, "$.toolCalls[0].result");
        return JsonPath.read(toolResult, "$.hold.id");
    }

    private void decide(String holdId, String action, String analyst, String note) throws Exception {
        mockMvc.perform(post("/api/holds/{id}/" + action, holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"" + analyst + "\", \"note\": \"" + note + "\"}"))
                .andExpect(status().isOk());
    }
}
