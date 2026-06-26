package com.easyhome.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT Token 黑名单服务
 * <p>
 * 基于 Redis 实现的 Token 黑名单机制，支持在退出登录、修改密码等场景下使 Token 立即失效。
 * <p>
 * 存储结构：Key = jwt:blacklist:{token}, Value = "1", TTL = Token 剩余有效期
 * <p>
 * 多实例部署时，所有节点共享同一 Redis，黑名单自动生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final RedissonClient redissonClient;

    /**
     * 将 Token 加入黑名单
     *
     * @param token        JWT Token 字符串
     * @param remainingMs  Token 剩余有效时间（毫秒），TTL 设为此值，过期后自动清理
     */
    public void blacklist(String token, long remainingMs) {
        if (remainingMs <= 0) {
            log.debug("Token 已过期，无需加入黑名单");
            return;
        }
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + token);
        bucket.set("1", remainingMs, TimeUnit.MILLISECONDS);
        log.info("Token 已加入黑名单，TTL={}ms", remainingMs);
    }

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT Token 字符串
     * @return true=已被拉黑（应拒绝），false=不在黑名单（可正常使用）
     */
    public boolean isBlacklisted(String token) {
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + token);
        return bucket.isExists();
    }
}
