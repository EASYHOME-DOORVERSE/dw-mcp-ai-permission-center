package com.easyhome.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 分布式锁配置
 * <p>
 * 使用单机模式连接 Redis，生产环境可切换为哨兵/集群模式。
 * 多机部署时所有节点连接同一个 Redis 实例即可实现分布式锁。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.username:}")
    private String username;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.setUsername(StringUtils.hasLength(username) ? username : null);
        config.setPassword(StringUtils.hasLength(password) ? password : null);
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4);
        return Redisson.create(config);
    }
}
