package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MCP 调用日志每日预汇总表（全局）
 * <p>
 * 每日 00:05 由定时任务跑批生成，基于用户维度汇总表 mcp_call_user_daily_stats 二次汇总，
 * 看板趋势图/成功率/热门工具优先从此表读取，避免每次实时聚合 mcp_tool_call_log 大表。
 * <p>
 * 维度：stat_date + tool_id（仅全局汇总，用户维度数据见 McpCallUserDailyStats）
 */
@Data
@TableName("mcp_call_daily_stats")
public class McpCallDailyStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** MCP 工具 ID */
    private Long toolId;

    /** MCP 工具名称 */
    private String toolName;

    /** 当日调用总次数 */
    private Integer callCount;

    /** 当日成功次数 */
    private Integer successCount;

    /** 当日失败次数 */
    private Integer failCount;

    /** 当日权限拒绝次数 */
    private Integer denyCount;

    /** 当日平均耗时（毫秒） */
    private Long avgDurationMs;

    /** 当日最大耗时（毫秒） */
    private Long maxDurationMs;

    /** 当日 P50 耗时（毫秒） */
    private Long p50DurationMs;

    /** 当日 P95 耗时（毫秒） */
    private Long p95DurationMs;

    /** 当日 P99 耗时（毫秒） */
    private Long p99DurationMs;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}