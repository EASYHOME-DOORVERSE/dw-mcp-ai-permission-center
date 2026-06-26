package com.easyhome.controller;

import com.easyhome.api.vo.DashboardStatsVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.service.DashboardService;
import com.easyhome.service.StatsAggregateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 看板 + 调用日志查询 Controller
 * <p>
 * 管理员和普通用户均可访问，数据范围按角色自动过滤。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final StatsAggregateService statsAggregateService;

    /**
     * 获取看板聚合统计数据
     *
     * @param days 趋势图天数（7 或 30，默认 7）
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Result<DashboardStatsVO> getStats(@RequestParam(defaultValue = "7") int days) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return Result.fail(401, "未登录");
        }
        boolean isAdmin = jwtAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Long userId = jwtAuth.getUserId();
        return Result.ok(dashboardService.getStats(isAdmin, userId, days));
    }

    /** 调用日志查询最大时间范围（天） */
    private static final int LOG_MAX_RANGE_DAYS = 90;

    /**
     * 分页查询调用日志（支持图表联动筛选）
     * <p>
     * 未指定 startTime 时默认查询近 90 天，范围超过 90 天时拒绝请求，防止全表扫描过慢。
     */
    @GetMapping("/call-logs")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<DashboardStatsVO.CallLogItem>> queryCallLogs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long toolId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String denyReason,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String username) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return Result.fail(401, "未登录");
        }
        boolean isAdmin = jwtAuth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Long userId = jwtAuth.getUserId();

        // 未传 startTime 时默认近 90 天
        LocalDate startDate = startTime != null ? LocalDate.parse(startTime)
                : LocalDate.now().minusDays(LOG_MAX_RANGE_DAYS);
        LocalDate endDate = endTime != null ? LocalDate.parse(endTime) : LocalDate.now();

        // 范围校验
        long rangeDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (rangeDays > LOG_MAX_RANGE_DAYS) {
            return Result.fail(400, "时间范围不能超过" + LOG_MAX_RANGE_DAYS + "天，当前" + rangeDays + "天");
        }

        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(23, 59, 59);

        return Result.ok(dashboardService.queryCallLogs(current, size, isAdmin, userId,
                toolId, toolName, success, denyReason, startDt, endDt, username));
    }

    /**
     * 手动触发汇总任务（仅管理员）
     * <p>
     * 用于跑批任务失败后修复数据，支持指定日期范围，幂等可重复执行。
     */
    @PostMapping("/aggregate-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StatsAggregateService.AggregateResult> manualAggregate(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            StatsAggregateService.AggregateResult result = statsAggregateService.aggregateRange(startDate, endDate);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.fail(500, "汇总执行失败: " + e.getMessage());
        }
    }
}