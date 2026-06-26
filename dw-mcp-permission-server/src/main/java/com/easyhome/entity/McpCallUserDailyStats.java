package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MCP 调用日志用户维度每日预汇总表
 * <p>
 * 第一级汇总：按 userId + toolId + statDate 聚合原始日志，
 * 全局表 mcp_call_daily_stats 基于本表二次汇总生成。
 * 看板中普通用户从此表读取自己的调用数据。
 */
@Data
@TableName("mcp_call_user_daily_stats")
public class McpCallUserDailyStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** MCP 工具 ID */
    private Long toolId;

    /** MCP 工具名称 */
    private String toolName;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

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
