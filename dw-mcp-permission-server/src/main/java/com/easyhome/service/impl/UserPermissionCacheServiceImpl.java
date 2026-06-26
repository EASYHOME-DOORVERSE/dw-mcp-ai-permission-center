package com.easyhome.service.impl;

import com.easyhome.entity.McpTool;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.UserPermissionCache;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.mapper.SysRoleMapper;
import com.easyhome.mapper.SysUserRoleMapper;
import com.easyhome.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionCacheServiceImpl implements UserPermissionCacheService {

    private static final String CACHE_KEY_PREFIX = "user:permission:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final SysRoleMapper roleMapper;
    private final McpToolMapper mcpToolMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RedissonClient redissonClient;

    @Override
    public List<String> getUserRoles(Long userId) {
        UserPermissionCache cache = getUserPermissionCache(userId);
        return cache != null ? cache.getRoleCodes() : List.of();
    }

    @Override
    public List<String> getAccessibleTools(Long userId) {
        UserPermissionCache cache = getUserPermissionCache(userId);
        return cache != null ? new java.util.ArrayList<>(cache.getAccessibleToolNames()) : List.of();
    }

    @Override
    public UserPermissionCache getUserPermissionCache(Long userId) {
        if (userId == null) {
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + userId;

        // 1. 尝试从缓存读取
        try {
            UserPermissionCache cached = (UserPermissionCache) redissonClient.getBucket(cacheKey).get();
            if (cached != null) {
                log.debug("命中用户权限缓存: userId={}, roleCount={}, toolCount={}",
                        userId, cached.getRoleCodes().size(), cached.getAccessibleToolNames().size());
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取用户权限缓存失败，降级到数据库查询: userId={}", userId, e);
        }

        // 2. 缓存未命中，查询数据库
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        List<McpTool> tools = mcpToolMapper.selectToolsByUserId(userId);
        Set<String> toolNames = tools.stream().map(McpTool::getToolName).collect(Collectors.toSet());

        // 3. 构建缓存对象
        UserPermissionCache cache = UserPermissionCache.of(userId, roleCodes, toolNames);

        // 4. 写入缓存
        try {
            redissonClient.getBucket(cacheKey).set(cache, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("用户权限已缓存: userId={}, roleCount={}, toolCount={}",
                    userId, roles.size(), tools.size());
        } catch (Exception e) {
            log.warn("写入用户权限缓存失败: userId={}", userId, e);
        }

        return cache;
    }

    @Override
    public void evictUserPermissionsByUserIds(Long... userIds) {
        if (userIds == null || userIds.length == 0) {
            return;
        }

        String[] cacheKeys = new String[userIds.length];
        for (int i = 0; i < userIds.length; i++) {
            cacheKeys[i] = CACHE_KEY_PREFIX + userIds[i];
        }

        try {
            long deletedCount = redissonClient.getKeys().delete(cacheKeys);
            if (deletedCount > 0) {
                log.debug("用户权限缓存已清除: count={}, userIds={}", deletedCount, Arrays.toString(userIds));
            }
        } catch (Exception e) {
            log.error("清除用户权限缓存失败: userIds={}", Arrays.toString(userIds), e);
        }
    }

    @Override
    public void evictUserPermissionsByRoleIds(Long... roleIds) {
        if (roleIds == null || roleIds.length == 0) {
            return;
        }

        try {
            log.info("开始批量清除用户权限缓存: roleIds={}", Arrays.toString(roleIds));

            int pageSize = 500;
            Long lastUserId = 0L;
            int processedCount = 0;
            int batchCount = 0;
            Set<Long> roleIdSet = new HashSet<>(Arrays.asList(roleIds));

            while (true) {
                List<Long> userIds = userRoleMapper.selectUserIdsByRoleIdsWithCursor(
                        roleIdSet, lastUserId, pageSize);

                if (userIds == null || userIds.isEmpty()) {
                    break;
                }

                batchCount++;
                log.debug("处理第 {} 批用户权限缓存清除，数量: {}", batchCount, userIds.size());

                processedCount += userIds.size();
                evictUserPermissionsByUserIds(userIds.toArray(new Long[0]));

                lastUserId = userIds.getLast();

                if (userIds.size() < pageSize) {
                    break;
                }
            }

            log.info("批量清除用户权限缓存完成: roleIds={}, batchCount={}, processedCount={}",
                    Arrays.toString(roleIds), batchCount, processedCount);
        } catch (Exception e) {
            log.error("批量清除用户权限缓存失败: roleIds={}", Arrays.toString(roleIds), e);
        }
    }
}
