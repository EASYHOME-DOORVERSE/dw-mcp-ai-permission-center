package com.easyhome.api.feign;

import com.easyhome.api.dto.McpToolDTO;
import com.easyhome.api.vo.McpToolVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * MCP 工具管理 Feign Client
 */
@FeignClient(name = "mcp-permission-center", contextId = "mcpToolClient", path = "/api/mcp/tools")
public interface McpToolClient {

    /**
     * 分页查询 MCP 工具列表
     */
    @GetMapping
    Result<PageResult<McpToolVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String toolType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status
    );

    /**
     * 查询工具详情
     */
    @GetMapping("/{id}")
    Result<McpToolVO> getById(@PathVariable Long id);

    /**
     * 新增 MCP 工具
     */
    @PostMapping
    Result<McpToolVO> create(@RequestBody McpToolDTO dto);

    /**
     * 更新 MCP 工具（id必填）
     */
    @PutMapping("/{id}")
    Result<McpToolVO> update(@PathVariable Long id, @RequestBody McpToolDTO dto);

    /**
     * 逻辑删除 MCP 工具
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 启用/停用工具
     *
     * @param status 1=启用 2=停用
     */
    @PutMapping("/{id}/status/{status}")
    Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status);

    /**
     * 按用户 RBAC 权限分页查询启用的工具
     * <p>
     * 普通用户专用：只返回该用户有权限访问的启用工具，支持工具名模糊、分类、类型筛选。
     */
    @GetMapping("/mine")
    Result<PageResult<McpToolVO>> mine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String toolType,
            @RequestParam(required = false) Long categoryId
    );
}
