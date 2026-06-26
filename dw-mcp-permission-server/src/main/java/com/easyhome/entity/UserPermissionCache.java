package com.easyhome.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 用户权限缓存对象
 * <p>
 * 包含用户的角色列表和可访问的工具列表，统一管理用户权限缓存
 *
 * @author DW MCP Team
 * @date 2026/6/1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionCache implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户拥有的角色列表
     */
    private List<String> roleCodes;

    /**
     * 用户可访问的 MCP 工具列表
     */
    private Set<String> accessibleToolNames;

    /**
     * 缓存创建时间戳（毫秒）
     */
    private Long cachedAt;

    /**
     * 创建缓存对象
     */
    public static UserPermissionCache of(Long userId, List<String> roleCodes, Set<String> accessibleToolNames) {
        return UserPermissionCache.builder()
                .userId(userId)
                .roleCodes(roleCodes)
                .accessibleToolNames(accessibleToolNames)
                .cachedAt(System.currentTimeMillis())
                .build();
    }
}
