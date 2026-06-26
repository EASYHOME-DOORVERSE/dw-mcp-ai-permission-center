package com.easyhome.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.easyhome.entity.McpDatasource;
import com.easyhome.mcp.DatasourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 一键生成工具配置服务
 */
@Slf4j
@Service
public class AiToolGenerateService {

    private final Optional<ChatClient.Builder> chatClientBuilder;
    private final Optional<DatasourceRegistry> datasourceRegistry;

    /** OpenAI 兼容端点 Base URL，如 http://10.230.221.9:3000/v1 */
    @Value("${spring.ai.openai.base-url:}")
    private String aiBaseUrl;

    /** OpenAI 兼容 API Key */
    @Value("${spring.ai.openai.api-key:}")
    private String aiApiKey;

    /** 模型名称，如 qwen3.5-plus */
    @Value("${spring.ai.openai.chat.options.model:qwen3.5-plus}")
    private String aiModel;

    public AiToolGenerateService(Optional<ChatClient.Builder> chatClientBuilder,
                                  Optional<DatasourceRegistry> datasourceRegistry) {
        this.chatClientBuilder = chatClientBuilder;
        this.datasourceRegistry = datasourceRegistry;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{\\s*([A-Za-z0-9_]+)\\s*}");
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*([\\s\\S]+?)\\s*```");
    /** 用于识别可选条件块定界符，剥离时保留块内文字 */
    private static final Pattern OPTIONAL_BLOCK_DELIMITERS = Pattern.compile("\\[\\[|]]");
    /** 从 SQL 中提取 FROM/JOIN 后的表名（含 schema.table 格式） */
    private static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);

    // ================================================================
    // 同步接口（向后兼容）
    // ================================================================

    /**
     * 根据 SQL 模板调用大模型生成工具配置（同步阻塞）
     */
    public AiToolGenerateResponse generate(AiToolGenerateRequest request) {
        if (chatClientBuilder.isEmpty()) {
            throw new IllegalStateException("AI 服务未配置，请设置 OPENAI_API_KEY 环境变量");
        }

        String sql = request.getSqlTemplate().trim();

        // 查询表结构（可选）
        String schemaContext = fetchSchemaContext(request.getDatasourceKey(), sql);

        String prompt = buildPrompt(sql, schemaContext);

        String rawResponse;
        try {
            rawResponse = chatClientBuilder.get()
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 生成工具配置失败", e);
            throw new RuntimeException("AI 生成失败：" + e.getMessage(), e);
        }

        log.debug("AI 原始响应：{}", rawResponse);
        return parseResponse(rawResponse, sql);
    }

    // ================================================================
    // SSE 流式接口
    // ================================================================

    /**
     * 根据 SQL 模板流式生成工具配置，通过 SseEmitter 推送进度和结果。
     *
     * SSE 事件类型：
     *   thinking        - 模型输出文本片段（逐字）
     *   schema_fetching - 正在查询表结构
     *   result          - 最终结果 JSON（AiToolGenerateResponse）
     *   error           - 错误信息
     */
    public void generateStream(AiToolGenerateRequest request, SseEmitter emitter) {
        if (!StringUtils.hasText(aiBaseUrl) || !StringUtils.hasText(aiApiKey)) {
            sendSseText(emitter, "error", "AI 服务未配置，请设置 OPENAI_API_KEY / OPENAI_BASE_URL 环境变量");
            emitter.complete();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String sql = request.getSqlTemplate().trim();

                // 1. 查询表结构（可选）
                String schemaContext = "";
                if (StringUtils.hasText(request.getDatasourceKey())) {
                    sendSseJson(emitter, "schema_fetching",
                            JSON.toJSONString(extractTableNames(sql)));
                    schemaContext = fetchSchemaContext(request.getDatasourceKey(), sql);
                }

                // 2. 构建 prompt
                String prompt = buildPrompt(sql, schemaContext);

                // 3. 组装 OpenAI 兼容请求体（stream=true）
                JSONObject reqBody = new JSONObject();
                reqBody.put("model", aiModel);
                reqBody.put("stream", true);
                JSONArray messages = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.add(userMsg);
                reqBody.put("messages", messages);

                String url = aiBaseUrl.endsWith("/") ? aiBaseUrl + "chat/completions"
                        : aiBaseUrl + "/chat/completions";

                // 4. 用 HttpURLConnection 逐行读取 SSE 原始流
                //    分别提取 reasoning_content（思考过程）和 content（正文）
                StringBuilder fullContent = new StringBuilder();
                boolean hasThinking = false;

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + aiApiKey);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(300_000);

                try {
                    byte[] bodyBytes = reqBody.toJSONString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bodyBytes);
                    }

                    int httpCode = conn.getResponseCode();
                    if (httpCode != 200) {
                        String errBody;
                        try (BufferedReader er = new BufferedReader(
                                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                            errBody = er.lines().reduce("", (a, b) -> a + b);
                        } catch (Exception ex) {
                            errBody = "(empty)";
                        }
                        log.error("AI API 返回错误 HTTP {}: {}", httpCode, errBody);
                        sendSseText(emitter, "error", "AI 接口返回错误: HTTP " + httpCode);
                        emitter.complete();
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) break;
                            if (data.isEmpty()) continue;

                            JSONObject delta = null;
                            try {
                                JSONObject parsed = JSON.parseObject(data);
                                JSONArray choices = parsed.getJSONArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    delta = choices.getJSONObject(0).getJSONObject("delta");
                                }
                            } catch (Exception ex) {
                                log.debug("SSE 行解析跳过: {}", data);
                                continue;
                            }

                            if (delta == null) continue;

                            // reasoning_content：深度思考内容（qwen3.5-plus 独立字段），实时推送到思考面板
                            String reasoningChunk = delta.getString("reasoning_content");
                            if (StringUtils.hasText(reasoningChunk)) {
                                hasThinking = true;
                                sendSseText(emitter, "thinking", reasoningChunk);
                            }

                            // content：正文内容，仅累积，等流结束后解析为 result 事件
                            String contentChunk = delta.getString("content");
                            if (StringUtils.hasText(contentChunk)) {
                                fullContent.append(contentChunk);
                            }
                        }
                    }
                } finally {
                    conn.disconnect();
                }

                if (fullContent.isEmpty()) {
                    sendSseText(emitter, "error", "AI 返回内容为空");
                    emitter.complete();
                    return;
                }

                // 5. 若模型未输出 reasoning_content（非深度思考模式/其他模型），
                //    兼容 content 中包含 <think>...</think> 块的情况：提取并展示
                String raw = fullContent.toString();
                if (!hasThinking) {
                    int thinkStart = raw.indexOf("<think>");
                    int thinkEnd = raw.indexOf("</think>");
                    if (thinkStart >= 0 && thinkEnd > thinkStart) {
                        String thinkContent = raw.substring(thinkStart + "<think>".length(), thinkEnd).trim();
                        if (!thinkContent.isEmpty()) {
                            sendSseText(emitter, "thinking", thinkContent);
                        }
                    }
                }

                // 6. 剥离 <think> 块后解析 JSON，发送 result 事件
                String resultText = stripThinkBlock(raw);
                try {
                    AiToolGenerateResponse resp = parseResponse(resultText, sql);
                    sendSseJson(emitter, "result", JSON.toJSONString(resp));
                } catch (Exception parseEx) {
                    log.error("AI 响应解析失败，原始内容：{}", raw, parseEx);
                    sendSseText(emitter, "error", "AI 响应解析失败：" + parseEx.getMessage());
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE 生成流程异常", e);
                sendSseText(emitter, "error", "生成失败：" + e.getMessage());
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 剥离模型输出中的 &lt;think&gt;...&lt;/think&gt; 思考块，返回正文部分。
     * 若无 think 块则原样返回。
     */
    private String stripThinkBlock(String raw) {
        if (raw == null) return "";
        // 找到 </think> 结束标签，取其后的内容作为正文
        int endIdx = raw.indexOf("</think>");
        if (endIdx >= 0) {
            return raw.substring(endIdx + "</think>".length()).trim();
        }
        return raw.trim();
    }

    /**
     * 发送文本类 SSE 事件（thinking / error）。
     * SSE data: 行不能含原生换行符，将 \ 转义为 \\，\n 转义为 \n 字面量，\r 同理。
     * 前端收到后按对应规则还原即可得到原始文本。
     * 返回 true 表示发送成功，false 表示连接已关闭。
     */
    private boolean sendSseText(SseEmitter emitter, String eventName, String text) {
        try {
            String safe = text == null ? "" : text
                    .replace("\\", "\\\\")
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n")
                    .replace("\r", "\\n");
            emitter.send(SseEmitter.event().name(eventName).data(safe));
            return true;
        } catch (IOException e) {
            log.debug("SSE 连接已关闭，事件[{}]发送失败", eventName);
            return false;
        } catch (Exception e) {
            log.warn("SSE 事件[{}]发送异常：{}", eventName, e.getMessage());
            return false;
        }
    }

    /**
     * 发送 JSON 类 SSE 事件（result / schema_fetching）。
     * JSON 字符串本身不含裸露换行，直接发送，不做额外转义。
     */
    private void sendSseJson(SseEmitter emitter, String eventName, String json) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            log.debug("SSE 连接已关闭，JSON事件[{}]发送失败", eventName);
        } catch (Exception e) {
            log.warn("SSE JSON事件[{}]发送异常：{}", eventName, e.getMessage());
        }
    }

    // ================================================================
    // 表结构查询
    // ================================================================

    /**
     * 从 SQL 中提取表名，并通过指定数据源查询列结构，格式化为 prompt 上下文文字。
     * 任何异常均被捕获并忽略，返回空字符串。
     */
    private String fetchSchemaContext(String datasourceKey, String sql) {
        if (!StringUtils.hasText(datasourceKey) || datasourceRegistry.isEmpty()) {
            return "";
        }

        List<String> tableNames = extractTableNames(sql);
        if (tableNames.isEmpty()) {
            return "";
        }

        McpDatasource ds;
        try {
            ds = datasourceRegistry.get().getDatasource(datasourceKey);
        } catch (Exception e) {
            log.warn("查询表结构：数据源[{}]获取失败，跳过：{}", datasourceKey, e.getMessage());
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n以下是相关表的列结构信息，请优先用于生成准确的字段类型和描述：\n");

        try (Connection conn = datasourceRegistry.get().createConnection(ds)) {
            for (String tableName : tableNames) {
                String columnInfo = queryColumnInfo(conn, ds.getDbType(), tableName);
                if (columnInfo != null && !columnInfo.isBlank()) {
                    context.append("\n表 ").append(tableName).append(" 的列结构：\n");
                    context.append(columnInfo);
                }
            }
        } catch (Exception e) {
            log.warn("查询表结构失败，跳过：{}", e.getMessage());
            return "";
        }

        return context.toString();
    }

    /**
     * 从 SQL 中提取所有 FROM/JOIN 后的表名（去重）
     */
    private List<String> extractTableNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = TABLE_NAME_PATTERN.matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            // 过滤掉子查询括号、关键字等
            if (!name.isBlank() && !name.matches("(?i)(SELECT|WHERE|AND|OR|ON|SET|VALUES|INTO)")) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 根据数据库类型查询指定表的列信息，返回格式化文字（每列一行）。
     */
    private String queryColumnInfo(Connection conn, String dbType, String tableName) {
        String type = dbType != null ? dbType.toLowerCase() : "mysql";
        String querySql;
        try {
            // 分离 schema 和 table
            String schema = null;
            String table = tableName;
            if (tableName.contains(".")) {
                String[] parts = tableName.split("\\.", 2);
                schema = parts[0];
                table = parts[1];
            }

            querySql = switch (type) {
                case "mysql" -> schema != null
                        ? "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT"
                          + " FROM information_schema.COLUMNS"
                          + " WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_NAME='" + table + "'"
                          + " ORDER BY ORDINAL_POSITION"
                        : "SHOW COLUMNS FROM `" + table + "`";
                case "oracle" ->
                        "SELECT COLUMN_NAME, DATA_TYPE, NULLABLE, COMMENTS"
                        + " FROM USER_TAB_COLUMNS utc"
                        + " LEFT JOIN USER_COL_COMMENTS ucc USING (TABLE_NAME, COLUMN_NAME)"
                        + " WHERE utc.TABLE_NAME = '" + table.toUpperCase() + "'"
                        + " ORDER BY COLUMN_ID";
                case "sqlserver" ->
                        "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.IS_NULLABLE,"
                        + " CAST(ep.value AS NVARCHAR(256)) AS COLUMN_COMMENT"
                        + " FROM INFORMATION_SCHEMA.COLUMNS c"
                        + " LEFT JOIN sys.extended_properties ep"
                        + "   ON ep.major_id = OBJECT_ID(c.TABLE_NAME)"
                        + "   AND ep.minor_id = c.ORDINAL_POSITION"
                        + "   AND ep.name = 'MS_Description'"
                        + " WHERE c.TABLE_NAME = '" + table + "'"
                        + (schema != null ? " AND c.TABLE_SCHEMA = '" + schema + "'" : "")
                        + " ORDER BY c.ORDINAL_POSITION";
                // postgresql / hologres
                default -> {
                    String schemaClause = schema != null ? schema : "public";
                    yield "SELECT column_name, data_type, is_nullable"
                          + " FROM information_schema.columns"
                          + " WHERE table_schema='" + schemaClause + "'"
                          + " AND table_name='" + table + "'"
                          + " ORDER BY ordinal_position";
                }
            };

            StringBuilder sb = new StringBuilder();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(querySql)) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    String colName = rs.getString(1);
                    String dataType = rs.getString(2);
                    String nullable = colCount >= 3 ? rs.getString(3) : "";
                    String comment = colCount >= 4 ? rs.getString(4) : "";
                    sb.append("- ").append(colName)
                      .append(" (").append(dataType);
                    if (nullable != null && (nullable.equalsIgnoreCase("NO")
                            || nullable.equalsIgnoreCase("N"))) {
                        sb.append(", NOT NULL");
                    }
                    sb.append(")");
                    if (comment != null && !comment.isBlank()) {
                        sb.append(": ").append(comment);
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.warn("查询表[{}]列结构失败，跳过：{}", tableName, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Prompt 构建
    // ================================================================

    private String buildPrompt(String sql, String schemaContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位数据库工具配置专家。请根据以下 SQL 模板，生成一个 MCP 工具的配置参数。\n\n");
        prompt.append("SQL模板：\n").append(sql).append("\n");

        if (StringUtils.hasText(schemaContext)) {
            prompt.append(schemaContext).append("\n");
        }

        prompt.append("\n【核心规则 - 必须严格遵守】\n");
        prompt.append("表字段备注中的枚举/可选值说明必须完整保留到 Schema 的 description 字段中，禁止省略、缩减或改写。\n");
        prompt.append("示例：若字段备注为「状态 1=启用 2=停用 3=注销」，则对应 description 必须包含「状态，可选值：1=启用 2=停用 3=注销」。\n");
        prompt.append("示例：若字段备注为「性别(0-未知,1-男,2-女)」，则对应 description 必须包含「性别，可选值：0=未知 1=男 2=女」。\n");
        prompt.append("这条规则同时适用于 inputSchema 的 parameter description 和 outputSchema 的 field description，缺一不可。\n\n");
        prompt.append("要求：\n");
        prompt.append("1. toolName：生成一个简洁的英文工具名（只包含字母、数字、下划线、连字符，不支持中文），用于程序调用\n");
        prompt.append("2. displayName：生成一个简短的中文显示名称\n");
        prompt.append("3. description：生成详细的中文描述，说明工具用途、每个参数的含义和用法\n");
        prompt.append("4. inputSchema：根据 SQL 中的 #{paramName} 占位符，生成符合 JSON Schema Draft-07 格式的参数定义。\n");
        prompt.append("   - SQL 中 [[条件块]] 是可选条件语法，块内的 #{paramName} 参数为可选参数（AI 调用时可不传，不传则该条件自动省略）\n");
        prompt.append("   - 所有参数放在 properties 中\n");
        prompt.append("   - 根据参数名语义推断类型（如 id/user_id 用 integer，name/code 用 string，startTime 用 string + format:date-time 等）\n");
        prompt.append("   - 参数描述用中文，[[条件块]] 内的参数描述末尾加上「（可选，不传时忽略该条件）」\n");
        prompt.append("   - 【必须】若该参数对应的表字段备注包含枚举/可选值说明，description 中必须完整保留，格式：「字段含义，可选值：X=说明 Y=说明 ...」\n");
        prompt.append("   - [[条件块]] 内的参数不放入 required 数组\n");
        prompt.append("   - 包含 _pageNum（页码，从1开始）和 _pageSize（最大值1000，默认值1000）两个分页参数，放入 required 数组\n");
        prompt.append("   - inputSchema 的值是一个合法的 JSON 对象序列化后的字符串，所有双引号用 \\\" 转义，不要格式化换行\n");
        prompt.append("5. outputSchema：根据 SQL 查询结果列，生成符合 JSON Schema Draft-07 格式的输出结果定义。\n");
        prompt.append("   - 顶层结构固定为 object 类型，properties 中有且仅有一个 data 字段，data 为 array 类型\n");
        prompt.append("   - array 的 items 为 object 类型，items.properties 中描述每个返回列的名称、类型（string/integer/number/boolean）和中文描述\n");
        prompt.append("   - 正确示例：{\"type\":\"object\",\"properties\":{\"data\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"ID\"}}}}}}\n");
        if (StringUtils.hasText(schemaContext)) {
            prompt.append("   - 优先使用上方提供的表结构信息确定字段类型和描述\n");
            prompt.append("   - 【必须】若字段备注包含枚举/可选值说明，outputSchema 对应字段的 description 必须完整保留，格式：「字段含义，可选值：X=说明 Y=说明 ...」\n");
        }
        prompt.append("   - 若无法确定则根据 SQL 语义合理推断\n");
        prompt.append("   - outputSchema 的值是一个合法的 JSON 对象序列化后的字符串，所有双引号用 \\\" 转义，不要格式化换行\n");
        prompt.append("6. optimizedSqlTemplate：对原 SQL 模板进行优化和语法校验修复，返回优化后的 SQL 字符串。要求：\n");
        prompt.append("   - 保持原有的 #{paramName} 必填参数占位符和 [[条件块]] 可选条件语法完全不变\n");
        prompt.append("   - 修复明显的 SQL 语法错误（如缺少空格、错误的关键字、多余的逗号等）\n");
        prompt.append("   - 统一关键字大写（SELECT/FROM/WHERE/AND/OR/LIKE/ORDER BY/GROUP BY 等）\n");
        prompt.append("   - 优化可读性（适当对齐条件，每个主要子句换行）\n");
        prompt.append("   - 如果原 SQL 没有问题也无需重大优化，可原样返回\n");
        prompt.append("   - 此字段值为纯 SQL 文本字符串，换行用 \\n 表示，整体作为 JSON 字符串值返回\n\n");
        prompt.append("输出格式要求：\n");
        prompt.append("- 整体输出一个合法 JSON 对象，不要包含 markdown 代码块标记（```json）或任何解释文字\n");
        prompt.append("- inputSchema 和 outputSchema 字段的值必须是字符串类型（即先序列化为 JSON 字符串，再作为字段值），不要直接嵌套 JSON 对象\n");
        prompt.append("{\n");
        prompt.append("  \"toolName\": \"...\",\n");
        prompt.append("  \"displayName\": \"...\",\n");
        prompt.append("  \"description\": \"...\",\n");
        prompt.append("  \"inputSchema\": \"{\\\"type\\\":\\\"object\\\",...}\",\n");
        prompt.append("  \"outputSchema\": \"{\\\"type\\\":\\\"object\\\",...}\",\n");
        prompt.append("  \"optimizedSqlTemplate\": \"SELECT ...\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    // ================================================================
    // 响应解析
    // ================================================================

    /**
     * 解析 AI 返回的 JSON
     */
    private AiToolGenerateResponse parseResponse(String rawResponse, String sql) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new RuntimeException("AI 返回内容为空");
        }

        String jsonStr = rawResponse.trim();

        // 尝试提取 markdown 代码块
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(jsonStr);
        if (matcher.find()) {
            jsonStr = matcher.group(1).trim();
        }

        // 如果还有前缀/后缀垃圾文本，尝试提取第一个 { 到最后一个 }
        int firstBrace = jsonStr.indexOf('{');
        int lastBrace = jsonStr.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);
        }

        JSONObject json;
        try {
            json = JSON.parseObject(jsonStr);
        } catch (Exception e) {
            log.error("AI 返回内容 JSON 解析失败：{}", jsonStr);
            throw new RuntimeException("AI 返回内容格式异常，无法解析", e);
        }

        AiToolGenerateResponse resp = new AiToolGenerateResponse();
        resp.setToolName(json.getString("toolName"));
        resp.setDisplayName(json.getString("displayName"));
        resp.setDescription(json.getString("description"));
        resp.setInputSchema(json.getString("inputSchema"));
        resp.setOutputSchema(json.getString("outputSchema"));
        resp.setOptimizedSqlTemplate(json.getString("optimizedSqlTemplate"));

        // 后处理：清理 toolName，确保只含合法字符
        if (resp.getToolName() != null) {
            resp.setToolName(resp.getToolName().replaceAll("[^A-Za-z0-9_-]", ""));
        }

        // 后处理：如果 AI 没返回 inputSchema，尝试根据 SQL 中的占位符自动生成一个基础版
        if (resp.getInputSchema() == null || resp.getInputSchema().isBlank()) {
            resp.setInputSchema(buildDefaultInputSchema(sql));
        }

        return resp;
    }

    /**
     * 根据 SQL 中的 #{param} 占位符生成默认的 JSON Schema。
     * 支持 [[条件块]] 语法：剥除定界符后正常提取参数。
     */
    private String buildDefaultInputSchema(String sql) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();

        // 剥除 [[ 和 ]] 定界符（保留块内文字），确保块内参数能被正常提取
        String strippedSql = OPTIONAL_BLOCK_DELIMITERS.matcher(sql).replaceAll("");

        Matcher m = PLACEHOLDER_PATTERN.matcher(strippedSql);
        while (m.find()) {
            String param = m.group(1);
            if ("_pageNum".equals(param) || "_pageSize".equals(param)) {
                continue;
            }
            JSONObject prop = new JSONObject();
            prop.put("type", inferType(param));
            prop.put("description", param);
            properties.put(param, prop);
        }

        schema.put("properties", properties);
        return schema.toJSONString();
    }

    private String inferType(String param) {
        String lower = param.toLowerCase();
        if (lower.endsWith("id") || lower.endsWith("count") || lower.endsWith("num") || lower.endsWith("size")) {
            return "integer";
        }
        if (lower.contains("time") || lower.contains("date")) {
            return "string";
        }
        if (lower.contains("amount") || lower.contains("price") || lower.contains("rate")) {
            return "number";
        }
        if (lower.startsWith("is") || lower.startsWith("has") || lower.startsWith("enable")) {
            return "boolean";
        }
        return "string";
    }
}
