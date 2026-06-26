package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.vo.DashboardStatsVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.entity.McpCallDailyStats;
import com.easyhome.entity.McpCallUserDailyStats;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.McpToolCallLog;
import com.easyhome.entity.McpToolCategory;
import com.easyhome.entity.SysApiKey;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysRoleTool;
import com.easyhome.entity.SysUser;
import com.easyhome.mapper.McpCallDailyStatsMapper;
import com.easyhome.mapper.McpCallUserDailyStatsMapper;
import com.easyhome.mapper.McpToolCallLogMapper;
import com.easyhome.mapper.McpToolCategoryMapper;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.mapper.SysApiKeyMapper;
import com.easyhome.mapper.SysRoleMapper;
import com.easyhome.mapper.SysRoleToolMapper;
import com.easyhome.mapper.SysUserMapper;
import com.easyhome.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final McpCallDailyStatsMapper dailyStatsMapper;
    private final McpCallUserDailyStatsMapper userDailyStatsMapper;
    private final McpToolCallLogMapper callLogMapper;
    private final McpToolMapper toolMapper;
    private final McpToolCategoryMapper categoryMapper;
    private final SysApiKeyMapper apiKeyMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleToolMapper roleToolMapper;

    @Override
    public DashboardStatsVO getStats(boolean isAdmin, Long userId, int days) {
        DashboardStatsVO vo = new DashboardStatsVO();

        // ---- 1. 概览统计 ----
        vo.setOverview(buildOverview(isAdmin, userId));

        // ---- 2. 调用趋势（普通用户看自己的调用记录） ----
        vo.setCallTrend(buildCallTrend(isAdmin, userId, days));

        // ---- 3. 成功率分布 ----
        vo.setSuccessRate(buildSuccessRate(isAdmin, userId, days));

        // ---- 4. 热门工具 Top 10（固定近 30 天，不跟随趋势图天数） ----
        vo.setTopTools(buildTopTools(isAdmin, userId, 30));

        // ---- 5. 耗时分布 ----
        vo.setDurationDistribution(buildDurationDistribution(isAdmin, userId));

        // ---- 6. 角色权限分布（管理员）/ 我的权限摘要（普通用户） ----
        if (isAdmin) {
            vo.setRoleDistribution(buildRoleDistribution());
        } else {
            vo.setMyPermissionSummary(buildMyPermissionSummary(userId));
        }

        return vo;
    }

    @Override
    public PageResult<DashboardStatsVO.CallLogItem> queryCallLogs(long current, long size,
                                                                   boolean isAdmin, Long userId,
                                                                   Long toolId, String toolName,
                                                                   Boolean success, String denyReason,
                                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                                   String username) {
        LambdaQueryWrapper<McpToolCallLog> wrapper = new LambdaQueryWrapper<McpToolCallLog>()
                // 普通用户只看自己的调用记录
                .eq(!isAdmin, McpToolCallLog::getUserId, userId)
                .eq(toolId != null, McpToolCallLog::getToolId, toolId)
                .like(toolName != null && !toolName.isBlank(), McpToolCallLog::getToolName, toolName)
                .eq(success != null, McpToolCallLog::getSuccess, success)
                .like(username != null && !username.isBlank() && isAdmin, McpToolCallLog::getUsername, username)
                .ge(startTime != null, McpToolCallLog::getCallAt, startTime)
                .le(endTime != null, McpToolCallLog::getCallAt, endTime)
                .orderByDesc(McpToolCallLog::getCallAt);

        // denyReason 精确匹配（仅失败时有值）
        if (denyReason != null && !denyReason.isBlank()) {
            wrapper.like(McpToolCallLog::getDenyReason, denyReason);
        }

        Page<McpToolCallLog> page = callLogMapper.selectPage(new Page<>(current, size), wrapper);

        // 加载工具显示名和分类名
        List<Long> toolIds = page.getRecords().stream()
                .map(McpToolCallLog::getToolId).distinct().toList();
        Map<Long, McpTool> toolMap = toolIds.isEmpty() ? Map.of() :
                toolMapper.selectBatchIds(toolIds).stream()
                        .collect(Collectors.toMap(McpTool::getId, t -> t));

        List<Long> categoryIds = toolMap.values().stream()
                .map(McpTool::getCategoryId).filter(c -> c != null && c > 0).distinct().toList();
        Map<Long, McpToolCategory> categoryMap = categoryIds.isEmpty() ? Map.of() :
                categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(McpToolCategory::getId, c -> c));

        List<DashboardStatsVO.CallLogItem> items = page.getRecords().stream().map(log -> {
            DashboardStatsVO.CallLogItem item = new DashboardStatsVO.CallLogItem();
            item.setId(log.getId());
            item.setUserId(log.getUserId());
            item.setUsername(log.getUsername());
            item.setToolId(log.getToolId());
            item.setToolName(log.getToolName());
            McpTool tool = toolMap.get(log.getToolId());
            item.setDisplayName(tool != null ? tool.getDisplayName() : null);
            if (tool != null && tool.getCategoryId() != null) {
                McpToolCategory cat = categoryMap.get(tool.getCategoryId());
                item.setCategoryName(cat != null ? cat.getCategoryName() : null);
            }
            item.setCallAt(log.getCallAt() != null ?
                    log.getCallAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            item.setDurationMs(log.getDurationMs());
            item.setSuccess(log.getSuccess());
            item.setDenyReason(log.getDenyReason());
            item.setRequestArgs(log.getRequestArgs());
            item.setResponseSize(log.getResponseSize());
            return item;
        }).toList();

        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), items);
    }

    // ---- 私有构建方法 ----

    private DashboardStatsVO.Overview buildOverview(boolean isAdmin, Long userId) {
        DashboardStatsVO.Overview ov = new DashboardStatsVO.Overview();

        if (isAdmin) {
            // 管理员：系统全局统计
            ov.setToolTotal(Math.toIntExact(toolMapper.selectCount(
                    new LambdaQueryWrapper<McpTool>().eq(McpTool::getIsDeleted, 0))));
            ov.setToolEnabled(Math.toIntExact(toolMapper.selectCount(
                    new LambdaQueryWrapper<McpTool>().eq(McpTool::getIsDeleted, 0).eq(McpTool::getStatus, 1))));

            ov.setApiKeyTotal(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0))));
            ov.setApiKeyEnabled(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0).eq(SysApiKey::getStatus, 1))));
            LocalDateTime expiringThreshold = LocalDateTime.now().plusDays(7);
            ov.setApiKeyExpiring(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0).eq(SysApiKey::getStatus, 1)
                            .isNotNull(SysApiKey::getExpiredAt).le(SysApiKey::getExpiredAt, expiringThreshold))));

            ov.setUserTotal(Math.toIntExact(userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getIsDeleted, 0).eq(SysUser::getStatus, 1))));
        } else {
            // 普通用户：只统计自己有权限的启用工具数
            ov.setToolEnabled(toolMapper.countEnabledToolsByUserId(userId));
            ov.setToolTotal(ov.getToolEnabled());

            // API Key：仅统计自己的
            ov.setApiKeyTotal(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0)
                            .eq(SysApiKey::getUserId, userId))));
            ov.setApiKeyEnabled(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0).eq(SysApiKey::getStatus, 1)
                            .eq(SysApiKey::getUserId, userId))));
            LocalDateTime expiringThreshold = LocalDateTime.now().plusDays(7);
            ov.setApiKeyExpiring(Math.toIntExact(apiKeyMapper.selectCount(
                    new LambdaQueryWrapper<SysApiKey>().eq(SysApiKey::getIsDeleted, 0).eq(SysApiKey::getStatus, 1)
                            .eq(SysApiKey::getUserId, userId)
                            .isNotNull(SysApiKey::getExpiredAt).le(SysApiKey::getExpiredAt, expiringThreshold))));

            // 普通用户无需看系统用户总数，设为0前端可据此隐藏
            ov.setUserTotal(0);
        }

        // 今日调用（普通用户看自己的调用记录）
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDateTime.now();
        Map<String, Object> todayCount = callLogMapper.countByDateRange(todayStart, todayEnd, isAdmin ? null : userId);
        int callToday = todayCount != null ? ((Number) todayCount.getOrDefault("total", 0)).intValue() : 0;
        ov.setCallToday(callToday);
        ov.setUserActiveToday(callToday > 0 ? Math.toIntExact(
                callLogMapper.countDistinctUser(todayStart, todayEnd, isAdmin ? null : userId)) : 0);

        // 较昨日增减百分比
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);
        Map<String, Object> yesterdayCount = callLogMapper.countByDateRange(yesterdayStart, yesterdayEnd, isAdmin ? null : userId);
        int callYesterday = yesterdayCount != null ? ((Number) yesterdayCount.getOrDefault("total", 0)).intValue() : 0;
        if (callYesterday > 0) {
            ov.setCallDeltaPct(Math.round((callToday - callYesterday) * 100.0 / callYesterday * 10) / 10.0);
        } else {
            ov.setCallDeltaPct(callToday > 0 ? 100.0 : 0.0);
        }

        return ov;
    }

    private List<DashboardStatsVO.CallTrendItem> buildCallTrend(boolean isAdmin, Long userId, int days) {
        LocalDate endDate = LocalDate.now().minusDays(1); // 预汇总表只有昨天的数据
        LocalDate startDate = endDate.minusDays(days - 1);

        List<DashboardStatsVO.CallTrendItem> trend = new ArrayList<>();

        if (isAdmin) {
            // 管理员：从全局预汇总表读取历史数据
            LambdaQueryWrapper<McpCallDailyStats> dailyWrapper = new LambdaQueryWrapper<McpCallDailyStats>()
                    .ge(McpCallDailyStats::getStatDate, startDate)
                    .le(McpCallDailyStats::getStatDate, endDate);
            List<McpCallDailyStats> dailyStats = dailyStatsMapper.selectList(dailyWrapper);

            if (!dailyStats.isEmpty()) {
                Map<LocalDate, List<McpCallDailyStats>> byDate = dailyStats.stream()
                        .collect(Collectors.groupingBy(McpCallDailyStats::getStatDate));
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    DashboardStatsVO.CallTrendItem item = new DashboardStatsVO.CallTrendItem();
                    item.setDate(d.toString());
                    List<McpCallDailyStats> dayStats = byDate.getOrDefault(d, List.of());
                    item.setTotal(dayStats.stream().mapToInt(s -> s.getCallCount() != null ? s.getCallCount() : 0).sum());
                    item.setFailed(dayStats.stream().mapToInt(s -> s.getFailCount() != null ? s.getFailCount() : 0).sum());
                    item.setDenied(dayStats.stream().mapToInt(s -> s.getDenyCount() != null ? s.getDenyCount() : 0).sum());
                    trend.add(item);
                }
            } else {
                // 降级：实时查日志表
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    LocalDateTime dayStart = d.atStartOfDay();
                    LocalDateTime dayEnd = d.atTime(LocalTime.MAX);
                    Map<String, Object> cnt = callLogMapper.countByDateRange(dayStart, dayEnd, null);
                    DashboardStatsVO.CallTrendItem item = new DashboardStatsVO.CallTrendItem();
                    item.setDate(d.toString());
                    item.setTotal(cnt != null ? ((Number) cnt.getOrDefault("total", 0)).intValue() : 0);
                    item.setFailed(cnt != null ? ((Number) cnt.getOrDefault("failed", 0)).intValue() : 0);
                    item.setDenied(0);
                    trend.add(item);
                }
            }
        } else {
            // 普通用户：从用户维度预汇总表读取自己的历史数据
            LambdaQueryWrapper<McpCallUserDailyStats> userWrapper = new LambdaQueryWrapper<McpCallUserDailyStats>()
                    .eq(McpCallUserDailyStats::getUserId, userId)
                    .ge(McpCallUserDailyStats::getStatDate, startDate)
                    .le(McpCallUserDailyStats::getStatDate, endDate);
            List<McpCallUserDailyStats> userStats = userDailyStatsMapper.selectList(userWrapper);

            if (!userStats.isEmpty()) {
                Map<LocalDate, List<McpCallUserDailyStats>> byDate = userStats.stream()
                        .collect(Collectors.groupingBy(McpCallUserDailyStats::getStatDate));
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    DashboardStatsVO.CallTrendItem item = new DashboardStatsVO.CallTrendItem();
                    item.setDate(d.toString());
                    List<McpCallUserDailyStats> dayStats = byDate.getOrDefault(d, List.of());
                    item.setTotal(dayStats.stream().mapToInt(s -> s.getCallCount() != null ? s.getCallCount() : 0).sum());
                    item.setFailed(dayStats.stream().mapToInt(s -> s.getFailCount() != null ? s.getFailCount() : 0).sum());
                    item.setDenied(dayStats.stream().mapToInt(s -> s.getDenyCount() != null ? s.getDenyCount() : 0).sum());
                    trend.add(item);
                }
            } else {
                // 降级：实时查日志表
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    LocalDateTime dayStart = d.atStartOfDay();
                    LocalDateTime dayEnd = d.atTime(LocalTime.MAX);
                    Map<String, Object> cnt = callLogMapper.countByDateRange(dayStart, dayEnd, userId);
                    DashboardStatsVO.CallTrendItem item = new DashboardStatsVO.CallTrendItem();
                    item.setDate(d.toString());
                    item.setTotal(cnt != null ? ((Number) cnt.getOrDefault("total", 0)).intValue() : 0);
                    item.setFailed(cnt != null ? ((Number) cnt.getOrDefault("failed", 0)).intValue() : 0);
                    item.setDenied(0);
                    trend.add(item);
                }
            }
        }

        // 补充今天的数据（实时查询，因为今天还没有跑批）
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDateTime.now();
        Map<String, Object> todayCnt = callLogMapper.countByDateRange(todayStart, todayEnd, isAdmin ? null : userId);
        DashboardStatsVO.CallTrendItem todayItem = new DashboardStatsVO.CallTrendItem();
        todayItem.setDate(LocalDate.now().toString());
        todayItem.setTotal(todayCnt != null ? ((Number) todayCnt.getOrDefault("total", 0)).intValue() : 0);
        todayItem.setFailed(todayCnt != null ? ((Number) todayCnt.getOrDefault("failed", 0)).intValue() : 0);
        trend.add(todayItem);

        return trend;
    }

    private DashboardStatsVO.SuccessRate buildSuccessRate(boolean isAdmin, Long userId, int days) {
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        Map<String, Object> cnt = callLogMapper.countByDateRange(start, end, isAdmin ? null : userId);

        DashboardStatsVO.SuccessRate rate = new DashboardStatsVO.SuccessRate();
        rate.setSuccess(cnt != null ? ((Number) cnt.getOrDefault("success", 0)).intValue() : 0);
        rate.setFailed(cnt != null ? ((Number) cnt.getOrDefault("failed", 0)).intValue() : 0);

        // 权限拒绝数单独查
        rate.setDenied(Math.toIntExact(callLogMapper.selectCount(
                new LambdaQueryWrapper<McpToolCallLog>()
                        .ge(McpToolCallLog::getCallAt, start)
                        .le(McpToolCallLog::getCallAt, end)
                        .eq(McpToolCallLog::getSuccess, 0)
                        .like(McpToolCallLog::getDenyReason, "%\u6743\u9650%")
                        .eq(!isAdmin, McpToolCallLog::getUserId, userId))));
        return rate;
    }

    private List<DashboardStatsVO.TopToolItem> buildTopTools(boolean isAdmin, Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDate endDate = LocalDate.now().minusDays(1);

        List<DashboardStatsVO.TopToolItem> items;
        if (isAdmin) {
            // 管理员：SQL 层聚合全局预汇总表，直接返回 Top 10
            items = dailyStatsMapper.selectTopTools(startDate, endDate, 10);
        } else {
            // 普通用户：SQL 层聚合用户维度预汇总表，直接返回 Top 10
            items = userDailyStatsMapper.selectTopToolsByUser(userId, startDate, endDate, 10);
        }

        // 预汇总表无数据时降级查日志表
        if (items.isEmpty()) {
            return buildTopToolsFallback(isAdmin, userId, days, startDate, endDate);
        }

        // 补充显示名
        List<Long> toolIds = items.stream().map(DashboardStatsVO.TopToolItem::getToolId).toList();
        Map<Long, McpTool> toolMap = toolMapper.selectBatchIds(toolIds).stream()
                .collect(Collectors.toMap(McpTool::getId, t -> t));
        items = items.stream().map(i -> {
            McpTool t = toolMap.get(i.getToolId());
            if (t != null) i.setDisplayName(t.getDisplayName());
            return i;
        }).toList();
        return items;
    }

    /**
     * Top 10 降级方案：预汇总表无数据时 SQL 层聚合查日志表（只返回 Top 10 行）
     */
    private List<DashboardStatsVO.TopToolItem> buildTopToolsFallback(boolean isAdmin, Long userId, int days,
                                                                      LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = callLogMapper.selectTopToolsAggregated(
                start, end, isAdmin ? null : userId, 10);

        List<DashboardStatsVO.TopToolItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DashboardStatsVO.TopToolItem item = new DashboardStatsVO.TopToolItem();
            item.setToolId(((Number) row.get("tool_id")).longValue());
            item.setToolName((String) row.get("tool_name"));
            int callCount = ((Number) row.get("call_count")).intValue();
            int successCount = row.get("success_count") != null ? ((Number) row.get("success_count")).intValue() : 0;
            item.setCallCount(callCount);
            item.setSuccessRate(callCount > 0 ? Math.round(successCount * 100.0 / callCount * 10) / 10.0 : 0);
            item.setAvgDurationMs(row.get("avg_duration_ms") != null ? ((Number) row.get("avg_duration_ms")).longValue() : 0L);
            items.add(item);
        }

        // 补充显示名
        List<Long> toolIds = items.stream().map(DashboardStatsVO.TopToolItem::getToolId).toList();
        if (!toolIds.isEmpty()) {
            Map<Long, McpTool> toolMap = toolMapper.selectBatchIds(toolIds).stream()
                    .collect(Collectors.toMap(McpTool::getId, t -> t));
            items = items.stream().map(i -> {
                McpTool t = toolMap.get(i.getToolId());
                if (t != null) i.setDisplayName(t.getDisplayName());
                return i;
            }).toList();
        }
        return items;
    }

    private List<DashboardStatsVO.DurationBucket> buildDurationDistribution(boolean isAdmin, Long userId) {
        // 近7天日志耗时分桶（SQL 层聚合，只返回 5 个计数值）
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        Map<String, Object> buckets = callLogMapper.countDurationBuckets(start, end, isAdmin ? null : userId);

        List<DashboardStatsVO.DurationBucket> result = new ArrayList<>();
        result.add(toBucket("0~100ms", buckets, "b0_100"));
        result.add(toBucket("100~500ms", buckets, "b100_500"));
        result.add(toBucket("500ms~1s", buckets, "b500_1000"));
        result.add(toBucket("1s~3s", buckets, "b1000_3000"));
        result.add(toBucket(">3s", buckets, "b3000_plus"));
        return result;
    }

    private DashboardStatsVO.DurationBucket toBucket(String label, Map<String, Object> buckets, String key) {
        DashboardStatsVO.DurationBucket bucket = new DashboardStatsVO.DurationBucket();
        bucket.setRange(label);
        bucket.setCount(buckets != null && buckets.get(key) != null ? ((Number) buckets.get(key)).intValue() : 0);
        return bucket;
    }

    private List<DashboardStatsVO.RoleDistributionItem> buildRoleDistribution() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getIsDeleted, 0).eq(SysRole::getStatus, 1));
        List<DashboardStatsVO.RoleDistributionItem> items = new ArrayList<>();
        for (SysRole role : roles) {
            DashboardStatsVO.RoleDistributionItem item = new DashboardStatsVO.RoleDistributionItem();
            item.setRoleId(role.getId());
            item.setRoleName(role.getRoleName());
            item.setToolCount(Math.toIntExact(roleToolMapper.selectCount(
                    new LambdaQueryWrapper<SysRoleTool>().eq(SysRoleTool::getRoleId, role.getId()))));
            items.add(item);
        }
        return items;
    }

    private DashboardStatsVO.MyPermissionSummary buildMyPermissionSummary(Long userId) {
        // 查询用户可访问的工具列表
        List<SysRoleTool> roleTools = roleToolMapper.selectList(
                new LambdaQueryWrapper<SysRoleTool>()
                        .inSql(SysRoleTool::getRoleId,
                                "SELECT role_id FROM sys_user_role WHERE user_id=" + userId + " AND is_deleted=0"));

        List<Long> toolIds = roleTools.stream().map(SysRoleTool::getToolId).distinct().toList();
        DashboardStatsVO.MyPermissionSummary summary = new DashboardStatsVO.MyPermissionSummary();
        summary.setToolCount(toolIds.size());

        if (!toolIds.isEmpty()) {
            List<McpTool> tools = toolMapper.selectBatchIds(toolIds);
            Map<Long, McpToolCategory> categoryMap = new java.util.HashMap<>();
            // 查分类
            List<Long> catIds = tools.stream().map(McpTool::getCategoryId)
                    .filter(c -> c != null && c > 0).distinct().toList();
            if (!catIds.isEmpty()) {
                categoryMapper.selectBatchIds(catIds).forEach(c -> categoryMap.put(c.getId(), c));
            }
            // 按分类聚合
            Map<String, Integer> catCounts = new java.util.LinkedHashMap<>();
            tools.forEach(t -> {
                String catName = t.getCategoryId() != null && categoryMap.containsKey(t.getCategoryId())
                        ? categoryMap.get(t.getCategoryId()).getCategoryName() : "未分类";
                catCounts.merge(catName, 1, Integer::sum);
            });
            List<DashboardStatsVO.CategoryCount> catList = catCounts.entrySet().stream()
                    .map(e -> {
                        DashboardStatsVO.CategoryCount cc = new DashboardStatsVO.CategoryCount();
                        cc.setCategoryName(e.getKey());
                        cc.setCount(e.getValue());
                        return cc;
                    }).toList();
            summary.setCategories(catList);
        }

        return summary;
    }
}