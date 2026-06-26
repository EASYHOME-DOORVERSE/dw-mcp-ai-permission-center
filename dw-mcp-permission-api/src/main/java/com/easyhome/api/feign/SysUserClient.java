package com.easyhome.api.feign;

import com.easyhome.api.dto.SysUserDTO;
import com.easyhome.api.vo.PageResult;
import com.easyhome.api.vo.Result;
import com.easyhome.api.vo.SysRoleVO;
import com.easyhome.api.vo.SysUserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 Feign Client
 *
 * <p>调用方需在启动类或配置类添加 {@code @EnableFeignClients}，
 * 并引入 dw-mcp-permission-api 依赖即可注入此接口。
 */
@FeignClient(name = "mcp-permission-center", contextId = "sysUserClient", path = "/api/users")
public interface SysUserClient {

    /**
     * 分页查询用户列表
     *
     * @param current  页码，从1开始
     * @param size     每页条数
     * @param username 用户名（模糊，可选）
     * @param status   状态过滤（可选）
     */
    @GetMapping
    Result<PageResult<SysUserVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status
    );

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    Result<SysUserVO> getById(@PathVariable Long id);

    /**
     * 新增用户
     */
    @PostMapping
    Result<SysUserVO> create(@RequestBody SysUserDTO dto);

    /**
     * 更新用户（id必填）
     */
    @PutMapping("/{id}")
    Result<SysUserVO> update(@PathVariable Long id, @RequestBody SysUserDTO dto);

    /**
     * 逻辑删除用户
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 启用/停用用户
     *
     * @param status 1=启用 2=停用
     */
    @PutMapping("/{id}/status/{status}")
    Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status);

    /**
     * 查询用户已绑定角色列表
     */
    @GetMapping("/{id}/roles")
    Result<List<SysRoleVO>> getRoles(@PathVariable Long id);

    /**
     * 为用户绑定角色（全量覆盖）
     */
    @PutMapping("/{id}/roles")
    Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds);

    /**
     * 重置用户密码为默认密码
     */
    @PutMapping("/{id}/reset-password")
    Result<Void> resetPassword(@PathVariable Long id);
}
