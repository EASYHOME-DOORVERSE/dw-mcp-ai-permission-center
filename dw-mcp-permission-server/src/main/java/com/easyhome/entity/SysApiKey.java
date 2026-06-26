package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户API Key表
 * 认证协议：HTTP Header  Authorization: Bearer sk-xxxxxxxx
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_api_key")
public class SysApiKey extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** API Key值，格式：sk-{32位随机串} */
    private String apiKey;

    /** Key备注名称（便于用户区分多个Key） */
    private String keyName;

    /** 账户ID，用于SQL模板参数自动注入 */
    private String accountId;

    /** 过期时间，NULL表示永不过期 */
    private LocalDateTime expiredAt;

    /** 最近一次使用时间 */
    private LocalDateTime lastUsedAt;

    /**
     * 判断Key是否有效：已启用（status=1）、未逻辑删除、未过期
     */
    public boolean isValid() {
        if (getStatus() == null || getStatus() != 1) {
            return false;
        }
        if (getIsDeleted() != null && getIsDeleted() == 1) {
            return false;
        }
        if (expiredAt != null && LocalDateTime.now().isAfter(expiredAt)) {
            return false;
        }
        return true;
    }
}
