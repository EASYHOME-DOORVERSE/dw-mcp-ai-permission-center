package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpTool;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.mcp.event.McpToolChangeEvent;
import com.easyhome.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

    private final McpToolMapper mcpToolMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<McpTool> listAllEnabledTools() {
        return mcpToolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getStatus, 1)
                .orderByAsc(McpTool::getSortOrder)
                .orderByAsc(McpTool::getId));
    }

    @Override
    public McpTool getByToolName(String toolName) {
        return mcpToolMapper.selectOne(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getToolName, toolName)
                .eq(McpTool::getStatus, 1));
    }

    @Override
    public Page<McpTool> page(long current, long size, String toolName, String toolType, Long categoryId, Integer status) {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<McpTool>()
                .and(StringUtils.hasText(toolName), w -> w
                        .like(McpTool::getToolName, toolName)
                        .or()
                        .like(McpTool::getDisplayName, toolName))
                .eq(StringUtils.hasText(toolType), McpTool::getToolType, toolType)
                .eq(categoryId != null, McpTool::getCategoryId, categoryId)
                .eq(status != null, McpTool::getStatus, status)
                .orderByAsc(McpTool::getSortOrder)
                .orderByDesc(McpTool::getCreatedAt);
        return mcpToolMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public Page<McpTool> pageByUserId(Long userId, long current, long size, String toolName, String toolType, Long categoryId) {
        return mcpToolMapper.selectToolsByUserIdPaged(
                new Page<>(current, size),
                userId,
                StringUtils.hasText(toolName) ? toolName : null,
                StringUtils.hasText(toolType) ? toolType : null,
                categoryId
        );
    }

    @Override
    public McpTool getById(Long id) {
        return mcpToolMapper.selectById(id);
    }

    @Override
    public McpTool create(McpTool tool) {
        validateByType(tool);
        // 唯一性校验：toolName 不允许重复
        McpTool existing = mcpToolMapper.selectOne(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getToolName, tool.getToolName()));
        if (existing != null) {
            throw new IllegalArgumentException("工具名称已存在: " + tool.getToolName());
        }
        mcpToolMapper.insert(tool);
        eventPublisher.publishEvent(
                new McpToolChangeEvent(this, tool.getId(), McpToolChangeEvent.ChangeType.CREATE));
        return tool;
    }

    @Override
    public McpTool update(McpTool tool) {
        validateByType(tool);
        // 不可修改校验：toolName 不允许变更
        McpTool existing = mcpToolMapper.selectById(tool.getId());
        if (existing != null && !existing.getToolName().equals(tool.getToolName())) {
            throw new IllegalArgumentException("工具名称不允许修改");
        }
        mcpToolMapper.updateById(tool);
        eventPublisher.publishEvent(
                new McpToolChangeEvent(this, tool.getId(), McpToolChangeEvent.ChangeType.UPDATE));
        return mcpToolMapper.selectById(tool.getId());
    }

    @Override
    public void delete(Long id) {
        mcpToolMapper.deleteById(id);
        eventPublisher.publishEvent(
                new McpToolChangeEvent(this, id, McpToolChangeEvent.ChangeType.DELETE));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        mcpToolMapper.update(null, new LambdaUpdateWrapper<McpTool>()
                .eq(McpTool::getId, id)
                .set(McpTool::getStatus, status));
        McpToolChangeEvent.ChangeType type = (status != null && status == 1)
                ? McpToolChangeEvent.ChangeType.ENABLE
                : McpToolChangeEvent.ChangeType.DISABLE;
        eventPublisher.publishEvent(new McpToolChangeEvent(this, id, type));
    }

    private void validateByType(McpTool tool) {
        if ("JDBC".equals(tool.getToolType())) {
            if (!StringUtils.hasText(tool.getDatasourceKey())) {
                throw new IllegalArgumentException("JDBC类型工具必须指定数据源Key");
            }
            if (!StringUtils.hasText(tool.getSqlTemplate())) {
                throw new IllegalArgumentException("JDBC类型工具必须填写SQL模板");
            }
        } else if ("HTTP_PROXY".equals(tool.getToolType())) {
            if (!StringUtils.hasText(tool.getHttpMethod())) {
                throw new IllegalArgumentException("HTTP_PROXY类型工具必须指定HTTP方法");
            }
            if (!StringUtils.hasText(tool.getHttpUrl())) {
                throw new IllegalArgumentException("HTTP_PROXY类型工具必须填写URL");
            }
        }
    }
}
