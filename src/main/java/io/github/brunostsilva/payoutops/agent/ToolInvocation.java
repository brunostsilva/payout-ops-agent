package io.github.brunostsilva.payoutops.agent;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.ToolExecutionResult;

/** One tool call made by the model while answering: what it called, with which arguments, and what it got back. */
public record ToolInvocation(String tool, String arguments, String result) {

    /**
     * Reads the calls and their results from the history returned by the tool execution, which ends
     * with the executed assistant message followed by the tool responses. Calls and results are paired
     * by position (Spring AI writes the responses in call order), not by id: some providers, such as
     * Ollama, may send empty or repeated tool call ids.
     */
    static List<ToolInvocation> from(ToolExecutionResult execution) {
        List<Message> history = execution.conversationHistory();
        if (history.size() < 2
                || !(history.get(history.size() - 2) instanceof AssistantMessage request)
                || !(history.getLast() instanceof ToolResponseMessage responses)) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> calls = request.getToolCalls();
        List<ToolResponseMessage.ToolResponse> results = responses.getResponses();
        return IntStream.range(0, calls.size())
                .mapToObj(i -> new ToolInvocation(
                        calls.get(i).name(),
                        calls.get(i).arguments(),
                        i < results.size() ? results.get(i).responseData() : null))
                .toList();
    }
}
