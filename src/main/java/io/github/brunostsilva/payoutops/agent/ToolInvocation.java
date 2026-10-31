package io.github.brunostsilva.payoutops.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolExecutionResult;

/** One tool call made by the model while answering: what it called, with which arguments, and what it got back. */
public record ToolInvocation(String tool, String arguments, String result) {

    static List<ToolInvocation> from(ChatResponse response, ToolExecutionResult execution) {
        Map<String, String> resultsByCallId = new HashMap<>();
        List<Message> history = execution.conversationHistory();
        if (!history.isEmpty() && history.getLast() instanceof ToolResponseMessage toolResponses) {
            toolResponses.getResponses().forEach(r -> resultsByCallId.put(r.id(), r.responseData()));
        }
        return response.getResults().stream()
                .flatMap(generation -> generation.getOutput().getToolCalls().stream())
                .map(call -> new ToolInvocation(call.name(), call.arguments(), resultsByCallId.get(call.id())))
                .toList();
    }
}
