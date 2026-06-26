package com.easyhome.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easyhome.entity.SysRole;
import com.easyhome.entity.SysUser;

import java.util.List;

/**
 * 用户管理服务
 */
public interface SysUserService {

    /**
     * 账号密码认证，验证成功返回用户实体，失败返回 null
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 认证通过的用户，失败返回 null
     */
    SysUser authenticate(String username, String password);

    Page<SysUser> page(long current, long size, String username, Integer status);

    SysUser getById(Long id);

    SysUser create(SysUser user);

    SysUser update(SysUser user);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    List<SysRole> getRoles(Long userId);

    /** 全量覆盖用户角色（先逻辑删除旧关联，再插入新关联） */
    void assignRoles(Long userId, List<Long> roleIds);

    /** 重置用户密码为默认密码 */
    void resetPassword(Long userId);

    /** 修改用户基础信息（用户自己操作） */
    SysUser updateProfile(Long userId, String nickname, String email, String remark);

    /** 修改用户密码（用户自己操作，需校验旧密码） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 按用户名查找用户（忘记密码场景） */
    SysUser findByUsername(String username);

    /** 忘记密码：验证码通过后重置密码为新密码 */
    void resetPasswordByUsername(String username, String newPassword);

    // ========== 登录错误次数锁定 ==========

    /** 检查账号是否因多次输错密码而被锁定 */
    boolean isLoginLocked(String username);

    /** 获取锁定剩余分钟数（未锁定返回 0） */
    long getLoginLockRemainingMinutes(String username);

    /** 记录一次登录失败，达到上限后自动锁定 */
    void recordLoginFailure(String username);

    /** 清除登录失败计数（登录成功或重置密码后调用） */
    void clearLoginFailures(String username);

    /** 获取已尝试错误次数 */
    int getLoginFailCount(String username);
}
