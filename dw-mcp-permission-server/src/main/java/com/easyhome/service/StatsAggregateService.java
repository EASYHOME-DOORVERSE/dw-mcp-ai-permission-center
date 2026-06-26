package com.easyhome.service;

import com.easyhome.entity.McpCallDailyStats;
import com.easyhome.entity.McpCallUserDailyStats;
import com.easyhome.mapper.McpCallDailyStatsMapper;
import com.easyhome.mapper.McpCallUserDailyStatsMapper;
import com.easyhome.mapper.McpToolCallLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 调用日志两级汇总服务
 * <p>
 * 唯一汇总入口，定时任务和手动补跑共用同一套逻辑：
 * <ul>
 *   <li>Step 1：从 mcp_tool_call_log 按 userId + toolId 聚合 → mcp_call_user_daily_stats</li>
 *   <li>Step 2：从 mcp_call_user_daily_stats 按 toolId 二次汇总 → mcp_call_daily_stats</li>
 * </ul>
 * <p>
 * 全局表的 P50/P95/P99 直接从原始日志表用 LIMIT OFFSET 查询（无法从用户级百分位简单相加得到）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregateService {

    private final McpToolCallLogMapper callLogMapper;
    private final McpCallUserDailyStatsMapper userDailyStatsMapper;
    private final McpCallDailyStatsMapper dailyStatsMapper;
    private final RedissonClient redissonClient;

    /** 单次补跑最大天数 */
    private static final int MAX_RANGE_DAYS = 90;

    /**
     * 聚合指定日期的两级汇总数据（幂等，可重复执行）
     * <p>
     * 定时任务调它（传 yesterday），手动补跑也调它（传用户选的日期）。
     */
    public void aggregateDate(LocalDate date) {
        String lockKey = "stats:aggregate:" + date;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = lock.tryLock(5, 300, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断: " + date, e);
        }

        if (!acquired) {
            throw new RuntimeException("日期 " + date + " 正在汇总中，请稍后再试");
        }

        try {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);

            // Step 1：用户维度汇总
            int userCount = aggregateUserStats(date, start, end);

            // Step 2：全局二次汇总
            int globalCount = aggregateGlobalStats(date, start, end);

            log.info("日期 {} 汇总完成: 用户维度 {} 条, 全局 {} 条", date, userCount, globalCount);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量聚合日期范围内的两级汇总数据
     * <p>
     * 单日失败不中断后续天数，统一收集错误后返回结果。
     */
    public AggregateResult aggregateRange(LocalDate start, LocalDate end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("单次补跑范围不能超过" + MAX_RANGE_DAYS + "天，当前" + days + "天");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }

        long startTime = System.currentTimeMillis();
        int successDays = 0;
        int failedDays = 0;
        List<String> errors = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            try {
                aggregateDate(d);
                successDays++;
            } catch (Exception e) {
                failedDays++;
                errors.add(d + ": " + e.getMessage());
                log.error("日期 {} 汇总失败: {}", d, e.getMessage(), e);
            }
        }

        long costMs = System.currentTimeMillis() - startTime;
        int totalDays = (int) days;

        AggregateResult result = new AggregateResult();
        result.setTotalDays(totalDays);
        result.setSuccessDays(successDays);
        result.setFailedDays(failedDays);
        result.setCostMs(costMs);
        result.setErrors(errors);

        log.info("批量汇总完成: 总{}天, 成功{}, 失败{}, 耗时{}ms", totalDays, successDays, failedDays, costMs);
        return result;
    }

    // ---- 内部方法 ----

    /** 分批处理每批大小 */
    private static final int AGGREGATE_BATCH_SIZE = 500;

    /**
     * Step 1：从原始日志按 userId + toolId 聚合，写入用户维度汇总表
     * <p>
     * 使用 keyset 分页分批拉取 (userId, toolId) 组合，避免数据量大时一次性加载所有组合到内存。
     */
    private int aggregateUserStats(LocalDate date, LocalDateTime start, LocalDateTime end) {
        int count = 0;
        long lastUserId = 0;
        long lastToolId = 0;

        while (true) {
            List<java.util.Map<String, Object>> batch = callLogMapper.selectUserToolPairsPage(
                    start, end, lastUserId, lastToolId, AGGREGATE_BATCH_SIZE);
            if (batch.isEmpty()) break;

            for (java.util.Map<String, Object> row : batch) {
                Long userId = row.get("user_id") != null ? ((Number) row.get("user_id")).longValue() : null;
                Long toolId = row.get("tool_id") != null ? ((Number) row.get("tool_id")).longValue() : null;
                if (userId == null || toolId == null) continue;

                McpCallUserDailyStats stats = callLogMapper.aggregateUserDailyStats(userId, toolId, start, end);
                if (stats == null || stats.getCallCount() == null || stats.getCallCount() <= 0) continue;

                stats.setStatDate(date);
                stats.setUserId(userId);
                stats.setToolId(toolId);

                // 合并 P50/P95/P99 到一条 SQL 查询，减少 N+1 DB 调用
                long totalCount = stats.getCallCount();
                java.util.Map<String, Object> pctMap = callLogMapper.selectPercentilesForUser(
                        userId, toolId, start, end,
                        percentileOffset(totalCount, 0.50),
                        percentileOffset(totalCount, 0.95),
                        percentileOffset(totalCount, 0.99));
                if (pctMap != null) {
                    if (pctMap.get("p50") != null) stats.setP50DurationMs(((Number) pctMap.get("p50")).longValue());
                    if (pctMap.get("p95") != null) stats.setP95DurationMs(((Number) pctMap.get("p95")).longValue());
                    if (pctMap.get("p99") != null) stats.setP99DurationMs(((Number) pctMap.get("p99")).longValue());
                }

                userDailyStatsMapper.upsertUserDailyStats(stats);
                count++;
            }

            // 更新游标为当前批次最后一条
            java.util.Map<String, Object> lastRow = batch.get(batch.size() - 1);
            lastUserId = lastRow.get("user_id") != null ? ((Number) lastRow.get("user_id")).longValue() : 0;
            lastToolId = lastRow.get("tool_id") != null ? ((Number) lastRow.get("tool_id")).longValue() : 0;

            if (batch.size() < AGGREGATE_BATCH_SIZE) break;

            log.debug("日期 {} 用户维度聚合已处理 {} 条，继续下一批...", date, count);
        }

        if (count == 0) {
            log.info("日期 {} 无调用记录，跳过用户维度聚合", date);
        }
        return count;
    }

    /**
     * Step 2：基于用户维度汇总表二次汇总，写入全局表
     * <p>
     * 基础计数指标（call_count 等）通过 SQL GROUP BY 聚合；
     * P50/P95/P99 直接从原始日志用 LIMIT OFFSET 查询（全局维度）。
     */
    private int aggregateGlobalStats(LocalDate date, LocalDateTime start, LocalDateTime end) {
        // SQL 层按 toolId 聚合，直接返回每个工具的全局基础统计
        List<McpCallDailyStats> toolStatsList = userDailyStatsMapper.aggregateGlobalByTool(date);

        if (toolStatsList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (McpCallDailyStats global : toolStatsList) {
            Long toolId = global.getToolId();
            int totalCallCount = global.getCallCount() != null ? global.getCallCount() : 0;

            // P50/P95/P99 合并一条 SQL 查询（全局维度，百分位不可加）
            if (totalCallCount > 0) {
                java.util.Map<String, Object> pctMap = callLogMapper.selectPercentiles(
                        toolId, start, end,
                        percentileOffset(totalCallCount, 0.50),
                        percentileOffset(totalCallCount, 0.95),
                        percentileOffset(totalCallCount, 0.99));
                if (pctMap != null) {
                    if (pctMap.get("p50") != null) global.setP50DurationMs(((Number) pctMap.get("p50")).longValue());
                    if (pctMap.get("p95") != null) global.setP95DurationMs(((Number) pctMap.get("p95")).longValue());
                    if (pctMap.get("p99") != null) global.setP99DurationMs(((Number) pctMap.get("p99")).longValue());
                }
            }

            dailyStatsMapper.upsertDailyStats(global);
            count++;
        }
        return count;
    }

    /**
     * 根据总数和百分位计算 OFFSET 偏移量（0-based）
     */
    private int percentileOffset(long totalCount, double pct) {
        if (totalCount <= 1) return 0;
        return (int) Math.min(Math.ceil(pct * totalCount) - 1, totalCount - 1);
    }

    /**
     * 汇总结果
     */
    @Data
    public static class AggregateResult implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 总天数 */
        private int totalDays;
        /** 成功天数 */
        private int successDays;
        /** 失败天数 */
        private int failedDays;
        /** 总耗时（毫秒） */
        private long costMs;
        /** 失败明细 */
        private List<String> errors;
    }
}
