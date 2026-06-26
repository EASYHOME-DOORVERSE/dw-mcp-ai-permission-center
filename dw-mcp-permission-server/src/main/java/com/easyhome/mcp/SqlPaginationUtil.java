package com.easyhome.mcp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 分页工具
 *
 * 负责检测 SQL 中是否已有分页语句，并根据数据库类型自动添加或替换分页。
 * 规则：
 *   1. SQL 中无分页语句 → 自动追加 LIMIT/OFFSET（默认每页 1000 行）
 *   2. SQL 已有分页但单页行数超过 1000 → 替换为默认值 1000
 */
public final class SqlPaginationUtil {

    /** 单页最大行数 */
    public static final int MAX_PAGE_SIZE = 1000;
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    private SqlPaginationUtil() {}

    // ================================================================
    // 字面量 / 注释剥离（供检测与替换共用）
    // ================================================================

    /**
     * 剥离 SQL 中的字符串字面量（单/双引号）以及单行/块注释，
     * 防止其中出现的分页关键字被误判。
     * 剥离时用单个空格替换，保留 token 边界。
     *
     * 支持的语法：
     *   - 单行注释 -- ... 行尾
     *   - 块注释 /* ... *&#47;
     *   - 单引号字符串 '...'（含转义 ''）
     *   - 双引号标识符 "..."（含转义 ""）
     *   - 美元符号字符串 $$...$$ / $tag$...$tag$（PostgreSQL/Hologres）
     */
    private static final Pattern STRIP_LITERALS = Pattern.compile(
            "--[^\\n]*"                                // 单行注释
            + "|/\\*[\\s\\S]*?\\*/"                    // 块注释
            + "|'(?:''|[^'])*'"                        // 单引号字符串
            + "|\"(?:\"\"|[^\"])*\""                   // 双引号标识符
            + "|\\$\\$[\\s\\S]*?\\$\\$"               // PostgreSQL $$...$$ 字符串（内容可含 $）
            + "|\\$[A-Za-z_]\\w*\\$[\\s\\S]*?\\$[A-Za-z_]\\w*\\$"  // PostgreSQL $tag$...$tag$
    );

    /**
     * 剥离 SQL 中的字面量和注释，返回仅含结构关键字的"骨架"。
     */
    static String stripLiteralsAndComments(String sql) {
        return STRIP_LITERALS.matcher(sql).replaceAll(" ");
    }

    // ================================================================
    // SQL 预处理：分号剥离 & 锁定子句剥离
    // ================================================================

    /**
     * 剥离 SQL 末尾的分号，返回 [无分号SQL, 分号后缀]。
     * 分页子句不能追加在分号之后，必须插在分号之前。
     */
    private static String[] stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return new String[]{trimmed.substring(0, trimmed.length() - 1).trim(), ";"};
        }
        return new String[]{trimmed, ""};
    }

    /**
     * 检测 SQL 末尾的锁定子句（FOR UPDATE / FOR SHARE / LOCK IN SHARE MODE 等）。
     * 分页子句必须放在锁定子句之前，因此需要在处理前先剥离、处理后再拼接。
     *
     * 由于 FOR UPDATE 是 SELECT 语句的最后子句，使用 .* 简化匹配，
     * 自动覆盖 OF 多列、NOWAIT、SKIP LOCKED、WAIT n 等所有变体。
     */
    private static final Pattern TRAILING_LOCK_PATTERN =
            Pattern.compile("(?i)\\s+(FOR\\s+UPDATE\\b.*|FOR\\s+SHARE\\b.*|LOCK\\s+IN\\s+SHARE\\s+MODE\\b.*)\\s*$");

    /**
     * 剥离 SQL 末尾的锁定子句，返回 [核心SQL, 锁定后缀]。
     * 若无锁定子句，后缀为空字符串。
     * 注意：输入 sql 应为已 trim 的字符串。
     */
    private static String[] stripTrailingLock(String sql) {
        Matcher m = TRAILING_LOCK_PATTERN.matcher(sql);
        if (m.find()) {
            String lockSuffix = " " + m.group(1).trim();
            String coreSql = sql.substring(0, m.start()).trim();
            return new String[]{coreSql, lockSuffix};
        }
        return new String[]{sql, ""};
    }

    /**
     * 完整预处理：剥离分号 → 剥离锁定后缀，返回预处理结果。
     * 按正确顺序：先剥离分号，再剥离锁定（因为锁定子句在分号之前）。
     */
    private static PreprocessResult preprocess(String sql) {
        // 1. 剥离末尾分号
        String[] semiParts = stripTrailingSemicolon(sql);
        String withoutSemi = semiParts[0];
        String semicolon = semiParts[1];

        // 2. 剥离锁定后缀（在无分号的 SQL 上操作）
        String[] lockParts = stripTrailingLock(withoutSemi);
        String coreSql = lockParts[0];
        String lockSuffix = lockParts[1];

        return new PreprocessResult(coreSql, lockSuffix, semicolon);
    }

    /** 预处理结果 */
    private record PreprocessResult(String coreSql, String lockSuffix, String semicolon) {
        /** 拼接完整的后缀（锁定 + 分号） */
        String fullSuffix() {
            return lockSuffix + semicolon;
        }
    }

    // ================================================================
    // 分页检测：判断 SQL 是否已包含分页子句
    // ================================================================

    // 关键字检测正则（预编译，避免每次调用重新编译）
    private static final Pattern KW_LIMIT  = Pattern.compile("\\bLIMIT\\b");
    private static final Pattern KW_FETCH  = Pattern.compile("\\bFETCH\\b");
    private static final Pattern KW_NEXT   = Pattern.compile("\\bNEXT\\b");
    private static final Pattern KW_ROWNUM = Pattern.compile("\\bROWNUM\\b");
    private static final Pattern KW_OFFSET = Pattern.compile("\\bOFFSET\\b");

    /**
     * 检测 SQL 中是否包含有效的分页子句。
     * 支持检测的方言：
     *   - MySQL / PostgreSQL / Hologres: LIMIT
     *   - SQL Server 2012+: OFFSET ... ROWS FETCH NEXT ... ROWS ONLY
     *   - Oracle: ROWNUM
     *   - Oracle 12c+: OFFSET ... FETCH
     *
     * 注意：单独的 OFFSET（无 LIMIT / FETCH 配合）不算完整分页，
     * 仍需追加 LIMIT 以限制返回行数。
     */
    public static boolean hasPagination(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String upper = stripLiteralsAndComments(sql).toUpperCase();
        // MySQL / PostgreSQL / Hologres: LIMIT
        if (KW_LIMIT.matcher(upper).find()) return true;
        // SQL Server 2012+: OFFSET ... ROWS FETCH NEXT ... ROWS ONLY
        if (KW_FETCH.matcher(upper).find() && KW_NEXT.matcher(upper).find()) return true;
        // Oracle: ROWNUM
        if (KW_ROWNUM.matcher(upper).find()) return true;
        // 单独的 OFFSET 不算完整分页（PostgreSQL OFFSET 无 LIMIT 仍返回所有行）
        return false;
    }

    // ================================================================
    // 分页 SQL 生成：根据数据库类型包装分页语句
    // ================================================================

    /**
     * 根据数据库类型，对 SQL 自动添加或替换分页语句。
     *
     * @param sql      原始 SQL
     * @param dbType   数据库类型（mysql/postgresql/oracle/sqlserver/hologres）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页行数
     * @return 处理后的分页结果
     */
    public static PaginationResult applyPagination(String sql, String dbType, int pageNum, int pageSize) {
        if (sql == null || sql.isBlank()) {
            return new PaginationResult("", 0, 0, false);
        }

        // 规范化分页参数
        if (pageNum <= 0) pageNum = DEFAULT_PAGE_NUM;
        if (pageSize <= 0) pageSize = MAX_PAGE_SIZE;
        boolean pageSizeCapped = false;

        if (hasPagination(sql)) {
            // SQL 已有分页 → 检查是否需要替换
            PaginationReplaceResult replaced = replacePagination(sql, dbType, pageNum);
            pageSizeCapped = replaced.capped();
            sql = replaced.sql();
            pageSize = replaced.effectivePageSize();
        } else {
            // SQL 无分页 → 自动添加
            if (pageSize > MAX_PAGE_SIZE) {
                pageSize = MAX_PAGE_SIZE;
                pageSizeCapped = true;
            }
            sql = appendPagination(sql, dbType, pageNum, pageSize);
        }

        return new PaginationResult(sql, pageNum, pageSize, pageSizeCapped);
    }

    /**
     * 追加分页语句到 SQL 末尾。
     *
     * 处理要点：
     *   - 先剥离末尾分号，分页子句插在分号之前
     *   - 再剥离 FOR UPDATE 等锁定后缀，分页子句插在锁定子句之前
     *   - Oracle / SQL Server 的 OFFSET...FETCH 要求 ORDER BY，缺少时自动追加
     */
    private static String appendPagination(String sql, String dbType, int pageNum, int pageSize) {
        long offset = (long) (pageNum - 1) * pageSize;
        String type = dbType != null ? dbType.toLowerCase() : "mysql";

        // 完整预处理：分号 + 锁定后缀
        PreprocessResult pp = preprocess(sql);

        String pagedSql = switch (type) {
            case "mysql", "postgresql", "hologres" ->
                    pp.coreSql() + " LIMIT " + pageSize + " OFFSET " + offset + pp.fullSuffix();
            case "oracle" -> {
                String withOrder = ensureOrderBy(pp.coreSql());
                yield withOrder + " OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY" + pp.fullSuffix();
            }
            case "sqlserver" -> {
                String withOrder = ensureOrderBy(pp.coreSql());
                yield withOrder + " OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY" + pp.fullSuffix();
            }
            default -> pp.coreSql() + " LIMIT " + pageSize + " OFFSET " + offset + pp.fullSuffix();
        };

        return pagedSql;
    }

    /**
     * 确保 SQL 包含 ORDER BY 子句。
     * Oracle 和 SQL Server 的 OFFSET...FETCH 语法强制要求 ORDER BY，
     * 若缺少则追加 ORDER BY 1（以第一列排序，仅用于满足语法要求）。
     */
    private static String ensureOrderBy(String sql) {
        String stripped = stripLiteralsAndComments(sql).toUpperCase();
        if (stripped.contains("ORDER BY")) {
            return sql;
        }
        return sql + " ORDER BY 1";
    }

    // ================================================================
    // 分页替换：已有分页但行数超限时替换
    // ================================================================

    // LIMIT 子句的正则：
    //   - 标准：LIMIT n [OFFSET m]
    //   - MySQL 逗号语法：LIMIT offset, count
    private static final Pattern LIMIT_PATTERN =
            Pattern.compile("(?i)\\bLIMIT\\s+(?:(\\d+)\\s*,\\s*(\\d+)|(\\d+)(?:\\s+OFFSET\\s+(\\d+))?)\\s*;?\\s*$");
    // SQL Server FETCH 子句的正则：OFFSET n ROWS FETCH NEXT n ROWS ONLY
    private static final Pattern SQLSERVER_FETCH_PATTERN =
            Pattern.compile("(?i)\\bOFFSET\\s+(\\d+)\\s+ROWS\\s+FETCH\\s+NEXT\\s+(\\d+)\\s+ROWS\\s+ONLY\\s*;?\\s*$");
    // Oracle ROWNUM 模式（检测 WHERE ROWNUM <= n 或 WHERE ROWNUM < n）
    private static final Pattern ORACLE_ROWNUM_PATTERN =
            Pattern.compile("(?i)\\bROWNUM\\s*(?:<=|<)\\s*(\\d+)");
    // Oracle 12c+ OFFSET...FETCH 模式
    private static final Pattern ORACLE_FETCH_PATTERN =
            Pattern.compile("(?i)\\bOFFSET\\s+(\\d+)\\s+ROWS\\s+FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS\\s+ONLY\\s*;?\\s*$");

    /**
     * 替换 SQL 中已有的分页语句，将超过 MAX_PAGE_SIZE 的行数限制替换为默认值。
     *
     * 处理要点：
     *   - 先剥离分号和锁定后缀，使分页正则能正确锚定 $ 匹配
     *   - 替换前先在骨架上验证关键字存在，避免误匹配字面量
     */
    private static PaginationReplaceResult replacePagination(String sql, String dbType, int pageNum) {
        String type = dbType != null ? dbType.toLowerCase() : "mysql";
        boolean capped = false;
        int effectivePageSize = MAX_PAGE_SIZE;

        // 完整预处理：分号 + 锁定后缀
        PreprocessResult pp = preprocess(sql);

        PaginationReplaceResult result = switch (type) {
            case "mysql", "postgresql", "hologres" -> {
                // 先在剥离字面量的骨架上验证 LIMIT 关键字确实存在
                String skeleton = stripLiteralsAndComments(pp.coreSql()).toUpperCase();
                if (!KW_LIMIT.matcher(skeleton).find()) {
                    yield new PaginationReplaceResult(sql, effectivePageSize, capped);
                }
                Matcher m = LIMIT_PATTERN.matcher(pp.coreSql());
                if (m.find()) {
                    int limitValue;
                    if (m.group(1) != null) {
                        // MySQL 逗号语法：LIMIT offset, count
                        limitValue = Integer.parseInt(m.group(2));
                    } else {
                        // 标准语法：LIMIT count [OFFSET offset]
                        limitValue = Integer.parseInt(m.group(3));
                    }
                    if (limitValue > MAX_PAGE_SIZE) {
                        capped = true;
                        effectivePageSize = MAX_PAGE_SIZE;
                        long newOffset = (long) (pageNum - 1) * effectivePageSize;
                        String replacement = "LIMIT " + effectivePageSize + " OFFSET " + newOffset;
                        String newSql = m.replaceFirst(replacement) + pp.fullSuffix();
                        yield new PaginationReplaceResult(newSql, effectivePageSize, capped);
                    }
                    effectivePageSize = limitValue;
                }
                yield new PaginationReplaceResult(sql, effectivePageSize, capped);
            }
            case "sqlserver" -> {
                String skeleton = stripLiteralsAndComments(pp.coreSql()).toUpperCase();
                if (!KW_OFFSET.matcher(skeleton).find()) {
                    yield new PaginationReplaceResult(sql, effectivePageSize, capped);
                }
                Matcher m = SQLSERVER_FETCH_PATTERN.matcher(pp.coreSql());
                if (m.find()) {
                    int fetchValue = Integer.parseInt(m.group(2));
                    if (fetchValue > MAX_PAGE_SIZE) {
                        capped = true;
                        effectivePageSize = MAX_PAGE_SIZE;
                        long newOffset = (long) (pageNum - 1) * effectivePageSize;
                        String replacement = "OFFSET " + newOffset + " ROWS FETCH NEXT " + effectivePageSize + " ROWS ONLY";
                        String newSql = m.replaceFirst(replacement) + pp.fullSuffix();
                        yield new PaginationReplaceResult(newSql, effectivePageSize, capped);
                    }
                    effectivePageSize = fetchValue;
                }
                yield new PaginationReplaceResult(sql, effectivePageSize, capped);
            }
            case "oracle" -> {
                String skeleton = stripLiteralsAndComments(pp.coreSql()).toUpperCase();
                // 优先检测 Oracle 12c+ OFFSET...FETCH 语法
                if (KW_OFFSET.matcher(skeleton).find()) {
                    Matcher m = ORACLE_FETCH_PATTERN.matcher(pp.coreSql());
                    if (m.find()) {
                        int fetchValue = Integer.parseInt(m.group(2));
                        if (fetchValue > MAX_PAGE_SIZE) {
                            capped = true;
                            effectivePageSize = MAX_PAGE_SIZE;
                            long newOffset = (long) (pageNum - 1) * effectivePageSize;
                            String replacement = "OFFSET " + newOffset + " ROWS FETCH NEXT " + effectivePageSize + " ROWS ONLY";
                            String newSql = m.replaceFirst(replacement) + pp.fullSuffix();
                            yield new PaginationReplaceResult(newSql, effectivePageSize, capped);
                        }
                        effectivePageSize = fetchValue;
                        yield new PaginationReplaceResult(sql, effectivePageSize, capped);
                    }
                }
                // Oracle 传统 ROWNUM 语法
                if (KW_ROWNUM.matcher(skeleton).find()) {
                    Matcher m = ORACLE_ROWNUM_PATTERN.matcher(pp.coreSql());
                    if (m.find()) {
                        int rownumValue = Integer.parseInt(m.group(1));
                        if (rownumValue > MAX_PAGE_SIZE) {
                            capped = true;
                            effectivePageSize = MAX_PAGE_SIZE;
                            long end = (long) (pageNum - 1) * effectivePageSize + effectivePageSize;
                            String newSql = m.replaceFirst("ROWNUM <= " + end) + pp.fullSuffix();
                            yield new PaginationReplaceResult(newSql, effectivePageSize, capped);
                        }
                        effectivePageSize = rownumValue;
                    }
                }
                yield new PaginationReplaceResult(sql, effectivePageSize, capped);
            }
            default -> new PaginationReplaceResult(sql, effectivePageSize, capped);
        };

        return result;
    }

    // ================================================================
    // COUNT SQL 生成
    // ================================================================

    /**
     * 生成 COUNT 查询 SQL，用于获取总记录数。
     * 对于已有分页子句的 SQL，先移除分页部分再包装为子查询。
     * 支持所有方言：LIMIT/OFFSET、OFFSET...FETCH、ROWNUM 包装。
     */
    public static String buildCountSql(String sql, String dbType) {
        if (sql == null || sql.isBlank()) {
            return "SELECT COUNT(*) FROM (SELECT 1) _t";
        }

        String type = dbType != null ? dbType.toLowerCase() : "mysql";

        // 先剥离分号和锁定后缀，使分页正则能正确锚定到 SQL 末尾
        PreprocessResult pp = preprocess(sql);
        String coreSql = pp.coreSql();
        // 注意：锁定后缀和分号不需要拼回 COUNT SQL

        switch (type) {
            case "mysql", "postgresql", "hologres":
                // 移除末尾的 LIMIT offset, count / LIMIT count OFFSET offset / LIMIT count
                coreSql = coreSql.replaceAll(
                        "(?i)\\bLIMIT\\s+(?:\\d+\\s*,\\s*\\d+|\\d+(?:\\s+OFFSET\\s+\\d+)?)\\s*$", "");
                break;
            case "sqlserver":
                // 移除末尾的 OFFSET n ROWS FETCH NEXT n ROWS ONLY
                coreSql = coreSql.replaceAll(
                        "(?i)\\bOFFSET\\s+\\d+\\s+ROWS\\s+FETCH\\s+NEXT\\s+\\d+\\s+ROWS\\s+ONLY\\s*$", "");
                break;
            case "oracle":
                // 移除 Oracle 12c+ OFFSET...FETCH
                coreSql = coreSql.replaceAll(
                        "(?i)\\bOFFSET\\s+\\d+\\s+ROWS\\s+FETCH\\s+(?:NEXT|FIRST)\\s+\\d+\\s+ROWS\\s+ONLY\\s*$", "");
                // 移除 Oracle ROWNUM 包装：SELECT * FROM (SELECT a.*, ROWNUM rn FROM (...) a WHERE ROWNUM <= n) WHERE rn > m
                Matcher oracleWrapper = ORACLE_ROWNUM_WRAPPER.matcher(coreSql);
                if (oracleWrapper.find()) {
                    coreSql = oracleWrapper.group(1).trim();
                }
                break;
            default:
                // 通用：尝试移除 LIMIT 子句
                coreSql = coreSql.replaceAll(
                        "(?i)\\bLIMIT\\s+(?:\\d+\\s*,\\s*\\d+|\\d+(?:\\s+OFFSET\\s+\\d+)?)\\s*$", "");
                break;
        }

        return "SELECT COUNT(*) FROM (" + coreSql + ") _t";
    }

    // Oracle ROWNUM 包装模式的正则：
    // SELECT * FROM (SELECT a.*, ROWNUM rn FROM (内层SQL) a WHERE ROWNUM <= n) WHERE rn > m
    private static final Pattern ORACLE_ROWNUM_WRAPPER =
            Pattern.compile("(?i)^SELECT\\s+\\*\\s+FROM\\s+\\(\\s*SELECT\\s+\\w+\\.\\*\\s*,\\s*ROWNUM\\s+\\w+\\s+FROM\\s+\\(\\s*([\\s\\S]+?)\\s*\\)\\s+\\w+\\s+WHERE\\s+ROWNUM\\s*(?:<=|<)\\s*\\d+\\s*\\)\\s+WHERE\\s+\\w+\\s*>\\s*\\d+\\s*$");

    // ================================================================
    // 结果记录
    // ================================================================

    /**
     * 分页处理结果
     *
     * @param sql            处理后的 SQL（含分页）
     * @param pageNum        实际使用的页码
     * @param pageSize       实际使用的每页行数
     * @param pageSizeCapped 是否因超过上限被截断为默认值
     */
    public record PaginationResult(String sql, int pageNum, int pageSize, boolean pageSizeCapped) {}

    /**
     * 分页替换结果（内部使用）
     */
    private record PaginationReplaceResult(String sql, int effectivePageSize, boolean capped) {}
}
