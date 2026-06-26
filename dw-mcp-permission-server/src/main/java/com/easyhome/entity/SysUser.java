package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录名，唯一） */
    private String username;

    /** 登录密码（BCrypt加密，管理后台登录用；纯API Key认证时可为空） */
    private String password;

    /** 显示名称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 备注 */
    private String remark;
}
