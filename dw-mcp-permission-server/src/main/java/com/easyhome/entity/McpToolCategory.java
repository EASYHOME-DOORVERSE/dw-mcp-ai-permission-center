package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP工具分类表
 *
 * 分类不需要启停用功能，因此不继承 BaseEntity（BaseEntity 包含 status 字段）。
 * 手动定义逻辑删除和审计字段。
 */
@Data
@TableName("mcp_tool_category")
public class McpToolCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认分类编码，分类删除时工具自动归入此分类 */
    public static final String DEFAULT_CATEGORY_CODE = "other";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 分类编码（唯一标识） */
    private String categoryCode;

    /** 排序值 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    // ---- 逻辑删除 & 审计字段（不继承 BaseEntity，手动定义） ----

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private String creator;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long modifierId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String modifier;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
