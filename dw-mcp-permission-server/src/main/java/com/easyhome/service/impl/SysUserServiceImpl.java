package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysUser;
import com.easyhome.entity.SysUserRole;
import com.easyhome.mapper.SysRoleMapper;
import com.easyhome.mapper.SysUserMapper;
import com.easyhome.mapper.SysUserRoleMapper;
import com.easyhome.mcp.event.RbacChangeEvent;
import com.easyhome.service.SysApiKeyService;
import com.easyhome.service.SysUserService;
import com.easyhome.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final UserPermissionCacheService userPermissionCacheService;
    private final SysApiKeyService apiKeyService;

    private static final String USER_ROLES_LOCK_PREFIX = "rbac:user:roles:";

    /** 登录失败计数 Redis Key 前缀 */
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    /** 最大连续错误次数 */
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    /** 锁定时长（分钟） */
    private static final int LOCK_DURATION_MINUTES = 30;

    /** 重置密码的默认密码 */
    private static final String DEFAULT_PASSWORD = "Admin@2024";

    @Override
    public SysUser authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1));
        if (user == null || user.getPassword() == null) {
            return null;
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        return user;
    }

    @Override
    public Page<SysUser> page(long current, long size, String username, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt);
        return userMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public SysUser getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public SysUser create(SysUser user) {
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.insert(user);
        return user;
    }

    @Override
    public SysUser update(SysUser user) {
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        // 如果状态变更，清除该用户的所有缓存
        if (user.getStatus() != null) {
            evictUserAllCache(user.getId());
        }
        userMapper.updateById(user);
        return userMapper.selectById(user.getId());
    }

    @Override
    public void delete(Long id) {
        // 先清除缓存再删除，确保 listByUserId 能查到 API Key
        evictUserAllCache(id);
        userMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // 状态变更，清除该用户的所有缓存
        evictUserAllCache(id);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status));
    }

    /**
     * 清除用户所有相关缓存
     * <p>
     * 包含：权限缓存 + API Key 验证缓存
     * 在用户被停用/删除时调用，确保攻击者无法继续使用缓存的凭据
     *
     * @param userId 用户 ID
     */
    private void evictUserAllCache(Long userId) {
        try {
            // 1. 清除用户权限缓存（角色 + 工具）
            userPermissionCacheService.evictUserPermissionsByUserIds(userId);
            // 2. 清除该用户所有 API Key 验证缓存
            apiKeyService.evictApiKeyCacheByUserId(userId);
            log.info("用户所有缓存已清除: userId={}", userId);
        } catch (Exception e) {
            log.error("清除用户缓存失败: userId={}", userId, e);
        }
    }

    @Override
    public List<SysRole> getRoles(Long userId) {
        return roleMapper.selectRolesByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        RLock lock = redissonClient.getLock(USER_ROLES_LOCK_PREFIX + userId);
        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    // 逻辑删除现有关联（@TableLogic 会将 is_deleted 置为 1）
                    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, userId));
                    // 插入新关联
                    if (roleIds != null && !roleIds.isEmpty()) {
                        for (Long roleId : roleIds) {
                            SysUserRole ur = new SysUserRole();
                            ur.setUserId(userId);
                            ur.setRoleId(roleId);
                            userRoleMapper.insert(ur);
                        }
                    }
                    // 用户角色变更，通知 MCP 客户端刷新工具列表
                    eventPublisher.publishEvent(RbacChangeEvent.userRolesChanged(this, userId));
                } catch (Exception e) {
                    log.error("用户角色分配失败, userId={}", userId, e);
                    throw new RuntimeException("操作冲突，请稍后重试");
                } finally {
                    lock.unlock();
                }
            } else {
                log.warn("获取用户角色分配分布式锁超时, userId={}", userId);
                throw new RuntimeException("操作冲突，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断，请稍后重试");
        }
    }

    @Override
    public void resetPassword(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(DEFAULT_PASSWORD)));
        // 重置密码后清除登录错误计数
        clearLoginFailures(user.getUsername());
        log.info("用户密码已重置为默认密码: userId={}, username={}", userId, user.getUsername());
    }

    @Override
    public SysUser updateProfile(Long userId, String nickname, String email, String remark) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId);
        boolean needUpdate = false;
        if (nickname != null) { wrapper.set(SysUser::getNickname, nickname); needUpdate = true; }
        if (email != null) { wrapper.set(SysUser::getEmail, email); needUpdate = true; }
        if (remark != null) { wrapper.set(SysUser::getRemark, remark); needUpdate = true; }
        if (needUpdate) {
            userMapper.update(null, wrapper);
        }
        return userMapper.selectById(userId);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword)));
        log.info("用户修改密码成功: userId={}, username={}", userId, user.getUsername());
    }

    @Override
    public SysUser findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getIsDeleted, 0));
    }

    @Override
    public void resetPasswordByUsername(String username, String newPassword) {
        SysUser user = findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword)));
        // 重置密码后清除登录错误计数
        clearLoginFailures(username);
        log.info("忘记密码重置成功: username={}", username);
    }

    // ========== 登录错误次数锁定 ==========

    @Override
    public boolean isLoginLocked(String username) {
        return getLoginFailCount(username) >= MAX_LOGIN_ATTEMPTS;
    }

    @Override
    public long getLoginLockRemainingMinutes(String username) {
        RAtomicLong counter = redissonClient.getAtomicLong(LOGIN_FAIL_PREFIX + username);
        long ttlMs = counter.remainTimeToLive();
        if (ttlMs <= 0) return 0;
        return (ttlMs / 1000 + 59) / 60; // 向上取整到分钟
    }

    @Override
    public void recordLoginFailure(String username) {
        RAtomicLong counter = redissonClient.getAtomicLong(LOGIN_FAIL_PREFIX + username);
        long count = counter.incrementAndGet();
        // 每次检查 TTL，防止 expire 丢失导致 key 永不过期
        if (counter.remainTimeToLive() <= 0) {
            counter.expire(LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
        if (count >= MAX_LOGIN_ATTEMPTS) {
            log.warn("用户登录失败达到 {} 次，账号已锁定 {} 分钟: username={}", count, LOCK_DURATION_MINUTES, username);
        } else {
            log.info("用户登录失败 {}/{} 次: username={}", count, MAX_LOGIN_ATTEMPTS, username);
        }
    }

    @Override
    public void clearLoginFailures(String username) {
        redissonClient.getAtomicLong(LOGIN_FAIL_PREFIX + username).delete();
    }

    @Override
    public int getLoginFailCount(String username) {
        RAtomicLong counter = redissonClient.getAtomicLong(LOGIN_FAIL_PREFIX + username);
        return counter.isExists() ? (int) counter.get() : 0;
    }
}
