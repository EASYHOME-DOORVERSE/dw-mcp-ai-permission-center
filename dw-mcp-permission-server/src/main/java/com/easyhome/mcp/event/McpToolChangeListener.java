package com.easyhome.mcp.event;

import com.easyhome.entity.SysRole;
import com.easyhome.service.SysRoleService;
import com.easyhome.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * MCP 工具变更 & RBAC 权限变更事件监听器
 * <p>
 * 1. 监听 McpToolChangeEvent，通过 Redis Pub/Sub 广播到所有节点。
 * 各节点（含本节点）收到消息后各自执行工具注册/注销。
 * 2. 监听 RbacChangeEvent，通知 MCP 客户端工具列表已变更，
 * 触发客户端重新拉取（tools/list），此时 RBAC handler 会
 * 根据最新数据库权限动态过滤工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolChangeListener {

    private final McpToolSyncService syncService;
    private final SysRoleService sysRoleService;
    private final UserPermissionCacheService userPermissionCacheService;

    /**
     * 监听 MCP 工具变更事件（异步执行）
     * <p>
     * 使用 @TransactionalEventListener 确保在事务提交后执行，避免查询不到刚插入的数据。
     * 收到 Spring 本地事件后，通过 Redis Pub/Sub 广播到所有节点。
     * 本节点同时立即执行本地刷新（作为快速路径，避免等待 Redis 往返）。
     * <p>
     * 优化：
     * 1. 收集所有关联用户，去重后批量清除（减少数据库查询次数）
     * 2. 添加异常处理，确保单个失败不影响整体流程
     * 3. 完善日志记录，方便追踪和监控
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMcpToolChange(McpToolChangeEvent event) {
        try {
            Long toolId = event.getToolId();
            McpToolChangeEvent.ChangeType type = event.getChangeType();
            String changeTypeName = type.name();

            log.info("收到本机 MCP 工具变更事件: toolId={}, type={}", toolId, changeTypeName);

            // 1. 通过 Redis 广播到所有节点（其他节点会收到并执行同步）
            syncService.broadcastChange(toolId, changeTypeName);

            // 2. 根据变更类型决定是否需要清除缓存
            switch (type) {
                case DELETE:
                case ENABLE:
                case DISABLE:
                    evictCacheByToolId(toolId);
                    break;
                case CREATE:
                case UPDATE:
                    log.debug("工具变更类型 {} 不需要清除缓存: toolId={}", changeTypeName, toolId);
                    break;
                default:
                    log.warn("未知的工具变更类型: type={}, toolId={}", changeTypeName, toolId);
                    break;
            }
        } catch (Exception e) {
            log.error("异步处理 MCP 工具变更事件失败: toolId={}, type={}",
                    event.getToolId(), event.getChangeType(), e);
        }
    }

    /**
     * 根据工具 ID 清除所有相关用户的权限缓存
     * <p>
     * 优化策略：
     * 1. 查询所有关联该工具的角色
     * 2. 收集所有角色 ID
     * 3. 使用去重优化方法一次性清除所有用户缓存
     *
     * @param toolId 工具 ID
     */
    private void evictCacheByToolId(Long toolId) {
        try {
            // 1. 查询所有关联该工具的角色
            List<SysRole> roles = sysRoleService.getRolesByToolId(toolId);
            if (roles == null || roles.isEmpty()) {
                log.info("工具没有关联任何角色，无需清除缓存: toolId={}", toolId);
                return;
            }

            log.info("工具关联 {} 个角色，开始清除用户缓存: toolId={}", roles.size(), toolId);

            // 2. 收集所有角色 ID（直接返回数组）
            Long[] roleIds = roles.stream().map(SysRole::getId).toArray(Long[]::new);
            
            // 3. 使用去重优化方法一次性清除（内部会收集所有用户并去重）
            userPermissionCacheService.evictUserPermissionsByRoleIds(roleIds);

            log.info("工具变更缓存清除完成: toolId={}, roleCount={}", toolId, roles.size());
        } catch (Exception e) {
            log.error("清除工具相关用户缓存失败: toolId={}", toolId, e);
        }
    }

    /**
     * 监听 RBAC 权限变更事件（异步执行）
     * <p>
     * 当用户角色或角色工具授权发生变更时，异步清除相关用户的角色缓存。
     * 使用异步执行避免阻塞主事务，提升响应速度。
     * <p>
     * 注意：
     * 1. 使用 @Async 注解实现异步执行
     * 2. 事务已提交后才会触发（@TransactionalEventListener）
     * 3. 缓存清除失败不影响主流程
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRbacChange(RbacChangeEvent event) {
        try {
            RbacChangeEvent.ChangeType type = event.getChangeType();
            log.info("异步处理 RBAC 权限变更事件: type={}, roleId={}, userId={}",
                    type, event.getRoleId(), event.getUserId());

            switch (type) {
                case ROLE_TOOLS_CHANGED:
                    log.info("开始清除角色下所有用户的权限缓存: roleId={}", event.getRoleId());
                    // 清除该角色下所有用户的权限缓存（包含角色和工具）
                    userPermissionCacheService.evictUserPermissionsByRoleIds(event.getRoleId());
                    break;
                case USER_ROLES_CHANGED:
                    log.info("开始清除用户权限缓存: userId={}", event.getUserId());
                    // 清除该用户的权限缓存（包含角色和工具）
                    userPermissionCacheService.evictUserPermissionsByUserIds(event.getUserId());
                    break;
                default:
                    log.warn("收到 RBAC 权限变更事件，未知类型: type={}, roleId={}, userId={}",
                            type, event.getRoleId(), event.getUserId());
                    break;
            }

            log.info("RBAC 权限变更事件处理完成: type={}, roleId={}, userId={}",
                    type, event.getRoleId(), event.getUserId());
        } catch (Exception e) {
            log.error("异步处理 RBAC 权限变更事件失败: type={}, roleId={}, userId={}",
                    event.getChangeType(), event.getRoleId(), event.getUserId(), e);
        }
    }
}
