package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.dto.SysRoleDTO;
import com.easyhome.api.feign.SysRoleClient;
import com.easyhome.api.vo.*;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.SysRole;
import com.easyhome.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysRoleController implements SysRoleClient {

    private final SysRoleService roleService;

    @Override
    public Result<PageResult<SysRoleVO>> page(long current, long size, String roleCode, Integer status) {
        Page<SysRole> p = roleService.page(current, size, roleCode, status);
        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(this::toVO).toList()));
    }

    @Override
    public Result<SysRoleVO> getById(@PathVariable Long id) {
        SysRole role = roleService.getById(id);
        return role == null ? Result.fail(404, "角色不存在") : Result.ok(toVO(role));
    }

    @Override
    public Result<SysRoleVO> create(@RequestBody @Validated SysRoleDTO dto) {
        return Result.ok(toVO(roleService.create(fromDTO(dto))));
    }

    @Override
    public Result<SysRoleVO> update(@PathVariable Long id, @RequestBody @Validated SysRoleDTO dto) {
        SysRole role = fromDTO(dto);
        role.setId(id);
        return Result.ok(toVO(roleService.update(role)));
    }

    @Override
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @Override
    public Result<List<McpToolVO>> getTools(@PathVariable Long id) {
        List<McpTool> tools = roleService.getTools(id);
        return Result.ok(tools.stream().map(this::toToolVO).toList());
    }

    @Override
    public Result<Void> assignTools(@PathVariable Long id, @RequestBody List<Long> toolIds) {
        roleService.assignTools(id, toolIds);
        return Result.ok();
    }

    // ---- 转换方法 ----

    private SysRoleVO toVO(SysRole r) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(r.getId());
        vo.setRoleCode(r.getRoleCode());
        vo.setRoleName(r.getRoleName());
        vo.setDescription(r.getDescription());
        vo.setStatus(r.getStatus());
        vo.setCreatorId(r.getCreatorId());
        vo.setCreator(r.getCreator());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setUpdatedAt(r.getUpdatedAt());
        return vo;
    }

    private McpToolVO toToolVO(McpTool t) {
        McpToolVO vo = new McpToolVO();
        vo.setId(t.getId());
        vo.setToolName(t.getToolName());
        vo.setToolType(t.getToolType());
        vo.setDisplayName(t.getDisplayName());
        vo.setDescription(t.getDescription());
        vo.setDatasourceKey(t.getDatasourceKey());
        vo.setInputSchema(t.getInputSchema());
        vo.setSortOrder(t.getSortOrder());
        vo.setStatus(t.getStatus());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setUpdatedAt(t.getUpdatedAt());
        return vo;
    }

    private SysRole fromDTO(SysRoleDTO dto) {
        SysRole r = new SysRole();
        r.setRoleCode(dto.getRoleCode());
        r.setRoleName(dto.getRoleName());
        r.setDescription(dto.getDescription());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        return r;
    }
}
