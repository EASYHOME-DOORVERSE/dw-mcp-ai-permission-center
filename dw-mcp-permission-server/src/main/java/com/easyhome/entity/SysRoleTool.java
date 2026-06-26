package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-MCP工具关联表
 */
@Data
@TableName("sys_role_tool")
public class SysRoleTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long toolId;

    @TableField(fill = FieldFill.INSERT)
    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
