package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.api.vo.DashboardStatsVO;
import com.easyhome.entity.McpCallDailyStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface McpCallDailyStatsMapper extends BaseMapper<McpCallDailyStats> {

    /**
     * 幂等写入：INSERT ON DUPLICATE KEY UPDATE
     * 全局表仅按 stat_date + tool_id 唯一键 upsert
     */
    @Insert("INSERT INTO mcp_call_daily_stats (stat_date, tool_id, tool_name, " +
            "call_count, success_count, fail_count, deny_count, " +
            "avg_duration_ms, max_duration_ms, p50_duration_ms, p95_duration_ms, p99_duration_ms) " +
            "VALUES (#{statDate}, #{toolId}, #{toolName}, " +
            "#{callCount}, #{successCount}, #{failCount}, #{denyCount}, " +
            "#{avgDurationMs}, #{maxDurationMs}, #{p50DurationMs}, #{p95DurationMs}, #{p99DurationMs}) " +
            "ON DUPLICATE KEY UPDATE " +
            "call_count=#{callCount}, success_count=#{successCount}, fail_count=#{failCount}, deny_count=#{denyCount}, " +
            "avg_duration_ms=#{avgDurationMs}, max_duration_ms=#{maxDurationMs}, " +
            "p50_duration_ms=#{p50DurationMs}, p95_duration_ms=#{p95DurationMs}, p99_duration_ms=#{p99DurationMs}")
    void upsertDailyStats(McpCallDailyStats stats);

    /**
     * SQL 层聚合全局维度热门工具 Top N，直接在数据库完成 GROUP BY + 排序 + 截断，
     * 避免全量加载明细到内存。avg_duration_ms 采用加权平均。
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
            FROM mcp_call_daily_stats
            WHERE stat_date >= #{startDate} AND stat_date <= #{endDate}
            GROUP BY tool_id, tool_name
            ORDER BY call_count DESC
            LIMIT #{limit}
            """)
    List<DashboardStatsVO.TopToolItem> selectTopTools(@Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate,
                                                       @Param("limit") int limit);
}