package com.easyhome.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * 自定义 MCP JSON-RPC 传输配置。MVC Transport 配置类
 */
@Slf4j
@Configuration
public class MvcTransportConfig {
    /**
     * 自定义 McpJsonMapper Bean（Primary）：新建一个配置了 NON_NULL 的 JsonMapper，
     * 传给 JacksonMcpJsonMapper，确保 MCP JSON-RPC 序列化时跳过所有 null 字段。
     *
     * 注意：不依赖 Spring AI 自动配置的 mcpServerJsonMapper Bean（避免自引用循环依赖），
     * 而是直接 JsonMapper.builder() 新建，并复制 NON_NULL 配置。
     */
    @Bean
    @Primary
    public McpJsonMapper mcpJsonMapper() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(
                        v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return new JacksonMcpJsonMapper(jsonMapper);
    }

    @Bean
    @Primary
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            McpJsonMapper mcpJsonMapper,
            McpServerStreamableHttpProperties properties,
            RbacMcpContextExtractor contextExtractor) {

        WebMvcStatelessServerTransport provider =
                WebMvcStatelessServerTransport.builder()
                        .jsonMapper(mcpJsonMapper)
                        .messageEndpoint(properties.getMcpEndpoint())
                        .contextExtractor(contextExtractor)
                        .build();

        log.info("WebMvcStatelessServerTransport 已创建，端点={}，contextExtractor={}",
                properties.getMcpEndpoint(), contextExtractor.getClass().getSimpleName());
        return provider;
    }
}
