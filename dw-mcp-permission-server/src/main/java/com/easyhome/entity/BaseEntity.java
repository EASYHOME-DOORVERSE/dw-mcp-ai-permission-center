package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共基类
 *
 * 所有主表实体均继承此类，统一管理以下公共字段：
 *   - status      : 业务状态（1=启用 2=停用）
 *   - isDeleted   : 逻辑删除标记，MyBatis-Plus 全局生效，禁止物理删除
 *   - creatorId / creator   : 创建人（ID + 名称冗余）
 *   - modifierId / modifier : 最近修改人（ID + 名称冗余）
 *   - createdAt / updatedAt : 时间戳，由 MetaObjectHandler 自动填充
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 业务状态：1=启用  2=停用 */
    @TableField(fill = FieldFill.INSERT)
    private Integer status;

    /**
     * 逻辑删除标记：0=未删除  1=已删除
     * MyBatis-Plus 的 @TableLogic 注解会在所有自动生成的 SQL 中自动追加
     *   WHERE is_deleted = 0
     * 并将 deleteById/delete 等操作改写为 UPDATE ... SET is_deleted = 1
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long creatorId;

    /** 创建人名称（冗余，避免 JOIN，便于展示和审计） */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /** 最近修改人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long modifierId;

    /** 最近修改人名称（冗余） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String modifier;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
