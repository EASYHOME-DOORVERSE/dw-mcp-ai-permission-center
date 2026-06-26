package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpTool;

import java.util.List;

/**
 * MCP 工具管理服务
 */
public interface McpToolService {

    /** 查询所有启用的工具，供 MCP Server 启动时注册 */
    List<McpTool> listAllEnabledTools();

    /** 根据工具名称查询工具定义 */
    McpTool getByToolName(String toolName);

    Page<McpTool> page(long current, long size, String toolName, String toolType, Long categoryId, Integer status);

    /** 按用户 RBAC 权限分页查询启用的工具（无状态筛选） */
    Page<McpTool> pageByUserId(Long userId, long current, long size, String toolName, String toolType, Long categoryId);

    McpTool getById(Long id);

    McpTool create(McpTool tool);

    McpTool update(McpTool tool);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
