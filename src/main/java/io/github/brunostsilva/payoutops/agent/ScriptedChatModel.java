package io.github.brunostsilva.payoutops.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Deterministic stand-in for an LLM, active when no real model is configured
 * ({@code spring.ai.model.chat=none}, the default).
 *
 * <p>It follows a fixed script that still goes through the real tool-calling loop: for a question
 * mentioning a seller id and a date it requests {@code comparePayoutWithPreviousWeek} (or
 * {@code proposePayoutHold} when the question asks to "hold" the payout), then turns the tool result
 * into a templated answer. This keeps the agent runnable and testable offline, without API keys or
 * network access.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none")
public class ScriptedChatModel implements ChatModel {

    static final String COMPARISON_TOOL = "comparePayoutWithPreviousWeek";
    static final String PROPOSE_HOLD_TOOL = "proposePayoutHold";
    static final String PREFIX = "[scripted model] ";

    private static final Pattern SELLER_ID = Pattern.compile("seller-[A-Za-z0-9]+");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    // An explicit request to hold/block a payout ("hold seller-123's payout ..."), not a question about
    // status such as "is the payout on hold?", where "payout" comes before "hold".
    private static final Pattern HOLD_REQUEST =
            Pattern.compile("\\b(?:hold|block)\\b[^.?!]*\\bpayout\\b", Pattern.CASE_INSENSITIVE);

    private final JsonMapper jsonMapper;

    public ScriptedChatModel(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Message last = prompt.getInstructions().getLast();
        AssistantMessage reply = last instanceof ToolResponseMessage toolResponses
                ? summarize(toolResponses)
                : planToolCall(last.getText());
        return new ChatResponse(List.of(new Generation(reply)));
    }

    private AssistantMessage planToolCall(String question) {
        Matcher seller = SELLER_ID.matcher(question);
        Matcher date = DATE.matcher(question);
        if (!seller.find() || !date.find()) {
            return text("Please mention a seller id (e.g. seller-123) and a date inside the week to analyse "
                    + "(YYYY-MM-DD). Enable the openai or ollama profile for free-form questions.");
        }
        ObjectNode arguments = jsonMapper.createObjectNode()
                .put("sellerId", seller.group())
                .put("weekOf", date.group());
        String tool = COMPARISON_TOOL;
        if (HOLD_REQUEST.matcher(question).find()) {
            tool = PROPOSE_HOLD_TOOL;
            arguments.put("reason", "Requested by an analyst through the agent: " + question.strip());
        }
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", tool, arguments.toString())))
                .build();
    }

    private AssistantMessage summarize(ToolResponseMessage toolResponses) {
        ToolResponseMessage.ToolResponse response = toolResponses.getResponses().getFirst();
        return PROPOSE_HOLD_TOOL.equals(response.name())
                ? summarizeHold(response.responseData())
                : summarizeComparison(response.responseData());
    }

    private AssistantMessage summarizeHold(String data) {
        JsonNode result = parse(data);
        if (result == null || !result.has("hold")) {
            return text("I couldn't propose the hold: " + data);
        }
        JsonNode hold = result.path("hold");
        String id = hold.path("id").asString();
        String status = hold.path("status").asString();
        if (!"PENDING".equals(status)) {
            return text("Seller %s's payout for the week starting %s already has an %s hold (hold %s), so no new "
                    .formatted(hold.path("sellerId").asString(), hold.path("periodStart").asString(),
                            status.toLowerCase(Locale.ROOT), id)
                    + "proposal was created.");
        }
        return text(("Proposed a payout hold for %s for the week starting %s (hold %s). Status: %s. Nothing has been "
                        + "held yet: an analyst must approve or reject it via POST /api/holds/%s/approve or /reject.")
                .formatted(
                        hold.path("sellerId").asString(),
                        hold.path("periodStart").asString(),
                        id,
                        status,
                        id));
    }

    private AssistantMessage summarizeComparison(String data) {
        JsonNode comparison = parse(data);
        if (comparison == null || !comparison.has("netChange")) {
            return text("I couldn't get the payout data: " + data);
        }
        String currency = comparison.path("netChange").path("currency").asString();
        String drivers = StreamSupport.stream(comparison.path("drivers").spliterator(), false)
                .map(driver -> "%s (%s %s)".formatted(
                        driver.path("component").asString().toLowerCase(Locale.ROOT),
                        currency,
                        amount(driver.path("effectOnNet").path("amount"))))
                .collect(Collectors.joining(", "));
        JsonNode percent = comparison.path("netChangePercent");
        return text("Seller %s's net payout changed by %s %s%s compared with the previous week. Drivers, largest impact first: %s."
                .formatted(
                        comparison.path("current").path("sellerId").asString(),
                        currency,
                        amount(comparison.path("netChange").path("amount")),
                        percent.isNumber()
                                ? " (" + percent.decimalValue().setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "%)"
                                : "",
                        drivers.isEmpty() ? "none" : drivers));
    }

    private JsonNode parse(String data) {
        if (data == null) {
            return null;
        }
        try {
            return jsonMapper.readTree(data);
        } catch (JacksonException notJson) {
            return null;
        }
    }

    private static String amount(JsonNode node) {
        // Jackson 3's decimalValue() throws on missing/non-numeric nodes; default to zero instead.
        return node.decimalValue(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static AssistantMessage text(String content) {
        return new AssistantMessage(PREFIX + content);
    }
}
