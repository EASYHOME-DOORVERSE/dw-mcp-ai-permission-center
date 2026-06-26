package com.easyhome.mcp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MCP 工具同步消息 — 通过 Redis Pub/Sub 跨节点广播
 *
 * 管理端在某节点操作工具后，该节点通过 Redis 频道 "mcp:tool:sync" 广播此消息，
 * 所有订阅该频道的节点收到消息后各自执行工具注册/注销操作。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具 ID */
    private Long toolId;

    /** 变更类型：CREATE / UPDATE / DELETE / ENABLE / DISABLE */
    private String changeType;

    /** 消息产生时间戳 */
    private long timestamp;

    public McpToolSyncMessage(Long toolId, String changeType) {
        this.toolId = toolId;
        this.changeType = changeType;
        this.timestamp = System.currentTimeMillis();
    }
}
