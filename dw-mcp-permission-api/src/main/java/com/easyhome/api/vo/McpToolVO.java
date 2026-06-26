package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 工具 VO
 */
@Data
public class McpToolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String toolName;
    private String toolType;
    private String displayName;
    private String description;
    private String datasourceKey;
    private String sqlTemplate;
    private String httpMethod;
    private String httpUrl;
    private String httpHeaders;
    private String inputSchema;
    private String outputSchema;
    private Long categoryId;
    private String categoryName;
    private Integer sortOrder;
    private String remark;
    /** 状态：1=启用 2=停用 */
    private Integer status;
    private Long creatorId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
