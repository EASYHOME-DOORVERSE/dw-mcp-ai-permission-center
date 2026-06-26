package com.easyhome.api.feign;

import com.easyhome.api.dto.SysRoleDTO;
import com.easyhome.api.vo.McpToolVO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.api.vo.SysRoleVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 Feign Client
 */
@FeignClient(name = "mcp-permission-center", contextId = "sysRoleClient", path = "/api/roles")
public interface SysRoleClient {

    /**
     * 分页查询角色列表
     */
    @GetMapping
    Result<PageResult<SysRoleVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Integer status
    );

    /**
     * 查询角色详情
     */
    @GetMapping("/{id}")
    Result<SysRoleVO> getById(@PathVariable Long id);

    /**
     * 新增角色
     */
    @PostMapping
    Result<SysRoleVO> create(@RequestBody SysRoleDTO dto);

    /**
     * 更新角色（id必填）
     */
    @PutMapping("/{id}")
    Result<SysRoleVO> update(@PathVariable Long id, @RequestBody SysRoleDTO dto);

    /**
     * 逻辑删除角色
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 查询角色已授权的 MCP 工具列表
     */
    @GetMapping("/{id}/tools")
    Result<List<McpToolVO>> getTools(@PathVariable Long id);

    /**
     * 为角色授权 MCP 工具（全量覆盖）
     */
    @PutMapping("/{id}/tools")
    Result<Void> assignTools(@PathVariable Long id, @RequestBody List<Long> toolIds);
}
