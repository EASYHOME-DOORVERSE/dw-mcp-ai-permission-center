package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建/更新 MCP 工具分类 DTO
 */
@Data
public class McpToolCategoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称最长128字符")
    private String categoryName;

    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码最长64字符")
    private String categoryCode;

    private Integer sortOrder;

    @Size(max = 256)
    private String remark;
}
