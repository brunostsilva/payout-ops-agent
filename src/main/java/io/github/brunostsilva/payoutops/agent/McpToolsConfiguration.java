package io.github.brunostsilva.payoutops.agent;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the agent's tools over the Model Context Protocol. Spring AI's MCP server picks up
 * {@link ToolCallbackProvider} beans, so external MCP clients (Claude Desktop, MCP Inspector, other
 * agents) get exactly the same read-only tools and propose-only hold action as {@link PayoutAgent},
 * backed by the same services and guarantees.
 */
@Configuration(proxyBeanMethods = false)
class McpToolsConfiguration {

    @Bean
    ToolCallbackProvider payoutMcpTools(PayoutTools payoutTools, PayoutActionTools actionTools) {
        return MethodToolCallbackProvider.builder().toolObjects(payoutTools, actionTools).build();
    }
}
