package com.easyhome.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建/更新 MCP 数据源 DTO
 */
@Data
public class McpDatasourceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "数据源标识不能为空")
    @Size(max = 64, message = "数据源标识最长64字符")
    private String dsKey;

    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称最长128字符")
    private String dsName;

    /** 数据库类型：mysql/postgresql/oracle/sqlserver */
    private String dbType;

    @NotBlank(message = "JDBC连接URL不能为空")
    @Size(max = 512)
    private String url;

    @NotBlank(message = "数据库用户名不能为空")
    @Size(max = 128)
    private String username;

    /** 密码（明文传输，服务端AES加密后存储；更新时留空表示不修改密码） */
    private String password;

    private String driverClass;

    private Integer poolMinSize;

    private Integer poolMaxSize;

    @Size(max = 256)
    private String remark;

    /** 状态：1=启用 2=停用 */
    private Integer status;
}
