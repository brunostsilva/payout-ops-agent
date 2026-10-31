package io.github.brunostsilva.payoutops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.brunostsilva.payoutops.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The human-in-the-loop flow end to end: the agent proposes, an analyst decides, exactly once. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class HoldWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void agentProposesAHoldThatAnAnalystApprovesExactlyOnce() throws Exception {
        String holdId = proposeThroughAgent(
                "Please hold seller-123's payout for the week of 2026-10-26, the chargebacks look like fraud");

        mockMvc.perform(get("/api/holds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + holdId + "')].status", contains("PENDING")))
                .andExpect(jsonPath("$[?(@.id == '" + holdId + "')].proposedBy", contains("payout-ops-agent")));

        mockMvc.perform(post("/api/holds/{id}/approve", holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"ana\", \"note\": \"Confirmed with the risk team\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("ana"))
                .andExpect(jsonPath("$.decisionNote").value("Confirmed with the risk team"));

        mockMvc.perform(post("/api/holds/{id}/reject", holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"bob\", \"note\": \"Too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Hold already decided"));

        mockMvc.perform(get("/api/holds/{id}", holdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void repeatedProposalsReuseThePendingHoldAndRejectionsNeedANote() throws Exception {
        String question = "Hold seller-456's payout for the week of 2026-10-19";
        String first = proposeThroughAgent(question);
        String second = proposeThroughAgent(question);
        assertThat(second).isEqualTo(first);

        mockMvc.perform(post("/api/holds/{id}/reject", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"ana\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/holds/{id}/reject", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analyst\": \"ana\", \"note\": \"Sales are legitimate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/holds").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + first + "')].decisionNote", contains("Sales are legitimate")));
    }

    @Test
    void unknownHoldsReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/holds/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Hold not found"));
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
}
