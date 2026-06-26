package com.easyhome.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * <p>
 * 仅处理管理端 /api/** 路径，MCP 端 /mcp/** 不经过此 Filter。
 * 与 {@link ApiKeyAuthFilter} 完全路径隔离，互不干扰。
 * <p>
 * 认证协议：
 * HTTP Header  Authorization: Bearer <jwt-token>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final JwtBlacklistService jwtBlacklistService;

    /**
     * 只处理 /api/** 路径，跳过 /mcp/** 等其他路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api");
    }

    /**
     * 父路隔离后 /api/** 財期没有异步 dispatch 场景，不再需要过滤 async dispatch。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        // 已有认证信息，跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        // 一次性验证并解析 JWT（仅解析 1 次，避免 validateToken + getUserId/Username/Roles 重复解析）
        JwtUtil.TokenInfo tokenInfo = jwtUtil.parseTokenInfo(token);
        if (tokenInfo == null) {
            // JWT 无效或已过期，放行（由后续 Security 授权层决定是否拒绝；
            // 如 /api/auth/login 等公开接口无需认证，不应在此拦截）
            filterChain.doFilter(request, response);
            return;
        }

        // 检查 Token 是否已被加入黑名单（退出登录/修改密码等场景）
        if (jwtBlacklistService.isBlacklisted(token)) {
            log.debug("JWT Token 已被加入黑名单，拒绝访问");
            filterChain.doFilter(request, response);
            return;
        }

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(tokenInfo.userId(), tokenInfo.username(), token, tokenInfo.expiration(), tokenInfo.roles());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("JWT 认证成功，用户: {} (ID: {}), 角色: {}", tokenInfo.username(), tokenInfo.userId(), tokenInfo.roles());

        filterChain.doFilter(request, response);
    }
}
