package io.github.brunostsilva.payoutops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.brunostsilva.payoutops.TestcontainersConfiguration;
import io.github.brunostsilva.payoutops.agent.ScriptedChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The agent endpoint end to end: HTTP, tool-calling loop, tools, service, JDBC and the seeded scenario. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class AgentEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatModel chatModel;

    @Test
    void usesTheScriptedModelWhenNoRealModelIsConfigured() {
        assertThat(chatModel).isInstanceOf(ScriptedChatModel.class);
    }

    @Test
    void explainsThePayoutDropWithATraceOfToolCalls() throws Exception {
        mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Why did seller-123's payout drop in the week of 2026-10-26?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("BRL -2420.00 (-27.5%)")))
                .andExpect(jsonPath("$.answer", containsString("chargebacks (BRL -1200.00)")))
                .andExpect(jsonPath("$.toolCalls.length()").value(1))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("comparePayoutWithPreviousWeek"))
                .andExpect(jsonPath("$.toolCalls[0].arguments", containsString("seller-123")))
                .andExpect(jsonPath("$.toolCalls[0].result", containsString("\"drivers\"")));
    }

    @Test
    void rejectsBlankQuestions() throws Exception {
        mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
