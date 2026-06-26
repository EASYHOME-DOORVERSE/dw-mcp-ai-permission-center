package com.easyhome.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.easyhome.security.ApiKeyAuthenticationToken;
import com.easyhome.security.JwtAuthenticationToken;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * INSERT 时：createdAt / updatedAt / status / isDeleted / creatorId / creator / modifierId / modifier
 * UPDATE 时：updatedAt / modifierId / modifier
 *
 * 操作人信息从 SecurityContextHolder 提取，兼容两种认证令牌：
 *   - JwtAuthenticationToken（管理端 JWT 登录）
 *   - ApiKeyAuthenticationToken（MCP 端 API Key 认证）
 *
 * 若未认证（系统初始化、定时任务等），返回 system 占位。
 */
@Component
public class MybatisPlusMetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt",  LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt",  LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "status",     Integer.class, 1);
        this.strictInsertFill(metaObject, "isDeleted",  Integer.class, 0);

        CurrentUser user = getCurrentUser();
        this.strictInsertFill(metaObject, "creatorId",  Long.class,   user.id());
        this.strictInsertFill(metaObject, "creator",    String.class, user.name());
        this.strictInsertFill(metaObject, "modifierId", Long.class,   user.id());
        this.strictInsertFill(metaObject, "modifier",   String.class, user.name());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt",  LocalDateTime.class, LocalDateTime.now());

        CurrentUser user = getCurrentUser();
        this.strictUpdateFill(metaObject, "modifierId", Long.class,   user.id());
        this.strictUpdateFill(metaObject, "modifier",   String.class, user.name());
    }

    /**
     * 从 SecurityContextHolder 获取当前登录用户
     *
     * 兼容 JWT（管理端）和 API Key（MCP 端）两种认证方式。
     * 若未认证（系统初始化、定时任务等），返回 system 占位。
     */
    private CurrentUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 管理端 JWT 认证
        if (auth instanceof JwtAuthenticationToken jwtToken) {
            return new CurrentUser(jwtToken.getUserId(), jwtToken.getUsername());
        }

        // MCP 端 API Key 认证
        if (auth instanceof ApiKeyAuthenticationToken apiKeyToken) {
            var u = apiKeyToken.getUser();
            String name = u.getNickname() != null ? u.getNickname() : u.getUsername();
            return new CurrentUser(u.getId(), name);
        }

        return new CurrentUser(0L, "system");
    }

    private record CurrentUser(Long id, String name) {}
}
