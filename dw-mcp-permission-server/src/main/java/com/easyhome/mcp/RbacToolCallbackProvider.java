package com.easyhome.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * MCP 工具提供者（占位）
 *
 * 返回空数组，不通过 Spring AI 自动注册工具。
 * 所有工具的注册统一由 {@link McpToolRegistry} 在启动时和运行时管理，
 * 保证启动时注册的工具与数据库实时一致。
 */
@Slf4j
@Component
public class RbacToolCallbackProvider implements ToolCallbackProvider {

    @Override
    public ToolCallback[] getToolCallbacks() {
        // 返回空数组，工具注册由 McpToolRegistry 统一接管
        return new ToolCallback[0];
    }
}
