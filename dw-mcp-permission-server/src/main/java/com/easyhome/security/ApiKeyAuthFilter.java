package com.easyhome.security;

import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysUser;
import com.easyhome.entity.UserPermissionCache;
import com.easyhome.service.SysApiKeyService;
import com.easyhome.service.SysApiKeyService.ApiKeyValidationResult;
import com.easyhome.service.UserPermissionCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * API Key 认证过滤器
 * <p>
 * 仅处理 MCP 端 /mcp/** 路径，管理端 /api/** 不经过此 Filter。
 * 与 {@link JwtAuthFilter} 完全路径隔离，互不干扰。
 * <p>
 * 认证协议：
 * 客户端在 HTTP 请求头中携带：
 * Authorization: Bearer sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * <p>
 * 流程：
 * 1. 提取 Authorization 头中的 Bearer Token
 * 2. 调用 SysApiKeyService 校验 Key 并获取对应用户
 * 3. 查询用户角色及可访问工具列表（带缓存），构建认证令牌
 * 4. 构建 ApiKeyAuthenticationToken 存入 SecurityContext
 * 5. 如果 Key 无效，直接返回 401
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SysApiKeyService apiKeyService;
    private final UserPermissionCacheService userPermissionCacheService;

    /**
     * 只处理 /mcp/** 路径，跳过 /api/** 等其他路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/mcp");
    }

    /**
     * 默认 OncePerRequestFilter 在 ASYNC dispatch 阶段不会再次执行，
     * 而本项目 STATELESS 且使用 Spring AI MCP 异步响应（SSE/DeferredResult），
     * 需要在 async dispatch 阶段重新从 Authorization 头重建 SecurityContext，
     * 否则 AuthorizationFilter 会报 Access Denied。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        // 如果当前请求已有认证信息，跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 没有携带 Authorization 头，直接放行（由后续 Security 配置决定是否拒绝）
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();

        // 校验 Key，获取用户 + accountId（一次查询完成）
        ApiKeyValidationResult validationResult = apiKeyService.validateAndGetUser(apiKey);
        if (validationResult == null) {
            log.debug("API Key认证失败，请求路径: {}", request.getRequestURI());
            sendUnauthorized(response, "Invalid or expired API Key");
            return;
        }

        SysUser user = validationResult.user();

        // 查询用户角色及工具列表（带缓存），构建认证令牌
        UserPermissionCache userPermission = userPermissionCacheService.getUserPermissionCache(user.getId());
        if (CollectionUtils.isEmpty(userPermission.getAccessibleToolNames())) {
            log.debug("用户可访问的 MCP 工具列表为空, 请求路径: {}", request.getRequestURI());
            sendUnauthorized(response, "User has no accessible MCP tools");
            return;
        }

        String accountId = validationResult.accountId();
        ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(user, apiKey, userPermission, accountId);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("API Key认证成功，用户: {}", user.getUsername());

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":401,"message":"%s"}
                """.formatted(message));
    }
}
