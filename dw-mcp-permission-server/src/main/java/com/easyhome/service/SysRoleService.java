package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.SysRole;

import java.util.List;

/**
 * 角色管理服务
 */
public interface SysRoleService {

    Page<SysRole> page(long current, long size, String roleCode, Integer status);

    SysRole getById(Long id);

    SysRole create(SysRole role);

    SysRole update(SysRole role);

    void delete(Long id);

    List<McpTool> getTools(Long roleId);

    /** 全量覆盖角色工具授权 */
    void assignTools(Long roleId, List<Long> toolIds);

    /**
     * 根据工具ID查询所有关联的角色
     *
     * @param toolId 工具ID
     * @return 角色列表
     */
    List<SysRole> getRolesByToolId(Long toolId);
}
