package com.easyhome.api.feign;

import com.easyhome.api.dto.McpToolCategoryDTO;
import com.easyhome.api.vo.McpToolCategoryVO;
import com.easyhome.api.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 工具分类管理 Feign Client
 */
@FeignClient(name = "mcp-permission-center", contextId = "mcpToolCategoryClient", path = "/api/mcp/tool-categories")
public interface McpToolCategoryClient {

    /**
     * 查询所有分类列表（不分页，分类数量有限）
     */
    @GetMapping
    Result<List<McpToolCategoryVO>> list();

    /**
     * 查询分类详情
     */
    @GetMapping("/{id}")
    Result<McpToolCategoryVO> getById(@PathVariable Long id);

    /**
     * 新增分类
     */
    @PostMapping
    Result<McpToolCategoryVO> create(@RequestBody McpToolCategoryDTO dto);

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    Result<McpToolCategoryVO> update(@PathVariable Long id, @RequestBody McpToolCategoryDTO dto);

    /**
     * 删除分类（工具自动归入默认"其他"分类）
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 查询用户可访问工具所属的去重分类
     * <p>
     * 普通用户分类下拉专用：只返回用户有权限访问的启用工具所属的分类。
     */
    @GetMapping("/mine")
    Result<List<McpToolCategoryVO>> mine();
}
