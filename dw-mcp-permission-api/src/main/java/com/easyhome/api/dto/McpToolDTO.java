package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建/更新 MCP 工具 DTO
 */
@Data
public class McpToolDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称最长128字符")
    private String toolName;

    /** 工具类型：JDBC / HTTP_PROXY */
    @NotBlank(message = "工具类型不能为空")
    private String toolType;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称最长128字符")
    private String displayName;

    @NotBlank(message = "描述不能为空")
    private String description;

    /** 关联数据源ds_key（JDBC类型必填） */
    private String datasourceKey;

    /** SQL模板，参数用#{paramName}占位 */
    private String sqlTemplate;

    /** HTTP方法：GET/POST/PUT/DELETE */
    private String httpMethod;

    @Size(max = 512, message = "URL最长512字符")
    private String httpUrl;

    /** 固定请求头（JSON格式） */
    private String httpHeaders;

    /** JSON Schema格式的输入参数定义 */
    private String inputSchema;

    /** JSON Schema格式的输出结果定义 */
    private String outputSchema;

    /** 所属分类ID */
    private Long categoryId;

    private Integer sortOrder;

    @Size(max = 256)
    private String remark;

    /** 状态：1=启用 2=停用 */
    private Integer status;
}
