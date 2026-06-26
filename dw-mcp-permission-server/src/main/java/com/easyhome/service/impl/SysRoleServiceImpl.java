package com.easyhome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.McpTool;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysRoleTool;
import com.easyhome.mapper.McpToolMapper;
import com.easyhome.mapper.SysRoleMapper;
import com.easyhome.mapper.SysRoleToolMapper;
import com.easyhome.mcp.event.RbacChangeEvent;
import com.easyhome.service.SysRoleService;
import com.easyhome.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleToolMapper roleToolMapper;
    private final McpToolMapper mcpToolMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final UserPermissionCacheService userPermissionCacheService;

   private static final String ROLE_TOOLS_LOCK_PREFIX = "rbac:role:tools:";

    /** 受保护的角色编码，禁止编辑、停用、删除 */
    private static final Set<String> PROTECTED_ROLE_CODES = Set.of("ADMIN");

    private void checkProtected(Long id) {
        SysRole existing = roleMapper.selectById(id);
        if (existing != null && PROTECTED_ROLE_CODES.contains(existing.getRoleCode())) {
            throw new IllegalArgumentException("系统内置角色[" + existing.getRoleCode() + "]不允许此操作");
        }
    }

    @Override
    public Page<SysRole> page(long current, long size, String roleCode, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(roleCode), SysRole::getRoleCode, roleCode)
                .eq(status != null, SysRole::getStatus, status)
                .orderByDesc(SysRole::getCreatedAt);
        return roleMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Override
    public SysRole getById(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    public SysRole create(SysRole role) {
        // 唯一性校验：roleCode 不允许重复
        SysRole existing = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode()));
        if (existing != null) {
            throw new IllegalArgumentException("角色编码已存在: " + role.getRoleCode());
        }
        roleMapper.insert(role);
        return role;
    }

    @Override
    public SysRole update(SysRole role) {
        // 受保护角色禁止编辑
        checkProtected(role.getId());
        // 不可修改校验：roleCode 不允许变更
        SysRole existing = roleMapper.selectById(role.getId());
        if (existing != null && !existing.getRoleCode().equals(role.getRoleCode())) {
            throw new IllegalArgumentException("角色编码不允许修改");
        }
        // 如果状态变更，清除该角色下所有用户的权限缓存
        if (role.getStatus() != null) {
            userPermissionCacheService.evictUserPermissionsByRoleIds(role.getId());
            log.info("角色状态变更，已清除关联用户缓存: roleId={}", role.getId());
        }
        roleMapper.updateById(role);
        return roleMapper.selectById(role.getId());
    }

    @Override
    public void delete(Long id) {
        // 受保护角色禁止删除
        checkProtected(id);
        roleMapper.deleteById(id);
        // 角色删除后，该角色下所有用户的工具权限变更
        eventPublisher.publishEvent(RbacChangeEvent.roleToolsChanged(this, id));
    }

    @Override
    public List<McpTool> getTools(Long roleId) {
        return mcpToolMapper.selectToolsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTools(Long roleId, List<Long> toolIds) {
        RLock lock = redissonClient.getLock(ROLE_TOOLS_LOCK_PREFIX + roleId);
        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    // 物理删除该角色现有授权（关联中间表无需逻辑删除）
                    roleToolMapper.delete(new LambdaQueryWrapper<SysRoleTool>()
                            .eq(SysRoleTool::getRoleId, roleId));
                    // 插入新授权
                    if (toolIds != null && !toolIds.isEmpty()) {
                        for (Long toolId : toolIds) {
                            SysRoleTool rt = new SysRoleTool();
                            rt.setRoleId(roleId);
                            rt.setToolId(toolId);
                            roleToolMapper.insert(rt);
                        }
                    }
                    // 角色工具授权变更，通知 MCP 客户端刷新工具列表
                    eventPublisher.publishEvent(RbacChangeEvent.roleToolsChanged(this, roleId));
                } catch (Exception e) {
                    log.error("角色工具授权变更，通知 MCP 客户端刷新工具列表失败, roleId={}", roleId, e);
                    throw new RuntimeException("操作失败，请稍后重试");
                } finally {
                    lock.unlock();
                }
            } else {
                log.warn("获取角色工具授权分布式锁超时, roleId={}", roleId);
                throw new RuntimeException("操作冲突，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断，请稍后重试");
        }
    }

    @Override
    public List<SysRole> getRolesByToolId(Long toolId) {
        if (toolId == null) {
            return List.of();
        }
        return roleMapper.selectRolesByToolId(toolId);
    }
}
