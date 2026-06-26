package com.easyhome.mcp;

import com.alibaba.fastjson2.JSON;
import com.easyhome.entity.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 代理工具执行器
 * <p>
 * 将数据库中定义的 HTTP_PROXY 类型 MCP 工具包装为 ToolCallback。
 * 执行时：
 *   1. 读取工具定义中的 httpMethod、httpUrl、httpHeaders
 *   2. 将 AI 传入的参数填充到 URL 路径变量（{paramName}）和请求体/查询参数
 *   3. 发起 HTTP 请求并返回响应 JSON 给 AI
 * <p>
 * 支持的 HTTP 方法：GET、POST、PUT、DELETE
 * <p>
 * URL 模板语法：
 *   - 路径变量：/api/users/{userId}  → 参数 userId 的值会替换到 URL 中
 *   - 非路径变量参数：GET 时作为 QueryString，POST/PUT 时作为 JSON Body
 *
 * @author DW MCP Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpProxyToolExecutor {

    /** URL 路径变量正则：匹配 {paramName} */
    private static final Pattern PATH_VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    /** 默认请求超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 将 McpTool 定义包装为可执行的 ToolCallback
     */
    public ToolCallback buildCallback(McpTool tool) {
        String description = buildDescription(tool);

        String inputSchema = tool.getInputSchema() != null
                ? tool.getInputSchema()
                : "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

        return FunctionToolCallback
                .builder(tool.getToolName(), (Map<String, Object> params) -> executeHttp(tool, params))
                .description(description)
                .inputSchema(inputSchema)
                .inputType(Map.class)
                .build();
    }

    /**
     * 执行 HTTP 请求并返回 JSON 结果
     */
    private String executeHttp(McpTool tool, Map<String, Object> params) {
        String httpMethod = tool.getHttpMethod().toUpperCase();
        String urlTemplate = tool.getHttpUrl();
        String headersJson = tool.getHttpHeaders();

        try {
            // 1. 解析路径变量，将已使用的参数从剩余参数中移除
            Map<String, Object> remainingParams = new LinkedHashMap<>(params != null ? params : Map.of());
            String resolvedUrl = resolvePathVariables(urlTemplate, remainingParams);

            // 2. 构建请求
            HttpRequest request = buildHttpRequest(httpMethod, resolvedUrl, headersJson, remainingParams);

            log.info("执行MCP HTTP工具[{}], method={}, url={}, params={}",
                    tool.getToolName(), httpMethod, resolvedUrl, JSON.toJSONString(remainingParams));

            // 打印请求体（POST/PUT 时为 JSON Body）
            if ("POST".equals(httpMethod) || "PUT".equals(httpMethod)) {
                String body = remainingParams.isEmpty() ? "{}" : JSON.toJSONString(remainingParams);
                log.info("MCP HTTP工具[{}] 请求Body: {}", tool.getToolName(), body);
            }

            // 3. 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. 构建返回结果
            return buildSuccessResult(response);

        } catch (Exception e) {
            String rootCause = getRootCause(e);
            log.error("MCP HTTP工具[{}] 执行失败: {} | 根因: {}",
                    tool.getToolName(), e.getMessage(), rootCause, e);
            return serializeError("HTTP请求执行失败：" + rootCause);
        }
    }

    /**
     * 解析 URL 模板中的路径变量 {paramName}，替换为实际参数值
     * 已替换的参数从 remainingParams 中移除
     */
    private String resolvePathVariables(String urlTemplate, Map<String, Object> remainingParams) {
        if (urlTemplate == null) {
            throw new IllegalArgumentException("HTTP URL 不能为空");
        }

        Matcher matcher = PATH_VAR_PATTERN.matcher(urlTemplate);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = remainingParams.remove(paramName);
            String replacement = value != null
                    ? URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)
                    : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 构建 HttpRequest
     */
    private HttpRequest buildHttpRequest(String method, String url, String headersJson,
                                         Map<String, Object> params) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));

        // 解析并设置自定义请求头
        Map<String, String> customHeaders = parseHeaders(headersJson);
        customHeaders.forEach(builder::header);

        switch (method) {
            case "GET", "DELETE" -> {
                // GET/DELETE：参数作为 QueryString
                String fullUrl = appendQueryString(url, params);
                builder.uri(URI.create(fullUrl));
                if ("GET".equals(method)) {
                    builder.GET();
                } else {
                    builder.DELETE();
                }
            }
            case "POST", "PUT" -> {
                builder.uri(URI.create(url));
                // POST/PUT：参数作为 JSON Body
                String body = params.isEmpty() ? "{}" : JSON.toJSONString(params);
                if (!customHeaders.containsKey("Content-Type") && !customHeaders.containsKey("content-type")) {
                    builder.header("Content-Type", "application/json;charset=UTF-8");
                }
                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
                if ("POST".equals(method)) {
                    builder.POST(bodyPublisher);
                } else {
                    builder.PUT(bodyPublisher);
                }
            }
            default -> throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }

        return builder.build();
    }

    /**
     * 将参数拼接为 QueryString 追加到 URL 后
     */
    private String appendQueryString(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!qs.isEmpty()) qs.append("&");
            qs.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        if (qs.isEmpty()) return url;
        return url + (url.contains("?") ? "&" : "?") + qs;
    }

    /**
     * 解析请求头 JSON 为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headersJson == null || headersJson.isBlank()) {
            return headers;
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(headersJson, Map.class);
            if (parsed != null) {
                parsed.forEach((k, v) -> {
                    if (v != null) headers.put(k, v.toString());
                });
            }
        } catch (Exception e) {
            log.warn("解析HTTP请求头JSON失败: {}", e.getMessage());
        }
        return headers;
    }

    /**
     * 构建成功结果 JSON
     */
    private String buildSuccessResult(HttpResponse<String> response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
        result.put("statusCode", response.statusCode());

        // 尝试解析响应体为 JSON 对象
        String body = response.body();
        if (body != null && !body.isBlank()) {
            try {
                Object parsed = JSON.parse(body);
                result.put("data", parsed);
            } catch (Exception e) {
                // 非 JSON 格式，直接作为文本返回
                result.put("data", body);
            }
        } else {
            result.put("data", null);
        }

        return JSON.toJSONString(result);
    }

    /**
     * 构建工具描述
     */
    private String buildDescription(McpTool tool) {
        StringBuilder desc = new StringBuilder();
        desc.append("[").append(tool.getToolName()).append("]");
        if (tool.getDisplayName() != null && !tool.getDisplayName().isBlank()) {
            desc.append(" ").append(tool.getDisplayName());
        }
        if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
            desc.append(" - ").append(tool.getDescription());
        }
        return desc.toString();
    }

    /**
     * 获取异常根因消息
     */
    private String getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private String serializeError(String message) {
        return JSON.toJSONString(Map.of(
                "success", false,
                "error", message != null ? message : "Unknown error"
        ));
    }
}
