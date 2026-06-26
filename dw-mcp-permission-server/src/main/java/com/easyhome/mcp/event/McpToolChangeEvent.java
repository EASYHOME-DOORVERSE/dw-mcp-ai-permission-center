package com.easyhome.mcp.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * MCP 工具变更事件
 *
 * 在工具新增、修改、删除、启用、停用时触发，
 * 由 McpToolRegistry 监听并同步到 MCP Server。
 */
@Getter
public class McpToolChangeEvent extends ApplicationEvent {

    private final Long toolId;
    private final ChangeType changeType;

    public McpToolChangeEvent(Object source, Long toolId, ChangeType changeType) {
        super(source);
        this.toolId = toolId;
        this.changeType = changeType;
    }

    public enum ChangeType {
        CREATE,   // 新增
        UPDATE,   // 修改
        DELETE,   // 删除
        ENABLE,   // 启用
        DISABLE   // 停用
    }
}
