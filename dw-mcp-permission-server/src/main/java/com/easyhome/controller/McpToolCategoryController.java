package com.easyhome.controller;

import com.easyhome.api.dto.McpToolCategoryDTO;
import com.easyhome.api.feign.McpToolCategoryClient;
import com.easyhome.api.vo.McpToolCategoryVO;
import com.easyhome.api.vo.Result;
import com.easyhome.entity.McpToolCategory;
import com.easyhome.security.JwtAuthenticationToken;
import com.easyhome.service.McpToolCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/tool-categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpToolCategoryController implements McpToolCategoryClient {

    private final McpToolCategoryService categoryService;

    @Override
    public Result<List<McpToolCategoryVO>> list() {
        List<McpToolCategory> categories = categoryService.list();
        return Result.ok(categories.stream().map(this::toVO).toList());
    }

    /**
     * 当前用户可访问工具所属的去重分类
     * <p>
     * 普通用户分类下拉专用：只返回用户有权限访问的启用工具所属的分类。
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public Result<List<McpToolCategoryVO>> mine() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth instanceof JwtAuthenticationToken jwtAuth ? jwtAuth.getUserId() : null;
        List<McpToolCategory> categories = categoryService.listByUserId(userId);
        return Result.ok(categories.stream().map(this::toVO).toList());
    }

    @Override
    public Result<McpToolCategoryVO> getById(@PathVariable Long id) {
        McpToolCategory category = categoryService.getById(id);
        return category == null ? Result.fail(404, "分类不存在") : Result.ok(toVO(category));
    }

    @Override
    public Result<McpToolCategoryVO> create(@RequestBody @Validated McpToolCategoryDTO dto) {
        return Result.ok(toVO(categoryService.create(fromDTO(dto))));
    }

    @Override
    public Result<McpToolCategoryVO> update(@PathVariable Long id, @RequestBody @Validated McpToolCategoryDTO dto) {
        McpToolCategory category = fromDTO(dto);
        category.setId(id);
        return Result.ok(toVO(categoryService.update(category)));
    }

    @Override
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }

    // ---- 转换方法 ----

    private McpToolCategoryVO toVO(McpToolCategory c) {
        McpToolCategoryVO vo = new McpToolCategoryVO();
        vo.setId(c.getId());
        vo.setCategoryName(c.getCategoryName());
        vo.setCategoryCode(c.getCategoryCode());
        vo.setSortOrder(c.getSortOrder());
        vo.setRemark(c.getRemark());
        vo.setBuiltIn(McpToolCategory.DEFAULT_CATEGORY_CODE.equals(c.getCategoryCode()));
        vo.setCreatorId(c.getCreatorId());
        vo.setCreator(c.getCreator());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        return vo;
    }

    private McpToolCategory fromDTO(McpToolCategoryDTO dto) {
        McpToolCategory c = new McpToolCategory();
        c.setCategoryName(dto.getCategoryName());
        c.setCategoryCode(dto.getCategoryCode());
        c.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        c.setRemark(dto.getRemark());
        return c;
    }
}
