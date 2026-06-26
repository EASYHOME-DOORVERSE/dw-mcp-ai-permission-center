package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP工具定义表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_tool")
public class McpTool extends BaseEntity {

    @TableId(type = IdType.AUTO)
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
    private Integer sortOrder;
    private String remark;
}
