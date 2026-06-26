package com.easyhome.mcp.event;

import com.easyhome.mcp.McpToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * MCP 工具跨节点同步服务
 * <p>
 * 解决多机部署时各节点 MCP Server 工具列表不一致的问题：
 * 1. 主路径 — Redis Pub/Sub 实时广播
 * - 工具变更时 → 发布消息到 Redis 频道 "mcp:tool:sync"
 * - 所有节点订阅该频道 → 收到消息后执行本地注册/注销
 * 2. 兜底 — 定时全量同步
 * - 每 5 分钟执行一次 syncAllTools()
 * - 防止消息丢失或 Redis 短暂不可用导致的遗漏
 * <p>
 * 使用已有的 Redisson RTopic，无需额外基础设施。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolSyncService {

    private static final String SYNC_CHANNEL = "mcp:tool:sync";

    private final RedissonClient redissonClient;
    private final McpToolRegistry mcpToolRegistry;

    /**
     * 启动时订阅 Redis 频道，接收其他节点的工具变更通知
     */
    @PostConstruct
    public void init() {
        RTopic topic = redissonClient.getTopic(SYNC_CHANNEL);
        topic.addListener(McpToolSyncMessage.class, (channel, msg) -> {
            log.info("收到跨节点工具同步消息: toolId={}, changeType={}",
                    msg.getToolId(), msg.getChangeType());
            mcpToolRegistry.syncToolByChangeType(msg.getToolId(), msg.getChangeType());
        });
        log.info("MCP 工具同步频道已订阅: {}", SYNC_CHANNEL);
    }

    /**
     * 广播工具变更事件到所有节点（含本节点）
     *
     * @param toolId     工具 ID
     * @param changeType 变更类型（CREATE/UPDATE/DELETE/ENABLE/DISABLE）
     */
    public void broadcastChange(Long toolId, String changeType) {
        McpToolSyncMessage msg = new McpToolSyncMessage(toolId, changeType);
        try {
            long subscribers = redissonClient.getTopic(SYNC_CHANNEL).publish(msg);
            log.info("已广播工具同步消息: toolId={}, changeType={}, 在线订阅者数={}",
                    toolId, changeType, subscribers);
        } catch (Exception e) {
            log.error("广播工具同步消息失败: toolId={}, changeType={}", toolId, changeType, e);
        }
    }

    /**
     * 定时全量同步兜底 — 每 5 分钟执行一次
     * <p>
     * 防止以下情况导致的遗漏：
     * - Redis 消息丢失
     * - 新节点加入集群
     * - 节点重启后恢复
     */
    @Scheduled(fixedRate = 300_000)
    public void scheduledSync() {
        log.debug("执行定时全量同步兜底...");
        mcpToolRegistry.syncAllTools();
    }
}
