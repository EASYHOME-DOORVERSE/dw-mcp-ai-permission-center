package com.easyhome.service;

import com.easyhome.entity.UserPermissionCache;

import java.util.List;

/**
 * 用户权限缓存服务（统一管理）
 * <p>
 * 缓存策略：
 * 1. 使用统一的 UserPermissionCache 对象，包含用户的角色和工具列表
 * 2. 首次查询时从数据库加载并缓存到 Redis（TTL 5分钟）
 * 3. 用户角色或工具权限变更时主动清除缓存
 * 4. 缓存未命中时自动回源数据库
 */
public interface UserPermissionCacheService {

    /** 获取用户角色列表（从统一缓存中获取） */
    List<String> getUserRoles(Long userId);

    /** 获取用户可访问的工具列表（从统一缓存中获取） */
    List<String> getAccessibleTools(Long userId);

    /** 获取用户权限缓存（统一对象） */
    UserPermissionCache getUserPermissionCache(Long userId);

    /** 清除用户权限缓存（包含角色和工具） */
    void evictUserPermissionsByUserIds(Long... userIds);

    /** 批量清除用户权限缓存（根据角色ID游标分页查询关联用户再清除） */
    void evictUserPermissionsByRoleIds(Long... roleIds);
}
