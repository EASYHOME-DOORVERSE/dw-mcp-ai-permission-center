package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板聚合统计数据响应 VO
 * <p>
 * 一次请求返回完整看板数据，前端并行渲染各模块。
 */
@Data
public class DashboardStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 概览统计 */
    private Overview overview;

    /** 调用趋势（按天） */
    private List<CallTrendItem> callTrend;

    /** 调用成功率分布 */
    private SuccessRate successRate;

    /** 热门工具 Top 10 */
    private List<TopToolItem> topTools;

    /** 耗时分布 */
    private List<DurationBucket> durationDistribution;

    /** 角色权限分布（管理员才有） */
    private List<RoleDistributionItem> roleDistribution;

    /** 我的工具权限摘要（普通用户才有） */
    private MyPermissionSummary myPermissionSummary;

    /** 调用日志查询结果行 */
    @Data
    public static class CallLogItem implements Serializable {
        private Long id;
        private Long userId;
        private String username;
        private Long toolId;
        private String toolName;
        private String displayName;
        private String categoryName;
        private String callAt;
        private Long durationMs;
        private Boolean success;
        private String denyReason;
        private String requestArgs;
        private Integer responseSize;
    }

    // ---- 内嵌数据结构 ----

    @Data
    public static class Overview implements Serializable {
        private int toolTotal;
        private int toolEnabled;
        private int apiKeyTotal;
        private int apiKeyEnabled;
        private int apiKeyExpiring;
        private int userTotal;
        private int userActiveToday;
        private int callToday;
        private double callDeltaPct;
    }

    @Data
    public static class CallTrendItem implements Serializable {
        private String date;
        private int total;
        private int failed;
        private int denied;
    }

    @Data
    public static class SuccessRate implements Serializable {
        private int success;
        private int failed;
        private int denied;
    }

    @Data
    public static class TopToolItem implements Serializable {
        private Long toolId;
        private String toolName;
        private String displayName;
        private int callCount;
        private double successRate;
        private Long avgDurationMs;
    }

    @Data
    public static class DurationBucket implements Serializable {
        private String range;
        private int count;
    }

    @Data
    public static class RoleDistributionItem implements Serializable {
        private Long roleId;
        private String roleName;
        private int toolCount;
    }

    @Data
    public static class MyPermissionSummary implements Serializable {
        private int toolCount;
        private List<CategoryCount> categories;
    }

    @Data
    public static class CategoryCount implements Serializable {
        private String categoryName;
        private int count;
    }
}