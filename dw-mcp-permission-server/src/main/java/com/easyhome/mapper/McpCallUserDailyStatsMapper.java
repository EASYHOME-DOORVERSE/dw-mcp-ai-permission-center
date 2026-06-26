package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.api.vo.DashboardStatsVO;
import com.easyhome.entity.McpCallDailyStats;
import com.easyhome.entity.McpCallUserDailyStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface McpCallUserDailyStatsMapper extends BaseMapper<McpCallUserDailyStats> {

    /**
     * 幂等写入：INSERT ON DUPLICATE KEY UPDATE
     * 保证跑批任务可重复执行不会报错
     */
    @Insert("INSERT INTO mcp_call_user_daily_stats (stat_date, tool_id, tool_name, user_id, username, " +
            "call_count, success_count, fail_count, deny_count, " +
            "avg_duration_ms, max_duration_ms, p50_duration_ms, p95_duration_ms, p99_duration_ms) " +
            "VALUES (#{statDate}, #{toolId}, #{toolName}, #{userId}, #{username}, " +
            "#{callCount}, #{successCount}, #{failCount}, #{denyCount}, " +
            "#{avgDurationMs}, #{maxDurationMs}, #{p50DurationMs}, #{p95DurationMs}, #{p99DurationMs}) " +
            "ON DUPLICATE KEY UPDATE " +
            "call_count=#{callCount}, success_count=#{successCount}, fail_count=#{failCount}, deny_count=#{denyCount}, " +
            "avg_duration_ms=#{avgDurationMs}, max_duration_ms=#{maxDurationMs}, " +
            "p50_duration_ms=#{p50DurationMs}, p95_duration_ms=#{p95DurationMs}, p99_duration_ms=#{p99DurationMs}")
    void upsertUserDailyStats(McpCallUserDailyStats stats);

    /**
     * SQL 层按 toolId 二次汇总：从用户维度表聚合出全局维度基础统计（不含百分位）
     * <p>
     * GROUP BY 包含 SELECT 中所有非聚合列，符合 MySQL only_full_group_by 规范。
     * avg_duration_ms 采用加权平均：SUM(avg * count) / SUM(count)。
     */
    @Select("""
            SELECT
                stat_date,
                tool_id,
                tool_name,
                SUM(call_count)    AS call_count,
                SUM(success_count) AS success_count,
                SUM(fail_count)    AS fail_count,
                SUM(deny_count)    AS deny_count,
                MAX(max_duration_ms) AS max_duration_ms,
                CASE WHEN SUM(call_count) > 0
                     THEN SUM(avg_duration_ms * call_count) / SUM(call_count)
                     ELSE 0 END   AS avg_duration_ms
            FROM mcp_call_user_daily_stats
            WHERE stat_date = #{date}
            GROUP BY stat_date, tool_id, tool_name
            """)
    List<McpCallDailyStats> aggregateGlobalByTool(@Param("date") LocalDate date);

    /**
     * SQL 层聚合用户维度热门工具 Top N（普通用户看板用），
     * 直接在数据库完成 GROUP BY + 排序 + 截断，避免全量加载明细到内存。
     */
    @Select("""
            SELECT tool_id, tool_name,
                   SUM(call_count)    AS call_count,
                   CASE WHEN SUM(call_count) > 0
                        THEN ROUND(SUM(success_count) * 100.0 / SUM(call_count), 1)
                        ELSE 0 END AS success_rate,
                   CASE WHEN SUM(call_count) > 0
                        THEN SUM(avg_duration_ms * call_count) / SUM(call_count)
                        ELSE 0 END AS avg_duration_ms
            FROM mcp_call_user_daily_stats
            WHERE user_id = #{userId}
              AND stat_date >= #{startDate} AND stat_date <= #{endDate}
            GROUP BY tool_id, tool_name
            ORDER BY call_count DESC
            LIMIT #{limit}
            """)
    List<DashboardStatsVO.TopToolItem> selectTopToolsByUser(@Param("userId") Long userId,
                                                              @Param("startDate") LocalDate startDate,
                                                              @Param("endDate") LocalDate endDate,
                                                              @Param("limit") int limit);
}
