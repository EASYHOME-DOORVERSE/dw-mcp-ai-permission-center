package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpToolCallLog;
import com.easyhome.mapper.McpToolCallLogMapper;
import com.easyhome.service.McpToolCallLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolCallLogServiceImpl implements McpToolCallLogService {

    private final McpToolCallLogMapper logMapper;

    /**
     * 异步写入调用日志，失败不影响主调用流程
     */
    @Override
    @Async
    public void saveAsync(McpToolCallLog callLog) {
        try {
            logMapper.insert(callLog);
        } catch (Exception e) {
            log.error("保存 MCP 调用日志失败: tool={} user={}", callLog.getToolName(), callLog.getUsername(), e);
        }
    }

    @Override
    public Page<McpToolCallLog> page(long current, long size,
                                     Long toolId, Long userId, String username,
                                     Boolean success,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<McpToolCallLog> wrapper = new LambdaQueryWrapper<McpToolCallLog>()
                .eq(toolId != null,   McpToolCallLog::getToolId,  toolId)
                .eq(userId != null,   McpToolCallLog::getUserId,  userId)
                .like(username != null && !username.isBlank(), McpToolCallLog::getUsername, username)
                .eq(success != null,  McpToolCallLog::getSuccess, success)
                .ge(startTime != null, McpToolCallLog::getCallAt, startTime)
                .le(endTime != null,   McpToolCallLog::getCallAt, endTime)
                .orderByDesc(McpToolCallLog::getCallAt);

        return logMapper.selectPage(new Page<>(current, size), wrapper);
    }
}
