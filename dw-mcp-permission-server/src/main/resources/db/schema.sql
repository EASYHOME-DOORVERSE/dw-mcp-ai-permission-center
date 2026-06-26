-- ============================================================
-- DW MCP AI 权限管理中心 - 数据库 DDL
-- 数据库：MySQL 8.0
-- 公共字段说明（所有主表均包含）：
--   status      : 1=启用 2=停用
--   is_deleted  : 逻辑删除（0=未删除 1=已删除），禁止物理删除
--   creator_id  : 创建人ID
--   creator     : 创建人名称（冗余，便于展示）
--   modifier_id : 最近修改人ID
--   modifier    : 最近修改人名称（冗余）
--   created_at  : 创建时间
--   updated_at  : 最近修改时间
-- ============================================================

CREATE DATABASE IF NOT EXISTS dw_mcp_permission DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dw_mcp_permission;

-- ============================================================
-- 一、用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名（登录名，唯一）',
    `password`    VARCHAR(128) DEFAULT NULL COMMENT '登录密码（BCrypt加密，管理后台登录用；API Key认证可为空）',
    `nickname`    VARCHAR(128) DEFAULT NULL COMMENT '显示名称',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `remark`      VARCHAR(256) DEFAULT NULL COMMENT '备注',
    -- 公共字段
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ============================================================
-- 二、角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(64)  NOT NULL COMMENT '角色编码（唯一），如 ADMIN、DATA_ANALYST',
    `role_name`   VARCHAR(128) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    -- 公共字段
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ============================================================
-- 三、用户-角色关联表
-- 关联关系不做 status，用 is_deleted 标记逻辑解除关联
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `role_id`     BIGINT   NOT NULL COMMENT '角色ID',
    -- is_deleted 纳入唯一键，允许撤销后重新绑定同一角色（逻辑删除后可再次插入）
    `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT   DEFAULT NULL COMMENT '操作人ID',
    `creator`     VARCHAR(50) DEFAULT NULL COMMENT '操作人名称',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- ============================================================
-- 四、API Key 表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_api_key` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '所属用户ID',
    `api_key`      VARCHAR(128) NOT NULL COMMENT 'API Key值，格式：sk-{32位随机串}',
    `key_name`     VARCHAR(64)  DEFAULT NULL COMMENT 'Key备注名称',
    `expired_at`   DATETIME     DEFAULT NULL COMMENT '过期时间，NULL表示永不过期',
    `last_used_at` DATETIME     DEFAULT NULL COMMENT '最近一次使用时间',
    -- 公共字段
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `account_id`   VARCHAR(64)  DEFAULT NULL COMMENT '账户ID，用于SQL模板参数自动注入',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户API Key表';

-- ============================================================
-- 五、MCP 数据源配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_datasource` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ds_key`        VARCHAR(64)  NOT NULL COMMENT '数据源标识（Dynamic-Datasource使用，全局唯一）',
    `ds_name`       VARCHAR(128) NOT NULL COMMENT '数据源显示名称',
    `db_type`       VARCHAR(32)  NOT NULL DEFAULT 'mysql' COMMENT '数据库类型：mysql/postgresql/oracle/sqlserver',
    `url`           VARCHAR(512) NOT NULL COMMENT 'JDBC连接URL',
    `username`      VARCHAR(128) DEFAULT NULL COMMENT '数据库用户名',
    `password`      VARCHAR(256) DEFAULT NULL COMMENT '数据库密码（AES加密存储）',
    `driver_class`  VARCHAR(128) DEFAULT NULL COMMENT 'JDBC驱动类（可自动推断）',
    `pool_min_size` INT          NOT NULL DEFAULT 5 COMMENT '连接池最小连接数',
    `pool_max_size` INT          NOT NULL DEFAULT 20 COMMENT '连接池最大连接数',
    `remark`        VARCHAR(256) DEFAULT NULL COMMENT '备注',
    -- 公共字段
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ds_key` (`ds_key`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP数据源配置表';

-- ============================================================
-- 五点五、MCP 工具分类表
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_tool_category` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_name` VARCHAR(128) NOT NULL COMMENT '分类名称',
    `category_code` VARCHAR(64)  NOT NULL COMMENT '分类编码（唯一标识）',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    `remark`        VARCHAR(256) DEFAULT NULL COMMENT '备注',
    -- 公共字段（无 status 字段，分类不需要启停用）
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`category_code`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具分类表';

-- ============================================================
-- 六、MCP 工具定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_tool` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tool_name`      VARCHAR(128) NOT NULL COMMENT 'MCP工具名称（协议级唯一标识，英文下划线命名）',
    `tool_type`      VARCHAR(32)  NOT NULL DEFAULT 'JDBC' COMMENT '工具类型：JDBC / HTTP_PROXY',
    `display_name`   VARCHAR(128) DEFAULT NULL COMMENT '显示名称（中文友好名）',
    `description`    TEXT         DEFAULT NULL COMMENT '工具描述（暴露给AI客户端）',
    `datasource_key` VARCHAR(64)  DEFAULT NULL COMMENT '关联的数据源ds_key（JDBC类型必填）',
    `sql_template`   TEXT         DEFAULT NULL COMMENT 'SQL模板，参数用#{paramName}占位',
    `http_method`    VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP方法：GET/POST/PUT/DELETE',
    `http_url`       VARCHAR(512) DEFAULT NULL COMMENT '代理目标URL',
    `http_headers`   TEXT         DEFAULT NULL COMMENT '固定请求头（JSON格式）',
    `input_schema`   TEXT         DEFAULT NULL COMMENT 'JSON Schema格式的输入参数定义',
    `output_schema`  TEXT         DEFAULT NULL COMMENT 'JSON Schema格式的输出结果定义',
    `category_id`    BIGINT       DEFAULT NULL COMMENT '所属分类ID，NULL或0表示默认分类',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    `remark`         VARCHAR(256) DEFAULT NULL COMMENT '备注',
    -- 公共字段
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    `creator_id`  BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `creator`     VARCHAR(50)  DEFAULT NULL COMMENT '创建人名称',
    `modifier_id` BIGINT       DEFAULT NULL COMMENT '修改人ID',
    `modifier`    VARCHAR(50)  DEFAULT NULL COMMENT '修改人名称',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_name` (`tool_name`),
    KEY `idx_tool_type` (`tool_type`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_is_deleted` (`is_deleted`),
    KEY `idx_datasource_key` (`datasource_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具定义表';

-- ============================================================
-- 七、角色-MCP工具关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role_tool` (
    `id`          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     BIGINT     NOT NULL COMMENT '角色ID',
    `tool_id`     BIGINT     NOT NULL COMMENT 'MCP工具ID',
    `creator_id`  BIGINT     DEFAULT NULL COMMENT '操作人ID',
    `creator`     VARCHAR(50) DEFAULT NULL COMMENT '操作人名称',
    `created_at`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    `updated_at`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 关联中间表使用物理删除，唯一键保证 (角色, 工具) 不重复
    UNIQUE KEY `uk_role_tool_active` (`role_id`, `tool_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-MCP工具关联表';

-- ============================================================
-- 八、MCP 工具调用日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_tool_call_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tool_id`       BIGINT       NOT NULL COMMENT 'MCP工具ID',
    `tool_name`     VARCHAR(128) NOT NULL COMMENT 'MCP工具名称（tool_{id}）',
    `user_id`       BIGINT       NOT NULL COMMENT '调用用户ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '调用用户名',
    `call_at`       DATETIME(3)  NOT NULL COMMENT '调用时间（毫秒精度）',
    `duration_ms`   BIGINT       NOT NULL DEFAULT 0 COMMENT '调用耗时（毫秒）',
    `success`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功：1=成功 0=失败/拒绝',
    `deny_reason`   VARCHAR(256) DEFAULT NULL COMMENT '拒绝/失败原因，成功时为NULL',
    `request_args`  TEXT         DEFAULT NULL COMMENT '调用参数（JSON）',
    `response_size` INT          DEFAULT NULL COMMENT '响应内容字节数',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    -- 看板查询优化：复合索引覆盖高频查询场景
    KEY `idx_call_at_user` (`call_at`, `user_id`),
    KEY `idx_call_at_tool_success` (`call_at`, `tool_id`, `success`),
    KEY `idx_user_call_at` (`user_id`, `call_at`),
    KEY `idx_tool_call_at` (`tool_id`, `call_at`),
    -- 百分位查询优化：ORDER BY duration_ms 走索引排序，避免 filesort
    KEY `idx_user_tool_callat_dur` (`user_id`, `tool_id`, `call_at`, `duration_ms`),
    KEY `idx_tool_callat_dur` (`tool_id`, `call_at`, `duration_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具调用日志表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 默认工具分类（分类删除时，工具自动归类到此分类）
INSERT INTO `mcp_tool_category` (`category_code`, `category_name`, `sort_order`, `remark`) VALUES
('other', '其他', 9999, '默认分类，工具未指定分类或分类被删除时自动归入');

INSERT INTO `sys_role` (`role_code`, `role_name`, `description`, `status`) VALUES
('ADMIN',        '管理员',     '拥有所有MCP工具的访问权限', 1),
('DATA_ANALYST', '数据分析师', '可访问数据查询类MCP工具',   1),
('READONLY',     '只读用户',   '只能访问只读查询工具',       1);

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `status`) VALUES
('admin', '$2a$10$vgRyDdZLW56k0ndwor/Zw.jgv8zCn8jgYjf8C4l8OJ7o4/4XPBLVi', '系统管理员', 'admin@example.com', 1);
-- 上述 password 为 BCrypt 加密的 'Admin@2024'，可用 BCryptPasswordEncoder.matches('Admin@2024', hash) 验证

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `sys_user` u, `sys_role` r
WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

INSERT INTO `sys_api_key` (`user_id`, `api_key`, `key_name`, `status`)
SELECT id, 'sk-demo-admin-key-for-testing-only', '演示Key（仅用于测试）', 1
FROM `sys_user` WHERE `username` = 'admin';

-- ============================================================
-- 九、MCP 调用日志每日预汇总表（全局）
-- 每日 00:05 由定时任务跑批生成，基于用户维度汇总表二次汇总，
-- 看板趋势图/成功率/热门工具优先从此表读取。
-- 维度：stat_date + tool_id（仅全局汇总）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_call_daily_stats` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date`     DATE         NOT NULL COMMENT '统计日期',
    `tool_id`       BIGINT       NOT NULL COMMENT 'MCP工具ID',
    `tool_name`     VARCHAR(128) NOT NULL COMMENT 'MCP工具名称',
    `call_count`    INT          NOT NULL DEFAULT 0 COMMENT '当日调用总次数',
    `success_count` INT          NOT NULL DEFAULT 0 COMMENT '当日成功次数',
    `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '当日失败次数',
    `deny_count`    INT          NOT NULL DEFAULT 0 COMMENT '当日权限拒绝次数',
    `avg_duration_ms` BIGINT     NOT NULL DEFAULT 0 COMMENT '当日平均耗时（毫秒）',
    `max_duration_ms` BIGINT     NOT NULL DEFAULT 0 COMMENT '当日最大耗时（毫秒）',
    `p50_duration_ms` BIGINT     NOT NULL DEFAULT 0 COMMENT '当日P50耗时（毫秒）',
    `p95_duration_ms` BIGINT     NOT NULL DEFAULT 0 COMMENT '当日P95耗时（毫秒）',
    `p99_duration_ms` BIGINT     NOT NULL DEFAULT 0 COMMENT '当日P99耗时（毫秒）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_tool` (`stat_date`, `tool_id`),
    KEY `idx_stat_date` (`stat_date`),
    KEY `idx_stat_date_tool` (`stat_date`, `tool_id`),
    KEY `idx_stat_date_user` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP调用日志每日预汇总表（全局）';

-- ============================================================
-- 十、MCP 调用日志用户维度每日预汇总表
-- 第一级汇总：按 userId + toolId + statDate 聚合原始日志，
-- 全局表 mcp_call_daily_stats 基于本表二次汇总生成。
-- 看板中普通用户从此表读取自己的数据。
-- ============================================================
CREATE TABLE IF NOT EXISTS `mcp_call_user_daily_stats` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date`       DATE         NOT NULL COMMENT '统计日期',
    `tool_id`         BIGINT       NOT NULL COMMENT 'MCP工具ID',
    `tool_name`       VARCHAR(128) NOT NULL COMMENT 'MCP工具名称',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `username`        VARCHAR(64)  NOT NULL COMMENT '用户名',
    `call_count`      INT          NOT NULL DEFAULT 0 COMMENT '当日调用总次数',
    `success_count`   INT          NOT NULL DEFAULT 0 COMMENT '当日成功次数',
    `fail_count`      INT          NOT NULL DEFAULT 0 COMMENT '当日失败次数',
    `deny_count`      INT          NOT NULL DEFAULT 0 COMMENT '当日权限拒绝次数',
    `avg_duration_ms` BIGINT       NOT NULL DEFAULT 0 COMMENT '当日平均耗时（毫秒）',
    `max_duration_ms` BIGINT       NOT NULL DEFAULT 0 COMMENT '当日最大耗时（毫秒）',
    `p50_duration_ms` BIGINT       NOT NULL DEFAULT 0 COMMENT '当日P50耗时（毫秒）',
    `p95_duration_ms` BIGINT       NOT NULL DEFAULT 0 COMMENT '当日P95耗时（毫秒）',
    `p99_duration_ms` BIGINT       NOT NULL DEFAULT 0 COMMENT '当日P99耗时（毫秒）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_tool_user` (`stat_date`, `tool_id`, `user_id`),
    KEY `idx_stat_date` (`stat_date`),
    KEY `idx_stat_date_user` (`stat_date`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP调用日志用户维度每日预汇总表';
