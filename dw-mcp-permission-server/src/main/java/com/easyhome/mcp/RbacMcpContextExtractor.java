package com.easyhome.mcp;

import com.easyhome.security.ApiKeyAuthenticationToken;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 传输上下文提取器
 * <p>
 * 从 Spring SecurityContext 中提取当前用户的认证信息，
 * 注入到 MCP TransportContext，使得 tools/list 和 tools/call
 * 的处理器可以获取当前用户身份进行 RBAC 过滤。
 */
@Component
public class RbacMcpContextExtractor implements McpTransportContextExtractor<ServerRequest> {

    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    public static final String ACCESSIBLE_TOOL_NAMES = "accessibleToolNames";
    public static final String ACCOUNT_ID = "accountId";

    @Override
    public McpTransportContext extract(ServerRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken token) {
            Map<String, Object> context = new HashMap<>();
            context.put(USER_ID, token.getUser().getId());
            context.put(USERNAME, token.getUser().getUsername());
            context.put(ACCESSIBLE_TOOL_NAMES, token.getAccessibleToolNames());
            context.put(ACCOUNT_ID, token.getAccountId());
            return McpTransportContext.create(context);
        }
        return McpTransportContext.EMPTY;
    }
}
