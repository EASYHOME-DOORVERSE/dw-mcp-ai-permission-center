package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建/更新 API Key DTO
 */
@Data
public class SysApiKeyDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long id;
    /**
     * 所属用户 ID（管理员指定，普通用户由后端自动填充当前用户 ID）
     */
    private Long userId;

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "Key备注名称最长64字符")
    private String keyName;

    @Size(max = 64, message = "账户ID最长64字符")
    private String accountId;

    /** 过期时间，null=永不过期 */
    private LocalDateTime expiredAt;
}
