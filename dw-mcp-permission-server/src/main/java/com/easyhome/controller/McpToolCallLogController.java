package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.entity.McpToolCallLog;
import com.easyhome.service.McpToolCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * MCP Tool 调用日志查询接口
 */
@RestController
@RequestMapping("/api/mcp/tool-call-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpToolCallLogController {

    private final McpToolCallLogService callLogService;

    /**
     * 分页查询调用日志
     *
     * @param current   页码（默认 1）
     * @param size      每页条数（默认 20）
     * @param toolId    工具 ID（可选）
     * @param userId    用户 ID（可选）
     * @param username  用户名模糊匹配（可选）
     * @param success   是否成功（可选）
     * @param startTime 开始时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     */
    @GetMapping
    public Result<PageResult<McpToolCallLog>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long toolId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Page<McpToolCallLog> p = callLogService.page(current, size, toolId, userId, username,
                success, startTime, endTime);

        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(), p.getRecords()));
    }
}
