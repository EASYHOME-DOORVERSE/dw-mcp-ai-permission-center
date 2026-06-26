package com.easyhome.mcp;

import com.alibaba.fastjson2.JSON;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.McpToolCallLog;
import com.easyhome.service.McpToolCallLogService;
import com.easyhome.service.McpToolService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 工具注册中心
 * <p>
 * 负责将数据库中的工具定义与 MCP Server 的运行时工具列表保持同步。
 * 支持：注册单个工具、注销单个工具、全量同步。
 * <p>
 * 关键操作使用 Redis 分布式锁（Redisson），保证多机部署时的并发安全。
 * <p>
 * 注册工具时，callHandler 会校验当前用户的 RBAC 权限：
 * - 从 McpTransportContext 获取用户身份（由 RbacMcpContextExtractor 注入）
 * - 委托 McpToolPermissionService 进行权限校验
 * - 无权限则拒绝执行并记录审计日志
 *
 * @author DW MCP Team
 * @date 2026/5/27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private static final String ANONYMOUS_USER = "anonymous";
    private static final String LOCK_PREFIX = "mcp:tool:lock:";

    private final McpStatelessSyncServer mcpStatelessSyncServer;
    private final JdbcToolExecutor jdbcToolExecutor;
    private final HttpProxyToolExecutor httpProxyToolExecutor;
    private final McpToolService mcpToolService;
    private final McpToolCallLogService callLogService;
    private final RedissonClient redissonClient;

    /** 本地缓存：toolName -> 上次注册时的 updatedAt，用于全量同步时检测变更 */
    private final ConcurrentHashMap<String, LocalDateTime> registeredVersions = new ConcurrentHashMap<>();

    /**
     * 注册单个工具到 MCP Server
     *
     * @param tool 数据库工具定义（必须已设置 id 和 toolName）
     */
    public void registerTool(McpTool tool) {
        if (tool == null || tool.getId() == null) {
            log.warn("注册工具失败：工具或 id 为空");
            return;
        }
        if (tool.getToolName() == null || tool.getToolName().isBlank()) {
            log.warn("注册工具失败：toolName 为空, toolId={}", tool.getId());
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + tool.getToolName());
        try {
            lock.lock();
            try {
                doRegisterTool(tool);
            } catch (Exception e) {
                log.error("注册工具失败：{}", tool.getToolName(), e);
                throw e;
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("注册单个工具获取分布式锁失败: {}", tool.getToolName());
            throw e;
        }
    }

    private void doRegisterTool(McpTool tool) {
        try {
            // 根据工具类型构建 ToolCallback
            ToolCallback callback = buildCallbackByType(tool);

            // 构建 MCP Schema Tool
            McpSchema.Tool mcpTool = buildMcpTool(tool);

            // 构建 SyncToolSpecification（callHandler 包含 RBAC 权限校验）
            McpStatelessServerFeatures.SyncToolSpecification spec = new McpStatelessServerFeatures.SyncToolSpecification(
                    mcpTool,
                    (ctx, request) -> executeWithRbacCheck(ctx, request, callback, tool)
            );

            mcpStatelessSyncServer.addTool(spec);
            log.info("MCP 工具已注册: {}", tool.getToolName());
        } catch (Exception e) {
            log.error("注册 MCP 工具失败: {}", tool.getToolName(), e);
        }
    }

    /**
     * 从 MCP Server 注销单个工具
     *
     * @param toolName 工具的 toolName
     */
    public void unregisterTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + toolName);
        try {
            lock.lock();
            try {
                doUnregisterTool(toolName);
            } catch (Exception e) {
                log.error("注销工具失败：{}", toolName, e);
                throw e;
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("注销单个工具获取分布式锁失败: {}", toolName);
            throw e;
        }
    }

    private void doUnregisterTool(String toolName) {
        try {
            mcpStatelessSyncServer.removeTool(toolName);
            log.info("MCP 工具已注销: {}", toolName);
        } catch (Exception e) {
            log.error("注销 MCP 工具失败: {}", toolName, e);
        }
    }

    /**
     * 根据变更类型同步工具到 MCP Server
     * <p>
     * 供事件监听器和跨节点同步服务调用，避免重复代码。
     * 内部自动从数据库查询工具定义，调用方只需传入 toolId。
     *
     * @param toolId     工具 ID
     * @param changeType 变更类型（CREATE/UPDATE/DELETE/ENABLE/DISABLE）
     */
    public void syncToolByChangeType(Long toolId, String changeType) {
        if (toolId == null) {
            log.warn("工具 ID 为空，跳过同步");
            return;
        }
            
        McpTool tool = mcpToolService.getById(toolId);
        if (tool == null) {
            log.warn("工具不存在，跳过同步: toolId={}", toolId);
            return;
        }
            
        switch (changeType) {
            case "CREATE":
                if (tool.getStatus() != null && tool.getStatus() == 1) {
                    registerTool(tool);
                }
                break;
            case "UPDATE":
                if (tool.getStatus() != null && tool.getStatus() == 1) {
                    registerTool(tool);
                } else {
                    unregisterTool(tool.getToolName());
                }
                break;
            case "DELETE":
            case "DISABLE":
                unregisterTool(tool.getToolName());
                break;
            case "ENABLE":
                registerTool(tool);
                break;
            default:
                log.warn("未知的变更类型: {}", changeType);
        }
    }

    /**
     * 全量同步：将数据库中所有启用的工具注册到 MCP Server
     * <p>
     * 策略：
     * 1. 获取当前 MCP Server 已注册的所有工具
     * 2. 获取数据库中所有启用的工具
     * 3. 移除数据库中不存在的工具（使用 HashSet 优化查找性能）
     * 4. 注册或刷新数据库中存在的工具
     */
    public void syncAllTools() {
        RLock lock = redissonClient.getLock("mcp:sync:all");
        try {
            lock.lock();

            try {
                doSyncAllTools();
            } catch (Exception e) {
                log.error("全量同步工具失败", e);
                throw e;
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            log.warn("获取全量同步分布式锁被中断");
            throw e;
        }
    }

    private void doSyncAllTools() {
        log.info("开始全量同步 MCP 工具...");
        try {
            // 当前 MCP Server 已注册的工具名称集合
            Set<String> registeredNames = mcpStatelessSyncServer.listTools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toSet());

            // 数据库中启用的工具
            List<McpTool> enabledTools = mcpToolService.listAllEnabledTools();

            // 构建启用工具名称集合，用于快速查找（O(1) 复杂度）
            Set<String> enabledToolNames = enabledTools.stream()
                    .map(McpTool::getToolName)
                    .collect(Collectors.toSet());

            // 移除数据库中不存在的工具
            int removed = 0;
            for (String registeredName : registeredNames) {
                if (!enabledToolNames.contains(registeredName)) {
                    doUnregisterTool(registeredName);
                    registeredVersions.remove(registeredName);
                    removed++;
                }
            }

            // 注册新增或更新的工具（跳过未变更的）
            int added = 0, updated = 0;
            for (McpTool tool : enabledTools) {
                String name = tool.getToolName();
                boolean isNew = !registeredNames.contains(name);
                boolean isChanged = !isNew && isToolChanged(name, tool.getUpdatedAt());

                if (isNew || isChanged) {
                    doRegisterTool(tool);
                    registeredVersions.put(name, tool.getUpdatedAt());
                    if (isNew) added++;
                    else updated++;
                }
            }

            log.info("MCP 工具全量同步完成，当前注册工具数: {}，本次新增: {}，更新: {}，移除: {}", enabledTools.size(), added, updated, removed);
        } catch (Exception e) {
            log.error("全量同步 MCP 工具失败", e);
        }
    }

    /**
     * 判断工具定义是否发生变化（比较 updatedAt 时间戳）
     */
    private boolean isToolChanged(String toolName, LocalDateTime updatedAt) {
        LocalDateTime lastVersion = registeredVersions.get(toolName);
        if (lastVersion == null || updatedAt == null) return true;
        return !lastVersion.equals(updatedAt);
    }

    /**
     * RBAC 权限校验 + 执行工具调用
     * <p>
     * 校验流程：
     * 1. 从 TransportContext 获取当前用户 ID
     * 2. 无认证信息 → 拒绝执行
     * 3. 查询该用户可访问的工具 ID 列表
     * 4. 当前工具不在列表中 → 拒绝执行
     * 5. 权限通过 → 执行工具
     */
    private McpSchema.CallToolResult executeWithRbacCheck(
            McpTransportContext ctx,
            McpSchema.CallToolRequest request,
            ToolCallback callback,
            McpTool tool) {

        Object userIdObj = ctx.get(RbacMcpContextExtractor.USER_ID);
        LocalDateTime callAt = LocalDateTime.now();
        String requestArgs = JSON.toJSONString(request.arguments());

        // 无认证信息
        if (userIdObj == null) {
            log.warn("tools/call 拒绝：无认证信息, tool={}", tool.getToolName());
            saveCallLog(tool, -1L, ANONYMOUS_USER, callAt, 0L, false,
                    "authentication required", requestArgs, 0);
            return buildErrorResult("Permission denied: authentication required");
        }

        Long userId = (Long) userIdObj;
        String username = (String) ctx.get(RbacMcpContextExtractor.USERNAME);

        Set<String> accessibleToolNames = (Set<String>) ctx.get(RbacMcpContextExtractor.ACCESSIBLE_TOOL_NAMES);
        if (accessibleToolNames == null || !accessibleToolNames.contains(tool.getToolName())) {
            log.warn("tools/call 拒绝：无权限访问, tool={}", tool.getToolName());
            saveCallLog(tool, userId, username != null ? username : "", callAt, 0L, false,
                    "no access to tool " + tool.getToolName(), requestArgs, 0);
            return buildErrorResult("Permission denied: no access to tool " + tool.getToolName());
        }

        // 权限通过，执行工具并记录日志
        long startMs = System.currentTimeMillis();
        McpSchema.CallToolResult result = executeToolCallback(callback, request);
        long durationMs = System.currentTimeMillis() - startMs;

        int responseSize = calculateResponseSize(result);

        saveCallLog(tool, userId, username != null ? username : "", callAt, durationMs,
                !result.isError(), null, requestArgs, responseSize);

        return result;
    }

    /**
     * 根据工具类型构建对应的 ToolCallback
     */
    private ToolCallback buildCallbackByType(McpTool tool) {
        return switch (tool.getToolType()) {
            case "HTTP_PROXY" -> httpProxyToolExecutor.buildCallback(tool);
            default -> jdbcToolExecutor.buildCallback(tool);
        };
    }

    /**
     * 构建 MCP Schema Tool 定义
     *
     * @param tool 数据库工具定义
     * @return MCP Schema Tool 对象
     */
    private McpSchema.Tool buildMcpTool(McpTool tool) {
        String description = buildDescription(tool);
        Map<String, Object> inputSchema = parseInputSchema(tool.getInputSchema());

        return McpSchema.Tool.builder()
                .name(tool.getToolName())
                .title(tool.getDisplayName())
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    /**
     * 执行 ToolCallback 并包装为 MCP CallToolResult
     */
    private McpSchema.CallToolResult executeToolCallback(ToolCallback callback,
                                                         McpSchema.CallToolRequest request) {
        try {
            // 将参数 Map 转为 JSON 字符串
            String argsJson = JSON.toJSONString(request.arguments());
            // 调用 ToolCallback
            String result = callback.call(argsJson);
            // 包装为 MCP 结果
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(result)),
                    false,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("MCP 工具执行失败: {}", request.name(), e);
            return buildErrorResult(e.getMessage());
        }
    }

    /**
     * 构建错误返回结果
     */
    private McpSchema.CallToolResult buildErrorResult(String errorMessage) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                        JSON.toJSONString(Map.of("success", false, "error", errorMessage)))),
                true,
                null,
                null
        );
    }

    /**
     * 保存工具调用日志
     *
     * @param tool         工具对象
     * @param userId       用户 ID
     * @param username     用户名
     * @param callAt       调用时间
     * @param durationMs   执行耗时（毫秒）
     * @param success      是否成功
     * @param denyReason   拒绝原因（权限不足时填写）
     * @param requestArgs  请求参数
     * @param responseSize 响应大小
     */
    private void saveCallLog(McpTool tool, Long userId, String username, LocalDateTime callAt,
                             long durationMs, boolean success, String denyReason,
                             String requestArgs, int responseSize) {
        callLogService.saveAsync(McpToolCallLog.builder()
                .toolId(tool.getId())
                .toolName(tool.getToolName())
                .userId(userId)
                .username(username)
                .callAt(callAt)
                .durationMs(durationMs)
                .success(success)
                .denyReason(denyReason)
                .requestArgs(requestArgs)
                .responseSize(responseSize)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * 计算响应内容大小
     *
     * @param result MCP 调用结果
     * @return 响应文本总长度
     */
    private int calculateResponseSize(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return 0;
        }
        return result.content().stream()
                .mapToInt(c -> {
                    if (c instanceof McpSchema.TextContent tc) {
                        return tc.text() == null ? 0 : tc.text().length();
                    }
                    return 0;
                })
                .sum();
    }

    /**
     * 构建工具描述
     */
    private String buildDescription(McpTool tool) {
        StringBuilder desc = new StringBuilder();
        desc.append("[").append(tool.getToolName()).append("]");
        if (tool.getDisplayName() != null && !tool.getDisplayName().isBlank()) {
            desc.append(" ").append(tool.getDisplayName());
        }
        if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
            desc.append(" - ").append(tool.getDescription());
        }
        return desc.toString();
    }

    /**
     * 解析 inputSchema JSON 字符串为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInputSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return Map.of("type", "object", "properties", Map.of(), "required", List.of());
        }
        try {
            return JSON.parseObject(inputSchema, Map.class);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of(), "required", List.of());
        }
    }

}
