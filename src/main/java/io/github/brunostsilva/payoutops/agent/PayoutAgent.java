package io.github.brunostsilva.payoutops.agent;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * Answers payout questions with an LLM that can only read data through {@link PayoutTools}.
 *
 * <p>In Spring AI 2 a {@link ChatModel} only returns the tool calls the model asks for; this class
 * executes them with {@link ToolCallingManager} and loops until the model answers. Owning the loop
 * lets it record every call for the response trace and stop a model that keeps requesting tools
 * without converging on an answer.
 */
@Service
public class PayoutAgent {

    static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            You are a payout operations assistant for a marketplace. You help analysts understand sellers' payouts.
            Rules:
            - Get every number from the tools. Never invent, estimate or recompute amounts yourself.
            - Weeks run Monday to Sunday. Pass dates to tools as YYYY-MM-DD. Today is %s.
            - To explain a change, start with comparePayoutWithPreviousWeek and walk through its drivers, largest
              first. Use getLedgerEntries or getCommissionRules when the user asks which orders or rules caused it.
            - If a tool returns an error (e.g. unknown seller), tell the user plainly instead of guessing.
            - Be concise and state amounts with their currency.
            """;

    private final ChatModel chatModel;
    private final Clock clock;
    private final List<ToolCallback> tools;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public PayoutAgent(ChatModel chatModel, PayoutTools payoutTools, Clock clock) {
        this.chatModel = chatModel;
        this.clock = clock;
        this.tools = List.of(MethodToolCallbackProvider.builder().toolObjects(payoutTools).build().getToolCallbacks());
    }

    public AgentAnswer ask(String question) {
        ToolCallingChatOptions options = requestOptions();
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(SYSTEM_PROMPT.formatted(LocalDate.now(clock))), new UserMessage(question)),
                options);

        ChatResponse response = chatModel.call(prompt);
        List<ToolInvocation> trace = new ArrayList<>();
        int rounds = 0;
        while (response.hasToolCalls()) {
            if (++rounds > MAX_TOOL_ROUNDS) {
                throw new AgentLoopLimitExceededException(MAX_TOOL_ROUNDS);
            }
            ToolExecutionResult execution = toolCallingManager.executeToolCalls(prompt, response);
            trace.addAll(ToolInvocation.from(execution));
            prompt = new Prompt(execution.conversationHistory(), options);
            response = chatModel.call(prompt);
        }
        return new AgentAnswer(answerText(response), List.copyOf(trace));
    }

    /**
     * Provider models (OpenAI, Ollama) cast the prompt options to their own options type and no longer
     * merge them with their configured defaults, so the request options must start from the model's
     * own options (model, temperature...) with the tools added on top.
     */
    private ToolCallingChatOptions requestOptions() {
        return chatModel.getOptions() instanceof ToolCallingChatOptions defaults
                ? defaults.mutate().toolCallbacks(tools).build()
                : ToolCallingChatOptions.builder().toolCallbacks(tools).build();
    }

    private static String answerText(ChatResponse response) {
        Generation result = response.getResult();
        String text = result == null ? null : result.getOutput().getText();
        return text == null ? "" : text;
    }
}
