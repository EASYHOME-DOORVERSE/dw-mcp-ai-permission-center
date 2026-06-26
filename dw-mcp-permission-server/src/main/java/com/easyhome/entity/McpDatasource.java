package com.easyhome.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP数据源配置表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_datasource")
public class McpDatasource extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源标识（Dynamic-Datasource使用的key，全局唯一） */
    private String dsKey;

    /** 数据源显示名称 */
    private String dsName;

    /** 数据库类型：mysql/postgresql/oracle/sqlserver */
    private String dbType;

    /** JDBC连接URL */
    private String url;

    /** 数据库用户名 */
    private String username;

    /** 数据库密码（AES加密存储） */
    private String password;

    /** JDBC驱动类（可自动推断） */
    private String driverClass;

    private Integer poolMinSize;

    private Integer poolMaxSize;

    private String remark;
}
