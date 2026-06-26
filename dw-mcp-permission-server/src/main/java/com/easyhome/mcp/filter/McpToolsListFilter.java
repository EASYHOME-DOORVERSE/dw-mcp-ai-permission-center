package com.easyhome.mcp.filter;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import com.easyhome.security.ApiKeyAuthenticationToken;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具列表过滤器 - 根据用户权限过滤 tools/list 响应结果
 * <p>
 * 采用请求阶段拦截方案：
 * - 在请求阶段判断是否为 tools/list
 * - 如果是，直接构建响应并返回，不经过后续处理
 * - 避免拦截 SSE 流式响应导致的性能问题
 * <p>
 * 权限数据直接从 ApiKeyAuthenticationToken 获取（路径隔离后 /mcp/** 只经过 ApiKeyAuthFilter）。
 *
 * @author DW MCP Team
 * @date 2026/5/27 18:38
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolsListFilter implements Filter {

    private final McpStatelessSyncServer mcpStatelessSyncServer;
    private final McpJsonMapper jsonMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 检查是否是 MCP 端点的 POST 请求
        if ("/mcp".equals(httpReq.getServletPath()) && "POST".equals(httpReq.getMethod())) {
            try {
                // 使用请求包装器缓存请求体
                RequestWrapper requestWrapper = new RequestWrapper(httpReq);
                String body = requestWrapper.getBody();
                
                // 解析 JSON-RPC 请求
                @SuppressWarnings("unchecked")
                Map<String, Object> rpcReq = jsonMapper.readValue(body, Map.class);
                String method = (String) rpcReq.get("method");
                
                // 如果是 tools/list 方法，直接拦截并返回
                if ("tools/list".equals(method)) {
                    log.info("拦截到 tools/list 请求，直接处理权限过滤");
                    handleToolsListRequest(httpResp, rpcReq);
                    return;
                }
                // 非 tools/list 请求，使用包装后的请求继续执行
                chain.doFilter(requestWrapper, response);
                return;
            } catch (Exception e) {
                log.error("处理 MCP 请求时发生错误，放行到后续处理", e);
            }
        }
        // 非 MCP 请求，正常执行
        chain.doFilter(request, response);
    }

    /**
     * 处理 tools/list 请求，直接返回过滤后的结果
     */
    private void handleToolsListRequest(HttpServletResponse response, Map<String, Object> rpcReq) throws IOException {
        // 从 SecurityContext 获取 API Key 认证用户的可访问工具集合
        // 路径隔离后 /mcp/** 只经过 ApiKeyAuthFilter，SecurityContext 中必为 ApiKeyAuthenticationToken
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof ApiKeyAuthenticationToken apiKeyAuth)) {
            log.warn("tools/list 请求缺少 API Key 认证信息，返回空列表");
            writeToolsResponse(response, rpcReq, List.of());
            return;
        }
        Set<String> accessibleToolNames = apiKeyAuth.getAccessibleToolNames();
        
        // 从 MCP Server 获取所有已注册的工具
        List<McpSchema.Tool> allTools = mcpStatelessSyncServer.listTools();
        
        // 过滤工具列表
        List<McpSchema.Tool> filteredTools = allTools.stream()
                .filter(tool -> accessibleToolNames.contains(tool.name()))
                .toList();
        
        log.info("tools/list 过滤完成，返回 {}/{} 个工具", filteredTools.size(), allTools.size());
        
        // 构建并返回响应
        writeToolsResponse(response, rpcReq, filteredTools);
    }

    /**
     * 写入工具列表响应
     */
    private void writeToolsResponse(HttpServletResponse response, Map<String, Object> rpcReq, 
                                    List<McpSchema.Tool> tools) throws IOException {
        // 使用 Spring AI MCP 框架的对象构建响应
        McpSchema.ListToolsResult listToolsResult = McpSchema.ListToolsResult.builder(tools).build();
        
        // 构建 JSON-RPC 响应
        McpSchema.JSONRPCResponse jsonResponse = new McpSchema.JSONRPCResponse(
                McpSchema.JSONRPC_VERSION,
                rpcReq.get("id"),
                listToolsResult,
                null
        );
        
        // 使用 JacksonMcpJsonMapper 序列化（自动处理 UTF-8 编码）
        String responseJson = jsonMapper.writeValueAsString(jsonResponse);
        
        // 设置响应头和编码
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        // 写入响应
        response.getWriter().write(responseJson);
        response.getWriter().flush();
        
        log.info("tools/list 响应完成，返回 {} 个工具", tools.size());
    }
}
