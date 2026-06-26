package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key VO（脱敏展示，明文key只在生成时返回一次）
 */
@Data
public class SysApiKeyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String keyName;
    private String accountId;
    /**
     * API Key 值：
     *   - 生成接口（/generate）返回完整明文，格式 sk-xxxx
     *   - 列表/详情接口返回脱敏值，格式 sk-****xxxx（后4位可见）
     */
    private String apiKey;
    private LocalDateTime expiredAt;
    private LocalDateTime lastUsedAt;
    /** 状态：1=启用 2=停用 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
