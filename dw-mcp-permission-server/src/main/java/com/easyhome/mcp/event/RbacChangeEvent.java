package com.easyhome.mcp.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * RBAC 权限变更事件
 *
 * 当用户角色或角色工具授权发生变更时触发，
 * 通知 MCP 客户端刷新工具列表（notifyToolsListChanged）。
 *
 * 触发场景：
 *   - 用户角色维护（assignRoles）
 *   - 角色工具授权变更（assignTools）
 *   - 角色删除/停用（影响该角色下所有用户的工具权限）
 */
@Getter
public class RbacChangeEvent extends ApplicationEvent {

    private final ChangeType changeType;
    /** 关联的角色 ID（角色工具授权变更时） */
    private final Long roleId;
    /** 关联的用户 ID（用户角色变更时） */
    private final Long userId;

    public RbacChangeEvent(Object source, ChangeType changeType, Long roleId, Long userId) {
        super(source);
        this.changeType = changeType;
        this.roleId = roleId;
        this.userId = userId;
    }

    /**
     * 创建「角色工具授权变更」事件
     */
    public static RbacChangeEvent roleToolsChanged(Object source, Long roleId) {
        return new RbacChangeEvent(source, ChangeType.ROLE_TOOLS_CHANGED, roleId, null);
    }

    /**
     * 创建「用户角色变更」事件
     */
    public static RbacChangeEvent userRolesChanged(Object source, Long userId) {
        return new RbacChangeEvent(source, ChangeType.USER_ROLES_CHANGED, null, userId);
    }

    public enum ChangeType {
        ROLE_TOOLS_CHANGED,  // 角色工具授权变更
        USER_ROLES_CHANGED   // 用户角色变更
    }
}
