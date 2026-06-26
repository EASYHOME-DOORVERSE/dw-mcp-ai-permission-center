package com.easyhome.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 一键生成工具配置响应
 */
@Data
public class AiToolGenerateResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具名（英文，程序调用） */
    private String toolName;

    /** 显示名称（中文） */
    private String displayName;

    /** 描述 */
    private String description;

    /** 推荐的数据源 Key */
    private String datasourceKey;

    /** JSON Schema 输入参数定义 */
    private String inputSchema;

    /** JSON Schema 输出结果定义 */
    private String outputSchema;

    /** AI 优化后的 SQL 模板（语法修复 + 可读性优化，保持原有占位符语法不变） */
    private String optimizedSqlTemplate;
}
