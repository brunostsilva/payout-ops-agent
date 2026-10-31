package io.github.brunostsilva.payoutops.agent;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Answers payout questions with an LLM that can only read data through {@link PayoutTools}.
 *
 * <p>In Spring AI 2 a {@link ChatModel} only returns the tool calls the model asks for; this class
 * executes them with {@link ToolCallingManager} and loops until the model answers. Owning the loop
 * lets it record every call for the response trace and stop a model that keeps requesting tools
 * without converging on an answer.
 *
 * <p>Each question is one {@value #OBSERVATION_NAME} observation (a span when tracing is enabled), and
 * the tool calls ({@code spring.ai.tool}) and provider model calls ({@code gen_ai.client.operation})
 * made while answering it are nested under it.
 */
@Service
public class PayoutAgent {

    static final int MAX_TOOL_ROUNDS = 5;
    static final String OBSERVATION_NAME = "payout.agent.ask";

    private static final String SYSTEM_PROMPT = """
            You are a payout operations assistant for a marketplace. You help analysts understand sellers' payouts.
            Rules:
            - Get every number from the tools. Never invent, estimate or recompute amounts yourself.
            - Weeks run Monday to Sunday. Pass dates to tools as YYYY-MM-DD. Today is %s.
            - To explain a change, start with comparePayoutWithPreviousWeek and walk through its drivers, largest
              first. Use getLedgerEntries or getCommissionRules when the user asks which orders or rules caused it.
            - If a tool returns an error (e.g. unknown seller), tell the user plainly instead of guessing.
            - You cannot hold, release or change payouts. Only when the user explicitly asks to hold or block a
              payout, call proposePayoutHold with a short factual reason. It only records a PENDING proposal:
              always tell the user that nothing has been held until an analyst approves it, and give the hold id.
            - Never propose a hold the user did not ask for; at most, mention that one could be proposed.
            - Be concise and state amounts with their currency.
            """;

    private final ChatModel chatModel;
    private final Clock clock;
    private final List<ToolCallback> tools;
    private final ObservationRegistry observations;
    private final ToolCallingManager toolCallingManager;

    @Autowired
    public PayoutAgent(
            ChatModel chatModel,
            PayoutTools payoutTools,
            PayoutActionTools actionTools,
            Clock clock,
            ObjectProvider<ObservationRegistry> observations) {
        this(chatModel, payoutTools, actionTools, clock, observations.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    public PayoutAgent(
            ChatModel chatModel,
            PayoutTools payoutTools,
            PayoutActionTools actionTools,
            Clock clock,
            ObservationRegistry observations) {
        this.chatModel = chatModel;
        this.clock = clock;
        this.observations = observations;
        this.tools = List.of(MethodToolCallbackProvider.builder()
                .toolObjects(payoutTools, actionTools)
                .build()
                .getToolCallbacks());
        // The manager defaults to a no-op registry: without this, tool calls would not be traced.
        this.toolCallingManager = ToolCallingManager.builder().observationRegistry(observations).build();
    }

    public AgentAnswer ask(String question) {
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observations)
                .contextualName("payout agent ask")
                .lowCardinalityKeyValue("payout.agent.chat_model", chatModel.getClass().getSimpleName())
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            AgentAnswer answer = answer(question);
            observation.highCardinalityKeyValue("payout.agent.tool_calls", String.valueOf(answer.toolCalls().size()));
            return answer;
        } catch (RuntimeException failure) {
            observation.error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    private AgentAnswer answer(String question) {
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
