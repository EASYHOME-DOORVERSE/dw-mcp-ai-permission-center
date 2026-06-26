package com.easyhome.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.SysApiKey;
import com.easyhome.entity.SysUser;
import com.easyhome.mapper.SysApiKeyMapper;
import com.easyhome.mapper.SysUserMapper;
import com.easyhome.service.SysApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysApiKeyServiceImpl implements SysApiKeyService {

    private static final String CACHE_KEY_PREFIX = "apikey:validate:";
    private static final long CACHE_SUCCESS_TTL_HOURS = 24;  // 验证成功缓存 24 小时
    private static final long CACHE_FAILURE_TTL_MINUTES = 5; // 验证失败缓存 5 分钟（防攻击）

    private final SysApiKeyMapper apiKeyMapper;
    private final SysUserMapper userMapper;
    private final RedissonClient redissonClient;

    @Override
    public ApiKeyValidationResult validateAndGetUser(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + apiKey;

        // 1. 尝试从缓存读取
        try {
            Object cached = redissonClient.getBucket(cacheKey).get();
            if (cached != null) {
                if (cached instanceof SysUser user) {
                    log.debug("API Key验证缓存命中（成功）: {}", maskKey(apiKey));
                    // 缓存命中时仍需查询 accountId（accountId 不缓存，但这是少数场景）
                    SysApiKey keyRecord = apiKeyMapper.selectByApiKey(apiKey);
                    String accountId = keyRecord != null ? keyRecord.getAccountId() : null;
                    return new ApiKeyValidationResult(user, accountId);
                } else if ("INVALID".equals(cached)) {
                    log.debug("API Key验证缓存命中（失败）: {}", maskKey(apiKey));
                    return null;
                }
            }
        } catch (Exception e) {
            log.warn("读取 API Key 验证缓存失败，降级到数据库查询: {}", maskKey(apiKey), e);
        }

        // 2. 缓存未命中，查询数据库
        SysApiKey keyRecord = apiKeyMapper.selectByApiKey(apiKey);
        if (keyRecord == null) {
            log.debug("API Key不存在: {}", maskKey(apiKey));
            cacheValidationResult(cacheKey, null, CACHE_FAILURE_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }

        if (!keyRecord.isValid()) {
            log.debug("API Key已停用或过期: {}", maskKey(apiKey));
            cacheValidationResult(cacheKey, null, CACHE_FAILURE_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }

        SysUser user = userMapper.selectById(keyRecord.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            log.debug("API Key对应用户不存在或已停用, userId={}", keyRecord.getUserId());
            cacheValidationResult(cacheKey, null, CACHE_FAILURE_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }

        // 3. 验证成功，缓存用户对象
        apiKeyMapper.updateLastUsedAt(apiKey);
        cacheValidationResult(cacheKey, user, CACHE_SUCCESS_TTL_HOURS, TimeUnit.HOURS);
        return new ApiKeyValidationResult(user, keyRecord.getAccountId());
    }

    /**
     * 缓存验证结果
     *
     * @param cacheKey 缓存 key
     * @param user     用户对象（验证成功）或 null（验证失败）
     * @param ttl      过期时间
     * @param timeUnit 时间单位
     */
    private void cacheValidationResult(String cacheKey, SysUser user, long ttl, TimeUnit timeUnit) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
            if (user != null) {
                // 验证成功：缓存用户对象
                bucket.set(user, ttl, timeUnit);
                log.debug("API Key验证结果已缓存（成功）: ttl={} {}", ttl, timeUnit);
            } else {
                // 验证失败：缓存标记，防止重复查询和恶意攻击
                bucket.set("INVALID", ttl, timeUnit);
                log.debug("API Key验证结果已缓存（失败）: ttl={} {}", ttl, timeUnit);
            }
        } catch (Exception e) {
            log.warn("缓存 API Key 验证结果失败: {}", maskKey(cacheKey.replace(CACHE_KEY_PREFIX, "")), e);
        }
    }

    /**
     * 根据 Key ID 清除缓存
     *
     * @param keyId API Key ID
     */
    private void evictCacheByKeyId(Long keyId) {
        if (keyId == null) {
            return;
        }
        SysApiKey key = apiKeyMapper.selectById(keyId);
        if (key != null) {
            evictApiKeyCache(key.getApiKey());
        }
    }

    /**
     * 清除指定 API Key 的验证缓存
     * <p>
     * 当 API Key 状态变更（启用/停用/删除）时调用
     *
     * @param apiKey API Key
     */
    public void evictApiKeyCache(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        String cacheKey = CACHE_KEY_PREFIX + apiKey;
        try {
            redissonClient.getBucket(cacheKey).delete();
            log.info("API Key 验证缓存已清除: {}", maskKey(apiKey));
        } catch (Exception e) {
            log.error("清除 API Key 验证缓存失败: {}", maskKey(apiKey), e);
        }
    }

    @Override
    public String getAccountIdByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        SysApiKey keyRecord = apiKeyMapper.selectByApiKey(apiKey);
        if (keyRecord == null) {
            return null;
        }
        return keyRecord.getAccountId();
    }

    @Override
    public SysApiKey generateKey(Long userId, String keyName) {
        String keyValue = "sk-" + RandomUtil.randomString(32);
        SysApiKey apiKey = new SysApiKey();
        apiKey.setUserId(userId);
        apiKey.setApiKey(keyValue);
        apiKey.setKeyName(keyName);
        apiKeyMapper.insert(apiKey);
        log.info("为用户[{}]生成新API Key: {}", userId, maskKey(keyValue));
        return apiKey;
    }

    @Override
    public void disableKey(Long keyId) {
        evictCacheByKeyId(keyId);
        apiKeyMapper.update(null, new LambdaUpdateWrapper<SysApiKey>()
                .eq(SysApiKey::getId, keyId)
                .set(SysApiKey::getStatus, 2));
    }

    @Override
    public void enableKey(Long keyId) {
        evictCacheByKeyId(keyId);
        apiKeyMapper.update(null, new LambdaUpdateWrapper<SysApiKey>()
                .eq(SysApiKey::getId, keyId)
                .set(SysApiKey::getStatus, 1));
    }

    @Override
    public void deleteKey(Long keyId) {
        evictCacheByKeyId(keyId);
        apiKeyMapper.deleteById(keyId);
    }

    @Override
    public Page<SysApiKey> page(long current, long size, Long userId, Integer status) {
        LambdaQueryWrapper<SysApiKey> wrapper = new LambdaQueryWrapper<SysApiKey>()
                .eq(userId != null, SysApiKey::getUserId, userId)
                .eq(status != null, SysApiKey::getStatus, status)
                .orderByDesc(SysApiKey::getCreatedAt);
        return apiKeyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public SysApiKey getById(Long keyId) {
        return apiKeyMapper.selectById(keyId);
    }

    @Override
    public List<SysApiKey> listByUserId(Long userId) {
        return apiKeyMapper.selectList(
                new LambdaQueryWrapper<SysApiKey>()
                        .eq(SysApiKey::getUserId, userId)
                        .orderByDesc(SysApiKey::getCreatedAt));
    }

    /**
     * 清除指定用户所有 API Key 的验证缓存
     * <p>
     * 当用户被停用/删除时调用，确保缓存的用户对象立即失效
     *
     * @param userId 用户 ID
     */
    @Override
    public void evictApiKeyCacheByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            List<SysApiKey> keys = listByUserId(userId);
            if (keys == null || keys.isEmpty()) {
                log.debug("该用户没有 API Key，无需清除缓存: userId={}", userId);
                return;
            }
            // 批量构建缓存 Key，一次 Redis 操作全部删除
            String[] cacheKeys = keys.stream()
                    .map(key -> CACHE_KEY_PREFIX + key.getApiKey())
                    .toArray(String[]::new);
            long deletedCount = redissonClient.getKeys().delete(cacheKeys);
            log.info("用户所有 API Key 验证缓存已清除: userId={}, keyCount={}, deletedCount={}",
                    userId, keys.size(), deletedCount);
        } catch (Exception e) {
            log.error("清除用户 API Key 验证缓存失败: userId={}", userId, e);
        }
    }


    @Override
    public void updateById(SysApiKey apiKey) {
        apiKeyMapper.updateById(apiKey);
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 10) return "***";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
