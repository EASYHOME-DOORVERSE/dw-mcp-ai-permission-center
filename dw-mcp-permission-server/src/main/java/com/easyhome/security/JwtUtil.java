package com.easyhome.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类
 *
 * 使用 HMAC-SHA256 对称密钥，支持多机共享同一 secret 实现无状态认证。
 * 多机部署时所有节点需配置相同的 jwt.secret。
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * 一次解析后的 Token 信息载体，避免重复解析 JWT
     */
    public record TokenInfo(Long userId, String username, List<String> roles, Date expiration) {}

    @Value("${jwt.secret:dw-mcp-permission-center-default-secret-key-change-in-production}")
    private String secret;

    @Value("${jwt.expiration-ms:7200000}")
    private long expirationMs;

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色编码列表（如 ["ADMIN"]）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT 字符串
     * @return true=有效, false=无效或过期
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 一次性验证并解析 Token，返回用户信息。
     * 比 validateToken + getUserId + getUsername + getRoles（4次解析）更高效，只解析 1 次。
     *
     * @param token JWT 字符串
     * @return Token 信息；如果 Token 无效或已过期返回 null
     */
    @SuppressWarnings("unchecked")
    public TokenInfo parseTokenInfo(String token) {
        try {
            Claims claims = parseClaims(token);
            return new TokenInfo(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("roles", List.class),
                    claims.getExpiration()
            );
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseClaims(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中提取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Claims claims = parseClaims(token);
        return claims.get("roles", List.class);
    }

    /**
     * 获取 Token 的剩余有效时间（毫秒），基于已解析的 expiration
     *
     * @param expiration Token 中的过期时间
     * @return 剩余毫秒数；如果已过期返回 0
     */
    public long getTokenRemainingMs(Date expiration) {
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * 获取 Token 的剩余有效时间（毫秒），需要解析 Token
     *
     * @return 剩余毫秒数；如果已过期返回 0
     */
    public long getTokenRemainingMs(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
