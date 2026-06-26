package com.easyhome.schedule;

import com.easyhome.service.StatsAggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 调用日志每日预汇总定时任务
 * <p>
 * 每日 00:05 执行，聚合前一天（00:00~23:59:59）的 mcp_tool_call_log 数据，
 * 分两步写入汇总表：
 * <ol>
 *   <li>用户维度汇总 → mcp_call_user_daily_stats</li>
 *   <li>全局二次汇总 → mcp_call_daily_stats</li>
 * </ol>
 * <p>
 * 核心逻辑统一由 {@link StatsAggregateService} 实现，本类仅负责定时触发。
 * 手动补跑入口在 DashboardController。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallStatsSchedule {

    private final StatsAggregateService statsAggregateService;

    /**
     * 每日 00:05 跑批，聚合前一天数据
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void aggregateDailyStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始定时跑批聚合调用日志，日期: {}", yesterday);
        try {
            statsAggregateService.aggregateDate(yesterday);
            log.info("定时跑批完成，日期: {}", yesterday);
        } catch (Exception e) {
            log.error("定时跑批失败，日期: {}: {}", yesterday, e.getMessage(), e);
        }
    }
}
