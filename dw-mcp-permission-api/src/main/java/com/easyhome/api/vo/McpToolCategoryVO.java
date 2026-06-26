package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 工具分类 VO
 */
@Data
public class McpToolCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String categoryName;
    private String categoryCode;
    private Integer sortOrder;
    private String remark;
    /** 是否为系统内置分类（不可删除） */
    private Boolean builtIn;
    private Long creatorId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
