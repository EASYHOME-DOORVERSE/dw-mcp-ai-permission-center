package com.easyhome.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户修改自己基础信息 DTO
 */
@Data
public class UpdateProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 128, message = "显示名称最长128字符")
    private String nickname;

    @Size(max = 128, message = "邮箱最长128字符")
    private String email;

    @Size(max = 256, message = "备注最长256字符")
    private String remark;
}
