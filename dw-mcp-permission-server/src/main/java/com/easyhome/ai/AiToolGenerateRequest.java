package com.easyhome.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 一键生成工具配置请求
 */
@Data
public class AiToolGenerateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SQL 模板 */
    @NotBlank(message = "SQL 模板不能为空")
    private String sqlTemplate;

    /** 数据源 Key，用于查询表结构以辅助生成（可为空） */
    private String datasourceKey;

    /** 数据源列表（供 AI 参考选择） */
    private String datasourceHint;
}
