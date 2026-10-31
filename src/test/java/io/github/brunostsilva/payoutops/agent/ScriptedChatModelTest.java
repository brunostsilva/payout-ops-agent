package io.github.brunostsilva.payoutops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class ScriptedChatModelTest {

    private final ScriptedChatModel model = new ScriptedChatModel(new ObjectMapper());

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
