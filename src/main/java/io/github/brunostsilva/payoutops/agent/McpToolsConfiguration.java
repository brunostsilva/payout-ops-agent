package io.github.brunostsilva.payoutops.agent;

import io.github.brunostsilva.payoutops.application.PayoutHoldService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the agent's tools over the Model Context Protocol. Spring AI's MCP server picks up
 * {@link ToolCallbackProvider} beans, so external MCP clients (Claude Desktop, MCP Inspector, other
 * agents) get exactly the same read-only tools and propose-only hold action as {@link PayoutAgent},
 * backed by the same services and guarantees. Holds they propose are recorded with their own
 * proposer, so analysts can tell them apart from the in-app agent's.
 */
@Configuration(proxyBeanMethods = false)
class McpToolsConfiguration {

    @Bean
    ToolCallbackProvider payoutMcpTools(PayoutTools payoutTools, PayoutHoldService holdService) {
        PayoutActionTools mcpActionTools =
                new PayoutActionTools(holdService, PayoutActionTools.MCP_CLIENT_PROPOSER);
        return MethodToolCallbackProvider.builder().toolObjects(payoutTools, mcpActionTools).build();
    }
}
