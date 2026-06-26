package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 调用日志查询结果 VO
 * <p>
 * 包含日志基本信息 + 关联的工具显示名和分类名，方便前端展示。
 */
@Data
public class CallLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private Long toolId;
    private String toolName;
    private String displayName;
    private String categoryName;
    private LocalDateTime callAt;
    private Long durationMs;
    private Boolean success;
    private String denyReason;
    private String requestArgs;
    private Integer responseSize;
}