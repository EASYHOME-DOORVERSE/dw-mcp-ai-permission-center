package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色信息 VO
 */
@Data
public class SysRoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    /** 状态：1=启用 2=停用 */
    private Integer status;
    private Long creatorId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
