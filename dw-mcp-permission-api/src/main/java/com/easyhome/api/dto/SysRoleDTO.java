package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建/更新角色 DTO
 */
@Data
public class SysRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码最长64字符")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称最长128字符")
    private String roleName;

    @Size(max = 256, message = "描述最长256字符")
    private String description;

    /** 状态：1=启用 2=停用 */
    private Integer status;
}
