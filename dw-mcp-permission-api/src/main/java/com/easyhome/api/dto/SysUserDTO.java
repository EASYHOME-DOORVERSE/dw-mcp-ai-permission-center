package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建/更新用户 DTO
 */
@Data
public class SysUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID（更新时必填，新增时留空） */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长64字符")
    private String username;

    /** 新增/修改密码时传入（BCrypt加密前的明文；不修改密码则不传） */
    @Size(min = 8, max = 64, message = "密码长度8-64位")
    private String password;

    @Size(max = 128, message = "显示名称最长128字符")
    private String nickname;

    @Size(max = 128, message = "邮箱最长128字符")
    private String email;

    @Size(max = 256, message = "备注最长256字符")
    private String remark;

    /** 状态：1=启用 2=停用 */
    private Integer status;
}
