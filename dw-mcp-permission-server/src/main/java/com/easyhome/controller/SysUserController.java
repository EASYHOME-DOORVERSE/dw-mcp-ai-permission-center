package com.easyhome.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.api.dto.SysUserDTO;
import com.easyhome.api.feign.SysUserClient;
import com.easyhome.api.vo.*;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysUser;
import com.easyhome.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysUserController implements SysUserClient {

    private final SysUserService userService;

    @Override
    public Result<PageResult<SysUserVO>> page(long current, long size, String username, Integer status) {
        Page<SysUser> p = userService.page(current, size, username, status);
        return Result.ok(PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(this::toVO).toList()));
    }

    @Override
    public Result<SysUserVO> getById(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        return user == null ? Result.fail(404, "用户不存在") : Result.ok(toVO(user));
    }

    @Override
    public Result<SysUserVO> create(@RequestBody @Validated SysUserDTO dto) {
        SysUser user = fromDTO(dto);
        return Result.ok(toVO(userService.create(user)));
    }

    @Override
    public Result<SysUserVO> update(@PathVariable Long id, @RequestBody @Validated SysUserDTO dto) {
        SysUser user = fromDTO(dto);
        user.setId(id);
        return Result.ok(toVO(userService.update(user)));
    }

    @Override
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @Override
    public Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.updateStatus(id, status);
        return Result.ok();
    }

    @Override
    public Result<List<SysRoleVO>> getRoles(@PathVariable Long id) {
        List<SysRole> roles = userService.getRoles(id);
        List<SysRoleVO> vos = roles.stream().map(this::toRoleVO).toList();
        return Result.ok(vos);
    }

    @Override
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return Result.ok();
    }

    @Override
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }

    // ---- 转换方法 ----

    private SysUserVO toVO(SysUser u) {
        SysUserVO vo = new SysUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setEmail(u.getEmail());
        vo.setRemark(u.getRemark());
        vo.setStatus(u.getStatus());
        vo.setCreatorId(u.getCreatorId());
        vo.setCreator(u.getCreator());
        vo.setCreatedAt(u.getCreatedAt());
        vo.setUpdatedAt(u.getUpdatedAt());
        return vo;
    }

    private SysRoleVO toRoleVO(SysRole r) {
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

    private SysUser fromDTO(SysUserDTO dto) {
        SysUser u = new SysUser();
        u.setUsername(dto.getUsername());
        u.setPassword(dto.getPassword());
        u.setNickname(dto.getNickname());
        u.setEmail(dto.getEmail());
        u.setRemark(dto.getRemark());
        if (dto.getStatus() != null) u.setStatus(dto.getStatus());
        return u;
    }
}
