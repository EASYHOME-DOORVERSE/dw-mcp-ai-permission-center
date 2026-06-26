package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.entity.McpCallUserDailyStats;
import com.easyhome.entity.McpToolCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface McpToolCallLogMapper extends BaseMapper<McpToolCallLog> {

    /**
     * 分页查询指定时间范围内有调用记录的 (userId, toolId) 组合
     * <p>
     * 使用 keyset 分页（游标翻页），通过 (user_id, tool_id) > (lastUserId, lastToolId) 避免大 OFFSET 性能问题。
     * 首批调用时 lastUserId/lastToolId 传 0，后续传入上一批最后一条的值。
     */
    @Select("""
            SELECT DISTINCT user_id, tool_id FROM mcp_tool_call_log
            WHERE call_at >= #{start} AND call_at <= #{end}
              AND (user_id, tool_id) > (#{lastUserId}, #{lastToolId})
            ORDER BY user_id ASC, tool_id ASC
            LIMIT #{batchSize}
            """)
    List<java.util.Map<String, Object>> selectUserToolPairsPage(@Param("start") LocalDateTime start,
                                                                   @Param("end") LocalDateTime end,
                                                                   @Param("lastUserId") long lastUserId,
                                                                   @Param("lastToolId") long lastToolId,
                                                                   @Param("batchSize") int batchSize);

    /**
     * 按用户+工具维度聚合指定日期的基础调用统计（不含百分位耗时）
     */
    @Select("""
            SELECT
                #{userId} AS user_id,
                l.username,
                #{toolId} AS tool_id,
                t.tool_name,
                COUNT(*) AS call_count,
                SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN l.success = 0 AND (l.deny_reason IS NULL OR l.deny_reason NOT LIKE '%权限%') THEN 1 ELSE 0 END) AS fail_count,
                SUM(CASE WHEN l.success = 0 AND l.deny_reason LIKE '%权限%' THEN 1 ELSE 0 END) AS deny_count,
                AVG(l.duration_ms) AS avg_duration_ms,
                MAX(l.duration_ms) AS max_duration_ms
            FROM mcp_tool_call_log l
            JOIN mcp_tool t ON t.id = l.tool_id AND t.is_deleted = 0
            WHERE l.user_id = #{userId}
              AND l.tool_id = #{toolId}
              AND l.call_at >= #{start}
              AND l.call_at <= #{end}
            GROUP BY l.username, t.tool_name
            """)
    McpCallUserDailyStats aggregateUserDailyStats(@Param("userId") Long userId,
                                                   @Param("toolId") Long toolId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * 合并用户维度的 P50/P95/P99 百分位查询到一条 SQL，减少 N+1 问题
     * <p>
     * 返回 Map: {p50: xxx, p95: xxx, p99: xxx}
     */
    @Select("""
            SELECT
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE user_id = #{userId} AND tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p50Offset}) AS p50,
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE user_id = #{userId} AND tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p95Offset}) AS p95,
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE user_id = #{userId} AND tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p99Offset}) AS p99
            """)
    java.util.Map<String, Object> selectPercentilesForUser(@Param("userId") Long userId,
                                                             @Param("toolId") Long toolId,
                                                             @Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end,
                                                             @Param("p50Offset") int p50Offset,
                                                             @Param("p95Offset") int p95Offset,
                                                             @Param("p99Offset") int p99Offset);

    /**
     * 合并全局维度的 P50/P95/P99 百分位查询到一条 SQL
     */
    @Select("""
            SELECT
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p50Offset}) AS p50,
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p95Offset}) AS p95,
                (SELECT duration_ms FROM mcp_tool_call_log
                 WHERE tool_id = #{toolId}
                   AND call_at >= #{start} AND call_at <= #{end}
                 ORDER BY duration_ms ASC LIMIT 1 OFFSET #{p99Offset}) AS p99
            """)
    java.util.Map<String, Object> selectPercentiles(@Param("toolId") Long toolId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("p50Offset") int p50Offset,
                                                      @Param("p95Offset") int p95Offset,
                                                      @Param("p99Offset") int p99Offset);

    /**
     * SQL 层直接做耗时分桶统计，只返回 5 个计数值，避免全量加载 duration 数据到内存
     */
    @Select("<script>" +
            "SELECT " +
            "SUM(CASE WHEN duration_ms &gt;= 0 AND duration_ms &lt; 100 THEN 1 ELSE 0 END) AS b0_100, " +
            "SUM(CASE WHEN duration_ms &gt;= 100 AND duration_ms &lt; 500 THEN 1 ELSE 0 END) AS b100_500, " +
            "SUM(CASE WHEN duration_ms &gt;= 500 AND duration_ms &lt; 1000 THEN 1 ELSE 0 END) AS b500_1000, " +
            "SUM(CASE WHEN duration_ms &gt;= 1000 AND duration_ms &lt; 3000 THEN 1 ELSE 0 END) AS b1000_3000, " +
            "SUM(CASE WHEN duration_ms &gt;= 3000 THEN 1 ELSE 0 END) AS b3000_plus " +
            "FROM mcp_tool_call_log " +
            "WHERE call_at &gt;= #{start} AND call_at &lt;= #{end} " +
            "<if test='userId != null'>AND user_id = #{userId} </if>" +
            "</script>")
    java.util.Map<String, Object> countDurationBuckets(@Param("start") LocalDateTime start,
                                                         @Param("end") LocalDateTime end,
                                                         @Param("userId") Long userId);

    /**
     * SQL 层聚合工具调用 Top N（支持按用户过滤），只返回 Top N 行而非全量日志
     */
    @Select("<script>" +
            "SELECT l.tool_id, l.tool_name, " +
            "COUNT(*) AS call_count, " +
            "SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END) AS success_count, " +
            "AVG(l.duration_ms) AS avg_duration_ms " +
            "FROM mcp_tool_call_log l " +
            "WHERE l.call_at &gt;= #{start} AND l.call_at &lt;= #{end} " +
            "<if test='userId != null'>AND l.user_id = #{userId} </if>" +
            "GROUP BY l.tool_id, l.tool_name " +
            "ORDER BY call_count DESC " +
            "LIMIT #{limit}" +
            "</script>")
    java.util.List<java.util.Map<String, Object>> selectTopToolsAggregated(@Param("start") LocalDateTime start,
                                                                             @Param("end") LocalDateTime end,
                                                                             @Param("userId") Long userId,
                                                                             @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(DISTINCT user_id) FROM mcp_tool_call_log " +
            "WHERE call_at &gt;= #{start} AND call_at &lt;= #{end} " +
            "<if test='userId != null'>AND user_id = #{userId} </if>" +
            "</script>")
    long countDistinctUser(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           @Param("userId") Long userId);

    /**
     * 查询指定时间范围内的总调用次数和成功/失败数（支持按用户过滤）
     */
    @Select("<script>" +
            "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed " +
            "FROM mcp_tool_call_log " +
            "WHERE call_at &gt;= #{start} AND call_at &lt;= #{end} " +
            "<if test='userId != null'>AND user_id = #{userId} </if>" +
            "</script>")
    java.util.Map<String, Object> countByDateRange(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     @Param("userId") Long userId);
}
