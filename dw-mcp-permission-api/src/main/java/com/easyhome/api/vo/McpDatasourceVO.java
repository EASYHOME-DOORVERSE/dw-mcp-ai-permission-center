package com.easyhome.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP 数据源 VO（密码字段不返回）
 */
@Data
public class McpDatasourceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String dsKey;
    private String dsName;
    private String dbType;
    private String url;
    private String username;
    private String driverClass;
    private Integer poolMinSize;
    private Integer poolMaxSize;
    private String remark;
    /** 状态：1=启用 2=停用 */
    private Integer status;
    private Long creatorId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
