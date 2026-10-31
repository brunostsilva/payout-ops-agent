package io.github.brunostsilva.payoutops.agent;

import java.util.List;

/** The agent's final answer plus the trace of every tool call it made, in order. */
public record AgentAnswer(String answer, List<ToolInvocation> toolCalls) {
}
