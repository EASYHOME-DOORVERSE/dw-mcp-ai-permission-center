package com.easyhome.mcp;

import com.alibaba.fastjson2.JSON;
import com.easyhome.entity.McpDatasource;
import com.easyhome.entity.McpTool;
import com.easyhome.security.ApiKeyAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDBC 工具执行器
 *
 * 将数据库中定义的 JDBC 类型 MCP 工具包装为 ToolCallback。
 * 执行时：
 *   1. 从 DatasourceRegistry 获取数据源连接信息，创建原生 JDBC 连接
 *   2. 将 AI 传入的参数替换到 SQL 模板的 #{param} 占位符
 *   3. 通过 PreparedStatement 执行 SQL
 *   4. 将结果集序列化为 JSON 字符串返回给 AI
 *   5. 连接在 finally 中关闭，不使用连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcToolExecutor {

    private final DatasourceRegistry datasourceRegistry;

    /**
     * MCP 工具名前缀，所有工具名格式为 tool_{id}
     */
    public static final String TOOL_NAME_PREFIX = "tool_";

    /**
     * 将 McpTool 定义包装为可执行的 ToolCallback
     */
    public ToolCallback buildCallback(McpTool tool) {
        String mcpToolName = TOOL_NAME_PREFIX + tool.getId();

        String description = buildDescription(tool);

        String inputSchema = tool.getInputSchema() != null
                ? tool.getInputSchema()
                : "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

        return FunctionToolCallback
                .builder(mcpToolName, (Map<String, Object> params) -> executeSql(tool, params))
                .description(description)
                .inputSchema(inputSchema)
                .inputType(Map.class)
                .build();
    }

    /**
     * 构建工具描述，包含原始中文名和自定义描述
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
     * 执行 SQL 并返回 JSON 格式结果。
     *
     * 自动分页规则：
     *   1. SQL 无分页语句 → 根据数据源 dbType 自动追加 LIMIT/OFFSET，默认每页 1000 行
     *   2. SQL 已有分页但单页行数超过 1000 → 替换为默认值 1000
     *   3. 同时执行 COUNT 查询返回总记录数
     *
     * 每次执行创建新的 JDBC 连接，执行完毕后立即关闭，不使用连接池。
     */
    private String executeSql(McpTool tool, Map<String, Object> params) {
        String datasourceKey = tool.getDatasourceKey();
        String sqlTemplate = tool.getSqlTemplate();

        try {
            // 自动注入 accountId 等系统参数
            Map<String, Object> enrichedParams = enrichParams(params, tool);
            
            // 处理预查询参数（执行子查询，并把 #{__query_xxx:SQL}) 替换为字面量列表）
            QueryResolveResult queryResolved = resolveQueryParams(sqlTemplate, enrichedParams, tool);
            
            // 解析 SQL 模板参数（基于已剔除 __query 块的 SQL）
            SqlWithParams sqlWithParams = resolveSqlTemplate(queryResolved.sql(), queryResolved.params());
            String originalSql = sqlWithParams.sql();

            // 获取数据源元数据，用于判断数据库类型
            McpDatasource ds = datasourceRegistry.getDatasource(datasourceKey);
            String dbType = ds.getDbType();

            // 从参数中提取分页参数（AI 可传入 _pageNum / _pageSize）
            int pageNum = getIntParam(enrichedParams, "_pageNum", SqlPaginationUtil.DEFAULT_PAGE_NUM);
            int pageSize = getIntParam(enrichedParams, "_pageSize", SqlPaginationUtil.MAX_PAGE_SIZE);

            // 自动添加或替换分页
            SqlPaginationUtil.PaginationResult paginationResult =
                    SqlPaginationUtil.applyPagination(originalSql, dbType, pageNum, pageSize);
            String pagedSql = paginationResult.sql();

            if (paginationResult.pageSizeCapped()) {
                log.warn("MCP工具[{}] SQL分页行数超过上限({}), 已自动替换为默认值",
                        tool.getToolName(), SqlPaginationUtil.MAX_PAGE_SIZE);
            }

            // 生成 COUNT SQL
            String countSql = SqlPaginationUtil.buildCountSql(originalSql, dbType);

            log.info("执行MCP工具[{}], 数据源[{}], 参数: {}, 分页SQL: {}, COUNT SQL: {}",
                    tool.getToolName(), datasourceKey,
                    JSON.toJSONString(sqlWithParams.args()),
                    compactSql(pagedSql), compactSql(countSql));

            try (Connection conn = datasourceRegistry.createConnection(ds)) {
                // 1. 查询总记录数
                long total = 0;
                try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                    for (int i = 0; i < sqlWithParams.args().length; i++) {
                        countStmt.setObject(i + 1, sqlWithParams.args()[i]);
                    }
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) {
                            total = rs.getLong(1);
                        }
                    }
                }

                // 2. 执行分页查询
                try (PreparedStatement pstmt = conn.prepareStatement(pagedSql)) {
                    pstmt.setQueryTimeout(120);
                    for (int i = 0; i < sqlWithParams.args().length; i++) {
                        pstmt.setObject(i + 1, sqlWithParams.args()[i]);
                    }
                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<Map<String, Object>> rows = toRowList(rs);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("success", true);
                        result.put("total", total);
                        result.put("pageNum", paginationResult.pageNum());
                        result.put("pageSize", paginationResult.pageSize());
                        result.put("rowCount", rows.size());
                        result.put("data", rows);
                        if (paginationResult.pageSizeCapped()) {
                            result.put("warning", "SQL分页行数超过上限("
                                    + SqlPaginationUtil.MAX_PAGE_SIZE + "), 已自动替换为默认值");
                        }
                        return JSON.toJSONString(result);
                    }
                }
            }
        } catch (Exception e) {
            String rootCause = getRootCause(e);
            log.error("MCP工具[{}] 数据源[{}] 执行失败: {} | 根因: {}",
                    tool.getToolName(), datasourceKey, e.getMessage(), rootCause, e);
            return serializeError(
                    "数据源[" + datasourceKey + "]调用失败：" + rootCause);
        }
    }

    /**
     * 压缩 SQL：去除换行符和多余空格，保持单行显示
     */
    private static String compactSql(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * 从参数 Map 中获取整数值
     */
    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    /**
     * 自动注入系统参数（如 accountId）到参数 Map 中
     */
    private Map<String, Object> enrichParams(Map<String, Object> params, McpTool tool) {
        Map<String, Object> enriched = new HashMap<>(params);
        
        // 从 SecurityContext 中获取当前用户的 accountId
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken token) {
            String accountId = token.getAccountId();
            if (accountId != null && !accountId.isBlank()) {
                enriched.put("accountId", accountId);
                log.debug("自动注入accountId参数: {}", accountId);
            }
        }
        
        return enriched;
    }

    /**
     * 预查询参数正则：匹配 《列表达式 IN (#{__query_paramName:SQL})》整个片段
     * 以便在子查询返回空时能把整个 IN 谓词改写为 1=1 避免语法或均衡失反。
     * - group(1)：列名表达式，如 ATX_T_1_."ac_code"
     * - group(2)：参数名
     * - group(3)：子查询 SQL
     * 示例：ATX_T_1_."ac_code" IN (#{__query_marketIds:SELECT id FROM t WHERE x=1})
     */
    private static final Pattern QUERY_PARAM_PATTERN =
            Pattern.compile("([^\\s(),]+)\\s+IN\\s*\\(\\s*#\\{__query_(\\w+):(.+?)\\}\\s*\\)");

    /**
     * 处理 SQL 模板中的预查询参数。
     * 1. 匹配 #{__query_paramName:SQL}) 语法（注意 QUERY_PARAM_PATTERN 末尾包含 IN(...) 的右括号）
     * 2. 执行子查询拿到值列表
     * 3. 把整个 #{__query_xxx:SQL}) 片段替换为 'v1','v2',...)
     *    若值列表为空，则替换为 NULL) 以保证 IN 表达式语法合法且不匹配任何行
     * 4. 同时把值列表写入 resolved Map（便于日志/调试）
     */
    private QueryResolveResult resolveQueryParams(String sqlTemplate, Map<String, Object> params, McpTool tool) {
        Map<String, Object> resolved = new HashMap<>(params);
        if (sqlTemplate == null) {
            return new QueryResolveResult("", resolved);
        }

        Matcher matcher = QUERY_PARAM_PATTERN.matcher(sqlTemplate);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String column = matcher.group(1);
            String paramName = matcher.group(2);
            String querySql = matcher.group(3);

            // 执行子查询获取参数值
            List<Object> values = executeQueryParamQuery(querySql, resolved, tool);

            String replacement;
            if (values != null && !values.isEmpty()) {
                resolved.put(paramName, values);
                log.debug("预查询参数[{}] = {}", paramName, values);
                // 重建为：<col> IN ('v1','v2',...)
                replacement = column + " IN (" + buildInListLiteral(values) + ")";
            } else {
                log.debug("预查询参数[{}]为空，整个 IN 谓词改写为恒真 1=1", paramName);
                // 空值：整个 IN 谓词改为 1=1，与外层 AND 连接后不影响其他条件
                replacement = "1=1";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return new QueryResolveResult(sb.toString(), resolved);
    }

    /**
     * 把子查询结果列表渲染成 IN 子句字面量（不含外层括号）。
     * - 数字类型不加引号
     * - 其它类型按字符串处理，单引号转义为两个单引号
     */
    private String buildInListLiteral(List<Object> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            Object v = values.get(i);
            if (v instanceof Number) {
                sb.append(v);
            } else {
                String s = v.toString().replace("'", "''");
                sb.append("'").append(s).append("'");
            }
        }
        return sb.toString();
    }

    /**
     * 预查询解析结果：替换后的 SQL 模板 + 已扩充参数 Map
     */
    private record QueryResolveResult(String sql, Map<String, Object> params) {}

    /**
     * 执行子查询获取参数值
     */
    private List<Object> executeQueryParamQuery(String querySql, Map<String, Object> params, McpTool tool) {
        String datasourceKey = tool.getDatasourceKey();
        if (datasourceKey == null) {
            return null;
        }
        
        try {
            McpDatasource ds = datasourceRegistry.getDatasource(datasourceKey);
            
            // 替换 SQL 中的已有参数（如 #{accountId}）
            String finalSql = querySql;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    finalSql = finalSql.replace("#{" + key + "}", "'" + value + "'");
                } else if (value != null) {
                    finalSql = finalSql.replace("#{" + key + "}", value.toString());
                }
            }
            
            try (Connection conn = datasourceRegistry.createConnection(ds)) {
                try (PreparedStatement pstmt = conn.prepareStatement(finalSql)) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<Object> values = new ArrayList<>();
                        while (rs.next()) {
                            // 只取第一列
                            Object value = rs.getObject(1);
                            if (value != null) {
                                values.add(value);
                            }
                        }
                        return values;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("执行预查询失败: {}, SQL: {}", e.getMessage(), querySql);
            return null;
        }
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

    /**
     * 将 ResultSet 转为 List<Map>，保留列顺序
     */
    private List<Map<String, Object>> toRowList(ResultSet rs) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 可选条件块正则：匹配 [[...]] 包裹的片段，支持跨行，非贪婪
     */
    private static final Pattern OPTIONAL_BLOCK_PATTERN =
            Pattern.compile("\\[\\[([\\s\\S]+?)]]");

    /**
     * 参数占位符正则：匹配 #{paramName}
     */
    private static final Pattern PARAM_NAME_PATTERN =
            Pattern.compile("#\\{(\\w+)}");

    /**
     * 将 SQL 模板中的 #{paramName} 按出现顺序替换为 ?，并收集对应参数值。
     * 使用 PreparedStatement 参数绑定，彻底防止 SQL 注入。
     *
     * 支持可选条件块语法 [[条件片段]]：
     *   - 块内所有 #{param} 参数均缺失（null 或空字符串）→ 移除整块
     *   - 块内至少一个参数有值 → 保留块内文字（去掉 [[ ]] 定界符）
     *   - 块内无任何 #{param}（裸常量条件）→ 始终保留
     */
    private SqlWithParams resolveSqlTemplate(String sqlTemplate, Map<String, Object> params) {
        if (sqlTemplate == null) {
            return new SqlWithParams("", new Object[0]);
        }

        // ── 阶段一：处理 [[条件块]] ──────────────────────────────────────
        Matcher blockMatcher = OPTIONAL_BLOCK_PATTERN.matcher(sqlTemplate);
        StringBuffer sb1 = new StringBuffer();
        while (blockMatcher.find()) {
            String blockContent = blockMatcher.group(1);
            // 扫描块内所有参数名
            List<String> paramNames = new ArrayList<>();
            Matcher paramMatcher = PARAM_NAME_PATTERN.matcher(blockContent);
            while (paramMatcher.find()) {
                paramNames.add(paramMatcher.group(1));
            }
            // 判断是否保留：无占位符（裸常量块）或至少一个参数有值则保留
            boolean keep = paramNames.isEmpty()
                    || paramNames.stream().anyMatch(name -> isValuePresent(params, name));
            if (keep) {
                // 去掉 [[ ]] 定界符，保留内容
                blockMatcher.appendReplacement(sb1, Matcher.quoteReplacement(blockContent));
            } else {
                // 移除整块
                blockMatcher.appendReplacement(sb1, "");
            }
        }
        blockMatcher.appendTail(sb1);
        String preprocessedSql = sb1.toString();

        // 检查是否有嵌套的 [[ 或残留的 ]]，防止误用
        if (preprocessedSql.contains("[[") || preprocessedSql.contains("]]")) {
            throw new IllegalArgumentException(
                    "SQL模板含有不合法的[[...]]语法（可能存在嵌套），请检查SQL模板格式");
        }

        // ── 阶段二：#{param} → ? 替换（现有逻辑）────────────────────────
        List<Object> args = new ArrayList<>();
        Matcher paramReplaceMatcher = PARAM_NAME_PATTERN.matcher(preprocessedSql);
        StringBuffer sb2 = new StringBuffer();
        while (paramReplaceMatcher.find()) {
            String paramName = paramReplaceMatcher.group(1);
            args.add(params.get(paramName));  // null 由 JDBC 处理为 SQL NULL
            paramReplaceMatcher.appendReplacement(sb2, "?");
        }
        paramReplaceMatcher.appendTail(sb2);
        return new SqlWithParams(sb2.toString(), args.toArray());
    }

    /**
     * 判断参数是否存在有效值（非 null 且非空字符串）
     */
    private boolean isValuePresent(Map<String, Object> params, String name) {
        if (!params.containsKey(name)) return false;
        Object val = params.get(name);
        if (val == null) return false;
        if (val instanceof String s) return !s.isBlank();
        return true;
    }

    private record SqlWithParams(String sql, Object[] args) {}

    private String serializeError(String message) {
        return JSON.toJSONString(Map.of(
                "success", false,
                "error", message != null ? message : "Unknown error"
        ));
    }
}
