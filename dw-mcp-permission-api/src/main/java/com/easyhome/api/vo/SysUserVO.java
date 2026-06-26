package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息 VO（响应给调用方，不含密码）
 */
@Data
public class SysUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String remark;
    /** 状态：1=启用 2=停用 */
    private Integer status;
    private Long creatorId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
