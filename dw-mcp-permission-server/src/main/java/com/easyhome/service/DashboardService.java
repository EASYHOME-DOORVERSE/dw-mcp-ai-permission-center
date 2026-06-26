package com.easyhome.service;

import com.easyhome.api.vo.DashboardStatsVO;
import com.easyhome.api.vo.PageResult;

import java.time.LocalDateTime;

/**
 * 看板聚合统计 + 调用日志查询服务
 */
public interface DashboardService {

    /**
     * 获取看板聚合统计数据
     *
     * @param isAdmin 是否管理员（决定数据范围和是否展示角色分布）
     * @param userId  当前用户 ID（普通用户时用于数据过滤）
     * @param days    趋势图天数（7 或 30）
     */
    DashboardStatsVO getStats(boolean isAdmin, Long userId, int days);

    /**
     * 分页查询调用日志（支持联动筛选）
     *
     * @param current   页码（从 1 开始）
     * @param size      每页条数
     * @param isAdmin   是否管理员
     * @param userId    当前用户 ID（普通用户强制过滤）
     * @param toolId    工具 ID（可选，图表联动）
     * @param toolName  工具名称（可选，模糊匹配）
     * @param success   调用状态（可选，null=全部）
     * @param denyReason 拒绝原因（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param username  用户名模糊查询（可选，仅管理员可用）
     */
    PageResult<DashboardStatsVO.CallLogItem> queryCallLogs(long current, long size,
                                                           boolean isAdmin, Long userId,
                                                           Long toolId, String toolName,
                                                           Boolean success, String denyReason,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           String username);
}