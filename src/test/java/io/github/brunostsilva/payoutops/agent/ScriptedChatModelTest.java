package io.github.brunostsilva.payoutops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

class ScriptedChatModelTest {

    private final ScriptedChatModel model = new ScriptedChatModel(new JsonMapper());

    @Test
    void requestsTheComparisonToolForASellerAndDate() {
        ChatResponse response = model.call(new Prompt(
                new UserMessage("Why did seller-123's payout drop in the week of 2026-10-26?")));

        assertThat(response.hasToolCalls()).isTrue();
        AssistantMessage.ToolCall call = response.getResult().getOutput().getToolCalls().getFirst();
        assertThat(call.name()).isEqualTo(ScriptedChatModel.COMPARISON_TOOL);
        assertThat(call.arguments()).isEqualTo("{\"sellerId\":\"seller-123\",\"weekOf\":\"2026-10-26\"}");
    }

    @Test
    void requestsAHoldProposalWhenAskedToHold() {
        ChatResponse response = model.call(new Prompt(
                new UserMessage("Hold seller-123's payout for 2026-10-26")));

        AssistantMessage.ToolCall call = response.getResult().getOutput().getToolCalls().getFirst();
        assertThat(call.name()).isEqualTo(ScriptedChatModel.PROPOSE_HOLD_TOOL);
        assertThat(call.arguments())
                .contains("\"sellerId\":\"seller-123\"")
                .contains("\"weekOf\":\"2026-10-26\"")
                .contains("\"reason\":\"Requested by an analyst through the agent: Hold seller-123's payout");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Is seller-123's payout on hold for 2026-10-26?",
        "Is there a hold on seller-123's payout for 2026-10-26?",
        "Release the hold on seller-123's payout for 2026-10-26",
        "Don't hold seller-123's payout for 2026-10-26",
        "Why did seller-123's payout drop in the week of 2026-10-26? Should we hold it?"
    })
    void doesNotProposeAHoldUnlessExplicitlyAskedTo(String question) {
        ChatResponse response = model.call(new Prompt(new UserMessage(question)));

        AssistantMessage.ToolCall call = response.getResult().getOutput().getToolCalls().getFirst();
        assertThat(call.name()).isEqualTo(ScriptedChatModel.COMPARISON_TOOL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Hold seller-123's payout for 2026-10-26",
        "Please block seller-123's payout for the week of 2026-10-26",
        "Could you hold seller-123's payout for 2026-10-26?",
        "The chargebacks look like fraud. Hold seller-123's payout for 2026-10-26"
    })
    void proposesAHoldForExplicitRequests(String question) {
        ChatResponse response = model.call(new Prompt(new UserMessage(question)));

        AssistantMessage.ToolCall call = response.getResult().getOutput().getToolCalls().getFirst();
        assertThat(call.name()).isEqualTo(ScriptedChatModel.PROPOSE_HOLD_TOOL);
    }

    @Test
    void summarizesAHoldProposal() {
        String proposal = """
                {"hold": {"id": "7f1c", "sellerId": "seller-123", "periodStart": "2026-10-26", "status": "PENDING"},
                 "note": "Proposal recorded as PENDING."}
                """;
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", ScriptedChatModel.PROPOSE_HOLD_TOOL, proposal)))
                .build();

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("question"), toolResponse)));

        assertThat(response.getResult().getOutput().getText()).isEqualTo(
                "[scripted model] Proposed a payout hold for seller-123 for the week starting 2026-10-26 (hold 7f1c). "
                        + "Status: PENDING. Nothing has been held yet: an analyst must approve or reject it via "
                        + "POST /api/holds/7f1c/approve or /reject.");
    }

    @Test
    void reportsAnAlreadyApprovedHoldInsteadOfAPendingProposal() {
        String result = """
                {"hold": {"id": "7f1c", "sellerId": "seller-123", "periodStart": "2026-10-26", "status": "APPROVED"},
                 "note": "An analyst already APPROVED a hold for this seller and week."}
                """;
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", ScriptedChatModel.PROPOSE_HOLD_TOOL, result)))
                .build();

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("question"), toolResponse)));

        assertThat(response.getResult().getOutput().getText()).isEqualTo(
                "[scripted model] Seller seller-123's payout for the week starting 2026-10-26 already has an approved "
                        + "hold (hold 7f1c), so no new proposal was created.");
    }

    @Test
    void asksForSellerAndDateWhenTheyAreMissing() {
        ChatResponse response = model.call(new Prompt(new UserMessage("Why did my payout drop?")));

        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.getResult().getOutput().getText()).contains("seller id").contains("YYYY-MM-DD");
    }

    @Test
    void summarizesTheComparisonResult() {
        String comparison = """
                {"current": {"sellerId": "seller-123"},
                 "netChange": {"amount": -2420.00, "currency": "BRL"},
                 "netChangePercent": -27.5,
                 "drivers": [
                   {"component": "CHARGEBACKS", "effectOnNet": {"amount": -1200.00, "currency": "BRL"}},
                   {"component": "REFUNDS", "effectOnNet": {"amount": 50.00, "currency": "BRL"}}
                 ]}
                """;
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", ScriptedChatModel.COMPARISON_TOOL, comparison)))
                .build();

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("question"), toolResponse)));

        assertThat(response.getResult().getOutput().getText()).isEqualTo(
                "[scripted model] Seller seller-123's net payout changed by BRL -2420.00 (-27.5%) compared with the "
                        + "previous week. Drivers, largest impact first: chargebacks (BRL -1200.00), refunds (BRL 50.00).");
    }

    @Test
    void reportsToolErrorsVerbatim() {
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", ScriptedChatModel.COMPARISON_TOOL, "Seller seller-999 not found")))
                .build();

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("question"), toolResponse)));

        assertThat(response.getResult().getOutput().getText())
                .isEqualTo("[scripted model] I couldn't get the payout data: Seller seller-999 not found");
    }
}
