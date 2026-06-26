package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.dto.McpToolDTO;
import com.easyhome.api.feign.McpToolClient;
import com.easyhome.api.vo.McpToolVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.McpToolCategory;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.service.McpToolCategoryService;
import com.easyhome.service.McpToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mcp/tools")
@RequiredArgsConstructor
public class McpToolController implements McpToolClient {

    private final McpToolService mcpToolService;
    private final McpToolCategoryService categoryService;

    @Override
    public Result<PageResult<McpToolVO>> page(long current, long size,
                                              String toolName, String toolType,
                                              Long categoryId, Integer status) {
        Page<McpTool> p = mcpToolService.page(current, size, toolName, toolType, categoryId, status);

        // 构建分类 ID → 名称映射
        Map<Long, String> categoryNames = categoryService.list().stream()
                .collect(Collectors.toMap(McpToolCategory::getId, McpToolCategory::getCategoryName));

        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(t -> toVO(t, categoryNames)).toList()));
    }

    @Override
    public Result<McpToolVO> getById(@PathVariable Long id) {
        McpTool tool = mcpToolService.getById(id);
        return tool == null ? Result.fail(404, "工具不存在") : Result.ok(toVO(tool, getCategoryNameMap()));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Result<McpToolVO> create(@RequestBody @Validated McpToolDTO dto) {
        return Result.ok(toVO(mcpToolService.create(fromDTO(dto)), getCategoryNameMap()));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Result<McpToolVO> update(@PathVariable Long id, @RequestBody @Validated McpToolDTO dto) {
        McpTool tool = fromDTO(dto);
        tool.setId(id);
        return Result.ok(toVO(mcpToolService.update(tool), getCategoryNameMap()));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        mcpToolService.delete(id);
        return Result.ok();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        mcpToolService.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * 当前用户可访问的启用工具（分页）
     * <p>
     * 普通用户查看自己拥有的 MCP 工具列表，支持工具名模糊、分类、类型筛选。
     */
    @Override
    public Result<PageResult<McpToolVO>> mine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String toolType,
            @RequestParam(required = false) Long categoryId) {
        Long userId = getCurrentUserId();
        Page<McpTool> p = mcpToolService.pageByUserId(
                userId, current, size, toolName, toolType, categoryId);

        // 构建分类 ID → 名称映射（使用用户可见分类）
        Map<Long, String> categoryNames = categoryService.listByUserId(userId).stream()
                .collect(Collectors.toMap(McpToolCategory::getId, McpToolCategory::getCategoryName));

        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(t -> toVO(t, categoryNames)).toList()));
    }

    // ---- 辅助方法 ----

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserId();
        }
        return null;
    }

    private Map<Long, String> getCategoryNameMap() {
        return categoryService.list().stream()
                .collect(Collectors.toMap(McpToolCategory::getId, McpToolCategory::getCategoryName));
    }

    private McpToolVO toVO(McpTool t, Map<Long, String> categoryNames) {
        McpToolVO vo = new McpToolVO();
        vo.setId(t.getId());
        vo.setToolName(t.getToolName());
        vo.setToolType(t.getToolType());
        vo.setDisplayName(t.getDisplayName());
        vo.setDescription(t.getDescription());
        vo.setDatasourceKey(t.getDatasourceKey());
        vo.setSqlTemplate(t.getSqlTemplate());
        vo.setHttpMethod(t.getHttpMethod());
        vo.setHttpUrl(t.getHttpUrl());
        vo.setHttpHeaders(t.getHttpHeaders());
        vo.setInputSchema(t.getInputSchema());
        vo.setOutputSchema(t.getOutputSchema());
        vo.setCategoryId(t.getCategoryId());
        vo.setCategoryName(t.getCategoryId() != null ? categoryNames.get(t.getCategoryId()) : null);
        vo.setSortOrder(t.getSortOrder());
        vo.setRemark(t.getRemark());
        vo.setStatus(t.getStatus());
        vo.setCreatorId(t.getCreatorId());
        vo.setCreator(t.getCreator());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setUpdatedAt(t.getUpdatedAt());
        return vo;
    }

    private McpTool fromDTO(McpToolDTO dto) {
        McpTool t = new McpTool();
        t.setToolName(dto.getToolName());
        t.setToolType(dto.getToolType());
        t.setDisplayName(dto.getDisplayName());
        t.setDescription(dto.getDescription());
        t.setDatasourceKey(dto.getDatasourceKey());
        t.setSqlTemplate(dto.getSqlTemplate());
        t.setHttpMethod(dto.getHttpMethod());
        t.setHttpUrl(dto.getHttpUrl());
        t.setHttpHeaders(dto.getHttpHeaders());
        t.setInputSchema(dto.getInputSchema());
        t.setOutputSchema(dto.getOutputSchema());
        t.setCategoryId(dto.getCategoryId());
        t.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        t.setRemark(dto.getRemark());
        if (dto.getStatus() != null) t.setStatus(dto.getStatus());
        return t;
    }
}
