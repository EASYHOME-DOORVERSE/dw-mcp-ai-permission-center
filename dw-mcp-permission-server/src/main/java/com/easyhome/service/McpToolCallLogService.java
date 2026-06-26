package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpToolCallLog;

import java.time.LocalDateTime;

public interface McpToolCallLogService {

    /**
     * 异步保存一条调用日志（非阻塞，失败不影响主流程）
     */
    void saveAsync(McpToolCallLog log);

    /**
     * 分页查询调用日志
     *
     * @param current   当前页（从1开始）
     * @param size      每页条数
     * @param toolId    工具 ID（可选）
     * @param userId    用户 ID（可选）
     * @param username  用户名模糊查询（可选）
     * @param success   成功状态过滤（可选，null=全部）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     */
    Page<McpToolCallLog> page(long current, long size,
                               Long toolId, Long userId, String username,
                               Boolean success,
                               LocalDateTime startTime, LocalDateTime endTime);
}
