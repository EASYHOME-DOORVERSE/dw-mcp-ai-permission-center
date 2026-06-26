package com.easyhome.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * JWT 认证令牌
 *
 * 管理端登录成功后存入 SecurityContext，携带用户 ID、用户名和角色。
 * 与 {@link ApiKeyAuthenticationToken} 并行存在，互不干扰：
 *   - JwtAuthenticationToken → 管理端 /api/**
 *   - ApiKeyAuthenticationToken → MCP 端 /mcp/**
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    @Getter
    private final Long userId;
    @Getter
    private final String username;
    @Getter
    private final String token;
    @Getter
    private final Date expiration;

    public JwtAuthenticationToken(Long userId, String username, String token, Date expiration, List<String> roles) {
        super(buildAuthorities(roles));
        this.userId = userId;
        this.username = username;
        this.token = token;
        this.expiration = expiration;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    private static Collection<GrantedAuthority> buildAuthorities(List<String> roles) {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
